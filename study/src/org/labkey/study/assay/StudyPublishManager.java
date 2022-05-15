/*
 * Copyright (c) 2008-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.study.assay;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AbstractAssayProvider;
import org.labkey.api.assay.AssayFileWriter;
import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.AssayTableMetadata;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.SampleTimelineAuditEvent;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.collections.LabKeyCollectors;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.data.*;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ProvenanceService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.qc.DataState;
import org.labkey.api.qc.DataStateManager;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.FolderAdminRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyEntity;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.publish.PublishKey;
import org.labkey.api.study.publish.StudyDatasetLinkedColumn;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.study.query.PublishResultsQueryView;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.study.StudySchema;
import org.labkey.study.StudyServiceImpl;
import org.labkey.study.assay.query.PublishAuditProvider;
import org.labkey.study.controllers.publish.PublishController;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.DatasetDomainKind;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.UploadLog;
import org.labkey.study.query.StudyQuerySchema;
import org.springframework.beans.MutablePropertyValues;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.labkey.api.audit.SampleTimelineAuditEvent.SAMPLE_TIMELINE_EVENT_TYPE;
import static org.labkey.study.query.DatasetTableImpl.SOURCE_ROW_LSID;

/**
 * Manages the link to study operation that links assay rows into datasets in the target study, creating the dataset
 * if needed.
 * User: Mark Igra
 * Date: Aug 16, 2006
 */
public class StudyPublishManager implements StudyPublishService
{
    private static final int MIN_ASSAY_ID = 5000;
    private static final Logger LOG = LogManager.getLogger(StudyPublishManager.class);

    public synchronized static StudyPublishManager getInstance()
    {
        return (StudyPublishManager) StudyPublishService.get();
    }


    private TableInfo getTinfoUpdateLog()
    {
        return StudySchema.getInstance().getTableInfoUploadLog();
    }

    /**
     * Studies that the user has permission to.
     */
    @Override
    public Set<Study> getValidPublishTargets(@NotNull User user, @NotNull Class<? extends Permission> permission)
    {
        Set<? extends Study> studies = StudyManager.getInstance().getAllStudies(ContainerManager.getRoot(), user, permission);

        // Sort based on full container path
        Set<Study> result = new TreeSet<>(Comparator.comparing(StudyEntity::getContainer));
        result.addAll(studies);
        return result;
    }

    private List<PropertyDescriptor> createTargetPropertyDescriptors(Dataset dataset, List<PropertyDescriptor> sourcePds, List<String> errors)
    {
        List<PropertyDescriptor> targetPds = new ArrayList<>(sourcePds.size());
        Set<String> legalNames = new HashSet<>();
        for (PropertyDescriptor sourcePd : sourcePds)
        {
            PropertyDescriptor targetPd = sourcePd.clone();

            // Deal with duplicate legal names.  It's too bad that we have to do so this late in the game
            // (rather than at assay design time), but for a long time there was no mechanism to
            // prevent assay designers from creating properties with names that are the same as hard columns.
            // There are also a few cases where an assay provider may add columns to the published set as
            // publish time; rather than reserve all these names at design time, we catch them here.
            String legalName = ColumnInfo.legalNameFromName(targetPd.getName()).toLowerCase();
            if (legalNames.contains(legalName))
            {
                errors.add("Unable to link to study: duplicate column \"" + targetPd.getName() + "\" detected in the assay design.  Please contact an administrator.");
                return Collections.emptyList();
            }
            legalNames.add(legalName);

            targetPd.setPropertyURI(dataset.getTypeURI() + "#" + sourcePd.getName());
            targetPd.setContainer(dataset.getContainer());
            targetPd.setProject(dataset.getContainer().getProject());
            if (targetPd.getLookupQuery() != null)
                targetPd.setLookupContainer(sourcePd.getLookupContainer());
            // set the ID to zero so it's clear that this is a new property descriptor:
            targetPd.setPropertyId(0);
            targetPds.add(targetPd);
        }
        return targetPds;
    }

    @Override
    public void checkForAlreadyLinkedRows(User user, Pair<Dataset.PublishSource, Integer> publishSource,
                                          List<String> errors, Map<Container, Set<Integer>> rowIdsByTargetContainer)
    {
        for (Map.Entry<Container, Set<Integer>> entry : rowIdsByTargetContainer.entrySet())
        {
            Study targetStudy = StudyService.get().getStudy(entry.getKey());

            // Look for an existing dataset backed by this assay definition
            for (Dataset dataset : targetStudy.getDatasets())
            {
                if (publishSource.second.equals(dataset.getPublishSourceId()) &&
                        publishSource.first == dataset.getPublishSource() &&
                        dataset.getKeyPropertyName() != null)
                {
                    // Check to see if it already has the data rows that are being linked
                    TableInfo tableInfo = ((DatasetDefinition)dataset).getDatasetSchemaTableInfo(user, false);
                    Filter datasetFilter = new SimpleFilter(new SimpleFilter.InClause(FieldKey.fromParts(dataset.getKeyPropertyName()), entry.getValue()));
                    long existingRowCount = new TableSelector(tableInfo, datasetFilter, null).getRowCount();
                    if (existingRowCount > 0)
                    {
                        // If so, don't let the user link them again, even if they have different participant/visit/date info
                        String errorMessage = existingRowCount == 1 ? "One of the selected rows has" : (existingRowCount + " of the selected rows have");
                        errorMessage += " already been linked to the study \"" + targetStudy.getLabel() + "\" in " + entry.getKey().getPath();
                        errorMessage += " (RowIds: " + entry.getValue() + ")";
                        errors.add(errorMessage);
                    }
                }
            }
        }
    }

    @Override
    public ActionURL publishData(User user, Container sourceContainer, Container targetContainer, String sourceName,
                                 Pair<Dataset.PublishSource, Integer> publishSource,
                                 List<Map<String, Object>> dataMaps, Map<String, PropertyType> types, List<String> errors)
    {
        return publishData(user, sourceContainer, targetContainer, sourceName, publishSource, dataMaps, types, null, errors);
    }

    @Override
    public ActionURL publishData(User user, Container sourceContainer, @Nullable Container targetContainer, @Nullable String datasetCategory,
                                 String sourceName, Pair<Dataset.PublishSource, Integer> publishSource,
                                 List<Map<String, Object>> dataMaps, String keyPropertyName, List<String> errors)
    {
        return publishData(user, sourceContainer, targetContainer, datasetCategory, sourceName, publishSource, dataMaps, Collections.emptyList(), keyPropertyName, errors);
    }

    private ActionURL publishData(User user, Container sourceContainer, Container targetContainer, String sourceName,
                                  Pair<Dataset.PublishSource, Integer> publishSource,
                                  List<Map<String, Object>> dataMaps, Map<String, PropertyType> types, String keyPropertyName, List<String> errors)
    {
        TimepointType timetype = StudyManager.getInstance().getStudy(targetContainer).getTimepointType();

        List<PropertyDescriptor> propertyDescriptors = new ArrayList<>();
        for (Map.Entry<String, PropertyType> entry : types.entrySet())
        {
            String pdName = entry.getKey();
            if ("Date".equalsIgnoreCase(pdName) && TimepointType.VISIT != timetype)
                continue;
            PropertyType type = types.get(pdName);
            PropertyDescriptor pd = new PropertyDescriptor(null,
                    type, pdName, targetContainer);
            if (type.getJavaType() == Double.class)
                pd.setFormat("0.###");
            propertyDescriptors.add(pd);
        }
        return publishData(user, sourceContainer, targetContainer, sourceName, publishSource, dataMaps, propertyDescriptors, keyPropertyName, errors);
    }

    private ActionURL publishData(User user, Container sourceContainer, @Nullable Container targetContainer, String sourceName,
                                  Pair<Dataset.PublishSource, Integer> publishSource,
                                  List<Map<String, Object>> dataMaps, List<PropertyDescriptor> columns, String keyPropertyName, List<String> errors)
    {
        return publishData(user, sourceContainer, targetContainer, null, sourceName, publishSource, dataMaps, columns, keyPropertyName, errors);
    }

    private ActionURL publishData(User user, Container sourceContainer, @Nullable Container targetContainer, @Nullable String datasetCategory,
                                  String sourceName, Pair<Dataset.PublishSource, Integer> publishSource,
                                  List<Map<String, Object>> dataMaps, List<PropertyDescriptor> columns, String keyPropertyName, List<String> errors)
    {
        // Partition dataMaps by targetStudy.
        Map<Container, List<Map<String, Object>>> partitionedDataMaps = new HashMap<>();
        for (Map<String, Object> dataMap : dataMaps)
        {
            Container targetStudy = targetContainer;
            if (dataMap.containsKey("TargetStudy"))
                targetStudy = (Container) dataMap.get("TargetStudy");
            assert targetStudy != null;

            List<Map<String, Object>> maps = partitionedDataMaps.computeIfAbsent(targetStudy, k -> new ArrayList<>(dataMap.size()));
            maps.add(dataMap);
        }

        // CONSIDER: transact all linkages together
        // CONSIDER: returning list of URLS along with a count of rows linked to each study.
        ActionURL url = null;
        for (Map.Entry<Container, List<Map<String, Object>>> entry : partitionedDataMaps.entrySet())
        {
            Container targetStudy = entry.getKey();
            List<Map<String, Object>> maps = entry.getValue();
            url = _publishData(user, sourceContainer, targetStudy, datasetCategory, sourceName, publishSource, maps, columns, keyPropertyName, errors);
        }
        return url;
    }

    private ActionURL _publishData(User user, Container sourceContainer, @NotNull Container targetContainer, @Nullable String datasetCategory, String sourceName,
                                   Pair<Dataset.PublishSource, Integer> publishSource,
                                   List<Map<String, Object>> dataMaps, List<PropertyDescriptor> columns, String keyPropertyName, List<String> errors)
    {
        if (dataMaps.isEmpty())
        {
            errors.add("No data rows to publish");
            return null;
        }

        if (publishSource == null)
        {
            errors.add("Publish source must be provided");
            return null;
        }
        StudyImpl targetStudy = StudyManager.getInstance().getStudy(targetContainer);
        assert verifyRequiredColumns(dataMaps, targetStudy.getTimepointType());

        boolean schemaChanged = false;
        DatasetDefinition dataset = null;
        List<Map<String, Object>> convertedDataMaps;
        List<String> datasetLsids;

        try (DbScope.Transaction transaction = StudySchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            List<DatasetDefinition> datasets = StudyManager.getInstance().getDatasetDefinitions(targetStudy);

            for (DatasetDefinition dsd : datasets)
            {
                // If there's a dataset linked to our protocol, use it
                if (dsd.getPublishSourceId() != null &&
                        dsd.getPublishSourceId().equals(publishSource.second) &&
                        dsd.getPublishSource() == publishSource.first)
                {
                    dataset = dsd;
                    break;
                }
                else if (publishSource.second == null &&
                        publishSource.first == Dataset.PublishSource.Assay &&
                        dsd.getTypeURI() != null &&
                        dsd.getTypeURI().equals(DatasetDomainKind.generateDomainURI(sourceName, dsd.getEntityId(), targetStudy.getContainer())))
                {
                    // No protocol, but we've got a type uri match. This is used when creating a study
                    // from a study design
                    dataset = dsd;
                    break;
                }
            }

            if (dataset == null)
            {
                DatasetDefinition.Builder datasetBuilder = new DatasetDefinition.Builder(createUniqueDatasetName(targetStudy, sourceName))
                        .setStudy(targetStudy)
                        .setKeyPropertyName(keyPropertyName)
                        .setPublishSourceId(publishSource.second)
                        .setPublishSource(publishSource.first);

                if (datasetCategory != null)
                    datasetBuilder.setCategoryId(ViewCategoryManager.getInstance().ensureViewCategory(targetContainer, user, datasetCategory).getRowId());

                dataset = createDataset(user, datasetBuilder);
            }
            else if (publishSource.second != null)
            {
                Integer datasetPublishSourceId = dataset.getPublishSourceId();
                if (datasetPublishSourceId == null)
                {
                    dataset = dataset.createMutable();
                    dataset.setPublishSourceId(publishSource.second);
                    StudyManager.getInstance().updateDatasetDefinition(user, dataset, errors);
                }
                else if (!datasetPublishSourceId.equals(publishSource.second) || dataset.getPublishSource() != publishSource.first)
                {
                    errors.add("The destination dataset belongs to a different linked data source");
                    return null;
                }

                // Set category (if given) if pre-existing category is 'Uncategorized'
                if (datasetCategory != null && dataset.getCategoryId() == null)
                {
                    dataset = dataset.createMutable();
                    dataset.setCategoryId(ViewCategoryManager.getInstance().ensureViewCategory(targetContainer, user, datasetCategory).getRowId());
                    StudyManager.getInstance().updateDatasetDefinition(user, dataset, errors);
                }

                // Make sure the key property matches,
                // or the dataset data row won't have a link back to the assay data row
                if (!keyPropertyName.equals(dataset.getKeyPropertyName()))
                {
                    dataset = dataset.createMutable();
                    dataset.setKeyPropertyName(keyPropertyName);
                    StudyManager.getInstance().updateDatasetDefinition(user, dataset, errors);
                }
            }

            List<PropertyDescriptor> types = createTargetPropertyDescriptors(dataset, columns, errors);
            for (PropertyDescriptor type : types)
            {
                if (type.getPropertyId() == 0)
                {
                    schemaChanged = true;
                    break;
                }
            }
            if (!errors.isEmpty())
                return null;

            Map<String, String> propertyNamesToUris = ensurePropertyDescriptors(user, dataset, dataMaps, types, keyPropertyName);
            convertedDataMaps = convertPropertyNamesToURIs(dataMaps, propertyNamesToUris, publishSource.getKey().name());

            // re-retrieve the datasetdefinition: this is required to pick up any new columns that may have been created
            // in 'ensurePropertyDescriptors'.
            if (schemaChanged)
                StudyManager.getInstance().uncache(dataset);
            dataset = StudyManager.getInstance().getDatasetDefinition(targetStudy, dataset.getRowId());
            Integer defaultQCStateId = targetStudy.getDefaultPublishDataQCState();
            DataState defaultQCState = null;
            if (defaultQCStateId != null)
                defaultQCState = DataStateManager.getInstance().getStateForRowId(targetContainer, defaultQCStateId.intValue());

            BatchValidationException validationException = new BatchValidationException();
            if (!targetContainer.hasPermission(user, AdminPermission.class) && targetContainer.hasPermission(user, InsertPermission.class))
            {
                // we allow linking data to a study even if the study security is set to read-only datasets, since the
                // underlying insert uses the QUS, we pass in a contextual role to allow the insert to succeed
                Set<Role> contextualRoles = new HashSet<>(user.getStandardContextualRoles());
                contextualRoles.add(RoleManager.getRole(FolderAdminRole.class));
                user = new LimitedUser(user, user.getGroups(), contextualRoles, false);
            }
            datasetLsids = StudyManager.getInstance().importDatasetData(user, dataset, convertedDataMaps, validationException, DatasetDefinition.CheckForDuplicates.sourceAndDestination, defaultQCState, null, false, false);
            StudyManager.getInstance().batchValidateExceptionToList(validationException, errors);

            final ExpObject source = publishSource.first.resolvePublishSource(publishSource.second);
            createProvenanceRun(user, targetContainer, publishSource.first, source, errors, dataset, datasetLsids);

            if (!errors.isEmpty())
                return null;

            if (datasetLsids.size() > 0)
            {
                logPublishEvent(publishSource.first, source, dataMaps, user, sourceContainer, targetContainer, dataset);
            }

            transaction.commit();
        }
        catch (ChangePropertyDescriptorException | IOException e)
        {
            throw UnexpectedException.wrap(e);
        }

        //Make sure that the study is updated with the correct timepoints.
        StudyManager.getInstance().getVisitManager(targetStudy).updateParticipantVisits(user, Collections.singleton(dataset));

        return PageFlowUtil.urlProvider(StudyUrls.class).getDatasetURL(targetContainer, dataset.getRowId());
    }

    private void createProvenanceRun(User user, @NotNull Container targetContainer, Dataset.PublishSource sourceType, @Nullable ExpObject source, List<String> errors, DatasetDefinition dataset, List<String> datasetLsids)
    {
        if (source == null || datasetLsids.isEmpty())
            return;

        // If provenance module is not present, do nothing
        ProvenanceService pvs = ProvenanceService.get();
        if (!pvs.isProvenanceSupported())
            return;

        switch (sourceType)
        {
            case SampleType -> {
                ExpSampleType sampleType = (ExpSampleType) source;
                createProvenanceRun(user, targetContainer, sampleType, errors, dataset, datasetLsids);
            }
            case Assay -> {
                ExpProtocol protocol = (ExpProtocol) source;
                AssayProvider provider = AssayService.get().getProvider(protocol);
                if (provider == null)
                {
                    errors.add("provenance error: assay provider for '" + protocol.getName() + "' not found");
                    return;
                }

                if (provider.getResultRowLSIDPrefix() == null)
                {
                    LOG.info("Can't create provenance run; Assay provider '" + provider.getName() + "' for assay '" + protocol.getName() + "' has no result row lsid prefix");
                    return;
                }

                createProvenanceRun(user, targetContainer, protocol, errors, dataset, datasetLsids);
            }
        }
    }

    private void createProvenanceRun(User user, @NotNull Container targetContainer, @NotNull ExpObject source, List<String> errors, DatasetDefinition dataset, List<String> datasetLsids)
    {
        assert !datasetLsids.isEmpty();

        ProvenanceService pvs = ProvenanceService.get();
        assert pvs.isProvenanceSupported();

        String domainName = dataset.getDomain().getName();
        TableInfo datasetTable = dataset.getDomainKind().getTableInfo(user, targetContainer, domainName, null);
        ColumnInfo datasetLsidCol = datasetTable.getColumn("lsid");
        ColumnInfo sourceRowLsidCol = datasetTable.getColumn(SOURCE_ROW_LSID);
        if (sourceRowLsidCol == null)
        {
            errors.add("provenance error: expected " + dataset.getName() + " dataset table for '" + source.getName() + "' to have source row LSID column '" + SOURCE_ROW_LSID + "'");
            return;
        }

        // Add Provenance details
        // Create a mapping from the assay result LSID to the dataset LSID
        Set<Pair<String, String>> lsidPairs = new HashSet<>();
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("lsid"), datasetLsids, CompareType.IN);
        Collection<Map<String, Object>> datasetRowsMap = new TableSelector(datasetTable, List.of(datasetLsidCol, sourceRowLsidCol), filter, null).getMapCollection();
        Set<String> sourceRowLsids = new LinkedHashSet<>();
        for (Map<String, Object> datasetRow : datasetRowsMap)
        {
            String sourceRowLsid = Objects.toString(datasetRow.get(SOURCE_ROW_LSID), null);
            String datasetLsid = Objects.toString(datasetRow.get("lsid"), null);
            if (sourceRowLsid == null || datasetLsid == null)
            {
                errors.add("provenance error: Expected source row to have an lsid: " + datasetRow);
                return;
            }
            lsidPairs.add(Pair.of(sourceRowLsid, datasetLsid));
            sourceRowLsids.add(sourceRowLsid);
        }

        // Create a new experiment run using the "Study Publish" Protocol
        ExpRun run = ExperimentService.get().createExperimentRun(targetContainer, "StudyPublishRun");
        ExpProtocol studyPublishProtocol;

        try
        {
            studyPublishProtocol = ensureStudyPublishProtocol(user);

            run.setProtocol(studyPublishProtocol);
            ViewBackgroundInfo info = new ViewBackgroundInfo(targetContainer, user, null);
            PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(info.getContainer());
            run.setFilePathRoot(pipeRoot.getRootPath());

            run = ExperimentService.get().saveSimpleExperimentRun(run,
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    info, LOG, false);
        }
        catch (ExperimentException e)
        {
            errors.add("provenance error: StudyPublishRun can not be created.");
            LOG.error(e);
            return;
        }

        // Add the source row LSIDs as provenance inputs to the “StudyPublish” run’s starting protocol application
        pvs.addProvenanceInputs(targetContainer, run.getInputProtocolApplication(), sourceRowLsids);

        // Add the provenance mapping of source row LSID to study dataset LSID to the run’s final protocol application
        pvs.addProvenance(targetContainer, run.getOutputProtocolApplication(), lsidPairs);

        // Call syncRunEdges
        ExperimentService.get().syncRunEdges(run);
    }

    /**
     * To help generate the assay audit record, group the rows by the source type lsid (assay protocol or sample type).
     */
    private Map<Optional<String>, List<Map<String, Object>>> groupBySourceLsid(List<Map<String, Object>> dataMaps)
    {
        // source LSID may be null
        return dataMaps.stream().collect(Collectors.groupingBy(m -> Optional.ofNullable((String) m.get(SOURCE_LSID_PROPERTY_NAME))));
    }

    // TODO : consider pushing this into PublishSource
    private void logPublishEvent(Dataset.PublishSource publishSource, ExpObject source, List<Map<String, Object>> dataMaps, User user, Container sourceContainer, Container targetContainer, Dataset dataset)
    {
        Map<Optional<String>, List<Map<String, Object>>> sourceLSIDCounts = groupBySourceLsid(dataMaps);
        if (source != null)
        {
            for (var entry : sourceLSIDCounts.entrySet())
            {
                // source LSID may be null
                String sourceLsid = entry.getKey().orElse(null);
                List<Map<String, Object>> rows = entry.getValue();
                int recordCount = rows.size();

                String auditMessage = publishSource.getLinkToStudyAuditMessage(source, recordCount);
                PublishAuditProvider.AuditEvent event = new PublishAuditProvider.AuditEvent(sourceContainer.getId(), auditMessage, publishSource, source, sourceLsid);

                event.setTargetStudy(targetContainer.getId());
                event.setDatasetId(dataset.getDatasetId());
                event.setRecordCount(recordCount);

                AuditLogService.get().addEvent(user, event);

                // Create sample timeline event for each of the samples
                if (Dataset.PublishSource.SampleType == publishSource)
                {
                    var timelineEventType = SampleTimelineAuditEvent.SampleTimelineEventType.PUBLISH;
                    Map<String, Object> eventMetadata = new HashMap<>();
                    eventMetadata.put(SAMPLE_TIMELINE_EVENT_TYPE, timelineEventType.name());
                    String metadata = AbstractAuditTypeProvider.encodeForDataMap(sourceContainer, eventMetadata);

                    List<Integer> sampleIds = rows.stream().map(m -> (Integer) m.get(StudyPublishService.ROWID_PROPERTY_NAME)).collect(toList());
                    List<? extends ExpMaterial> samples = ExperimentService.get().getExpMaterials(sampleIds);
                    List<AuditTypeEvent> events = new ArrayList<>(samples.size());
                    for (ExpMaterial sample : samples)
                    {
                        int sampleId = sample.getRowId();
                        String sampleName = sample.getName();
                        String sampleLsid = sample.getLSID();

                        SampleTimelineAuditEvent timelineEvent = new SampleTimelineAuditEvent(sourceContainer.getId(), timelineEventType.getComment());
                        timelineEvent.setSampleType(source.getName());
                        timelineEvent.setSampleTypeId(source.getRowId());
                        timelineEvent.setSampleId(sampleId);
                        timelineEvent.setSampleName(sampleName);
                        timelineEvent.setSampleLsid(sampleLsid);

                        timelineEvent.setMetadata(metadata);
                        timelineEvent.setLineageUpdate(false);
                        events.add(timelineEvent);
                    }

                    AuditLogService.get().addEvents(user, events);
                }
            }
        }
    }

    private boolean verifyRequiredColumns(List<Map<String, Object>> dataMaps, TimepointType timepointType)
    {
        for (Map<String, Object> dataMap : dataMaps)
        {
            Set<String> lcaseSet = new HashSet<>();
            for (String key : dataMap.keySet())
                lcaseSet.add(key.toLowerCase());
            assert lcaseSet.contains("participantid") : "Publishable assay results must include participantid, sequencenum, and sourcelsid columns.";
            assert !timepointType.isVisitBased() || lcaseSet.contains("sequencenum") : "Publishable assay results must include participantid, sequencenum, and sourcelsid columns.";
            assert timepointType.isVisitBased() || lcaseSet.contains("date") : "Publishable assay results must include participantid, date, and sourcelsid columns.";
            //assert lcaseSet.contains("sourcelsid") : "Publishable assay results must include participantid, sequencenum, and sourcelsid columns.";
        }
        return true;
    }

    private List<Map<String, Object>> convertPropertyNamesToURIs(List<Map<String, Object>> dataMaps, Map<String, String> propertyNamesToUris, String publishSourceTypeName)
    {
        List<Map<String, Object>> ret = new ArrayList<>(dataMaps.size());
        for (Map<String, Object> dataMap : dataMaps)
        {
            Map<String, Object> newMap = new CaseInsensitiveHashMap<>(dataMap.size());
            for (Map.Entry<String, Object> entry : dataMap.entrySet())
            {
                String uri = propertyNamesToUris.get(entry.getKey());

                // NOTE Date is always special for publish be sure to use VisitDateURI to 'mark' this column
                if (StudyPublishService.DATE_PROPERTY_NAME.equalsIgnoreCase(entry.getKey()))
                    uri = DatasetDefinition.getVisitDateURI();

                if (null == uri)
                {
                    // Skip "TargetStudy"
                    if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equalsIgnoreCase(entry.getKey()))
                        continue;
                }

                assert uri != null : String.format("Expected all properties to already be present in %s type. Couldn't find one for " + entry.getKey(), publishSourceTypeName);
                newMap.put(uri, entry.getValue());
            }
            ret.add(newMap);
        }
        return ret;
    }


    private Map<String, String> ensurePropertyDescriptors(
            User user, DatasetDefinition dataset,
            List<Map<String, Object>> dataMaps, List<PropertyDescriptor> types, String keyPropertyName) throws ChangePropertyDescriptorException
    {
        Domain domain = dataset.getDomain();
        if (domain == null)
        {
            domain = PropertyService.get().createDomain(dataset.getContainer(), dataset.getTypeURI(), dataset.getName());
            domain.save(user);
        }
        // Strip out any spaces from existing PropertyDescriptors in the dataset
        boolean propertyChanged = false;
        for (DomainProperty existingProperty : domain.getProperties())
        {
            if (existingProperty.getName().contains(" "))
            {
                existingProperty.setName(existingProperty.getName().replace(" ", ""));
                existingProperty.setPropertyURI(existingProperty.getPropertyURI().replace(" ", ""));
                propertyChanged = true;
            }
        }
        if (propertyChanged)
        {
            domain.save(user);
        }

        // Strip out spaces from any proposed PropertyDescriptor names
        for (PropertyDescriptor newPD : types)
        {
            if (newPD.getName().contains(" "))
            {
                String newName = newPD.getName().replace(" ", "");
                for (Map<String, Object> dataMap : dataMaps)
                {
                    Object value = dataMap.get(newPD.getName());
                    dataMap.remove(newPD.getName());
                    dataMap.put(newName, value);
                }
                newPD.setName(newName);
                if (newPD.getPropertyURI() != null)
                {
                    newPD.setPropertyURI(newPD.getPropertyURI().replace(" ", ""));
                }
            }
        }

        // we'll return a mapping from column name to column uri
        Map<String, String> propertyNamesToUris = new CaseInsensitiveHashMap<>();

        // add ontology properties to our return map
        for (DomainProperty property : domain.getProperties())
            propertyNamesToUris.put(property.getName(), property.getPropertyURI());

        // add hard columns to our return map
        for (ColumnInfo col : DatasetDefinition.getTemplateTableInfo().getColumns())
        {
            // Swap out whatever subject column name is used in the target study for 'ParticipantID'.
            // This allows the assay side to use its column name (ParticipantID) to find the study-side
            // property URI:
            if (col.getName().equalsIgnoreCase(StudyService.get().getSubjectColumnName(dataset.getContainer())))
                propertyNamesToUris.put("ParticipantID", col.getPropertyURI());
            else
                propertyNamesToUris.put(col.getName(), col.getPropertyURI());
        }

        // create a set of all columns that will be required, so we can detect
        // if any of these are new
        Set<String> newPdNames = new TreeSet<>();
        for (Map<String, Object> dataMap : dataMaps)
            newPdNames.addAll(dataMap.keySet());
        if (dataset.getStudy().getTimepointType() != TimepointType.VISIT)  // don't try to create a propertydescriptor for date
            newPdNames.remove("Date");
        newPdNames.remove(AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME);

        Map<String, PropertyDescriptor> typeMap = new HashMap<>();
        for (PropertyDescriptor pd : types)
            typeMap.put(pd.getName(), pd);

        // loop through all new columns, and verify that we have a property already defined:
        int sortOrder = 0;
        boolean changed = false;
        for (String newPdName : newPdNames)
        {
            if (!propertyNamesToUris.containsKey(newPdName))
            {
                // We used to link batch properties with the "Run" prefix - see if we need to rename the target column
                if (!renameRunPropertyToBatch(domain, propertyNamesToUris, newPdNames, newPdName, user))
                {
                    PropertyDescriptor pd = typeMap.get(newPdName);
                    if (pd != null)
                    {
                        DomainProperty newProperty = domain.addProperty();
                        pd.copyTo(newProperty.getPropertyDescriptor());
                        domain.setPropertyIndex(newProperty, sortOrder++);
                        changed = true;
                        propertyNamesToUris.put(newPdName, pd.getPropertyURI());
                    }
                }
            }
        }
        if (keyPropertyName != null && !propertyNamesToUris.containsKey(keyPropertyName))
        {
            // Make sure we have a property for the key field
            DomainProperty newProperty = domain.addProperty();
            newProperty.setName(keyPropertyName);
            newProperty.setPropertyURI(domain.getTypeURI() + "#" + keyPropertyName);
            newProperty.setMeasure(false);
            newProperty.setDimension(false);
            newProperty.setRequired(true);
            newProperty.setRangeURI(PropertyType.INTEGER.getTypeUri());
            domain.setPropertyIndex(newProperty, sortOrder++);
            changed = true;
        }
        if (changed)
        {
            domain.save(user);
        }
        if (keyPropertyName != null)
        {
            propertyNamesToUris.put(keyPropertyName, domain.getPropertyByName(keyPropertyName).getPropertyURI());
        }
        return propertyNamesToUris;
    }

    private boolean renameRunPropertyToBatch(Domain domain, Map<String, String> propertyNamesToUris, Set<String> newPdNames, String newPdName, User user)
            throws ChangePropertyDescriptorException
    {
        if (newPdName.startsWith(AssayService.BATCH_COLUMN_NAME))
        {
            String oldName = "Run" + newPdName.substring(AssayService.BATCH_COLUMN_NAME.length());
            // Check if we don't have a different run-prefixed property to link and and we do have a run-prefixed property in the target domain
            if (!newPdNames.contains(oldName) && propertyNamesToUris.containsKey(oldName))
            {
                String originalURI = propertyNamesToUris.get(oldName);

                for (DomainProperty property : domain.getProperties())
                {
                    if (property.getPropertyURI().equals(originalURI))
                    {
                        // Rename the property, including its URI
                        property.setName(newPdName);
                        property.setLabel(null);
                        property.setPropertyURI(property.getPropertyURI().replace("#Run", "#Batch"));
                        propertyNamesToUris.remove(oldName);
                        propertyNamesToUris.put(newPdName, property.getPropertyURI());
                        domain.save(user);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @NotNull
    public DatasetDefinition createDataset(User user, @NotNull DatasetDefinition.Builder builder)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        try (DbScope.Transaction transaction = schema.getScope().ensureTransaction())
        {
            StudyImpl study = builder.getStudy();
            if (study == null)
                throw new IllegalStateException("A study must be set on the DatasetDefinition.Builder");

            // auto generate a dataset ID
            if (null == builder.getDatasetId())
            {
                // To help avoid datasetid collisions, child studies in a dataspace project try to avoid colliding with project dataset id's.
                // Even better would be if the project datasets also tried to avoid collisions with all other datasets in the entire project folder hierarchy
                var sharedStudy = StudyManager.getInstance().getSharedStudy(study);
                int id = null != sharedStudy ? 10000 : MIN_ASSAY_ID;
                Integer mx = new SqlSelector(schema, "SELECT MAX(datasetid) FROM study.dataset WHERE container=?", study.getContainer().getId()).getObject(Integer.class);
                if (null != mx)
                    id = Math.max(id,mx);
                if (null != sharedStudy)
                {
                    mx = new SqlSelector(schema, "SELECT MAX(datasetid) FROM study.dataset WHERE container=?", sharedStudy.getContainer().getId()).getObject(Integer.class);
                    if (null != mx)
                       id = Math.max(id, mx);
                }
                builder.setDatasetId(id+1);
            }

            DatasetDefinition dsd = builder.build();
            if (dsd.getUseTimeKeyField() && (dsd.isDemographicData() || dsd.getKeyPropertyName() != null))
                throw new IllegalStateException("UseTimeKeyField not compatible with iDemographic or other key field.");

            StudyManager.getInstance().createDatasetDefinition(user, dsd);

            transaction.commit();
            return dsd;
        }
    }

    /**
     * Try to use the assay name for a dataset, but if it's already taken, add an integer suffix until it's unique
     */
    private static String createUniqueDatasetName(Study study, String assayName)
    {
        Set<String> inUseNames = new CaseInsensitiveHashSet();
        for (Dataset def : study.getDatasetsByType(Dataset.TYPE_STANDARD, Dataset.TYPE_PLACEHOLDER))
            inUseNames.add(def.getName());

        int suffix = 1;
        String name = assayName;

        while (inUseNames.contains(name))
        {
            name = assayName + Integer.toString(suffix);
            suffix++;
        }

        return name;
    }

    public UploadLog saveUploadData(User user, Dataset dsd, FileStream tsv, String filename) throws IOException
    {
        PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(dsd.getContainer());
        if (null == pipelineRoot || !pipelineRoot.isValid())
            throw new IOException("Please have your administrator set up a pipeline root for this folder.");

        Path dir = pipelineRoot.resolveToNioPath(AssayFileWriter.DIR_NAME);
        if (null == dir)
            throw new IOException("Cannot create directory uploaded data: " + AssayFileWriter.DIR_NAME);

        if (!Files.exists(dir))
        {
            Files.createDirectory(dir);
        }

        //File name is studyname_datasetname_date_hhmm.ss
        Date dateCreated = new Date();
        String dateString = DateUtil.formatDateTime(dateCreated, "yyy-MM-dd-HHmm");
        int id = 0;
        Path file;
        do
        {
            String extension = StringUtils.defaultString(filename == null ? "tsv" : FileUtil.getExtension(filename), "tsv");
            String extra = id++ == 0 ? "" : String.valueOf(id);
            String fileName = dsd.getStudy().getLabel() + "-" + dsd.getLabel() + "-" + dateString + extra + "." + extension;
            fileName = fileName.replace('\\', '_').replace('/', '_').replace(':', '_');
            file = dir.resolve(fileName);
        }
        while (Files.exists(file));

        try (OutputStream out = Files.newOutputStream(file))
        {
            IOUtils.copy(tsv.openInputStream(), out);
            tsv.closeInputStream();
        }

        UploadLog ul = new UploadLog();
        ul.setContainer(dsd.getContainer());
        ul.setDatasetId(dsd.getDatasetId());
        ul.setCreated(dateCreated);
        ul.setUserId(user.getUserId());
        ul.setStatus("Initializing");
        String filePath = FileUtil.hasCloudScheme(file) ? FileUtil.pathToString(file) : file.toFile().getPath();
        ul.setFilePath(filePath);

        return Table.insert(user, getTinfoUpdateLog(), ul);
    }


    /**
     * Return an array of LSIDs from the newly created dataset entries,
     * along with the upload log.
     */
    public Pair<List<String>, UploadLog> importDatasetTSV(User user, StudyImpl study, DatasetDefinition dsd, DataLoader dl, boolean importLookupByAlternateKey, FileStream fileIn, String originalFileName, Map<String, String> columnMap, BatchValidationException errors, QueryUpdateService.InsertOption insertOption, @Nullable AuditBehaviorType auditBehaviorType)
    {
        DbScope scope = StudySchema.getInstance().getScope();

        UploadLog ul = null;
        List<String> lsids = Collections.emptyList();

        try
        {
            if (null != fileIn)
                ul = saveUploadData(user, dsd, fileIn, originalFileName);

            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                Integer defaultQCStateId = study.getDefaultDirectEntryQCState();
                DataState defaultQCState = null;
                if (defaultQCStateId != null)
                    defaultQCState = DataStateManager.getInstance().getStateForRowId(study.getContainer(), defaultQCStateId.intValue());
                lsids = StudyManager.getInstance().importDatasetData(user, dsd, dl, columnMap, errors, DatasetDefinition.CheckForDuplicates.sourceOnly,
                        defaultQCState, insertOption, null, importLookupByAlternateKey, auditBehaviorType);
                if (!errors.hasErrors())
                    transaction.commit();
            }

            if (!errors.hasErrors())
                StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(user, Collections.singleton(dsd));
        }
        catch (IOException x)
        {
            errors.addRowError(new ValidationException("Exception: " + x.getMessage()));
            if (ul != null)
            {
                ul.setStatus("ERROR");
                String description = ul.getDescription();
                ul.setDescription(description == null ? "" : description + "\n" + new Date() + ":" + x.getMessage());
                ul = Table.update(user, StudySchema.getInstance().getTableInfoUploadLog(), ul, ul.getRowId());
                return Pair.of(lsids, ul);
            }
        }

        if (!errors.hasErrors())
        {
            //Update the status
            assert ul != null : "Upload log should always exist if no errors have occurred.";
            ul.setStatus("SUCCESS");
            ul = Table.update(user, getTinfoUpdateLog(), ul, ul.getRowId());
        }
        else if (ul != null)
        {
            ul.setStatus("ERROR");
            StringBuilder sb = new StringBuilder();
            String sep = "";
            for (ValidationException e : errors.getRowErrors())
            {
                sb.append(sep).append(e.getMessage());
                sep = "\n";
            }
            ul.setDescription(sb.toString());
            ul = Table.update(user, getTinfoUpdateLog(), ul, ul.getRowId());
        }
        return Pair.of(lsids, ul);
    }

    public UploadLog getUploadLog(Container c, int id)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("rowId"), id);

        return new TableSelector(getTinfoUpdateLog(), filter, null).getObject(UploadLog.class);
    }

    @Override
    public ActionURL getPublishHistory(Container c, Dataset.PublishSource source, Integer publishSourceId)
    {
        return getPublishHistory(c, source, publishSourceId, null);
    }

    @Override
    public ActionURL getPublishHistory(Container container, Dataset.PublishSource source, Integer publishSourceId, ContainerFilter containerFilter)
    {
        if (source != null && publishSourceId != null)
        {
            switch (source)
            {
                case Assay -> {
                    ActionURL url = new ActionURL(PublishController.PublishAssayHistoryAction.class, container).addParameter("rowId", publishSourceId);
                    if (containerFilter != null && containerFilter.getType() != null)
                        url.addParameter("containerFilterName", containerFilter.getType().name());
                    return url;
                }

                case SampleType -> {
                    ActionURL url = new ActionURL(PublishController.PublishSampleTypeHistoryAction.class, container).addParameter("rowId", publishSourceId);
                    if (containerFilter != null && containerFilter.getType() != null)
                        url.addParameter("containerFilterName", containerFilter.getType().name());
                    return url;
                }

                default -> throw new IllegalArgumentException("No publish history view for : " + source);
            }
        }

        throw new NotFoundException("Specified publish source is invalid");
    }

    @Override
    public TimepointType getTimepointType(Container container)
    {
        Study study = StudyManager.getInstance().getStudy(container);
        if (null == study)
            throw new IllegalArgumentException("No study in container: " + container.getPath());

        return study.getTimepointType();
    }

    @Override
    public boolean hasMismatchedInfo(List<Integer> dataRowPKs, AssayProtocolSchema schema)
    {
        TableInfo tableInfo = schema.createDataTable(null);
        if (tableInfo == null)
            return false;

        AssayTableMetadata tableMetadata = schema.getProvider().getTableMetadata(schema.getProtocol());

        // Try to find the column that tells us if the specimen matches
        FieldKey matchFieldKey = new FieldKey(tableMetadata.getSpecimenIDFieldKey(), AbstractAssayProvider.ASSAY_SPECIMEN_MATCH_COLUMN_NAME);
        Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(tableInfo, Collections.singleton(matchFieldKey));
        ColumnInfo matchColumn = columns.get(matchFieldKey);
        if (matchColumn == null)
        {
            // Couldn't find it, so there's no use in trying to reset to the study's version of the specimen data
            return false;
        }

        // Check if there are any rows that have been selected to link that have specimen data that doesn't match
        // the target study
        SimpleFilter filter = new SimpleFilter(matchFieldKey, false);
        filter.addClause(new SimpleFilter.InClause(tableMetadata.getResultRowIdFieldKey(), dataRowPKs));
        return new TableSelector(tableInfo, filter, null).exists();
    }

    /** Automatically link assay data to a study if the design is set up to do so */
    @Override
    @Nullable
    public ActionURL autoLinkAssayResults(ExpProtocol protocol, ExpRun run, User user, Container container, List<String> errors)
    {
        LOG.debug("Considering whether to attempt auto-link results from assay run " + run.getName() + " from container " + container.getPath());
        AssayProvider provider = AssayService.get().getProvider(protocol);
        if (protocol.getObjectProperties().get(StudyPublishService.AUTO_LINK_TARGET_PROPERTY_URI) != null)
        {
            // First, track down the target study
            String targetStudyContainerId = protocol.getObjectProperties().get(StudyPublishService.AUTO_LINK_TARGET_PROPERTY_URI).getStringValue();
            if (targetStudyContainerId != null)
            {
                LOG.debug("Found configured target study container ID, " + targetStudyContainerId + " for auto-linking with " + run.getName() + " from container " + container.getPath());
                final Container targetStudyContainer = ContainerManager.getForId(targetStudyContainerId);

                // Determine if the category is predefined
                String categoryName = null;
                if (protocol.getObjectProperties().get(StudyPublishService.AUTO_LINK_CATEGORY_PROPERTY_URI) != null)
                {
                    categoryName = protocol.getObjectProperties().get(StudyPublishService.AUTO_LINK_CATEGORY_PROPERTY_URI).getStringValue();
                    LOG.debug("Obtained predefined Dataset Category to assign, " + categoryName);
                }

                return autoLinkResults(protocol, provider, run, user, container, targetStudyContainer, categoryName, errors, LOG);
            }
        }

        return null;
    }

    /**
     * In the sample derivation case, fields from the parent may contain subject or timepoint information that can be used to
     * automatically link the rows to the configured study. We'll need to use the query view to pull any special column values
     * out since these may be lineage display columns.
     *
     * @param sampleType - the sample type to link
     * @param keys - the list of sample row IDs to link to the configured study
     * @throws SQLException
     * @throws IOException
     */
    @Override
    public void autoLinkDerivedSamples(ExpSampleType sampleType, List<Integer> keys, Container container, User user) throws ExperimentException
    {
        if (sampleType != null && sampleType.getAutoLinkTargetContainer() != null)
        {
            // attempt to auto link the results
            QuerySettings qs = new QuerySettings(new MutablePropertyValues(), QueryView.DATAREGIONNAME_DEFAULT);
            qs.setSchemaName(SamplesSchema.SCHEMA_NAME);
            qs.setQueryName(sampleType.getName());
            qs.setBaseFilter(new SimpleFilter().addInClause(FieldKey.fromParts("RowId"), keys));

            Map<StudyPublishService.LinkToStudyKeys, FieldKey> fieldKeyMap = StudyPublishService.get().getSamplePublishFieldKeys(user, container, sampleType, qs);
            UserSchema userSchema = QueryService.get().getUserSchema(user, container, SamplesSchema.SCHEMA_NAME);
            QueryView view = new QueryView(userSchema, qs, null);

            DataView dataView = view.createDataView();
            RenderContext ctx = dataView.getRenderContext();
            Map<FieldKey, ColumnInfo> selectColumns = dataView.getDataRegion().getSelectColumns();
            List<Map<FieldKey, Object>> rows = new ArrayList<>();

            try (Results rs = view.getResults())
            {
                ctx.setResults(rs);
                ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);
                while (rs.next())
                {
                    Map<FieldKey, Object> row = new HashMap<>();
                    ctx.setRow(factory.getRowMap(rs));

                    getColumnValue(fieldKeyMap.get(StudyPublishService.LinkToStudyKeys.ParticipantId), ctx, selectColumns, row);
                    getColumnValue(fieldKeyMap.get(StudyPublishService.LinkToStudyKeys.VisitId), ctx, selectColumns, row);
                    getColumnValue(fieldKeyMap.get(StudyPublishService.LinkToStudyKeys.Date), ctx, selectColumns, row);
                    getColumnValue(FieldKey.fromParts(StudyPublishService.ROWID_PROPERTY_NAME), ctx, selectColumns, row);

                    rows.add(row);
                }
            }
            catch (Exception e)
            {
                throw new ExperimentException(e);
            }

            if (!rows.isEmpty())
                autoLinkSamples(sampleType, rows, container, user);
        }
    }

    private void getColumnValue(@Nullable FieldKey fieldKey, RenderContext ctx, Map<FieldKey, ColumnInfo> selectColumns, Map<FieldKey, Object> row)
    {
        if (fieldKey != null && selectColumns.containsKey(fieldKey))
        {
            var col = selectColumns.get(fieldKey);
            Object val = PublishResultsQueryView.getColumnValue(col, ctx);

            row.put(fieldKey, val);
        }
    }

    @Override
    public void autoLinkSamples(ExpSampleType sampleType, List<Map<FieldKey, Object>> results, Container container, User user)
    {
        LOG.debug(String.format("Considering whether to attempt auto-link results for row insert to %s from container %s", sampleType.getName(), container.getPath()));

        Container targetContainer = sampleType.getAutoLinkTargetContainer();
        List<String> publishErrors = new ArrayList<>();
        if (targetContainer != null)
        {
            // the below configuration translates to the current folder
            if (targetContainer.equals(StudyPublishService.AUTO_LINK_TARGET_IMPORT_FOLDER))
                targetContainer = container;

            Study study = StudyService.get().getStudy(targetContainer);
            String targetContainerPath = targetContainer.getPath();
            if (study != null)
            {
                String sampleTypeName = sampleType.getName();
                String containerPath = container.getPath();

                LOG.debug(String.format("Found configured target study container ID, %s for auto-linking with %s from container %s", study.getShortName(), sampleTypeName, containerPath));

                Set<Study> validStudies = StudyPublishService.get().getValidPublishTargets(user, InsertPermission.class);
                if (validStudies.contains(study))
                {
                    LOG.debug(String.format("Resolved target study in container %s for auto-linking with %s from container %s", targetContainerPath, sampleTypeName, containerPath));
                    List<Map<String, Object>> dataMaps = new ArrayList<>();

                    // attempt to match up the subject/timepoint information even if the sample has not been published to
                    // a study yet, this includes traversing any parent lineage samples for corresponding information
                    QuerySettings qs = new QuerySettings(new MutablePropertyValues(), QueryView.DATAREGIONNAME_DEFAULT);
                    qs.setSchemaName(SamplesSchema.SCHEMA_NAME);
                    qs.setQueryName(sampleType.getName());

                    Map<LinkToStudyKeys, FieldKey> publishKeys = StudyPublishService.get().getSamplePublishFieldKeys(user, container, sampleType, qs);
                    final boolean visitBased = study.getTimepointType().isVisitBased();
                    LinkToStudyKeys timePointKey = visitBased ? LinkToStudyKeys.VisitId : LinkToStudyKeys.Date;

                    // the schema supports the subject/timepoint fields
                    if (publishKeys.containsKey(LinkToStudyKeys.ParticipantId) && publishKeys.containsKey(timePointKey))
                    {
                        FieldKey timePointFieldKey = publishKeys.get(timePointKey);
                        String timePointPropName = visitBased ? StudyPublishService.SEQUENCENUM_PROPERTY_NAME : StudyPublishService.DATE_PROPERTY_NAME;

                        for (Map<FieldKey, Object> row : results)
                        {
                            if (row.containsKey(publishKeys.get(LinkToStudyKeys.ParticipantId)) && row.containsKey(timePointFieldKey))
                            {
                                // Issue : 13647 - handle conversion for timepoint field
                                Object timePointValue = visitBased
                                        ? Float.parseFloat(String.valueOf(row.get(timePointFieldKey)))
                                        : ConvertUtils.convert(String.valueOf(row.get(timePointFieldKey)), Date.class);

                                dataMaps.add(Map.of(
                                        LinkToStudyKeys.ParticipantId.name(), row.get(publishKeys.get(LinkToStudyKeys.ParticipantId)),
                                        timePointPropName, timePointValue,
                                        StudyPublishService.ROWID_PROPERTY_NAME, row.get(FieldKey.fromParts(StudyPublishService.ROWID_PROPERTY_NAME)),
                                        StudyPublishService.SOURCE_LSID_PROPERTY_NAME, sampleType.getLSID()
                                ));
                            }
                        }
                    }

                    StudyPublishService.get().publishData(
                        user,
                        container,
                        targetContainer,
                        sampleType.getAutoLinkCategory(),
                        sampleTypeName,
                        Pair.of(Dataset.PublishSource.SampleType, sampleType.getRowId()),
                        dataMaps,
                        ExpMaterialTable.Column.RowId.toString(),
                        publishErrors
                    );
                }
                else
                {
                    LOG.error("Insufficient permission to link assay data to study in folder : " + targetContainerPath);
                }
            }
            else
            {
                LOG.info("Unable to link the assay data, there is no study in the folder: " + targetContainerPath);
            }
        }
    }


    @Nullable
    public ActionURL autoLinkResults(ExpProtocol protocol, AssayProvider provider, ExpRun run, User user, Container container,
                                     Container targetStudyContainer, @Nullable String datasetCategory, List<String> errors, Logger log)
    {
        if (targetStudyContainer != null)
        {
            if (targetStudyContainer.equals(StudyPublishService.AUTO_LINK_TARGET_IMPORT_FOLDER))
            {
                // this configuration translates to the current folder
                targetStudyContainer = container;
            }

            final StudyImpl study = StudyManager.getInstance().getStudy(targetStudyContainer);
            if (study != null)
            {
                boolean hasPermission = false;
                Set<Study> publishTargets = getValidPublishTargets(user, InsertPermission.class);
                for (Study publishTarget : publishTargets)
                {
                    if (publishTarget.getContainer().equals(targetStudyContainer))
                    {
                        hasPermission = true;
                        break;
                    }
                }
                if (!hasPermission)
                {
                    // We don't have permission to create or add to
                    log.error("Insufficient permission to link assay data to study in folder : " + targetStudyContainer.getPath());
                    return null;
                }

                log.debug("Resolved target study in container " + targetStudyContainer.getPath() + " for auto-linking with " + run.getName() + " from container " + container.getPath());

                FieldKey ptidFK = provider.getTableMetadata(protocol).getParticipantIDFieldKey();
                FieldKey visitFK = provider.getTableMetadata(protocol).getVisitIDFieldKey(study.getTimepointType());
                FieldKey objectIdFK = provider.getTableMetadata(protocol).getResultRowIdFieldKey();
                FieldKey runFK = provider.getTableMetadata(protocol).getRunRowIdFieldKeyFromResults();

                AssayProtocolSchema schema = provider.createProtocolSchema(user, container, protocol, null);

                // Do a query to get all the info we need to do the link
                TableInfo resultTable = schema.createDataTable(null, false);

                // Check if we can resolve the PTID column by name. See issue 32281
                if (resultTable.getColumn(ptidFK) == null)
                {
                    for (ColumnInfo c : resultTable.getColumns())
                    {
                        // Check for a column with the PTID concept URI instead
                        if (org.labkey.api.gwt.client.ui.PropertyType.PARTICIPANT_CONCEPT_URI.equals(c.getConceptURI()))
                        {
                            ptidFK = c.getFieldKey();
                        }
                    }
                }

                Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(resultTable, Arrays.asList(ptidFK, visitFK, objectIdFK, runFK));
                final ColumnInfo ptidColumn = cols.get(ptidFK);
                final ColumnInfo visitColumn = cols.get(visitFK);
                final ColumnInfo objectIdColumn = cols.get(objectIdFK);
                final Map<Integer, PublishKey> keys = new HashMap<>();
                assert cols.get(runFK) != null : "Could not find object id column: " + objectIdFK;

                SQLFragment sql = QueryService.get().getSelectSQL(resultTable, cols.values(), new SimpleFilter(runFK, run.getRowId()), null, Table.ALL_ROWS, Table.NO_OFFSET, false);

                Container targetContainer = targetStudyContainer;
                new SqlSelector(resultTable.getSchema(), sql).forEach(rs -> {
                    // Be careful to not assume that we have participant or visit columns in our data domain
                    Object ptidObject = ptidColumn == null ? null : ptidColumn.getValue(rs);
                    String ptid = ptidObject == null ? null : ptidObject.toString();
                    int objectId = ((Number) objectIdColumn.getValue(rs)).intValue();
                    Object visit = visitColumn == null ? null : visitColumn.getValue(rs);
                    // Only link rows that have a participant and a visit/date
                    if (ptid != null && visit != null)
                    {
                        PublishKey key;
                        // 13647: Conversion exception in assay auto link to study
                        if (study.getTimepointType().isVisitBased())
                        {
                            float visitId = Float.parseFloat(visit.toString());
                            key = new PublishKey(targetContainer, ptid, visitId, objectId);
                            log.debug("Resolved info (" + ptid + "/" + visitId + ") for auto-linking of row " + objectId + " for " + run.getName() + " from container " + container.getPath());
                        }
                        else
                        {
                            Date date = (Date) ConvertUtils.convert(visit.toString(), Date.class);
                            key = new PublishKey(targetContainer, ptid, date, objectId);
                            log.debug("Resolved info (" + ptid + "/" + date + ") for auto-linking of row " + objectId + " for " + run.getName() + " from container " + container.getPath());
                        }
                        keys.put(objectId, key);
                    }
                    else
                    {
                        log.debug("Missing ptid and/or visit info for auto-linking of row " + objectId + " for " + run.getName() + " from container " + container.getPath());
                    }
                });

                log.debug("Identified " + keys + " rows with sufficient data to link to " + targetStudyContainer.getPath() + " for auto-linking with " + run.getName() + " from container " + container.getPath());
                return provider.linkToStudy(user, container, protocol, targetStudyContainer, datasetCategory, keys, errors);
            }
            else
                log.info("Unable to link the assay data, there is no study in the folder: " + targetStudyContainer.getPath());
        }
        return null;
    }

    @Override
    public ExpProtocol ensureStudyPublishProtocol(User user) throws ExperimentException
    {
        String protocolName = STUDY_PUBLISH_PROTOCOL_NAME;
        String protocolLsid = STUDY_PUBLISH_PROTOCOL_LSID;
        ExpProtocol protocol = ExperimentService.get().getExpProtocol(protocolLsid);

        if (protocol == null)
        {
            ExpProtocol baseProtocol = ExperimentService.get().createExpProtocol(ContainerManager.getSharedContainer(), ExpProtocol.ApplicationType.ExperimentRun, protocolName);
            baseProtocol.setLSID(protocolLsid);
            baseProtocol.setMaxInputMaterialPerInstance(0);
            baseProtocol.setProtocolDescription("Simple protocol for publishing study using link to study.");
            return ExperimentService.get().insertSimpleProtocol(baseProtocol, user);
        }
        return protocol;
    }

    @Override
    public Set<DatasetDefinition> getDatasetsForPublishSource(Integer publishSourceId, Dataset.PublishSource publishSource)
    {
        TableInfo datasetTable = StudySchema.getInstance().getTableInfoDataset();
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("publishSourceId"), publishSourceId);
        filter.addCondition(FieldKey.fromParts("publishSourceType"), publishSource.name());

        Set<DatasetDefinition> result = new HashSet<>();
        Collection<Map<String, Object>> rows = new TableSelector(datasetTable, new CsvSet("container,datasetid"), filter, null).getMapCollection();
        for (Map<String, Object> row : rows)
        {
            String containerId = (String) row.get("container");
            int datasetId = ((Number) row.get("datasetid")).intValue();
            Container container = ContainerManager.getForId(containerId);
            result.add(StudyServiceImpl.INSTANCE.getDataset(container, datasetId));
        }
        return result;
    }

    @Override
    public Set<Dataset> getDatasetsForAssayRuns(Collection<ExpRun> runs, User user)
    {
        // Cache the datasets for a specific protocol (assay design)
        Map<ExpProtocol, Set<DatasetDefinition>> protocolDatasets = new HashMap<>();
        // Remember all of the run RowIds for a given protocol (assay design)
        Map<ExpProtocol, List<Integer>> allProtocolRunIds = new HashMap<>();

        // Go through the runs and figure out what protocols they belong to, and what datasets they could have been linked to
        for (ExpRun run : runs)
        {
            ExpProtocol protocol = run.getProtocol();
            Set<DatasetDefinition> datasets = protocolDatasets.get(protocol);
            if (datasets == null)
            {
                datasets = StudyPublishManager.getInstance().getDatasetsForPublishSource(protocol.getRowId(), Dataset.PublishSource.Assay);
                protocolDatasets.put(protocol, datasets);
            }
            List<Integer> protocolRunIds = allProtocolRunIds.get(protocol);
            if (protocolRunIds == null)
            {
                protocolRunIds = new ArrayList<>();
                allProtocolRunIds.put(protocol, protocolRunIds);
            }
            protocolRunIds.add(run.getRowId());
        }

        // All of the datasets that have rows backed by data in the specified runs
        Set<Dataset> result = new HashSet<>();

        for (Map.Entry<ExpProtocol, Set<DatasetDefinition>> entry : protocolDatasets.entrySet())
        {
            for (DatasetDefinition dataset : entry.getValue())
            {
                // Don't enforce permissions for the current user - we still want to tell them if the data
                // has been linked even if they can't see the dataset.
                UserSchema schema = StudyQuerySchema.createSchema(dataset.getStudy(), user, RoleManager.getRole(ReaderRole.class));
                TableInfo tableInfo = schema.getTable(dataset.getName());
                AssayProvider provider = AssayService.get().getProvider(entry.getKey());
                if (provider != null)
                {
                    AssayTableMetadata tableMetadata = provider.getTableMetadata(entry.getKey());
                    SimpleFilter filter = new SimpleFilter();
                    filter.addInClause(tableMetadata.getRunRowIdFieldKeyFromResults(), allProtocolRunIds.get(entry.getKey()));
                    if (new TableSelector(tableInfo, filter, null).exists())
                    {
                        result.add(dataset);
                    }
                }
            }
        }

        return result;
    }

    @Override
    public String checkForLockedLinks(Dataset def, @Nullable  List<Integer> rowIds)
    {
        Dataset.PublishSource sourceType = def.getPublishSource();
        if (sourceType != null)
        {
            if (sourceType == Dataset.PublishSource.SampleType && rowIds != null)
            {
                List<? extends ExpMaterial> samples = ExperimentService.get().getExpMaterials(rowIds);
                SampleTypeService sampleService = SampleTypeService.get();
                Collection<? extends ExpMaterial> lockedSamples = sampleService.getSamplesNotPermitted(samples, SampleTypeService.SampleOperations.RecallFromStudy);
                if (!lockedSamples.isEmpty())
                    return sampleService.getOperationNotPermittedMessage(lockedSamples, SampleTypeService.SampleOperations.RecallFromStudy);
            }
        }
        return null;
    }

    @Override
    public void addRecallAuditEvent(Container sourceContainer, User user, Dataset def, int rowCount, @Nullable Collection<Pair<String,Integer>> pairs)
    {
        Dataset.PublishSource sourceType = def.getPublishSource();
        if (sourceType != null)
        {
            ExpObject source = def.resolvePublishSource();

            String sourceName = def.getLabel();
            if (source != null)
                sourceName = source.getName();

            String auditMessage = sourceType.getRecallFromStudyAuditMessage(sourceName, rowCount);
            PublishAuditProvider.AuditEvent event = new PublishAuditProvider.AuditEvent(sourceContainer.getId(), auditMessage, sourceType, source, null);

            event.setTargetStudy(def.getStudy().getContainer().getId());
            event.setDatasetId(def.getDatasetId());
            event.setRecordCount(rowCount);

            AuditLogService.get().addEvent(user, event);

            // Create sample timeline event for each of the samples
            if (sourceType == Dataset.PublishSource.SampleType && pairs != null)
            {
                var timelineEventType = SampleTimelineAuditEvent.SampleTimelineEventType.RECALL;
                Map<String, Object> eventMetadata = new HashMap<>();
                eventMetadata.put(SAMPLE_TIMELINE_EVENT_TYPE, timelineEventType.name());
                String metadata = AbstractAuditTypeProvider.encodeForDataMap(sourceContainer, eventMetadata);

                List<Integer> sampleIds = pairs.stream().map(Pair::getValue).collect(toList());
                List<? extends ExpMaterial> samples = ExperimentService.get().getExpMaterials(sampleIds);
                List<AuditTypeEvent> events = new ArrayList<>(samples.size());
                for (ExpMaterial sample : samples)
                {
                    int sampleId = sample.getRowId();
                    String sampleName = sample.getName();
                    String sampleLsid = sample.getLSID();

                    SampleTimelineAuditEvent timelineEvent = new SampleTimelineAuditEvent(sourceContainer.getId(), timelineEventType.getComment());
                    timelineEvent.setSampleType(sourceName);
                    if (source != null)
                        timelineEvent.setSampleTypeId(source.getRowId());
                    timelineEvent.setSampleId(sampleId);
                    timelineEvent.setSampleName(sampleName);
                    timelineEvent.setSampleLsid(sampleLsid);

                    timelineEvent.setMetadata(metadata);
                    timelineEvent.setLineageUpdate(false);
                    events.add(timelineEvent);
                }

                AuditLogService.get().addEvents(user, events);
            }
        }
    }

    /**
     * Transform an illegal name into a safe version. All non-letter characters
     * become underscores, and the first character must be a letter. Retain this implementation for backwards
     * compatibility with linked to study column names. See issue 41030.
     */
    private String sanitizeName(String originalName)
    {
        StringBuilder sb = new StringBuilder();
        boolean first = true; // first character is special
        for (int i = 0; i < originalName.length(); i++)
        {
            char c = originalName.charAt(i);
            if (AliasManager.isLegalNameChar(c, first))
            {
                sb.append(c);
                first = false;
            }
            else if (!first)
            {
                sb.append('_');
            }
        }
        return sb.toString();
    }

    @Override
    public Set<String> addLinkedToStudyColumns(AbstractTableInfo table, Dataset.PublishSource publishSource, boolean setVisibleColumns, int rowId, String rowIdName, User user)
    {
        Set<String> visibleColumnNames = new HashSet<>();
        StudyService svc = StudyService.get();

        if (null != svc)
        {
            int datasetIndex = 0;
            Set<String> usedColumnNames = new HashSet<>();

            for (final Dataset assayDataset : StudyPublishService.get().getDatasetsForPublishSource(rowId, publishSource))
            {
                if (!assayDataset.getContainer().hasPermission(user, ReadPermission.class) || !assayDataset.canRead(user))
                {
                    continue;
                }

                String datasetIdColumnName = "dataset" + datasetIndex++;

                final StudyDatasetLinkedColumn datasetColumn = new StudyDatasetLinkedColumn(table, datasetIdColumnName, assayDataset, rowIdName, user);
                datasetColumn.setHidden(true);
                datasetColumn.setUserEditable(false);
                datasetColumn.setShownInInsertView(false);
                datasetColumn.setShownInUpdateView(false);
                datasetColumn.setReadOnly(true);
                table.addColumn(datasetColumn);

                String studyLinkedSql = "(SELECT CASE WHEN " + datasetColumn.getDatasetIdAlias() +
                        "._key IS NOT NULL THEN 'linked' ELSE NULL END)";

                String studyName = assayDataset.getStudy().getLabel();
                if (studyName == null)
                    continue; // No study in that folder

                String studyColumnName;
                String sanitizedStudyName = sanitizeName(studyName);
                if (sanitizedStudyName.isEmpty() || "study".equalsIgnoreCase(sanitizedStudyName))
                {
                    // issue 41472 include the prefix as part of the sanitization process
                    studyColumnName = sanitizeName("linked_to_" + studyName);
                }
                else
                    studyColumnName = "linked_to_" + sanitizeName(studyName);

                // column names must be unique. Prevent collisions
                while (usedColumnNames.contains(studyColumnName))
                    studyColumnName = studyColumnName + datasetIndex;
                usedColumnNames.add(studyColumnName);

                final ExprColumn studyLinkedColumn = new ExprColumn(table,
                        studyColumnName,
                        new SQLFragment(studyLinkedSql),
                        JdbcType.VARCHAR,
                        datasetColumn);
                final String linkedToStudyColumnCaption = "Linked to " + studyName;
                studyLinkedColumn.setLabel(linkedToStudyColumnCaption);
                studyLinkedColumn.setUserEditable(false);
                studyLinkedColumn.setReadOnly(true);
                studyLinkedColumn.setShownInInsertView(false);
                studyLinkedColumn.setShownInUpdateView(false);
                studyLinkedColumn.setURL(StringExpressionFactory.createURL(StudyService.get().getDatasetURL(assayDataset.getContainer(), assayDataset.getDatasetId())));

                table.addColumn(studyLinkedColumn);

                // Issue 42937: limit default visible columns to 3 for a given assay protocol
                if (datasetIndex > 3)
                    visibleColumnNames.clear();
                else
                    visibleColumnNames.add(studyLinkedColumn.getName());
            }
            if (setVisibleColumns && visibleColumnNames.size() > 0)
            {
                List<FieldKey> visibleColumns = new ArrayList<>(table.getDefaultVisibleColumns());
                for (String columnName : visibleColumnNames)
                {
                    visibleColumns.add(new FieldKey(null, columnName));
                }
                table.setDefaultVisibleColumns(visibleColumns);
            }
        }

        return visibleColumnNames;
    }

    @Override
    public Map<LinkToStudyKeys, FieldKey> getSamplePublishFieldKeys(User user, Container container,
                                                                    ExpSampleType sampleType,
                                                                    @Nullable QuerySettings qs)
    {
        Map<LinkToStudyKeys, FieldKey> fieldKeyMap = new HashMap<>();
        if (sampleType != null)
        {
            UserSchema userSchema = QueryService.get().getUserSchema(user, container, SamplesSchema.SCHEMA_NAME);
            TableInfo tableInfo = userSchema.getTable(sampleType.getName());
            if (tableInfo == null)
                throw new IllegalStateException(String.format("Sample Type %s not found", sampleType.getName()));

            Map<FieldKey, ColumnInfo> columns = tableInfo.getColumns().stream()
                    .collect(LabKeyCollectors.toLinkedMap(ColumnInfo::getFieldKey, c -> c));

            // optionally add columns added through a view, useful for picking up any lineage fields
            if (qs != null)
            {
                QueryView view = new QueryView(userSchema, qs, null);
                // Issue 45238 - configure as API style invocation to skip setting up buttons and other items that
                // rely on being invoked inside an HTTP request/ViewContext
                view.setApiResponseView(true);
                DataView dataView = view.createDataView();
                for (Map.Entry<FieldKey, ColumnInfo> entry : dataView.getDataRegion().getSelectColumns().entrySet())
                {
                    if (!columns.containsKey(entry.getKey()))
                        columns.put(entry.getKey(), entry.getValue());
                }
            }

            for (ColumnInfo ci : columns.values())
            {
                ColumnInfo col = ci;
                DisplayColumn dc = col.getRenderer();

                // hack to pull in lineage concept URI info, because the metadata doesn't get propagated
                // to the actual lookup column
                if (dc instanceof ILineageDisplayColumn)
                {
                    col = ((ILineageDisplayColumn) dc).getInnerBoundColumn();
                }

                if (org.labkey.api.gwt.client.ui.PropertyType.VISIT_CONCEPT_URI.equalsIgnoreCase(col.getConceptURI()))
                {
                    if (!fieldKeyMap.containsKey(LinkToStudyKeys.VisitId) && col.getJdbcType().isReal())
                        fieldKeyMap.put(LinkToStudyKeys.VisitId, ci.getFieldKey());
                    if (!fieldKeyMap.containsKey(LinkToStudyKeys.Date) && col.getJdbcType().isDateOrTime())
                        fieldKeyMap.put(LinkToStudyKeys.Date, ci.getFieldKey());
                }

                if (!fieldKeyMap.containsKey(LinkToStudyKeys.ParticipantId) && org.labkey.api.gwt.client.ui.PropertyType.PARTICIPANT_CONCEPT_URI.equalsIgnoreCase(col.getConceptURI()))
                {
                    fieldKeyMap.put(LinkToStudyKeys.ParticipantId, ci.getFieldKey());
                }
            }
        }
        return fieldKeyMap;
    }
}

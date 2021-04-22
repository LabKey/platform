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
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
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
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.qc.QCState;
import org.labkey.api.qc.QCStateManager;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyEntity;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.publish.PublishKey;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.study.StudySchema;
import org.labkey.study.StudyServiceImpl;
import org.labkey.study.assay.query.AssayAuditProvider;
import org.labkey.study.controllers.PublishController;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.DatasetDomainKind;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.UploadLog;
import org.labkey.study.query.StudyQuerySchema;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.labkey.api.audit.SampleTimelineAuditEvent.SAMPLE_TIMELINE_EVENT_TYPE;
import static org.labkey.study.query.DatasetTableImpl.SOURCE_ROW_LSID;

/**
 * Manages the copy-to-study operation that links assay rows into datasets in the target study, creating the dataset
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
                errors.add("Unable to copy to study: duplicate column \"" + targetPd.getName() + "\" detected in the assay design.  Please contact an administrator.");
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
    public void checkForAlreadyCopiedRows(User user, Pair<Dataset.PublishSource, Integer> publishSource,
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
                    // Check to see if it already has the data rows that are being copied
                    TableInfo tableInfo = dataset.getTableInfo(user, false);
                    Filter datasetFilter = new SimpleFilter(new SimpleFilter.InClause(FieldKey.fromParts(dataset.getKeyPropertyName()), entry.getValue()));
                    long existingRowCount = new TableSelector(tableInfo, datasetFilter, null).getRowCount();
                    if (existingRowCount > 0)
                    {
                        // If so, don't let the user copy them again, even if they have different participant/visit/date info
                        String errorMessage = existingRowCount == 1 ? "One of the selected rows has" : (existingRowCount + " of the selected rows have");
                        errorMessage += " already been copied to the study \"" + targetStudy.getLabel() + "\" in " + entry.getKey().getPath();
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
    public ActionURL publishData(User user, Container sourceContainer, @Nullable Container targetContainer, String sourceName,
                                 Pair<Dataset.PublishSource, Integer> publishSource,
                                 List<Map<String, Object>> dataMaps, String keyPropertyName, List<String> errors)
    {
        return publishData(user, sourceContainer, targetContainer, sourceName, publishSource, dataMaps, Collections.emptyList(), keyPropertyName, errors);
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

        // CONSIDER: transact all copies together
        // CONSIDER: returning list of URLS along with a count of rows copied to each study.
        ActionURL url = null;
        for (Map.Entry<Container, List<Map<String, Object>>> entry : partitionedDataMaps.entrySet())
        {
            Container targetStudy = entry.getKey();
            List<Map<String, Object>> maps = entry.getValue();
            url = _publishData(user, sourceContainer, targetStudy, sourceName, publishSource, maps, columns, keyPropertyName, errors);
        }
        return url;
    }

    private ActionURL _publishData(User user, Container sourceContainer, @NotNull Container targetContainer, String sourceName,
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
                dataset = createDataset(user, new DatasetDefinition.Builder(createUniqueDatasetName(targetStudy, sourceName))
                        .setStudy(targetStudy)
                        .setKeyPropertyName(keyPropertyName)
                        .setPublishSourceId(publishSource.second)
                        .setPublishSource(publishSource.first));
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
            QCState defaultQCState = null;
            if (defaultQCStateId != null)
                defaultQCState = QCStateManager.getInstance().getQCStateForRowId(targetContainer, defaultQCStateId.intValue());

            datasetLsids = StudyManager.getInstance().importDatasetData(user, dataset, convertedDataMaps, errors, DatasetDefinition.CheckForDuplicates.sourceAndDestination, defaultQCState, null, false);

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
        catch (ChangePropertyDescriptorException e)
        {
            throw new UnexpectedException(e);
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
                ExpSampleType sampleType = (ExpSampleType)source;
                createProvenanceRun(user, targetContainer, sampleType, errors, dataset, datasetLsids);
            }
            case Assay -> {
                ExpProtocol protocol = (ExpProtocol)source;
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
        TableInfo datasetTable = dataset.getDomainKind().getTableInfo(user, targetContainer, domainName);
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
            studyPublishProtocol = ensureStudyPublishProtocol(user, targetContainer, null, null);

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
     * To help generate the assay audit record, group the rows by the source type lsid.
     */
    private Map<String, List<Map<String, Object>>> groupBySourceLsid(List<Map<String, Object>> dataMaps)
    {
        return dataMaps.stream().collect(Collectors.groupingBy(m -> (String)m.get(SOURCE_LSID_PROPERTY_NAME)));
    }

    // TODO : consider pushing this into PublishSource
    private void logPublishEvent(Dataset.PublishSource publishSource, ExpObject source, List<Map<String, Object>> dataMaps, User user, Container sourceContainer, Container targetContainer, Dataset dataset)
    {
        Map<String, List<Map<String, Object>>> sourceLSIDCounts = groupBySourceLsid(dataMaps);
        if (source != null)
        {
            for (Map.Entry<String, List<Map<String, Object>>> entry : sourceLSIDCounts.entrySet())
            {
                String sourceLsid = entry.getKey();
                List<Map<String, Object>> rows = entry.getValue();
                int recordCount = rows.size();

                String auditMessage = publishSource.getLinkToStudyAuditMessage(source, recordCount);
                AssayAuditProvider.AssayAuditEvent event = new AssayAuditProvider.AssayAuditEvent(sourceContainer.getId(), auditMessage, publishSource, source);

                event.setTargetStudy(targetContainer.getId());
                event.setDatasetId(dataset.getDatasetId());
                event.setSourceLsid(sourceLsid);
                event.setRecordCount(recordCount);

                AuditLogService.get().addEvent(user, event);

                // Create sample timeline event for each of the samples
                if (Dataset.PublishSource.SampleType == publishSource)
                {
                    var timelineEventType = SampleTimelineAuditEvent.SampleTimelineEventType.PUBLISH;
                    Map<String, Object> eventMetadata = new HashMap<>();
                    eventMetadata.put(SAMPLE_TIMELINE_EVENT_TYPE, timelineEventType.name());
                    String metadata = AbstractAuditTypeProvider.encodeForDataMap(sourceContainer, eventMetadata);

                    List<Integer> sampleIds = rows.stream().map(m -> (Integer)m.get(StudyPublishService.ROWID_PROPERTY_NAME)).collect(toList());
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
                // We used to copy batch properties with the "Run" prefix - see if we need to rename the target column
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
            // Check if we don't have a different run-prefixed property to copy and and we do have a run-prefixed property in the target domain
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
                builder.setDatasetId(new SqlSelector(schema, "SELECT MAX(n) + 1 AS id FROM (SELECT Max(datasetid) AS n FROM study.dataset WHERE container=? UNION SELECT ? As n) x", study.getContainer().getId(), MIN_ASSAY_ID).getObject(Integer.class));

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
                QCState defaultQCState = null;
                if (defaultQCStateId != null)
                    defaultQCState = QCStateManager.getInstance().getQCStateForRowId(study.getContainer(), defaultQCStateId.intValue());
                lsids = StudyManager.getInstance().importDatasetData(user, dsd, dl, columnMap, errors, DatasetDefinition.CheckForDuplicates.sourceOnly,
                        defaultQCState, insertOption, null, null, importLookupByAlternateKey, auditBehaviorType);
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

        // Check if there are any rows that have been selected to copy that have specimen data that doesn't match
        // the target study
        SimpleFilter filter = new SimpleFilter(matchFieldKey, false);
        filter.addClause(new SimpleFilter.InClause(tableMetadata.getResultRowIdFieldKey(), dataRowPKs));
        return new TableSelector(tableInfo, filter, null).exists();
    }

    /** Automatically copy assay data to a study if the design is set up to do so */
    @Override
    @Nullable
    public ActionURL autoCopyResults(ExpProtocol protocol, ExpRun run, User user, Container container, List<String> errors)
    {
        LOG.debug("Considering whether to attempt auto-copy results from assay run " + run.getName() + " from container " + container.getPath());
        AssayProvider provider = AssayService.get().getProvider(protocol);
        if (protocol.getObjectProperties().get(StudyPublishService.AUTO_COPY_TARGET_PROPERTY_URI) != null)
        {
            // First, track down the target study
            String targetStudyContainerId = protocol.getObjectProperties().get(StudyPublishService.AUTO_COPY_TARGET_PROPERTY_URI).getStringValue();
            if (targetStudyContainerId != null)
            {
                LOG.debug("Found configured target study container ID, " + targetStudyContainerId + " for auto-copy with " + run.getName() + " from container " + container.getPath());
                final Container targetStudyContainer = ContainerManager.getForId(targetStudyContainerId);

                return autoCopyResults(protocol, provider, run, user, container, targetStudyContainer, errors, LOG);
            }
        }

        return null;
    }

    @Nullable
    public ActionURL autoCopyResults(ExpProtocol protocol, AssayProvider provider, ExpRun run, User user, Container container,
                                        Container targetStudyContainer, List<String> errors, Logger log)
    {
        if (targetStudyContainer != null)
        {
            if (targetStudyContainer.equals(StudyPublishService.AUTO_COPY_TARGET_ASSAY_IMPORT_FOLDER))
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
                    log.error("Insufficient permission to copy assay data to study in folder : " + targetStudyContainer.getPath());
                    return null;
                }

                log.debug("Resolved target study in container " + targetStudyContainer.getPath() + " for auto-copy with " + run.getName() + " from container " + container.getPath());

                FieldKey ptidFK = provider.getTableMetadata(protocol).getParticipantIDFieldKey();
                FieldKey visitFK = provider.getTableMetadata(protocol).getVisitIDFieldKey(study.getTimepointType());
                FieldKey objectIdFK = provider.getTableMetadata(protocol).getResultRowIdFieldKey();
                FieldKey runFK = provider.getTableMetadata(protocol).getRunRowIdFieldKeyFromResults();

                AssayProtocolSchema schema = provider.createProtocolSchema(user, container, protocol, null);

                // Do a query to get all the info we need to do the copy
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
                    // Only copy rows that have a participant and a visit/date
                    if (ptid != null && visit != null)
                    {
                        PublishKey key;
                        // 13647: Conversion exception in assay auto copy-to-study
                        if (study.getTimepointType().isVisitBased())
                        {
                            float visitId = Float.parseFloat(visit.toString());
                            key = new PublishKey(targetContainer, ptid, visitId, objectId);
                            log.debug("Resolved info (" + ptid + "/" + visitId + ") for auto-copy of row " + objectId + " for " + run.getName() + " from container " + container.getPath());
                        }
                        else
                        {
                            Date date = (Date) ConvertUtils.convert(visit.toString(), Date.class);
                            key = new PublishKey(targetContainer, ptid, date, objectId);
                            log.debug("Resolved info (" + ptid + "/" + date + ") for auto-copy of row " + objectId + " for " + run.getName() + " from container " + container.getPath());
                        }
                        keys.put(objectId, key);
                    }
                    else
                    {
                        log.debug("Missing ptid and/or visit info for auto-copy of row " + objectId + " for " + run.getName() + " from container " + container.getPath());
                    }
                });

                log.debug("Identified " + keys + " rows with sufficient data to copy to " + targetStudyContainer.getPath() + " for auto-copy with " + run.getName() + " from container " + container.getPath());
                return provider.copyToStudy(user, container, protocol, targetStudyContainer, keys, errors);
            }
            else
                log.info("Unable to copy the assay data, there is no study in the folder: " + targetStudyContainer.getPath());
        }
        return null;
    }

    @Override
    public ExpProtocol ensureStudyPublishProtocol(User user, Container container, String name, String lsid) throws ExperimentException
    {
        String protocolName = null != name ? name : STUDY_PUBLISH_PROTOCOL_NAME;
        String protocolLsid = null != lsid ? lsid : STUDY_PUBLISH_PROTOCOL_LSID;
        ExpProtocol protocol = ExperimentService.get().getExpProtocol(protocolLsid);

        if (protocol == null)
        {
            ExpProtocol baseProtocol = ExperimentService.get().createExpProtocol(container, ExpProtocol.ApplicationType.ExperimentRun, protocolName);
            baseProtocol.setLSID(protocolLsid);
            baseProtocol.setMaxInputMaterialPerInstance(0);
            baseProtocol.setProtocolDescription("Simple protocol for publishing study using copy to study.");
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
            String containerId = (String)row.get("container");
            int datasetId = ((Number)row.get("datasetid")).intValue();
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

        // Go through the runs and figure out what protocols they belong to, and what datasets they could have been copied to
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
                // has been copied even if they can't see the dataset.
                UserSchema schema = StudyQuerySchema.createSchema(dataset.getStudy(), user, false);
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
            AssayAuditProvider.AssayAuditEvent event = new AssayAuditProvider.AssayAuditEvent(sourceContainer.getId(), auditMessage, sourceType, source);

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
}

/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
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
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayFileWriter;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayPublishKey;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayTableMetadata;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.study.StudySchema;
import org.labkey.study.assay.query.AssayAuditProvider;
import org.labkey.study.controllers.assay.AssayController;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.DatasetDomainKind;
import org.labkey.study.model.QCState;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.UploadLog;

import javax.servlet.ServletException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Manages the copy-to-study operation that links assay rows into datasets in the target study, creating the dataset
 * if needed.
 * User: Mark Igra
 * Date: Aug 16, 2006
 */
public class AssayPublishManager implements AssayPublishService
{
    private static final int MIN_ASSAY_ID = 5000;
    private static final Logger LOG = Logger.getLogger(AssayPublishManager.class);

    public synchronized static AssayPublishManager getInstance()
    {
        return (AssayPublishManager) AssayPublishService.get();
    }


    private TableInfo getTinfoUpdateLog()
    {
        return StudySchema.getInstance().getTableInfoUploadLog();
    }

    /**
     * Studies that the user has permission to.
     */
    public Set<Study> getValidPublishTargets(@NotNull User user, @NotNull Class<? extends Permission> permission)
    {
        Set<? extends Study> studies = StudyManager.getInstance().getAllStudies(ContainerManager.getRoot(), user, permission);

        // Sort based on full container path
        Set<Study> result = new TreeSet<>(Comparator.comparing(StudyEntity::getContainer));
        result.addAll(studies);
        return result;
    }

    public ActionURL publishAssayData(User user, Container sourceContainer, Container targetContainer, String assayName, ExpProtocol protocol,
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
        return publishAssayData(user, sourceContainer, targetContainer, assayName, protocol, dataMaps, propertyDescriptors, keyPropertyName, errors);
    }

    public ActionURL publishAssayData(User user, Container sourceContainer, Container targetContainer, String assayName, ExpProtocol protocol,
                                      List<Map<String, Object>> dataMaps, Map<String, PropertyType> types, List<String> errors)
    {
        return publishAssayData(user, sourceContainer, targetContainer, assayName, protocol, dataMaps, types, null, errors);
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

    public ActionURL publishAssayData(User user, Container sourceContainer, @Nullable Container targetContainer, String assayName, @Nullable ExpProtocol protocol,
                                      List<Map<String, Object>> dataMaps, String keyPropertyName, List<String> errors)
    {
        return publishAssayData(user, sourceContainer, targetContainer, assayName, protocol, dataMaps, Collections.emptyList(), keyPropertyName, errors);
    }

    private ActionURL publishAssayData(User user, Container sourceContainer, @Nullable Container targetContainer, String assayName, @Nullable ExpProtocol protocol,
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
            url = _publishAssayData(user, sourceContainer, targetStudy, assayName, protocol, maps, columns, keyPropertyName, errors);
        }
        return url;
    }

    private ActionURL _publishAssayData(User user, Container sourceContainer, @NotNull Container targetContainer, String assayName, @Nullable ExpProtocol protocol,
                                        List<Map<String, Object>> dataMaps, List<PropertyDescriptor> columns, String keyPropertyName, List<String> errors)
    {
        if (dataMaps.isEmpty())
        {
            errors.add("No data rows to publish");
            return null;
        }

        StudyImpl targetStudy = StudyManager.getInstance().getStudy(targetContainer);
        assert verifyRequiredColumns(dataMaps, targetStudy.getTimepointType());

        boolean schemaChanged = false;
        DatasetDefinition dataset = null;
        List<Map<String, Object>> convertedDataMaps;

        try (DbScope.Transaction transaction = StudySchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            List<DatasetDefinition> datasets = StudyManager.getInstance().getDatasetDefinitions(targetStudy);

            for (int i = 0; i < datasets.size() && dataset == null; i++)
            {
                // If there's a dataset linked to our protocol, use it
                if (protocol != null &&
                        datasets.get(i).getProtocolId() != null &&
                        datasets.get(i).getProtocolId().equals(protocol.getRowId()))
                {
                    dataset = datasets.get(i);
                }
                else if (protocol == null &&
                        datasets.get(i).getTypeURI() != null &&
                        datasets.get(i).getTypeURI().equals(DatasetDomainKind.generateDomainURI(assayName, datasets.get(i).getEntityId(), targetStudy.getContainer())))
                {
                    // No protocol, but we've got a type uri match. This is used when creating a study
                    // from a study design
                    dataset = datasets.get(i);
                }
            }
            if (dataset == null)
                dataset = createAssayDataset(user, targetStudy, createUniqueDatasetName(targetStudy, assayName), keyPropertyName, null, false, protocol);
            else if (protocol != null)
            {
                Integer datasetProtocolId = dataset.getProtocolId();
                if (datasetProtocolId == null)
                {
                    dataset = dataset.createMutable();
                    dataset.setProtocolId(protocol.getRowId());
                    StudyManager.getInstance().updateDatasetDefinition(user, dataset, errors);
                }
                else if (!datasetProtocolId.equals(protocol.getRowId()))
                {
                    errors.add("The destination dataset belongs to a different assay protocol");
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
            convertedDataMaps = convertPropertyNamesToURIs(dataMaps, propertyNamesToUris);
            transaction.commit();
        }
        catch (ChangePropertyDescriptorException e)
        {
            throw new UnexpectedException(e);
        }

        // re-retrieve the datasetdefinition: this is required to pick up any new columns that may have been created
        // in 'ensurePropertyDescriptors'.
        if (schemaChanged)
            StudyManager.getInstance().uncache(dataset);
        dataset = StudyManager.getInstance().getDatasetDefinition(targetStudy, dataset.getRowId());
        Integer defaultQCStateId = targetStudy.getDefaultAssayQCState();
        QCState defaultQCState = null;
        if (defaultQCStateId != null)
            defaultQCState = StudyManager.getInstance().getQCStateForRowId(targetContainer, defaultQCStateId.intValue());

        // unfortunately, the actual import cannot happen within our transaction: we eventually hit the
        // IllegalStateException in ContainerManager.ensureContainer.
        List<String> lsids = StudyManager.getInstance().importDatasetData(user, dataset, convertedDataMaps, errors, DatasetDefinition.CheckForDuplicates.sourceAndDestination, defaultQCState, null, false);
        if (lsids.size() > 0 && protocol != null)
        {
            for (Map.Entry<String, int[]> entry : getSourceLSID(dataMaps).entrySet())
            {
                AssayAuditProvider.AssayAuditEvent event = new AssayAuditProvider.AssayAuditEvent(sourceContainer.getId(),
                        entry.getValue()[0] + " row(s) were copied to a study from the assay: " + protocol.getName());

                event.setProtocol(protocol.getRowId());
                event.setTargetStudy(targetContainer.getId());
                event.setDatasetId(dataset.getDatasetId());
                event.setSourceLsid(entry.getKey());
                event.setRecordCount(entry.getValue()[0]);

                AuditLogService.get().addEvent(user, event);
            }
        }
        //Make sure that the study is updated with the correct timepoints.
        StudyManager.getInstance().getVisitManager(targetStudy).updateParticipantVisits(user, Collections.singleton(dataset));

        return PageFlowUtil.urlProvider(StudyUrls.class).getDatasetURL(targetContainer, dataset.getRowId());
    }

    private Map<String, int[]> getSourceLSID(List<Map<String, Object>> dataMaps)
    {
        Map<String, int[]> lsidMap = new HashMap<>();

        for (Map<String, Object> map : dataMaps)
        {
            for (Map.Entry<String, Object> entry : map.entrySet())
            {
                if (entry.getKey().equalsIgnoreCase("sourcelsid"))
                {
                    String lsid = String.valueOf(entry.getValue());
                    int[] count = lsidMap.computeIfAbsent(lsid, k -> new int[1]);
                    count[0]++;
                    break;
                }
            }
        }
        return lsidMap;
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

    private List<Map<String, Object>> convertPropertyNamesToURIs(List<Map<String, Object>> dataMaps, Map<String, String> propertyNamesToUris)
    {
        List<Map<String, Object>> ret = new ArrayList<>(dataMaps.size());
        for (Map<String, Object> dataMap : dataMaps)
        {
            Map<String, Object> newMap = new CaseInsensitiveHashMap<>(dataMap.size());
            for (Map.Entry<String, Object> entry : dataMap.entrySet())
            {
                String uri = propertyNamesToUris.get(entry.getKey());

                // NOTE Date is always special for publish be sure to use VisitDateURI to 'mark' this column
                if (AssayPublishService.DATE_PROPERTY_NAME.equalsIgnoreCase(entry.getKey()))
                    uri = DatasetDefinition.getVisitDateURI();

                if (null == uri)
                {
                    // Skip "TargetStudy"
                    if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equalsIgnoreCase(entry.getKey()))
                        continue;
                }
                assert uri != null : "Expected all properties to already be present in assay type. Couldn't find one for " + entry.getKey();
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
    public DatasetDefinition createDataset(User user, StudyImpl study, String name, @Nullable Integer datasetId, boolean isDemographicData)
    {
        return createAssayDataset(user, study, name, null, datasetId, isDemographicData, Dataset.TYPE_STANDARD, null, null, false);
    }

    @NotNull
    public DatasetDefinition createAssayDataset(User user, StudyImpl study, String name, @Nullable String keyPropertyName, @Nullable Integer datasetId, boolean isDemographicData, @Nullable ExpProtocol protocol)
    {
        return createAssayDataset(user, study, name, keyPropertyName, datasetId, isDemographicData, Dataset.TYPE_STANDARD, null, protocol, false);
    }

    @NotNull
    public DatasetDefinition createAssayDataset(User user, StudyImpl study, String name, @Nullable String keyPropertyName, @Nullable Integer datasetId,
                                                boolean isDemographicData, @Nullable ExpProtocol protocol, boolean useTimeKeyField)
    {
        return createAssayDataset(user, study, name, keyPropertyName, datasetId, isDemographicData, Dataset.TYPE_STANDARD, null, protocol, useTimeKeyField);
    }

    @NotNull
    public DatasetDefinition createAssayDataset(User user, StudyImpl study, String name, @Nullable String keyPropertyName, @Nullable Integer datasetId,
                                                boolean isDemographicData, String type, @Nullable Integer categoryId, @Nullable ExpProtocol protocol, boolean useTimeKeyField)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        if (useTimeKeyField && (isDemographicData || keyPropertyName != null))
            throw new IllegalStateException("UseTimeKeyField not compatible with iDemographic or other key field.");
        try (DbScope.Transaction transaction = schema.getScope().ensureTransaction())
        {
            if (null == datasetId)
                datasetId = new SqlSelector(schema, "SELECT MAX(n) + 1 AS id FROM (SELECT Max(datasetid) AS n FROM study.dataset WHERE container=? UNION SELECT ? As n) x", study.getContainer().getId(), MIN_ASSAY_ID).getObject(Integer.class);
            DatasetDefinition newDataset = new DatasetDefinition(study, datasetId.intValue(), name, name, null, null, null);
            newDataset.setShowByDefault(true);
            newDataset.setType(type);

            if (categoryId != null)
                newDataset.setCategoryId(categoryId);
            if (keyPropertyName != null)
                newDataset.setKeyPropertyName(keyPropertyName);
            newDataset.setDemographicData(isDemographicData);
            newDataset.setUseTimeKeyField(useTimeKeyField);
            if (protocol != null)
                newDataset.setProtocolId(protocol.getRowId());

            StudyManager.getInstance().createDatasetDefinition(user, newDataset);

            transaction.commit();
            return newDataset;
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

        File dir = pipelineRoot.resolvePath(AssayFileWriter.DIR_NAME);
        if (!dir.exists())
        {
            boolean success = dir.mkdir();
            if (!success)
                throw new IOException("Could not create directory: " + dir);
        }

        //File name is studyname_datasetname_date_hhmm.ss
        Date dateCreated = new Date();
        String dateString = DateUtil.formatDateTime(dateCreated, "yyy-MM-dd-HHmm");
        int id = 0;
        File file;
        do
        {
            String extension = StringUtils.defaultString(filename == null ? "tsv" : FileUtil.getExtension(filename), "tsv");
            String extra = id++ == 0 ? "" : String.valueOf(id);
            String fileName = dsd.getStudy().getLabel() + "-" + dsd.getLabel() + "-" + dateString + extra + "." + extension;
            fileName = fileName.replace('\\', '_').replace('/', '_').replace(':', '_');
            file = new File(dir, fileName);
        }
        while (file.exists());

        try (FileOutputStream out = new FileOutputStream(file))
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
        ul.setFilePath(file.getPath());

        return Table.insert(user, getTinfoUpdateLog(), ul);
    }


    /**
     * Return an array of LSIDs from the newly created dataset entries,
     * along with the upload log.
     */
    public Pair<List<String>, UploadLog> importDatasetTSV(User user, StudyImpl study, DatasetDefinition dsd, DataLoader dl, boolean importLookupByAlternateKey, FileStream fileIn, String originalFileName, Map<String, String> columnMap, BatchValidationException errors) throws ServletException
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
                    defaultQCState = StudyManager.getInstance().getQCStateForRowId(study.getContainer(), defaultQCStateId.intValue());
                lsids = StudyManager.getInstance().importDatasetData(user, dsd, dl, columnMap, errors, DatasetDefinition.CheckForDuplicates.sourceOnly,
                        defaultQCState, QueryUpdateService.InsertOption.IMPORT, null, null, importLookupByAlternateKey);
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

    public ActionURL getPublishHistory(Container c, ExpProtocol protocol)
    {
        return getPublishHistory(c, protocol, null);
    }

    public ActionURL getPublishHistory(Container container, ExpProtocol protocol, ContainerFilter containerFilter)
    {
        if (protocol != null)
        {
            ActionURL url = new ActionURL(AssayController.PublishHistoryAction.class, container).addParameter("rowId", protocol.getRowId());
            if (containerFilter != null && containerFilter.getType() != null)
                url.addParameter("containerFilterName", containerFilter.getType().name());
            return url;
        }

        throw new NotFoundException("Specified protocol is invalid");
    }

    public TimepointType getTimepointType(Container container)
    {
        Study study = StudyManager.getInstance().getStudy(container);
        if (null == study)
            throw new IllegalArgumentException("No study in container: " + container.getPath());

        return study.getTimepointType();
    }

    public boolean hasMismatchedInfo(List<Integer> dataRowPKs, AssayProtocolSchema schema)
    {
        TableInfo tableInfo = schema.createDataTable();
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
    public List<String> autoCopyResults(ExpProtocol protocol, ExpRun run, User user, Container container)
    {
        LOG.debug("Considering whether to attempt auto-copy results from assay run " + run.getName() + " from container " + container.getPath());
        AssayProvider provider = AssayService.get().getProvider(protocol);
        if (protocol.getObjectProperties().get(AssayPublishService.AUTO_COPY_TARGET_PROPERTY_URI) != null)
        {
            // First, track down the target study
            String targetStudyContainerId = protocol.getObjectProperties().get(AssayPublishService.AUTO_COPY_TARGET_PROPERTY_URI).getStringValue();
            if (targetStudyContainerId != null)
            {
                LOG.debug("Found configured target study container ID, " + targetStudyContainerId + " for auto-copy with " + run.getName() + " from container " + container.getPath());
                final Container targetStudyContainer = ContainerManager.getForId(targetStudyContainerId);
                if (targetStudyContainer != null)
                {
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
                            return Collections.emptyList();
                        }

                        LOG.debug("Resolved target study in container " + targetStudyContainer.getPath() + " for auto-copy with " + run.getName() + " from container " + container.getPath());

                        FieldKey ptidFK = provider.getTableMetadata(protocol).getParticipantIDFieldKey();
                        FieldKey visitFK = provider.getTableMetadata(protocol).getVisitIDFieldKey(study.getTimepointType());
                        FieldKey objectIdFK = provider.getTableMetadata(protocol).getResultRowIdFieldKey();
                        FieldKey runFK = provider.getTableMetadata(protocol).getRunRowIdFieldKeyFromResults();

                        AssayProtocolSchema schema = provider.createProtocolSchema(user, container, protocol, null);

                        // Do a query to get all the info we need to do the copy
                        TableInfo resultTable = schema.createDataTable(false);

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
                        final Map<Integer, AssayPublishKey> keys = new HashMap<>();
                        assert cols.get(runFK) != null : "Could not find object id column: " + objectIdFK;

                        SQLFragment sql = QueryService.get().getSelectSQL(resultTable, cols.values(), new SimpleFilter(runFK, run.getRowId()), null, Table.ALL_ROWS, Table.NO_OFFSET, false);

                        new SqlSelector(resultTable.getSchema(), sql).forEach(new Selector.ForEachBlock<ResultSet>()
                        {
                            @Override
                            public void exec(ResultSet rs) throws SQLException
                            {
                                // Be careful to not assume that we have participant or visit columns in our data domain
                                Object ptidObject = ptidColumn == null ? null : ptidColumn.getValue(rs);
                                String ptid = ptidObject == null ? null : ptidObject.toString();
                                int objectId = ((Number) objectIdColumn.getValue(rs)).intValue();
                                Object visit = visitColumn == null ? null : visitColumn.getValue(rs);
                                // Only copy rows that have a participant and a visit/date
                                if (ptid != null && visit != null)
                                {
                                    AssayPublishKey key;
                                    // 13647: Conversion exception in assay auto copy-to-study
                                    if (study.getTimepointType().isVisitBased())
                                    {
                                        float visitId = Float.parseFloat(visit.toString());
                                        key = new AssayPublishKey(targetStudyContainer, ptid, visitId, objectId);
                                        LOG.debug("Resolved info (" + ptid + "/" + visitId + ") for auto-copy of row " + objectId + " for " + run.getName() + " from container " + container.getPath());
                                    }
                                    else
                                    {
                                        Date date = (Date) ConvertUtils.convert(visit.toString(), Date.class);
                                        key = new AssayPublishKey(targetStudyContainer, ptid, date, objectId);
                                        LOG.debug("Resolved info (" + ptid + "/" + date + ") for auto-copy of row " + objectId + " for " + run.getName() + " from container " + container.getPath());
                                    }
                                    keys.put(objectId, key);
                                }
                                else
                                {
                                    LOG.debug("Missing ptid and/or visit info for auto-copy of row " + objectId + " for " + run.getName() + " from container " + container.getPath());
                                }
                            }
                        });

                        List<String> copyErrors = new ArrayList<>();
                        LOG.debug("Identified " + keys + " rows with sufficient data to copy to " + targetStudyContainer.getPath() + " for auto-copy with " + run.getName() + " from container " + container.getPath());
                        provider.copyToStudy(user, container, protocol, targetStudyContainer, keys, copyErrors);
                        return copyErrors;
                    }
                }
            }
        }

        return Collections.emptyList();
    }
}

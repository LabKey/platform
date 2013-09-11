/*
 * Copyright (c) 2008-2013 LabKey Corporation
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

package org.labkey.study;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.data.*;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.etl.DataIteratorUtil;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayTableMetadata;
import org.labkey.api.util.GUID;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.study.assay.AssayPublishManager;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.dataset.DatasetAuditViewFactory;
import org.labkey.study.importer.StudyImportJob;
import org.labkey.study.model.*;
import org.labkey.study.pipeline.SampleMindedTransformTask;
import org.labkey.study.query.DataSetTableImpl;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.security.roles.SpecimenCoordinatorRole;
import org.labkey.study.security.roles.SpecimenRequesterRole;
import org.springframework.validation.BindException;

import java.io.File;
import java.sql.SQLException;
import java.util.*;

/**
 * User: jgarms
 */
public class StudyServiceImpl implements StudyService.Service
{
    public static final StudyServiceImpl INSTANCE = new StudyServiceImpl();

    private StudyServiceImpl() {}

    public Study getStudy(Container container)
    {
        return StudyManager.getInstance().getStudy(container);
    }

    @Override
    public Study createStudy(Container container, User user, String name, TimepointType timepointType) throws SQLException
    {
        // Needed for study creation from VISC module. We might want to remove this when we don't need the old study design tool.

        // We no longer check for admin permissions due to Issue 14493, permissions are checked earlier during folder creation,
        // and permissions are not properly set on a new folder until after the study is created, so folder Admins will not be
        // recognized as folder admins at this stage.

            StudyImpl study = new StudyImpl(container, name);

            study.setTimepointType(timepointType);
            study.setSubjectColumnName("ParticipantId");
            study.setSubjectNounSingular("Participant");
            study.setSubjectNounPlural("Participants");
            study.setStartDate(new Date());

            return StudyManager.getInstance().createStudy(user, study);
    }

    public String getStudyName(Container container)
    {
        Study study = getStudy(container);
        return study == null ? null : study.getLabel();
    }

    public DataSetDefinition getDataSet(Container c, int datasetId)
    {
        Study study = StudyManager.getInstance().getStudy(c);
        if (study != null)
            return StudyManager.getInstance().getDataSetDefinition(study, datasetId);
        return null;
    }

    @Override
    public int getDatasetIdByLabel(Container c, String datasetLabel)
    {
        Study study = StudyManager.getInstance().getStudy(c);
        if (study == null)
            return -1;
        DataSet def = StudyManager.getInstance().getDataSetDefinitionByLabel(study, datasetLabel);

        return def == null ? -1 : def.getDataSetId();
    }

    @Override
    public int getDatasetIdByName(Container c, String datasetName)
    {
        Study study = StudyManager.getInstance().getStudy(c);
        if (study == null)
            return -1;
        DataSet def = StudyManager.getInstance().getDataSetDefinitionByName(study, datasetName);

        return def == null ? -1 : def.getDataSetId();
    }

    @Override
    public int getDatasetIdByQueryName(Container c, String queryName)
    {
        Study study = StudyManager.getInstance().getStudy(c);
        if (study == null)
            return -1;

        // first try resolving the dataset def by name and then by label
        DataSet def = StudyManager.getInstance().getDataSetDefinitionByName(study, queryName);
        if (def == null)
            def = StudyManager.getInstance().getDataSetDefinitionByLabel(study, queryName);

        return def == null ? -1 : def.getDataSetId();
    }

    public String updateDatasetRow(User u, Container c, int datasetId, String lsid, Map<String, Object> data, List<String> errors)
            throws SQLException
    {
        boolean allowAliasesInUpdate = false; // SEE https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=12592
        
        StudyImpl study = StudyManager.getInstance().getStudy(c);
        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);

        QCState defaultQCState = null;
        Integer defaultQcStateId = study.getDefaultDirectEntryQCState();
        if (defaultQcStateId != null)
             defaultQCState = StudyManager.getInstance().getQCStateForRowId(c, defaultQcStateId.intValue());

        String managedKey = null;
        if (def.getKeyType() == DataSet.KeyType.SUBJECT_VISIT_OTHER && def.getKeyManagementType() != DataSet.KeyManagementType.None)
            managedKey = def.getKeyPropertyName();

        ensureTransaction();
        try
        {
            Map<String,Object> oldData = getDatasetRow(u, c, datasetId, lsid);

            if (oldData == null)
            {
                // No old record found, so we can't update
                errors.add("Record not found with lsid: " + lsid);
                return null;
            }


            Map<String,Object> mergeData = new CaseInsensitiveHashMap<>(oldData);
            // don't default to using old qcstate
            mergeData.remove("qcstate");

            TableInfo target = def.getTableInfo(u);
            Map<String,ColumnInfo> colMap = DataIteratorUtil.createTableMap(target, allowAliasesInUpdate);

            // If any fields aren't included, use the old values
            for (Map.Entry<String,Object> entry : data.entrySet())
            {
                ColumnInfo col = colMap.get(entry.getKey());
                String name = null==col ? entry.getKey() : col.getName();
                if (name.equalsIgnoreCase(managedKey))
                    continue;
                mergeData.put(name,entry.getValue());
            }

            // these columns are always recalculated
            mergeData.remove("lsid");
            mergeData.remove("participantsequencenum");


            def.deleteRows(u, Collections.singletonList(lsid));

            List<Map<String,Object>> dataMap = new ArrayList<>();
            dataMap.add(mergeData);

            List<String> result = StudyManager.getInstance().importDatasetData(
                    u, def, dataMap, errors, true, defaultQCState, null, true);

            if (errors.size() > 0)
            {
                // Update failed
                return null;
            }

            // lsid is not in the updated map by default since it is not editable,
            // however it can be changed by the update
            String newLSID = result.get(0);
            mergeData.put("lsid", newLSID);

            addDatasetAuditEvent(u, c, def, oldData, mergeData);

            // Successfully updated
            commitTransaction();

            return newLSID;
        }
        finally
        {
            closeConnection();
        }
    }


    // change a map's keys to have proper casing just like the list of columns
    private static Map<String,Object> canonicalizeDatasetRow(Map<String,Object> source, List<ColumnInfo> columns)
    {
        CaseInsensitiveHashMap<String> keyNames = new CaseInsensitiveHashMap<>();
        for (ColumnInfo col : columns)
        {
            keyNames.put(col.getName(), col.getName());
        }

        Map<String,Object> result = new CaseInsensitiveHashMap<>();

        for (Map.Entry<String,Object> entry : source.entrySet())
        {
            String key = entry.getKey();
            String newKey = keyNames.get(key);
            if (newKey != null)
                key = newKey;
            else if ("_row".equals(key))
                continue;
            result.put(key, entry.getValue());
        }

        return result;
    }

    public Map<String,Object> getDatasetRow(User u, Container c, int datasetId, String lsid) throws SQLException
    {
        if (null == lsid)
            return null;
        Map<String, Object>[] rows = getDatasetRows(u, c, datasetId, Collections.singleton(lsid));
        return rows.length > 0 ? rows[0] : null;
    }

    @SuppressWarnings("unchecked") @NotNull
    public Map<String,Object>[] getDatasetRows(User u, Container c, int datasetId, Collection<String> lsids) throws SQLException
    {
        StudyImpl study = StudyManager.getInstance().getStudy(c);
        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);

        if (def == null)
        {
            throw new RuntimeException("Dataset for id " + datasetId + " not found");
        }

        // Unfortunately we need to use two tableinfos: one to get the column names with correct casing,
        // and one to get the data.  We should eventually be able to convert to using Query completely.
        StudyQuerySchema querySchema = new StudyQuerySchema(study, u, true);
        TableInfo queryTableInfo = querySchema.createDatasetTableInternal(def);

        TableInfo tInfo = def.getTableInfo(u, true);
        SimpleFilter filter = new SimpleFilter();
        filter.addInClause("lsid", lsids);

        Set<String> selectColumns = new TreeSet<>();
        List<ColumnInfo> columns = tInfo.getColumns();
        ColumnInfo colLSID = tInfo.getColumn("lsid");
        ColumnInfo colSourceLSID = tInfo.getColumn("sourcelsid");
        ColumnInfo colQCState = tInfo.getColumn("QCState");

        for (ColumnInfo col : columns)
        {
            // special handling for lsids and keys -- they're not user-editable,
            // but we want to display them
            if (!col.isUserEditable())
            {
                if (!(col == colLSID || col == colSourceLSID || col == colQCState ||
                        col.isKeyField() ||
                        col.getName().equalsIgnoreCase(def.getKeyPropertyName())))
                {
                    continue;
                }
            }
            selectColumns.add(col.getName());
        }

        Map<String, Object>[] datas = new TableSelector(tInfo, selectColumns, filter, null).getMapArray();

        if (datas.length == 0)
            return datas;

        if (datas[0] instanceof ArrayListMap)
        {
            ((ArrayListMap)datas[0]).getFindMap().remove("_key");
        }

        Map<String, Object>[] canonicalDatas = new Map[datas.length];

        for (int i = 0; i < datas.length; i++)
        {
            Map<String, Object> data = datas[i];
            canonicalDatas[i] = canonicalizeDatasetRow(data, queryTableInfo.getColumns());
        }

        return canonicalDatas;
    }

    public void deleteDatasetRow(User u, Container c, int datasetId, String lsid) throws SQLException
    {
        Study study = StudyManager.getInstance().getStudy(c);
        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);

        // Need to fetch the old item in order to log the deletion
        Map<String, Object> oldData = getDatasetRow(u, c, datasetId, lsid);

        try
        {
            ensureTransaction();

            def.deleteRows(u, Collections.singletonList(lsid));

            addDatasetAuditEvent(u, c, def, oldData, null);

            commitTransaction();
        }
        finally
        {
            closeConnection();
        }
    }

    /**
     * Requests arrive as maps of name->value. The StudyManager expects arrays of maps
     * of property URI -> value. This is a convenience method to do that conversion.
    private List<Map<String,Object>> convertMapToPropertyMapArray(User user, Map<String,Object> origData, DataSetDefinition def)
        throws SQLException
    {
        Map<String,Object> map = new HashMap<String,Object>();

        TableInfo tInfo = def.getTableInfo(user, false);

        Set<String> mvColumnNames = new HashSet<String>();
        for (ColumnInfo col : tInfo.getColumns())
        {
            String name = col.getName();
            if (mvColumnNames.contains(name))
                continue; // We've already processed this field
            Object value = origData.get(name);

            if (col.isMvEnabled())
            {
                String mvColumnName = col.getMvColumnName();
                mvColumnNames.add(mvColumnName);
                String mvIndicator = (String)origData.get(mvColumnName);
                if (mvIndicator != null)
                {
                    value = new MvFieldWrapper(value, mvIndicator);
                }
            }

            if (value == null) // value isn't in the map. Ignore.
                continue;

            map.put(col.getPropertyURI(), value);
        }

        if (origData.containsKey(DataSetTableImpl.QCSTATE_LABEL_COLNAME))
        {
            // DataSetDefinition.importDatasetData() pulls this one out by name instead of PropertyURI
            map.put(DataSetTableImpl.QCSTATE_LABEL_COLNAME, origData.get(DataSetTableImpl.QCSTATE_LABEL_COLNAME));
        }

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        result.add(map);
        return result;
    }
*/

    public void addAssayRecallAuditEvent(DataSet def, int rowCount, Container sourceContainer, User user)
    {
        AuditLogEvent event = new AuditLogEvent();

        event.setCreatedBy(user);
        event.setEventType(AssayPublishManager.ASSAY_PUBLISH_AUDIT_EVENT);
        event.setContainerId(sourceContainer.getId());
        event.setKey1(def.getStudy().getContainer().getId());

        String assayName = def.getLabel();
        ExpProtocol protocol = def.getAssayProtocol();
        if (protocol != null)
        {
            assayName = protocol.getName();
            event.setIntKey1(protocol.getRowId());
        }

        event.setComment(rowCount + " row(s) were recalled to the assay: " + assayName);

        Map<String,Object> dataMap = Collections.<String,Object>singletonMap(DataSetDefinition.DATASETKEY, def.getDataSetId());

        AuditLogService.get().addEvent(event, dataMap, AuditLogService.get().getDomainURI(AssayPublishManager.ASSAY_PUBLISH_AUDIT_EVENT));
    }


    /**
     * if oldRecord is null, it's an insert, if newRecord is null, it's delete,
     * if both are set, it's an edit
     */
    public static void addDatasetAuditEvent(User u, Container c, DataSet def, @Nullable Map<String,Object>oldRecord, @Nullable Map<String,Object> newRecord)
    {
        String comment;
        if (oldRecord == null)
            comment = "A new dataset record was inserted";
        else if (newRecord == null)
            comment = "A dataset record was deleted";
        else
            comment = "A dataset record was modified";
        addDatasetAuditEvent(u, c, def, oldRecord, newRecord, comment);
    }

    /**
     * if oldRecord is null, it's an insert, if newRecord is null, it's delete,
     * if both are set, it's an edit
     */
    public static void addDatasetAuditEvent(User u, Container c, DataSet def, Map<String,Object> oldRecord, Map<String,Object> newRecord, String auditComment)
    {
        AuditLogEvent event = new AuditLogEvent();
        event.setCreatedBy(u);

        event.setContainerId(c.getId());
        if (c.getProject() != null)
            event.setProjectId(c.getProject().getId());

        event.setIntKey1(def.getDataSetId());

        // IntKey2 is non-zero because we have details (a previous or new datamap)
        event.setIntKey2(1);

        event.setEventType(DatasetAuditViewFactory.DATASET_AUDIT_EVENT);

        String oldRecordString = null;
        String newRecordString = null;
        Object lsid;
        if (oldRecord == null)
        {
            newRecordString = DatasetAuditViewFactory.encodeForDataMap(newRecord);
            lsid = newRecord.get("lsid");
        }
        else if (newRecord == null)
        {
            oldRecordString = DatasetAuditViewFactory.encodeForDataMap(oldRecord);
            lsid = oldRecord.get("lsid");
        }
        else
        {
            oldRecordString = DatasetAuditViewFactory.encodeForDataMap(oldRecord);
            newRecordString = DatasetAuditViewFactory.encodeForDataMap(newRecord);
            lsid = newRecord.get("lsid");
        }
        event.setKey1(lsid == null ? null : lsid.toString());

        event.setComment(auditComment);

        Map<String,Object> dataMap = new HashMap<>();
        if (oldRecordString != null) dataMap.put(DatasetAuditViewFactory.OLD_RECORD_PROP_NAME, oldRecordString);
        if (newRecordString != null) dataMap.put(DatasetAuditViewFactory.NEW_RECORD_PROP_NAME, newRecordString);

        AuditLogService.get().addEvent(event, dataMap, AuditLogService.get().getDomainURI(DatasetAuditViewFactory.DATASET_AUDIT_EVENT));
    }

    public static void addDatasetAuditEvent(User u, Container c, DataSet def, String comment, UploadLog ul /*optional*/)
    {
        AuditLogEvent event = new AuditLogEvent();
        event.setCreatedBy(u);

        event.setContainerId(c.getId());
        if (c.getProject() != null)
            event.setProjectId(c.getProject().getId());

        event.setIntKey1(def.getDataSetId());

        event.setEventType(DatasetAuditViewFactory.DATASET_AUDIT_EVENT);

        event.setComment(comment);

        if (ul != null)
        {
            event.setKey1(ul.getFilePath());
        }
/*
        AuditLogService.get().addEvent(event,
                Collections.<String,Object>emptyMap(),
                AuditLogService.get().getDomainURI(DatasetAuditViewFactory.DATASET_AUDIT_EVENT));
                */
    }

    public void ensureTransaction() throws SQLException
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        scope.ensureTransaction();
    }

    public void commitTransaction() throws SQLException
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        scope.commitTransaction();
    }

    public void closeConnection()
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        scope.closeConnection();
    }

    public void applyDefaultQCStateFilter(DataView view)
    {
        if (StudyManager.getInstance().showQCStates(view.getRenderContext().getContainer()))
        {
            QCStateSet stateSet = QCStateSet.getDefaultStates(view.getRenderContext().getContainer());
            if (null != stateSet)
            {
                SimpleFilter filter = (SimpleFilter) view.getRenderContext().getBaseFilter();
                if (null == filter)
                {
                    filter = new SimpleFilter();
                    view.getRenderContext().setBaseFilter(filter);
                }
                FieldKey qcStateKey = FieldKey.fromParts(DataSetTableImpl.QCSTATE_ID_COLNAME, "rowid");
                Map<FieldKey, ColumnInfo> qcStateColumnMap = QueryService.get().getColumns(view.getDataRegion().getTable(), Collections.singleton(qcStateKey));
                ColumnInfo qcStateColumn = qcStateColumnMap.get(qcStateKey);
                if (qcStateColumn != null)
                    filter.addClause(new SimpleFilter.SQLClause(stateSet.getStateInClause(qcStateColumn.getAlias()), null, qcStateColumn.getFieldKey()));
            }
        }
    }

    public ActionURL getDatasetURL(Container container, int datasetId)
    {
        return new ActionURL(StudyController.DatasetAction.class, container).addParameter("datasetId", datasetId);
    }

    public Set<Study> findStudy(@NotNull Object studyReference, @Nullable User user)
    {
        if (studyReference == null)
            return Collections.emptySet();
        
        Container c = null;
        if (studyReference instanceof Container)
            c = (Container)studyReference;

        if (studyReference instanceof GUID)
            c = ContainerManager.getForId((GUID)studyReference);

        if (studyReference instanceof String)
        {
            try
            {
                c = (Container)ConvertUtils.convert((String)studyReference, Container.class);
            }
            catch (ConversionException ce)
            {
                // Ignore. Input may have been a Study label.
            }
        }

        if (c != null)
        {
            Study study = null;
            if (user == null || c.hasPermission(user, ReadPermission.class))
                study = getStudy(c);
            return study != null ? Collections.singleton(study) : Collections.<Study>emptySet();
        }

        Set<Study> result = new HashSet<>();
        if (studyReference instanceof String)
        {
            String studyRef = (String)studyReference;
            // look for study by label
            Study[] studies = user == null ?
                    StudyManager.getInstance().getAllStudies() :
                    StudyManager.getInstance().getAllStudies(ContainerManager.getRoot(), user, ReadPermission.class);

            for (Study study : studies)
            {
                if (studyRef.equals(study.getLabel()))
                    result.add(study);
            }
        }

        return result;
    }

    public Set<DataSetDefinition> getDatasetsForAssayProtocol(ExpProtocol protocol)
    {
        TableInfo datasetTable = StudySchema.getInstance().getTableInfoDataSet();
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("protocolid"), protocol.getRowId());
        Set<DataSetDefinition> result = new HashSet<>();
        Map<String, Object>[] rows = new TableSelector(datasetTable, new CsvSet("container,datasetid"), filter, null).getMapArray();
        for (Map<String, Object> row : rows)
        {
            String containerId = (String)row.get("container");
            int datasetId = ((Number)row.get("datasetid")).intValue();
            Container container = ContainerManager.getForId(containerId);
            result.add(getDataSet(container, datasetId));
        }
        return result;
    }

    public Map<DataSetDefinition, String> getDatasetsAndSelectNameForAssayProtocol(ExpProtocol protocol)
    {
        Set<DataSetDefinition> dataSets = getDatasetsForAssayProtocol(protocol);
        Map<DataSetDefinition, String> result = new HashMap<>();
        for (DataSetDefinition dataSet : dataSets)
            result.put(dataSet, dataSet.getStorageTableInfo().getSelectName());
        return result;
    }

    @Override
    public Set<DataSet> getDatasetsForAssayRuns(Collection<ExpRun> runs, User user)
    {
        // Cache the datasets for a specific protocol (assay design)
        Map<ExpProtocol, Set<DataSetDefinition>> protocolDatasets = new HashMap<>();
        // Remember all of the run RowIds for a given protocol (assay design)
        Map<ExpProtocol, List<Integer>> allProtocolRunIds = new HashMap<>();

        // Go through the runs and figure out what protocols they belong to, and what datasets they could have been copied to
        for (ExpRun run : runs)
        {
            ExpProtocol protocol = run.getProtocol();
            Set<DataSetDefinition> datasets = protocolDatasets.get(protocol);
            if (datasets == null)
            {
                datasets = getDatasetsForAssayProtocol(protocol);
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
        Set<DataSet> result = new HashSet<>();

        for (Map.Entry<ExpProtocol, Set<DataSetDefinition>> entry : protocolDatasets.entrySet())
        {
            for (DataSetDefinition dataset : entry.getValue())
            {
                // Don't enforce permissions for the current user - we still want to tell them if the data
                // has been copied even if they can't see the dataset.
                UserSchema schema = new StudyQuerySchema(dataset.getStudy(), user, false);
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

    public List<SecurableResource> getSecurableResources(Container container, User user)
    {
        Study study = StudyManager.getInstance().getStudy(container);

        if(null == study || !SecurityPolicyManager.getPolicy(container).hasPermission(user, ReadPermission.class))
            return Collections.emptyList();
        else
            return Collections.singletonList((SecurableResource)study);
    }

    public Set<Role> getStudyRoles()
    {
        return RoleManager.roleSet(SpecimenCoordinatorRole.class, SpecimenRequesterRole.class);
    }

    public String getSubjectNounSingular(Container container)
    {
        Study study = getStudy(container);
        if (study == null)
            return "Participant";
        return study.getSubjectNounSingular();
    }

    public String getSubjectNounPlural(Container container)
    {
        Study study = getStudy(container);
        if (study == null)
            return "Participants";
        return study.getSubjectNounPlural();
    }

    public String getSubjectColumnName(Container container)
    {
        Study study = getStudy(container);
        if (study == null)
            return "ParticipantId";
        return study.getSubjectColumnName();
    }

    public String getSubjectVisitColumnName(Container container)
    {
        return ColumnInfo.legalNameFromName(getSubjectNounSingular(container) + "Visit");
    }

    public String getSubjectTableName(Container container)
    {
        return getSubjectTableName(getSubjectNounSingular(container));
    }

    public String getSubjectVisitTableName(Container container)
    {
        return getSubjectVisitTableName(getSubjectNounSingular(container));
    }

    private String getSubjectTableName(String subjectNounSingular)
    {
        return ColumnInfo.legalNameFromName(subjectNounSingular);
    }

    private String getSubjectVisitTableName(String subjectNounSingular)
    {
        return getSubjectTableName(subjectNounSingular) + "Visit";
    }

    public String getSubjectCategoryTableName(Container container)
    {
        return getSubjectTableName(container) + "Category";
    }

    public String getSubjectGroupTableName(Container container)
    {
        return getSubjectTableName(container) + "Group";
    }

    public String getSubjectGroupMapTableName(Container container)
    {
        return getSubjectTableName(container) + "GroupMap";
    }

    public boolean isValidSubjectColumnName(Container container, String subjectColumnName)
    {
        if (subjectColumnName == null || subjectColumnName.length() == 0)
            return false;
        // Short-circuit for the common case:
        if ("ParticipantId".equalsIgnoreCase(subjectColumnName))
            return true;
        Set<String> colNames = new CaseInsensitiveHashSet(Arrays.asList(StudyUnionTableInfo.COLUMN_NAMES));
        // We allow any name that isn't found in the default set of columns added to all datasets, except "participantid",
        // which is handled above:
        return !colNames.contains(subjectColumnName);
    }

    public boolean isValidSubjectNounSingular(Container container, String subjectNounSingular)
    {
        if (subjectNounSingular == null || subjectNounSingular.length() == 0)
            return false;

        String subjectTableName = getSubjectTableName(subjectNounSingular);
        String subjectVisitTableName = getSubjectVisitTableName(subjectNounSingular);

        for (String tableName : StudySchema.getInstance().getSchema().getTableNames())
        {
            if (!tableName.equalsIgnoreCase("Participant") && !tableName.equalsIgnoreCase("ParticipantVisit"))
            {
                if (subjectTableName.equalsIgnoreCase(tableName) || subjectVisitTableName.equalsIgnoreCase(tableName))
                    return false;
            }
        }

        return true;
    }

    @Override
    public DataSet.KeyType getDatasetKeyType(Container container, String datasetName)
    {
        Study study = StudyManager.getInstance().getStudy(container);
        if (study != null)
        {
            DataSet dataset = StudyManager.getInstance().getDataSetDefinitionByName(study, datasetName);
            if (dataset != null)
                return dataset.getKeyType();
        }
        if (datasetName.equals(getSubjectGroupMapTableName(container)) || datasetName.equals(getSubjectTableName(container)))
        {
            // Treat these the same as demographics datasets for JOIN purposes - just use ParticipantId
            return DataSet.KeyType.SUBJECT;
        }
        return null;
    }

    public Map<String, String> getAlternateIdMap(Container container)
    {
        Map<String, String> alternateIdMap = new HashMap<>();
        Map<String, StudyManager.ParticipantInfo> pairMap = StudyManager.getInstance().getParticipantInfos(StudyManager.getInstance().getStudy(container), false, true);

        for(String ptid : pairMap.keySet())
            alternateIdMap.put(ptid, pairMap.get(ptid).getAlternateId());

        return alternateIdMap;
    }

    @Override
    public Study[] getAllStudies(Container root, User user)
    {
        return StudyManager.getInstance().getAllStudies(root, user);
    }

    @Override
    public boolean runStudyImportJob(Container c, User user, ActionURL url, File studyXml, String originalFilename, BindException errors, PipeRoot pipelineRoot)
    {
        try
        {
            PipelineService.get().queueJob(new StudyImportJob(c, user, url, studyXml, originalFilename, errors, pipelineRoot));
            return true;
        }
        catch (PipelineValidationException e)
        {
            return false;
        }
    }


    public DataIteratorBuilder wrapSampleMindedTransform(DataIteratorBuilder in, DataIteratorContext context, Study study, TableInfo target)
    {
        return SampleMindedTransformTask.wrapSampleMindedTransform(in,context,study,target);
    }

    @Override
    public ColumnInfo createAlternateIdColumn(TableInfo ti, ColumnInfo column, Container c)
    {
        // join to the study.participant table to get the participant's alternateId
        SQLFragment sql = new SQLFragment();
        sql.append("(SELECT p.AlternateId FROM ");
        sql.append(StudySchema.getInstance().getTableInfoParticipant(), "p");
        sql.append(" WHERE p.participantid = ");
        sql.append(column.getValueSql(ExprColumn.STR_TABLE_ALIAS));
        sql.append(" AND p.container = ?)");
        sql.add(c);

        return new ExprColumn(ti, column.getName(), sql, column.getJdbcType(), column);
    }

    @Override
    public String getDefaultDateFormatString(Container container)
    {
        return StudyManager.getInstance().getDefaultDateFormatString(container);
    }
}

/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.*;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.DataView;
import org.labkey.study.dataset.DatasetAuditViewFactory;
import org.labkey.study.model.*;
import org.labkey.study.query.DatasetUpdateService;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.query.DataSetTable;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * User: jgarms
 */
public class StudyServiceImpl implements StudyService.Service
{
    public int getDatasetId(Container c, String datasetName)
    {
        Study study = StudyManager.getInstance().getStudy(c);
        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetName);
        if (def == null)
            return -1;
        return def.getDataSetId();
    }

    public String updateDatasetRow(User u, Container c, int datasetId, String lsid, Map<String, Object> data, List<String> errors)
            throws SQLException
    {
        return updateDatasetRow(u, c, datasetId, lsid, data, errors, null);
    }

    public String updateDatasetRow(User u, Container c, int datasetId, String lsid, Map<String, Object> data, List<String> errors, String auditComment)
            throws SQLException
    {
        Study study = StudyManager.getInstance().getStudy(c);
        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);

        Integer defaultQcStateId = study.getDefaultDirectEntryQCState();
        QCState defaultQCState = null;
        if (defaultQcStateId != null)
            defaultQCState = StudyManager.getInstance().getQCStateForRowId(c, defaultQcStateId.intValue());

        // Start a transaction, so that we can rollback if our insert fails
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        boolean transactionOwner = !scope.isTransactionActive();
        if (transactionOwner)
            beginTransaction();
        try
        {
            Map<String,Object> oldData = getDatasetRow(u, c, datasetId, lsid);

            if (oldData == null)
            {
                // No old record found, so we can't update
                errors.add("Record not found with lsid: " + lsid);
                return null;
            }

            Map<String,Object> newData = new CaseInsensitiveHashMap<Object>(data);
            // If any fields aren't included, use the old values
            for (Map.Entry<String,Object> oldField : oldData.entrySet())
            {
                if (oldField.getKey().equals("lsid"))
                    continue;
                if (!newData.containsKey(oldField.getKey()))
                    newData.put(oldField.getKey(), oldField.getValue());
            }

            StudyManager.getInstance().deleteDatasetRows(study, def, Collections.singletonList(lsid));

            String tsv = createTSV(newData);
            String[] result = StudyManager.getInstance().importDatasetTSV(study, u, def, tsv, System.currentTimeMillis(),
                Collections.<String,String>emptyMap(), errors, true, defaultQCState);

            if (errors.size() > 0)
            {
                // Update failed
                return null;
            }
            // Successfully updated
            if(transactionOwner)
                commitTransaction();

            // lsid is not in the updated map by default since it is not editable,
            // however it can be changed by the update
            newData.put("lsid", result[0]);

            addDatasetAuditEvent(u, c, def, oldData, newData, auditComment);

            return result[0];
        }
        catch (IOException ioe)
        {
            throw UnexpectedException.wrap(ioe);
        }
        catch (ServletException se)
        {
            throw UnexpectedException.wrap(se);
        }
        finally
        {
            if(transactionOwner)
                rollbackTransaction();
        }
    }

    // change a map's keys to have proper casing just like the list of columns
    private static Map<String,Object> canonicalizeDatasetRow(Map<String,Object> source, List<ColumnInfo> columns)
    {
        CaseInsensitiveHashMap<String> keyNames = new CaseInsensitiveHashMap<String>();
        for (ColumnInfo col : columns)
        {
            keyNames.put(col.getName(), col.getName());
        }

        Map<String,Object> result = new HashMap<String,Object>();

        for (Map.Entry<String,Object> entry : source.entrySet())
        {
            String key = entry.getKey();
            String newKey = keyNames.get(key);
            if (newKey != null)
                key = newKey;

            result.put(key, entry.getValue());
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    public Map<String,Object> getDatasetRow(User u, Container c, int datasetId, String lsid) throws SQLException
    {
        Study study = StudyManager.getInstance().getStudy(c);
        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);


        // Unfortunately we need to select twice: once to get the right column names,
        // and once to get the canonical data.
        // We should eventually be able to convert to using Query completely.
        StudyQuerySchema querySchema = new StudyQuerySchema(study, u, true);
        TableInfo queryTableInfo = querySchema.getDataSetTable(def, null);
        Map<String,Object> result = Table.selectObject(queryTableInfo, lsid, Map.class);

        if (result == null)
            return null;

        try
        {
            TableInfo tInfo = def.getTableInfo(u);
            Map<String,Object> data = Table.selectObject(tInfo, lsid, Map.class);

            if (data == null)
                return null;

            // Need to remove extraneous columns
            data.remove("_row");
            List<ColumnInfo> columns = tInfo.getColumns();
            for (ColumnInfo col : columns)
            {
                // special handling for lsids and keys -- they're not user-editable,
                // but we want to display them
                if (col.getName().equals("lsid") ||
                        col.getName().equals("sourcelsid") ||
                        col.isKeyField())
                {
                    continue;
                }
                if (!col.isUserEditable())
                    data.remove(col.getName());
            }
            return canonicalizeDatasetRow(data, queryTableInfo.getColumns());
        }
        catch (ServletException se)
        {
            throw UnexpectedException.wrap(se);
        }
    }

    public String insertDatasetRow(User u, Container c, int datasetId, Map<String, Object> data, List<String> errors) throws SQLException
    {
        Study study = StudyManager.getInstance().getStudy(c);
        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);
        String tsv = createTSV(data);

        Integer defaultQcStateId = study.getDefaultDirectEntryQCState();
        QCState defaultQCState = null;
        if (defaultQcStateId != null)
            defaultQCState = StudyManager.getInstance().getQCStateForRowId(c, defaultQcStateId.intValue());

        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        boolean transactionOwner = !scope.isTransactionActive();
        try
        {
            if (transactionOwner)
                beginTransaction();

            String[] result = StudyManager.getInstance().importDatasetTSV(study, u, def, tsv, System.currentTimeMillis(),
                Collections.<String,String>emptyMap(), errors, true, defaultQCState);

            if (result.length > 0)
            {
                // Log to the audit log
                Map<String,Object> auditDataMap = new HashMap<String,Object>();
                auditDataMap.putAll(data);
                auditDataMap.put("lsid", result[0]);
                addDatasetAuditEvent(u, c, def, null, auditDataMap);

                if (transactionOwner)
                    commitTransaction();

                return result[0];
            }

            // Update failed
            return null;
        }
        catch (IOException ioe)
        {
            throw UnexpectedException.wrap(ioe);
        }
        catch (ServletException se)
        {
            throw UnexpectedException.wrap(se);
        }
        finally
        {
            if (transactionOwner)
                rollbackTransaction();
        }
    }

    public void deleteDatasetRow(User u, Container c, int datasetId, String lsid) throws SQLException
    {
        Study study = StudyManager.getInstance().getStudy(c);
        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);

        // Need to fetch the old item in order to log the deletion
        Map<String,Object> oldData = getDatasetRow(u, c, datasetId, lsid);

        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        boolean transactionOwner = !scope.isTransactionActive();
        try
        {
            if (transactionOwner)
                beginTransaction();

            StudyManager.getInstance().deleteDatasetRows(study, def, Collections.singletonList(lsid));

            addDatasetAuditEvent(u, c, def, oldData, null);

            if (transactionOwner)
                commitTransaction();
        }
        finally
        {
            if (transactionOwner)
                rollbackTransaction();
        }
    }

    private String createTSV(Map<String,Object> data)
    {
        StringBuilder sb = new StringBuilder();

        // Need to hold the keys in an array list to preserve order
        List<String> keyList = new ArrayList<String>();
        for (Map.Entry<String,Object> entry : data.entrySet())
        {
            keyList.add(entry.getKey());
            sb.append(entry.getKey()).append('\t');
        }
        sb.append(System.getProperty("line.separator"));

        for (String key:keyList)
        {
            Object valueObj = data.get(key);
            if (valueObj != null)
            {
                // Since we're creating a TSV, we can't use tabs.
                // Replace them with 4 spaces.
                valueObj = valueObj.toString().replaceAll("\t", "    ");
            }
            else
            {
                valueObj = "";
            }
            sb.append(valueObj).append('\t');
        }
        return sb.toString();
    }

    /**
     * if oldRecord is null, it's an insert, if newRecord is null, it's delete,
     * if both are set, it's an edit
     */
    private void addDatasetAuditEvent(User u, Container c, DataSetDefinition def, Map<String,Object>oldRecord, Map<String,Object> newRecord)
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
    private void addDatasetAuditEvent(User u, Container c, DataSetDefinition def, Map<String,Object> oldRecord, Map<String,Object> newRecord, String auditComment)
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
        DatasetAuditViewFactory.getInstance().ensureDomain(u);

        String oldRecordString = null;
        String newRecordString = null;
        if (oldRecord == null)
        {
            newRecordString = encodeAuditMap(newRecord);
        }
        else if (newRecord == null)
        {
            oldRecordString = encodeAuditMap(oldRecord);
        }
        else
        {
            oldRecordString = encodeAuditMap(oldRecord);
            newRecordString = encodeAuditMap(newRecord);
        }

        event.setComment(auditComment);

        Map<String,Object> dataMap = new HashMap<String,Object>();
        if (oldRecordString != null) dataMap.put("oldRecordMap", oldRecordString);
        if (newRecordString != null) dataMap.put("newRecordMap", newRecordString);

        AuditLogService.get().addEvent(event, dataMap, AuditLogService.get().getDomainURI(DatasetAuditViewFactory.DATASET_AUDIT_EVENT));
    }

    public static void addDatasetAuditEvent(User u, Container c, DataSetDefinition def, String comment, UploadLog ul /*optional*/)
    {
        AuditLogEvent event = new AuditLogEvent();
        event.setCreatedBy(u);

        event.setContainerId(c.getId());
        if (c.getProject() != null)
            event.setProjectId(c.getProject().getId());

        event.setIntKey1(def.getDataSetId());

        event.setEventType(DatasetAuditViewFactory.DATASET_AUDIT_EVENT);
        DatasetAuditViewFactory.getInstance().ensureDomain(u);

        event.setComment(comment);

        if (ul != null)
        {
            event.setKey1(ul.getFilePath());
        }

        AuditLogService.get().addEvent(event,
                Collections.<String,Object>emptyMap(),
                AuditLogService.get().getDomainURI(DatasetAuditViewFactory.DATASET_AUDIT_EVENT));
    }

    private String encodeAuditMap(Map<String,Object> data)
    {
        // encoding requires all strings, so convert our map
        Map<String,String> stringMap = new HashMap<String,String>();
        for (Map.Entry<String,Object> entry :  data.entrySet())
        {
            Object value = entry.getValue();
            stringMap.put(entry.getKey(), value == null ? null : value.toString());
        }
        return DatasetAuditViewFactory.encodeForDataMap(stringMap, true);
    }

    public void beginTransaction() throws SQLException
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        if(!scope.isTransactionActive())
            scope.beginTransaction();
    }

    public void commitTransaction() throws SQLException
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        if(scope.isTransactionActive())
            scope.commitTransaction();
    }

    public void rollbackTransaction()
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        if(scope.isTransactionActive())
            scope.rollbackTransaction();
    }

    public boolean isTransactionActive()
    {
        return StudySchema.getInstance().getSchema().getScope().isTransactionActive();
    }

    public boolean areDatasetsEditable(Container container)
    {
        // TODO: remove this method entirely, and use
        // column metadata to indicate if info is editable
        Study study = StudyManager.getInstance().getStudy(container);
        return study.getSecurityType() == SecurityType.EDITABLE_DATASETS;
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
                FieldKey qcStateKey = FieldKey.fromParts(DataSetTable.QCSTATE_ID_COLNAME, "rowid");
                Map<FieldKey, ColumnInfo> qcStateColumnMap = QueryService.get().getColumns(view.getDataRegion().getTable(), Collections.singleton(qcStateKey));
                ColumnInfo qcStateColumn = qcStateColumnMap.get(qcStateKey);
                filter.addClause(new SimpleFilter.SQLClause(stateSet.getStateInClause(qcStateColumn.getAlias()), null, qcStateColumn.getName()));
            }
        }
    }

    public String getSchemaName()
    {
        return StudyQuerySchema.SCHEMA_NAME;
    }

    public QueryUpdateService getQueryUpdateService(String queryName, Container container, User user)
    {
        //check to make sure datasets are updatable in this study
        if(!areDatasetsEditable(container))
            return null;

        int datasetId = getDatasetId(container, queryName);
        return datasetId >= 0 ? new DatasetUpdateService(datasetId) : null;
    }
}

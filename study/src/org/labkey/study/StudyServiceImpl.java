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
import org.labkey.api.security.User;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.labkey.api.util.UnexpectedException;
import org.labkey.study.dataset.DatasetAuditViewFactory;
import org.labkey.study.model.*;
import org.labkey.study.query.DatasetUpdateService;
import org.labkey.study.query.StudyQuerySchema;

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
        Study study = StudyManager.getInstance().getStudy(c);
        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);

        // Start a transaction, so that we can rollback if our insert fails
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
            String[] result = StudyManager.getInstance().importDatasetTSV(study, def, tsv, System.currentTimeMillis(),
                Collections.<String,String>emptyMap(), errors, true);

            if (errors.size() > 0)
            {
                // Update failed
                return null;
            }
            // Successfully updated
            if(isTransactionActive())
                commitTransaction();

            // lsid is not in the updated map by default since it is not editable,
            // however it can be changed by the update
            newData.put("lsid", result[0]);

            addDatasetAuditEvent(u, c, def, oldData, newData);

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
            if(isTransactionActive())
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
        try
        {
            String[] result = StudyManager.getInstance().importDatasetTSV(study,def, tsv, System.currentTimeMillis(),
                Collections.<String,String>emptyMap(), errors, true);

            if (result.length > 0)
            {
                // Log to the audit log
                Map<String,Object> auditDataMap = new HashMap<String,Object>();
                auditDataMap.putAll(data);
                auditDataMap.put("lsid", result[0]);
                addDatasetAuditEvent(u, c, def, null, auditDataMap);

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
    }

    public void deleteDatasetRow(User u, Container c, int datasetId, String lsid) throws SQLException
    {
        Study study = StudyManager.getInstance().getStudy(c);
        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);

        // Need to fetch the old item in order to log the deletion
        Map<String,Object> oldData = getDatasetRow(u, c, datasetId, lsid);

        StudyManager.getInstance().deleteDatasetRows(study, def, Collections.singletonList(lsid));

        addDatasetAuditEvent(u, c, def, oldData, null);
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
        AuditLogEvent event = new AuditLogEvent();
        event.setCreatedBy(u.getUserId());

        event.setContainerId(c.getId());
        if (c.getProject() != null)
            event.setProjectId(c.getProject().getId());

        event.setIntKey1(def.getDataSetId());

        // IntKey2 is non-zero because we have details (a previous or new datamap)
        event.setIntKey2(1);

        event.setEventType(DatasetAuditViewFactory.DATASET_AUDIT_EVENT);
        DatasetAuditViewFactory.getInstance().ensureDomain(u);

        String comment;
        String oldRecordString = null;
        String newRecordString = null;
        if (oldRecord == null)
        {
            comment = "A new dataset record was inserted";
            newRecordString = encodeAuditMap(newRecord);
        }
        else if (newRecord == null)
        {
            comment = "A dataset record was deleted";
            oldRecordString = encodeAuditMap(oldRecord);
        }
        else
        {
            comment = "A dataset record was modified";
            oldRecordString = encodeAuditMap(oldRecord);
            newRecordString = encodeAuditMap(newRecord);
        }

        event.setComment(comment);

        Map<String,Object> dataMap = new HashMap<String,Object>();
        if (oldRecordString != null) dataMap.put("oldRecordMap", oldRecordString);
        if (newRecordString != null) dataMap.put("newRecordMap", newRecordString);

        AuditLogService.get().addEvent(event, dataMap, AuditLogService.get().getDomainURI(DatasetAuditViewFactory.DATASET_AUDIT_EVENT));
    }

    public static void addDatasetAuditEvent(User u, Container c, DataSetDefinition def, String comment, UploadLog ul /*optional*/)
    {
        AuditLogEvent event = new AuditLogEvent();
        event.setCreatedBy(u.getUserId());

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

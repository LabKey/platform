/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

package org.labkey.study.importer;

import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.security.User;
import org.labkey.common.tools.TabLoader;
import org.labkey.common.tools.ColumnDescriptor;
import org.labkey.study.StudySchema;
import org.labkey.study.visitmanager.VisitManager;
import org.labkey.study.visitmanager.SequenceVisitManager;
import org.labkey.study.model.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * User: brittp
 * Date: Jan 6, 2006
 * Time: 10:19:36 AM
 */
public class VisitMapImporter
{
    public boolean process(User user, Study study, String content, List<String> errors) throws SQLException
    {
        if (content == null)
        {
            errors.add("Visit map is empty");
            return false;
        }

        List<VisitMapRecord> records;
        try
        {
            records = getRecords(content);
        }
        catch (NumberFormatException x)
        {
            errors.add(x.getMessage());
            return false;
        }
        
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        try
        {
            scope.beginTransaction();
            saveDataSets(user, study, records);
            saveVisits(user, study, records);
            SequenceVisitManager visitManager = (SequenceVisitManager) StudyManager.getInstance().getVisitManager(study);
            if (!visitManager.validateVisitRanges(errors))
            {
                scope.rollbackTransaction();
                return false;
            }
            saveVisitMap(user, study, records);
            scope.commitTransaction();
            return true;
        }
        finally
        {
            if (scope != null)
                scope.closeConnection();
        }
    }


    private void saveVisits(User user, Study study, List<VisitMapRecord> records) throws SQLException
    {
        VisitManager visitManager = StudyManager.getInstance().getVisitManager(study);
        for (VisitMapRecord record : records)
        {
            Visit visit = visitManager.findVisitBySequence(record.getSequenceNumMin());

            // we're using sequenceNumMin as the key in this instance
            if (visit != null && visit.getSequenceNumMin() != record.getSequenceNumMin())
                visit = null;
            
            if (visit == null)
            {
                visit = new Visit(study.getContainer(), record.getSequenceNumMin(), record.getSequenceNumMax(), record.getVisitLabel(), record.getVisitType());
                visit.setVisitDateDatasetId(record.getVisitDatePlate());
                int rowId = StudyManager.getInstance().createVisit(study, user, visit);
                record.setVisitRowId(rowId);
                assert record.getVisitRowId() > 0;
            }
            else
            {
                if (visit.getVisitDateDatasetId() <= 0 && record.getVisitDatePlate() > 0)
                {
                    visit = _mutable(visit);
                    visit.setVisitDateDatasetId(record.getVisitDatePlate());
                }
                if (visit.getSequenceNumMax() != record.getSequenceNumMax())
                {
                    visit = _mutable(visit);
                    visit.setSequenceNumMax(record.getSequenceNumMax());
                }
                if (visit.isMutable())
                {
                    StudyManager.getInstance().updateVisit(user, visit);
                }
                record.setVisitRowId(visit.getRowId());
                assert record.getVisitRowId() > 0;
            }
        }
    }

    
    private Visit _mutable(Visit v)
    {
        if (!v.isMutable())
            v = v.createMutable();
        return v;
    }


    private void saveVisitMap(User user, Study study, List<VisitMapRecord> records) throws SQLException
    {
        // NOTE: the only visit map setting for now is REQUIRED/OPTIONAL so...
        Container container = study.getContainer();
        Map<VisitMapKey,Boolean> requiredMapCurr = StudyManager.getInstance().getRequiredMap(study);
        Map<VisitMapKey,Boolean> requiredMapNew = new HashMap<VisitMapKey, Boolean>();

        for (VisitMapRecord record : records)
        {
            int visitId = record.getVisitRowId();
            assert visitId > 0;

            for (int dataSetId : record.getOptionalPlates())
                requiredMapNew.put(new VisitMapKey(dataSetId,visitId), Boolean.FALSE);
            for (int dataSetId : record.getRequiredPlates())
                requiredMapNew.put(new VisitMapKey(dataSetId,visitId), Boolean.TRUE);
        }
            
        for (Map.Entry<VisitMapKey,Boolean> e : requiredMapNew.entrySet())
        {
            VisitMapKey key = e.getKey();
            Boolean isRequiredNew = e.getValue();
            Boolean isRequiredCurrent = requiredMapCurr.get(key);

            // CREATE
            if (null == isRequiredCurrent)
            {
                StudyManager.getInstance().createVisitDataSetMapping(user, container, key.visitRowId, key.datasetId, isRequiredNew);
            }
            // UPDATE
            else
            {
                requiredMapCurr.remove(key);
                if (isRequiredCurrent != isRequiredNew)
                {
                    // this does a bit too much work...
                    StudyManager.getInstance().updateVisitDataSetMapping(user, container, key.visitRowId, key.datasetId, isRequiredNew ? VisitDataSetType.REQUIRED : VisitDataSetType.OPTIONAL);
                }
            }
        }

        // NOTE: extra mappings don't hurt, just make sure they are not required
        for (Map.Entry<VisitMapKey, Boolean> e : requiredMapCurr.entrySet())
        {
            VisitMapKey key = e.getKey();
            Boolean isRequiredCurrent = e.getValue();
            if (isRequiredCurrent)
                StudyManager.getInstance().updateVisitDataSetMapping(user, container, key.visitRowId, key.datasetId, VisitDataSetType.OPTIONAL);
        }
    }


    List<VisitMapRecord> getRecords(String content)
    {
        List<VisitMapRecord> records = new ArrayList<VisitMapRecord>();
        List<Map<String, Object>> maps;

        String tsv = content.replace('|','\t');
        try
        {
            TabLoader loader = new TabLoader(tsv, false);
            loader.setColumns(new ColumnDescriptor[]
                    {
                    new ColumnDescriptor("sequenceRange", String.class),
                    new ColumnDescriptor("visitType", String.class),
                    new ColumnDescriptor("visitLabel", String.class),
                    new ColumnDescriptor("visitDatePlate", Integer.class),
                    new ColumnDescriptor("visitDateField", String.class),
                    new ColumnDescriptor("visitDueDay", Integer.class),
                    new ColumnDescriptor("visitDueAllowance", Integer.class),
                    new ColumnDescriptor("requiredPlates", String.class),
                    new ColumnDescriptor("optionalPlates", String.class),
                    new ColumnDescriptor("missedNotificationPlate", Integer.class),
                    new ColumnDescriptor("terminationWindow", String.class)
                    });

            // UNDONE: TabLoader does not integrate with ObjectFactory yet...
            maps = loader.load();
        }
        catch (IOException x)
        {
            throw new RuntimeException(x);
        }
        catch (Exception x)
        {
            throw new RuntimeException(x);
        }

        for (Map<String, Object> m : maps)
        {
            if (m.get("sequenceRange") != null)
                records.add(new VisitMapRecord(m));
        }

        return records;
    }


    private void saveDataSets(User user, Study study, List<VisitMapRecord> records) throws SQLException
    {
        DataSetDefinition[] defs = StudyManager.getInstance().getDataSetDefinitions(study);
        Set<Integer> existingSet = new HashSet<Integer>();
        for (DataSetDefinition def : defs)
            existingSet.add(def.getDataSetId());

        Set<Integer> addDatasetIds = new HashSet<Integer>();
        for (VisitMapRecord record : records)
        {
            for (int id : record.getRequiredPlates())
                addDatasetIds.add(id);
            for (int id : record.getOptionalPlates())
                addDatasetIds.add(id);
        }

        for (Integer dataSetId : addDatasetIds)
        {
            if (dataSetId > 0 && !existingSet.contains(dataSetId))
                StudyManager.getInstance().createDataSetDefinition(user, study.getContainer(), dataSetId);
        }
    }
}

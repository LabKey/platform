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
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.study.StudySchema;
import org.labkey.study.model.*;
import org.labkey.study.visitmanager.VisitManager;

import java.io.File;
import java.sql.SQLException;
import java.util.*;

/**
 * User: brittp
 * Date: Jan 6, 2006
 * Time: 10:19:36 AM
 */
public class VisitMapImporter
{
    public enum Format
    {
        DataFax {
            public VisitMapReader getReader()
            {
                return new DataFaxVisitMapReader();
            }

            public String getExtension()
            {
                return ".txt";
            }},

        Xml {
            public VisitMapReader getReader()
            {
                return new XmlVisitMapReader();
            }

            public String getExtension()
            {
                return ".xml";
            }};

        abstract public VisitMapReader getReader();
        abstract public String getExtension();

        static Format getFormat(File visitMapFile)
        {
            String name = visitMapFile.getName();

            for (Format format : Format.values())
                if (name.endsWith(format.getExtension()))
                    return format;

            throw new IllegalStateException("Unknown visit map extension for file " + name);
        }
    }

    public boolean process(User user, StudyImpl study, String content, Format format, List<String> errors) throws SQLException, StudyImporter.StudyImportException
    {
        if (content == null)
        {
            errors.add("Visit map is empty");
            return false;
        }

        List<VisitMapRecord> records;

        try
        {
            records = format.getReader().getRecords(content);
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
            saveVisitMap(user, study, records);
            scope.commitTransaction();
            return true;
        }
        catch (StudyManager.VisitCreationException e)
        {
            errors.add(e.getMessage());
            return false;
        }
        finally
        {
            if (scope != null)
                scope.closeConnection();
        }
    }


    private void saveVisits(User user, StudyImpl study, List<VisitMapRecord> records) throws SQLException
    {
        Container c = study.getContainer();
        StudyManager studyManager = StudyManager.getInstance();
        VisitManager visitManager = studyManager.getVisitManager(study);

        for (VisitMapRecord record : records)
        {
            VisitImpl visit = visitManager.findVisitBySequence(record.getSequenceNumMin());

            // we're using sequenceNumMin as the key in this instance
            if (visit != null && visit.getSequenceNumMin() != record.getSequenceNumMin())
                visit = null;
            
            if (visit == null)
            {
                visit = new VisitImpl(study.getContainer(), record.getSequenceNumMin(), record.getSequenceNumMax(), record.getVisitLabel(), record.getVisitType());
                visit.setVisitDateDatasetId(record.getVisitDatePlate());
                visit.setShowByDefault(record.isShowByDefault());
                int rowId = studyManager.createVisit(study, user, visit);
                record.setVisitRowId(rowId);
                assert record.getVisitRowId() > 0;
            }
            else
            {
                if (visit.getVisitDateDatasetId() <= 0 && record.getVisitDatePlate() > 0)
                {
                    visit = _ensureMutable(visit);
                    visit.setVisitDateDatasetId(record.getVisitDatePlate());
                }
                if (visit.getSequenceNumMax() != record.getSequenceNumMax())
                {
                    visit = _ensureMutable(visit);
                    visit.setSequenceNumMax(record.getSequenceNumMax());
                }
                if (visit.isShowByDefault() != record.isShowByDefault())
                {
                    visit = _ensureMutable(visit);
                    visit.setShowByDefault(record.isShowByDefault());
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

    
    private VisitImpl _ensureMutable(VisitImpl v)
    {
        if (!v.isMutable())
            v = v.createMutable();
        return v;
    }


    private void saveVisitMap(User user, Study study, List<VisitMapRecord> records) throws SQLException
    {
        // NOTE: the only visit map setting for now is REQUIRED/OPTIONAL so...
        Container container = study.getContainer();
        Map<VisitMapKey, Boolean> requiredMapCurr = StudyManager.getInstance().getRequiredMap(study);
        Map<VisitMapKey, Boolean> requiredMapNew = new HashMap<VisitMapKey, Boolean>();

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


    private void saveDataSets(User user, Study study, List<VisitMapRecord> records) throws SQLException
    {
        DataSet[] defs = StudyManager.getInstance().getDataSetDefinitions(study);
        Set<Integer> existingSet = new HashSet<Integer>();
        for (DataSet def : defs)
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

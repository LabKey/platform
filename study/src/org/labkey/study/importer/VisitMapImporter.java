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

package org.labkey.study.importer;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.TimepointType;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.StudySchema;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitDatasetType;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.model.VisitMapKey;
import org.labkey.study.model.VisitTag;
import org.labkey.study.visitmanager.VisitManager;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * User: brittp
 * Date: Jan 6, 2006
 * Time: 10:19:36 AM
 */
public class VisitMapImporter
{
    private boolean _ensureDatasets = true;

    public enum Format
    {
        // As of 15.1, XML is the only supported visit map format. We'll leave the enum in place in case we want to support other formats in the future.
        @SuppressWarnings({"UnusedDeclaration"})
        Xml
                {
                    public VisitMapReader getReader(String contents, Logger logger) throws VisitMapParseException
                    {
                        return new XmlVisitMapReader(contents);
                    }

                    public VisitMapReader getReader(VirtualFile file, String name, Logger logger) throws VisitMapParseException, IOException
                    {
                        return new XmlVisitMapReader(file.getXmlBean(name));
                    }

                    public String getExtension()
                    {
                        return ".xml";
                    }
                };

        abstract public VisitMapReader getReader(String contents, Logger logger) throws VisitMapParseException;

        abstract public VisitMapReader getReader(VirtualFile file, String name, Logger logger) throws VisitMapParseException, IOException;

        abstract public String getExtension();

        static Format getFormat(String name)
        {
            for (Format format : Format.values())
                if (name.endsWith(format.getExtension()))
                    return format;

            throw new IllegalStateException("Unknown visit map extension for file " + name);
        }
    }

    public boolean isEnsureDatasets()
    {
        return _ensureDatasets;
    }

    public void setEnsureDatasets(boolean ensureDatasets)
    {
        _ensureDatasets = ensureDatasets;
    }

    public boolean process(User user, StudyImpl study, String content, Format format, List<String> errors, Logger logger) throws SQLException, IOException, ValidationException
    {
        if (content == null)
        {
            errors.add("Visit map is empty");
            return false;
        }

        try
        {
            VisitMapReader reader = format.getReader(content, logger);
            return _process(user, study, reader, errors, logger);
        }
        catch (VisitMapParseException x)
        {
            errors.add("Unable to parse the visit map format: " + x.getMessage());
            return false;
        }
    }

    public boolean process(User user, StudyImpl study, VirtualFile file, String name, Format format, List<String> errors, Logger logger) throws SQLException, IOException, ValidationException
    {
        if (file == null)
        {
            errors.add("Visit map is empty");
            return false;
        }
        try
        {
            VisitMapReader reader = format.getReader(file, name, logger);
            return _process(user, study, reader, errors, logger);
        }
        catch (VisitMapParseException x)
        {
            errors.add("Unable to parse the visit map format: " + x.getMessage());
            return false;
        }
    }

    private boolean _process(User user, StudyImpl study, VisitMapReader reader, List<String> errors, Logger logger) throws SQLException, IOException, ValidationException
    {
        if (study.getTimepointType() == TimepointType.CONTINUOUS)
        {
            logger.warn("Can't import visits for an continuous date based study.");
            return true;
        }

        List<VisitMapRecord> records;
        List<StudyManager.VisitAlias> aliases;
        List<VisitTag> visitTags;

        try
        {
            records = reader.getVisitMapRecords(study.getTimepointType());
            aliases = reader.getVisitImportAliases();
            visitTags = reader.getVisitTags();
        }
        catch (VisitMapParseException x)
        {
            errors.add("Unable to parse the visit map format: " + x.getMessage());
            return false;
        }
        catch (IOException x)
        {
            errors.add("IOException while parsing visit map: " + x.getMessage());
            return false;
        }

        verifyDistinctSequenceNums(records);

        DbScope scope = StudySchema.getInstance().getSchema().getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            saveDatasets(user, study, records);
            saveVisits(user, study, records);
            saveVisitMap(user, study, records);
            saveImportAliases(user, study, aliases);
            saveVisitTags(user, study, visitTags);
            transaction.commit();
            return true;
        }
        catch (StudyManager.VisitCreationException|VisitMapImportException e)
        {
            errors.add(e.getMessage());
            return false;
        }
    }

    private void verifyDistinctSequenceNums(List<VisitMapRecord> records)
    {
        Set<Double> uniqueSequenceNums = new HashSet<>();

        for (VisitMapRecord record : records)
        {
            String errorMsg = "Visit " + (record.getVisitLabel()) + " range overlaps with another record in the visit map.";

            if (uniqueSequenceNums.contains(record.getSequenceNumMin()))
                throw new VisitMapImportException(errorMsg);
            uniqueSequenceNums.add(record.getSequenceNumMin());

            if (record.getSequenceNumMax() != record.getSequenceNumMin() && uniqueSequenceNums.contains(record.getSequenceNumMax()))
                throw new VisitMapImportException(errorMsg);
            uniqueSequenceNums.add(record.getSequenceNumMax());
        }
    }

    private void saveImportAliases(User user, Study study, List<StudyManager.VisitAlias> aliases) throws ValidationException, IOException
    {
        StudyManager.getInstance().importVisitAliases(study, user, aliases);
    }

    private void saveVisits(User user, StudyImpl study, List<VisitMapRecord> records) throws SQLException
    {
        StudyManager studyManager = StudyManager.getInstance();
        VisitManager visitManager = studyManager.getVisitManager(study);

        Study visitStudy = studyManager.getStudyForVisits(study);

        for (VisitMapRecord record : records)
        {
            VisitImpl visit = visitManager.findVisitBySequence(record.getSequenceNumMin());

            // we're using sequenceNumMin as the key in this instance
            if (visit != null && visit.getSequenceNumMin() != record.getSequenceNumMin())
                visit = null;

            if (visit == null)
            {
                visit = new VisitImpl(visitStudy.getContainer(), record.getSequenceNumMin(), record.getSequenceNumMax(), record.getVisitLabel(), record.getVisitType());
                visit.setProtocolDay(record.getProtocolDay());
                visit.setDescription(record.getVisitDescription());
                visit.setVisitDateDatasetId(record.getVisitDatePlate());
                visit.setShowByDefault(record.isShowByDefault());
                visit.setChronologicalOrder(record.getChronologicalOrder());
                visit.setDisplayOrder(record.getDisplayOrder());
                visit.setSequenceNumHandling(record.getSequenceNumHandling());
                int rowId = studyManager.createVisit(study, user, visit).getRowId();
                record.setVisitRowId(rowId); // used by saveVisitMap
                assert record.getVisitRowId() > 0;
            }
            else
            {
                assert visitStudy.getContainer().equals(visit.getContainer()) : "Existing visit should have been created in shared visit study container";

                if (!StringUtils.equals(visit.getDescription(), record.getVisitDescription()))
                {
                    visit = _ensureMutable(visit);
                    visit.setDescription(record.getVisitDescription());
                }
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
                if (!Objects.equals(visit.getProtocolDay(), record.getProtocolDay()))
                {
                    visit = _ensureMutable(visit);
                    visit.setProtocolDay(record.getProtocolDay());
                }
                if (visit.isShowByDefault() != record.isShowByDefault())
                {
                    visit = _ensureMutable(visit);
                    visit.setShowByDefault(record.isShowByDefault());
                }
                if (visit.isMutable())
                {
                    if (visitManager.isVisitOverlapping(visit))
                    {
                        String visitLabel = visit.getLabel() != null ? visit.getLabel() : ""+visit.getSequenceNumMin();
                        throw new VisitMapImportException("Visit " + visitLabel + " range overlaps with an existing visit in this study.");
                    }

                    StudyManager.getInstance().updateVisit(user, visit);
                }
                record.setVisitRowId(visit.getRowId()); // used by saveVisitMap
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
        Map<VisitMapKey, Boolean> requiredMapNew = new HashMap<>();

        for (VisitMapRecord record : records)
        {
            int visitId = record.getVisitRowId();
            assert visitId > 0;

            for (int datasetId : record.getOptionalPlates())
                requiredMapNew.put(new VisitMapKey(datasetId, visitId), Boolean.FALSE);
            for (int datasetId : record.getRequiredPlates())
                requiredMapNew.put(new VisitMapKey(datasetId, visitId), Boolean.TRUE);
        }

        for (Map.Entry<VisitMapKey, Boolean> e : requiredMapNew.entrySet())
        {
            VisitMapKey key = e.getKey();
            Boolean isRequiredNew = e.getValue();
            Boolean isRequiredCurrent = requiredMapCurr.get(key);

            // CREATE
            if (null == isRequiredCurrent)
            {
                StudyManager.getInstance().createVisitDatasetMapping(user, container, key.visitRowId, key.datasetId, isRequiredNew);
            }
            // UPDATE
            else
            {
                requiredMapCurr.remove(key);
                if (isRequiredCurrent != isRequiredNew)
                {
                    // this does a bit too much work...
                    StudyManager.getInstance().updateVisitDatasetMapping(user, container, key.visitRowId, key.datasetId, isRequiredNew ? VisitDatasetType.REQUIRED : VisitDatasetType.OPTIONAL);
                }
            }
        }

        // NOTE: extra mappings don't hurt, just make sure they are not required
        for (Map.Entry<VisitMapKey, Boolean> e : requiredMapCurr.entrySet())
        {
            VisitMapKey key = e.getKey();
            Boolean isRequiredCurrent = e.getValue();
            if (isRequiredCurrent)
                StudyManager.getInstance().updateVisitDatasetMapping(user, container, key.visitRowId, key.datasetId, VisitDatasetType.OPTIONAL);
        }
    }


    private void saveDatasets(User user, Study study, List<VisitMapRecord> records) throws SQLException
    {
        List<DatasetDefinition> defs = StudyManager.getInstance().getDatasetDefinitions(study);
        Set<Integer> existingSet = new HashSet<>();
        for (Dataset def : defs)
            existingSet.add(def.getDatasetId());

        Set<Integer> addDatasetIds = new HashSet<>();
        for (VisitMapRecord record : records)
        {
            for (int id : record.getRequiredPlates())
                addDatasetIds.add(id);
            for (int id : record.getOptionalPlates())
                addDatasetIds.add(id);
        }

        for (Integer datasetId : addDatasetIds)
        {
            if (datasetId > 0 && _ensureDatasets && !existingSet.contains(datasetId))
                StudyManager.getInstance().createDatasetDefinition(user, study.getContainer(), datasetId);
        }
    }

    private Map<String, VisitTag> saveVisitTags(User user, Study study, List<VisitTag> visitTags) throws ValidationException
    {
        return StudyManager.getInstance().importVisitTags(study, user, visitTags);
    }

    public static class VisitMapImportException extends RuntimeException
    {
        public VisitMapImportException(String message)
        {
            super(message);
        }
    }
}

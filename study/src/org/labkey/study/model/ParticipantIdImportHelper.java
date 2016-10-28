/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.study.model;

import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.study.query.StudyQuerySchema;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * An import helper class to translate participant IDs based on a configured alias table
 * User: gktaylor
 * Date: 2013-12-17
 */
public class ParticipantIdImportHelper
{
    Study _study;
    User _user;
    HashMap<String, String> _aliasLookup;
    HashSet<String> _duplicateAliasLookup;
    HashSet<String> _conflictAliasLookup;

    /*
     * Default constructor for the ParticipantIdImportHelper saves parameter values for future use.
     *
     * @param study The study where data is being imported.
     * @param user  The user with priviledges to import the data. This value is used to resolve the aliases table.
     * @param def   The dataset definition of the dataset that is being imported into.
     */
    public ParticipantIdImportHelper(@NotNull Study study, @NotNull User user, @Nullable DatasetDefinition def) throws ValidationException
    {
        _study = study;
        _user = user;
        _duplicateAliasLookup = new HashSet<>();
        _conflictAliasLookup = new HashSet<>();
        _aliasLookup = generateAliasHashMap(def);
    }

    // for testing
    public ParticipantIdImportHelper(
            @Nullable HashMap<String,String> aliasLookupMap)
    {
        _aliasLookup = aliasLookupMap;
        _duplicateAliasLookup = new HashSet<>();
        _conflictAliasLookup = new HashSet<>();
    }

    /*
     * The setup function that extracts alias information from the alias table and saves it in a hashmap.
     */
    public HashMap<String,String> generateAliasHashMap(@Nullable DatasetDefinition targetDef) throws ValidationException
    {
        HashMap<String,String> result = new HashMap<>();
        StudyImpl studyImpl = StudyManager.getInstance().getStudy(_study.getContainer());
        // extract the participant alias information from the study configuration
        Integer participantAliasDatasetId = studyImpl.getParticipantAliasDatasetId();
        String participantAliasSourceProperty = studyImpl.getParticipantAliasSourceProperty();
        String participantAliasProperty = studyImpl.getParticipantAliasProperty();

        // only generate the alias hashmap if the alias table has been configured properly
        if (participantAliasDatasetId != null && (targetDef == null || targetDef.getDatasetId() != participantAliasDatasetId)) // do not apply lookups on the alias table itself.
        {
            StudyManager studyManager = StudyManager.getInstance();
            DatasetDefinition aliasDataset = studyManager.getDatasetDefinition(studyImpl, participantAliasDatasetId);
            if (aliasDataset != null) // possible if the alias dataset has been deleted
            {
                // Build up a HashSet of ptids to check for conflicts in the alias table
                HashSet<String> ptids = generatePtidHashSet(studyImpl);

                //  Build up a HashMap of aliases
                TableInfo aliasTableInfo = studyManager.getDatasetDefinition(studyImpl, participantAliasDatasetId).getTableInfo(_user);
                Map<String, Object>[] rows = new TableSelector(aliasTableInfo, ImmutableSet.of(studyImpl.getSubjectColumnName(), participantAliasSourceProperty, participantAliasProperty), null, null).getMapArray();
                for (Map<String, Object> row : rows)
                {
                    Object idObj = row.get(studyImpl.getSubjectColumnName());
                    Object aliasObj = row.get(participantAliasProperty);
                    Object sourceObj = row.get(participantAliasSourceProperty);
                    // If there was an error in the alias table, (i.e. a column is misconfigured or deleted) stop the import with an error.
                    if (idObj == null || aliasObj == null)
                        throw new ValidationException("Invalid configuration of alternate " + studyImpl.getSubjectNounSingular() + " " + studyImpl.getSubjectColumnName() + "s and aliases.");
                    String id = idObj.toString();
                    String alias = aliasObj.toString();
                    if (result.containsKey(alias))  // maintain a conflict set in the case that there are multiple entries for one key, error on access
                        _duplicateAliasLookup.add(alias);
                    else if (ptids.contains(alias))
                        _conflictAliasLookup.add(alias);
                    else
                        result.put(alias, id);
                }
            }
        }
        return result;
    }

    /*
     * Build up a HashSet of ptids to check for conflicts in the alias table
     */
    protected HashSet<String> generatePtidHashSet(StudyImpl studyImpl)
    {
        HashSet<String> ptids = new HashSet<>();
        StudyQuerySchema studySchema = StudyQuerySchema.createSchema(studyImpl, _user, true);
        TableInfo ptidTableInfo = studySchema.getTable(studyImpl.getSubjectNounSingular());
        Map<String, Object>[] tmp_rows = new TableSelector(ptidTableInfo, ImmutableSet.of(studyImpl.getSubjectColumnName()), null, null).getMapArray();
        for (Map<String, Object> row : tmp_rows)
        {
            Object idObj = row.get(studyImpl.getSubjectColumnName());
            ptids.add((String) idObj);
        }
        return ptids;
    }

    /*
     * The getCallable method returns a collable object that translates the participantId to its alias on lookup
     */
    public Callable<Object> getCallable(@NotNull final DataIterator it, @Nullable final Integer participantIndex)
    {
        return new Callable<Object>()
        {
            @Override
            public Object call() throws Exception
            {
                Object pid = null==participantIndex ? null : it.get(participantIndex);
                String participantId = translateParticipantId(pid);
                return participantId;
            }
        };
    }

    /*
     * translateParticipantId performs the lookup of the participantId value using the alias created on helper setup
     */
    public String translateParticipantId(@Nullable Object p) throws ValidationException
    {
        String participantId = null;

        if (null != p)
        {
            if (p instanceof String)
                participantId = (String) p;
            else
                participantId = p.toString();
        }

        if (_duplicateAliasLookup.contains(participantId))
            throw new ValidationException("There are multiple entries for the alias " + participantId + " which must be corrected before the import may continue.");

        if (_conflictAliasLookup.contains(participantId))
            throw new ValidationException("There is a collision, the alias " + participantId + " already exists as a " + _study.getSubjectNounSingular() + ".  You must remove either the alias or the " + _study.getSubjectNounSingular()+ ".");

        String value = _aliasLookup.get(participantId);
        if (value != null)
            participantId = value;
        return participantId;
    }

    /**
     *  TESTS
     **/
    public static class ParticipantIdTest extends Assert
    {
        @Test
        public void testAliasLookup()
        {
            HashMap<String, String> map = new HashMap<>();
            map.put("IdAnimal01", "IdAnimal01_ID");
            map.put("IdAnimal02", "IdAnimal02_ID");
            map.put("IdAnimal03", "IdAnimal03_ID");
            map.put("IdAnimal04", "IdAnimal04_ID");
            ParticipantIdImportHelper h = new ParticipantIdImportHelper(
                    map
                    );
            // the map is populated with values to return upon translation
            try
            {
                String result1 = h.translateParticipantId("IdAnimal01");
                assertEquals(result1, "IdAnimal01_ID");
                String result3 = h.translateParticipantId("IdAnimal03");
                assertEquals(result3, "IdAnimal03_ID");
            }
            catch (ValidationException e) {
                assert(false); // fail
            }
        }

        @Test
        public void testEmptyAliasLookup()
        {
            HashMap<String, String> map = new HashMap<>();
            ParticipantIdImportHelper h = new ParticipantIdImportHelper(
                    map
            );
            try
            {
                // the map is empty, so values should be "translated" back to themselves
                String result1 = h.translateParticipantId("IdAnimal01");
                assertEquals(result1, "IdAnimal01");
                String result3 = h.translateParticipantId("IdAnimal03");
                assertEquals(result3, "IdAnimal03");
            }
            catch (ValidationException e)
            {
                assert(false); // fail
            }
        }
    }
}

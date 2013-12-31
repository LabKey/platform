/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
import org.labkey.api.etl.DataIterator;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.TimepointType;

import java.util.Date;
import java.util.HashMap;
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

    /*
     * Default constructor for the ParticipantIdImportHelper saves parameter values for future use.
     *
     * @param study The study where data is being imported.
     * @param user  The user with priviledges to import the data. This value is used to resolve the aliases table.
     * @param def   The dataset definition of the dataset that is being imported into.
     */
    public ParticipantIdImportHelper(@NotNull Study study, @NotNull User user, @Nullable DataSetDefinition def) throws ValidationException
    {
        _study = study;
        _user = user;
        _aliasLookup = generateAliasHashMap(def);
    }

    // for testing
    public ParticipantIdImportHelper(
            @Nullable HashMap<String,String> aliasLookupMap)
    {
        _aliasLookup = aliasLookupMap;
    }

    /*
     * The setup function that extracts alias information from the alias table and saves it in a hashmap.
     */
    public HashMap<String,String> generateAliasHashMap(@Nullable DataSetDefinition targetDef) throws ValidationException
    {
        HashMap<String,String> result = new HashMap<>();
        StudyImpl studyImpl = StudyManager.getInstance().getStudy(_study.getContainer());
        // extract the participant alias information from the study configuration
        Integer participantAliasDatasetId = studyImpl.getParticipantAliasDatasetId();
        String participantAliasSourceProperty = studyImpl.getParticipantAliasSourceProperty();
        String participantAliasProperty = studyImpl.getParticipantAliasProperty();

        // only generate the alias hashmap if the alias table has been configured properly
        if (participantAliasDatasetId != null && (targetDef == null || targetDef.getDataSetId() != participantAliasDatasetId)) // do not apply lookups on the alias table itself.
        {
            StudyManager studyManager = StudyManager.getInstance();
            DataSetDefinition aliasDataset = studyManager.getDataSetDefinition(studyImpl, participantAliasDatasetId);
            if (aliasDataset != null) // possible if the alias dataset has been deleted
            {
                TableInfo aliasTableInfo = studyManager.getDataSetDefinition(studyImpl, participantAliasDatasetId).getTableInfo(_user);
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
                    result.put(alias, id);
                }
            }
        }
        return result;
    }

    // for testing
    public ParticipantIdImportHelper(
            TimepointType timetype,
            Date startDate,
            @Nullable String defaultParticipantId,
            Map<String, Double> visitNameMap)
    {
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
    public String translateParticipantId(@Nullable Object p)
    {
        String participantId = null;

        if (null != p)
        {
            if (p instanceof String)
                participantId = (String) p;
            else
                participantId = p.toString();
        }

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
            String result1 = h.translateParticipantId((Object) "IdAnimal01");
            assertEquals(result1, "IdAnimal01_ID");
            String result3 = h.translateParticipantId((Object) "IdAnimal03");
            assertEquals(result3, "IdAnimal03_ID");
        }

        @Test
        public void testEmptyAliasLookup()
        {
            HashMap<String, String> map = new HashMap<>();
            ParticipantIdImportHelper h = new ParticipantIdImportHelper(
                    map
            );
            // the map is empty, so values should be "translated" back to themselves
            String result1 = h.translateParticipantId((Object) "IdAnimal01");
            assertEquals(result1, "IdAnimal01");
            String result3 = h.translateParticipantId((Object) "IdAnimal03");
            assertEquals(result3, "IdAnimal03");
        }
    }
}

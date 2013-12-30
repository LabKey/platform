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

    /*
    static class TestSequenceVisitMap implements SequenceVisitMap
    {
        @Override
        public Visit get(final Double d)
        {
            return new VisitImpl()
            {
                @Override
                public double getSequenceNumMin()
                {
                    return Math.floor(d);
                }

                @Override
                public SequenceHandling getSequenceNumHandlingEnum()
                {
                    return 9999.0==Math.floor(d) ? SequenceHandling.logUniqueByDate : SequenceHandling.normal;
                }
            };
        }
    }

    public static class ParticipantIdTest extends Assert
    {
        private static final double DELTA = 1E-8;
    
        @Test
        public void testEpoch()
        {
            assertEquals(0, convertToDaysSinceEpoch(parseDateTime("1 Jan 1970")));
            assertEquals(0, convertToDaysSinceEpoch(parseDateTime("1 Jan 1970 0:00")));
            assertEquals(0, convertToDaysSinceEpoch(parseDateTime("1 Jan 1970 1:00")));
            assertEquals(0, convertToDaysSinceEpoch(parseDateTime("1 Jan 1970 23:59:59")));
        }

        @Test
        public void testVisitBasedNonDemographic()
        {
            CaseInsensitiveHashMap<Double> map = new CaseInsensitiveHashMap<>();
            map.put("Enrollment",1.0000);
            map.put("SR",9999.0000);
            ParticipantIdImportHelper h = new ParticipantIdImportHelper(
                    TimepointType.VISIT,
                    parseDateTime("1 Jan 2000 1:00pm"),
                    null,
                    map,
                    new TestSequenceVisitMap()
                    );
            assertEquals(null, h.translateParticipantId(null, null));
            assertEquals(null, h.translateParticipantId(null, parseDateTime("1 Jan 2010")));
            assertEquals(1.0, h.translateParticipantId(1.0, null), DELTA);
            assertEquals(1.0, h.translateParticipantId("Enrollment", null), DELTA);
            assertEquals(9999.0000, h.translateParticipantId("SR", null), DELTA);
            assertEquals(9999.0000, h.translateParticipantId(9999.0000, null), DELTA);
            assertEquals(9999.0000, h.translateParticipantId("9999.0000", null), DELTA);
            assertEquals(9999.0001, h.translateParticipantId("9999.0001", parseDateTime("1 Jan 2001")), DELTA);
            assertEquals(9999.0000, h.translateParticipantId("SR", parseDateTime("1 Jan 2000")), DELTA);
            assertEquals(9999.0000, h.translateParticipantId("SR", parseDateTime("1 Jan 2000 01:00")), DELTA);
            assertEquals(9999.0000, h.translateParticipantId("SR", parseDateTime("1 Jan 2000 23:00")), DELTA);
            assertEquals(9999.0001, h.translateParticipantId("SR", parseDateTime("2 Jan 2000")), DELTA);
            assertEquals(9999.0001, h.translateParticipantId("SR", parseDateTime("2 Jan 2000 01:00")), DELTA);
            assertEquals(9999.0001, h.translateParticipantId("SR", parseDateTime("2 Jan 2000 23:00")), DELTA);
            assertEquals(9999.0365, h.translateParticipantId("SR", parseDateTime("31 Dec 2000 23:59:59")), DELTA);
            assertEquals(9999.0366, h.translateParticipantId("SR", parseDateTime("1 Jan 2001")), DELTA);
        }

        @Test
        public void testVisitBasedDemographic()
        {
            CaseInsensitiveHashMap<Double> map = new CaseInsensitiveHashMap<>();
            map.put("Enrollment",1.0000);
            map.put("SR",9999.0000);
            ParticipantIdImportHelper h = new ParticipantIdImportHelper(
                    TimepointType.VISIT,
                    parseDateTime("1 Jan 2000 1:00pm"),
                    42.0000,
                    map,
                    new TestSequenceVisitMap()
                    );
            assertEquals(42.0000, h.translateParticipantId(null, null), DELTA);
            assertEquals(42.0000, h.translateParticipantId(null, parseDateTime("1 Jan 2010")), DELTA);
            assertEquals(1.0, h.translateParticipantId(1.0, null), DELTA);
            assertEquals(1.0, h.translateParticipantId("Enrollment", null), DELTA);
            assertEquals(9999.0000, h.translateParticipantId("SR", null), DELTA);
        }


        // this test does not run stand-alone because of StudyManager.sequenceNumFromDate(date)
        @Test
        public void testDateBasedNotDemographic()
        {
            CaseInsensitiveHashMap<Double> map = new CaseInsensitiveHashMap<>();
            ParticipantIdImportHelper h = new ParticipantIdImportHelper(
                    TimepointType.DATE,
                    parseDateTime("1 Jan 2000 1:00pm"),
                    null,
                    map,
                    new TestSequenceVisitMap()
                    );
            assertEquals(-1.0, h.translateParticipantId(null, null), DELTA);
            assertEquals(20100203.0, h.translateParticipantId(null, parseDateTime("3 Feb 2010")), DELTA);
            assertEquals(20100203.0, h.translateParticipantId(null, parseDateTime("3 Feb 2010 1:00")), DELTA);
            assertEquals(20100203.0, h.translateParticipantId(null, parseDateTime("3 Feb 2010 23:00")), DELTA);
            assertEquals(20100203.0, h.translateParticipantId(20100203.0, null), DELTA);
        }
    }
    */
}

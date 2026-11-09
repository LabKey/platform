/*
 * Copyright (c) 2019-2026 LabKey Corporation
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
package org.labkey.experiment.api;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.NameGenerator;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.test.TestTimeout;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.labkey.api.exp.api.ExperimentService.QueryOptions.SkipBulkRemapCache;

@TestWhen(TestWhen.When.BVT)
@TestTimeout(180)
public class ExperimentStressTest
{
    private static final Logger LOG = LogManager.getLogger(ExperimentStressTest.class);

    @Before
    public void setup()
    {
        Container junit = ContainerManager.getForPath(JunitUtil.getTestContainerPath());
        if (junit != null)
        {
            LOG.info("deleting junit container");
            JunitUtil.deleteTestContainer();
        }
    }

    @AfterClass
    public static void tearDown()
    {
        JunitUtil.deleteTestContainer();
    }

    private final Random random;

    public ExperimentStressTest()
    {
        this.random = new Random(0);
    }

    private boolean randomBool()
    {
        return random.nextBoolean();
    }

    private int randomInt(int max)
    {
        return random.nextInt(max);
    }

    private List<String> insertSamples(User user, Container c, String sampleTypeName, List<String> existingNames, int rowCount, boolean aliquot)
            throws Exception
    {
        String noun = "samples";
        if (existingNames != null)
        {
            if (aliquot)
                noun = "aliquots";
            else
                noun = "derived samples";
        }
        LOG.info("** inserting {} {} ...", rowCount, noun);
        int existingNameCount = existingNames != null ? existingNames.size() : 0;

        // generate some data
        List<Map<String, Object>> samples = new ArrayList<>();
        for (int i = 0; i < rowCount; i++)
        {
            String parentName = null;
            boolean derived = existingNameCount > 0 && randomBool();
            if (derived)
            {
                int parentId = randomInt(existingNameCount-1);
                parentName = existingNames.get(parentId);
            }

            Map<String, Object> row = CaseInsensitiveHashMap.of("age", 100);
            if (parentName != null)
            {
                if (aliquot)
                {
                    row = CaseInsensitiveHashMap.of("AliquotedFrom", parentName);
                }
                else
                {
                    row.put("MaterialInputs/" + sampleTypeName, parentName);
                }
            }
            samples.add(row);
        }

        // perform the insert
        TableInfo ssTable = QueryService.get().getUserSchema(user, c, "samples").getTable(sampleTypeName);
        try (DbScope.Transaction tx = ExperimentService.get().ensureTransaction())
        {
            BatchValidationException errors = new BatchValidationException();
            Map<Enum, Object> options = new HashMap<>();
            options.put(SampleTypeService.ConfigParameters.SkipMaxSampleCounterFunction, true);
            options.put(SampleTypeService.ConfigParameters.SkipAliquotRollup, true); // skip recompute since recompute of roots unavoidably cause deadlock on sql server
            options.put(SkipBulkRemapCache, true);
            List<Map<String,Object>> inserted = ssTable.getUpdateService().insertRows(user, c, samples, errors, options, null);
            if (errors.hasErrors())
                throw errors;

            tx.commit();
            LOG.info("** inserted {} {}", inserted.size(), noun);

            // get the inserted names
            return inserted.stream().map(row -> (String)row.get("name")).collect(Collectors.toList());
        }
    }

    enum RunMode
    {
        nolineage("without lineage", 5, 5, false, false, GEN_ID_NAMES),
        withlineage("with lineage", 5, 5, true, false, GEN_ID_NAMES),
        withaliquot("with aliquots", 2, 2, true, true, GEN_ID_NAMES),
        withsamplecount("with sampleCount", 5, 5, false, false, "S-${sampleCount}"),
        // Every other import inserts roots only, so it never takes the project lock that the aliquot imports take mid-import
        mixedaliquot("with aliquots alongside counter-free inserts", 4, 3, true, true, GEN_ID_NAMES);

        public final String _description;
        public final int _thread;
        public final int _race;
        public final boolean _needParents;
        public final boolean _aliquot;
        public final String _nameExpression;

        RunMode(String description, int thread, int race, boolean needParents, boolean aliquot, String nameExpression)
        {
            _description = description;
            _thread = thread;
            _race = race;
            _needParents = needParents;
            _aliquot = aliquot;
            _nameExpression = nameExpression;
        }
    }

    private static final String GEN_ID_NAMES = "S-${genId}";
    private static final String SAMPLE_COUNT_PROJECT = "_ExperimentStressTestSampleCount";

    @Test
    public void sampleTypeInsertsWithoutLineage() throws Throwable
    {
        _sampleTypeInserts(RunMode.nolineage, JunitUtil.getTestContainer());
    }

    @Test
    public void sampleTypeInsertsWithLineage() throws Throwable
    {
        _sampleTypeInserts(RunMode.withlineage, JunitUtil.getTestContainer());
    }

    @Test
    public void sampleTypeInsertsWithAliquot() throws Throwable
    {
        ExpSampleType st = _sampleTypeInserts(RunMode.withaliquot, JunitUtil.getTestContainer());
        verifyAliquotCounters(st);
    }

    @Test
    public void sampleTypeInsertsWithSampleCount() throws Throwable
    {
        // sampleCount is per project and stays active, so use a fresh project rather than leave it on in /Shared
        User user = TestContext.get().getUser();
        Container leftover = ContainerManager.getForPath(SAMPLE_COUNT_PROJECT);
        if (null != leftover)
            ContainerManager.deleteAll(leftover, user);
        Container project = ContainerManager.ensureContainer(SAMPLE_COUNT_PROJECT, user);
        try
        {
            ExpSampleType st = _sampleTypeInserts(RunMode.withsamplecount, project);

            List<Long> counts = getSampleNames(st).stream().map(name -> Long.parseLong(name.substring("S-".length()))).sorted().toList();
            assertEquals("sampleCount values must be unique and contiguous", LongStream.rangeClosed(1, counts.size()).boxed().toList(), counts);
            assertEquals(counts.size(), SampleTypeService.get().getCurrentCount(NameGenerator.EntityCounter.sampleCount, project));
        }
        finally
        {
            ContainerManager.deleteAll(project, user);
        }
    }

    @Test
    public void sampleTypeInsertsWithAliquotAndCounterFree() throws Throwable
    {
        ExpSampleType st = _sampleTypeInserts(RunMode.mixedaliquot, JunitUtil.getTestContainer());
        verifyAliquotCounters(st);
    }

    private List<String> getSampleNames(ExpSampleType st)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("CpasType"), st.getLSID());
        return new TableSelector(ExperimentService.get().getTinfoMaterial().getColumn("Name"), filter, null).getArrayList(String.class);
    }

    /** Each parent's :withCounter aliquot suffixes must be exactly 1..n */
    private void verifyAliquotCounters(ExpSampleType st)
    {
        TableInfo material = ExperimentService.get().getTinfoMaterial();
        SQLFragment sql = new SQLFragment().unsafeAppend("SELECT p.Name AS Parent, a.Name AS Aliquot FROM ").append(material, "a")
                .unsafeAppend(" JOIN ").append(material, "p").unsafeAppend(" ON a.AliquotedFromLsid = p.LSID WHERE a.CpasType = ?").add(st.getLSID());

        Map<String, List<Long>> suffixesByParent = new HashMap<>();
        new SqlSelector(material.getSchema(), sql).forEach(rs -> {
            String parent = rs.getString("Parent");
            String aliquot = rs.getString("Aliquot");
            suffixesByParent.computeIfAbsent(parent, _ -> new ArrayList<>()).add(Long.parseLong(aliquot.substring(parent.length() + 1)));
        });

        assertFalse("Expected aliquots", suffixesByParent.isEmpty());
        suffixesByParent.forEach((parent, suffixes) ->
                assertEquals("Aliquots of " + parent, LongStream.rangeClosed(1, suffixes.size()).boxed().toList(), suffixes.stream().sorted().toList()));
    }

    private ExpSampleType _sampleTypeInserts(RunMode mode, Container c) throws Throwable
    {
        LOG.info("** starting sample type insert test {}", mode._description);
        final User user = TestContext.get().getUser();

        final String sampleTypeName = "MySamples" + mode.name();

        // create a target sampletype
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("name", "string"));
        props.add(new GWTPropertyDescriptor("age", "int"));
        ExpSampleType st = SampleTypeService.get().createSampleType(c, user, sampleTypeName, null, props, Collections.emptyList(), -1, -1, -1, -1, mode._nameExpression, null);

        // seed samples
        final int rowsToInsert = 50;
        final List<String> firstInsertedNames = insertSamples(user, c, sampleTypeName, null, rowsToInsert, false);

        // if we are inserting without lineage, just ignore the parent names
        final List<String> parentNames = mode._needParents ? firstInsertedNames : null;

        // first level - ensures we have an objectId for the SampleType when inserting with lineage
        insertSamples(user, c, sampleTypeName, parentNames, 5, false);

        final int threads = mode._thread;
        final int races = mode._race;
        LOG.info("** starting racy inserts (threads={}, races={}, rows=" + rowsToInsert + ")", threads, races);
        AtomicInteger inserts = new AtomicInteger();
        JunitUtil.createRaces(() -> {

            try
            {
                Thread.sleep(randomInt(10) * 200);
                if (mode == RunMode.mixedaliquot && inserts.getAndIncrement() % 2 == 1)
                    insertSamples(user, c, sampleTypeName, null, rowsToInsert, false);
                else
                    insertSamples(user, c, sampleTypeName, parentNames, rowsToInsert, mode._aliquot);
            }
            catch (Exception e)
            {
                // game over
                throw new RuntimeException(e);
            }

        }, threads, races, 60);

        return st;
    }

}

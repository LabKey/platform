package org.labkey.experiment.api;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import static org.labkey.api.exp.api.ExperimentService.QueryOptions.SkipBulkRemapCache;

@TestWhen(TestWhen.When.BVT)
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
        LOG.info("** inserting " + rowCount + " " + noun + " " + "...");
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
            LOG.info("** inserted " + inserted.size() + " " + noun);

            // get the inserted names
            return inserted.stream().map(row -> (String)row.get("name")).collect(Collectors.toList());
        }
    }

    enum RunMode
    {
        nolineage("without lineage", 5, 5, false),
        withlineage("with lineage", 5, 5, true),
        withaliquot("with aliquots", 2, 2, true);

        public final String _description;
        public final int _thread;
        public final int _race;
        public final boolean _needParents;

        RunMode(String description, int thread, int race, boolean needParents)
        {
            _description = description;
            _thread = thread;
            _race = race;
            _needParents = needParents;
        }
    }

    @Test
    public void sampleTypeInsertsWithoutLineage() throws Throwable
    {
        _sampleTypeInserts(RunMode.nolineage);
    }

    @Test
    public void sampleTypeInsertsWithLineage() throws Throwable
    {
        _sampleTypeInserts(RunMode.withlineage);
    }

    @Test
    public void sampleTypeInsertsWithAliquot() throws Throwable
    {
        _sampleTypeInserts(RunMode.withaliquot);
    }

    private void _sampleTypeInserts(RunMode mode) throws Throwable
    {
        /*
         * Deadlock on recompute introduced by Issue 47033 has been fixed by Issue 47246.
         * The tests are currently passing when run locally using sql server.
         * However, there are more deadlocks with sql server when run on TC, now on ExperimentRun table.
         */
        Assume.assumeFalse("Issue 47033: Test does not yet pass on SQL Server. Skipping.",
                CoreSchema.getInstance().getSqlDialect().isSqlServer());

        LOG.info("** starting sample type insert test " + mode._description);
        final User user = TestContext.get().getUser();
        final Container c = JunitUtil.getTestContainer();

        final String sampleTypeName = "MySamples" + mode.name();

        // create a target sampletype
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("name", "string"));
        props.add(new GWTPropertyDescriptor("age", "int"));
        ExpSampleType st = SampleTypeService.get().createSampleType(c, user, sampleTypeName, null, props, Collections.emptyList(), -1, -1, -1, -1, "S-${genId}", null);

        // seed samples
        final int rowsToInsert = 50;
        final List<String> firstInsertedNames = insertSamples(user, c, sampleTypeName, null, rowsToInsert, false);

        // if we are inserting without lineage, just ignore the parent names
        final List<String> parentNames = mode._needParents ? firstInsertedNames : null;

        // first level - ensures we have an objectId for the SampleType when inserting with lineage
        insertSamples(user, c, sampleTypeName, parentNames, 5, false);

        final int threads = mode._thread;
        final int races = mode._race;
        LOG.info("** starting racy inserts (threads=" + threads + ", races=" + races + ", rows=" + rowsToInsert + ")");
        JunitUtil.createRaces(() -> {

            try
            {
                Thread.sleep(randomInt(10) * 200);
                insertSamples(user, c, sampleTypeName, parentNames, rowsToInsert, mode == RunMode.withaliquot);
            }
            catch (Exception e)
            {
                // game over
                throw new RuntimeException(e);
            }

        }, threads, races, 60);
    }

}

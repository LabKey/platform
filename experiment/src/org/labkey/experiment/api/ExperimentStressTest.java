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
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

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

    private List<String> insertSamples(User user, Container c, String sampleTypeName, List<String> existingNames, int rowCount)
            throws Exception
    {
        LOG.info("** inserting " + rowCount + " samples " + (existingNames != null ? "with lineage" : "without lineage") + "...");
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
                row.put("MaterialInputs/" + sampleTypeName, parentName);
            }
            samples.add(row);
        }

        // perform the insert
        TableInfo ssTable = QueryService.get().getUserSchema(user, c, "samples").getTable(sampleTypeName);
        try (DbScope.Transaction tx = ExperimentService.get().ensureTransaction())
        {
            BatchValidationException errors = new BatchValidationException();
            List<Map<String,Object>> inserted = ssTable.getUpdateService().insertRows(user, c, samples, errors, null, null);
            if (errors.hasErrors())
                throw errors;

            tx.commit();
            LOG.info("** inserted " + inserted.size() + " samples");

            // get the inserted names
            return inserted.stream().map(row -> (String)row.get("name")).collect(Collectors.toList());
        }
    }

    @Test
    public void sampleTypeInsertsWithoutLineage() throws Throwable
    {
        _sampleTypeInserts(false);
    }

    // Issue 37518: deadlock when concurrently inserting samples with lineage
    @Test
    public void sampleTypeInsertsWithLineage() throws Throwable
    {
        Assume.assumeFalse("Issue 37518: Test does not yet pass on SQL Server. Skipping.", CoreSchema.getInstance().getSqlDialect().isSqlServer());
        _sampleTypeInserts(true);
    }

    private void _sampleTypeInserts(boolean withLineage) throws Throwable
    {
        LOG.info("** starting sample type insert test " + (withLineage ? "with lineage" : "without lineage"));
        final User user = TestContext.get().getUser();
        final Container c = JunitUtil.getTestContainer();

        final String sampleTypeName = "MySamples";

        // create a target sampletype
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("name", "string"));
        props.add(new GWTPropertyDescriptor("age", "int"));
        ExpSampleType st = SampleTypeService.get().createSampleType(c, user, sampleTypeName, null, props, Collections.emptyList(), -1, -1, -1, -1, "S-${genId}", null);

        // seed samples
        final int rowsToInsert = 50;
        final List<String> firstInsertedNames = insertSamples(user, c, sampleTypeName, null, rowsToInsert);

        // if we are inserting without lineage, just ignore the parent names
        final List<String> parentNames = withLineage ? firstInsertedNames : null;

        // first level - ensures we have an objectId for the SampleType when inserting with lineage
        insertSamples(user, c, sampleTypeName, parentNames, 5);

        final int threads = 5;
        final int races = 5;
        LOG.info("** starting racy inserts (threads=" + threads + ", races=" + races + ", rows=" + rowsToInsert + ")");
        JunitUtil.createRaces(() -> {

            try
            {
                Thread.sleep(randomInt(10) * 200);
                insertSamples(user, c, sampleTypeName, parentNames, rowsToInsert);
            }
            catch (Exception e)
            {
                // game over
                throw new RuntimeException(e);
            }

        }, threads, races, 60);
    }

}

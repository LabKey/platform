package org.labkey.experiment.api;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.ExperimentalFeatureService;
import org.labkey.api.test.TestTimeout;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ViewBackgroundInfo;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.labkey.api.util.JunitUtil.deleteTestContainer;

@TestWhen(TestWhen.When.WEEKLY)
@TestTimeout(TestTimeout.DEFAULT * 10)
public class LineagePerfTest extends Assert
{
    private static final Logger LOG = Logger.getLogger(LineagePerfTest.class);

    private static boolean _currentSetting;
    private static User _user;
    private static Container _container;

    @BeforeClass
    public static void setup()
    {
        _currentSetting = ServiceRegistry.get(ExperimentalFeatureService.class).isFeatureEnabled(ExperimentService.EXPERIMENTAL_LINEAGE_PERFORMANCE);
        LOG.info("lineage perf setting currently enabled: " + _currentSetting);

        _user = TestContext.get().getUser();
        assertNotNull("Should have access to a user", _user);

        LOG.info("deleting test container");
        deleteTestContainer();

        LOG.info("creating test container");
        _container = JunitUtil.getTestContainer();
    }

    @AfterClass
    public static void cleanup()
    {
        LOG.info("restoring previous perf setting: " + _currentSetting);
        ServiceRegistry.get(ExperimentalFeatureService.class).setFeatureEnabled(ExperimentService.EXPERIMENTAL_LINEAGE_PERFORMANCE, _currentSetting, _user);
        //deleteTestContainer();
    }


    // create new data
    private int generateNewData(List<Map<String, Object>> data, List<Map<String, Object>> samples, Random random)
    {
        CaseInsensitiveHashMap<Object> row = new CaseInsensitiveHashMap<>();
        String name = "M-" + data.size();
        row.put("name", name);
        row.put("id", data.size());
        data.add(row);
        LOG.debug("New data: " + name);
        return 1;
    }

    // create new sample
    private int generateNewSample(List<Map<String, Object>> data, List<Map<String, Object>> samples, Random random)
    {
        CaseInsensitiveHashMap<Object> row = new CaseInsensitiveHashMap<>();
        String name = "S-" + samples.size();
        row.put("name", name);
        row.put("age", samples.size());
        samples.add(row);
        LOG.debug("New sample: " + name);
        return 1;
    }

    // create new data from 1 or more existing parents
    // NOTE: It is important that the derived data have no sample parents so we can insert all data before inserting samples
    private int generateDerivedData(List<Map<String, Object>> data, List<Map<String, Object>> samples, Random random)
    {
        double d1 = random.nextDouble();
        int numDataParents = d1 < 0.85 ? 1 : 2;

        return generateDerived(data, samples, random, numDataParents, 0, 1, "m");
    }

    // create new sample from 1 or more existing parents
    private int generateDerivedSample(List<Map<String, Object>> data, List<Map<String, Object>> samples, Random random)
    {
        double d1 = random.nextDouble();
        int numDataParents = d1 < 0.65 ? 0 : 1;

        double d2 = random.nextDouble();
        int numSampleParents =
                  d2 < 0.5 ? 1
                : d2 < 0.8 ? 2
                : d2 < 0.9 ? 4
                : 8;

        return generateDerived(data, samples, random, numDataParents, numSampleParents, 1, "s");
    }


    // generate a plate of samples all derived from a single sample
    private int generateDerivedSample(List<Map<String, Object>> data, List<Map<String, Object>> samples, Random random, int numChildren)
    {
        return generateDerived(data, samples, random, 0, 1, numChildren, "p");
    }

    private int generateDerived(List<Map<String, Object>> data, List<Map<String, Object>> samples, Random random, int numDataParents, int numSampleParents, int numChildren, String namePrefix)
    {
        if (numChildren == 0)
            return 0;

        if (data.isEmpty())
            numDataParents = 0;

        if (samples.isEmpty())
            numSampleParents = 0;

        if (numDataParents == 0 && numSampleParents == 0)
            return generateNewSample(data, samples, random);

        LOG.debug("Derive " + numChildren + " from " + numDataParents + " data and " + numSampleParents + " samples");

        String dataParents = pickParents(data, numDataParents, random);
        if (dataParents != null)
            LOG.debug("  data parents:  " + dataParents);

        String sampleParents = pickParents(samples, numSampleParents, random);
        if (sampleParents != null)
            LOG.debug("  sample parents:  " + sampleParents);

        // now generate the derived children
        List<String> childNames = new ArrayList<>();
        for (int i = 0; i < numChildren; i++)
        {
            CaseInsensitiveHashMap<Object> row = new CaseInsensitiveHashMap<>();
            String name = namePrefix + "-" + samples.size();
            row.put("name", name);
            row.put("age", samples.size());
            if (dataParents != null)
                row.put("DataInputs/MyData", dataParents);
            if (sampleParents != null)
                row.put("MaterialInputs/MySamples", sampleParents);
            samples.add(row);
            childNames.add(name);
        }

        LOG.debug("  children: " + StringUtils.join(childNames, ", "));
        return numChildren;
    }

    @Nullable
    private String pickParents(List<Map<String, Object>> items, int numParents, Random random)
    {
        if (numParents == 0)
            return null;

        // reduce the number of requested parents with room to spare
        if (numParents > items.size())
            numParents = items.size() / 2;

        StringBuilder parentNames = new StringBuilder();

        // get a random selection of samples to be used as parents
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < numParents; i++)
        {
            int index;
            while (true)
            {
                index = random.nextInt(items.size());
                if (seen.contains(index))
                {
                    LOG.debug("  spin again");
                    continue;
                }
                break;
            }

            seen.add(index);
            Map<String, Object> parent = items.get(index);
            String name = (String)parent.get("name");
            if (i > 0)
                parentNames.append(",");
            parentNames.append(name);
        }
        return parentNames.toString();
    }

    private Pair<List<Map<String, Object>>, List<Map<String, Object>>> generateSamples(int size)
    {
        long seed = 12345;
        Random random = new Random(seed);
        List<Map<String, Object>> data = new ArrayList<>(size * 2);
        List<Map<String, Object>> samples = new ArrayList<>(size * 2);
        for (int i = 0; i < size; )
        {
            int operation = random.nextInt(12);
            switch (operation) {
                case 0:
                case 1:
                    i += generateNewData(data, samples, random);
                    break;

                case 2:
                case 3:
                case 4:
                    i += generateNewSample(data, samples, random);
                    break;

                case 5:
                case 6:
                case 7:
                    i += generateDerivedData(data, samples, random);
                    break;

                case 8:
                case 9:
                case 10:
                    i += generateDerivedSample(data, samples, random);
                    break;

                case 11:
                    i += generateDerivedSample(data, samples, random, 24);
                    break;

                case 12:
                    i += generateDerivedSample(data, samples, random, 48);
                    break;

                default:
                    break;
            }
        }

        return Pair.of(data, samples);
    }

    @Test
    public void lineagePerformance() throws Exception
    {
        CPUTimer elapsedTimer = new CPUTimer("lineagePerformance");
        CPUTimer generateRowsTimer = new CPUTimer("generateRows");
        CPUTimer insertDataTimer = new CPUTimer("insertData");
        CPUTimer insertSamplesTimer = new CPUTimer("insertSamples");

        CPUTimer oldLineageQuery = new CPUTimer("old lineage query");
        CPUTimer oldLineageGraph = new CPUTimer("old lineage graph");
        CPUTimer oldInsertMoreTimer = new CPUTimer("old insertMore");

        CPUTimer newLineageQuery = new CPUTimer("new lineage query");
        CPUTimer newLineageGraph = new CPUTimer("new lineage graph");
        CPUTimer newInsertMoreTimer = new CPUTimer("new insertMore");

        //
        // SETUP: insert lots of samples derived from each other
        //

        elapsedTimer.start();

        Pair<ExpSampleSet, ExpData> pair = reuseExistingJunk();
        if (pair == null)
            pair = generateJunk(generateRowsTimer, insertDataTimer, insertSamplesTimer);
        ExpSampleSet ss = pair.first;
        ExpData firstData = pair.second;


        //
        // TEST: 10 x (insert a sample, query lineage twice)
        //

        LOG.info("TEST querying with very OLD hotness: ");
        ServiceRegistry.get(ExperimentalFeatureService.class).setFeatureEnabled(ExperimentService.EXPERIMENTAL_LINEAGE_PERFORMANCE, false, _user);
        lineageQueries("OLD", oldLineageQuery, oldLineageGraph, oldInsertMoreTimer, ss, firstData);

        LOG.info("TEST querying with very NEW hotness: ");
        ServiceRegistry.get(ExperimentalFeatureService.class).setFeatureEnabled(ExperimentService.EXPERIMENTAL_LINEAGE_PERFORMANCE, true, _user);
        lineageQueries("NEW", newLineageQuery, newLineageGraph, newInsertMoreTimer, ss, firstData);

        elapsedTimer.stop();


        LOG.info("===");
        LOG.info(CPUTimer.header());
        LOG.info(generateRowsTimer);
        LOG.info(insertDataTimer);
        LOG.info(insertSamplesTimer);
        LOG.info(oldLineageQuery);
        LOG.info(oldLineageGraph);
        LOG.info(oldInsertMoreTimer);
        LOG.info(newLineageQuery);
        LOG.info(newLineageGraph);
        LOG.info(newInsertMoreTimer);
        LOG.info(elapsedTimer);
    }

    public Pair<ExpSampleSet, ExpData> generateJunk(CPUTimer generateRowsTimer, CPUTimer insertDataTimer, CPUTimer insertSamplesTimer)
            throws ExperimentException, SQLException, DuplicateKeyException, BatchValidationException, QueryUpdateServiceException
    {
        ExpSampleSet ss;
        ExpData firstData;

        // Create a DataClass and SampleSet and insert into MyData first, then MySamples
        try (DbScope.Transaction tx = ExperimentService.get().ensureTransaction())
        {
            // Generate lots of samples
            LOG.info("generating data and samples");
            generateRowsTimer.start();
            Pair<List<Map<String, Object>>, List<Map<String, Object>>> generated = generateSamples(30000);
            generateRowsTimer.stop();
            List<Map<String, Object>> data = generated.first;
            List<Map<String, Object>> samples = generated.second;


            // Create DataClass and insert data
            LOG.info("inserting data");
            insertDataTimer.start();
            List<GWTPropertyDescriptor> props = new ArrayList<>();
            props.add(new GWTPropertyDescriptor("age", "int"));
            final ExpDataClass dc = ExperimentService.get().createDataClass(_container, _user, "MyData", null, props, Collections.emptyList(), null, null, null);
            TableInfo dcTable = QueryService.get().getUserSchema(_user, _container, "exp.data").getTable("MyData");
            BatchValidationException errors = new BatchValidationException();
            List<Map<String, Object>> insertedDatas = dcTable.getUpdateService().insertRows(_user, _container, data, errors, null, null);
            if (errors.hasErrors())
                throw errors;
            LOG.info("inserted " + data.size() + " data");
            insertDataTimer.stop();

            Map<String, Object> firstDataMap = insertedDatas.get(0);
            Integer firstDataRowId = (Integer)firstDataMap.get("rowId");
            assertNotNull(firstDataRowId);
            firstData = ExperimentService.get().getExpData(firstDataRowId);
            assertNotNull(firstData);


            // Create SampleSet and insert samples
            LOG.info("inserting samples");
            insertSamplesTimer.start();
            props = new ArrayList<>();
            props.add(new GWTPropertyDescriptor("name", "string"));
            props.add(new GWTPropertyDescriptor("age", "int"));
            ss = ExperimentService.get().createSampleSet(_container, _user, "MySamples", null, props, Collections.emptyList(), -1, -1, -1, -1, null, null);
            TableInfo ssTable = QueryService.get().getUserSchema(_user, _container, "samples").getTable("MySamples");
            errors = new BatchValidationException();
            ssTable.getUpdateService().insertRows(_user, _container, samples, errors, null, null);
            if (errors.hasErrors())
                throw errors;
            LOG.info("inserted " + samples.size() + " samples");
            insertSamplesTimer.stop();

            tx.commit();
            LOG.info("committed tx");
        }

        return Pair.of(ss, firstData);
    }

    private Pair<ExpSampleSet, ExpData> reuseExistingJunk()
    {
        ExpSampleSet ss = ExperimentService.get().getSampleSet(_container, "MySamples", false);
        if (ss == null)
            return null;

        ExpDataClass dc = ExperimentService.get().getDataClass(_container, "MyData");
        if (dc == null)
            return null;

        SimpleFilter filter = SimpleFilter.createContainerFilter(_container);
        filter.addCondition(FieldKey.fromParts("classId"), dc.getRowId());
        Integer firstDataRowId = new TableSelector(ExperimentService.get().getTinfoData(), Collections.singleton("rowId"), filter, new Sort("rowId")).setMaxRows(1).getObject(Integer.class);
        if (firstDataRowId == null)
            return null;

        ExpData data = ExperimentService.get().getExpData(firstDataRowId);
        if (data == null)
            return null;

        LOG.info("found existing data and samples to use; skipping generation of new data");
        return Pair.of(ss, data);
    }

    private void lineageQueries(String prefix, CPUTimer lineageQuery, CPUTimer lineageGraph, CPUTimer insertMoreTimer, ExpSampleSet ss, ExpData firstData) throws ExperimentException
    {
        // parse the query once
        final StringBuilder sql = new StringBuilder()
                .append("SELECT\n")
                .append("  ss.Name,\n")
                .append("  ss.Inputs.Data.MyData.Name AS Inputs_MyData_Name,\n")
                .append("  ss.Inputs.Materials.MySamples.Name AS Inputs_MySamples_Name\n")
                .append("FROM samples.MySamples AS ss\n");

        final UserSchema schema = QueryService.get().getUserSchema(_user, _container, "samples");
        final TableSelector ts = QueryService.get().selector(schema, sql.toString());

        final ExpLineageOptions opt = new ExpLineageOptions();
        final ViewBackgroundInfo info = new ViewBackgroundInfo(_container, _user, null);

        Integer maxMaterialId = new SqlSelector(ExperimentService.get().getSchema(), "SELECT MAX(rowId) FROM exp.material").getObject(Integer.class);

        for (int i = 0; i < 10; i++)
        {
            LOG.info("iteration: " + i);

            // insert a new sample
            LOG.info("  creating sample");
            insertMoreTimer.start();
            String name = prefix + "-" + maxMaterialId + 1 + i;
            Lsid lsid = ss.generateSampleLSID().setObjectId(name).build();
            ExpMaterial sample = ExperimentService.get().createExpMaterial(_container, lsid);
            sample.setCpasType(ss.getLSID());
            sample.save(_user);

            // derive from the first MyData
            LOG.info("  deriving sample from data");
            ExpRun run = ExperimentService.get().derive(Collections.emptyMap(), Collections.singletonMap(firstData, "Data"), Collections.singletonMap(sample, "Sample"), Collections.emptyMap(), info, LOG);

            insertMoreTimer.stop();

            // query the lineage a bunch of times
            LOG.info("  lineage query 1");
            lineageQuery.start();
            Collection<Map<String, Object>> rows1 = ts.getMapCollection();
            lineageQuery.stop();

            LOG.info("  lineage query 2");
            lineageQuery.start();
            Collection<Map<String, Object>> rows2 = ts.getMapCollection();
            lineageQuery.stop();

            assertEquals(rows1.size(), rows2.size());

            LOG.info("  lineage query 3");
            lineageQuery.start();
            ts.getMapCollection();
            lineageQuery.stop();

            LOG.info("  lineage query 4");
            lineageQuery.start();
            ts.getMapCollection();
            lineageQuery.stop();

            LOG.info("  lineage graph 1");
            lineageGraph.start();
            ExperimentService.get().getLineage(sample, opt);
            lineageGraph.stop();

            LOG.info("  lineage graph 2");
            lineageGraph.start();
            ExperimentService.get().getLineage(sample, opt);
            lineageGraph.stop();
        }
    }

}

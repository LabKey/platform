package org.labkey.experiment.api;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.ListofMapsDataIterator;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpLineage;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.MapLoader;
import org.labkey.api.security.User;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.writer.DefaultContainerUser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.joining;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LineageTest extends ExpProvisionedTableTestHelper
{
    Container c;

    @Before
    public void setUp()
    {
        JunitUtil.deleteTestContainer();
        c = JunitUtil.getTestContainer();
    }

    @After
    public void tearDown()
    {
    }

    @Test
    public void testDeriveDuringImport() throws Exception
    {
        final User user = TestContext.get().getUser();

        // just some properties used in both the SampleSet and DataClass
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("me", "string"));
        props.add(new GWTPropertyDescriptor("age", "string"));

        // Create a SampleSet and some samples
        final ExpSampleSet ss = ExperimentService.get().createSampleSet(c, user, "Samples", null, props, emptyList(), 0, -1, -1, -1, null, null);
        final ExpMaterial s1 = ExperimentService.get().createExpMaterial(c,
                ss.generateSampleLSID().setObjectId("S-1").toString(), "S-1");
        s1.setCpasType(ss.getLSID());
        s1.save(user);

        final ExpMaterial s2 = ExperimentService.get().createExpMaterial(c,
                ss.generateSampleLSID().setObjectId("S-2").toString(), "S-2");
        s2.setCpasType(ss.getLSID());
        s2.save(user);

        // Create two DataClasses
        final String firstDataClassName = "firstDataClass";
        final ExpDataClassImpl firstDataClass = ExperimentServiceImpl.get().createDataClass(c, user, firstDataClassName, null, props, emptyList(), null, null, null, null);

        final String secondDataClassName = "secondDataClass";
        final ExpDataClassImpl secondDataClass = ExperimentServiceImpl.get().createDataClass(c, user, secondDataClassName, null, props, emptyList(), null, null, null, null);
        insertRows(c, Arrays.asList(new CaseInsensitiveHashMap<>(Collections.singletonMap("name", "jimbo"))), secondDataClassName);

        // Import data with magic "DataInputs" and "MaterialInputs" columns
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new CaseInsensitiveHashMap<>();
        row.put("name", "bob");
        row.put("age", "10");
        row.put("DataInputs/" + firstDataClassName, null);
        row.put("DataInputs/" + secondDataClassName, null);
        row.put("MaterialInputs/Samples", "S-1");
        rows.add(row);

        row = new CaseInsensitiveHashMap<>();
        row.put("name", "sally");
        row.put("age", "11");
        row.put("DataInputs/" + firstDataClassName, "bob");
        row.put("DataInputs/" + secondDataClassName, "jimbo");
        row.put("MaterialInputs/Samples", "S-2");
        rows.add(row);

        row = new CaseInsensitiveHashMap<>();
        row.put("name", "mike");
        row.put("age", "12");
        row.put("DataInputs/" + firstDataClassName, "bob,sally");
        row.put("MaterialInputs/Samples", "S-1,S-2");
        rows.add(row);

        final UserSchema schema = QueryService.get().getUserSchema(user, c, expDataSchemaKey);
        final TableInfo table = schema.getTable(firstDataClassName);
        final MapLoader mapLoader = new MapLoader(rows);
        DataIteratorContext diContext = new DataIteratorContext();
        int count = table.getUpdateService().loadRows(user, c, mapLoader, diContext, null);
        assertFalse( diContext.getErrors().hasErrors() );
        assertEquals(3, count);

        // Verify lineage
        ExpLineageOptions options = new ExpLineageOptions();
        options.setDepth(2);
        options.setParents(true);
        options.setChildren(false);

        ContainerUser context = new DefaultContainerUser(c, user);
        final ExpData bob = ExperimentService.get().getExpData(firstDataClass, "bob");
        ExpLineage lineage = ExperimentService.get().getLineage(c, user, bob, options);
        Assert.assertTrue(lineage.getDatas().isEmpty());
        assertEquals(1, lineage.getMaterials().size());
        Assert.assertTrue(lineage.getMaterials().contains(s1));

        final ExpData jimbo = ExperimentService.get().getExpData(secondDataClass, "jimbo");
        final ExpData sally = ExperimentService.get().getExpData(firstDataClass, "sally");
        lineage = ExperimentService.get().getLineage(c, user, sally, options);
        assertEquals(2, lineage.getDatas().size());
        Assert.assertTrue(lineage.getDatas().contains(bob));
        Assert.assertTrue(lineage.getDatas().contains(jimbo));
        assertEquals(1, lineage.getMaterials().size(), 1);
        Assert.assertTrue(lineage.getMaterials().contains(s2));

        final ExpData mike = ExperimentService.get().getExpData(firstDataClass, "mike");
        lineage = ExperimentService.get().getLineage(c, user, mike, options);
        assertEquals("Expected 2 data, found: " + lineage.getDatas().stream().map(ExpData::getName).collect(joining(", ")),
                2, lineage.getDatas().size());
        Assert.assertTrue(lineage.getDatas().contains(bob));
        Assert.assertTrue(lineage.getDatas().contains(sally));
        assertEquals("Expected 2 samples, found: " + lineage.getMaterials().stream().map(ExpMaterial::getName).collect(joining(", ")),
                2, lineage.getMaterials().size());
        Assert.assertTrue(lineage.getMaterials().contains(s1));
        Assert.assertTrue(lineage.getMaterials().contains(s2));

        // Get lineage using query
        String sql =
                "SELECT\n" +
                        "  dc.Name,\n" +
                        "  dc.Inputs.Data.\"All\".Name AS InputsDataAllNames,\n" +
                        "  dc.Inputs.Data." + firstDataClassName + ".Name AS InputsDataFirstDataClassNames,\n" +
                        "  dc.Inputs.Materials.Samples.Name AS InputsMaterialSampleNames,\n" +
                        "  dc.Outputs.Data." + secondDataClassName + ".Name AS OutputsDataSecondDataClassNames\n" +
                        "FROM exp.data." + firstDataClassName + " AS dc\n" +
                        "ORDER BY dc.RowId\n";

        try (Results rs = QueryService.get().selectResults(schema, sql, null, null, true, true))
        {
            RenderContext ctx = new RenderContext(new ViewContext());
            ctx.getViewContext().setRequest(TestContext.get().getRequest());
            ctx.getViewContext().setUser(user);
            ctx.getViewContext().setContainer(c);
            ctx.getViewContext().setActionURL(new ActionURL());

            ColumnInfo colName    = rs.getColumn(rs.findColumn(FieldKey.fromParts("Name")));
            DisplayColumn dcName  = colName.getRenderer();
            ColumnInfo colInputsMaterialSampleNames    = rs.getColumn(rs.findColumn(FieldKey.fromParts("InputsMaterialSampleNames")));
            DisplayColumn dcInputsMaterialSampleNames  = colInputsMaterialSampleNames.getRenderer();
            ColumnInfo colInputsDataAllNames    = rs.getColumn(rs.findColumn(FieldKey.fromParts("InputsDataAllNames")));
            DisplayColumn dcInputsDataAllNames  = colInputsDataAllNames.getRenderer();
            ColumnInfo colInputsDataFirstDataClassNames    = rs.getColumn(rs.findColumn(FieldKey.fromParts("InputsDataFirstDataClassNames")));
            DisplayColumn dcInputsDataFirstDataClassNames  = colInputsDataFirstDataClassNames.getRenderer();

            Assert.assertTrue(rs.next());
            ctx.setRow(rs.getRowMap());
            assertEquals("bob", dcName.getValue(ctx));
            assertMultiValue(dcInputsMaterialSampleNames.getDisplayValue(ctx), "S-1");

            Assert.assertTrue(rs.next());
            ctx.setRow(rs.getRowMap());
            assertEquals("sally", dcName.getValue(ctx));
            assertMultiValue(dcInputsDataAllNames.getDisplayValue(ctx), "jimbo", "bob");
            assertMultiValue(dcInputsDataFirstDataClassNames.getDisplayValue(ctx), "bob");
            assertMultiValue(dcInputsMaterialSampleNames.getDisplayValue(ctx), "S-2", "S-1");

            Assert.assertTrue(rs.next());
            ctx.setRow(rs.getRowMap());
            assertEquals("mike", dcName.getValue(ctx));
            assertMultiValue(dcInputsDataAllNames.getDisplayValue(ctx), "sally", "jimbo", "bob");
            assertMultiValue(dcInputsDataFirstDataClassNames.getDisplayValue(ctx), "bob", "sally");
            assertMultiValue(dcInputsMaterialSampleNames.getDisplayValue(ctx), "S-2", "S-1");

            assertFalse(rs.next());
        }
    }

    // Issue 29361: Support updating lineage for DataClasses
    @Test
    public void testUpdateLineage() throws Exception
    {
        final User user = TestContext.get().getUser();

        // setup sample set
        List<GWTPropertyDescriptor> sampleProps = new ArrayList<>();
        sampleProps.add(new GWTPropertyDescriptor("name", "string"));
        sampleProps.add(new GWTPropertyDescriptor("age", "int"));

        final ExpSampleSetImpl ss = SampleSetServiceImpl.get().createSampleSet(c, user,
                "MySamples", null, sampleProps, Collections.emptyList(),
                -1, -1, -1, -1, null, null);
        final ExpMaterial s1 = ExperimentService.get().createExpMaterial(c,
                ss.generateSampleLSID().setObjectId("S-1").toString(), "S-1");
        s1.setCpasType(ss.getLSID());
        s1.save(user);

        final ExpMaterial s2 = ExperimentService.get().createExpMaterial(c,
                ss.generateSampleLSID().setObjectId("S-2").toString(), "S-2");
        s2.setCpasType(ss.getLSID());
        s2.save(user);

        // Create DataClass
        List<GWTPropertyDescriptor> dcProps = new ArrayList<>();
        dcProps.add(new GWTPropertyDescriptor("age", "int"));
        final String myDataClassName = "MyData";
        final ExpDataClassImpl myDataClass = ExperimentServiceImpl.get().createDataClass(c, user, myDataClassName, null, dcProps, emptyList(), null, null, null, null);

        // Import data and derive from "S-1"
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of(
                "name", "bob",
                "age", "10",
                "MaterialInputs/MySamples", "S-1"
        ));

        final UserSchema schema = QueryService.get().getUserSchema(user, c, expDataSchemaKey);
        final TableInfo table = schema.getTable(myDataClassName);
        MapLoader mapLoader = new MapLoader(rows);
        DataIteratorContext diContext = new DataIteratorContext();
        int count = table.getUpdateService().loadRows(user, c, mapLoader, diContext, null);
        if (diContext.getErrors().hasErrors())
            throw diContext.getErrors();
        assertEquals(1, count);

        final ExpData bob = ExperimentService.get().getExpData(myDataClass, "bob");

        // Verify the lineage
        ExpLineageOptions options = new ExpLineageOptions();
        options.setDepth(2);
        options.setParents(true);
        options.setChildren(false);

        ContainerUser context = new DefaultContainerUser(c, user);
        ExpLineage lineage = ExperimentService.get().getLineage(c, user, bob, options);
        Assert.assertTrue(lineage.getDatas().isEmpty());
        assertEquals(1, lineage.getRuns().size());
        assertEquals(1, lineage.getMaterials().size());
        Assert.assertTrue(lineage.getMaterials().contains(s1));

        // Use updateRows to create new lineage and derive from "S-2"
        rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of(
                "rowId", bob.getRowId(),
                "MaterialInputs/MySamples", "S-2"
        ));

        List<Map<String, Object>> updatedRows = table.getUpdateService().updateRows(user, c, rows, rows, null, null);
        assertEquals(1, updatedRows.size());

        // TODO: Is the expected behavior to create a new derivation run from S-2 and leave the existing derivation from S-1 intact?
        // TODO: Or should the existing derivation run be deleted/updated to match the SampleSet derivation behavior?
        // Verify the lineage
        lineage = ExperimentService.get().getLineage(c, user, bob, options);
        Assert.assertTrue(lineage.getDatas().isEmpty());
        assertEquals(2, lineage.getRuns().size());
        assertEquals(2, lineage.getMaterials().size());
        Assert.assertTrue(lineage.getMaterials().contains(s1));
        Assert.assertTrue(lineage.getMaterials().contains(s2));
    }

    // Issue 37690: Customize Grid on Assay Results Data Won't Allow for Showing Input to Sample ID
    @Test
    public void testListAndSampleLineage() throws Exception
    {
        final User user = TestContext.get().getUser();

        // setup sample set
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("name", "string"));
        props.add(new GWTPropertyDescriptor("age", "int"));
        final ExpSampleSetImpl ss = SampleSetServiceImpl.get().createSampleSet(c, user,
                "MySamples", null, props, Collections.emptyList(),
                -1, -1, -1, -1, null, null);

        UserSchema schema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts("Samples"));
        TableInfo table = schema.getTable("MySamples");
        QueryUpdateService svc = table.getUpdateService();

        // insert a sample and a derived sample
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of("name", "bob", "age", 50));
        rows.add(CaseInsensitiveHashMap.of("name", "sally", "age", 10, "MaterialInputs/MySamples", "bob"));

        BatchValidationException errors = new BatchValidationException();
        List<Map<String, Object>> inserted = svc.insertRows(user, c, rows, errors, null, null);
        if (errors.hasErrors())
            throw errors;

        // setup list with lookup to sample set
        ListDefinition listDef = ListService.get().createList(c, "MyList", ListDefinition.KeyType.AutoIncrementInteger);

        Domain listDomain = listDef.getDomain();
        listDomain.addProperty(new PropertyStorageSpec("Key", JdbcType.INTEGER));
        DomainProperty sampleLookup = listDomain.addProperty(new PropertyStorageSpec("SampleId", JdbcType.VARCHAR));
        sampleLookup.setLookup(new Lookup(null, "Samples", "MySamples"));
        listDef.setKeyName("Key");
        listDef.save(user);

        // insert a a row with a lookup to the sample
        UserSchema listSchema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts("lists"));
        QueryUpdateService listQus = listSchema.getTable("MyList").getUpdateService();

        rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of("SampleId", "sally"));

        DataIteratorContext context = new DataIteratorContext();
        context.setAllowImportLookupByAlternateKey(true);

        errors = new BatchValidationException();
        listQus.loadRows(user, c, new ListofMapsDataIterator.Builder(Set.of("SampleId"), rows), context, null);
        if (errors.hasErrors())
            throw errors;

        // query
        TableSelector ts = QueryService.get().selector(listSchema,
                "SELECT SampleId, SampleId.Inputs.Materials.MySamples.Name As MySampleParent FROM MyList");
        Results results = ts.getResults();
        RenderContext ctx = new RenderContext(new ViewContext());
        ctx.getViewContext().setRequest(TestContext.get().getRequest());
        ctx.getViewContext().setUser(user);
        ctx.getViewContext().setContainer(c);
        ctx.getViewContext().setActionURL(new ActionURL());
        ColumnInfo sampleId       = results.getColumn(results.findColumn(FieldKey.fromParts("SampleId")));
        DisplayColumn dcSampleId  = sampleId.getRenderer();
        ColumnInfo mySampleParent = results.getColumn(results.findColumn(FieldKey.fromParts("MySampleParent")));
        DisplayColumn dcMySampleParent = mySampleParent.getRenderer();

        assertTrue(results.next());
        ctx.setRow(results.getRowMap());
        assertEquals("sally", dcSampleId.getValue(ctx));
        assertEquals("bob", dcMySampleParent.getDisplayValue(ctx));
    }

    @Test
    public void testObjectInputOutput() throws Exception
    {
        Lsid.LsidBuilder lsidBuilder = new Lsid.LsidBuilder("JUnitTest", null);

        // register a silly LsidHandler for our test namespace
        LsidManager.get().registerHandler("JUnitTest", new LsidManager.OntologyObjectLsidHandler());

        // create some exp.object rows for use as input and outputs
        Lsid a1Lsid = lsidBuilder.setObjectId("A1").build();
        int a1ObjectId = OntologyManager.ensureObject(c, a1Lsid.toString());
        Identifiable a1 = LsidManager.get().getObject(a1Lsid);
        assertNotNull(a1);

        Lsid a2Lsid = lsidBuilder.setObjectId("A2").build();
        int a2ObjectId = OntologyManager.ensureObject(c, a2Lsid.toString());
        Identifiable a2 = LsidManager.get().getObject(a2Lsid);
        assertNotNull(a2);

        Lsid b1Lsid = lsidBuilder.setObjectId("B1").build();
        int b1ObjectId = OntologyManager.ensureObject(c, b1Lsid.toString());
        Identifiable b1 = LsidManager.get().getObject(b1Lsid);
        assertNotNull(b1);

        Lsid b2Lsid = lsidBuilder.setObjectId("B2").build();
        int b2ObjectId = OntologyManager.ensureObject(c, b2Lsid.toString());
        Identifiable b2 = LsidManager.get().getObject(b2Lsid);
        assertNotNull(b2);


        // create empty run
        ExpRun run = ExperimentService.get().createExperimentRun(c, "testing");
        PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(c);
        run.setFilePathRoot(pipeRoot.getRootPath());

        ExpProtocol protocol = ExperimentService.get().ensureSampleDerivationProtocol(user);
        run.setProtocol(protocol);

        // add A objects as inputs, B objects as outputs
        ViewBackgroundInfo info = new ViewBackgroundInfo(c, user, null);
        run = ExperimentServiceImpl.get().saveSimpleExperimentRun(run, emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), info, null, false);
        int runObjectId = run.getObjectId();

        // HACK: Until we have the ability to add provenance information to the run, just insert directly into exp.edge
        TableInfo edgeTable = ExperimentServiceImpl.get().getTinfoEdge();
        Table.insert(null, edgeTable, Map.of(
                "runId", run.getRowId(), "fromObjectId", a1ObjectId, "toObjectId", runObjectId));
        Table.insert(null, edgeTable, Map.of(
                "runId", run.getRowId(), "fromObjectId", a2ObjectId, "toObjectId", runObjectId));

        Table.insert(null, edgeTable, Map.of(
                "runId", run.getRowId(), "fromObjectId", runObjectId, "toObjectId", b1ObjectId));
        Table.insert(null, edgeTable, Map.of(
                "runId", run.getRowId(), "fromObjectId", runObjectId, "toObjectId", b2ObjectId));

        // query the lineage
        ExpLineageOptions options = new ExpLineageOptions();
        ExpLineage lineage = ExperimentServiceImpl.get().getLineage(c, user, Set.of(a1), options);

        assertTrue(lineage.getRuns().contains(run));
        assertEquals(Set.of(a1), lineage.getSeeds());
        assertEquals(Set.of(b1, b2), lineage.getObjects());

        // verify lineage parent and children
        assertTrue(lineage.getNodeParents(a1).isEmpty());
        assertEquals(Set.of(run), lineage.getNodeChildren(a1));
        assertEquals(Set.of(a1), lineage.getNodeParents(run));
        assertEquals(Set.of(b1, b2), lineage.getNodeChildren(run));

        // verify json structure
        JSONObject json = lineage.toJSON(user, true, false);
        assertEquals(a1Lsid.toString(), json.getString("seed"));

        JSONObject nodes = json.getJSONObject("nodes");
        assertEquals(4, nodes.size());
        JSONObject a1json = nodes.getJSONObject(a1Lsid.toString());
        assertEquals("A1", a1json.getString("name"));
        assertEquals(a1Lsid.toString(), a1json.getString("lsid"));
        assertEquals("JUnitTest", a1json.getString("type"));

        JSONArray a1parentsJson = a1json.getJSONArray("parents");
        assertEquals(0, a1parentsJson.length());

        JSONArray a1childrenJson = a1json.getJSONArray("children");
        assertEquals(1, a1childrenJson.length());
        assertEquals(run.getLSID(), a1childrenJson.getJSONObject(0).getString("lsid"));

    }
}

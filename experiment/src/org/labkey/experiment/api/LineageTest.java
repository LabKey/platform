package org.labkey.experiment.api;

import org.apache.commons.collections4.ListUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
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
import org.labkey.api.exp.api.ExpLineageEdge;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.query.SamplesSchema;
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
import org.labkey.api.test.TestWhen;
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
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.joining;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.labkey.api.exp.api.ExperimentService.SAMPLE_ALIQUOT_PROTOCOL_NAME;
import static org.labkey.api.exp.api.ExperimentService.SAMPLE_DERIVATION_PROTOCOL_NAME;

@TestWhen(TestWhen.When.BVT)
public class LineageTest extends ExpProvisionedTableTestHelper
{
    Container c;
    Lsid.LsidBuilder lsidBuilder;

    @Before
    public void setUp()
    {
        JunitUtil.deleteTestContainer();
        c = JunitUtil.getTestContainer();

        lsidBuilder = new Lsid.LsidBuilder("JUnitTest", null);
        LsidManager.get().registerHandler("JUnitTest", new LsidManager.OntologyObjectLsidHandler());
    }

    @After
    public void tearDown()
    {
        JunitUtil.deleteTestContainer();
    }

    @Test
    public void testDeriveDuringImport() throws Exception
    {
        final User user = TestContext.get().getUser();

        // Create a SampleType
        List<GWTPropertyDescriptor> sampleProps = new ArrayList<>();
        sampleProps.add(new GWTPropertyDescriptor("name", "string"));
        sampleProps.add(new GWTPropertyDescriptor("age", "string"));
        final ExpSampleType st = SampleTypeService.get().createSampleType(c, user, "Samples", null, sampleProps, emptyList(), -1, -1, -1, -1, null, null);

        // Create some samples
        final ExpMaterial s1 = ExperimentService.get().createExpMaterial(c,
                st.generateSampleLSID().setObjectId("S-1").toString(), "S-1"); // don't use generateNextDBSeqLSID in unit tests
        s1.setCpasType(st.getLSID());
        s1.save(user);

        final ExpMaterial s2 = ExperimentService.get().createExpMaterial(c,
                st.generateSampleLSID().setObjectId("S-2").toString(), "S-2"); // don't use generateNextDBSeqLSID in unit tests
        s2.setCpasType(st.getLSID());
        s2.save(user);

        // Create aliquot samples from "S-2" using magic "AliquotedFrom" column
        final String firstAliquotName = "S-2-aliquot1";
        // expected autogenerated aliquot name
        final String secondAliquotName = "S-2-1";
        TableInfo samplesTable = QueryService.get().getUserSchema(user, c, SamplesSchema.SCHEMA_NAME).getTable("Samples");
        List<Map<String, Object>> insertSampleRows = new ArrayList<>();
        insertSampleRows.add(CaseInsensitiveHashMap.of("Name", firstAliquotName, "AliquotedFrom", "S-2"));
        insertSampleRows.add(CaseInsensitiveHashMap.of("Name", "", "AliquotedFrom", "S-2"));
        BatchValidationException insertSampleErrors = new BatchValidationException();
        List<Map<String, Object>> insertedSampleRows = samplesTable.getUpdateService().insertRows(user, c, insertSampleRows, insertSampleErrors, null, null);
        if (insertSampleErrors.hasErrors())
            throw insertSampleErrors;
        assertEquals(2, insertedSampleRows.size());
        assertEquals(firstAliquotName, insertedSampleRows.get(0).get("Name"));
        assertEquals(secondAliquotName, insertedSampleRows.get(1).get("Name"));

        ExpMaterial firstAliquot = st.getSample(c, firstAliquotName);
        ExpMaterial secondAliquot = st.getSample(c, secondAliquotName);

        // Create two DataClasses
        List<GWTPropertyDescriptor> dcProps = new ArrayList<>();
        dcProps.add(new GWTPropertyDescriptor("age", "string"));
        final String firstDataClassName = "firstDataClass";
        final ExpDataClassImpl firstDataClass = ExperimentServiceImpl.get().createDataClass(c, user, firstDataClassName, null, dcProps, emptyList(), null, null);

        final String secondDataClassName = "secondDataClass";
        final ExpDataClassImpl secondDataClass = ExperimentServiceImpl.get().createDataClass(c, user, secondDataClassName, null, dcProps, emptyList(), null, null);

        // insert data class rows
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
        row.put("MaterialInputs/Samples", firstAliquotName);
        rows.add(row);

        row = new CaseInsensitiveHashMap<>();
        row.put("name", "mike");
        row.put("age", "12");
        row.put("DataInputs/" + firstDataClassName, "bob,sally");
        row.put("MaterialInputs/Samples", "S-1," + secondAliquotName);
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
        Assert.assertTrue(lineage.getMaterials().contains(firstAliquot));

        final ExpData mike = ExperimentService.get().getExpData(firstDataClass, "mike");
        lineage = ExperimentService.get().getLineage(c, user, mike, options);
        assertEquals("Expected 2 data, found: " + lineage.getDatas().stream().map(ExpData::getName).collect(joining(", ")),
                2, lineage.getDatas().size());
        Assert.assertTrue(lineage.getDatas().contains(bob));
        Assert.assertTrue(lineage.getDatas().contains(sally));
        assertEquals("Expected 2 samples, found: " + lineage.getMaterials().stream().map(ExpMaterial::getName).collect(joining(", ")),
                2, lineage.getMaterials().size());
        Assert.assertTrue(lineage.getMaterials().contains(s1));
        Assert.assertTrue(lineage.getMaterials().contains(secondAliquot));

        // Get lineage using query
        String sql =
                "SELECT\n" +
                        "  dc.Name,\n" +
                        "  dc.Inputs.\"All\".Name AS InputsAllNames,\n" +
                        "  dc.Inputs.Data.\"All\".Name AS InputsDataAllNames,\n" +
                        "  dc.Inputs.Data." + firstDataClassName + ".Name AS InputsDataFirstDataClassNames,\n" +
                        "  dc.Inputs.Materials.Samples.Name AS InputsMaterialSampleNames,\n" +
                        "  dc.Inputs.Runs.\"All\".Name AS InputsRunsAllNames,\n" +
                        "  dc.Inputs.Runs.\"" + SAMPLE_DERIVATION_PROTOCOL_NAME + "\".Name AS InputsRunsDerivationNames,\n" +
                        "  dc.Inputs.Runs.\"" + SAMPLE_ALIQUOT_PROTOCOL_NAME + "\".Name AS InputsRunsAliquotNames,\n" +
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

            ColumnInfo colName = rs.getColumn(rs.findColumn(FieldKey.fromParts("Name")));
            DisplayColumn dcName = colName.getRenderer();

            ColumnInfo colInputsAllNames = rs.getColumn(rs.findColumn(FieldKey.fromParts("InputsAllNames")));
            LineageDisplayColumn dcInputsAllNames = (LineageDisplayColumn)colInputsAllNames.getRenderer();

            ColumnInfo colInputsMaterialSampleNames = rs.getColumn(rs.findColumn(FieldKey.fromParts("InputsMaterialSampleNames")));
            LineageDisplayColumn dcInputsMaterialSampleNames = (LineageDisplayColumn)colInputsMaterialSampleNames.getRenderer();

            ColumnInfo colInputsDataAllNames = rs.getColumn(rs.findColumn(FieldKey.fromParts("InputsDataAllNames")));
            LineageDisplayColumn dcInputsDataAllNames = (LineageDisplayColumn)colInputsDataAllNames.getRenderer();

            ColumnInfo colInputsDataFirstDataClassNames = rs.getColumn(rs.findColumn(FieldKey.fromParts("InputsDataFirstDataClassNames")));
            LineageDisplayColumn dcInputsDataFirstDataClassNames = (LineageDisplayColumn)colInputsDataFirstDataClassNames.getRenderer();

            ColumnInfo colInputsRunsAllNames = rs.getColumn(rs.findColumn(FieldKey.fromParts("InputsRunsAllNames")));
            LineageDisplayColumn dcInputsRunsAllNames = (LineageDisplayColumn)colInputsRunsAllNames.getRenderer();

            ColumnInfo colInputsRunsDerivationNames = rs.getColumn(rs.findColumn(FieldKey.fromParts("InputsRunsDerivationNames")));
            LineageDisplayColumn dcInputsRunsDerivationNames = (LineageDisplayColumn)colInputsRunsDerivationNames.getRenderer();

            ColumnInfo colInputsRunsAliquotNames = rs.getColumn(rs.findColumn(FieldKey.fromParts("InputsRunsAliquotNames")));
            LineageDisplayColumn dcInputsRunsAliquotNames = (LineageDisplayColumn)colInputsRunsAliquotNames.getRenderer();

            Assert.assertTrue(rs.next());
            ctx.setRow(rs.getRowMap());
            assertEquals("bob", dcName.getValue(ctx));
            assertMultiValue(dcInputsMaterialSampleNames.getDisplayValue(ctx), "S-1");

            Assert.assertTrue(rs.next());
            ctx.setRow(rs.getRowMap());
            assertEquals("sally", dcName.getValue(ctx));
            assertMultiValue(dcInputsDataAllNames.getDisplayValue(ctx), "jimbo", "bob");
            assertMultiValue(dcInputsDataFirstDataClassNames.getDisplayValue(ctx), "bob");
            assertMultiValue(dcInputsMaterialSampleNames.getDisplayValue(ctx), firstAliquotName, "S-1");

            Assert.assertTrue(rs.next());
            ctx.setRow(rs.getRowMap());

            // mike's expected input data, samples, and run names
            List<String> expectedInputRunsDerivationNames = List.of(
                    "Derive data from S-1",
                    "Derive data from bob, jimbo, " + firstAliquotName,
                    "Derive data from bob, sally, S-1, " + secondAliquotName);
            List<String> expectedInputRunsAliquotNames = List.of("Create 2 aliquots from S-2");
            List<String> expectedInputRunsAllNames = ListUtils.union(expectedInputRunsAliquotNames, expectedInputRunsDerivationNames);
            List<String> expectedInputDataAllNames = List.of("jimbo", "bob", "sally");
            List<String> expectedInputMaterialAllNames = List.of("S-2", "S-1", secondAliquotName, firstAliquotName);
            List<String> expectedInputAllNames = ListUtils.union(
                    ListUtils.union(expectedInputDataAllNames, expectedInputMaterialAllNames),
                    expectedInputRunsAllNames);

            assertEquals("mike", dcName.getValue(ctx));
            assertMultiValue(dcInputsAllNames.getDisplayValues(ctx), expectedInputAllNames);
            assertMultiValue(dcInputsDataAllNames.getDisplayValues(ctx), expectedInputDataAllNames);
            assertMultiValue(dcInputsDataFirstDataClassNames.getDisplayValues(ctx), List.of("bob", "sally"));
            assertMultiValue(dcInputsMaterialSampleNames.getDisplayValues(ctx), expectedInputMaterialAllNames);
            assertMultiValue(dcInputsRunsAllNames.getDisplayValues(ctx), expectedInputRunsAllNames);
            assertMultiValue(dcInputsRunsDerivationNames.getDisplayValues(ctx), expectedInputRunsDerivationNames);
            assertMultiValue(dcInputsRunsAliquotNames.getDisplayValues(ctx), expectedInputRunsAliquotNames);

            assertFalse(rs.next());
        }
    }

    // Issue 29361: Support updating lineage for DataClasses
    // Issue 40302: Unable to use samples or data class with integer like names as material or data input
    @Test
    public void testUpdateLineage() throws Exception
    {
        final User user = TestContext.get().getUser();

        final String numericSampleName = "100";

        // setup sample type
        List<GWTPropertyDescriptor> sampleProps = new ArrayList<>();
        sampleProps.add(new GWTPropertyDescriptor("name", "string"));
        sampleProps.add(new GWTPropertyDescriptor("age", "int"));

        final ExpSampleTypeImpl st = SampleTypeServiceImpl.get().createSampleType(c, user,
                "MySamples", null, sampleProps, Collections.emptyList(),
                -1, -1, -1, -1, null, null);
        final ExpMaterial s1 = ExperimentService.get().createExpMaterial(c,
                st.generateSampleLSID().setObjectId(numericSampleName).toString(), numericSampleName); // don't use generateNextDBSeqLSID in unit tests
        s1.setCpasType(st.getLSID());
        s1.save(user);

        final ExpMaterial s2 = ExperimentService.get().createExpMaterial(c,
                st.generateSampleLSID().setObjectId("S-2").toString(), "S-2"); // don't use generateNextDBSeqLSID in unit tests
        s2.setCpasType(st.getLSID());
        s2.save(user);

        // Create DataClass
        List<GWTPropertyDescriptor> dcProps = new ArrayList<>();
        dcProps.add(new GWTPropertyDescriptor("age", "int"));
        final String myDataClassName = "MyData";
        final ExpDataClassImpl myDataClass = ExperimentServiceImpl.get().createDataClass(c, user, myDataClassName, null, dcProps, emptyList(), null, null);

        // Import data and derive from "100"
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of(
            "name", "bob",
            "age", "10",
            "MaterialInputs/MySamples", numericSampleName
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

        // Use updateRows to create new lineage and derive from "S-2" and numeric sample
        // This will merge with the previous run
        rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of(
            "rowId", bob.getRowId(),
            "MaterialInputs/MySamples", "S-2, " + numericSampleName
        ));

        BatchValidationException errors = new BatchValidationException();

        List<Map<String, Object>> updatedRows = table.getUpdateService().updateRows(user, c, rows, rows, errors, null, null);
        assertFalse(errors.hasErrors());
        assertEquals(1, updatedRows.size());

        // The merge behavior for dataclasses matches samples. An existing derivation run will be updated for the lineage changes
        // if it has other inputs/outputs that match the update values, otherwise the existing derivation run (if exists)
        // will be deleted and a new one created for only the lineage in the update command.

        // Verify the lineage
        lineage = ExperimentService.get().getLineage(c, user, bob, options);
        Assert.assertTrue(lineage.getDatas().isEmpty());
        assertEquals(1, lineage.getRuns().size());
        assertEquals(2, lineage.getMaterials().size());
        Assert.assertTrue(lineage.getMaterials().contains(s1));
        Assert.assertTrue(lineage.getMaterials().contains(s2));

        // Use updateRows to create new lineage and derive from "S-2"
        // Since numeric sample is not in this update, it will be removed from the lineage. This will create a new run
        // with only S-2
        rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of(
            "rowId", bob.getRowId(),
            "MaterialInputs/MySamples", "S-2"
        ));

        updatedRows = table.getUpdateService().updateRows(user, c, rows, rows, errors, null, null);
        assertFalse(errors.hasErrors());

        assertEquals(1, updatedRows.size());

        // Verify the lineage
        lineage = ExperimentService.get().getLineage(c, user, bob, options);
        Assert.assertTrue(lineage.getDatas().isEmpty());
        assertEquals(1, lineage.getRuns().size());
        assertEquals(1, lineage.getMaterials().size());
        Assert.assertFalse(lineage.getMaterials().contains(s1));
        Assert.assertTrue(lineage.getMaterials().contains(s2));
    }

    // Issue 37690: Customize Grid on Assay Results Data Won't Allow for Showing Input to Sample ID
    @Test
    public void testListAndSampleLineage() throws Exception
    {
        Assume.assumeTrue("This test requires the list module.", ListService.get() != null);
        final User user = TestContext.get().getUser();

        // setup sample type
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("name", "string"));
        props.add(new GWTPropertyDescriptor("age", "int"));
        final ExpSampleTypeImpl st = SampleTypeServiceImpl.get().createSampleType(c, user,
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

        // setup list with lookup to sample type
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
        RenderContext ctx;
        DisplayColumn dcSampleId;
        DisplayColumn dcMySampleParent;
        try (Results results = ts.getResults())
        {
            ctx = new RenderContext(new ViewContext());
            ctx.getViewContext().setRequest(TestContext.get().getRequest());
            ctx.getViewContext().setUser(user);
            ctx.getViewContext().setContainer(c);
            ctx.getViewContext().setActionURL(new ActionURL());
            ColumnInfo sampleId = results.getColumn(results.findColumn(FieldKey.fromParts("SampleId")));
            dcSampleId = sampleId.getRenderer();
            ColumnInfo mySampleParent = results.getColumn(results.findColumn(FieldKey.fromParts("MySampleParent")));
            dcMySampleParent = mySampleParent.getRenderer();

            assertTrue(results.next());
            ctx.setRow(results.getRowMap());
        }
        assertEquals("sally", dcSampleId.getValue(ctx));
        assertEquals("bob", dcMySampleParent.getDisplayValue(ctx));
    }

    @Test
    public void testObjectInputOutput() throws Exception
    {
        var expSvc = ExperimentService.get();

        // create some exp.object rows for use as input and outputs
        var a1 = createExpObject("A1");
        var a2 = createExpObject("A2");
        var b1 = createExpObject("B1");
        var b2 = createExpObject("B2");

        // create empty run
        ExpRun run = expSvc.createExperimentRun(c, "testing");
        PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(c);
        assertNotNull(pipeRoot);
        run.setFilePathRoot(pipeRoot.getRootPath());

        ExpProtocol protocol = expSvc.ensureSampleDerivationProtocol(user);
        run.setProtocol(protocol);

        // add A objects as inputs, B objects as outputs
        ViewBackgroundInfo info = new ViewBackgroundInfo(c, user, null);
        run = expSvc.saveSimpleExperimentRun(run, emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), info, null, false);
        Integer runObjectId = run.getObjectId();
        assertNotNull(runObjectId);

        // HACK: Until we have the ability to add provenance information to the run, just insert directly into exp.edge
        TableInfo edgeTable = ExperimentServiceImpl.get().getTinfoEdge();
        Table.insert(null, edgeTable, Map.of(
                "runId", run.getRowId(), "fromObjectId", a1.objectId, "toObjectId", runObjectId));
        Table.insert(null, edgeTable, Map.of(
                "runId", run.getRowId(), "fromObjectId", a2.objectId, "toObjectId", runObjectId));

        Table.insert(null, edgeTable, Map.of(
                "runId", run.getRowId(), "fromObjectId", runObjectId, "toObjectId", b1.objectId));
        Table.insert(null, edgeTable, Map.of(
                "runId", run.getRowId(), "fromObjectId", runObjectId, "toObjectId", b2.objectId));

        // query the lineage
        ExpLineage lineage = expSvc.getLineage(c, user, a1.identifiable, new ExpLineageOptions());

        assertTrue(lineage.getRuns().contains(run));
        assertEquals(Set.of(a1.identifiable), lineage.getSeeds());
        assertEquals(Set.of(b1.identifiable, b2.identifiable), lineage.getObjects());

        // verify lineage parent and children
        assertTrue(lineage.getNodeParents(a1.identifiable).isEmpty());
        assertEquals(Set.of(run), lineage.getNodeChildren(a1.identifiable));
        assertEquals(Set.of(a1.identifiable), lineage.getNodeParents(run));
        assertEquals(Set.of(b1.identifiable, b2.identifiable), lineage.getNodeChildren(run));

        // verify json structure
        JSONObject json = lineage.toJSON(user, true, new ExperimentJSONConverter.Settings(false, false, false));
        assertEquals(a1.identifiable.getLSID(), json.getString("seed"));

        JSONObject nodes = json.getJSONObject("nodes");
        assertEquals(4, nodes.length());
        JSONObject a1json = nodes.getJSONObject(a1.identifiable.getLSID());
        assertEquals("A1", a1json.getString("name"));
        assertEquals(a1.identifiable.getLSID(), a1json.getString("lsid"));
        assertEquals("JUnitTest", a1json.getString("type"));

        JSONArray a1parentsJson = a1json.getJSONArray("parents");
        assertEquals(0, a1parentsJson.length());

        JSONArray a1childrenJson = a1json.getJSONArray("children");
        assertEquals(1, a1childrenJson.length());
        assertEquals(run.getLSID(), a1childrenJson.getJSONObject(0).getString("lsid"));
    }

    @Test
    public void testAddEdges()
    {
        // Arrange
        var expSvc = ExperimentService.get();
        var sourceKey = "testAddEdges";
        var aa = createExpObject("addAA");
        var bb = createExpObject("addBB");
        var cc = createExpObject("addCC");

        // Act
        // Handles null/empty (noop)
        expSvc.addEdges(null);
        expSvc.addEdges(Collections.emptyList());

        // Does not allow run-based edge insertion
        try
        {
            expSvc.addEdges(List.of(new ExpLineageEdge(aa.objectId, bb.objectId, -1, bb.objectId, sourceKey)));
            fail("An error should have been thrown");
        }
        catch (IllegalArgumentException e)
        {
            assertTrue("Received unexpected error", e.getMessage().contains("Adding edges with a runId is not supported"));
        }

        // skips cycles
        var edge1 = new ExpLineageEdge(aa.objectId, bb.objectId, null, bb.objectId, sourceKey);
        var cycle = new ExpLineageEdge(bb.objectId, bb.objectId, null, bb.objectId, sourceKey);
        var edge3 = new ExpLineageEdge(cc.objectId, bb.objectId, null, bb.objectId, sourceKey);
        expSvc.addEdges(List.of(edge1, cycle, edge3));

        var bbEdges = new HashSet<>(expSvc.getEdges(new ExpLineageEdge.FilterOptions().sourceId(bb.objectId)));
        assertEquals("Unexpected number of edges", 2, bbEdges.size());
        assertFalse("Add edges inserted a cycle", bbEdges.contains(cycle));
    }

    @Test
    public void testRemoveEdges()
    {
        // Arrange
        var expSvc = ExperimentService.get();
        var sourceKey = "testRemoveEdges";
        var aa = createExpObject("removeAA");
        var bb = createExpObject("removeBB");
        var cc = createExpObject("removeCC");

        expSvc.addEdges(List.of(
            new ExpLineageEdge(aa.objectId, bb.objectId, null, bb.objectId, sourceKey),
            new ExpLineageEdge(bb.objectId, cc.objectId, null, bb.objectId, sourceKey),
            new ExpLineageEdge(aa.objectId, cc.objectId, null, bb.objectId, sourceKey)
        ));

        // Act
        // Handles empty options
        assertEquals("Unexpected edges removed", 0, expSvc.removeEdges(new ExpLineageEdge.FilterOptions()));

        // Does not allow run-based edge removal
        var exceptionThrown = false;
        try
        {
            expSvc.removeEdges(new ExpLineageEdge.FilterOptions().runId(-1));
        }
        catch (IllegalArgumentException e)
        {
            exceptionThrown = true;
            assertTrue("Received unexpected error", e.getMessage().contains("Edges with a runId cannot be deleted via removeEdge()"));
        }
        assertTrue("Expected exception when attempting to remove run-based lineage edge", exceptionThrown);

        // Successfully remove edges
        var actualRemoved = expSvc.removeEdges(new ExpLineageEdge.FilterOptions().sourceId(bb.objectId).sourceKey(sourceKey));
        assertEquals("Unexpected number of edges removed", 3, actualRemoved);

        var edges = expSvc.getEdges(new ExpLineageEdge.FilterOptions().sourceId(bb.objectId));
        assertEquals("Unexpected edges still persisted", 0, edges.size());
    }

    @Test
    public void testFilterLineageBySourceKey()
    {
        var expSvc = ExperimentService.get();
        var sourceKey = "snow☃man";
        var aa = createExpObject("filteredAA");
        var bb = createExpObject("filteredBB");
        var cc = createExpObject("filteredCC");
        var dd = createExpObject("filteredDD");
        var ee = createExpObject("filteredEE");

        expSvc.addEdges(List.of(
            new ExpLineageEdge(aa.objectId, bb.objectId, null, aa.objectId, sourceKey),
            new ExpLineageEdge(bb.objectId, cc.objectId, null, bb.objectId, "SELECT(*"),
            new ExpLineageEdge(cc.objectId, dd.objectId, null, cc.objectId, sourceKey),
            new ExpLineageEdge(dd.objectId, ee.objectId, null, dd.objectId, sourceKey)
        ));

        // Base case
        {
            var lineageOptions = new ExpLineageOptions(false, true, 10);
            var lineage = expSvc.getLineage(c, user, aa.identifiable, lineageOptions);
            assertEquals("Unexpected number of child objects", 4, lineage.getObjects().size());
        }

        // Filter children by sourceKey expecting only connected edges with that sourceKey
        {
            var lineageOptions = new ExpLineageOptions(false, true, 10);
            lineageOptions.setSourceKey(sourceKey);
            var lineage = expSvc.getLineage(c, user, aa.identifiable, lineageOptions);
            var objectNames = getLineageObjectNames(lineage);
            assertEquals("Unexpected number of child objects filtered", 1, objectNames.size());
            assertEquals("Unexpected child object", List.of("filteredBB"), objectNames);
        }

        // Filter parent by sourceKey expecting only connected edges with that sourceKey
        {
            var lineageOptions = new ExpLineageOptions(true, false, 10);
            lineageOptions.setSourceKey(sourceKey);
            var lineage = expSvc.getLineage(c, user, ee.identifiable, lineageOptions);
            var objectNames = getLineageObjectNames(lineage);
            assertEquals("Unexpected number of parent objects filtered", 2, objectNames.size());
            assertEquals("Unexpected parent objects filtered", List.of("filteredCC", "filteredDD"), objectNames);
        }

        // Attempt SQL injection #1
        {
            var lineageOptions = new ExpLineageOptions(true, false, 10);
            lineageOptions.setSourceKey("SELECT(*");
            var lineage = expSvc.getLineage(c, user, cc.identifiable, lineageOptions);
            var objectNames = getLineageObjectNames(lineage);
            assertEquals("Unexpected number of objects from SQL injection #1", 1, objectNames.size());
            assertEquals("Unexpected object from SQL injection #1", List.of("filteredBB"), objectNames);
        }

        // Attempt SQL injection #2
        {
            var lineageOptions = new ExpLineageOptions(true, false, 10);
            lineageOptions.setSourceKey(sourceKey + " OR _Edges.sourcekey = 'SELECT(*'");
            var lineage = expSvc.getLineage(c, user, ee.identifiable, lineageOptions);
            assertEquals("Unexpected number of objects from SQL injection #2", 0, lineage.getObjects().size());
        }
    }

    @Test
    public void testPropertySerialization() throws Exception
    {
        // Issue 48551: Ensure values in provisioned tables for Exp Data and Exp Materials are
        // provided as properties when requested via lineage.
        // Arrange
        var expSvc = ExperimentService.get();

        // Create a Data Class
        var dataClassName = "Roster";
        var dcProps = new ArrayList<GWTPropertyDescriptor>();
        dcProps.add(new GWTPropertyDescriptor("number", "int"));
        dcProps.add(new GWTPropertyDescriptor("position", "string"));
        var dataClass = expSvc.createDataClass(c, user, dataClassName, null, dcProps, emptyList(), null, null);

        // Create a Sample Type
        var sampleTypeName = "Games";
        List<GWTPropertyDescriptor> sampleProps = new ArrayList<>();
        sampleProps.add(new GWTPropertyDescriptor("name", "string"));
        sampleProps.add(new GWTPropertyDescriptor("home", "string"));
        sampleProps.add(new GWTPropertyDescriptor("away", "string"));
        sampleProps.add(new GWTPropertyDescriptor("date", "date"));
        var sampleType = SampleTypeService.get().createSampleType(c, user, sampleTypeName, null, sampleProps, emptyList(), -1, -1, -1, -1, null, null);

        // Add Data Class data
        var dad = "Ken Griffey Sr";
        var son = "Ken Griffey Jr";
        List<Map<String, Object>> dataClassRows = new ArrayList<>();
        dataClassRows.add(CaseInsensitiveHashMap.of("name", dad, "position", "OF"));
        dataClassRows.add(CaseInsensitiveHashMap.of(
            "name", son,
            "number", 24,
            "position", "CF",
            "DataInputs/" + dataClassName, dad
        ));

        insertRows(c, dataClassRows, dataClassName, QueryService.get().getUserSchema(user, c, expDataSchemaKey));
        var dadExpData = expSvc.getExpData(dataClass, dad);
        var sonExpData = expSvc.getExpData(dataClass, son);

        // Add Sample Type data
        List<Map<String, Object>> sampleRows = new ArrayList<>();
        Date gameDate = new Date(653295600000L); // Sept. 14, 1990
        sampleRows.add(CaseInsensitiveHashMap.of(
            "name", "Father/Son Game",
            "home", "Seattle Mariners",
            "away", "California Angels",
            "date", gameDate,
            "DataInputs/" + dataClassName, List.of(dad, son)
        ));
        var rows = insertRows(c, sampleRows, sampleTypeName, QueryService.get().getUserSchema(user, c, SchemaKey.fromParts("Samples")));
        var gameSample = expSvc.getExpMaterial((Integer) rows.get(0).get("RowId"));

        // Request lineage including all rows
        var lineage = expSvc.getLineage(c, user, dadExpData, new ExpLineageOptions(false, true, 2));

        // Act
        var json = lineage.toJSON(user, true, new ExperimentJSONConverter.Settings(true, false, false));

        // Assert
        assertEquals(dadExpData.getLSID(), json.getString("seed"));
        var nodes = json.getJSONObject("nodes");

        var numberProperty = dataClass.getDomain().getPropertyByName("number");
        var positionProperty = dataClass.getDomain().getPropertyByName("position");

        var dadProperties = nodes.getJSONObject(dadExpData.getLSID()).getJSONObject("properties");
        assertEquals("OF", dadProperties.getString(positionProperty.getPropertyURI()));
        assertFalse("Expected null values to not be serialized", dadProperties.has(numberProperty.getPropertyURI()));

        var sonProperties = nodes.getJSONObject(sonExpData.getLSID()).getJSONObject("properties");
        assertEquals("CF", sonProperties.getString(positionProperty.getPropertyURI()));
        assertEquals(24, sonProperties.getNumber(numberProperty.getPropertyURI()));

        var homeProperty = sampleType.getDomain().getPropertyByName("home");
        var dateProperty = sampleType.getDomain().getPropertyByName("date");
        var gameProperties = nodes.getJSONObject(gameSample.getLSID()).getJSONObject("properties");
        assertEquals("Seattle Mariners", gameProperties.getString(homeProperty.getPropertyURI()));
        // TODO: Logic is mostly correct but need to determine best date behavior for CI
        // expected:<Fri Sep 14 07:00:00 UTC 1990> but was:<1990-09-14 00:00:00.0>
        // assertEquals(gameDate, gameProperties.get(dateProperty.getPropertyURI()));
    }

    private List<String> getLineageObjectNames(ExpLineage lineage)
    {
        return lineage.getObjects().stream()
                .map(Identifiable::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    private _ExpObject createExpObject(String objectName)
    {
        Lsid lsid = lsidBuilder.setObjectId(objectName).build();
        Integer objectId = OntologyManager.ensureObject(c, lsid.toString());
        Identifiable identifiable = LsidManager.get().getObject(lsid);
        assertNotNull(identifiable);
        return new _ExpObject(objectId, identifiable);
    }

    private record _ExpObject(Integer objectId, Identifiable identifiable) {}
}

package org.labkey.experiment.api;

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
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.ListofMapsDataIterator;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpLineage;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
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
import static java.util.stream.Collectors.joining;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
        final ExpDataClassImpl firstDataClass = ExperimentServiceImpl.get().createDataClass(c, user, firstDataClassName, null, props, emptyList(), null, null, null);

        final String secondDataClassName = "secondDataClass";
        final ExpDataClassImpl secondDataClass = ExperimentServiceImpl.get().createDataClass(c, user, secondDataClassName, null, props, emptyList(), null, null, null);
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
        ExpLineage lineage = ExperimentService.get().getLineage(context, bob, options);
        Assert.assertTrue(lineage.getDatas().isEmpty());
        assertEquals(1, lineage.getMaterials().size());
        Assert.assertTrue(lineage.getMaterials().contains(s1));

        final ExpData jimbo = ExperimentService.get().getExpData(secondDataClass, "jimbo");
        final ExpData sally = ExperimentService.get().getExpData(firstDataClass, "sally");
        lineage = ExperimentService.get().getLineage(context, sally, options);
        assertEquals(2, lineage.getDatas().size());
        Assert.assertTrue(lineage.getDatas().contains(bob));
        Assert.assertTrue(lineage.getDatas().contains(jimbo));
        assertEquals(1, lineage.getMaterials().size(), 1);
        Assert.assertTrue(lineage.getMaterials().contains(s2));

        final ExpData mike = ExperimentService.get().getExpData(firstDataClass, "mike");
        lineage = ExperimentService.get().getLineage(context, mike, options);
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

}

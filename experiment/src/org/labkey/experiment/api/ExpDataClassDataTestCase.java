/*
 * Copyright (c) 2015-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.Results;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpLineage;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainTemplate;
import org.labkey.api.exp.property.DomainTemplateGroup;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.gwt.client.model.GWTIndex;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.MapLoader;
import org.labkey.api.security.User;
import org.labkey.api.settings.ConceptURIProperties;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.TestContext;

import java.io.StringWriter;
import java.sql.Clob;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.joining;

/**
 * User: kevink
 * Date: 12/27/15
 */
@TestWhen(TestWhen.When.BVT)
public class ExpDataClassDataTestCase
{
    private static SchemaKey expDataSchemaKey = SchemaKey.fromParts(ExpSchema.SCHEMA_NAME, ExpSchema.NestedSchemas.data.toString());

    Container c;

    @Before
    public void setUp() throws Exception
    {
        // NOTE: We need to use a project to create the DataClass so we can insert rows into sub-folders
        c = ContainerManager.getForPath("_testDataClass");
        if (c != null)
            ContainerManager.deleteAll(c, TestContext.get().getUser());
        c = ContainerManager.createContainer(ContainerManager.getRoot(), "_testDataClass");
    }

    @After
    public void tearDown() throws Exception
    {
        //ContainerManager.deleteAll(c, TestContext.get().getUser());
    }

    private static List<Map<String, Object>> insertRows(Container c, List<Map<String, Object>> rows, String tableName)
            throws Exception
    {
        final User user = TestContext.get().getUser();

        BatchValidationException errors = new BatchValidationException();
        UserSchema schema = QueryService.get().getUserSchema(user, c, expDataSchemaKey);
        TableInfo table = schema.getTable(tableName);
        Assert.assertNotNull(table);

        QueryUpdateService qus = table.getUpdateService();
        Assert.assertNotNull(qus);

        List<Map<String, Object>> ret = qus.insertRows(user, c, rows, errors, null, null);
        if (errors.hasErrors())
            throw errors;
        return ret;
    }

    //@Test
    public void testDataClass() throws Exception
    {
        final User user = TestContext.get().getUser();
        final Container sub = ContainerManager.createContainer(c, "sub");

        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("aa", "int"));
        props.add(new GWTPropertyDescriptor("bb", "string"));

        List<GWTIndex> indices = new ArrayList<>();
        indices.add(new GWTIndex(Arrays.asList("aa"), true));

        String nameExpr = "JUNIT-${genId}-${aa}";

        final ExpDataClassImpl dataClass = ExperimentServiceImpl.get().createDataClass(c, user, "testing", null, props, indices, null, nameExpr, null);
        Assert.assertNotNull(dataClass);

        final Domain domain = dataClass.getDomain();
        Assert.assertNotNull(domain);

        UserSchema schema = QueryService.get().getUserSchema(user, c, expDataSchemaKey);
        TableInfo table = schema.getTable("testing");
        Assert.assertNotNull("data class not in query schema", table);

        String expectedName = "JUNIT-1-20";
        testNameExpressionGeneration(dataClass, table, expectedName);
        testInsertDuplicate(dataClass, table);
        String expectedSubName = "JUNIT-3-30";
        testInsertIntoSubfolder(dataClass, table, sub, expectedSubName);
        testTruncateRows(dataClass, table, expectedName, expectedSubName);
        testBulkImport(dataClass, table, user);
        testInsertAliases(dataClass, table);
        testDeleteExpData(dataClass, user, 3);
        testDeleteExpDataClass(dataClass, user, table, domain.getTypeURI());
    }

    private void testNameExpressionGeneration(ExpDataClassImpl dataClass, TableInfo table, String expectedName) throws Exception
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new CaseInsensitiveHashMap<>();
        row.put("aa", 20);
        row.put("bb", "hi");
        rows.add(row);

        List<Map<String, Object>> ret;
        try (DbScope.Transaction tx = table.getSchema().getScope().beginTransaction())
        {
            ret = insertRows(c, rows, table.getName());
            tx.commit();
        }

        Assert.assertEquals(1, ret.size());
        Assert.assertEquals(1, ret.get(0).get("genId"));
        Assert.assertEquals(expectedName, ret.get(0).get("name"));

        Integer rowId = (Integer) ret.get(0).get("RowId");
        ExpData data = ExperimentService.get().getExpData(rowId);
        ExpData data1 = ExperimentService.get().getExpData(dataClass, expectedName);
        Assert.assertEquals(data, data1);

        TableSelector ts = new TableSelector(table);
        Assert.assertEquals(1L, ts.getRowCount());
    }

    private void testInsertIntoSubfolder(ExpDataClassImpl dataClass, TableInfo table, Container sub, String expectedSubName) throws Exception
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new CaseInsensitiveHashMap<>();
        row.put("aa", 30);
        row.put("container", sub);
        row.put("bb", "bye");
        rows.add(row);

        List<Map<String, Object>> ret;
        try (DbScope.Transaction tx = table.getSchema().getScope().beginTransaction())
        {
            ret = insertRows(sub, rows, table.getName());
            tx.commit();
        }

        Assert.assertEquals(1, ret.size());
        Assert.assertEquals(sub.getId(), ret.get(0).get("container"));

        ExpData data = ExperimentService.get().getExpData(dataClass, expectedSubName);
        Assert.assertNotNull(data);
        Assert.assertEquals(sub, data.getContainer());

        // TODO: Why is my filter not working?
//            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("container"), Arrays.asList(c, sub), CompareType.IN);
//            TableSelector ts = new TableSelector(table, filter, null);
//            Assert.assertEquals(2L, ts.getRowCount());

        Assert.assertEquals(2, dataClass.getDatas().size());
    }

    private void testInsertDuplicate(ExpDataClassImpl dataClass, TableInfo table) throws Exception
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new CaseInsensitiveHashMap<>();
        row.put("aa", 20);
        row.put("bb", "bye");
        rows.add(row);

        try (DbScope.Transaction tx = table.getSchema().getScope().beginTransaction())
        {
            insertRows(c, rows, table.getName());
            tx.commit();
            Assert.fail("Expected exception");
        }
        catch (BatchValidationException e)
        {
            // ok, expected
        }

        TableSelector ts = new TableSelector(table);
        Assert.assertEquals(1L, ts.getRowCount());
        Assert.assertEquals(1, dataClass.getDatas().size());
    }

    private void testTruncateRows(ExpDataClassImpl dataClass, TableInfo table, String expectedName, String expectedSubName) throws ExperimentException
    {
        int count;
        try (DbScope.Transaction tx = table.getSchema().getScope().beginTransaction())
        {
            // TODO: truncate rows API doesn't support truncating from all containers
            //count = table.getUpdateService().truncateRows(user, c, null, null);
            count = ExperimentServiceImpl.get().truncateDataClass(dataClass, TestContext.get().getUser(), null);
            tx.commit();
        }
        Assert.assertEquals(2, count);

        Assert.assertEquals(0, dataClass.getDatas().size());
        Assert.assertNull(ExperimentService.get().getExpData(dataClass, expectedName));
        Assert.assertNull(ExperimentService.get().getExpData(dataClass, expectedSubName));
    }

    private void testBulkImport(ExpDataClassImpl dataClass, TableInfo table, User user) throws Exception
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new CaseInsensitiveHashMap<>();
        row.put("aa", 40);
        row.put("bb", "qq");
        row.put("alias", "a");
        rows.add(row);

        row = new CaseInsensitiveHashMap<>();
        row.put("aa", 50);
        row.put("bb", "zz");
        row.put("alias", "a,b,c");
        rows.add(row);

        MapLoader mapLoader = new MapLoader(rows);
        int count = table.getUpdateService().loadRows(user, c, mapLoader, new DataIteratorContext(), null);
        Assert.assertEquals(2, count);
        Assert.assertEquals(2, dataClass.getDatas().size());
        verifyAliases(new ArrayList<>(Arrays.asList("a", "b", "c")));
    }

    private void testDeleteExpData(ExpDataClassImpl dataClass, User user, int expectedCount)
    {
        List<? extends ExpData> datas = dataClass.getDatas();
        Assert.assertEquals(expectedCount, datas.size());

        datas.get(0).delete(user);
        Assert.assertEquals(expectedCount-1, dataClass.getDatas().size());
    }

    private void testDeleteExpDataClass(ExpDataClassImpl dataClass, User user, TableInfo table, String typeURI)
    {
        Assert.assertNotNull(ExperimentService.get().getDataClass(c, table.getName()));
        Assert.assertNotNull(PropertyService.get().getDomain(c, typeURI));

        String storageTableName = dataClass.getTinfo().getName();
        DbSchema dbSchema = DbSchema.get("labkey.expdataclass", DbSchemaType.Provisioned);
        TableInfo dbTable = dbSchema.getTable(storageTableName);
        Assert.assertNotNull(dbTable);

        dataClass.delete(user);

        Assert.assertNull(ExperimentService.get().getDataClass(c, table.getName()));
        Assert.assertNull(PropertyService.get().getDomain(c, typeURI));

        UserSchema schema1 = QueryService.get().getUserSchema(user, c, expDataSchemaKey);
        Assert.assertNull(schema1.getTable(table.getName()));

        dbTable = dbSchema.getTable(storageTableName);
        Assert.assertNull(dbTable);
    }

    private void testInsertAliases(ExpDataClassImpl dataClass, TableInfo table) throws Exception
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new CaseInsensitiveHashMap<>();
        row.put("aa", 20);
        row.put("bb", "bye");
        row.put("alias", "aa");
        rows.add(row);

        try (DbScope.Transaction tx = table.getSchema().getScope().beginTransaction())
        {
            List<Map<String, Object>> ret = insertRows(c, rows, table.getName());
            verifyAliases(new ArrayList<>(Arrays.asList("aa")));
            tx.commit();
        }
    }

    private void verifyAliases(Collection<String> aliasNames)
    {
        for (String name : aliasNames)
        {
            Assert.assertEquals(new TableSelector(ExperimentService.get().getTinfoAlias(),
                    new SimpleFilter(FieldKey.fromParts("name"), name), null).getRowCount(), 1);
        }
    }

    //@Test
    public void testDeriveDuringImport() throws Exception
    {
        final User user = TestContext.get().getUser();

        // just some properties used in both the SampleSet and DataClass
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("me", "string"));
        props.add(new GWTPropertyDescriptor("age", "string"));

        // Create a SampleSet and some samples
        final ExpSampleSet ss = ExperimentService.get().createSampleSet(c, user, "Samples", null, props, Collections.emptyList(), 0, -1, -1, -1, null, null);
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
        final ExpDataClassImpl firstDataClass = ExperimentServiceImpl.get().createDataClass(c, user, firstDataClassName, null, props, Collections.emptyList(), null, null, null);

        final String secondDataClassName = "secondDataClass";
        final ExpDataClassImpl secondDataClass = ExperimentServiceImpl.get().createDataClass(c, user, secondDataClassName, null, props, Collections.emptyList(), null, null, null);
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
        int count = table.getUpdateService().loadRows(user, c, mapLoader, new DataIteratorContext(), null);
        Assert.assertEquals(3, count);

        // Verify lineage
        ExpLineageOptions options = new ExpLineageOptions();
        options.setDepth(2);
        options.setParents(true);
        options.setChildren(false);

        final ExpData bob = ExperimentService.get().getExpData(firstDataClass, "bob");
        ExpLineage lineage = ExperimentService.get().getLineage(bob, options);
        Assert.assertTrue(lineage.getDatas().isEmpty());
        Assert.assertEquals(1, lineage.getMaterials().size());
        Assert.assertTrue(lineage.getMaterials().contains(s1));

        final ExpData jimbo = ExperimentService.get().getExpData(secondDataClass, "jimbo");
        final ExpData sally = ExperimentService.get().getExpData(firstDataClass, "sally");
        lineage = ExperimentService.get().getLineage(sally, options);
        Assert.assertEquals(2, lineage.getDatas().size());
        Assert.assertTrue(lineage.getDatas().contains(bob));
        Assert.assertTrue(lineage.getDatas().contains(jimbo));
        Assert.assertEquals(1, lineage.getMaterials().size(), 1);
        Assert.assertTrue(lineage.getMaterials().contains(s2));

        final ExpData mike = ExperimentService.get().getExpData(firstDataClass, "mike");
        lineage = ExperimentService.get().getLineage(mike, options);
        Assert.assertEquals("Expected 2 data, found: " + lineage.getDatas().stream().map(ExpData::getName).collect(joining(", ")),
                2, lineage.getDatas().size());
        Assert.assertTrue(lineage.getDatas().contains(bob));
        Assert.assertTrue(lineage.getDatas().contains(sally));
        Assert.assertEquals("Expected 2 samples, found: " + lineage.getMaterials().stream().map(ExpMaterial::getName).collect(joining(", ")),
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

        try (Results rs = QueryService.get().selectResults(schema, sql, null, null, true, false))
        {
            Assert.assertTrue(rs.next());
            Map<FieldKey, Object> bobMap = rs.getFieldKeyRowMap();
            Assert.assertEquals("bob", bobMap.get(FieldKey.fromParts("Name")));
            assertMultiValue(bobMap.get(FieldKey.fromParts("InputsMaterialSampleNames")), "S-1");

            Assert.assertTrue(rs.next());
            Map<FieldKey, Object> sallyMap = rs.getFieldKeyRowMap();
            Assert.assertEquals("sally", sallyMap.get(FieldKey.fromParts("Name")));
            assertMultiValue(sallyMap.get(FieldKey.fromParts("InputsDataAllNames")), "jimbo", "bob");
            assertMultiValue(sallyMap.get(FieldKey.fromParts("InputsDataFirstDataClassNames")), "bob");
            assertMultiValue(sallyMap.get(FieldKey.fromParts("InputsMaterialSampleNames")), "S-2", "S-1");

            Assert.assertTrue(rs.next());
            Map<FieldKey, Object> mikeMap = rs.getFieldKeyRowMap();
            Assert.assertEquals("mike", mikeMap.get(FieldKey.fromParts("Name")));
            assertMultiValue(mikeMap.get(FieldKey.fromParts("InputsDataAllNames")), "sally", "jimbo", "bob");
            assertMultiValue(mikeMap.get(FieldKey.fromParts("InputsDataFirstDataClassNames")), "bob", "sally");
            assertMultiValue(mikeMap.get(FieldKey.fromParts("InputsMaterialSampleNames")), "S-2", "S-1");

            Assert.assertFalse(rs.next());
        }
    }

    void assertMultiValue(Object value, String... expected) throws Exception
    {
        Assert.assertNotNull(value);
        String s;
        if (value instanceof Clob)
        {
            StringWriter sw = new StringWriter();
            org.apache.commons.io.IOUtils.copy(((Clob)value).getCharacterStream(),sw);
            s = sw.toString();
        }
        else
        {
            s = String.valueOf(value);
        }

        for (String e : expected)
            Assert.assertTrue("Failed to find '" + e + "' in multivalue '" + s + "'", s.contains(e));
    }

    @Test
    public void testDataClassFromTemplate() throws Exception
    {
        final User user = TestContext.get().getUser();
        final Container sub = ContainerManager.createContainer(c, "sub2");
        final String domainName = "mydataclass";

        Set<Module> activeModules = new HashSet<>(c.getActiveModules());
        Module m = ModuleLoader.getInstance().getModule("simpletest");
        Assert.assertNotNull("This test requires 'simplemodule' to be deployed", m);
        activeModules.add(m);
        c.setActiveModules(activeModules);

        DomainTemplateGroup templateGroup = DomainTemplateGroup.get(c, "TestingFromTemplate");
        Assert.assertNotNull(templateGroup);
        Assert.assertFalse(
                "Errors in template: " + StringUtils.join(templateGroup.getErrors(), ", "),
                templateGroup.hasErrors());

        DomainTemplate template = templateGroup.getTemplate("testingFromTemplate");
        Assert.assertNotNull(template);

        final Domain domain = template.createAndImport(c, user, domainName, true, false);
        Assert.assertNotNull(domain);

        TemplateInfo t = domain.getTemplateInfo();
        Assert.assertNotNull("Expected template information to be persisted", t);
        Assert.assertEquals("simpletest", t.getModuleName());
        Assert.assertEquals("TestingFromTemplate", t.getTemplateGroupName());
        Assert.assertEquals("testingFromTemplate", t.getTableName());

        DomainKind kind = domain.getDomainKind();
        Assert.assertTrue(kind instanceof DataClassDomainKind);
        Set<String> mandatory = kind.getMandatoryPropertyNames(domain);
        Assert.assertTrue("Expected template to set 'aa' as mandatory: " + mandatory, mandatory.contains("aa"));

        ExpDataClassImpl dataClass = (ExpDataClassImpl)ExperimentService.get().getDataClass(c, domainName);
        Assert.assertNotNull(dataClass);

        // add ConceptURI mappings for this container
        String listName = createConceptLookupList(c, user);
        Lookup lookup = new Lookup(c, "lists", listName);
        ConceptURIProperties.setLookup(c, "http://cpas.labkey.com/Experiment#Testing", lookup);

        UserSchema schema = QueryService.get().getUserSchema(user, c, expDataSchemaKey);
        TableInfo table = schema.getTable(domainName);
        Assert.assertNotNull("data class not in query schema", table);

        // verify that the lookup from the ConceptURI mapping is applied as a FK to the column
        TableInfo aaLookupTable = table.getColumn("aa").getFkTableInfo();
        Assert.assertNotNull(aaLookupTable);
        Assert.assertTrue("lists".equals(aaLookupTable.getPublicSchemaName()));
        Assert.assertTrue(listName.equals(aaLookupTable.getName()));
        Assert.assertNull(table.getColumn("bb").getFkTableInfo());

        String expectedName = "TEST-1-20";
        testNameExpressionGeneration(dataClass, table, expectedName);
        testInsertDuplicate(dataClass, table);
        String expectedSubName = "TEST-3-30";
        testInsertIntoSubfolder(dataClass, table, sub, expectedSubName);
        testTruncateRows(dataClass, table, expectedName, expectedSubName);
        testBulkImport(dataClass, table, user);
        testDeleteExpData(dataClass, user, 2);
        testDeleteExpDataClass(dataClass, user, table, domain.getTypeURI());
    }

    private String createConceptLookupList(Container c, User user) throws Exception
    {
        String listName = "ConceptList";
        ListDefinition list = ListService.get().createList(c, listName, ListDefinition.KeyType.Integer);
        list.setKeyName("Key");
        list.getDomain().addProperty(new PropertyStorageSpec("Key", JdbcType.INTEGER));
        list.getDomain().addProperty(new PropertyStorageSpec("Value", JdbcType.VARCHAR));
        list.save(user);
        List<ListItem> lis = new ArrayList<>();
        ListItem li = list.createListItem();
        li.setProperty(list.getDomain().getPropertyByName("Key"), 20);
        li.setProperty(list.getDomain().getPropertyByName("Value"), "Value 20");
        lis.add(li);
        list.insertListItems(user, c, lis);
        return listName;
    }


    //@Test
    public void testDomainTemplate() throws Exception
    {
        final User user = TestContext.get().getUser();
        final Container sub = ContainerManager.createContainer(c, "sub3");

        Set<Module> activeModules = new HashSet<>(c.getActiveModules());
        Module m = ModuleLoader.getInstance().getModule("simpletest");
        Assert.assertNotNull("This test requires 'simplemodule' to be deployed", m);
        activeModules.add(m);
        c.setActiveModules(activeModules);

        DomainTemplateGroup templateGroup = DomainTemplateGroup.get(c, "todolist");
        Assert.assertNotNull(templateGroup);
        Assert.assertFalse(
                "Errors in template: " + StringUtils.join(templateGroup.getErrors(), ", "),
                templateGroup.hasErrors());

        List<Domain> created = templateGroup.createAndImport(sub, user, true, true);

        // verify the "Priority" list was created and data was imported
        UserSchema listSchema = QueryService.get().getUserSchema(user, sub, "lists");
        TableInfo priorityTable = listSchema.getTable("Priority");

        Collection<Map<String, Object>> priorities = new TableSelector(priorityTable).getMapCollection();
        Assert.assertEquals(5, priorities.size());

        // Issue 27729: sequence not updated to max after bulk import with importIdentity turned on
        // verify that we can insert a new priority
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new CaseInsensitiveHashMap<>();
        row.put("title", "p5");
        rows.add(row);

        List<Map<String, Object>> ret;
        try (DbScope.Transaction tx = priorityTable.getSchema().getScope().beginTransaction())
        {
            BatchValidationException errors = new BatchValidationException();
            ret = priorityTable.getUpdateService().insertRows(user, sub, rows, errors, null, null);
            if (errors.hasErrors())
                throw errors;
            tx.commit();
        }

        Assert.assertEquals(1, ret.size());
        Assert.assertEquals(5, ((Integer)ret.get(0).get("pri")).longValue());
        Assert.assertEquals("p5", ret.get(0).get("title"));


        // verify the "TodoList" DataCLass was created and data was imported
        UserSchema expSchema = QueryService.get().getUserSchema(user, sub, expDataSchemaKey);
        TableInfo table = expSchema.getTable("TodoList");
        Assert.assertNotNull("data class not in query schema", table);

        Collection<Map<String, Object>> todos = new TableSelector(table, TableSelector.ALL_COLUMNS, null, null).getMapCollection();
        Assert.assertEquals(1, todos.size());
        Map<String, Object> todo = todos.iterator().next();
        Assert.assertEquals("create xsd", todo.get("title"));

        ExpDataClass dataClass = ExperimentServiceImpl.get().getDataClass(sub, "TodoList");
        ExpData data = ExperimentServiceImpl.get().getExpData(dataClass, "TODO-1");

        Collection<String> aliases = data.getAliases();
        Assert.assertTrue("Expected aliases to contain 'xsd' and 'domain templates', got: " + aliases, aliases.containsAll(Arrays.asList("xsd", "domain templates")));

    }


    // Issue 25224: NPE trying to delete a folder with a DataClass with at least one result row in it
    //@Test
    public void testContainerDelete() throws Exception
    {
        final User user = TestContext.get().getUser();
        final Container sub = ContainerManager.createContainer(c, "sub");

        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("aa", "int"));

        final ExpDataClassImpl dataClass = ExperimentServiceImpl.get().createDataClass(c, user, "testing", null, props, Collections.emptyList(), null, null, null);
        final int dataClassId = dataClass.getRowId();

        UserSchema schema = QueryService.get().getUserSchema(user, c, expDataSchemaKey);
        TableInfo table = schema.getTable("testing");

        // setup: insert into junit container
        int dataRowId1;
        {
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, Object> row = new CaseInsensitiveHashMap<>();
            row.put("name", "first");
            row.put("aa", 20);
            rows.add(row);

            List<Map<String, Object>> ret;
            try (DbScope.Transaction tx = table.getSchema().getScope().beginTransaction())
            {
                ret = insertRows(c, rows, table.getName());
                tx.commit();
            }

            Assert.assertEquals(1, ret.size());
            dataRowId1 = ((Integer)ret.get(0).get("RowId")).intValue();
        }

        // setup: insert into sub container
        int dataRowId2;
        {
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, Object> row = new CaseInsensitiveHashMap<>();
            row.put("name", "second");
            row.put("aa", 30);
            rows.add(row);

            List<Map<String, Object>> ret;
            try (DbScope.Transaction tx = table.getSchema().getScope().beginTransaction())
            {
                ret = insertRows(sub, rows, table.getName());
                tx.commit();
            }

            Assert.assertEquals(1, ret.size());
            dataRowId2 = ((Integer)ret.get(0).get("RowId")).intValue();
        }

        // test: delete container, ensure everything is removed
        {
            // verify exists
            Assert.assertNotNull(ExperimentService.get().getDataClass(dataClassId));
            Assert.assertNotNull(ExperimentService.get().getExpData(dataRowId1));
            Assert.assertNotNull(ExperimentService.get().getExpData(dataRowId2));

            String storageTableName = dataClass.getTinfo().getName();
            DbSchema dbSchema = DbSchema.get("labkey.expdataclass", DbSchemaType.Provisioned);
            TableInfo dbTable = dbSchema.getTable(storageTableName);
            Assert.assertNotNull(dbTable);

            // delete
            ContainerManager.deleteAll(c, user);

            // verify deleted
            Assert.assertNull(ExperimentService.get().getDataClass(dataClassId));
            Assert.assertNull(ExperimentService.get().getExpData(dataRowId1));
            Assert.assertNull(ExperimentService.get().getExpData(dataRowId2));

            dbTable = dbSchema.getTable(storageTableName);
            Assert.assertNull(dbTable);
        }

    }

    // Issue 26129: sqlserver maximum size of index keys must be < 900 bytes
    //@Test
    public void testLargeUniqueOnSingleColumnOnly() throws ExperimentException
    {
        final User user = TestContext.get().getUser();

        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("aa", "int"));
        props.add(new GWTPropertyDescriptor("bb", "multiLine"));

        List<GWTIndex> indices = new ArrayList<>();
        indices.add(new GWTIndex(Arrays.asList("aa", "bb"), true));

        boolean sqlServer = ExperimentService.get().getSchema().getSqlDialect().isSqlServer();
        try
        {
            final ExpDataClassImpl dataClass = ExperimentServiceImpl.get().createDataClass(c, user, "largeUnique", null, props, indices, null, null, null);
            if (sqlServer)
                Assert.fail("Expected exception creating large index over two columns");
        }
        catch (IllegalArgumentException ex)
        {
            // sqlserver only error
            String msg = ex.getMessage();
            String expected = "Error creating index over 'aa, bb'";
            Assert.assertTrue("Expected \"" + expected + "\", got \"" + msg + "\"", msg.contains(expected));

            expected = "Index over large columns currently only supported for a single string column";
            Assert.assertTrue("Expected \"" + expected + "\", got \"" + msg + "\"", msg.contains(expected));
        }
    }

    //@Test
    public void testLargeUnique() throws Exception
    {
        final User user = TestContext.get().getUser();

        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("aa", "int"));
        GWTPropertyDescriptor prop = new GWTPropertyDescriptor("bb", "multiLine");
        prop.setScale(20000);
        props.add(prop);


        List<GWTIndex> indices = new ArrayList<>();
        indices.add(new GWTIndex(Arrays.asList("bb"), true));

        String nameExpr = "JUNIT-${genId}-${aa}";

        final ExpDataClassImpl dataClass = ExperimentServiceImpl.get().createDataClass(c, user, "largeUnique2", null, props, indices, null, nameExpr, null);
        final int dataClassId = dataClass.getRowId();

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new CaseInsensitiveHashMap<>();
        row.put("aa", 10);
        // generate long string
        row.put("bb", StringUtils.repeat("0123456789", 1000));
        rows.add(row);

        row = new CaseInsensitiveHashMap<>();
        row.put("aa", 10);
        // generate long string that is different at the end
        row.put("bb", StringUtils.repeat("0123456789", 1001));
        rows.add(row);

        // first insert should succeed
        try (DbScope.Transaction tx = ExperimentService.get().getSchema().getScope().beginTransaction())
        {
            insertRows(c, rows, "largeUnique2");
            tx.commit();
        }

        // second insert should fail
        try (DbScope.Transaction tx = ExperimentService.get().getSchema().getScope().beginTransaction())
        {
            insertRows(c, rows, "largeUnique2");
            Assert.fail("Expected constraint exception");
        }
        catch (BatchValidationException e)
        {
            boolean sqlServer = ExperimentService.get().getSchema().getSqlDialect().isSqlServer();
            if (sqlServer)
            {
                // Check error message from trigger script is propagated up on SqlServer
                Assert.assertTrue("Expected error to start with '" + "\n" + SqlDialect.CUSTOM_UNIQUE_ERROR_MESSAGE + "', got '" + e.getMessage() + "'",
                        e.getMessage().startsWith("\n" + SqlDialect.CUSTOM_UNIQUE_ERROR_MESSAGE));
            }
            Throwable t = e.getLastRowError().getGlobalError(0).getCause();
            Assert.assertTrue("Expected a SQLException", t instanceof SQLException);
            Assert.assertTrue("Expected a constraint violation", RuntimeSQLException.isConstraintException((SQLException)t));
        }
    }

}

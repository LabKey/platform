/*
 * Copyright (c) 2015-2019 LabKey Corporation
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
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.ListofMapsDataIterator;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainTemplate;
import org.labkey.api.exp.property.DomainTemplateGroup;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTIndex;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.MapLoader;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.ConceptURIProperties;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.TestContext;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * User: kevink
 * Date: 12/27/15
 */
@TestWhen(TestWhen.When.BVT)
public class ExpDataClassDataTestCase extends ExpProvisionedTableTestHelper
{

    Container c;

    @Before
    public void setUp()
    {
        // NOTE: We need to use a project to create the DataClass so we can insert rows into sub-folders
        c = ContainerManager.getForPath("_testDataClass");
        if (c != null)
            ContainerManager.deleteAll(c, TestContext.get().getUser());
        c = ContainerManager.createContainer(ContainerManager.getRoot(), "_testDataClass");
    }

    @After
    public void tearDown()
    {
        //ContainerManager.deleteAll(c, TestContext.get().getUser());
    }


    // validate name is not null
    @Test
    public void nameNotNull() throws Exception
    {
        final User user = TestContext.get().getUser();

        try
        {
            List<GWTPropertyDescriptor> props = new ArrayList<>();
            props.add(new GWTPropertyDescriptor("foo", "string"));

            ExperimentServiceImpl.get().createDataClass(c, user, null, null, props, emptyList(), null);
        }
        catch (IllegalArgumentException e)
        {
            assertEquals("DataClass name is required.", e.getMessage());
        }
    }

    // validate name scale
    @Test
    public void nameScale() throws Exception
    {
        final User user = TestContext.get().getUser();

        try
        {
            List<GWTPropertyDescriptor> props = new ArrayList<>();
            props.add(new GWTPropertyDescriptor("foo", "string"));

            String name = StringUtils.repeat("a", 1000);
            ExperimentServiceImpl.get().createDataClass(c, user, name, null, props, emptyList(), null);
        }
        catch (IllegalArgumentException e)
        {
            assertEquals("DataClass name may not exceed 200 characters.", e.getMessage());
        }
    }

    // validate name expression scale
    @Test
    public void nameExpressionScale() throws Exception
    {
        final User user = TestContext.get().getUser();

        try
        {
            List<GWTPropertyDescriptor> props = new ArrayList<>();
            props.add(new GWTPropertyDescriptor("foo", "string"));

            String nameExpr = StringUtils.repeat("a", 1000);
            ExperimentServiceImpl.get().createDataClass(c, user, "testing", null, props, emptyList(), null, nameExpr, null, null);
        }
        catch (IllegalArgumentException e)
        {
            assertEquals("Name expression may not exceed 500 characters.", e.getMessage());
        }
    }


    @Test
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

        final ExpDataClassImpl dataClass = ExperimentServiceImpl.get().createDataClass(c, user, "testing", null, props, indices, null, nameExpr, null, null);
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
        testEmptyInsert(dataClass, table, user);
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

        assertEquals(1, ret.size());
        assertEquals(1, ret.get(0).get("genId"));
        assertEquals(expectedName, ret.get(0).get("name"));

        Integer rowId = (Integer) ret.get(0).get("RowId");
        ExpData data = ExperimentService.get().getExpData(rowId);
        ExpData data1 = ExperimentService.get().getExpData(dataClass, expectedName);
        assertEquals(data, data1);

        TableSelector ts = new TableSelector(table);
        assertEquals(1L, ts.getRowCount());
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

        assertEquals(1, ret.size());
        assertEquals(sub.getId(), ret.get(0).get("container"));

        ExpData data = ExperimentService.get().getExpData(dataClass, expectedSubName);
        Assert.assertNotNull(data);
        assertEquals(sub, data.getContainer());

        // TODO: Why is my filter not working?
//            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("container"), Arrays.asList(c, sub), CompareType.IN);
//            TableSelector ts = new TableSelector(table, filter, null);
//            Assert.assertEquals(2L, ts.getRowCount());

        assertEquals(2, dataClass.getDatas().size());
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
        assertEquals(1L, ts.getRowCount());
        assertEquals(1, dataClass.getDatas().size());
    }

    private void testTruncateRows(ExpDataClassImpl dataClass, TableInfo table, String expectedName, String expectedSubName)
    {
        int count;
        try (DbScope.Transaction tx = table.getSchema().getScope().beginTransaction())
        {
            // TODO: truncate rows API doesn't support truncating from all containers
            //count = table.getUpdateService().truncateRows(user, c, null, null);
            count = ExperimentServiceImpl.get().truncateDataClass(dataClass, TestContext.get().getUser(), null);
            tx.commit();
        }
        assertEquals(2, count);

        assertEquals(0, dataClass.getDatas().size());
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
        assertEquals(2, count);
        assertEquals(2, dataClass.getDatas().size());
        verifyAliases(new ArrayList<>(Arrays.asList("a", "b", "c")));
    }

    private void testDeleteExpData(ExpDataClassImpl dataClass, User user, int expectedCount)
    {
        List<? extends ExpData> datas = dataClass.getDatas();
        assertEquals(expectedCount, datas.size());

        datas.get(0).delete(user);
        assertEquals(expectedCount-1, dataClass.getDatas().size());
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
            assertEquals(new TableSelector(ExperimentService.get().getTinfoAlias(),
                    new SimpleFilter(FieldKey.fromParts("name"), name), null).getRowCount(), 1);
        }
    }

    // Issue 35013: Importing a file with zero rows into a DataClass results in NPE
    private void testEmptyInsert(ExpDataClassImpl dataClass, TableInfo table, User user) throws Exception
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        ListofMapsDataIterator.Builder b = new ListofMapsDataIterator.Builder(new CaseInsensitiveHashSet("name", "flag", "alias", "aa"), rows);

        BatchValidationException errors = new BatchValidationException();
        int count = table.getUpdateService().importRows(user, c, b, errors, null, null);
        if (errors.hasErrors())
            throw errors;
        assertEquals(0, count);
    }



    @Test
    public void testDataClassFromTemplate() throws Exception
    {
        if (!AppProps.getInstance().isDevMode()) // Skip test in production mode if necessary modules are not available
        {
            Assume.assumeTrue("List module is required to test data class templates", ModuleLoader.getInstance().getModule("list") != null);
            Assume.assumeTrue("simpletest module is required to test data class templates", ModuleLoader.getInstance().getModule("simpletest") != null);
        }

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
        assertFalse(
                "Errors in template: " + StringUtils.join(templateGroup.getErrors(), ", "),
                templateGroup.hasErrors());

        DomainTemplate template = templateGroup.getTemplate("testingFromTemplate");
        Assert.assertNotNull(template);

        final Domain domain = template.createAndImport(c, user, domainName, true, false);
        Assert.assertNotNull(domain);

        TemplateInfo t = domain.getTemplateInfo();
        Assert.assertNotNull("Expected template information to be persisted", t);
        assertEquals("simpletest", t.getModuleName());
        assertEquals("TestingFromTemplate", t.getTemplateGroupName());
        assertEquals("testingFromTemplate", t.getTableName());

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
        assertEquals("lists", aaLookupTable.getPublicSchemaName());
        assertEquals(listName, aaLookupTable.getName());
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


    @Test
    public void testDomainTemplate() throws Exception
    {
        if (!AppProps.getInstance().isDevMode()) // Skip test in production mode if necessary modules are not available
        {
            Assume.assumeTrue("List module is required to test domain templates", ModuleLoader.getInstance().getModule("list") != null);
            Assume.assumeTrue("simpletest module is required to test domain templates", ModuleLoader.getInstance().getModule("simpletest") != null);
        }

        final User user = TestContext.get().getUser();
        final Container sub = ContainerManager.createContainer(c, "sub3");

        Set<Module> activeModules = new HashSet<>(c.getActiveModules());
        Module m = ModuleLoader.getInstance().getModule("simpletest");
        Assert.assertNotNull("This test requires 'simplemodule' to be deployed", m);
        activeModules.add(m);
        c.setActiveModules(activeModules);

        DomainTemplateGroup templateGroup = DomainTemplateGroup.get(c, "todolist");
        Assert.assertNotNull(templateGroup);
        assertFalse(
                "Errors in template: " + StringUtils.join(templateGroup.getErrors(), ", "),
                templateGroup.hasErrors());

        List<Domain> created = templateGroup.createAndImport(sub, user, true, true);

        // verify the "Priority" list was created and data was imported
        UserSchema listSchema = QueryService.get().getUserSchema(user, sub, "lists");
        TableInfo priorityTable = listSchema.getTable("Priority");

        Collection<Map<String, Object>> priorities = new TableSelector(priorityTable).getMapCollection();
        assertEquals(5, priorities.size());

        // Issue 27729: sequence not updated to max after bulk import with importIdentity turned on
        // verify that we can insert a new priority
        {
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

            assertEquals(1, ret.size());
            assertEquals(5, ((Integer) ret.get(0).get("pri")).longValue());
            assertEquals("p5", ret.get(0).get("title"));
        }

        // Issue 35579: Cannot create non-auto-increment Integer PK list via Domain.create
        ListDefinition milestoneListDef = ListService.get().getList(sub, "Milestone");
        Assert.assertEquals(ListDefinition.KeyType.Integer, milestoneListDef.getKeyType());

        // verify that we can insert a new milestone
        {
            TableInfo milestoneTable = listSchema.getTable("Milestone");
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, Object> row = new CaseInsensitiveHashMap<>();
            row.put("m", "3");
            row.put("title", "Milestone 3");
            rows.add(row);

            List<Map<String, Object>> ret;
            try (DbScope.Transaction tx = milestoneTable.getSchema().getScope().beginTransaction())
            {
                BatchValidationException errors = new BatchValidationException();
                ret = milestoneTable.getUpdateService().insertRows(user, sub, rows, errors, null, null);
                if (errors.hasErrors())
                    throw errors;
                tx.commit();
            }

            Assert.assertEquals(1, ret.size());
            Assert.assertEquals(3, ((Integer)ret.get(0).get("m")).longValue());
            Assert.assertEquals("Milestone 3", ret.get(0).get("title"));
        }

        // verify the "TodoList" DataClass was created and data was imported
        UserSchema expSchema = QueryService.get().getUserSchema(user, sub, expDataSchemaKey);
        TableInfo table = expSchema.getTable("TodoList");
        Assert.assertNotNull("data class not in query schema", table);

        Collection<Map<String, Object>> todos = new TableSelector(table, TableSelector.ALL_COLUMNS, null, null).getMapCollection();
        assertEquals(1, todos.size());
        Map<String, Object> todo = todos.iterator().next();
        assertEquals("create xsd", todo.get("title"));

        ExpDataClass dataClass = ExperimentServiceImpl.get().getDataClass(sub, "TodoList");
        ExpData data = ExperimentServiceImpl.get().getExpData(dataClass, "TODO-1");

        Collection<String> aliases = data.getAliases();
        Assert.assertTrue("Expected aliases to contain 'xsd' and 'domain templates', got: " + aliases, aliases.containsAll(Arrays.asList("xsd", "domain templates")));
    }


    // Issue 25224: NPE trying to delete a folder with a DataClass with at least one result row in it
    @Test
    public void testContainerDelete() throws Exception
    {
        final User user = TestContext.get().getUser();
        final Container sub = ContainerManager.createContainer(c, "sub");

        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("aa", "int"));

        final ExpDataClassImpl dataClass = ExperimentServiceImpl.get().createDataClass(c, user, "testing", null, props, emptyList(), null);
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

            assertEquals(1, ret.size());
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

            assertEquals(1, ret.size());
            dataRowId2 = ((Integer)ret.get(0).get("RowId")).intValue();
        }

        // test: delete container, ensure everything is removed
        {
            // verify exists
            Assert.assertNotNull(ExperimentServiceImpl.get().getDataClass(dataClassId));
            Assert.assertNotNull(ExperimentServiceImpl.get().getExpData(dataRowId1));
            Assert.assertNotNull(ExperimentServiceImpl.get().getExpData(dataRowId2));

            String storageTableName = dataClass.getTinfo().getName();
            DbSchema dbSchema = DbSchema.get("labkey.expdataclass", DbSchemaType.Provisioned);
            TableInfo dbTable = dbSchema.getTable(storageTableName);
            Assert.assertNotNull(dbTable);

            // delete
            ContainerManager.deleteAll(c, user);

            // verify deleted
            Assert.assertNull(ExperimentServiceImpl.get().getDataClass(dataClassId));
            Assert.assertNull(ExperimentServiceImpl.get().getExpData(dataRowId1));
            Assert.assertNull(ExperimentServiceImpl.get().getExpData(dataRowId2));

            dbTable = dbSchema.getTable(storageTableName);
            Assert.assertNull(dbTable);
        }
    }

    // Issue 26129: sqlserver maximum size of index keys must be < 900 bytes
    @Test
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
            final ExpDataClassImpl dataClass = ExperimentServiceImpl.get().createDataClass(c, user, "largeUnique", null, props, indices, null);
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

    @Test
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

        final ExpDataClassImpl dataClass = ExperimentServiceImpl.get().createDataClass(c, user, "largeUnique2", null, props, indices, null, nameExpr, null, null);
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
                Assert.assertTrue("Expected error to start with '" + SqlDialect.CUSTOM_UNIQUE_ERROR_MESSAGE + "', got '" + e.getMessage() + "'",
                        e.getMessage().startsWith(SqlDialect.CUSTOM_UNIQUE_ERROR_MESSAGE));
            }
            Throwable t = e.getLastRowError().getGlobalError(0).getCause();
            Assert.assertTrue("Expected a SQLException", t instanceof SQLException);
            Assert.assertTrue("Expected a constraint violation", RuntimeSQLException.isConstraintException((SQLException)t));
        }
    }

    @Test
    public void testDataClassWithVocabularyProperties() throws Exception
    {
        User user = TestContext.get().getUser();

        String dataClassName = "DataClassesWithVocabularyProperties";
        int dataClassAge = 2;
        int updatedDataClassAge = 4;
        String dataClassType = "TypeB";
        String dataClassColor = "Orange";

        Domain testDomain = createVocabularyTestDomain(user, c);
        Map<String, String> vocabularyPropertyURIs = getVocabularyPropertyURIS(testDomain);

        final String colorPropertyURI = vocabularyPropertyURIs.get(colorPropertyName);
        final String agePropertyURI = vocabularyPropertyURIs.get(agePropertyName);
        final String typePropertyURI = vocabularyPropertyURIs.get(typePropertyName);

        ExpDataClassImpl dataClass = ExperimentServiceImpl.get().createDataClass(c, user, dataClassName, null,
                List.of(new GWTPropertyDescriptor("OtherProp", "string")), Collections.emptyList(), null);
        assertNotNull(dataClass);

        // insert a data class
        ArrayListMap<String, Object> rowToInsert = new ArrayListMap<>();
        rowToInsert.put("OtherProp", "OtherValue");
        // inserting a property with null value
        rowToInsert.put(colorPropertyURI, null);
        rowToInsert.put(agePropertyURI, dataClassAge);
        rowToInsert.put(typePropertyURI, dataClassType);
        rowToInsert.put("Name", "WithOtherProp");
        List<Map<String, Object>> rowsToInsert = buildRows(rowToInsert);

        var insertedDataClassRows = insertRows(c, rowsToInsert, dataClassName);
        assertEquals(1, insertedDataClassRows.size());

        var insertedDataClass = insertedDataClassRows.get(0);
        Integer insertedRowId = (Integer)insertedDataClass.get("RowId");
        String insertedLsid = (String)insertedDataClass.get("LSID");

        Map<String, ObjectProperty> insertedProps = OntologyManager.getPropertyObjects(c, insertedLsid);
        assertEquals("Expected only two properties after insert: " + insertedProps, 2, insertedProps.size());
        assertEquals("Custom Property is not inserted", dataClassAge, insertedProps.get(agePropertyURI).getFloatValue().intValue());

        // Verifying property with null value is not inserted
        assertFalse("Property with null value is present.", insertedProps.containsKey(colorPropertyURI));

        ArrayListMap<String, Object> rowToUpdate = new ArrayListMap<>();
        rowToUpdate.put("RowId", insertedRowId);
        rowToUpdate.put(typePropertyURI, null);
        // updating existing property with null value
        rowToUpdate.put(agePropertyURI, updatedDataClassAge);
        // inserting a new property in update rows
        rowToUpdate.put(colorPropertyURI, dataClassColor);
        List<Map<String, Object>> rowsToUpdate = buildRows(rowToUpdate);

        List<Map<String, Object>> oldKeys = new ArrayList<>();
        ArrayListMap<String, Object> oldKey = new ArrayListMap<>();
        oldKey.put("RowId", insertedRowId);
        oldKeys.add(oldKey);

        updateRows(c, rowsToUpdate, oldKeys, dataClassName);

        Map<String, ObjectProperty> updatedProps = OntologyManager.getPropertyObjects(c, insertedLsid);
        assertEquals("Expected only two properties after update: " + updatedProps, 2, updatedProps.size());
        assertEquals("Custom Property is not updated",
                updatedDataClassAge, updatedProps.get(agePropertyURI).getFloatValue().intValue());

        // Verify property inserted during update rows in inserted
        assertEquals("New Property is not inserted with update rows", dataClassColor,
                updatedProps.get(colorPropertyURI).getStringValue());

        // Verify property updated to a null value gets deleted
        assertFalse("Property with null value is present.", updatedProps.containsKey(typePropertyURI));
    }

    @Test
    public void testViewSupportForVocabularyDomains() throws Exception
    {
        User user = TestContext.get().getUser();

        // Create a sample type with name
        String sampleName = "CarLocations";
        String sampleOneLocation = "California";

        ExpSampleTypeImpl ss = SampleTypeServiceImpl.get().createSampleType(c, user,
                sampleName, null, List.of(new GWTPropertyDescriptor("name", "string")), Collections.emptyList(),
                -1, -1, -1, -1, null, null);

        UserSchema schema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts("Samples"));

        // Insert a sample into sample type
        ArrayListMap<String, Object> row = new ArrayListMap<>();
        row.put("name", sampleOneLocation);

        List<Map<String, Object>> rows = buildRows(row);

        var insertedSampleRows = insertRows(c, rows ,sampleName, schema);

        var insertedSample = insertedSampleRows.get(0);
        Integer insertedSampleRowId = (Integer)insertedSample.get("RowId");

        // Create a vocab domain with lookup prop to the sample type
        String domainName = "LookUpVocabularyDomain";
        String domainDescription = "This is a lookup vocabulary for car locations.";
        String locationPropertyName = "Location";
        String lookUpSchema = "samples";

        GWTPropertyDescriptor prop1 = new GWTPropertyDescriptor();
        prop1.setRangeURI("int");
        prop1.setName(locationPropertyName);
        prop1.setLookupSchema(lookUpSchema);
        prop1.setLookupQuery(sampleName);

        GWTDomain domain = new GWTDomain();
        domain.setName(domainName);
        domain.setDescription(domainDescription);
        domain.setFields(List.of(prop1));

        Domain lookUpDomain = DomainUtil.createDomain("Vocabulary", domain, null, c, user, domainName, null);

        Map<String, String> vocabularyPropertyURIs = getVocabularyPropertyURIS(lookUpDomain);
        final String locationPropertyURI = vocabularyPropertyURIs.get(locationPropertyName);

        // Create a data class
        String dataClassName = "CarsDataClasses";
        String carName1 = "Tesla";

        ExpDataClassImpl dataClass = ExperimentServiceImpl.get().createDataClass(c, user, dataClassName, null,
                List.of(new GWTPropertyDescriptor("OtherProp", "string")), Collections.emptyList(), null);

        UserSchema userSchema = QueryService.get().getUserSchema(user, c, expDataSchemaKey);

        // insert a data class with vocab look up prop using row id of inserted sample
        ArrayListMap<String, Object> rowToInsert = new ArrayListMap<>();
        rowToInsert.put("Name", carName1);
        rowToInsert.put("OtherProp", "OtherValue");
        rowToInsert.put(locationPropertyURI, insertedSampleRowId);
        List<Map<String, Object>> rowsToInsert = buildRows(rowToInsert);
        insertRows(c, rowsToInsert, dataClassName);

        PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(locationPropertyURI, c);
        TableInfo tableInfo = DefaultSchema.get(userSchema.getUser(), c).getSchema(pd.getLookupSchema()).getTable(pd.getLookupQuery(), null);
        ColumnInfo vocabularyDomainColumn = tableInfo.getColumn(domainName + lookUpDomain.getTypeId());
        ColumnInfo propertiesColumn = tableInfo.getColumn("Properties");

        // Verify vocabulary domains got attached to the data class
        assertNotNull("TestVocabularyDomain is not present in table columns.", vocabularyDomainColumn);

        // Verify properties column got attached to the data class
        assertNotNull("Properties column is not present in table columns.", propertiesColumn);

        // Verify the values for lookup props
        Map<String, Object> lookUpProp = new TableSelector(tableInfo, SimpleFilter.createContainerFilter(c), null).getMap();
        String insertedLookUpValue = lookUpProp.getOrDefault("Name", null).toString();
        assertEquals("Look up value is not found or equal.", sampleOneLocation, insertedLookUpValue);
    }
}

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

<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.jetbrains.annotations.NotNull" %>
<%@ page import="org.junit.After" %>
<%@ page import="org.junit.Assume" %>
<%@ page import="org.junit.Before" %>
<%@ page import="org.junit.Test" %>
<%@ page import="org.labkey.api.action.ApiUsageException" %>
<%@ page import="org.labkey.api.audit.AuditLogService" %>
<%@ page import="org.labkey.api.audit.AuditTypeEvent" %>
<%@ page import="org.labkey.api.audit.DetailedAuditTypeEvent" %>
<%@ page import="org.labkey.api.collections.ArrayListMap" %>
<%@ page import="org.labkey.api.collections.CaseInsensitiveHashMap" %>
<%@ page import="org.labkey.api.data.ColumnInfo" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.data.DbSchema" %>
<%@ page import="org.labkey.api.data.DbSchemaType" %>
<%@ page import="org.labkey.api.data.DbScope" %>
<%@ page import="org.labkey.api.data.JdbcType" %>
<%@ page import="org.labkey.api.data.PropertyStorageSpec" %>
<%@ page import="org.labkey.api.data.RuntimeSQLException" %>
<%@ page import="org.labkey.api.data.SimpleFilter" %>
<%@ page import="org.labkey.api.data.Sort" %>
<%@ page import="org.labkey.api.data.TableInfo" %>
<%@ page import="org.labkey.api.data.TableSelector" %>
<%@ page import="org.labkey.api.dataiterator.DataIteratorContext" %>
<%@ page import="org.labkey.api.dataiterator.DetailedAuditLogDataIterator" %>
<%@ page import="org.labkey.api.dataiterator.MapDataIterator" %>
<%@ page import="org.labkey.api.exp.ExperimentException" %>
<%@ page import="org.labkey.api.exp.ObjectProperty" %>
<%@ page import="org.labkey.api.exp.OntologyManager" %>
<%@ page import="org.labkey.api.exp.PropertyDescriptor" %>
<%@ page import="org.labkey.api.exp.TemplateInfo" %>
<%@ page import="org.labkey.api.exp.api.DataClassDomainKindProperties" %>
<%@ page import="org.labkey.api.exp.api.ExpData" %>
<%@ page import="org.labkey.api.exp.api.ExpDataClass" %>
<%@ page import="org.labkey.api.exp.api.ExperimentService" %>
<%@ page import="org.labkey.api.exp.list.ListDefinition" %>
<%@ page import="org.labkey.api.exp.list.ListItem" %>
<%@ page import="org.labkey.api.exp.list.ListService" %>
<%@ page import="org.labkey.api.exp.property.Domain" %>
<%@ page import="org.labkey.api.exp.property.DomainKind" %>
<%@ page import="org.labkey.api.exp.property.DomainTemplate" %>
<%@ page import="org.labkey.api.exp.property.DomainTemplateGroup" %>
<%@ page import="org.labkey.api.exp.property.DomainUtil" %>
<%@ page import="org.labkey.api.exp.property.Lookup" %>
<%@ page import="org.labkey.api.exp.property.PropertyService" %>
<%@ page import="org.labkey.api.exp.query.ExpDataTable" %>
<%@ page import="org.labkey.api.exp.query.ExpSchema" %>
<%@ page import="org.labkey.api.gwt.client.AuditBehaviorType" %>
<%@ page import="org.labkey.api.gwt.client.model.GWTDomain" %>
<%@ page import="org.labkey.api.gwt.client.model.GWTIndex" %>
<%@ page import="org.labkey.api.gwt.client.model.GWTPropertyDescriptor" %>
<%@ page import="org.labkey.api.module.Module" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.api.query.BatchValidationException" %>
<%@ page import="org.labkey.api.query.DefaultSchema" %>
<%@ page import="org.labkey.api.query.FieldKey" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.query.QueryUpdateService" %>
<%@ page import="org.labkey.api.query.SchemaKey" %>
<%@ page import="org.labkey.api.query.UserSchema" %>
<%@ page import="org.labkey.api.search.SearchService" %>
<%@ page import="org.labkey.api.security.SecurityManager" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.settings.ConceptURIProperties" %>
<%@ page import="org.labkey.api.util.TestContext" %>
<%@ page import="org.labkey.experiment.api.DataClassDomainKind" %>
<%@ page import="org.labkey.experiment.api.ExpDataClassImpl" %>
<%@ page import="org.labkey.experiment.api.ExpProvisionedTableTestHelper" %>
<%@ page import="org.labkey.experiment.api.ExpSampleTypeImpl" %>
<%@ page import="org.labkey.experiment.api.ExperimentServiceImpl" %>
<%@ page import="org.labkey.experiment.api.SampleTypeServiceImpl" %>
<%@ page import="org.labkey.remoteapi.ApiKeyCredentialsProvider" %>
<%@ page import="org.labkey.remoteapi.Connection" %>
<%@ page import="org.labkey.remoteapi.query.Filter" %>
<%@ page import="org.labkey.remoteapi.query.SelectRowsCommand" %>
<%@ page import="org.labkey.remoteapi.query.SelectRowsResponse" %>
<%@ page import="java.sql.SQLException" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="static java.util.Collections.emptyList" %>
<%@ page import="static org.junit.Assert.*" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.util.concurrent.TimeUnit" %>
<%@ page import="java.util.stream.Collectors" %>
<%@ page import="static org.labkey.api.util.PageFlowUtil.encodeURIComponent" %>
<%@ page import="static org.labkey.api.util.IntegerUtils.asInteger" %>
<%@ page import="static org.labkey.api.util.IntegerUtils.asLong" %>
<%@ page import="static org.hamcrest.Matchers.containsString" %>
<%@ page extends="org.labkey.api.jsp.JspTest.BVT" %>

<%!
ExpProvisionedTableTestHelper helper = new ExpProvisionedTableTestHelper();
Container c;

@Before
public void setUp()
{
    // NOTE: We need to use a project to create the DataClass so we can insert rows into sub-folders
    c = ContainerManager.getForPath("_testDataClass");
    if (c != null)
        ContainerManager.deleteAll(c, TestContext.get().getUser());
    c = ContainerManager.createContainer(ContainerManager.getRoot(), "_testDataClass", TestContext.get().getUser());
}

@After
public void tearDown() throws InterruptedException
{
    // Wait for the indexer to finish working on the data we just added to help avoid deadlocks
    SearchService.get().drainQueue(SearchService.PRIORITY.crawl, 15, TimeUnit.SECONDS);
    ContainerManager.deleteAll(c, TestContext.get().getUser());
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

        ExperimentServiceImpl.get().createDataClass(c, user, null, null, props, emptyList(), null, null);
    }
    catch (ApiUsageException e)
    {
        assertEquals("DataClass name is required.", e.getMessage());
    }
}

@Test // Issue 51321
public void reservedNameFirst() throws Exception
{
    final User user = TestContext.get().getUser();

    try
    {
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("foo", "string"));

        ExperimentServiceImpl.get().createDataClass(c, user, "First", null, props, emptyList(), null, null);
    }
    catch (ApiUsageException e)
    {
        assertEquals("Data Class name 'First' is a reserved name.", e.getMessage());
    }
}

@Test // Issue 51321
public void reservedNameAll() throws Exception
{
    final User user = TestContext.get().getUser();

    try
    {
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("foo", "string"));

        ExperimentServiceImpl.get().createDataClass(c, user, "All", null, props, emptyList(), null, null);
    }
    catch (ApiUsageException e)
    {
        assertEquals("Data Class name 'All' is a reserved name.", e.getMessage());
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
        ExperimentServiceImpl.get().createDataClass(c, user, name, null, props, emptyList(), null, null);
    }
    catch (ApiUsageException e)
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

        DataClassDomainKindProperties options = new DataClassDomainKindProperties();
        options.setNameExpression(StringUtils.repeat("a", 1000));

        ExperimentServiceImpl.get().createDataClass(c, user, "testing", options, props, emptyList(), null, null);
    }
    catch (ApiUsageException e)
    {
        assertEquals("Name expression may not exceed 500 characters.", e.getMessage());
    }
}


@Test
public void testDataClass() throws Exception
{
    final User user = TestContext.get().getUser();
    final Container sub = ContainerManager.createContainer(c, "sub", TestContext.get().getUser());
    final String dataClassName = "testing";

    List<GWTPropertyDescriptor> props = new ArrayList<>();
    props.add(new GWTPropertyDescriptor("aa", "int"));
    props.add(new GWTPropertyDescriptor("bb", "string"));

    List<GWTIndex> indices = new ArrayList<>();
    indices.add(new GWTIndex(Arrays.asList("aa"), true));

    DataClassDomainKindProperties options = new DataClassDomainKindProperties();
    options.setNameExpression("JUNIT-${genId}-${aa}");

    final ExpDataClassImpl dataClass = ExperimentServiceImpl.get().createDataClass(c, user, dataClassName, options, props, indices, null, null);
    assertNotNull(dataClass);

    final Domain domain = dataClass.getDomain();
    assertNotNull(domain);

    TableInfo table = getDataClassTable(dataClassName);
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
        ret = helper.insertRows(c, rows, table.getName());
        tx.commit();
    }

    assertEquals(1, ret.size());
    assertEquals(1, ret.getFirst().get("genId"));
    assertEquals(expectedName, ret.getFirst().get("name"));

    Long rowId = asLong(ret.getFirst().get("RowId"));
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
    row.put("bb", "bye");
    String expectedComment = "waving in the wind";
    row.put("flag", expectedComment);
    rows.add(row);

    List<Map<String, Object>> ret;
    try (DbScope.Transaction tx = table.getSchema().getScope().beginTransaction())
    {
        ret = helper.insertRows(sub, rows, table.getName());
        tx.commit();
    }

    assertEquals(1, ret.size());
    assertEquals(sub.getId(), ret.getFirst().get("folder"));

    ExpData data = ExperimentService.get().getExpData(dataClass, expectedSubName);
    assertNotNull(data);
    assertEquals(sub, data.getContainer());
    assertEquals(2, dataClass.getDatas().size());
    assertEquals(expectedComment, data.getComment());
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
        helper.insertRows(c, rows, table.getName());
        tx.commit();
        fail("Expected exception");
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
    assertNull(ExperimentService.get().getExpData(dataClass, expectedName));
    assertNull(ExperimentService.get().getExpData(dataClass, expectedSubName));
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

    int count = table.getUpdateService().loadRows(user, c, MapDataIterator.of(rows), new DataIteratorContext(), null);
    assertEquals(2, count);
    assertEquals(2, dataClass.getDatas().size());

    int rowId = new TableSelector(table, Collections.singleton("rowId"), new SimpleFilter("aa", 50).addCondition("bb", "zz"), null).getObject(Integer.class);

    verifyAliases(table, rowId, Set.of("a", "b", "c"));
}

private void testDeleteExpData(ExpDataClassImpl dataClass, User user, int expectedCount)
{
    List<? extends ExpData> datas = dataClass.getDatas();
    assertEquals(expectedCount, datas.size());

    datas.getFirst().delete(user);
    assertEquals(expectedCount-1, dataClass.getDatas().size());
}

private void testDeleteExpDataClass(ExpDataClassImpl dataClass, User user, TableInfo table, String typeURI)
{
    assertNotNull(ExperimentService.get().getDataClass(c, table.getName()));
    assertNotNull(PropertyService.get().getDomain(c, typeURI));

    String storageTableName = dataClass.getTinfo().getName();
    DbSchema dbSchema = DbSchema.get("labkey.expdataclass", DbSchemaType.Provisioned);
    TableInfo dbTable = dbSchema.getTable(storageTableName);
    assertNotNull(dbTable);

    dataClass.delete(user);

    assertNull(ExperimentService.get().getDataClass(c, table.getName()));
    assertNull(PropertyService.get().getDomain(c, typeURI));

    UserSchema schema1 = QueryService.get().getUserSchema(user, c, helper.expDataSchemaKey);
    assertNull(schema1.getTable(table.getName()));

    dbTable = dbSchema.getTable(storageTableName);
    assertNull(dbTable);
}

private void testInsertAliases(ExpDataClassImpl dataClass, TableInfo table) throws Exception
{
    List<Map<String, Object>> rows = new ArrayList<>();
    Map<String, Object> row = new CaseInsensitiveHashMap<>();
    row.put("aa", 20);
    row.put("bb", "bye");
    row.put("alias", "aa,bb");
    rows.add(row);

    long insertedRowId;
    try (DbScope.Transaction tx = table.getSchema().getScope().beginTransaction())
    {
        List<Map<String, Object>> ret = helper.insertRows(c, rows, table.getName());
        insertedRowId = asLong(ret.getFirst().get("rowId"));
        tx.commit();
    }

    verifyAliases(table, insertedRowId, Set.of("aa","bb"));
}

private void verifyAliases(TableInfo table, long expDataRowId, Set<String> expectedAliases) throws Exception
{
    for (String name : expectedAliases)
    {
        assertEquals(1, new TableSelector(ExperimentService.get().getTinfoAlias(),
                new SimpleFilter(FieldKey.fromParts("name"), name), null).getRowCount());
    }

    ExpData data = ExperimentService.get().getExpData(expDataRowId);
    Collection<String> actualAliases = data.getAliases();
    assertEquals(new HashSet<>(actualAliases), expectedAliases);

    verifyAliasesViaSelectRows("exp", "Data", expDataRowId, expectedAliases);
    verifyAliasesViaSelectRows("exp.data", table.getPublicName(), expDataRowId, expectedAliases);
}

private void verifyAliasesViaSelectRows(String schemaName, String queryName, long expDataRowId, Set<String> expectedAliases)
        throws Exception
{
    try (var session = SecurityManager.createTransformSession(TestContext.get().getRequest(), TestContext.get().getRequest().getSession()))
    {
        String baseURL = AppProps.getInstance().getBaseServerUrl() + AppProps.getInstance().getContextPath();
        Connection conn = new Connection(baseURL, new ApiKeyCredentialsProvider(session.getApiKey()));
        SelectRowsCommand cmd = new SelectRowsCommand(schemaName, queryName);
        cmd.setRequiredVersion(17.1);
        cmd.setColumns(List.of("RowId", "Name", ExpDataTable.Column.Alias.name()));
        cmd.setFilters(List.of(new Filter("rowId", expDataRowId, Filter.Operator.EQUAL)));
        SelectRowsResponse resp = cmd.execute(conn, c.getPath());

        assertEquals(1, resp.getRowCount().intValue());
        Map<String, Object> row0 = resp.getRows().getFirst();
        Map<String, Object> row0data = (Map<String, Object>)row0.get("data");
        // expected 17.1 format for the row's data:
        // {
        //    "RowId": {
        //        "value": 88080, "url": "..."
        //    },
        //    "Name": {
        //        "value": "JUNIT-5-50", "url": "..."
        //    },
        //    "Alias": [{
        //        "value": "a"
        //    },{
        //        "value": "b"
        //    },{
        //        "value": "c"
        //    }]
        //}
        List<Map<String, Object>> row0aliases = (List<Map<String, Object>>)row0data.get(ExpDataTable.Column.Alias.name());
        Set<String> aliases = row0aliases.stream().map(m -> (String)m.get("value")).collect(Collectors.toSet());
        assertEquals(aliases, expectedAliases);
    }
}

// Issue 35013: Importing a file with zero rows into a DataClass results in NPE
private void testEmptyInsert(ExpDataClassImpl dataClass, TableInfo table, User user) throws Exception
{
    List<Map<String, Object>> rows = new ArrayList<>();
    BatchValidationException errors = new BatchValidationException();
    int count = table.getUpdateService().importRows(user, c, MapDataIterator.of(rows), errors, null, null);
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
    final Container sub = ContainerManager.createContainer(c, "sub2", TestContext.get().getUser());
    final String domainName = "mydataclass";

    Set<Module> activeModules = new HashSet<>(c.getActiveModules());
    Module m = ModuleLoader.getInstance().getModule("simpletest");
    assertNotNull("This test requires 'simplemodule' to be deployed", m);
    activeModules.add(m);
    c.setActiveModules(activeModules);

    DomainTemplateGroup templateGroup = DomainTemplateGroup.get(c, "TestingFromTemplate");
    assertNotNull(templateGroup);
    assertFalse(
            "Errors in template: " + StringUtils.join(templateGroup.getErrors(), ", "),
            templateGroup.hasErrors());

    DomainTemplate template = templateGroup.getTemplate("testingFromTemplate");
    assertNotNull(template);

    final Domain domain = template.createAndImport(c, user, domainName, true, false);
    assertNotNull(domain);

    TemplateInfo t = domain.getTemplateInfo();
    assertNotNull("Expected template information to be persisted", t);
    assertEquals("simpletest", t.getModuleName());
    assertEquals("TestingFromTemplate", t.getTemplateGroupName());
    assertEquals("testingFromTemplate", t.getTableName());

    DomainKind kind = domain.getDomainKind();
    assertTrue(kind instanceof DataClassDomainKind);
    Set<String> mandatory = kind.getMandatoryPropertyNames(domain);
    assertTrue("Expected template to set 'aa' as mandatory: " + mandatory, mandatory.contains("aa"));

    ExpDataClassImpl dataClass = (ExpDataClassImpl)ExperimentService.get().getDataClass(c, domainName);
    assertNotNull(dataClass);

    // add ConceptURI mappings for this container
    String listName = createConceptLookupList(c, user);
    Lookup lookup = new Lookup(c, "lists", listName);
    ConceptURIProperties.setLookup(c, "http://cpas.labkey.com/Experiment#Testing", lookup);

    // verify that the lookup from the ConceptURI mapping is applied as a FK to the column
    TableInfo table = getDataClassTable(domainName);
    TableInfo aaLookupTable = table.getColumn("aa").getFkTableInfo();
    assertNotNull(aaLookupTable);
    assertEquals("lists", aaLookupTable.getPublicSchemaName());
    assertEquals(listName, aaLookupTable.getName());
    assertNull(table.getColumn("bb").getFkTableInfo());

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
    final Container sub = ContainerManager.createContainer(c, "sub3", user);

    Set<Module> activeModules = new HashSet<>(c.getActiveModules());
    Module m = ModuleLoader.getInstance().getModule("simpletest");
    assertNotNull("This test requires 'simplemodule' to be deployed", m);
    activeModules.add(m);
    c.setActiveModules(activeModules);

    DomainTemplateGroup templateGroup = DomainTemplateGroup.get(c, "todolist");
    assertNotNull(templateGroup);
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
        assertEquals(5, asInteger(ret.getFirst().get("pri")).intValue());
        assertEquals("p5", ret.getFirst().get("title"));
    }

    // Issue 35579: Cannot create non-auto-increment Integer PK list via Domain.create
    ListDefinition milestoneListDef = ListService.get().getList(sub, "Milestone");
    assertEquals(ListDefinition.KeyType.Integer, milestoneListDef.getKeyType());

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

        assertEquals(1, ret.size());
        assertEquals(3, asInteger(ret.getFirst().get("m")).intValue());
        assertEquals("Milestone 3", ret.getFirst().get("title"));
    }

    // verify the "TodoList" DataClass was created and data was imported
    UserSchema expSchema = QueryService.get().getUserSchema(user, sub, helper.expDataSchemaKey);
    TableInfo table = expSchema.getTable("TodoList");
    assertNotNull("data class not in query schema", table);

    Collection<Map<String, Object>> todos = new TableSelector(table, TableSelector.ALL_COLUMNS, null, null).getMapCollection();
    assertEquals(1, todos.size());
    Map<String, Object> todo = todos.iterator().next();
    assertEquals("create xsd", todo.get("title"));

    ExpDataClass dataClass = ExperimentServiceImpl.get().getDataClass(sub, "TodoList");
    ExpData data = ExperimentServiceImpl.get().getExpData(dataClass, "TODO-1");

    Collection<String> aliases = data.getAliases();
    assertTrue("Expected aliases to contain 'xsd' and 'domain templates', got: " + aliases, aliases.containsAll(Arrays.asList("xsd", "domain templates")));
}


// Issue 25224: NPE trying to delete a folder with a DataClass with at least one result row in it
@Test
public void testContainerDelete() throws Exception
{
    final User user = TestContext.get().getUser();
    final Container sub = ContainerManager.createContainer(c, "sub", user);
    final String dataClassName = "testing";

    List<GWTPropertyDescriptor> props = new ArrayList<>();
    props.add(new GWTPropertyDescriptor("aa", "int"));

    final ExpDataClassImpl dataClass = ExperimentServiceImpl.get().createDataClass(c, user, dataClassName, null, props, emptyList(), null, null);
    final long dataClassId = dataClass.getRowId();

    TableInfo table = getDataClassTable(dataClassName);

    // setup: insert into junit container
    long dataRowId1;
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new CaseInsensitiveHashMap<>();
        row.put("name", "first");
        row.put("aa", 20);
        rows.add(row);

        List<Map<String, Object>> ret;
        try (DbScope.Transaction tx = table.getSchema().getScope().beginTransaction())
        {
            ret = helper.insertRows(c, rows, table.getName());
            tx.commit();
        }

        assertEquals(1, ret.size());
        dataRowId1 = asLong(ret.getFirst().get("RowId"));
    }

    // setup: insert into sub container
    long dataRowId2;
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new CaseInsensitiveHashMap<>();
        row.put("name", "second");
        row.put("aa", 30);
        rows.add(row);

        List<Map<String, Object>> ret;
        try (DbScope.Transaction tx = table.getSchema().getScope().beginTransaction())
        {
            ret = helper.insertRows(sub, rows, table.getName());
            tx.commit();
        }

        assertEquals(1, ret.size());
        dataRowId2 = asLong(ret.getFirst().get("RowId"));
    }

    // test: delete container, ensure everything is removed
    {
        // verify exists
        assertNotNull(ExperimentServiceImpl.get().getDataClass(dataClassId));
        assertNotNull(ExperimentServiceImpl.get().getExpData(dataRowId1));
        assertNotNull(ExperimentServiceImpl.get().getExpData(dataRowId2));

        String storageTableName = dataClass.getTinfo().getName();
        DbSchema dbSchema = DbSchema.get("labkey.expdataclass", DbSchemaType.Provisioned);
        TableInfo dbTable = dbSchema.getTable(storageTableName);
        assertNotNull(dbTable);

        // delete
        ContainerManager.deleteAll(c, user);

        // verify deleted
        assertNull(ExperimentServiceImpl.get().getDataClass(dataClassId));
        assertNull(ExperimentServiceImpl.get().getExpData(dataRowId1));
        assertNull(ExperimentServiceImpl.get().getExpData(dataRowId2));

        dbTable = dbSchema.getTable(storageTableName);
        assertNull(dbTable);
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
        final ExpDataClassImpl dataClass = ExperimentServiceImpl.get().createDataClass(c, user, "largeUnique", null, props, indices, null, null);
        if (sqlServer)
            fail("Expected exception creating large index over two columns");
    }
    catch (IllegalArgumentException ex)
    {
        // Not supported on SQL Server
        String msg = ex.getMessage();
        String expected = "Index over large columns is not supported";
        assertTrue("Unexpected message: " + ex.getMessage(), msg.contains(expected));
    }
}

@Test
public void testLargeUnique() throws Exception
{
    final User user = TestContext.get().getUser();

    boolean sqlServer = ExperimentService.get().getSchema().getSqlDialect().isSqlServer();
    List<GWTPropertyDescriptor> props = new ArrayList<>();
    props.add(new GWTPropertyDescriptor("aa", "int"));
    GWTPropertyDescriptor prop = new GWTPropertyDescriptor("bb", "multiLine");
    prop.setScale(20000);
    props.add(prop);

    List<GWTIndex> indices = new ArrayList<>();
    indices.add(new GWTIndex(Arrays.asList("bb"), true));

    DataClassDomainKindProperties options = new DataClassDomainKindProperties();
    options.setNameExpression("JUNIT-${genId}-${aa}");

    ExpDataClassImpl dataClass;
    try
    {
        dataClass = ExperimentServiceImpl.get().createDataClass(c, user, "largeUnique2", options, props, indices, null, null);
    }
    catch (IllegalArgumentException e)
    {
        // Not supported on SQL Server, so create with no indices
        assertTrue("Expected exception creating large index over two columns", e.getMessage().contains("Index over large columns is not supported"));
        assertTrue(sqlServer);
        dataClass = ExperimentServiceImpl.get().createDataClass(c, user, "largeUnique2", options, props, List.of(), null, null);
    }

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
        helper.insertRows(c, rows, dataClass.getName());
        tx.commit();
    }

    // second insert should fail
    try (DbScope.Transaction tx = ExperimentService.get().getSchema().getScope().beginTransaction())
    {
        helper.insertRows(c, rows, dataClass.getName());
        if (!sqlServer)
            fail("Expected constraint exception");
    }
    catch (BatchValidationException e)
    {
        Throwable t = e.getLastRowError().getGlobalError(0).getCause();
        assertTrue("Expected a SQLException", t instanceof SQLException);
        assertTrue("Expected a constraint violation", RuntimeSQLException.isConstraintException((SQLException)t));
    }
}

private ArrayListMap<String, Object> newArrayListMap()
{
    return new ArrayListMap<>(new ArrayListMap.FindMap<>(new CaseInsensitiveHashMap<>()));
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

    Domain testDomain = helper.createVocabularyTestDomain(user, c);
    Map<String, String> vocabularyPropertyURIs = helper.getVocabularyPropertyURIS(testDomain);

    final String colorPropertyURI = vocabularyPropertyURIs.get(ExpProvisionedTableTestHelper.colorPropertyName);
    final String agePropertyURI = vocabularyPropertyURIs.get(ExpProvisionedTableTestHelper.agePropertyName);
    final String typePropertyURI = vocabularyPropertyURIs.get(ExpProvisionedTableTestHelper.typePropertyName);

    ExpDataClassImpl dataClass = ExperimentServiceImpl.get().createDataClass(c, user, dataClassName, null,
            List.of(new GWTPropertyDescriptor("OtherProp", "string")), emptyList(), null, null);
    assertNotNull(dataClass);

    // insert a data class
    ArrayListMap<String, Object> rowToInsert = newArrayListMap();
    rowToInsert.put("OtherProp", "OtherValue");
    // inserting a property with null value
    rowToInsert.put(colorPropertyURI, null);
    rowToInsert.put(agePropertyURI, dataClassAge);
    rowToInsert.put(typePropertyURI, dataClassType);
    rowToInsert.put("Name", "WithOtherProp");
    List<Map<String, Object>> rowsToInsert = helper.buildRows(rowToInsert);

    var insertedDataClassRows = helper.insertRows(c, rowsToInsert, dataClassName);
    assertEquals(1, insertedDataClassRows.size());

    var insertedDataClass = insertedDataClassRows.getFirst();
    Long insertedRowId = asLong(insertedDataClass.get("RowId"));
    String insertedLsid = (String)insertedDataClass.get("LSID");

    Map<String, ObjectProperty> insertedProps = OntologyManager.getPropertyObjects(c, insertedLsid);
    assertEquals("Expected only two properties after insert: " + insertedProps, 2, insertedProps.size());
    assertEquals("Custom Property is not inserted", dataClassAge, insertedProps.get(agePropertyURI).getFloatValue().intValue());

    // Verifying property with null value is not inserted
    assertFalse("Property with null value is present.", insertedProps.containsKey(colorPropertyURI));

    ArrayListMap<String, Object> rowToUpdate = newArrayListMap();
    rowToUpdate.put("RowId", insertedRowId);
    rowToUpdate.put(typePropertyURI, null);
    // updating existing property with null value
    rowToUpdate.put(agePropertyURI, updatedDataClassAge);
    // inserting a new property in update rows
    rowToUpdate.put(colorPropertyURI, dataClassColor);
    List<Map<String, Object>> rowsToUpdate = helper.buildRows(rowToUpdate);

    List<Map<String, Object>> oldKeys = new ArrayList<>();
    ArrayListMap<String, Object> oldKey = newArrayListMap();
    oldKey.put("RowId", insertedRowId);
    oldKeys.add(oldKey);

    helper.updateRows(c, rowsToUpdate, oldKeys, dataClassName);

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

    ExpSampleTypeImpl st = SampleTypeServiceImpl.get().createSampleType(c, user,
            sampleName, null, List.of(new GWTPropertyDescriptor("name", "string")), emptyList(),
            -1, -1, -1, -1, null, null);

    UserSchema schema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts("Samples"));

    // Insert a sample into sample type
    ArrayListMap<String, Object> row = newArrayListMap();
    row.put("name", sampleOneLocation);

    List<Map<String, Object>> rows = helper.buildRows(row);

    var insertedSampleRows = helper.insertRows(c, rows ,sampleName, schema);

    var insertedSample = insertedSampleRows.getFirst();
    Long insertedSampleRowId = asLong(insertedSample.get("RowId"));

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

    Domain lookUpDomain = DomainUtil.createDomain("Vocabulary", domain, null, c, user, domainName, null, false);

    Map<String, String> vocabularyPropertyURIs = helper.getVocabularyPropertyURIS(lookUpDomain);
    final String locationPropertyURI = vocabularyPropertyURIs.get(locationPropertyName);

    // Create a data class
    String dataClassName = "CarsDataClasses";
    String carName1 = "Tesla";

    ExpDataClassImpl dataClass = ExperimentServiceImpl.get().createDataClass(c, user, dataClassName, null,
            List.of(new GWTPropertyDescriptor("OtherProp", "string")), emptyList(), null, null);

    UserSchema userSchema = QueryService.get().getUserSchema(user, c, helper.expDataSchemaKey);

    // insert a data class with vocab look up prop using row id of inserted sample
    ArrayListMap<String, Object> rowToInsert = newArrayListMap();
    rowToInsert.put("Name", carName1);
    rowToInsert.put("OtherProp", "OtherValue");
    rowToInsert.put(locationPropertyURI, insertedSampleRowId);
    List<Map<String, Object>> rowsToInsert = helper.buildRows(rowToInsert);
    helper.insertRows(c, rowsToInsert, dataClassName);

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

@Test
public void testInsertOptionUpdate() throws Exception
{
    final User user = TestContext.get().getUser();
    final String dataClassName = "DataClassWithImportOption";

    List<GWTPropertyDescriptor> props = new ArrayList<>();
    props.add(new GWTPropertyDescriptor("prop", "string"));

    String longFieldName = "Field100 ABCDEFGHIJKLMNOPQRSTUVWXYZ%()=+-[]_|*`'\":;<>?!@#^AABBCCDDEEFFGGHHIIJJKKLLMMNNOOPPQQRRSSTTU)";
    props.add(new GWTPropertyDescriptor(longFieldName, "string"));

    ExpDataClass dataClass = ExperimentServiceImpl.get().createDataClass(c, user, dataClassName, null, props, emptyList(), null, null);
    List<Map<String, Object>> rowsToAdd = new ArrayList<>();
    rowsToAdd.add(CaseInsensitiveHashMap.of("name", "D-1", "prop", "a", longFieldName, "Very", "alias", "Much", "flag", "c100"));
    rowsToAdd.add(CaseInsensitiveHashMap.of("name", "D-1-d", "prop", "c", longFieldName, "Long", "alias", "Extended", "flag", "c200"));
    rowsToAdd.add(CaseInsensitiveHashMap.of("name", "D-2", "prop", "b", longFieldName, "Field", "alias", "Paddock"));

    TableInfo table = getDataClassTable(dataClassName);
    QueryUpdateService qus = table.getUpdateService();
    assertNotNull(qus);

    String longFieldAlias = table.getColumn(longFieldName).getAlias().getId();
    assertFalse("Unexpected long field alias", longFieldName.equalsIgnoreCase(longFieldAlias));

    DataIteratorContext context = new DataIteratorContext();
    context.setInsertOption(QueryUpdateService.InsertOption.IMPORT);

    var count = qus.loadRows(user, c, MapDataIterator.of(rowsToAdd), context, null);
    assertFalse(context.getErrors().hasErrors());
    assertEquals(3, count);

    TableInfo dataInputTInfo = ((ExperimentServiceImpl) ExperimentService.get()).getTinfoExperimentRunDataInputs();
    SimpleFilter filter = SimpleFilter.createContainerFilter(c);
    TableSelector ts = new TableSelector(dataInputTInfo, filter, null);
    int inputCount =  (int) ts.getRowCount();

    // update regular properties as well as datainputs
    List<Map<String, Object>> rowsToUpdate = new ArrayList<>();
    rowsToUpdate.add(CaseInsensitiveHashMap.of("name", "D-1", "prop", "a1", "DataInputs/DataClassWithImportOption", null, "alias", "A lot"));
    rowsToUpdate.add(CaseInsensitiveHashMap.of("name", "D-1-d", "prop", "c1", "DataInputs/DataClassWithImportOption", "D-1"));
    rowsToUpdate.add(CaseInsensitiveHashMap.of("name", "D-2", "prop", "b1", "DataInputs/DataClassWithImportOption", null, "alias", "Grassland"));

    context = new DataIteratorContext();
    context.setInsertOption(QueryUpdateService.InsertOption.UPDATE);
    count = qus.loadRows(user, c, MapDataIterator.of(rowsToUpdate), context, null);

    assertFalse(context.getErrors().hasErrors());
    assertEquals(3, count);

    // Issue 53168: circular lineage not allowed
    rowsToUpdate.clear();
    rowsToUpdate.add(CaseInsensitiveHashMap.of("rowId", dataClass.getData(c, "D-1").getRowId(), "DataInputs/DataClassWithImportOption", "D-1-d"));
    try
    {
        qus.updateRows(user, c, rowsToUpdate, null, new BatchValidationException(), null, null);
        fail("Expected to throw exception");
    }
    catch (Exception e)
    {
        assertThat(e.getMessage(), containsString("'D-1-d' is child of the current source 'D-1'. Circular relationships are not allowed."));
    }

    Set<String> columnNames = new HashSet<>();
    columnNames.add("rowId");
    columnNames.add("lsid");
    columnNames.add("Name");
    columnNames.add("prop");
    columnNames.add(longFieldName);
    columnNames.add("alias");
    columnNames.add("flag");

    ts = new TableSelector(table, columnNames, null, new Sort("Name"));
    ts.setForDisplay(true);
    String aliasAlias = "alias";
    String flagAlias = "flag$";

    List<Map<String,Object>> rows = Arrays.asList(ts.getMapArray());

    assertEquals("D-1", rows.get(0).get("Name"));
    assertEquals("D-1-d", rows.get(1).get("Name"));
    assertEquals("D-2", rows.get(2).get("Name"));
    long d2RowId = asLong(rows.get(2).get("RowId"));

    // prop
    assertEquals("a1", rows.get(0).get("prop"));
    assertEquals("c1", rows.get(1).get("prop"));
    assertEquals("b1", rows.get(2).get("prop"));

    // long field
    assertEquals("Very", rows.get(0).get(longFieldAlias));
    assertEquals("Long", rows.get(1).get(longFieldAlias));
    assertEquals("Field", rows.get(2).get(longFieldAlias));

    // alias
    assertEquals("A lot", rows.get(0).get(aliasAlias));
    assertNull(rows.get(1).get(aliasAlias));
    assertEquals("Grassland", rows.get(2).get(aliasAlias));

    // flag
    assertEquals("c100", rows.get(0).get(flagAlias));
    assertEquals("c200", rows.get(1).get(flagAlias));
    assertNull(rows.get(2).get(flagAlias));

    ts = new TableSelector(dataInputTInfo, filter, null);
    int newInputCount = (int) ts.getRowCount();
    assertEquals(inputCount + 1, newInputCount);

    List<Map<String, Object>> rowsToMerge = new ArrayList<>();
    rowsToMerge.add(CaseInsensitiveHashMap.of("name", "D-2", "prop", "gene", longFieldName, "Pasture", "alias", "Exceedingly", "flag", "cOne"));
    rowsToMerge.add(CaseInsensitiveHashMap.of("name", "D-22", "prop", null, longFieldName, "Goal", "alias", "Immensely", "flag", "cEight"));

    context = new DataIteratorContext();
    context.setInsertOption(QueryUpdateService.InsertOption.MERGE);
    count = qus.loadRows(user, c, MapDataIterator.of(rowsToMerge), context, null);

    assertFalse(context.getErrors().hasErrors());
    assertEquals(2, count);

    ts = new TableSelector(table, columnNames, null, new Sort("Name"));
    ts.setForDisplay(true);

    rows = Arrays.asList(ts.getMapArray());
    assertEquals(4, rows.size());

    assertEquals("D-1", rows.get(0).get("Name"));
    assertEquals("D-1-d", rows.get(1).get("Name"));
    assertEquals("D-2", rows.get(2).get("Name"));
    assertEquals("D-22", rows.get(3).get("Name"));

    // verify merge retained existing row
    assertEquals(d2RowId, asLong(rows.get(2).get("RowId")).longValue());

    // prop
    assertEquals("a1", rows.get(0).get("prop"));
    assertEquals("c1", rows.get(1).get("prop"));
    assertEquals("gene", rows.get(2).get("prop"));
    assertNull(rows.get(3).get("prop"));

    // long field
    assertEquals("Very", rows.get(0).get(longFieldAlias));
    assertEquals("Long", rows.get(1).get(longFieldAlias));
    assertEquals("Pasture", rows.get(2).get(longFieldAlias));
    assertEquals("Goal", rows.get(3).get(longFieldAlias));

    // alias
    assertEquals("A lot", rows.get(0).get(aliasAlias));
    assertNull(rows.get(1).get(aliasAlias));
    assertEquals("Exceedingly", rows.get(2).get(aliasAlias));
    assertEquals("Immensely", rows.get(3).get(aliasAlias));

    // flag
    assertEquals("c100", rows.get(0).get(flagAlias));
    assertEquals("c200", rows.get(1).get(flagAlias));
    assertEquals("cOne", rows.get(2).get(flagAlias));
    assertEquals("cEight", rows.get(3).get(flagAlias));
}

private @NotNull TableInfo getDataClassTable(String dataClassName)
{
    UserSchema schema = QueryService.get().getUserSchema(TestContext.get().getUser(), c, ExpSchema.SCHEMA_EXP_DATA);
    return schema.getTableOrThrow(dataClassName);
}

@Test // Issue 52886
public void testUpdateAuditForLongField() throws Exception
{
    User user = TestContext.get().getUser();

    String dataClassName = "DataClassesWithLongFields";
    String fieldName = "longField " + StringUtils.repeat("AB CD", 20);

    ExpDataClassImpl dataClass = ExperimentServiceImpl.get().createDataClass(c, user, dataClassName, null,
            List.of(new GWTPropertyDescriptor(fieldName, "string")), emptyList(), null, null);
    assertNotNull(dataClass);

    List<Map<String, Object>> rowsToAdd = new ArrayList<>();
    rowsToAdd.add(CaseInsensitiveHashMap.of("name", "A-1", "prop", "a", fieldName, "Initial"));
    TableInfo table = getDataClassTable(dataClassName);
    QueryUpdateService qus = table.getUpdateService();
    assertNotNull(qus);
    DataIteratorContext context = new DataIteratorContext();
    context.setInsertOption(QueryUpdateService.InsertOption.IMPORT);
    context.getConfigParameters().put(DetailedAuditLogDataIterator.AuditConfigs.AuditBehavior, AuditBehaviorType.DETAILED);
    var count = qus.loadRows(user, c, MapDataIterator.of(rowsToAdd), context, null);
    assertFalse("Unexpected errors from import", context.getErrors().hasErrors());
    assertEquals("Number of rows inserted not as expected", 1, count);
    for (Map<String, Object> row : rowsToAdd)
    {
        TableSelector ts = new TableSelector(table, Collections.singleton("RowId"), new SimpleFilter(FieldKey.fromParts("Name"), row.get("name")), null);
        int rowId;
        try (var results = ts.getResults())
        {
            if (results.next())
            {
                rowId = results.getInt(FieldKey.fromParts("RowId"));
                row.put("RowId", rowId); // intentional side-effect to help in retrieving update events as well

                SimpleFilter filter = SimpleFilter.createContainerFilter(c);
                filter.addCondition(FieldKey.fromParts("rowPk"), String.valueOf(rowId));
                filter.addCondition(FieldKey.fromParts("queryName"), dataClassName);
                List<AuditTypeEvent> events = AuditLogService.get().getAuditEvents(c, user, "QueryUpdateAuditEvent", filter, null);
                assertEquals("Number of audit events not as expected", 1, events.size());
            }
        }
    }

    List<Map<String, Object>> rowsToUpdate = new ArrayList<>();
    rowsToUpdate.add(CaseInsensitiveHashMap.of("name", "A-1", fieldName, "Updated"));
    context = new DataIteratorContext();
    context.setInsertOption(QueryUpdateService.InsertOption.UPDATE);
    context.getConfigParameters().put(DetailedAuditLogDataIterator.AuditConfigs.AuditBehavior, AuditBehaviorType.DETAILED);
    count = qus.loadRows(user, c, MapDataIterator.of(rowsToUpdate), context, null);
    assertFalse("Unexpected errors from update", context.getErrors().hasErrors());
    assertEquals("Number of rows updated not as expected", 1, count);

    for (Map<String, Object> row : rowsToAdd)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("rowPk"), String.valueOf(row.get("rowId")));
        filter.addCondition(FieldKey.fromParts("queryName"), dataClassName);
        List<AuditTypeEvent> events = AuditLogService.get().getAuditEvents(c, user, "QueryUpdateAuditEvent", filter, new Sort("-RowId"));
        assertEquals("Number of audit events not as expected", 2, events.size()); // should have one for insert and one for update
        String oldRecordMap = ((DetailedAuditTypeEvent) events.getFirst()).getOldRecordMap();
        assertTrue("Old record map (" + oldRecordMap + ") does not contain expected field", oldRecordMap.contains(encodeURIComponent(fieldName) + "=Initial"));
    }
}
%>

/*
 * Copyright (c) 2016-2019 LabKey Corporation
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
<%@ page import="org.junit.After" %>
<%@ page import="org.junit.Before" %>


<%@ page import="org.junit.Test" %>
<%@ page import="org.labkey.api.audit.AuditLogService" %>
<%@ page import="org.labkey.api.audit.SampleTimelineAuditEvent" %>
<%@ page import="org.labkey.api.collections.ArrayListMap" %>
<%@ page import="org.labkey.api.collections.CaseInsensitiveHashMap" %>
<%@ page import="org.labkey.api.data.CompareType" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.data.DbScope" %>
<%@ page import="org.labkey.api.data.JdbcType" %>
<%@ page import="org.labkey.api.data.SQLFragment" %>
<%@ page import="org.labkey.api.data.SimpleFilter" %>
<%@ page import="org.labkey.api.data.Sort" %>
<%@ page import="org.labkey.api.data.SqlSelector" %>
<%@ page import="org.labkey.api.data.TableInfo" %>
<%@ page import="org.labkey.api.data.TableSelector" %>
<%@ page import="org.labkey.api.dataiterator.DetailedAuditLogDataIterator" %>
<%@ page import="org.labkey.api.dataiterator.ListofMapsDataIterator" %>
<%@ page import="org.labkey.api.exp.ExperimentException" %>
<%@ page import="org.labkey.api.exp.Lsid" %>
<%@ page import="org.labkey.api.exp.OntologyManager" %>
<%@ page import="org.labkey.api.exp.PropertyDescriptor" %>
<%@ page import="org.labkey.api.exp.api.ExpLineage" %>
<%@ page import="org.labkey.api.exp.api.ExpLineageOptions" %>
<%@ page import="org.labkey.api.exp.api.ExpMaterial" %>
<%@ page import="org.labkey.api.exp.api.ExpRun" %>
<%@ page import="org.labkey.api.exp.api.ExpSampleType" %>
<%@ page import="org.labkey.api.exp.api.ExperimentService" %>
<%@ page import="org.labkey.api.exp.api.SampleTypeService" %>
<%@ page import="org.labkey.api.exp.property.Domain" %>
<%@ page import="org.labkey.api.exp.query.ExpSchema" %>
<%@ page import="org.labkey.api.gwt.client.AuditBehaviorType" %>
<%@ page import="org.labkey.api.gwt.client.model.GWTPropertyDescriptor" %>
<%@ page import="org.labkey.api.query.BatchValidationException" %>
<%@ page import="org.labkey.api.query.DefaultSchema" %>

<%@ page import="org.labkey.api.query.FieldKey" %>
<%@ page import="org.labkey.api.query.QuerySchema" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.query.QueryUpdateService" %>
<%@ page import="org.labkey.api.query.SchemaKey" %>
<%@ page import="org.labkey.api.query.UserSchema" %>
<%@ page import="org.labkey.api.reader.DataLoader" %>

<%@ page import="static org.hamcrest.CoreMatchers.hasItems" %>
<%@ page import="static org.junit.Assert.*" %>
<%@ page import="org.labkey.api.reader.TabLoader" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.DeletePermission" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.TestContext" %>
<%@ page import="org.labkey.experiment.api.ExpProvisionedTableTestHelper" %>
<%@ page import="org.labkey.experiment.api.ExpSampleTypeImpl" %>
<%@ page import="org.labkey.experiment.api.ExperimentServiceImpl" %>
<%@ page import="org.labkey.experiment.api.SampleTypeServiceImpl" %>
<%@ page import="java.io.StringBufferInputStream" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.sql.SQLException" %>
<%@ page import="org.labkey.api.query.QueryUpdateServiceException" %>
<%@ page import="org.labkey.api.query.InvalidKeyException" %>
<%@ page import="static org.hamcrest.CoreMatchers.containsString" %>
<%@ page import="org.labkey.api.exp.query.SamplesSchema" %>

<%@ page extends="org.labkey.api.jsp.JspTest.BVT" %>

<%!
/**
 * User: kevink
 * Date: 11/24/16
 */
private static final String PROJECT_NAME = "_testSampleType";
private final ExpProvisionedTableTestHelper helper = new ExpProvisionedTableTestHelper();

private Container c;

@Before
public void setUp()
{
    // NOTE: We need to use a project to create the DataClass so we can insert rows into sub-folders
    c = ContainerManager.getForPath(PROJECT_NAME);
    if (c != null)
        ContainerManager.deleteAll(c, TestContext.get().getUser());
    c = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT_NAME);
}

@After
public void tearDown()
{
    ContainerManager.deleteAll(c, TestContext.get().getUser());
}

private void assertExpectedName(ExpSampleType st, String expectedName)
{
    ExpMaterial s = st.getSample(c, expectedName);
    assertNotNull("Expected to create sample with name '" + expectedName + "'", s);
    assertEquals(expectedName, s.getName());
}

// validate name is not null
@Test
public void nameNotNull() throws Exception
{
    final User user = TestContext.get().getUser();

    try
    {
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("name", "string"));

        final ExpSampleType st = SampleTypeService.get().createSampleType(c, user,
                null, null, props, Collections.emptyList(),
                -1, -1, -1, -1, null, null);
    }
    catch (ExperimentException ee)
    {
        assertEquals("SampleType name is required", ee.getMessage());
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
        props.add(new GWTPropertyDescriptor("name", "string"));

        String name = StringUtils.repeat("a", 1000);

        final ExpSampleType st = SampleTypeService.get().createSampleType(c, user,
                name, null, props, Collections.emptyList(),
                -1, -1, -1, -1, null, null);
    }
    catch (ExperimentException ee)
    {
        assertEquals("SampleType name may not exceed 100 characters.", ee.getMessage());
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
        props.add(new GWTPropertyDescriptor("name", "string"));
        props.add(new GWTPropertyDescriptor("prop", "string"));
        props.add(new GWTPropertyDescriptor("age", "int"));

        String nameExpression = StringUtils.repeat("a", 1000);

        final ExpSampleType st = SampleTypeService.get().createSampleType(c, user,
                "Samples", null, props, Collections.emptyList(),
                -1, -1, -1, -1, nameExpression, null);
    }
    catch (ExperimentException ee)
    {
        assertEquals("Name expression may not exceed 500 characters.", ee.getMessage());
    }
}

// idCols all null, nameExpression null, no 'name' property -- fail
@Test
public void idColsUnset_nameExpressionNull_noNameProperty() throws Exception
{
    final User user = TestContext.get().getUser();

    try
    {
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("notName", "string"));
        props.add(new GWTPropertyDescriptor("age", "int"));

        final ExpSampleType st = SampleTypeService.get().createSampleType(c, user,
                "Samples", null, props, Collections.emptyList(),
                -1, -1, -1, -1, null, null);
        fail("Expected exception");
    }
    catch (ExperimentException ee)
    {
        assertEquals("Either a 'Name' property or an index for idCol1 is required", ee.getMessage());
    }
}

// idCols all null, nameExpression null, has 'name' property -- ok
@Test
public void idColsUnset_nameExpressionNull_hasNameProperty() throws Exception
{
    final User user = TestContext.get().getUser();

    List<GWTPropertyDescriptor> props = new ArrayList<>();
    props.add(new GWTPropertyDescriptor("name", "string"));
    props.add(new GWTPropertyDescriptor("age", "int"));

    final ExpSampleType st = SampleTypeService.get().createSampleType(c, user,
            "Samples", null, props, Collections.emptyList(),
            -1, -1, -1, -1, null, null);

    ExpMaterial sample = st.getSample(c, "bob");
    assertNull(sample);

    UserSchema schema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts("Samples"));
    TableInfo table = schema.getTable("Samples");
    QueryUpdateService svc = table.getUpdateService();

    List<Map<String, Object>> rows = new ArrayList<>();
    rows.add(CaseInsensitiveHashMap.of("name", "bob", "age", 10));

    BatchValidationException errors = new BatchValidationException();
    svc.insertRows(user, c, rows, errors, null, null);
    if (errors.hasErrors())
        throw errors;

    assertExpectedName(st, "bob");
}

// idCols all null, nameExpression not null, has 'name' property -- ok
@Test
public void idColsUnset_nameExpression_hasNameProperty() throws Exception
{
    final User user = TestContext.get().getUser();

    List<GWTPropertyDescriptor> props = new ArrayList<>();
    props.add(new GWTPropertyDescriptor("name", "string"));
    props.add(new GWTPropertyDescriptor("prop", "string"));
    props.add(new GWTPropertyDescriptor("age", "int"));

    final String nameExpression = "S-${prop}.${age}";

    final ExpSampleType st = SampleTypeService.get().createSampleType(c, user,
            "Samples", null, props, Collections.emptyList(),
            -1, -1, -1, -1, nameExpression, null);
}

// idCols not null, nameExpression null, no 'name' property -- ok
@Test
public void idColsSet_nameExpressionNull_noNameProperty() throws Exception
{
    final User user = TestContext.get().getUser();

    List<GWTPropertyDescriptor> props = new ArrayList<>();
    props.add(new GWTPropertyDescriptor("prop", "string"));
    props.add(new GWTPropertyDescriptor("age", "int"));

    final ExpSampleType st = SampleTypeService.get().createSampleType(c, user,
            "Samples", null, props, Collections.emptyList(),
            0, 1, -1, -1, null, null);

    final String expectedName1 = "bob";
    final String expectedName2 = "red-11";
    ExpMaterial sample1 = st.getSample(c, expectedName1);
    assertNull(sample1);
    ExpMaterial sample2 = st.getSample(c, expectedName2);
    assertNull(sample2);

    UserSchema schema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts("Samples"));
    TableInfo table = schema.getTable("Samples");
    QueryUpdateService svc = table.getUpdateService();

    List<Map<String, Object>> rows = new ArrayList<>();
    rows.add(CaseInsensitiveHashMap.of("name", "bob", "prop", "blue", "age", 10));
    rows.add(CaseInsensitiveHashMap.of("prop", "red", "age", 11));

    BatchValidationException errors = new BatchValidationException();
    svc.insertRows(user, c, rows, errors, null, null);
    if (errors.hasErrors())
        throw errors;

    assertExpectedName(st, expectedName1);
    assertExpectedName(st, expectedName2);
}

// idCols not null, nameExpression null, 'name' property (not used) -- fail **
@Test
public void idColsSet_nameExpressionNull_hasUnusedNameProperty() throws Exception
{
    final User user = TestContext.get().getUser();

    try
    {
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("name", "string"));
        props.add(new GWTPropertyDescriptor("age", "int"));

        final ExpSampleType st = SampleTypeService.get().createSampleType(c, user,
                "Samples", null, props, Collections.emptyList(),
                1, -1, -1, -1, null, null);
        fail("Expected exception");
    }
    catch (ExperimentException ee)
    {
        assertEquals("Either a 'Name' property or idCols can be used, but not both", ee.getMessage());
    }
}

// idCols not null, nameExpression null, 'name' property (used) -- ok
@Test
public void idColsSet_nameExpressionNull_hasNameProperty() throws Exception
{
    final User user = TestContext.get().getUser();

    List<GWTPropertyDescriptor> props = new ArrayList<>();
    props.add(new GWTPropertyDescriptor("name", "string"));
    props.add(new GWTPropertyDescriptor("prop", "string"));
    props.add(new GWTPropertyDescriptor("age", "int"));

    final ExpSampleType st = SampleTypeService.get().createSampleType(c, user,
            "Samples", null, props, Collections.emptyList(),
            0, -1, -1, -1, null, null);

    final String expectedName1 = "bob";
    ExpMaterial sample1 = st.getSample(c, expectedName1);
    assertNull(sample1);

    UserSchema schema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts("Samples"));
    TableInfo table = schema.getTable("Samples");
    QueryUpdateService svc = table.getUpdateService();

    List<Map<String, Object>> rows = new ArrayList<>();
    rows.add(CaseInsensitiveHashMap.of("name", "bob", "prop", "blue", "age", 10));

    BatchValidationException errors = new BatchValidationException();
    svc.insertRows(user, c, rows, errors, null, null);
    if (errors.hasErrors())
        throw errors;

    assertExpectedName(st, expectedName1);

    // try to insert without a value for 'name' property results in an error
    rows = new ArrayList<>();
    rows.add(CaseInsensitiveHashMap.of("prop", "red", "age", 11));

    errors = new BatchValidationException();
    svc.insertRows(user, c, rows, errors, null, null);
    assertTrue(errors.hasErrors());
    assertTrue(errors.getMessage().contains("Name is required for sample on row 1"));
}

// idCols not null, nameExpression not null, 'name' property (not used) -- fail
@Test
public void idColsSet_nameExpression_hasUnusedNameProperty() throws Exception
{
    final User user = TestContext.get().getUser();

    try
    {
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("name", "string"));
        props.add(new GWTPropertyDescriptor("age", "int"));

        final ExpSampleType st = SampleTypeService.get().createSampleType(c, user,
                "Samples", null, props, Collections.emptyList(),
                1, -1, -1, -1, "S-${name}.${age}", null);
        fail("Expected exception");
    }
    catch (ExperimentException ee)
    {
        assertEquals("Name expression cannot be used with id columns", ee.getMessage());
    }
}

@Test
public void testNameExpression() throws Exception
{
    final User user = TestContext.get().getUser();

    // setup
    List<GWTPropertyDescriptor> props = new ArrayList<>();
    props.add(new GWTPropertyDescriptor("name", "string"));
    props.add(new GWTPropertyDescriptor("prop", "string"));
    props.add(new GWTPropertyDescriptor("age", "int"));

    final String nameExpression = "S-${prop}.${age}.${genId:number('000')}";

    final ExpSampleType st = SampleTypeService.get().createSampleType(c, user,
            "Samples", null, props, Collections.emptyList(),
            -1, -1, -1, -1, nameExpression, null);

    final String expectedName1 = "bob";
    final String expectedName2 = "S-red.11.002";
    final String expectedName3 = "S-red.11.003";
    assertNull(st.getSample(c, expectedName1));
    assertNull(st.getSample(c, expectedName2));
    assertNull(st.getSample(c, expectedName3));

    UserSchema schema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts("Samples"));
    TableInfo table = schema.getTable("Samples");
    QueryUpdateService svc = table.getUpdateService();

    List<Map<String, Object>> rows = new ArrayList<>();
    rows.add(CaseInsensitiveHashMap.of("name", "bob", "prop", "blue", "age", 10));
    rows.add(CaseInsensitiveHashMap.of("prop", "red", "age", 11));
    rows.add(CaseInsensitiveHashMap.of("prop", "red", "age", 11));

    BatchValidationException errors = new BatchValidationException();
    List<Map<String, Object>> ret = svc.insertRows(user, c, rows, errors, null, null);
    if (errors.hasErrors())
        throw errors;

    assertEquals(3, ret.size());

    assertEquals(1, ret.get(0).get("genId"));
    assertEquals(expectedName1, ret.get(0).get("name"));
    assertExpectedName(st, expectedName1);

    assertEquals(2, ret.get(1).get("genId"));
    assertEquals(expectedName2, ret.get(1).get("name"));
    assertExpectedName(st, expectedName2);

    assertEquals(3, ret.get(2).get("genId"));
    assertEquals(expectedName3, ret.get(2).get("name"));
    assertExpectedName(st, expectedName3);
}

@Test
public void testAliases() throws Exception
{
    final User user = TestContext.get().getUser();

    // setup
    List<GWTPropertyDescriptor> props = new ArrayList<>();
    props.add(new GWTPropertyDescriptor("name", "string"));
    props.add(new GWTPropertyDescriptor("age", "int"));

    final ExpSampleType st = SampleTypeService.get().createSampleType(c, user,
            "Samples", null, props, Collections.emptyList(),
            -1, -1, -1, -1, null, null);

    List<Map<String, Object>> rows = new ArrayList<>();
    Map<String, Object> row = new CaseInsensitiveHashMap<>();
    row.put("name", "boo");
    row.put("age", 20);
    row.put("alias", "a,b,c");
    rows.add(row);


    UserSchema schema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts("Samples"));
    TableInfo table = schema.getTable("Samples");
    QueryUpdateService svc = table.getUpdateService();

    List<Map<String, Object>> insertedRows = null;
    try (DbScope.Transaction tx = table.getSchema().getScope().beginTransaction())
    {
        BatchValidationException errors = new BatchValidationException();
        QueryUpdateService qus = table.getUpdateService();

        insertedRows = qus.insertRows(user, c, rows, errors, null, null);
        if (errors.hasErrors())
            throw errors;
        tx.commit();
    }

    ExpMaterial m = st.getSample(c, "boo");
    Collection<String> aliases = m.getAliases();
    assertThat(aliases, hasItems("a", "b", "c"));
}


// Issue 33682: Calling insertRows on SampleType with empty values will not insert new samples
@Test
public void testBlankRows() throws Exception
{
    final User user = TestContext.get().getUser();

    // setup
    List<GWTPropertyDescriptor> props = new ArrayList<>();
    props.add(new GWTPropertyDescriptor("name", "string"));
    props.add(new GWTPropertyDescriptor("age", "int"));

    final String nameExpression = "S-${now:date}-${dailySampleCount}";

    final ExpSampleTypeImpl st = SampleTypeServiceImpl.get().createSampleType(c, user,
            "Samples", null, props, Collections.emptyList(),
            -1, -1, -1, -1, nameExpression, null);

    List<? extends ExpMaterial> allSamples = st.getSamples(c);
    assertTrue("Expected no samples", allSamples.isEmpty());

    //
    // insert via insertRows -- blank rows should be preserved
    //

    UserSchema schema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts("Samples"));
    TableInfo table = schema.getTable("Samples");
    QueryUpdateService svc = table.getUpdateService();
    assertNotNull(svc);

    // insert 3 rows with no values
    List<Map<String, Object>> rows = new ArrayList<>();
    rows.add(new CaseInsensitiveHashMap<>());
    rows.add(new CaseInsensitiveHashMap<>());
    rows.add(new CaseInsensitiveHashMap<>());

    BatchValidationException errors = new BatchValidationException();
    List<Map<String, Object>> inserted = svc.insertRows(user, c, rows, errors, null, null);
    if (errors.hasErrors())
        throw errors;

    assertEquals("Expected to generate 3 sample rows, got: " + inserted, 3, inserted.size());

    String name1 = (String)inserted.get(0).get("name");
    assertTrue("Expected generated sample name to start with 'S-', got: " + name1, name1 != null && name1.startsWith("S-"));

    allSamples = st.getSamples(c);
    assertEquals("Expected 3 total samples", 3, allSamples.size());

    //
    // insert as if we pasted a tsv in the "upload samples" page -- blank rows should be skipped
    //

    // data has three lines, one blank.  expect to insert only two samples
    String dataTxt =
            "age\n" +
            "20\n" +
            "\n" +
            "30\n";
    DataLoader tsv = DataLoader.get().createLoader("upload.txt", "text/plain", new StringBufferInputStream(dataTxt), true, c, TabLoader.TSV_FILE_TYPE);
    var dataMaps = tsv.load();

    errors = new BatchValidationException();
    var insertedRows = svc.insertRows(user, c, dataMaps, errors, null, null);
    if (errors.hasErrors())
        throw errors;

    assertEquals("Expected to insert 2 samples, got: " + insertedRows.size(), 2, insertedRows.size());

    ExpMaterial material1 = ExperimentService.get().getExpMaterial((Integer)insertedRows.get(0).get("rowid"));
    Map<PropertyDescriptor, Object> map = material1.getPropertyValues();
    assertEquals("Expected to only have 'age' property, got: " + map, 1, map.size());

    Integer age1 = (Integer)material1.getPropertyValues().values().iterator().next();
    assertNotNull(age1);
    assertEquals("Expected to insert age of 20, got: " + age1, 20, age1.intValue());

    ExpMaterial material2 = ExperimentService.get().getExpMaterial((Integer)insertedRows.get(1).get("rowid"));
    Integer age2 = (Integer)material2.getPropertyValues().values().iterator().next();
    assertNotNull(age2);
    assertEquals("Expected to insert age of 30, got: " + age2, 30, age2.intValue());

    allSamples = st.getSamples(c);
    assertEquals("Expected 5 total samples", 5, allSamples.size());

    // how about an update
    var updated = new CaseInsensitiveHashMap<Object>();
    updated.put("name", material1.getName());
    updated.put("lsid", material1.getLSID());
    updated.put("age", age1 + 1);
    svc.updateRows(user, c, Collections.singletonList(updated), null, null, null);
    var result = new TableSelector(table, TableSelector.ALL_COLUMNS, new SimpleFilter("lsid", material1.getLSID()), null).getMap();
    assertEquals(21, ((Integer)result.get("age")).intValue());

    // and a delete
    svc.deleteRows(user, c, Collections.singletonList(updated), null, null);
    allSamples = st.getSamples(c);
    assertEquals("Expected 5 total samples", 4, allSamples.size());
}

// Issue 40109: Assure we can change one parent during merge without affecting the others
@Test
public void testUpdateSomeParents() throws Exception
{
    final User user = TestContext.get().getUser();

    // setup
    List<GWTPropertyDescriptor> props = new ArrayList<>();
    props.add(new GWTPropertyDescriptor("name", "string"));
    props.add(new GWTPropertyDescriptor("age", "int"));
    final ExpSampleTypeImpl childType = SampleTypeServiceImpl.get().createSampleType(c, user,
            "ChildSamples", null, props, Collections.emptyList(),
            -1, -1, -1, -1, null, null);

    final ExpSampleTypeImpl parent1Type = SampleTypeServiceImpl.get().createSampleType(c, user,
            "Parent1Samples", null, props, Collections.emptyList(),
            -1, -1, -1, -1, null, null);

    final ExpSampleTypeImpl parent2Type = SampleTypeServiceImpl.get().createSampleType(c, user,
            "Parent2Samples", null, props, Collections.emptyList(),
            -1, -1, -1, -1, null, null);


    UserSchema schema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts("Samples"));
    List<Map<String, Object>> rows = new ArrayList<>();


    // add first parents
    TableInfo table = schema.getTable("Parent1Samples", null, true, false);
    QueryUpdateService svc = table.getUpdateService();

    BatchValidationException errors = new BatchValidationException();
    rows.add(CaseInsensitiveHashMap.of("name", "P1-1"));
    rows.add(CaseInsensitiveHashMap.of("name", "P1-2"));
    rows.add(CaseInsensitiveHashMap.of("name", "P1-3"));
    List<Map<String, Object>> inserted = svc.insertRows(user, c, rows, errors, null, null);
    assertFalse(errors.hasErrors());
    assertEquals("Number of parent1 samples inserted not as expected", 3, inserted.size());

    // add second parents
    table = schema.getTable("Parent2Samples", null, true, false);
    svc = table.getUpdateService();

    rows.clear();
    rows.add(CaseInsensitiveHashMap.of("name", "P2-1"));
    rows.add(CaseInsensitiveHashMap.of("name", "P2-2"));
    rows.add(CaseInsensitiveHashMap.of("name", "P2-3"));
    inserted = svc.insertRows(user, c, rows, errors, null, null);
    assertFalse(errors.hasErrors());
    assertEquals("Number of parent2 samples inserted not as expected", 3, inserted.size());

    // add child samples
    table = schema.getTable("ChildSamples", null, true, false);
    svc = table.getUpdateService();

    rows.clear();
    rows.add(CaseInsensitiveHashMap.of("name", "C1",  "MaterialInputs/Parent1Samples", "P1-1,P1-2", "MaterialInputs/Parent2Samples", "P2-1"));
    rows.add(CaseInsensitiveHashMap.of("name", "C2", "MaterialInputs/Parent1Samples", "P1-1", "MaterialInputs/Parent2Samples", "P2-1"));
    rows.add(CaseInsensitiveHashMap.of("name", "C3", "age", 42, "MaterialInputs/Parent1Samples", "P1-1", "MaterialInputs/Parent2Samples", "P2-1, P2-2"));
    rows.add(CaseInsensitiveHashMap.of("name", "C4", "MaterialInputs/Parent1Samples", "P1-2", "MaterialInputs/Parent2Samples", "P2-2"));
    inserted = svc.insertRows(user, c, rows, errors, null, null);
    assertFalse(errors.hasErrors());
    assertEquals("Number of child samples inserted not as expected", 4, inserted.size());

    ExpMaterial P11 = parent1Type.getSample(c, "P1-1");
    ExpMaterial P12 = parent1Type.getSample(c, "P1-2");
    ExpMaterial P21 = parent2Type.getSample(c, "P2-1");
    ExpMaterial P22 = parent2Type.getSample(c, "P2-2");


    ExpMaterial C1 = childType.getSample(c, "C1");
    ExpMaterial C2 = childType.getSample(c, "C2");
    ExpMaterial C3 = childType.getSample(c, "C3");
    ExpMaterial C4 = childType.getSample(c, "C4");

    ExpLineageOptions opts = new ExpLineageOptions();
    opts.setChildren(false);
    opts.setParents(true);
    opts.setDepth(2);

    // now update the children with various types of modifications to the parentage
    rows.clear();
    rows.add(CaseInsensitiveHashMap.of("name", "C1", "MaterialInputs/Parent1Samples", "P1-1")); // change one parent but not the other
    rows.add(CaseInsensitiveHashMap.of("name", "C4", "MaterialInputs/Parent1Samples", null)); // remove one parent but not the other

    svc.mergeRows(user, c, new ListofMapsDataIterator(rows.get(0).keySet(),rows), errors, null, null);
    assertFalse(errors.hasErrors());

    ExpLineage lineage = ExperimentService.get().getLineage(c, user, C1, opts);
    assertTrue("Expected 'C1' to be derived from 'P1-1'", lineage.getMaterials().contains(P11));
    assertFalse("Expected 'C1' to no longer be derived from 'P1-2'", lineage.getMaterials().contains(P12));
    assertTrue("Expected 'C1' to still be derived from 'P2-1'", lineage.getMaterials().contains(P21));

    lineage = ExperimentService.get().getLineage(c, user, C4, opts);
    assertFalse("Expected 'C4' to not be derived from 'P1-2'", lineage.getMaterials().contains(P12));
    assertTrue("Expected 'C4' to still be derived from 'P2-2'", lineage.getMaterials().contains(P22));


    rows.clear();
    rows.add(CaseInsensitiveHashMap.of("name", "C4", "MaterialInputs/Parent1Samples", "P1-1", "MaterialInputs/Parent2Samples", "P2-1")); // change both parents
    rows.add(CaseInsensitiveHashMap.of("name", "C2", "MaterialInputs/Parent1Samples", "", "MaterialInputs/Parent2Samples", null)); // remove both parents

    svc.mergeRows(user, c, new ListofMapsDataIterator(rows.get(0).keySet(),rows), errors, null, null);
    assertFalse(errors.hasErrors());

    lineage = ExperimentService.get().getLineage(c, user, C2, opts);
    assertTrue("Expected 'C2' to have no parents'", lineage.getMaterials().isEmpty());

    lineage = ExperimentService.get().getLineage(c, user, C4, opts);
    assertEquals("Expected 'C4' to have two parents", 2, lineage.getMaterials().size());
    assertTrue("Expected 'C4' to be derived from 'P1-1'", lineage.getMaterials().contains(P11));
    assertTrue("Expected 'C4' to be derived from 'P2-1'", lineage.getMaterials().contains(P21));

}

// Issue 29060: Deriving with DataInputs and MaterialInputs on SampleType even when Parent col is set
@Test
public void testParentColAndDataInputDerivation() throws Exception
{
    final User user = TestContext.get().getUser();

    // setup
    List<GWTPropertyDescriptor> props = new ArrayList<>();
    props.add(new GWTPropertyDescriptor("name", "string"));
    props.add(new GWTPropertyDescriptor("data", "int"));
    props.add(new GWTPropertyDescriptor("parent", "string"));

    final ExpSampleTypeImpl st = SampleTypeServiceImpl.get().createSampleType(c, user,
            "Samples", null, props, Collections.emptyList(),
            0, -1, -1, 2, null, null);

    // insert and derive with both 'parent' column and 'DataInputs/Samples'
    UserSchema schema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts("Samples"));
    TableInfo table = schema.getTable("Samples");
    QueryUpdateService svc = table.getUpdateService();

    List<Map<String, Object>> rows = new ArrayList<>();
    rows.add(CaseInsensitiveHashMap.of("name", "A", "data", 10, "parent", null));
    rows.add(CaseInsensitiveHashMap.of("name", "B", "data", 11, "parent", "A"));
    rows.add(CaseInsensitiveHashMap.of("name", "C", "data", 12, "parent", null, "MaterialInputs/Samples", "B"));
    rows.add(CaseInsensitiveHashMap.of("name", "D", "data", 12, "parent", "B", "MaterialInputs/Samples", "C"));
    rows.add(CaseInsensitiveHashMap.of("name", "E", "data", 12, "parent", null, "MaterialInputs/Samples", "B,C"));
    rows.add(CaseInsensitiveHashMap.of("name", "F", "data", 12, "parent", null));

    // lineage graph:
    // A
    // B <- A
    // C <- B
    // D <- B,C
    // E <- B,C
    // F

    BatchValidationException errors = new BatchValidationException();
    List<Map<String, Object>> inserted = svc.insertRows(user, c, rows, errors, null, null);
    assertFalse(errors.hasErrors());
    assertEquals(6, inserted.size());

    // verify
    ExpLineageOptions opts = new ExpLineageOptions();
    opts.setChildren(false);
    opts.setParents(true);
    opts.setDepth(2);

    ExpMaterial A = st.getSample(c, "A");
    assertNotNull(A);
    ExpLineage lineage = ExperimentService.get().getLineage(c, user, A, opts);
    assertTrue(lineage.getMaterials().isEmpty());
    assertNull(A.getRunId());

    ExpMaterial B = st.getSample(c, "B");
    assertNotNull(B);
    lineage = ExperimentService.get().getLineage(c, user, B, opts);
    assertEquals(1, lineage.getMaterials().size());
    assertTrue("Expected 'B' to be derived from 'A'", lineage.getMaterials().contains(A));
    assertNotNull(B.getRunId());

    ExpMaterial C = st.getSample(c, "C");
    assertNotNull(C);
    lineage = ExperimentService.get().getLineage(c, user, C, opts);
    assertEquals(1, lineage.getMaterials().size());
    assertTrue("Expected 'C' to be derived from 'B'", lineage.getMaterials().contains(B));

    ExpMaterial D = st.getSample(c, "D");
    assertNotNull(D);
    lineage = ExperimentService.get().getLineage(c, user, D, opts);
    assertEquals(2, lineage.getMaterials().size());
    assertTrue("Expected 'D' to be derived from 'B'", lineage.getMaterials().contains(B));
    assertTrue("Expected 'D' to be derived from 'C'", lineage.getMaterials().contains(C));

    ExpMaterial E = st.getSample(c, "E");
    assertNotNull(E);
    lineage = ExperimentService.get().getLineage(c, user, E, opts);
    assertTrue("Expected 'E' to be the seed", lineage.getSeeds().contains(E));
    assertEquals(2, lineage.getMaterials().size());
    assertTrue("Expected 'E' to be derived from 'B'", lineage.getMaterials().contains(B));
    assertTrue("Expected 'E' to be derived from 'C'", lineage.getMaterials().contains(C));

    // verify that 'E' is derived in the same run as 'D' since they share the same parents
    assertEquals("Expected 'E' and 'D' to be derived in the same run since they share 'B' and 'C' as parents",
            E.getRowId(), E.getRowId());
    ExpRun derivationRun = E.getRun();

    assertTrue(derivationRun.getMaterialInputs().containsKey(B));
    assertTrue(derivationRun.getMaterialInputs().containsKey(C));
    assertTrue(derivationRun.getMaterialOutputs().contains(D));
    assertTrue(derivationRun.getMaterialOutputs().contains(E));

    assertEquals(1, lineage.getRuns().size());
    assertTrue("Expected lineage to include derivation run", lineage.getRuns().contains(derivationRun));


    // verify lineage using the derivation run as a seed
    lineage = ExperimentServiceImpl.get().getLineage(c, user, Set.of(derivationRun), new ExpLineageOptions(true, false, 1));
    assertTrue("Expected derivationRun to be the seed", lineage.getSeeds().contains(derivationRun));
    assertEquals(2, lineage.getMaterials().size());
    assertTrue("Expected 'B' to be input into derivationRun", lineage.getMaterials().contains(B));
    assertTrue("Expected 'C' to be input into derivationRun", lineage.getMaterials().contains(C));
    assertTrue("Expected no additional runs in lineage results", lineage.getRuns().isEmpty());


    // update 'D' to derive from 'B' and 'E'
    rows = new ArrayList<>();
    rows.add(CaseInsensitiveHashMap.of("rowId", D.getRowId(), "MaterialInputs/Samples", "B,E"));

    List<Map<String, Object>> updated = svc.updateRows(user, c, rows, null, null, null);
    assertEquals(1, updated.size());

    ExpMaterial D2 = st.getSample(c, "D");
    lineage = ExperimentService.get().getLineage(c, user, D2, opts);
    assertEquals(2, lineage.getMaterials().size());
    assertTrue("Expected 'D' to be derived from 'B'", lineage.getMaterials().contains(B));
    assertTrue("Expected 'D' to be derived from 'E'", lineage.getMaterials().contains(E));
    assertFalse("Expected 'D' to not be derived from 'C'", lineage.getMaterials().contains(C));

    // D is no longer attached as an output of derivationRun
    ExpRun derivationRun2 = D2.getRun();
    assertNotEquals("Updating 'D' lineage should create new derivation run", derivationRun.getRowId(), derivationRun2.getRowId());

    assertTrue(derivationRun2.getMaterialInputs().containsKey(B));
    assertTrue(derivationRun2.getMaterialInputs().containsKey(E));
    assertFalse(derivationRun2.getMaterialInputs().containsKey(C));
    assertTrue(derivationRun2.getMaterialOutputs().contains(D));
    assertFalse(derivationRun2.getMaterialOutputs().contains(E));

    ExpRun oldDerivationRun = ExperimentService.get().getExpRun(derivationRun.getRowId());
    assertEquals(oldDerivationRun.getRowId(), derivationRun.getRowId());

    assertTrue(oldDerivationRun.getMaterialInputs().containsKey(B));
    assertTrue(oldDerivationRun.getMaterialInputs().containsKey(C));
    assertFalse(oldDerivationRun.getMaterialInputs().containsKey(E));
    assertFalse(oldDerivationRun.getMaterialOutputs().contains(D));
    assertTrue(oldDerivationRun.getMaterialOutputs().contains(E));

}

@Test
public void testSampleTypeWithVocabularyProperties() throws Exception
{
    User user = TestContext.get().getUser();

    String sampleName = "SamplesWithVocabularyProperties";
    String sampleType = "TypeA";
    String updatedSampleType = "TypeB";
    String sampleColor = "Blue";
    int sampleAge = 5;

    Domain mockDomain = helper.createVocabularyTestDomain(user, c);
    Map<String, String> vocabularyPropertyURIs = helper.getVocabularyPropertyURIS(mockDomain);

    //create sample type
    ExpSampleTypeImpl st = SampleTypeServiceImpl.get().createSampleType(c, user,
            sampleName, null, List.of(new GWTPropertyDescriptor("name", "string")), Collections.emptyList(),
            -1, -1, -1, -1, null, null);

    assertNotNull(st);

    UserSchema schema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts("Samples"));

    // insert a sample
    ArrayListMap<String, Object> row = new ArrayListMap<>();
    row.put("name", "TestSample");
    row.put(vocabularyPropertyURIs.get(helper.typePropertyName), sampleType);
    row.put(vocabularyPropertyURIs.get(helper.colorPropertyName), sampleColor);
    row.put(vocabularyPropertyURIs.get(helper.agePropertyName), null); // inserting a property with null value
    List<Map<String, Object>> rows = helper.buildRows(row);

    var insertedSample = helper.insertRows(c, rows ,sampleName, schema);

    assertEquals("Custom Property is not inserted", sampleType,
            OntologyManager.getPropertyObjects(c, insertedSample.get(0).get("LSID").toString()).get(vocabularyPropertyURIs.get(helper.typePropertyName)).getStringValue());

    //Verifying property with null value is not inserted
    assertEquals("Property with null value is present.", 0, OntologyManager.getPropertyObjects(c, vocabularyPropertyURIs.get(helper.agePropertyName)).size());

    //update inserted sample
    ArrayListMap<String, Object> rowToUpdate = new ArrayListMap<>();
    rowToUpdate.put("name", "TestSample");
    rowToUpdate.put("RowId", insertedSample.get(0).get("RowId"));
    rowToUpdate.put(vocabularyPropertyURIs.get(helper.typePropertyName), updatedSampleType);
    rowToUpdate.put(vocabularyPropertyURIs.get(helper.colorPropertyName), null); // nulling out existing property
    rowToUpdate.put(vocabularyPropertyURIs.get(helper.agePropertyName), sampleAge); //inserting a new property in update rows
    List<Map<String, Object>> rowsToUpdate = helper.buildRows(rowToUpdate);

    List<Map<String, Object>> oldKeys = new ArrayList<>();
    ArrayListMap<String, Object> oldKey = new ArrayListMap<>();
    oldKey.put("name", "TestSample");
    oldKey.put("RowId", insertedSample.get(0).get("RowId"));
    oldKeys.add(oldKey);

    var updatedSample = helper.updateRows(c, rowsToUpdate, oldKeys, sampleName, schema);
    assertEquals("Custom Property is not updated", updatedSampleType,
            OntologyManager.getPropertyObjects(c, updatedSample.get(0).get("LSID").toString()).get(vocabularyPropertyURIs.get(helper.typePropertyName)).getStringValue());

    //Verify property updated to a null value gets deleted
    assertEquals("Property with null value is present.", 0, OntologyManager.getPropertyObjects(c, vocabularyPropertyURIs.get(helper.colorPropertyName)).size());

    //Verify property inserted during update rows in inserted
    assertEquals("New Property is not inserted with update rows", sampleAge,
            OntologyManager.getPropertyObjects(c, updatedSample.get(0).get("LSID").toString()).get(vocabularyPropertyURIs.get(helper.agePropertyName)).getFloatValue().intValue());
}


@Test
public void testDetailedAuditLog() throws Exception
{
    User user = TestContext.get().getUser();

    UserSchema auditSchema = AuditLogService.get().createSchema(user, c);
    TableInfo auditTable = auditSchema.getTable(SampleTimelineAuditEvent.EVENT_TYPE);
    Integer RowId = new SqlSelector(auditSchema.getDbSchema(), new SQLFragment("select max(rowid) FROM ").append(auditTable.getFromSQL("_")))
            .getObject(Integer.class);
    int auditMaxRowid = null==RowId ? 0 : RowId.intValue();

    List<GWTPropertyDescriptor> props = new ArrayList<>();
    props.add(new GWTPropertyDescriptor("Name", "string"));
    props.add(new GWTPropertyDescriptor("Measure", "string"));
    props.add(new GWTPropertyDescriptor("Value", "float"));
    final ExpSampleTypeImpl st = SampleTypeServiceImpl.get().createSampleType(c, user,
            "SamplesDAL", null, props, Collections.emptyList(),
            -1, -1, -1, -1, null, null);

    QuerySchema samplesSchema = DefaultSchema.get(user, c, "samples");
    assertNotNull(samplesSchema);
    TableInfo samplesTable = samplesSchema.getTable("SamplesDAL");
    assertNotNull(samplesTable);
    QueryUpdateService qus = samplesTable.getUpdateService();
    assertNotNull(qus);
    BatchValidationException errors = new BatchValidationException();
    Map<Enum,Object> config = Map.of(DetailedAuditLogDataIterator.AuditConfigs.AuditBehavior, AuditBehaviorType.DETAILED);

    // insert a sample
    List<Map<String, Object>> rows = new ArrayList<>();
    rows.add(PageFlowUtil.mapInsensitive("Name", "A1", "Measure", "Initial", "Value", 1.0));
    List<Map<String,Object>> ret = qus.insertRows(user, c, rows, errors, config, null);
    String msg = errors.getRowErrors().size() > 0 ? errors.getRowErrors().get(0).toString() : "no message";
    assertFalse(msg, errors.hasErrors());
    assertEquals(1,ret.size());
    assertNotNull(ret.get(0).get("rowid"));
    int rowid = (int) JdbcType.INTEGER.convert(ret.get(0).get("rowid"));
    // check audit log
    SimpleFilter f = new SimpleFilter(new FieldKey(null,"RowId"),auditMaxRowid, CompareType.GT);
    List<SampleTimelineAuditEvent> events = AuditLogService.get().getAuditEvents(c,user,SampleTimelineAuditEvent.EVENT_TYPE,f,new Sort("-RowId"));
    assertFalse(events.isEmpty());
    assertNull(events.get(0).getOldRecordMap());
    assertNotNull(events.get(0).getNewRecordMap());
    Map<String,String> newRecordMap = new CaseInsensitiveHashMap<>(PageFlowUtil.mapFromQueryString(events.get(0).getNewRecordMap()));
    assertEquals("Initial", newRecordMap.get("Measure"));
    assertEquals("1.0", newRecordMap.get("Value"));

    // UPDATE
    rows.clear(); errors.clear();
    rows.add(PageFlowUtil.mapInsensitive("RowId", rowid, "Measure", "Updated", "Value", 2.0));
    qus.updateRows(user, c, rows, null, config, null);
    // check audit log
    events = AuditLogService.get().getAuditEvents(c,user,SampleTimelineAuditEvent.EVENT_TYPE,f,new Sort("-RowId"));
    assertFalse(events.isEmpty());
    assertNotNull(events.get(0).getOldRecordMap());
    Map<String,String> oldRecordMap = new CaseInsensitiveHashMap<>(PageFlowUtil.mapFromQueryString(events.get(0).getOldRecordMap()));
    assertNotNull(events.get(0).getNewRecordMap());
    newRecordMap = new CaseInsensitiveHashMap<>(PageFlowUtil.mapFromQueryString(events.get(0).getNewRecordMap()));
    assertFalse(oldRecordMap.containsKey("lsid"));
    assertEquals("Initial", oldRecordMap.get("Measure"));
    assertEquals("1.0", oldRecordMap.get("Value"));
    assertEquals(2, oldRecordMap.size());
    assertFalse(newRecordMap.containsKey("lsid"));
    assertEquals("Updated",newRecordMap.get("Measure"));
    assertEquals("2.0", newRecordMap.get("Value"));
    assertEquals(2, newRecordMap.size());

    // MERGE
    // and since merge is a different code path...
    rows.clear(); errors.clear();
    rows.add(PageFlowUtil.mapInsensitive("Name", "A1", "Measure", "Merged", "Value", 3.0));
    int count = qus.mergeRows(user, c, new ListofMapsDataIterator(null,rows), errors, config, null);
    assertEquals(1, count);
    // check audit log
    events = AuditLogService.get().getAuditEvents(c,user,SampleTimelineAuditEvent.EVENT_TYPE,f,new Sort("-RowId"));
    assertFalse(events.isEmpty());
    assertNotNull(events.get(0).getOldRecordMap());
    oldRecordMap = new CaseInsensitiveHashMap<>(PageFlowUtil.mapFromQueryString(events.get(0).getOldRecordMap()));
    assertNotNull(events.get(0).getNewRecordMap());
    newRecordMap = new CaseInsensitiveHashMap<>(PageFlowUtil.mapFromQueryString(events.get(0).getNewRecordMap()));
    assertFalse(oldRecordMap.containsKey("lsid"));
    assertEquals("Updated", oldRecordMap.get("Measure"));
    assertEquals("2.0", oldRecordMap.get("Value"));
    assertEquals(2, oldRecordMap.size());
    assertFalse(newRecordMap.containsKey("lsid"));
    assertEquals("Merged",newRecordMap.get("Measure"));
    assertEquals("3.0", newRecordMap.get("Value"));
    assertEquals(2, newRecordMap.size());

    st.delete(user);
}

// Issue 43442: samples: not able to delete a Material not in a sample type
// - verify we can't insert or update via QueryUpdateService
// - verify we can read and delete via QueryUpdateService
@Test
public void testExpMaterialPermissions() throws Exception
{
    User user = TestContext.get().getUser();
    var schema = QueryService.get().getUserSchema(user, c, ExpSchema.SCHEMA_EXP);

    // create sample type
    ExpSampleTypeImpl st = SampleTypeServiceImpl.get().createSampleType(c, user,
            "MySamples", null, List.of(new GWTPropertyDescriptor("name", "string")), Collections.emptyList(),
            -1, -1, -1, -1, null, null);

    // insert a sample
    var errors = new BatchValidationException();
    List<Map<String,Object>> ret = QueryService.get().getUserSchema(user, c, SamplesSchema.SCHEMA_SAMPLES)
            .getTable("MySamples")
            .getUpdateService()
            .insertRows(user, c, List.of(CaseInsensitiveHashMap.of("name", "SampleInSampleType")), errors, null, null);
    String msg = errors.getRowErrors().size() > 0 ? errors.getRowErrors().get(0).toString() : "no message";
    assertFalse(msg, errors.hasErrors());
    assertEquals(1,ret.size());
    assertNotNull(ret.get(0).get("rowid"));
    int stSampleId = (int) JdbcType.INTEGER.convert(ret.get(0).get("rowid"));

    // verify insert, update aren't allowed, but read and delete are allowed
    var materialsTable = schema.getTable(ExpSchema.TableType.Materials.name());
    var qus = materialsTable.getUpdateService();
    assertNotNull(qus);
    assertTrue(qus.hasPermission(user, ReadPermission.class));
    assertTrue(qus.hasPermission(user, DeletePermission.class));
    assertFalse(qus.hasPermission(user, InsertPermission.class));
    assertFalse(qus.hasPermission(user, UpdatePermission.class));

    // create a sample outside of a SampleType
    var lsid = ExperimentService.get().generateLSID(c, ExpMaterial.class, "SampleNotInSampleType");
    ExpMaterial m = ExperimentService.get().createExpMaterial(c, Lsid.parse(lsid));
    m.save(user);

    // verify we can't delete both samples when targeting the default 'Material' cpasType
    try
    {
        ExperimentServiceImpl.get().deleteMaterialByRowIds(
                user, c, List.of(stSampleId, m.getRowId()), true, null, false);
        fail("Expected to throw exception");
    }
    catch (Exception e)
    {
        assertThat(e.getMessage(), containsString("Error deleting sample of default 'Material' type: 'SampleInSampleType' is in the sample type '" + st.getLSID() + "'"));
    }

    // verify we can't delete both samples when targeting the "MySamples" type
    try
    {
        ExperimentServiceImpl.get().deleteMaterialByRowIds(
                user, c, List.of(stSampleId, m.getRowId()), true, st, false);
        fail("Expected to throw exception");
    }
    catch (Exception e)
    {
        assertThat(e.getMessage(), containsString("Error deleting '" + st.getName() + "' sample: 'SampleNotInSampleType' is in the sample type '" + ExpMaterial.DEFAULT_CPAS_TYPE + "'"));
    }

    // verify read via QUS
    var rows = qus.getRows(user, c, List.of(CaseInsensitiveHashMap.of("rowId", m.getRowId())));
    assertEquals("Failed to fetch material via QUS", 1, rows.size());
    assertEquals(m.getLSID(), rows.get(0).get("lsid"));

    // verify delete via QUS
    rows = qus.deleteRows(user, c, List.of(CaseInsensitiveHashMap.of("rowId", m.getRowId())), null, null);
    assertEquals("Failed to delete material via QUS", 1, rows.size());
}
%>

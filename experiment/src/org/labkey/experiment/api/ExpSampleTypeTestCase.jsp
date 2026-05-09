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
<%@ page import="org.labkey.api.data.JdbcType" %>
<%@ page import="org.labkey.api.data.SQLFragment" %>
<%@ page import="org.labkey.api.data.SimpleFilter" %>
<%@ page import="org.labkey.api.data.Sort" %>
<%@ page import="org.labkey.api.data.SqlSelector" %>
<%@ page import="org.labkey.api.data.TableInfo" %>
<%@ page import="org.labkey.api.data.TableSelector" %>
<%@ page import="org.labkey.api.dataiterator.DataIteratorContext" %>
<%@ page import="org.labkey.api.dataiterator.DetailedAuditLogDataIterator" %>
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
<%@ page import="org.labkey.api.exp.query.SamplesSchema" %>
<%@ page import="org.labkey.api.gwt.client.AuditBehaviorType" %>
<%@ page import="org.labkey.api.gwt.client.model.GWTPropertyDescriptor" %>
<%@ page import="org.labkey.api.query.BatchValidationException" %>
<%@ page import="org.labkey.api.query.FieldKey" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.query.QueryUpdateService" %>
<%@ page import="static org.hamcrest.CoreMatchers.hasItems" %>
<%@ page import="static org.junit.Assert.*" %>
<%@ page import="org.labkey.api.query.UserSchema" %>
<%@ page import="org.labkey.api.reader.DataLoader" %>
<%@ page import="org.labkey.api.reader.TabLoader" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.DeletePermission" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.TestContext" %>
<%@ page import="org.labkey.experiment.api.ExpProvisionedTableTestHelper" %>
<%@ page import="org.labkey.experiment.api.ExperimentServiceImpl" %>
<%@ page import="java.io.StringBufferInputStream" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="static org.hamcrest.CoreMatchers.containsString" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ page import="org.labkey.api.action.ApiUsageException" %>
<%@ page import="org.labkey.api.exp.api.ExpLineageService" %>
<%@ page import="org.labkey.api.search.SearchService" %>
<%@ page import="java.util.concurrent.TimeUnit" %>
<%@ page import="org.jetbrains.annotations.NotNull" %>
<%@ page import="org.labkey.api.dataiterator.MapDataIterator" %>
<%@ page import="static org.labkey.api.exp.api.ExperimentService.asInteger" %>
<%@ page import="static org.labkey.api.exp.api.ExperimentService.asLong" %>
<%@ page import="static java.util.Collections.emptyList" %>
<%@ page import="org.jetbrains.annotations.Nullable" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.query.QueryParam" %>
<%@ page import="org.labkey.api.view.ViewServlet" %>
<%@ page import="org.labkey.api.util.JsonUtil" %>
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page extends="org.labkey.api.jsp.JspTest.BVT" %>

<%!
private static final String PROJECT_NAME = "_testSampleType";
private final ExpProvisionedTableTestHelper helper = new ExpProvisionedTableTestHelper();

private Container c;

@Before
public void setUp()
{
    // NOTE: We need to use a project to create the sample type so we can insert rows into sub-folders
    c = ContainerManager.getForPath(PROJECT_NAME);
    if (c != null)
        ContainerManager.deleteAll(c, TestContext.get().getUser());
    c = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT_NAME, TestContext.get().getUser());
}

@After
public void tearDown() throws InterruptedException
{
    // Wait for the indexer to finish working on the data we just added to help avoid deadlocks
    SearchService.get().drainQueue(SearchService.PRIORITY.crawl, 15, TimeUnit.SECONDS);
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
    try
    {
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("name", "string"));

        createSampleType(null, props, null);
    }
    catch (ApiUsageException ee)
    {
        assertEquals("Sample Type name is required.", ee.getMessage());
    }
}

@Test // Issue 51321
public void reservedNameFirst() throws Exception
{
    try
    {
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("name", "string"));

        createSampleType("First", props, null);
    }
    catch (ApiUsageException ee)
    {
        assertEquals("Sample Type name 'First' is a reserved name.", ee.getMessage());
    }
}

@Test // Issue 51321
public void reservedNameAll() throws Exception
{
    try
    {
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("name", "string"));

        createSampleType("All", props, null);
    }
    catch (ApiUsageException ee)
    {
        assertEquals("Sample Type name 'All' is a reserved name.", ee.getMessage());
    }
}

// validate name scale
@Test
public void nameScale() throws Exception
{
    try
    {
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("name", "string"));

        createSampleType(StringUtils.repeat("a", 1000), props, null);
    }
    catch (ApiUsageException ee)
    {
        assertEquals("Sample Type name may not exceed 100 characters.", ee.getMessage());
    }
}

// validate name expression scale
@Test
public void nameExpressionScale() throws Exception
{
    try
    {
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("name", "string"));
        props.add(new GWTPropertyDescriptor("prop", "string"));
        props.add(new GWTPropertyDescriptor("age", "int"));

        createSampleType("Samples", props, StringUtils.repeat("a", 1000));
    }
    catch (ApiUsageException ee)
    {
        assertEquals("Name expression may not exceed 500 characters.", ee.getMessage());
    }
}

// idCols all null, nameExpression null, no 'name' property -- fail
@Test
public void idColsUnset_nameExpressionNull_noNameProperty() throws Exception
{
    try
    {
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("notName", "string"));
        props.add(new GWTPropertyDescriptor("age", "int"));

        createSampleType("Samples", props, null);
        fail("Expected exception");
    }
    catch (ApiUsageException ee)
    {
        assertEquals("Either a 'Name' property or an index for idCol1 is required", ee.getMessage());
    }
}

// idCols all null, nameExpression null, has 'name' property -- ok
@Test
public void idColsUnset_nameExpressionNull_hasNameProperty() throws Exception
{
    List<GWTPropertyDescriptor> props = new ArrayList<>();
    props.add(new GWTPropertyDescriptor("name", "string"));
    props.add(new GWTPropertyDescriptor("age", "int"));

    final ExpSampleType st = createSampleType("Samples", props, null);

    ExpMaterial sample = st.getSample(c, "bob");
    assertNull(sample);

    List<Map<String, Object>> rows = new ArrayList<>();
    rows.add(CaseInsensitiveHashMap.of("name", "bob", "age", 10));

    insertSampleRows("Samples", rows);

    assertExpectedName(st, "bob");
}

// idCols all null, nameExpression not null, has 'name' property -- ok
@Test
public void idColsUnset_nameExpression_hasNameProperty() throws Exception
{
    List<GWTPropertyDescriptor> props = new ArrayList<>();
    props.add(new GWTPropertyDescriptor("name", "string"));
    props.add(new GWTPropertyDescriptor("prop", "string"));
    props.add(new GWTPropertyDescriptor("age", "int"));

    createSampleType("Samples", props, "S-${prop}.${age}");
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

    List<Map<String, Object>> rows = new ArrayList<>();
    rows.add(CaseInsensitiveHashMap.of("name", "bob", "prop", "blue", "age", 10));
    rows.add(CaseInsensitiveHashMap.of("prop", "red", "age", 11));

    insertSampleRows("Samples", rows);

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

        SampleTypeService.get().createSampleType(c, user,
                "Samples", null, props, Collections.emptyList(),
                1, -1, -1, -1, null, null);
        fail("Expected exception");
    }
    catch (ApiUsageException ee)
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

    final ExpSampleType st = createSampleType("Samples", props, null);

    final String expectedName1 = "bob";
    ExpMaterial sample1 = st.getSample(c, expectedName1);
    assertNull(sample1);

    List<Map<String, Object>> rows = new ArrayList<>();
    rows.add(CaseInsensitiveHashMap.of("name", "bob", "prop", "blue", "age", 10));

    BatchValidationException errors = new BatchValidationException();
    QueryUpdateService svc = getSampleTypeUpdateService(st.getName());
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
    assertTrue(errors.getMessage().contains("SampleID or Name is required for sample"));
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

        SampleTypeService.get().createSampleType(c, user,
                "Samples", null, props, Collections.emptyList(),
                1, -1, -1, -1, "S-${name}.${age}", null);
        fail("Expected exception");
    }
    catch (ApiUsageException ee)
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

    final String sampleTypeName = "Samples";
    final ExpSampleType st = createSampleType(sampleTypeName, props, "S-${prop}.${age}.${genId:number('000')}");

    final String expectedName1 = "bob";
    final String expectedName2 = "S-red.11.002";
    final String expectedName3 = "S-red.11.003";
    assertNull(st.getSample(c, expectedName1));
    assertNull(st.getSample(c, expectedName2));
    assertNull(st.getSample(c, expectedName3));

    List<Map<String, Object>> rows = new ArrayList<>();
    rows.add(CaseInsensitiveHashMap.of("name", "bob", "prop", "blue", "age", 10));
    rows.add(CaseInsensitiveHashMap.of("prop", "red", "age", 11));
    rows.add(CaseInsensitiveHashMap.of("prop", "red", "age", 11));

    List<Map<String, Object>> ret = insertSampleRows(sampleTypeName, rows);

    assertEquals(3, ret.size());

    assertEquals(1, ret.getFirst().get("genId"));
    assertEquals(expectedName1, ret.get(0).get("name"));
    assertExpectedName(st, expectedName1);

    assertEquals(2, ret.get(1).get("genId"));
    assertEquals(expectedName2, ret.get(1).get("name"));
    assertExpectedName(st, expectedName2);

    assertEquals(3, ret.get(2).get("genId"));
    assertEquals(expectedName3, ret.get(2).get("name"));
    assertExpectedName(st, expectedName3);

    // Issue 53400: Verify the aliquot naming pattern is case-insensitive
    st.setAliquotNameExpression("${aliquotedFrom}-ALI-${genId:number('0000')}");
    st.save(user);

    List<Map<String, Object>> aliquotRows = new ArrayList<>();
    aliquotRows.add(CaseInsensitiveHashMap.of("aliquotedFrom", expectedName1, "AliquotCount", 10));
    List<Map<String, Object>> aliquots = insertSampleRows(sampleTypeName, aliquotRows);
    assertExpectedName(st, expectedName1 + "-ALI-0004");
    assertEquals(expectedName1, aliquots.getFirst().get("AliquotedFrom"));

    aliquotRows = new ArrayList<>();
    aliquotRows.add(CaseInsensitiveHashMap.of("Aliquotedfrom", expectedName2, "aliquotCount", 5));
    aliquots = insertSampleRows(sampleTypeName, aliquotRows);
    assertExpectedName(st, expectedName2 + "-ALI-0005");
    assertEquals(expectedName2, aliquots.getFirst().get("aliquotedFrom"));

    aliquotRows = new ArrayList<>();
    aliquotRows.add(CaseInsensitiveHashMap.of("ALIQUOTEDFROM", expectedName1, "Aliquotcount", 15));
    aliquots = insertSampleRows(sampleTypeName, aliquotRows);
    assertExpectedName(st, expectedName1 + "-ALI-0006");
    assertEquals(expectedName1, aliquots.getFirst().get("aliquotedfrom"));

    aliquotRows = new ArrayList<>();
    aliquotRows.add(CaseInsensitiveHashMap.of("AliquotedFrom", expectedName3, "ALIQUOTCOUNT", 2));
    aliquots = insertSampleRows(sampleTypeName, aliquotRows);
    assertExpectedName(st, expectedName3 + "-ALI-0007");
    assertEquals(expectedName3, aliquots.getFirst().get("ALIQUOTEDFROM"));

    // Issue 53063: Support "Aliquoted From"
    aliquotRows = new ArrayList<>();
    aliquotRows.add(CaseInsensitiveHashMap.of("Aliquoted From", expectedName2, "ALIQUOTCOUNT", 2));
    aliquots = insertSampleRows(sampleTypeName, aliquotRows);
    assertExpectedName(st, expectedName2 + "-ALI-0008");
    assertEquals(expectedName2, aliquots.getFirst().get("ALIQUOTEDFROM"));

    aliquotRows = new ArrayList<>();
    aliquotRows.add(CaseInsensitiveHashMap.of("aliquoted from", expectedName3, "ALIQUOTCOUNT", 3));
    aliquots = insertSampleRows(sampleTypeName, aliquotRows);
    assertExpectedName(st, expectedName3 + "-ALI-0009");
    assertEquals(expectedName3, aliquots.getFirst().get("aliquotedFrom"));

    // test the default aliquot naming pattern (${${AliquotedFrom}-:withCounter}
    st.setAliquotNameExpression("");
    st.save(user);

    aliquotRows = new ArrayList<>();
    aliquotRows.add(CaseInsensitiveHashMap.of("aliquotedFrom", expectedName1));
    aliquots = insertSampleRows(sampleTypeName, aliquotRows);
    assertExpectedName(st, expectedName1 + "-1");
    assertEquals(expectedName1, aliquots.getFirst().get("AliquotedFrom"));

    aliquotRows = new ArrayList<>();
    aliquotRows.add(CaseInsensitiveHashMap.of("Aliquotedfrom", expectedName1));
    aliquots = insertSampleRows(sampleTypeName, aliquotRows);
    assertExpectedName(st, expectedName1 + "-2");
    assertEquals(expectedName1, aliquots.getFirst().get("aliquotedFrom"));

    aliquotRows = new ArrayList<>();
    aliquotRows.add(CaseInsensitiveHashMap.of("ALIQUOTEDFROM", expectedName1));
    aliquots = insertSampleRows(sampleTypeName, aliquotRows);
    assertExpectedName(st, expectedName1 + "-3");
    assertEquals(expectedName1, aliquots.getFirst().get("aliquotedfrom"));

    aliquotRows = new ArrayList<>();
    aliquotRows.add(CaseInsensitiveHashMap.of("AliquotedFrom", expectedName1));
    aliquots = insertSampleRows(sampleTypeName, aliquotRows);
    assertExpectedName(st, expectedName1 + "-4");
    assertEquals(expectedName1, aliquots.getFirst().get("ALIQUOTEDFROM"));

    aliquotRows = new ArrayList<>();
    aliquotRows.add(CaseInsensitiveHashMap.of("Aliquoted From", expectedName1));
    aliquots = insertSampleRows(sampleTypeName, aliquotRows);
    assertExpectedName(st, expectedName1 + "-5");
    assertEquals(expectedName1, aliquots.getFirst().get("ALIQUOTEDFROM"));

    aliquotRows = new ArrayList<>();
    aliquotRows.add(CaseInsensitiveHashMap.of("aliquoted from", expectedName1));
    aliquots = insertSampleRows(sampleTypeName, aliquotRows);
    assertExpectedName(st, expectedName1 + "-6");
    assertEquals(expectedName1, aliquots.getFirst().get("aliquotedFrom"));
}

@Test
public void testAliases() throws Exception
{
    List<GWTPropertyDescriptor> props = new ArrayList<>();
    props.add(new GWTPropertyDescriptor("name", "string"));
    props.add(new GWTPropertyDescriptor("age", "int"));
    final ExpSampleType st = createSampleType("Samples", props, null);

    ExpMaterial fooSample;
    ExpMaterial booSample;
    List<Map<String, Object>> rows;

    // Insert
    {
        rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of("name", "foo", "age", 19, "alias", "youth, teen, minor", "flag", "new zealand"));
        rows.add(CaseInsensitiveHashMap.of("name", "boo", "age", 21, "alias", "elder, adult, major?", "flag", "australia"));

        insertSampleRows(st.getName(), rows);

        fooSample = st.getSample(c, "foo");
        assertThat(fooSample.getAliases(), hasItems("youth", "teen", "minor"));
        assertEquals("new zealand", fooSample.getComment());

        booSample = st.getSample(c, "boo");
        assertThat(booSample.getAliases(), hasItems("elder", "adult", "major?"));
        assertEquals("australia", booSample.getComment());
    }

    // Update, keyed by name
    {
        rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of("name", fooSample.getName(), "alias", "gerald, r, ford", "flag", "kenya"));
        rows.add(CaseInsensitiveHashMap.of("name", booSample.getName(), "alias", "dwight, d, eisenhower", "flag", "uganda"));

        updateSampleRows(st.getName(), rows);

        fooSample = st.getSample(c, fooSample.getName());
        assertThat(fooSample.getAliases(), hasItems("gerald", "r", "ford"));
        assertEquals("kenya", fooSample.getComment());

        booSample = st.getSample(c, "boo");
        assertThat(booSample.getAliases(), hasItems("dwight", "d", "eisenhower"));
        assertEquals("uganda", booSample.getComment());
    }

    // Update, keyed by rowId
    {
        rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of("Row Id", fooSample.getRowId(), "alias", "ken, griffey", "flag", "norway"));
        rows.add(CaseInsensitiveHashMap.of("Row Id", booSample.getRowId(), "alias", "edgar, martinez", "flag", "sweden"));

        updateSampleRows(st.getName(), rows);

        fooSample = st.getSample(c, fooSample.getName());
        assertThat(fooSample.getAliases(), hasItems("ken", "griffey"));
        assertEquals("norway", fooSample.getComment());

        booSample = st.getSample(c, booSample.getName());
        assertThat(booSample.getAliases(), hasItems("edgar", "martinez"));
        assertEquals("sweden", booSample.getComment());
    }

    // Update with different row shapes
    {
        rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of("rowId", fooSample.getRowId(), "description", "east coast destination", "alias", "martha's, vineyard", "flag", "japan"));
        rows.add(CaseInsensitiveHashMap.of("name", booSample.getName(), "age", 1_000_000, "alias", "cannon, beach", "flag", "fiji"));

        updateSampleRows(st.getName(), rows);

        fooSample = st.getSample(c, fooSample.getName());
        assertThat(fooSample.getAliases(), hasItems("martha's", "vineyard"));
        assertEquals("japan", fooSample.getComment());

        booSample = st.getSample(c, booSample.getName());
        assertThat(booSample.getAliases(), hasItems("cannon", "beach"));
        assertEquals("fiji", booSample.getComment());
    }

    // Merge
    {
        rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of("name", fooSample.getName(), "age", 40, "alias", "son, family, father", "flag", "canada"));
        rows.add(CaseInsensitiveHashMap.of("name", booSample.getName(), "age", 80, "alias", "grandma, family, mother", "flag", "america"));
        rows.add(CaseInsensitiveHashMap.of("name", "moo", "age", 12, "alias", "cow, bovine", "flag", "mexico"));

        mergeSampleRows(st.getName(), rows);

        var expectedRowId = fooSample.getRowId();
        fooSample = st.getSample(c, fooSample.getName());
        assertEquals(expectedRowId, fooSample.getRowId());
        assertThat(fooSample.getAliases(), hasItems("son", "family", "father"));
        assertEquals("canada", fooSample.getComment());

        expectedRowId = booSample.getRowId();
        booSample = st.getSample(c, booSample.getName());
        assertEquals(expectedRowId, booSample.getRowId());
        assertThat(booSample.getAliases(), hasItems("grandma", "family", "mother"));
        assertEquals("america", booSample.getComment());

        ExpMaterial mooSample = st.getSample(c, "moo");
        assertThat(mooSample.getAliases(), hasItems("cow", "bovine"));
        assertEquals("mexico", mooSample.getComment());
    }
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

    final ExpSampleType st = createSampleType("Samples", props, "S-${now:date}-${dailySampleCount}");

    List<? extends ExpMaterial> allSamples = st.getSamples(c);
    assertTrue("Expected no samples", allSamples.isEmpty());

    // insert via insertRows -- blank rows should be preserved
    QueryUpdateService svc = getSampleTypeUpdateService(st.getName());

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

    String name1 = (String)inserted.getFirst().get("name");
    assertTrue("Expected generated sample name to start with 'S-', got: " + name1, name1 != null && name1.startsWith("S-"));

    allSamples = st.getSamples(c);
    assertEquals("Expected 3 total samples", 3, allSamples.size());
    assertEquals(0, allSamples.getFirst().getAliquotCount());

    // insert as if we pasted a tsv in the "upload samples" page -- blank rows should be skipped
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
    assertEquals(0, insertedRows.getFirst().get("AliquotCount"));

    ExpMaterial material1 = ExperimentService.get().getExpMaterial(asLong(insertedRows.getFirst().get("rowid")));
    assertNotNull(material1);
    Map<PropertyDescriptor, Object> map = material1.getPropertyValues();
    assertEquals("Expected to only have 'age' property, got: " + map, 1, map.size());

    Integer age1 = asInteger(material1.getPropertyValues().values().iterator().next());
    assertNotNull(age1);
    assertEquals("Expected to insert age of 20, got: " + age1, 20, age1.intValue());

    ExpMaterial material2 = ExperimentService.get().getExpMaterial(asLong(insertedRows.get(1).get("rowid")));
    assertNotNull(material2);
    Integer age2 = asInteger(material2.getPropertyValues().values().iterator().next());
    assertNotNull(age2);
    assertEquals("Expected to insert age of 30, got: " + age2, 30, age2.intValue());

    allSamples = st.getSamples(c);
    assertEquals("Expected 5 total samples", 5, allSamples.size());

    // how about an update
    var updated = new CaseInsensitiveHashMap<>();
    updated.put("name", material1.getName());
    updated.put("lsid", material1.getLSID());
    updated.put("age", age1 + 1);
    svc.updateRows(user, c, Collections.singletonList(updated), null, errors, null, null);
    assertFalse(errors.hasErrors());
    TableInfo table = getSampleTypeTable(st.getName());
    var result = new TableSelector(table, new SimpleFilter("lsid", material1.getLSID()), null).getMap();
    assertEquals(21, asInteger(result.get("age")).intValue());

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
    final ExpSampleType childType = createSampleType("ChildSamples", props, null);
    final ExpSampleType parent1Type = createSampleType("Parent1Samples", props, null);
    final ExpSampleType parent2Type = createSampleType("Parent2Samples", props, null);

    // add first parents
    QueryUpdateService updateService = getSampleTypeUpdateService(parent1Type.getName());

    BatchValidationException errors = new BatchValidationException();
    List<Map<String, Object>> rows = new ArrayList<>();
    rows.add(CaseInsensitiveHashMap.of("name", "P1-1"));
    rows.add(CaseInsensitiveHashMap.of("name", "P1-2"));
    rows.add(CaseInsensitiveHashMap.of("name", "P1-3"));
    rows.add(CaseInsensitiveHashMap.of("name", "P1-4,test"));

    List<Map<String, Object>> inserted = updateService.insertRows(user, c, rows, errors, null, null);
    assertFalse(errors.hasErrors());
    assertEquals("Number of parent1 samples inserted not as expected", 4, inserted.size());

    // add second parents
    updateService = getSampleTypeUpdateService(parent2Type.getName());

    rows.clear();
    rows.add(CaseInsensitiveHashMap.of("name", "P2-1"));
    rows.add(CaseInsensitiveHashMap.of("name", "P2-2"));
    rows.add(CaseInsensitiveHashMap.of("name", "P2-3"));
    inserted = updateService.insertRows(user, c, rows, errors, null, null);
    assertFalse(errors.hasErrors());
    assertEquals("Number of parent2 samples inserted not as expected", 3, inserted.size());

    // add child samples
    updateService = getSampleTypeUpdateService(childType.getName());

    rows.clear();
    rows.add(CaseInsensitiveHashMap.of("name", "C1",  "MaterialInputs/Parent1Samples", "P1-1,P1-2", "MaterialInputs/Parent2Samples", "P2-1"));
    rows.add(CaseInsensitiveHashMap.of("name", "C2", "MaterialInputs/Parent1Samples", "P1-1", "MaterialInputs/Parent2Samples", "P2-1"));
    rows.add(CaseInsensitiveHashMap.of("name", "C3", "age", 42, "MaterialInputs/Parent1Samples", "P1-1", "MaterialInputs/Parent2Samples", "P2-1, P2-2"));
    rows.add(CaseInsensitiveHashMap.of("name", "C4", "MaterialInputs/Parent1Samples", "P1-2", "MaterialInputs/Parent2Samples", "P2-2"));
    rows.add(CaseInsensitiveHashMap.of("name", "C5", "MaterialInputs/Parent1Samples", "P1-1, \"P1-4,test\", P1-2"));

    inserted = updateService.insertRows(user, c, rows, errors, null, null);
    assertFalse(errors.hasErrors());
    assertEquals("Number of child samples inserted not as expected", 5, inserted.size());

    // Issue 53168: circular lineage not allowed
    rows.clear();
    rows.add(CaseInsensitiveHashMap.of("rowId", parent2Type.getSample(c, "P2-1").getRowId(), "MaterialInputs/ChildSamples", "C1"));
    try
    {
        getSampleTypeUpdateService(parent2Type.getName()).updateRows(user, c, rows, null, errors, null, null);
        fail("Expected to throw exception");
    }
    catch (Exception e)
    {
        assertThat(e.getMessage(), containsString("'C1' is derived from sample 'P2-1'. Circular relationships are not allowed."));
    }

    errors = new BatchValidationException();
    ExpMaterial P11 = parent1Type.getSample(c, "P1-1");
    ExpMaterial P12 = parent1Type.getSample(c, "P1-2");
    ExpMaterial P14 = parent1Type.getSample(c, "P1-4,test");
    ExpMaterial P21 = parent2Type.getSample(c, "P2-1");
    ExpMaterial P22 = parent2Type.getSample(c, "P2-2");

    ExpMaterial C1 = childType.getSample(c, "C1");
    ExpMaterial C2 = childType.getSample(c, "C2");
    ExpMaterial C4 = childType.getSample(c, "C4");
    ExpMaterial C5 = childType.getSample(c, "C5");

    ExpLineageOptions opts = new ExpLineageOptions();
    opts.setChildren(false);
    opts.setParents(true);
    opts.setDepth(2);

    // Attempt to merge using rowIds
    {
        rows.clear();
        rows.add(CaseInsensitiveHashMap.of("rowId", C1.getRowId(), "name", "C1", "MaterialInputs/Parent1Samples", "P1-1"));
        rows.add(CaseInsensitiveHashMap.of("rowId", C4.getRowId(), "name", "C5", "MaterialInputs/Parent1Samples", null)); // intentionally mix up name

        updateService.mergeRows(user, c, MapDataIterator.of(rows), errors, null, null);
        assertThat(errors.getMessage(), containsString("RowId is not accepted when merging samples. Specify only the sample name instead."));
        errors = new BatchValidationException();
    }

    // Attempt to merge using "Row Id" label
    {
        rows.clear();
        rows.add(CaseInsensitiveHashMap.of("Row Id", C1.getRowId(), "name", "C1", "MaterialInputs/Parent1Samples", "P1-1"));
        rows.add(CaseInsensitiveHashMap.of("Row Id", C4.getRowId(), "name", "C5", "MaterialInputs/Parent1Samples", null)); // intentionally mix up name

        updateService.mergeRows(user, c, MapDataIterator.of(rows), errors, null, null);
        assertThat(errors.getMessage(), containsString("RowId is not accepted when merging samples. Specify only the sample name instead."));
        errors = new BatchValidationException();
    }

    // Attempt to update using outdated "LSID" and do not specify any other keys
    // Note: using try/catch here as updateRows() executes with retry which throws
    // if validation exceptions are encountered.
    try
    {
        rows.clear();
        rows.add(CaseInsensitiveHashMap.of("LSID", C1.getLSID(), "MaterialInputs/Parent1Samples", "P1-1"));
        rows.add(CaseInsensitiveHashMap.of("LSID", C4.getLSID(), "MaterialInputs/Parent1Samples", null));

        updateService.updateRows(user, c, rows, null, errors, null, null);
        fail("Expected to throw exception");
    }
    catch (Exception e)
    {
        assertThat(e.getMessage(), containsString("LSID is no longer accepted as a key for sample update"));
    }

    // Attempt to merge using outdated "LSID" and do not specify any other keys
    {
        rows.clear();
        rows.add(CaseInsensitiveHashMap.of("LSID", C1.getLSID(), "MaterialInputs/Parent1Samples", "P1-1"));
        rows.add(CaseInsensitiveHashMap.of("LSID", C4.getLSID(), "MaterialInputs/Parent1Samples", null));

        updateService.mergeRows(user, c, MapDataIterator.of(rows), errors, null, null);
        assertThat(errors.getMessage(), containsString("LSID is no longer accepted as a key for sample merge"));
        errors = new BatchValidationException();
    }

    // now update the children with various types of modifications to the parentage
    rows.clear();
    rows.add(CaseInsensitiveHashMap.of("name", "C1", "MaterialInputs/Parent1Samples", "P1-1")); // change one parent but not the other
    rows.add(CaseInsensitiveHashMap.of("name", "C4", "MaterialInputs/Parent1Samples", null)); // remove one parent but not the other

    updateService.mergeRows(user, c, MapDataIterator.of(rows), errors, null, null);
    assertFalse(errors.hasErrors());

    ExpLineage lineage = ExpLineageService.get().getLineage(c, user, C1, opts);
    assertTrue("Expected 'C1' to be derived from 'P1-1'", lineage.getMaterials().contains(P11));
    assertFalse("Expected 'C1' to no longer be derived from 'P1-2'", lineage.getMaterials().contains(P12));
    assertTrue("Expected 'C1' to still be derived from 'P2-1'", lineage.getMaterials().contains(P21));

    lineage = ExpLineageService.get().getLineage(c, user, C4, opts);
    assertFalse("Expected 'C4' to not be derived from 'P1-2'", lineage.getMaterials().contains(P12));
    assertTrue("Expected 'C4' to still be derived from 'P2-2'", lineage.getMaterials().contains(P22));

    rows.clear();
    rows.add(CaseInsensitiveHashMap.of("name", "C4", "MaterialInputs/Parent1Samples", "P1-1", "MaterialInputs/Parent2Samples", "P2-1")); // change both parents
    rows.add(CaseInsensitiveHashMap.of("name", "C2", "MaterialInputs/Parent1Samples", "", "MaterialInputs/Parent2Samples", null)); // remove both parents

    updateService.mergeRows(user, c, MapDataIterator.of(rows), errors, null, null);
    assertFalse(errors.hasErrors());

    lineage = ExpLineageService.get().getLineage(c, user, C2, opts);
    assertTrue("Expected 'C2' to have no parents'", lineage.getMaterials().isEmpty());

    lineage = ExpLineageService.get().getLineage(c, user, C4, opts);
    assertEquals("Expected 'C4' to have two parents", 2, lineage.getMaterials().size());
    assertTrue("Expected 'C4' to be derived from 'P1-1'", lineage.getMaterials().contains(P11));
    assertTrue("Expected 'C4' to be derived from 'P2-1'", lineage.getMaterials().contains(P21));

    lineage = ExpLineageService.get().getLineage(c, user, C5, opts);
    assertEquals("Expected 'C5' to have three parents", 3, lineage.getMaterials().size());
    assertTrue("Expected 'C5' to be derived from 'P1-1'", lineage.getMaterials().contains(P11));
    assertTrue("Expected 'C5' to be derived from 'P1-4,test'", lineage.getMaterials().contains(P14));
    assertTrue("Expected 'C5' to be derived from 'P1-2'", lineage.getMaterials().contains(P12));
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

    String sampleTypeName = "Samples";
    final ExpSampleType st = createSampleType(sampleTypeName, props, null);

    // insert and derive with both 'parent' column and 'DataInputs/Samples'

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
    QueryUpdateService svc = getSampleTypeUpdateService(st.getName());
    List<Map<String, Object>> inserted = svc.insertRows(user, c, rows, errors, null, null);
    assertFalse(errors.hasErrors());
    assertEquals(6, inserted.size());

    // verify
    ExpLineageOptions opts = new ExpLineageOptions();
    opts.setChildren(false);
    opts.setParents(true);
    opts.setDepth(2);

    ExpMaterial A = st.getSample(c, "A");
    assertEquals(0, A.getAliquotCount());
    assertNotNull(A);
    ExpLineage lineage = ExpLineageService.get().getLineage(c, user, A, opts);
    assertTrue(lineage.getMaterials().isEmpty());
    assertNull(A.getRunId());

    ExpMaterial B = st.getSample(c, "B");
    assertEquals(0, A.getAliquotCount());
    assertNotNull(B);
    lineage = ExpLineageService.get().getLineage(c, user, B, opts);
    assertEquals(1, lineage.getMaterials().size());
    assertTrue("Expected 'B' to be derived from 'A'", lineage.getMaterials().contains(A));
    assertNotNull(B.getRunId());

    ExpMaterial C = st.getSample(c, "C");
    assertEquals(0, A.getAliquotCount());
    assertNotNull(C);
    lineage = ExpLineageService.get().getLineage(c, user, C, opts);
    assertEquals(1, lineage.getMaterials().size());
    assertTrue("Expected 'C' to be derived from 'B'", lineage.getMaterials().contains(B));

    ExpMaterial D = st.getSample(c, "D");
    assertEquals(0, A.getAliquotCount());
    assertNotNull(D);
    lineage = ExpLineageService.get().getLineage(c, user, D, opts);
    assertEquals(2, lineage.getMaterials().size());
    assertTrue("Expected 'D' to be derived from 'B'", lineage.getMaterials().contains(B));
    assertTrue("Expected 'D' to be derived from 'C'", lineage.getMaterials().contains(C));

    ExpMaterial E = st.getSample(c, "E");
    assertNotNull(E);
    lineage = ExpLineageService.get().getLineage(c, user, E, opts);
    assertTrue("Expected 'E' to be the seed", lineage.getSeeds().contains(E));
    assertEquals(2, lineage.getMaterials().size());
    assertTrue("Expected 'E' to be derived from 'B'", lineage.getMaterials().contains(B));
    assertTrue("Expected 'E' to be derived from 'C'", lineage.getMaterials().contains(C));

    // verify that 'E' is derived in the same run as 'D' since they share the same parents
    assertEquals("Expected 'E' and 'D' to be derived in the same run since they share 'B' and 'C' as parents",
            E.getRowId(), E.getRowId());
    ExpRun derivationRun = E.getRun();
    assertNotNull(derivationRun);

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

    List<Map<String, Object>> updated = svc.updateRows(user, c, rows, null, errors, null, null);
    assertFalse(errors.hasErrors());
    assertEquals(1, updated.size());

    ExpMaterial D2 = st.getSample(c, "D");
    lineage = ExpLineageService.get().getLineage(c, user, D2, opts);
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
    assertNotNull(oldDerivationRun);
    assertEquals(oldDerivationRun.getRowId(), derivationRun.getRowId());

    assertTrue(oldDerivationRun.getMaterialInputs().containsKey(B));
    assertTrue(oldDerivationRun.getMaterialInputs().containsKey(C));
    assertFalse(oldDerivationRun.getMaterialInputs().containsKey(E));
    assertFalse(oldDerivationRun.getMaterialOutputs().contains(D));
    assertTrue(oldDerivationRun.getMaterialOutputs().contains(E));

    // Issue 43241: Display of dates from Input/Output columns for sample types does not use the project date format
    {
        var folderProps = LookAndFeelProperties.getWriteableInstance(c);
        folderProps.setDefaultDateTimeFormat("'kevink' dd-MM-yyyy");
        folderProps.save();

        var multiValueColumn = "Outputs/Materials/" + sampleTypeName + "/Created";
        var url = new ActionURL("query", "selectRows", c);
        url.addParameter(QueryParam.schemaName, getSampleSchema().getName());
        url.addParameter("query." + QueryParam.queryName, sampleTypeName);
        url.addParameter("query." + QueryParam.columns, "Name, " + multiValueColumn);
        url.addFilter("query", FieldKey.fromParts("Name"), CompareType.EQUAL, "A");
        url.addParameter("includeMetadata", false);
        url.addParameter("apiVersion", "17.1");

        var response = ViewServlet.GET(url, user, null);
        assertEquals(200, response.getStatus());

        var json = JsonUtil.DEFAULT_MAPPER.readTree(response.getContentAsString());
        var resultRows = json.get("rows");
        assertEquals(1, resultRows.size());

        var createdValues = resultRows.get(0).get("data").get(multiValueColumn);
        assertEquals(4, createdValues.size());

        for (var data : createdValues)
        {
            var value = data.get("value").asText();
            assertNotNull(value);

            // Formatted with container date format
            var formattedValue = data.get("formattedValue").asText();
            assertTrue("Expected date format not applied", formattedValue.startsWith("kevink "));

            // Do not care what the JSON format looks like, as long as it is different
            assertNotEquals(value, formattedValue);
        }

        folderProps.clearDefaultDateTimeFormat();
        folderProps.save();
    }
}

@Test
public void testSampleTypeWithVocabularyProperties() throws Exception
{
    User user = TestContext.get().getUser();

    String sampleTypeName = "SamplesWithVocabularyProperties";
    String sampleType = "TypeA";
    String updatedSampleType = "TypeB";
    String sampleColor = "Blue";
    int sampleAge = 5;

    Domain mockDomain = helper.createVocabularyTestDomain(user, c);
    Map<String, String> vocabularyPropertyURIs = helper.getVocabularyPropertyURIS(mockDomain);

    // create a sample type
    createSampleType(sampleTypeName, List.of(new GWTPropertyDescriptor("name", "string")), null);

    // insert a sample
    var sampleName = "TestSample";
    ArrayListMap<String, Object> row = new ArrayListMap<>();
    row.put("name", sampleName);
    row.put(vocabularyPropertyURIs.get(helper.typePropertyName), sampleType);
    row.put(vocabularyPropertyURIs.get(helper.colorPropertyName), sampleColor);
    row.put(vocabularyPropertyURIs.get(helper.agePropertyName), null); // inserting a property with null value
    List<Map<String, Object>> rows = helper.buildRows(row);

    UserSchema schema = getSampleSchema();
    var insertedSample = helper.insertRows(c, rows, sampleTypeName, schema).getFirst();
    var sampleLsid = insertedSample.get("LSID").toString();
    var sampleRowId = insertedSample.get("RowId");

    assertEquals("Custom Property is not inserted", sampleType,
            OntologyManager.getPropertyObjects(c, sampleLsid).get(vocabularyPropertyURIs.get(helper.typePropertyName)).getStringValue());

    // Verifying property with null value is not inserted
    assertEquals("Property with null value is present.", 0, OntologyManager.getPropertyObjects(c, vocabularyPropertyURIs.get(helper.agePropertyName)).size());

    // update inserted sample
    ArrayListMap<String, Object> rowToUpdate = new ArrayListMap<>();
    rowToUpdate.put("name", sampleName);
    rowToUpdate.put("RowId", sampleRowId);
    rowToUpdate.put(vocabularyPropertyURIs.get(helper.typePropertyName), updatedSampleType);
    rowToUpdate.put(vocabularyPropertyURIs.get(helper.colorPropertyName), null); // nulling out existing property
    rowToUpdate.put(vocabularyPropertyURIs.get(helper.agePropertyName), sampleAge); //inserting a new property in update rows
    List<Map<String, Object>> rowsToUpdate = helper.buildRows(rowToUpdate);

    List<Map<String, Object>> oldKeys = new ArrayList<>();
    ArrayListMap<String, Object> oldKey = new ArrayListMap<>();
    oldKey.put("name", sampleName);
    oldKey.put("RowId", sampleRowId);
    oldKeys.add(oldKey);

    helper.updateRows(c, rowsToUpdate, oldKeys, sampleTypeName, schema);
    assertEquals("Custom Property is not updated", updatedSampleType,
            OntologyManager.getPropertyObjects(c, sampleLsid).get(vocabularyPropertyURIs.get(helper.typePropertyName)).getStringValue());

    // Verify property updated to a null value gets deleted
    assertEquals("Property with null value is present.", 0, OntologyManager.getPropertyObjects(c, vocabularyPropertyURIs.get(helper.colorPropertyName)).size());

    // Verify property inserted during update rows in inserted
    assertEquals("New Property is not inserted with update rows", sampleAge,
            OntologyManager.getPropertyObjects(c, sampleLsid).get(vocabularyPropertyURIs.get(helper.agePropertyName)).getFloatValue().intValue());
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
    final ExpSampleType st = createSampleType("SamplesDAL", props, null);

    BatchValidationException errors = new BatchValidationException();

    // insert a sample
    List<Map<String,Object>> ret = insertSampleRows(st.getName(), List.of(CaseInsensitiveHashMap.of("Name", "A1", "Measure", "Initial", "Value", 1.0)));
    assertEquals(1, ret.size());
    assertNotNull(ret.getFirst().get("rowid"));
    int rowid = (int) JdbcType.INTEGER.convert(ret.getFirst().get("rowid"));

    // check audit log
    SimpleFilter f = new SimpleFilter(new FieldKey(null, "RowId"), auditMaxRowid, CompareType.GT);
    List<SampleTimelineAuditEvent> events = AuditLogService.get().getAuditEvents(c, user, SampleTimelineAuditEvent.EVENT_TYPE, f, new Sort("-RowId"));
    assertFalse(events.isEmpty());
    assertNull(events.getFirst().getOldRecordMap());
    assertNotNull(events.getFirst().getNewRecordMap());
    Map<String,String> newRecordMap = new CaseInsensitiveHashMap<>(PageFlowUtil.mapFromQueryString(events.getFirst().getNewRecordMap()));
    assertEquals("Initial", newRecordMap.get("Measure"));
    assertEquals("1.0", newRecordMap.get("Value"));
    assertNull(newRecordMap.get("AliquotCount"));
    assertNull(newRecordMap.get("AliquotVolume"));
    assertNull(newRecordMap.get("AvailableAliquotVolume"));
    assertNull(newRecordMap.get("AvailableAliquotCount"));
    assertNull(newRecordMap.get("AliquotUnit"));

    // UPDATE
    updateSampleRows(st.getName(), List.of(CaseInsensitiveHashMap.of("RowId", rowid, "Measure", "Updated", "Value", 2.0)));

    // check audit log
    events = AuditLogService.get().getAuditEvents(c, user, SampleTimelineAuditEvent.EVENT_TYPE, f, new Sort("-RowId"));
    assertFalse(events.isEmpty());
    assertNotNull(events.getFirst().getOldRecordMap());
    Map<String,String> oldRecordMap = new CaseInsensitiveHashMap<>(PageFlowUtil.mapFromQueryString(events.getFirst().getOldRecordMap()));
    assertNotNull(events.getFirst().getNewRecordMap());
    newRecordMap = new CaseInsensitiveHashMap<>(PageFlowUtil.mapFromQueryString(events.getFirst().getNewRecordMap()));
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
    int count = mergeSampleRows(st.getName(), List.of(CaseInsensitiveHashMap.of("Name", "A1", "Measure", "Merged", "Value", 3.0)));
    assertEquals(1, count);

    // check audit log
    events = AuditLogService.get().getAuditEvents(c, user, SampleTimelineAuditEvent.EVENT_TYPE, f, new Sort("-RowId"));
    assertFalse(events.isEmpty());
    assertNotNull(events.getFirst().getOldRecordMap());
    oldRecordMap = new CaseInsensitiveHashMap<>(PageFlowUtil.mapFromQueryString(events.getFirst().getOldRecordMap()));
    assertNotNull(events.getFirst().getNewRecordMap());
    newRecordMap = new CaseInsensitiveHashMap<>(PageFlowUtil.mapFromQueryString(events.getFirst().getNewRecordMap()));
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
    // create a sample type
    ExpSampleType st = createSampleType("MySamples", List.of(new GWTPropertyDescriptor("name", "string")), null);

    // insert a sample
    List<Map<String,Object>> ret = insertSampleRows(st.getName(), List.of(CaseInsensitiveHashMap.of("name", "SampleInSampleType")));
    assertEquals(1, ret.size());
    assertNotNull(ret.getFirst().get("rowid"));
    long stSampleId = (long) JdbcType.BIGINT.convert(ret.getFirst().get("rowid"));

    // verify insert, update aren't allowed, but read and delete are allowed
    User user = TestContext.get().getUser();
    var schema = QueryService.get().getUserSchema(user, c, ExpSchema.SCHEMA_EXP);
    var materialsTable = schema.getTableOrThrow(ExpSchema.TableType.Materials.name());
    var qus = materialsTable.getUpdateService();
    assertNotNull(qus);
    assertTrue(qus.hasPermission(user, ReadPermission.class));
    assertTrue(qus.hasPermission(user, DeletePermission.class));
    assertFalse(qus.hasPermission(user, InsertPermission.class));
    assertFalse(qus.hasPermission(user, UpdatePermission.class));

    // create a sample outside a SampleType
    var lsid = ExperimentService.get().generateLSID(c, ExpMaterial.class, "SampleNotInSampleType");
    ExpMaterial m = ExperimentService.get().createExpMaterial(c, Lsid.parse(lsid));
    m.save(user);

    // verify we can't delete both samples when targeting the default 'Material' cpasType
    try
    {
        ExperimentServiceImpl.get().deleteMaterialByRowIds(
                user, c, List.of(stSampleId, m.getRowId()), true, null, false, false);
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
                user, c, List.of(stSampleId, m.getRowId()), true, st, false, false);
        fail("Expected to throw exception");
    }
    catch (Exception e)
    {
        assertThat(e.getMessage(), containsString("Error deleting '" + st.getName() + "' sample: 'SampleNotInSampleType' is in the sample type '" + ExpMaterial.DEFAULT_CPAS_TYPE + "'"));
    }

    // verify read via QUS
    var rows = qus.getRows(user, c, List.of(CaseInsensitiveHashMap.of("rowId", m.getRowId())));
    assertEquals("Failed to fetch material via QUS", 1, rows.size());
    assertEquals(m.getLSID(), rows.getFirst().get("lsid"));

    // verify delete via QUS
    rows = qus.deleteRows(user, c, List.of(CaseInsensitiveHashMap.of("rowId", m.getRowId())), null, null);
    assertEquals("Failed to delete material via QUS", 1, rows.size());
}

@Test
public void testInsertOptionUpdate() throws Exception
{
    final User user = TestContext.get().getUser();

    // create sample type
    List<GWTPropertyDescriptor> props = new ArrayList<>();
    props.add(new GWTPropertyDescriptor("name", "string"));
    props.add(new GWTPropertyDescriptor("intVal", "int"));

    String requiredColName = "RequiredCol";
    var requiredCol = new GWTPropertyDescriptor(requiredColName, "string");
    requiredCol.setRequired(true);
    props.add(requiredCol);

    String longFieldName = "Field100 ABCDEFGHIJKLMNOPQRSTUVWXYZ%()=+-[]_|*`'\":;<>?!@#^AABBCCDDEEFFGGHHIIJJKKLLMMNNOOPPQQRRSSTTU)";
    props.add(new GWTPropertyDescriptor(longFieldName, "string"));

    final String sampleTypeName = "TestSamplesWithRequired";
    ExpSampleType sampleType = createSampleType(sampleTypeName, props, null);
    TableInfo table = getSampleTypeTable(sampleTypeName);
    QueryUpdateService qus = getSampleTypeUpdateService(sampleTypeName);

    String longFieldAlias = table.getColumn(longFieldName).getAlias().getId();
    assertFalse("Unexpected long field alias", longFieldName.equalsIgnoreCase(longFieldAlias));

    // import samples
    List<Map<String, Object>> rowsToAdd = new ArrayList<>();
    rowsToAdd.add(CaseInsensitiveHashMap.of("name", "S-1", "intVal", 10, "AliquotedFrom", null, requiredColName, "a", longFieldName, "Very"));
    rowsToAdd.add(CaseInsensitiveHashMap.of("name", null, "intVal", null, "AliquotedFrom", "S-1", requiredColName, null, longFieldName, "Long"));
    rowsToAdd.add(CaseInsensitiveHashMap.of("name", "S-2", "intVal", 20, "AliquotedFrom", null, requiredColName, "b", longFieldName, "Field"));

    DataIteratorContext context = new DataIteratorContext();
    context.setInsertOption(QueryUpdateService.InsertOption.IMPORT);
    var count = qus.loadRows(user, c, MapDataIterator.of(rowsToAdd), context, null);

    assertFalse(context.getErrors().hasErrors());
    assertEquals("Unexpected count from IMPORT on loadRows()", 3, count);

    // Issue 53168: aliquot cannot be a parent to its aliquot parent
    BatchValidationException errors = new BatchValidationException();
    List<Map<String, Object>> rows = new ArrayList<>();
    rows.add(CaseInsensitiveHashMap.of("rowId", sampleType.getSample(c, "S-1").getRowId(), "MaterialInputs/" + sampleTypeName, "S-1-1"));
    try
    {
        qus.updateRows(user, c, rows, null, errors, null, null);
        fail("Expected to throw exception");
    }
    catch (Exception e)
    {
        assertThat(e.getMessage(), containsString("'S-1-1' is aliquoted from sample 'S-1'. Circular relationships are not allowed."));
    }

    rows = getSampleRows(sampleTypeName);
    assertEquals("S-1", rows.getFirst().get("name"));
    assertEquals(1, rows.getFirst().get("aliquotcount"));
    assertEquals(0.0, rows.getFirst().get("aliquotvolume"));
    assertEquals(0, rows.getFirst().get("availablealiquotcount"));
    assertEquals(0.0, rows.getFirst().get("availablealiquotvolume"));
    assertEquals("a", rows.get(0).get(requiredColName));
    assertEquals(String.format("Failed insert for field \"%s\"", longFieldName), "Very", rows.get(0).get(longFieldAlias));

    assertEquals("S-1-1", rows.get(1).get("name"));
    assertEquals(10, rows.get(1).get("intVal"));
    assertNull(rows.get(1).get("aliquotcount"));
    assertNull(rows.get(1).get("aliquotvolume"));
    assertNull(rows.get(1).get("availablealiquotcount"));
    assertNull(rows.get(1).get("availablealiquotvolume"));
    assertEquals("Expected aliquot parent values to be copied into aliquot", "a", rows.get(1).get(requiredColName));
    assertEquals("Expected aliquot parent values to be copied into aliquot", "Very", rows.get(1).get(longFieldAlias));

    assertEquals("S-2", rows.get(2).get("name"));
    assertEquals("b", rows.get(2).get(requiredColName));
    assertEquals(0, rows.get(2).get("aliquotcount"));
    assertEquals(0.0, rows.get(2).get("aliquotvolume"));
    assertEquals(0, rows.get(2).get("availablealiquotcount"));
    assertEquals(0.0, rows.get(2).get("availablealiquotvolume"));
    assertEquals(String.format("Failed insert for field \"%s\"", longFieldName), "Field", rows.get(2).get(longFieldAlias));

    // Update samples using data iterator
    // -- AliquotedFrom is not needed for update
    // -- Required fields can be absent for update
    List<Map<String, Object>> rowsToUpdate = new ArrayList<>();
    rowsToUpdate.add(CaseInsensitiveHashMap.of("name", "S-1", "intVal", 100));
    rowsToUpdate.add(CaseInsensitiveHashMap.of("name", "S-1-1", "intVal", null));
    rowsToUpdate.add(CaseInsensitiveHashMap.of("name", "S-2", "intVal", 200));

    context = new DataIteratorContext();
    context.setInsertOption(QueryUpdateService.InsertOption.UPDATE);
    count = qus.loadRows(user, c, MapDataIterator.of(rowsToUpdate), context, null);

    assertFalse(context.getErrors().hasErrors());
    assertEquals("Unexpected count from UPDATE on loadRows()", 3, count);

    rows = getSampleRows(sampleTypeName);
    // test existing row value is updated
    assertEquals(100, rows.getFirst().get("intVal"));
    assertEquals(1, rows.getFirst().get("aliquotcount"));
    assertEquals(0.0, rows.getFirst().get("aliquotvolume"));
    assertEquals(0, rows.getFirst().get("availablealiquotcount"));
    assertEquals(0.0, rows.get(0).get("availablealiquotvolume"));
    assertEquals(String.format("Data for field \"%s\" unexpectedly changed", longFieldName), "Very", rows.get(0).get(longFieldAlias));

    assertEquals(100, rows.get(1).get("intVal"));
    assertEquals("a", rows.get(1).get(requiredColName)); // absent columns are not blanked out
    final String aliquotedFromLSID = (String) rows.get(1).get("AliquotedFromLSID");
    assertEquals(true, rows.get(1).get("IsAliquot"));
    assertEquals(100, rows.get(1).get("intVal"));
    assertEquals(String.format("Data for field \"%s\" unexpectedly changed", longFieldName), "Very", rows.get(1).get(longFieldAlias));

    assertEquals(200, rows.get(2).get("intVal"));
    assertEquals("b", rows.get(2).get(requiredColName)); // absent columns are not blanked out
    assertEquals(0, rows.get(2).get("aliquotcount"));
    assertEquals(0.0, rows.get(2).get("aliquotvolume"));
    assertEquals(0, rows.get(2).get("availablealiquotcount"));
    assertEquals(0.0, rows.get(2).get("availablealiquotvolume"));
    assertEquals(String.format("Data for field \"%s\" unexpectedly changed", longFieldName), "Field", rows.get(2).get(longFieldAlias));

    // update a sample that doesn't exist should throw error
    rowsToUpdate = new ArrayList<>();
    rowsToUpdate.add(CaseInsensitiveHashMap.of("name", "S-1-absent", "intVal", 100));
    context = new DataIteratorContext();
    context.setInsertOption(QueryUpdateService.InsertOption.UPDATE);
    qus.loadRows(user, c, MapDataIterator.of(rowsToUpdate), context, null);
    assertTrue(context.getErrors().hasErrors());
    String msg = !context.getErrors().getRowErrors().isEmpty() ? context.getErrors().getRowErrors().getFirst().toString() : "no message";
    assertTrue(msg.contains("Sample does not exist: S-1-absent."));

    context = new DataIteratorContext();
    context.setInsertOption(QueryUpdateService.InsertOption.UPDATE);
    // with detailed audit turned on, checking for existing record should still work
    Map<Enum, Object> auditOptions = new HashMap<>();
    auditOptions.put(DetailedAuditLogDataIterator.AuditConfigs.AuditBehavior, AuditBehaviorType.DETAILED);
    context.setConfigParameters(auditOptions);
    qus.loadRows(user, c, MapDataIterator.of(rowsToUpdate), context, null);
    assertTrue(context.getErrors().hasErrors());
    msg = !context.getErrors().getRowErrors().isEmpty() ? context.getErrors().getRowErrors().getFirst().toString() : "no message";
    assertTrue(msg.contains("Sample does not exist: S-1-absent."));

    // AliquotedFrom is supplied but doesn't match the current aliquot status / parents should get ignored
    rowsToUpdate = new ArrayList<>();
    rowsToUpdate.add(CaseInsensitiveHashMap.of("name", "S-1", "intVal", 100, "AliquotedFrom", "S-2"));
    rowsToUpdate.add(CaseInsensitiveHashMap.of("name", "S-1-1", "intVal", null, "AliquotedFrom", "S-2"));
    rowsToUpdate.add(CaseInsensitiveHashMap.of("name", "S-2", "intVal", 200, "AliquotedFrom", "S-1"));

    context = new DataIteratorContext();
    context.setInsertOption(QueryUpdateService.InsertOption.UPDATE);
    qus.loadRows(user, c, MapDataIterator.of(rowsToUpdate), context, null);
    assertFalse(context.getErrors().hasErrors());
    assertEquals(3, count);
    rows = getSampleRows(sampleTypeName);
    assertNull(rows.get(0).get("AliquotedFromLSID"));
    assertEquals(aliquotedFromLSID, rows.get(1).get("AliquotedFromLSID"));
    assertNull(rows.get(2).get("AliquotedFromLSID"));
}

private ExpSampleType createSampleType(String sampleTypeName, List<GWTPropertyDescriptor> props, @Nullable String nameExpression) throws Exception
{
    return SampleTypeService.get().createSampleType(c, TestContext.get().getUser(), sampleTypeName, null, props, emptyList(), -1, -1, -1, -1, nameExpression, null);
}

private @NotNull UserSchema getSampleSchema()
{
    UserSchema schema = QueryService.get().getUserSchema(TestContext.get().getUser(), c, SamplesSchema.SCHEMA_SAMPLES);
    assertNotNull(schema);
    return schema;
}

private @NotNull TableInfo getSampleTypeTable(String sampleType)
{
    return getSampleSchema().getTableOrThrow(sampleType);
}

private @NotNull QueryUpdateService getSampleTypeUpdateService(String sampleType)
{
    var table = getSampleTypeTable(sampleType);
    var updateService = table.getUpdateService();
    assertNotNull("Update service cannot be null", updateService);
    return updateService;
}

private List<Map<String,Object>> getSampleRows(String sampleType)
{
    TableInfo table = getSampleTypeTable(sampleType);
    return Arrays.asList(new TableSelector(table, null, new Sort("Name")).getMapArray());
}

private List<Map<String, Object>> insertSampleRows(String sampleType, List<Map<String, Object>> rows) throws Exception
{
    BatchValidationException errors = new BatchValidationException();
    QueryUpdateService svc = getSampleTypeUpdateService(sampleType);
    List<Map<String, Object>> ret = svc.insertRows(TestContext.get().getUser(), c, rows, errors, null, null);
    if (errors.hasErrors())
        throw errors;
    return ret;
}

private int mergeSampleRows(String sampleType, List<Map<String, Object>> rows) throws Exception
{
    BatchValidationException errors = new BatchValidationException();
    QueryUpdateService svc = getSampleTypeUpdateService(sampleType);
    Map<Enum, Object> config = Map.of(DetailedAuditLogDataIterator.AuditConfigs.AuditBehavior, AuditBehaviorType.DETAILED);
    int count = svc.mergeRows(TestContext.get().getUser(), c, MapDataIterator.of(rows), errors, config, null);
    if (errors.hasErrors())
        throw errors;
    return count;
}

private List<Map<String, Object>> updateSampleRows(String sampleType, List<Map<String, Object>> rows) throws Exception
{
    BatchValidationException errors = new BatchValidationException();
    QueryUpdateService svc = getSampleTypeUpdateService(sampleType);
    Map<Enum, Object> config = Map.of(DetailedAuditLogDataIterator.AuditConfigs.AuditBehavior, AuditBehaviorType.DETAILED);
    List<Map<String, Object>> ret = svc.updateRows(TestContext.get().getUser(), c, rows, null, errors, config, null);
    if (errors.hasErrors())
        throw errors;
    return ret;
}
%>

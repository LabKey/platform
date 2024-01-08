<%@ page import="com.fasterxml.jackson.databind.JsonNode" %>
<%@ page import="com.fasterxml.jackson.databind.ObjectMapper" %>
<%@ page import="com.fasterxml.jackson.databind.node.ArrayNode" %>
<%@ page import="com.fasterxml.jackson.databind.node.ObjectNode" %>
<%@ page import="com.google.common.collect.ImmutableList" %>
<%@ page import="org.junit.After" %>
<%@ page import="org.junit.Assert" %>
<%@ page import="org.junit.Before" %>
<%@ page import="org.junit.Test" %>
<%@ page import="org.labkey.api.collections.CaseInsensitiveHashMap" %>
<%@ page import="org.labkey.api.data.CompareType" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.data.SimpleFilter" %>
<%@ page import="org.labkey.api.data.Table" %>
<%@ page import="org.labkey.api.data.TableInfo" %>
<%@ page import="org.labkey.api.exp.api.ExpMaterial" %>
<%@ page import="org.labkey.api.exp.api.ExperimentService" %>
<%@ page import="org.labkey.api.exp.query.ExpMaterialTable" %>
<%@ page import="org.labkey.api.module.Module" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.api.query.BatchValidationException" %>
<%@ page import="org.labkey.api.query.FieldKey" %>
<%@ page import="org.labkey.api.query.QueryParam" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.security.Group" %>
<%@ page import="org.labkey.api.security.SecurityManager" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.GUID" %>
<%@ page import="org.labkey.api.util.TestContext" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.ViewServlet" %>
<%@ page import="org.labkey.query.controllers.QueryController" %>
<%@ page import="org.springframework.mock.web.MockHttpServletResponse" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="static org.hamcrest.CoreMatchers.hasItems" %>
<%@ page import="static org.junit.Assert.assertThat" %>
<%@ page import="static org.junit.Assert.assertEquals" %>
<%@ page import="java.util.Set" %>
<%@ page import="static org.labkey.api.exp.query.ExpMaterialTable.Column.Alias" %>
<%@ page import="static org.labkey.api.exp.query.ExpMaterialTable.Column.Name" %>
<%@ page import="com.fasterxml.jackson.core.JsonPointer" %>
<%@ page import="org.labkey.api.study.MasterPatientIndexService" %>
<%@ page import="org.labkey.api.settings.FolderSettingsCache" %>
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="static org.junit.Assert.assertNotNull" %>
<%@ page import="static org.junit.Assert.assertTrue" %>
<%@ page import="org.labkey.api.util.JsonUtil" %>
<%@ page extends="org.labkey.api.jsp.JspTest.DRT" %>
<%!
    final String aliasPrefix = "MultiValueTest-";
    Container c;

    User getUser()
    {
        return TestContext.get().getUser();
    }

    @Before
    public void setUp()
    {
        // NOTE: We need to use a project to create new security groups
        c = ContainerManager.getForPath("_testMultiValue");
        if (c != null)
            ContainerManager.deleteAll(c, TestContext.get().getUser());
        c = ContainerManager.createContainer(ContainerManager.getRoot(), "_testMultiValue", TestContext.get().getUser());
    }

    @After
    public void tearDown()
    {
        ContainerManager.deleteAll(c, TestContext.get().getUser());

        Table.delete(ExperimentService.get().getTinfoAlias(), new SimpleFilter(FieldKey.fromParts("name"), aliasPrefix, CompareType.STARTS_WITH));
    }

    @Test
    public void testCoreGroups() throws Exception
    {
        // Add a user to multiple groups
        Group g1 = SecurityManager.createGroup(c, "group1");
        SecurityManager.addMember(g1, getUser());

        Group g2 = SecurityManager.createGroup(c, "group2");
        SecurityManager.addMember(g2, getUser());

        ActionURL url = new ActionURL(QueryController.SelectRowsAction.class, c);
        url.addParameter(QueryParam.schemaName, "core");
        url.addParameter(QueryParam.queryName, "Users");
        url.addParameter(QueryParam.columns, "Groups");
        url.addFilter("query", FieldKey.fromParts("userid"), CompareType.EQUAL, getUser().getUserId());
        url.addParameter("includeMetadata", false);
        // enable the multi-value array response
        url.addParameter("apiVersion", "16.2");

        MockHttpServletResponse resp = ViewServlet.GET(url, getUser(), null);
        String content = resp.getContentAsString();

        ObjectNode n = JsonUtil.DEFAULT_MAPPER.readValue(content, ObjectNode.class);
        Assert.assertEquals("Expected only one row", n.get("rowCount").asInt(), 1);
        ArrayNode rows = n.withArray("rows");

        JsonNode row0 = rows.get(0);
        ArrayNode row0groups = (ArrayNode)row0.at("/data/Groups");

        Integer group1Id = null;
        Integer group2Id = null;

        for (JsonNode node : row0groups)
        {
            if ("group1".equals(node.get("displayValue").asText()))
                group1Id = node.get("value").asInt();
            else if ("group2".equals(node.get("displayValue").asText()))
                group2Id = node.get("value").asInt();
        }

        Assert.assertNotNull("Expected to find group1 id", group1Id);
        Assert.assertNotNull("Expected to find group2 id", group2Id);
        Assert.assertNotEquals("Value should not be the wrapped userid", group1Id.intValue(), getUser().getUserId());
        Assert.assertNotEquals("Group id values should not match", group1Id.intValue(), group2Id.intValue());
    }

    @Test
    public void testReagentSpecies() throws Exception
    {
        Module module = ModuleLoader.getInstance().getModule("reagent");
        if (null == module)
            return;

        // enable reagent
        Set<Module> active = new HashSet<>(c.getActiveModules());
        active.add(module);
        c.setActiveModules(active);

        //
        // SETUP - insert a reagent with a multi-value species junction
        //

        // insert an Antigen
        BatchValidationException errors = new BatchValidationException();
        TableInfo antigensTable = QueryService.get().getUserSchema(getUser(), c, "reagent").getTable("antigens");
        List<Map<String, Object>> antigenRows = ImmutableList.of(
                CaseInsensitiveHashMap.of("name", "test")
        );
        List<Map<String, Object>> insertedAntigens = antigensTable.getUpdateService().insertRows(getUser(), c, antigenRows, errors, null, null);
        throwErrors(errors);
        Integer antigenId = (Integer)insertedAntigens.get(0).get("rowId");

        // insert a Label
        errors = new BatchValidationException();
        TableInfo labelsTable = QueryService.get().getUserSchema(getUser(), c, "reagent").getTable("labels");
        List<Map<String, Object>> labelRows = ImmutableList.of(
                CaseInsensitiveHashMap.of("name", "test")
        );
        List<Map<String, Object>> insertedLabels = labelsTable.getUpdateService().insertRows(getUser(), c, labelRows, errors, null, null);
        throwErrors(errors);
        Integer labelId = (Integer)insertedLabels.get(0).get("rowId");

        // insert Species
        errors = new BatchValidationException();
        TableInfo speciesTable = QueryService.get().getUserSchema(getUser(), c, "reagent").getTable("species");
        List<Map<String, Object>> speciesRows = ImmutableList.of(
                CaseInsensitiveHashMap.of("name", "foo1"),
                CaseInsensitiveHashMap.of("name", "foo2"),
                CaseInsensitiveHashMap.of("name", "foo3"),
                CaseInsensitiveHashMap.of("name", "foo4")
        );
        List<Map<String, Object>> insertedSpecies = speciesTable.getUpdateService().insertRows(getUser(), c, speciesRows, errors, null, null);
        throwErrors(errors);
        Integer speciesId1 = (Integer)insertedSpecies.get(0).get("rowId");
        Integer speciesId2 = (Integer)insertedSpecies.get(1).get("rowId");
        Integer speciesId3 = (Integer)insertedSpecies.get(2).get("rowId");
        Integer speciesId4 = (Integer)insertedSpecies.get(3).get("rowId");

        // insert Reagent
        errors = new BatchValidationException();
        TableInfo reagentsTable = QueryService.get().getUserSchema(getUser(), c, "reagent").getTable("reagents");
        List<Map<String, Object>> reagentRows = ImmutableList.of(
                CaseInsensitiveHashMap.of("antigenId", antigenId, "labelId", labelId, "clone", "foo")
        );
        List<Map<String, Object>> insertedReagents = reagentsTable.getUpdateService().insertRows(getUser(), c, reagentRows, errors, null, null);
        throwErrors(errors);
        Integer reagentId = (Integer)insertedReagents.get(0).get("rowId");

        // insert ReagentSpecies
        errors = new BatchValidationException();
        TableInfo reagentSpeciesTable = QueryService.get().getUserSchema(getUser(), c, "reagent").getTable("reagentSpecies");
        List<Map<String, Object>> reagentSpeciesRows = ImmutableList.of(
                CaseInsensitiveHashMap.of("reagentId", reagentId, "speciesId", speciesId1),
                CaseInsensitiveHashMap.of("reagentId", reagentId, "speciesId", speciesId2),
                CaseInsensitiveHashMap.of("reagentId", reagentId, "speciesId", speciesId3),
                CaseInsensitiveHashMap.of("reagentId", reagentId, "speciesId", speciesId4)
        );
        List<Map<String, Object>> insertedReagentSpecies = reagentSpeciesTable.getUpdateService().insertRows(getUser(), c, reagentSpeciesRows, errors, null, null);
        throwErrors(errors);

        //
        // VERIFY - query Reagent table with Species multi-value column
        //

        ActionURL selectUrl = new ActionURL(QueryController.SelectRowsAction.class, c);
        selectUrl.addParameter(QueryParam.schemaName, "reagent");
        selectUrl.addParameter(QueryParam.queryName, "Reagents");
        selectUrl.addParameter(QueryParam.columns, "Species");
        selectUrl.addFilter("query", FieldKey.fromParts("reagentId"), CompareType.EQUAL, reagentId);
        selectUrl.addParameter("includeMetadata", false);
        // enable the multi-value array response
        selectUrl.addParameter("apiVersion", "16.2");

        MockHttpServletResponse resp = ViewServlet.GET(selectUrl, getUser(), null);
        String content = resp.getContentAsString();

        ObjectNode n = JsonUtil.DEFAULT_MAPPER.readValue(content, ObjectNode.class);
        Assert.assertEquals("Expected only one row", n.get("rowCount").asInt(), 1);
        ArrayNode rows = n.withArray("rows");

        JsonNode row0 = rows.get(0);
        ArrayNode row0species = (ArrayNode)row0.at("/data/Species");

        Integer verifySpecies1 = null;
        Integer verifySpecies2 = null;
        Integer verifySpecies3 = null;
        Integer verifySpecies4 = null;

        for (JsonNode node : row0species)
        {
            if ("foo1".equals(node.get("displayValue").asText()))
                verifySpecies1 = node.get("value").asInt();
            else if ("foo2".equals(node.get("displayValue").asText()))
                verifySpecies2 = node.get("value").asInt();
            else if ("foo3".equals(node.get("displayValue").asText()))
                verifySpecies3 = node.get("value").asInt();
            else if ("foo4".equals(node.get("displayValue").asText()))
                verifySpecies4 = node.get("value").asInt();
        }

        Assert.assertNotNull("Expected to find species1 id", verifySpecies1);
        Assert.assertNotNull("Expected to find species2 id", verifySpecies2);
        Assert.assertNotNull("Expected to find species3 id", verifySpecies3);
        Assert.assertNotNull("Expected to find species4 id", verifySpecies4);
        Assert.assertEquals(speciesId1, verifySpecies1);
        Assert.assertEquals(speciesId2, verifySpecies2);
        Assert.assertEquals(speciesId3, verifySpecies3);
        Assert.assertEquals(speciesId4, verifySpecies4);
    }

    // Issue 43241: SM: Display of dates from Input columns for sample types does not use project date format
    @Test
    public void testSampleAlias() throws Exception
    {
        //
        // SETUP - create a Sample with >1 alias
        //

        // set a date format
        var props = LookAndFeelProperties.getWriteableInstance(c);
        props.setDefaultDateTimeFormat("'kevink' dd-MM-yyyy");
        props.save();

        String materialLsid = ExperimentService.get().generateLSID(c, ExpMaterial.class, "TestMaterial");
        var material = ExperimentService.get().createExpMaterial(c, materialLsid, "TestMaterial");
        material.save(getUser());

        // insert some aliases
        var alias1 = Table.insert(getUser(), ExperimentService.get().getTinfoAlias(), CaseInsensitiveHashMap.of("name", aliasPrefix + "1-" + new GUID()));
        var alias2 = Table.insert(getUser(), ExperimentService.get().getTinfoAlias(), CaseInsensitiveHashMap.of("name", aliasPrefix + "2-" + new GUID()));

        // insert sample -> alias junction entries
        ExperimentService.get().getTinfoMaterialAliasMap();
        Table.insert(getUser(), ExperimentService.get().getTinfoMaterialAliasMap(), CaseInsensitiveHashMap.of(
                "container", c.getEntityId(),
                "alias", alias1.get("rowId"),
                "lsid", materialLsid
        ));
        Table.insert(getUser(), ExperimentService.get().getTinfoMaterialAliasMap(), CaseInsensitiveHashMap.of(
                "container", c.getEntityId(),
                "alias", alias2.get("rowId"),
                "lsid", materialLsid
        ));

        // verify we have set up correctly
        var aliases = material.getAliases();
        assertThat(aliases, hasItems(alias1.get("name"), alias2.get("name")));

        //
        // VERIFY - query the aliases and verify the date is formatted correctly
        //

        ActionURL selectUrl = new ActionURL(QueryController.SelectRowsAction.class, c);
        selectUrl.addParameter(QueryParam.schemaName, "exp");
        selectUrl.addParameter(QueryParam.queryName, "Materials");
        selectUrl.addParameter("query." + QueryParam.columns, "Alias/Created");
        selectUrl.addFilter("query", FieldKey.fromParts("rowId"), CompareType.EQUAL, material.getRowId());
        selectUrl.addParameter("includeMetadata", false);
        // enable the multi-value array response and "formattedValue"
        selectUrl.addParameter("apiVersion", "17.1");

        MockHttpServletResponse resp = ViewServlet.GET(selectUrl, getUser(), null);
        Assert.assertEquals(200, resp.getStatus());
        String content = resp.getContentAsString();
        //System.out.println("query response:\n" + content);

        ObjectNode n = JsonUtil.DEFAULT_MAPPER.readValue(content, ObjectNode.class);
        Assert.assertEquals("Expected only one row", n.get("rowCount").asInt(), 1);
        ArrayNode rows = n.withArray("rows");

        JsonNode row0 = rows.get(0);
        // JSON Pointer uses "~1" to encode "/" in "Alias/Created"
        ArrayNode row0aliases = (ArrayNode)row0.at("/data/Alias~1Created");
        Assert.assertEquals("Expected two aliases, got:\n" + row0aliases,
                2, row0aliases.size());

        for (var row0alias : row0aliases)
        {
            // JSON formatted date value
            String value = row0alias.get("value").asText();
            Assert.assertNotNull(value);

            // formatted with container date format
            String formattedValue = row0alias.get("formattedValue").asText();
            Assert.assertTrue("Expected date format to be applied, got: " + formattedValue,
                    formattedValue.startsWith("kevink "));

            // don't care what the json format looks like, as long as it is different
            Assert.assertNotEquals(value, formattedValue);
        }

    }

    private void throwErrors(BatchValidationException errors) throws BatchValidationException
    {
        if (errors.hasErrors())
        {
            throw errors;
        }
    }

%>

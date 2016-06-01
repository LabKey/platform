package org.labkey.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewServlet;
import org.labkey.query.controllers.QueryController;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: kevink
 * Date: 5/27/16
 */
public class MultiValueTest
{
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
        c = ContainerManager.createContainer(ContainerManager.getRoot(), "_testMultiValue");
    }

    @After
    public void tearDown()
    {
        ContainerManager.deleteAll(c, TestContext.get().getUser());
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

        ObjectMapper om = new ObjectMapper();
        ObjectNode n = om.readValue(content, ObjectNode.class);
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
        List<Map<String, Object>> insertedAntigens = antigensTable.getUpdateService().insertRows(getUser(), c, Arrays.asList(new CaseInsensitiveHashMap<>(Collections.singletonMap("name", "test"))), errors, null, null);
        Assert.assertFalse(errors.hasErrors());
        Integer antigenId = (Integer)insertedAntigens.get(0).get("rowId");

        // insert a Label
        errors = new BatchValidationException();
        TableInfo labelsTable = QueryService.get().getUserSchema(getUser(), c, "reagent").getTable("labels");
        List<Map<String, Object>> insertedLabels = labelsTable.getUpdateService().insertRows(getUser(), c, Arrays.asList(new CaseInsensitiveHashMap<>(Collections.singletonMap("name", "test"))), errors, null, null);
        Assert.assertFalse(errors.hasErrors());
        Integer labelId = (Integer)insertedLabels.get(0).get("rowId");

        // insert Species
        errors = new BatchValidationException();
        TableInfo speciesTable = QueryService.get().getUserSchema(getUser(), c, "reagent").getTable("species");
        CaseInsensitiveHashMap<Object> row1 = new CaseInsensitiveHashMap<>();
        row1.put("name", "foo1");
        CaseInsensitiveHashMap<Object> row2 = new CaseInsensitiveHashMap<>();
        row2.put("name", "foo2");
        List<Map<String, Object>> insertedSpecies = speciesTable.getUpdateService().insertRows(getUser(), c, Arrays.asList(row1, row2), errors, null, null);
        Assert.assertFalse(errors.hasErrors());
        Integer speciesId1 = (Integer)insertedSpecies.get(0).get("rowId");
        Integer speciesId2 = (Integer)insertedSpecies.get(1).get("rowId");

        // insert Reagent
        errors = new BatchValidationException();
        TableInfo reagentsTable = QueryService.get().getUserSchema(getUser(), c, "reagent").getTable("reagents");
        CaseInsensitiveHashMap<Object> row = new CaseInsensitiveHashMap<>();
        row.put("antigenId", antigenId);
        row.put("labelId", labelId);
        row.put("clone", "foo");
        List<Map<String, Object>> insertedReagents = reagentsTable.getUpdateService().insertRows(getUser(), c, Arrays.asList(row), errors, null, null);
        Assert.assertFalse(errors.hasErrors());
        Integer reagentId = (Integer)insertedReagents.get(0).get("rowId");

        // insert ReagentSpecies
        errors = new BatchValidationException();
        TableInfo reagentSpeciesTable = QueryService.get().getUserSchema(getUser(), c, "reagent").getTable("reagentSpecies");
        row1 = new CaseInsensitiveHashMap<>();
        row1.put("reagentId", reagentId);
        row1.put("speciesId", speciesId1);
        row2 = new CaseInsensitiveHashMap<>();
        row2.put("reagentId", reagentId);
        row2.put("speciesId", speciesId2);
        List<Map<String, Object>> insertedReagentSpecies = reagentSpeciesTable.getUpdateService().insertRows(getUser(), c, Arrays.asList(row1, row2), errors, null, null);
        Assert.assertFalse(errors.hasErrors());

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

        ObjectMapper om = new ObjectMapper();
        ObjectNode n = om.readValue(content, ObjectNode.class);
        Assert.assertEquals("Expected only one row", n.get("rowCount").asInt(), 1);
        ArrayNode rows = n.withArray("rows");

        JsonNode row0 = rows.get(0);
        ArrayNode row0species = (ArrayNode)row0.at("/data/Species");

        Integer verifySpecies1 = null;
        Integer verifySpecies2 = null;

        for (JsonNode node : row0species)
        {
            if ("foo1".equals(node.get("displayValue").asText()))
                verifySpecies1 = node.get("value").asInt();
            else if ("foo2".equals(node.get("displayValue").asText()))
                verifySpecies2 = node.get("value").asInt();
        }

        Assert.assertNotNull("Expected to find species1 id", verifySpecies1);
        Assert.assertNotNull("Expected to find species2 id", verifySpecies2);
        Assert.assertEquals(speciesId1, verifySpecies1);
        Assert.assertEquals(speciesId2, verifySpecies2);
        Assert.assertNotEquals("Value should not be the wrapped column value", verifySpecies1.intValue(), reagentId.intValue());

    }

}

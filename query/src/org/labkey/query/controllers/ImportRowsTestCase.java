/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.query.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.settings.OptionalFeatureService;
import org.labkey.api.test.TestTimeout;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ViewServlet;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;
import java.util.Map;

/** GH Issue 1867: importRows rejects rows that differ in which fields they supply, and no command loses values when rows case a key differently */
@TestTimeout(TestTimeout.DEFAULT * 5) // Each test creates and deletes a project
public class ImportRowsTestCase extends Assert
{
    private static final String PROJECT_NAME = "ImportRowsTestProject";
    private static final String LIST_NAME = "ImportRowsTestList";

    @Before
    public void doSetup() throws Exception
    {
        doCleanup();
        User user = TestContext.get().getUser();
        Container project = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT_NAME, user);
        ContainerManager.updateSearchable(project, false, user);

        ListDefinition ld = ListService.get().createList(project, LIST_NAME, ListDefinition.KeyType.Varchar);
        Domain domain = ld.getDomain();
        assertNotNull(domain);
        domain.addProperty(new PropertyStorageSpec("Name", JdbcType.VARCHAR));
        domain.addProperty(new PropertyStorageSpec("Description", JdbcType.VARCHAR));
        domain.addProperty(new PropertyStorageSpec("Volume", JdbcType.INTEGER));
        ld.setKeyName("Name");
        ld.save(user);
    }

    @After
    public void doCleanup()
    {
        Container project = ContainerManager.getForPath(PROJECT_NAME);
        if (project != null)
            ContainerManager.deleteAll(project, TestContext.get().getUser());
    }

    @Test
    public void testRaggedImportRowsRejected() throws Exception
    {
        MockHttpServletResponse response = postSaveRows("importRows", List.of(
                Map.of("Name", "S1"),
                Map.of("Name", "S2", "Description", "x", "Volume", 5)));

        assertNotEquals("Ragged importRows should fail", HttpServletResponse.SC_OK, response.getStatus());
        String body = response.getContentAsString();
        assertTrue("Error should name the row and the differing fields: " + body, body.contains("rows[1]") && body.contains("Description") && body.contains("Volume"));
        assertEquals("Incorrect row count", 0L, new TableSelector(getTable()).getRowCount());
    }

    @Test
    public void testRaggedImportRowsAllowedByFlag() throws Exception
    {
        OptionalFeatureService svc = OptionalFeatureService.get();
        boolean wasEnabled = svc.isFeatureEnabled(QueryService.DEPRECATED_ALLOW_RAGGED_IMPORT_ROWS);
        svc.setFeatureEnabled(QueryService.DEPRECATED_ALLOW_RAGGED_IMPORT_ROWS, true, TestContext.get().getUser());
        try
        {
            saveRows("importRows", List.of(
                    Map.of("Name", "S1"),
                    Map.of("Name", "S2", "Description", "x", "Volume", 5)));
        }
        finally
        {
            svc.setFeatureEnabled(QueryService.DEPRECATED_ALLOW_RAGGED_IMPORT_ROWS, wasEnabled, TestContext.get().getUser());
        }

        assertEquals("Incorrect row count", 2L, new TableSelector(getTable()).getRowCount());
        assertValue("S1", "Description", null);
        assertValue("S2", "Description", "x");
        assertValue("S2", "Volume", 5);
    }

    @Test
    public void testMixedKeyCasingImportRows() throws Exception
    {
        assertMixedKeyCasing("importRows");
    }

    @Test
    public void testMixedKeyCasingInsert() throws Exception
    {
        assertMixedKeyCasing("insert");
    }

    @Test
    public void testMixedKeyCasingUpdate() throws Exception
    {
        saveRows("insert", List.of(
                Map.of("Name", "S1", "Description", "a"),
                Map.of("Name", "S2", "Description", "b")));

        saveRows("update", List.of(
                Map.of("Name", "S1", "Description", "a2"),
                Map.of("Name", "S2", "description", "b2")));

        assertValue("S1", "Description", "a2");
        assertValue("S2", "Description", "b2");
    }

    private void assertMixedKeyCasing(String command) throws Exception
    {
        saveRows(command, List.of(
                Map.of("Name", "S1", "Description", "a"),
                Map.of("Name", "S2", "description", "b")));

        assertValue("S1", "Description", "a");
        assertValue("S2", "Description", "b");
    }

    private static @NotNull Container getProject()
    {
        Container project = ContainerManager.getForPath(PROJECT_NAME);
        assertNotNull(project);
        return project;
    }

    private static @NotNull TableInfo getTable()
    {
        ListDefinition ld = ListService.get().getList(getProject(), LIST_NAME);
        assertNotNull(ld);
        TableInfo table = ld.getTable(TestContext.get().getUser());
        assertNotNull(table);
        return table;
    }

    private static void saveRows(String commandName, List<Map<String, Object>> rows) throws Exception
    {
        MockHttpServletResponse response = postSaveRows(commandName, rows);
        assertEquals(commandName + " via saveRows failed: " + response.getContentAsString(), HttpServletResponse.SC_OK, response.getStatus());
    }

    private static MockHttpServletResponse postSaveRows(String commandName, List<Map<String, Object>> rows) throws Exception
    {
        JSONObject command = new JSONObject();
        command.put("command", commandName);
        command.put("schemaName", "lists");
        command.put("queryName", LIST_NAME);
        command.put("rows", new JSONArray(rows));

        JSONObject json = new JSONObject();
        json.put("commands", List.of(command));

        HttpServletRequest request = ViewServlet.mockRequest(RequestMethod.POST.name(),
                DetailsURL.fromString("/query/saveRows.view").copy(getProject()).getActionURL(), TestContext.get().getUser(),
                Map.of("Content-Type", "application/json"), json.toString());
        return ViewServlet.mockDispatch(request, null);
    }

    private static void assertValue(String key, String column, @Nullable Object expected)
    {
        Class<?> type = null == expected ? Object.class : expected.getClass();
        Object actual = new TableSelector(getTable(), PageFlowUtil.set(column)).getObject(key, type);
        assertEquals(column + " of " + key, expected, actual);
    }
}

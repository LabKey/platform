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
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MultiChoice;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.test.TestTimeout;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ViewServlet;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** GH Issue 1867: inserts must keep each row's values, and give each omitted column its default, when rows differ in which keys they supply or how they case them */
@TestTimeout(TestTimeout.DEFAULT * 5) // Each test creates and deletes a project
public class ImportRowsTestCase extends Assert
{
    private static final String PROJECT_NAME = "ImportRowsTestProject";
    private static final String LIST_NAME = "ImportRowsSparseList";
    private static final String STATUS_DEFAULT = "Pending";

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
        // Provisioned as a database DEFAULT, the only kind of default API inserts honor
        domain.addProperty(new PropertyStorageSpec("Status", JdbcType.VARCHAR, 50, true, STATUS_DEFAULT));
        // Multi-choice columns are provisioned NOT NULL DEFAULT '{}'; the dialect rejects any other default for ARRAY
        domain.addProperty(new PropertyStorageSpec("Tags", JdbcType.ARRAY)).setRangeURI(PropertyType.MULTI_CHOICE.getTypeUri());
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
    public void testKeysAbsentFromFirstRow() throws Exception
    {
        saveRows("importRows", List.of(
                Map.of("Name", "S1"),
                Map.of("Name", "S2", "Description", "x", "Volume", 5)));

        assertEquals("Incorrect row count", 2L, new TableSelector(getTable()).getRowCount());
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

    @Test
    public void testOmittedColumnDefaultImportRows() throws Exception
    {
        assertOmittedColumnDefault("importRows");
    }

    @Test
    public void testOmittedColumnDefaultInsert() throws Exception
    {
        assertOmittedColumnDefault("insert");
    }

    @Test
    public void testOmittedColumnDefaultInsertWithKeys() throws Exception
    {
        saveRows("insertWithKeys", List.of(Map.of("values", Map.of("Name", "S0"))));
        assertValue("S0", "Status", STATUS_DEFAULT);

        saveRows("insertWithKeys", List.of(
                Map.of("values", Map.of("Name", "S1")),
                Map.of("values", Map.of("Name", "S2", "Status", "Done"))));

        assertValue("S1", "Status", STATUS_DEFAULT);
        assertValue("S2", "Status", "Done");
    }

    @Test
    public void testOmittedColumnDefaultUpdateService() throws Exception
    {
        insertRows(List.of(row("Name", "S0")));
        assertValue("S0", "Status", STATUS_DEFAULT);

        insertRows(List.of(
                row("Name", "S1"),
                row("Name", "S2", "Status", "Done")));

        assertValue("S1", "Status", STATUS_DEFAULT);
        assertValue("S2", "Status", "Done");
    }

    @Test
    public void testExplicitNullUpdateService() throws Exception
    {
        insertRows(List.of(
                row("Name", "S1", "Status", null),
                row("Name", "S2", "Status", "Done", "Description", "x")));

        assertNull("Status of S1", new TableSelector(getTable(), PageFlowUtil.set("Status")).getObject("S1", String.class));
        assertValue("S2", "Status", "Done");
    }

    @Test
    public void testOmittedMultiChoiceDefault() throws Exception
    {
        saveRows("insert", List.of(Map.of("Name", "S0")));
        assertMultiChoiceValue("S0", List.of());

        saveRows("insert", List.of(
                Map.of("Name", "S1"),
                Map.of("Name", "S2", "Tags", List.of("a", "b"))));

        assertMultiChoiceValue("S1", List.of());
        assertMultiChoiceValue("S2", List.of("a", "b"));
    }

    private void assertMixedKeyCasing(String command) throws Exception
    {
        saveRows(command, List.of(
                Map.of("Name", "S1", "Description", "a"),
                Map.of("Name", "S2", "description", "b")));

        assertValue("S1", "Description", "a");
        assertValue("S2", "Description", "b");
    }

    private void assertOmittedColumnDefault(String command) throws Exception
    {
        // Proves the database default exists, so a mixed-request failure below is the bug, not the setup
        saveRows(command, List.of(Map.of("Name", "S0")));
        assertValue("S0", "Status", STATUS_DEFAULT);

        saveRows(command, List.of(
                Map.of("Name", "S1"),
                Map.of("Name", "S2", "Status", "Done")));

        assertValue("S1", "Status", STATUS_DEFAULT);
        assertValue("S2", "Status", "Done");
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
        MockHttpServletResponse response = ViewServlet.mockDispatch(request, null);
        assertEquals(commandName + " via saveRows failed: " + response.getContentAsString(), HttpServletResponse.SC_OK, response.getStatus());
    }

    private static void insertRows(List<Map<String, Object>> rows) throws Exception
    {
        User user = TestContext.get().getUser();
        QueryUpdateService qus = getTable().getUpdateService();
        assertNotNull(qus);
        BatchValidationException errors = new BatchValidationException();
        qus.insertRows(user, getProject(), new ArrayList<>(rows), errors, null, null);
        if (errors.hasErrors())
            throw errors;
    }

    /** Unlike Map.of, allows null values */
    private static Map<String, Object> row(Object... keysAndValues)
    {
        Map<String, Object> row = new CaseInsensitiveHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2)
            row.put((String) keysAndValues[i], keysAndValues[i + 1]);
        return row;
    }

    private static void assertMultiChoiceValue(String key, List<String> expected)
    {
        Object actual = new TableSelector(getTable(), PageFlowUtil.set("Tags")).getObject(key, Object.class);
        // The multi-choice converter maps null to an empty array, so check the raw value before converting
        assertNotNull("Tags of " + key, actual);
        assertEquals("Tags of " + key, MultiChoice.Converter.getInstance().convert(MultiChoice.Array.class, expected),
                MultiChoice.Converter.getInstance().convert(MultiChoice.Array.class, actual));
    }

    private static void assertValue(String key, String column, @NotNull Object expected)
    {
        Object actual = new TableSelector(getTable(), PageFlowUtil.set(column)).getObject(key, expected.getClass());
        assertEquals(column + " of " + key, expected, actual);
    }
}

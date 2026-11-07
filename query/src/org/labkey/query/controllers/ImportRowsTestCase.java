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
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ViewServlet;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;
import java.util.Map;

/** GH Issue 1867: importRows must import every row's values, not just the first row's columns */
public class ImportRowsTestCase extends Assert
{
    private static final String PROJECT_NAME = "ImportRowsTestProject";
    private static final String LIST_NAME = "ImportRowsSparseList";

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
    public void testKeysAbsentFromFirstRow() throws Exception
    {
        User user = TestContext.get().getUser();
        Container project = ContainerManager.getForPath(PROJECT_NAME);
        assertNotNull(project);

        JSONArray rows = new JSONArray();
        rows.put(Map.of("Name", "S1"));
        rows.put(Map.of("Name", "S2", "Description", "x", "Volume", 5));

        JSONObject command = new JSONObject();
        command.put("command", "importRows");
        command.put("schemaName", "lists");
        command.put("queryName", LIST_NAME);
        command.put("rows", rows);

        JSONObject json = new JSONObject();
        json.put("commands", List.of(command));

        HttpServletRequest request = ViewServlet.mockRequest(RequestMethod.POST.name(),
                DetailsURL.fromString("/query/saveRows.view").copy(project).getActionURL(), user,
                Map.of("Content-Type", "application/json"), json.toString());
        MockHttpServletResponse response = ViewServlet.mockDispatch(request, null);
        assertEquals("saveRows failed: " + response.getContentAsString(), HttpServletResponse.SC_OK, response.getStatus());

        ListDefinition ld = ListService.get().getList(project, LIST_NAME);
        assertNotNull(ld);
        TableInfo table = ld.getTable(user);
        assertNotNull(table);
        assertEquals("Incorrect row count", 2L, new TableSelector(table).getRowCount());
        assertEquals("Description of S2", "x", new TableSelector(table, PageFlowUtil.set("Description")).getObject("S2", String.class));
        assertEquals("Volume of S2", Integer.valueOf(5), new TableSelector(table, PageFlowUtil.set("Volume")).getObject("S2", Integer.class));
    }
}

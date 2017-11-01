/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

package org.labkey.api.data;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.action.ApiJsonWriter;
import org.labkey.api.action.ApiResponseWriter;
import org.labkey.api.action.ExtendedApiQueryResponse;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BindException;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * User: adam
 * Date: Apr 27, 2006
 * Time: 10:21:24 AM
 */
@TestWhen(TestWhen.When.BVT)
public class ContainerDisplayColumn extends DataColumn
{
    public static final DisplayColumnFactory FACTORY = colInfo -> new ContainerDisplayColumn(colInfo, false, true);

    private final boolean _showPath;
    private final boolean _boundColHasEntityId;

    /**
     * @param showPath if true, show the container's full path. If false, show just its name
     */
    public ContainerDisplayColumn(ColumnInfo col, boolean showPath)
    {
        this(col, showPath, false);
    }

    /**
     * @param showPath if true, show the container's full path. If false, show just its name
     * @param boundColHasEntityId if true, the value of this column will be used as the entityId.  If not, it will resolve to the containers table (for example, container/EntityId)
     */
    public ContainerDisplayColumn(ColumnInfo col, boolean showPath, boolean boundColHasEntityId)
    {
        super(col);
        _showPath = showPath;
        _boundColHasEntityId = boundColHasEntityId;
    }

    @Override
    public Object getJsonValue(RenderContext ctx)
    {
        Container c = getContainer(ctx);
        if (c == null)
            return null;

        Object result = super.getJsonValue(ctx);

        return _showPath ? c.getPath() : result;
    }

    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        // Get the container for this row
        Container c = getContainer(ctx);

        if (c == null)
        {
            String id = getEntityIdValue(ctx);
            if(id != null)
                return "<deleted>";
            else if (getEntityIdFieldKey(ctx) != null && id == null)
                return "";
            else
                return "<could not resolve container>";
        }
        return _showPath ? c.getPath() : c.getTitle();
    }

    //NOTE: custom SQL statements may not container a column named entityId, so we fall back to container if entityId is absent
    private List<FieldKey> getEntityIdFieldKeys()
    {
        List<FieldKey> keys = new ArrayList<>();

        if(_boundColHasEntityId)
        {
            keys.add(getBoundColumn().getFieldKey());
        }
        else
        {
            keys.add(new FieldKey(getDisplayColumn().getFieldKey().getParent(), "EntityId"));
            keys.add(new FieldKey(getDisplayColumn().getFieldKey().getParent(), "Container"));
            keys.add(new FieldKey(getDisplayColumn().getFieldKey().getParent(), "Folder"));
        }

        return keys;
    }

    private FieldKey getEntityIdFieldKey(RenderContext ctx)
    {
        for(FieldKey fk : getEntityIdFieldKeys())
        {
            if(ctx.containsKey(fk) && (ctx.get(fk) == null || ctx.get(fk) instanceof String))
            {
                return fk;
            }
        }
        return null;
    }

    private String getEntityIdValue(RenderContext ctx)
    {
        FieldKey fk = getEntityIdFieldKey(ctx);
        if(fk == null)
            return null;

        return ctx.get(fk) == null ? null : (String)ctx.get(fk);
    }

    private Container getContainer(RenderContext ctx)
    {
        String id = getEntityIdValue(ctx);

        return id == null ? null : ContainerManager.getForId(id);
    }


    @Override
    public void addQueryFieldKeys(Set<FieldKey> keys)
    {
        super.addQueryFieldKeys(keys);
        keys.addAll(getEntityIdFieldKeys());
    }

    @Override
    public String renderURL(RenderContext ctx)
    {
        Container c = getContainer(ctx);

        if(c == null)
            return null;

        return super.renderURL(ctx);
    }

    @Override @NotNull
    public String getFormattedValue(RenderContext ctx)
    {
        String displayValue = getDisplayValue(ctx).toString();
        if (getRequiresHtmlFiltering())
            displayValue = PageFlowUtil.filter(displayValue);
        return displayValue;
    }

    public boolean isFilterable()
    {
        return !_showPath;
    }

    public boolean isSortable()
    {
        return !_showPath;
    }


    @TestWhen(TestWhen.When.BVT)
    public static class TestCase extends Assert
    {
        private TestContext _ctx;
        private User _user;
        String PROJECT_NAME = "__ContainerDisplayColumnTestProject";

        @Before
        public void setUp()
        {
            _ctx = TestContext.get();
            User loggedIn = _ctx.getUser();
            assertTrue("login before running this test", null != loggedIn);
            assertFalse("login before running this test", loggedIn.isGuest());
            _user = _ctx.getUser().cloneUser();
        }

        @Test
        public void testDisplayColumn() throws Exception
        {
            if (ContainerManager.getForPath(PROJECT_NAME) != null)
            {
                ContainerManager.deleteAll(ContainerManager.getForPath(PROJECT_NAME), _user);
            }

            Container project = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT_NAME, null, null, Container.TYPE.normal, _user);
            Container subFolder1 = ContainerManager.createContainer(project, "subfolder1");
            Container subFolder2 = ContainerManager.createContainer(subFolder1, "subfolder2");

            //create and delete containers to give audit events
            Container workbook1 = ContainerManager.createContainer(subFolder2, "Workbook1", "Workbook1", "", Container.TYPE.workbook, _user);
            assertTrue(ContainerManager.delete(workbook1, _user));
            ContainerManager.deleteAll(subFolder2, _user);

            UserSchema us = QueryService.get().getUserSchema(_user, ContainerManager.getRoot(), "auditLog");
            MutablePropertyValues mpv = new MutablePropertyValues();
            mpv.addPropertyValue("schemaName", "auditLog");
            mpv.addPropertyValue("query.queryName", "ContainerAuditEvent");
            mpv.addPropertyValue("query" + DataRegion.CONTAINER_FILTER_NAME, "AllFolders");

            List<FieldKey> fieldKeys = new ArrayList<>();
            fieldKeys.add(FieldKey.fromString("ProjectId"));
            fieldKeys.add(FieldKey.fromString("ProjectId/Name"));
            fieldKeys.add(FieldKey.fromString("ProjectId/Parent/Name"));
            fieldKeys.add(FieldKey.fromString("ContainerId"));
            fieldKeys.add(FieldKey.fromString("ContainerId/Name"));
            fieldKeys.add(FieldKey.fromString("Comment"));
            mpv.addPropertyValue("query.columns", StringUtils.join(fieldKeys, ","));

            BindException errors = new NullSafeBindException(new Object(), "command");
            QuerySettings qs = us.getSettings(mpv, "query");
            qs.setBaseFilter(new SimpleFilter(FieldKey.fromString("projectId"), project.getEntityId()));

            // Use a mock request so that we don't end up writing to the "real" output when we write
            // out spaces to see if the client is still listening during an async query
            final ViewContext vc = new ViewContext();
            vc.setActionURL(new ActionURL("fake", "fake", project));
            final MockHttpServletResponse response = new MockHttpServletResponse();
            vc.setResponse(response);
            QueryView view = new QueryView(us, qs, errors)
            {
                @Override
                public DataView createDataView()
                {
                    DataView dataView = super.createDataView();
                    dataView.getViewContext().setResponse(response);
                    dataView.getRenderContext().getViewContext().setResponse(response);
                    return dataView;
                }
            };
            view.setViewContext(vc);
            ExtendedApiQueryResponse resp = new ExtendedApiQueryResponse(view, false, false, "auditLog", "ContainerAuditEvent", 0, fieldKeys, false, false, false);
            Writer writer = new StringWriter();
            ApiResponseWriter apiWriter = new ApiJsonWriter(writer);
            resp.render(apiWriter);
            JSONObject json = new JSONObject(writer.toString());
            JSONArray rows = json.getJSONArray("rows");
            assertEquals("Wrong number of rows returned", 4, rows.length());
            for (JSONObject row : rows.toJSONObjectArray())
            {
                String comment = row.getJSONObject("Comment").getString("value");
                if (comment.contains(project.getName() + " was created"))
                {
                    assertEquals("Incorrect display value for ProjectId column", row.getJSONObject("ProjectId").getString("displayValue"), project.getName());
                    assertEquals("Incorrect json value for for ProjectId column", row.getJSONObject("ProjectId").getString("value"), project.getEntityId().toString());
                    assertEquals("Incorrect json value for ProjectId/Name column", row.getJSONObject("ProjectId/Name").getString("value"), project.getName());

                    assertEquals("Incorrect json value for ContainerId column", row.getJSONObject("ContainerId").getString("value"), project.getEntityId().toString());
                    assertEquals("Incorrect json value for ContainerId column", row.getJSONObject("ContainerId").getString("displayValue"), project.getName());

                    assertEquals("Incorrect json value for ContainerId column", row.getJSONObject("ContainerId/Name").getString("value"), project.getName());

                    assertEquals("Incorrect json value for ContainerId column", row.getJSONObject("ProjectId/Parent/Name").getString("value"), null);
                    assertEquals("Incorrect json value for ContainerId column", row.getJSONObject("ProjectId/Parent/Name").getString("displayValue"), "");

                }
                else if (comment.contains(subFolder1.getName() + " was created"))
                {
                    assertEquals("Incorrect display value for ProjectId column", row.getJSONObject("ProjectId").getString("displayValue"), subFolder1.getName());
                    assertEquals("Incorrect json value for for ProjectId column", row.getJSONObject("ProjectId").getString("value"), subFolder1.getEntityId().toString());
                    assertEquals("Incorrect json value for ProjectId/Name column", row.getJSONObject("ProjectId/Name").getString("value"), subFolder1.getName());

                    assertEquals("Incorrect json value for ContainerId column", row.getJSONObject("ContainerId").getString("value"), subFolder1.getEntityId().toString());
                    assertEquals("Incorrect json value for ContainerId column", row.getJSONObject("ContainerId").getString("displayValue"), subFolder1.getName());

                    assertEquals("Incorrect json value for ContainerId column", row.getJSONObject("ContainerId/Name").getString("value"), subFolder1.getName());

                    assertEquals("Incorrect json value for ContainerId column", row.getJSONObject("ProjectId/Parent/Name").getString("value"), null);
                    assertEquals("Incorrect json value for ContainerId column", row.getJSONObject("ProjectId/Parent/Name").getString("displayValue"), "");

                }
                else if (comment.contains(workbook1.getName() + " was created") || comment.contains(workbook1.getName() + " was deleted"))
                {
                    assertEquals("Incorrect display value for ProjectId column", project.getName(), row.getJSONObject("ProjectId").getString("displayValue"));
                    assertEquals("Incorrect json value for for ProjectId column", project.getEntityId().toString(), row.getJSONObject("ProjectId").getString("value"));
                    assertEquals("Incorrect json value for ProjectId/Name column", project.getName().toString(), row.getJSONObject("ProjectId/Name").getString("value"));

                    assertEquals("Incorrect json value for ContainerId column", null, row.getJSONObject("ContainerId").getString("value"));
                    assertEquals("Incorrect json value for ContainerId column", "<deleted>", row.getJSONObject("ContainerId").getString("displayValue"));

                    assertEquals("Incorrect json value for ContainerId column", null, row.getJSONObject("ContainerId/Name").getString("value"));
                    assertEquals("Incorrect json value for ContainerId column", "", row.getJSONObject("ContainerId/Name").getString("displayValue"));

                    assertEquals("Incorrect json value for ContainerId column", null, row.getJSONObject("ProjectId/Parent/Name").getString("value"));
                    assertEquals("Incorrect json value for ContainerId column", "", row.getJSONObject("ProjectId/Parent/Name").getString("displayValue"));
                }
                else if (comment.contains(subFolder2.getName() + " was deleted"))
                {
                    assertEquals("Incorrect display value for ProjectId column", project.getName(), row.getJSONObject("ProjectId").getString("displayValue"));
                    assertEquals("Incorrect json value for for ProjectId column", project.getEntityId().toString(), row.getJSONObject("ProjectId").getString("value"));
                    assertEquals("Incorrect json value for ProjectId/Name column", project.getName().toString(), row.getJSONObject("ProjectId/Name").getString("value"));

                    assertEquals("Incorrect json value for ContainerId column", null, row.getJSONObject("ContainerId").getString("value"));
                    assertEquals("Incorrect json value for ContainerId column", "<deleted>", row.getJSONObject("ContainerId").getString("displayValue"));

                    assertEquals("Incorrect json value for ContainerId column", null, row.getJSONObject("ContainerId/Name").getString("value"));
                    assertEquals("Incorrect json value for ContainerId column", "", row.getJSONObject("ContainerId/Name").getString("displayValue"));

                    assertEquals("Incorrect json value for ContainerId column", null, row.getJSONObject("ProjectId/Parent/Name").getString("value"));
                    assertEquals("Incorrect json value for ContainerId column", "", row.getJSONObject("ProjectId/Parent/Name").getString("displayValue"));
                }
                else
                {
                    throw new Exception("Row not expected.  The comment was: " + comment);
                }
            }
            ContainerManager.deleteAll(project, _user);
        }
    }
}

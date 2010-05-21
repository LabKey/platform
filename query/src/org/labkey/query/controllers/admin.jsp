<%
/*
 * Copyright (c) 2006-2010 LabKey Corporation
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
%>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.query.persist.ExternalSchemaDef" %>
<%@ page import="org.labkey.query.persist.QueryManager" %>
<%@ page import="org.labkey.query.controllers.QueryController.*" %>
<%@ page import="org.labkey.query.controllers.QueryController" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="java.util.*" %>
<%@ page import="org.labkey.api.data.DbScope" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<p>
    Administrators can define external schemas to make data stored in PostgreSQL, Microsoft SQL Server, and SAS available
    for viewing, querying, and editing via LabKey Server.  This feature should be used with great care since, depending
    on your configuration, any user with access to this folder could view and modify arbitrary data in your databases.
</p>
<p>
    Defining any of the standard LabKey schemas as an external schema is strongly discouraged since it bypasses LabKey's
    security and data integrity checks.  Also, the database schema may change in future releases, so queries written
    against LabKey schemas may stop working after an upgrade.
</p>
<%
    Container c = getContainer();
    List<ExternalSchemaDef> defs = Arrays.asList(QueryManager.get().getExternalSchemaDefs(c));
    boolean isAdmin = getUser().isAdministrator();

    if (defs.isEmpty()) { %>
<p>There are no database user schemas defined in this folder.</p>
<% }
else
{
    Collections.sort(defs, new Comparator<ExternalSchemaDef>()
    {
        @Override
        public int compare(ExternalSchemaDef def1, ExternalSchemaDef def2)
        {
            return def1.getUserSchemaName().compareToIgnoreCase(def2.getUserSchemaName());
        }
    });

    String reloadedSchema = StringUtils.trimToNull(request.getParameter("reloadedSchema"));

    if (null != reloadedSchema)
    {
        %>
        <div class="labkey-error"><%

        if ("ALL".equals(reloadedSchema))
        { %>
            All schemas in this folder were reloaded successfully.<%
        }
        else
        { %>
            Schema <%=h(reloadedSchema)%> was reloaded successfully.<%
        } %>
        </div><br>
        <%
    } %>

    <table>
    <%
    QueryUrlsImpl urls = new QueryUrlsImpl();

    for (ExternalSchemaDef def : defs)
    {
        ActionURL urlEdit = urls.urlUpdateExternalSchema(c, def);
        ActionURL urlView = new ActionURL(QueryController.SchemaAction.class, c);
        urlView.addParameter("schemaName", def.getUserSchemaName());
        ActionURL urlReload = urlEdit.clone();
        urlReload.setAction(QueryController.ReloadExternalSchemaAction.class);
        ActionURL urlDelete = QueryController.getDeleteExternalSchemaURL(c, def.getExternalSchemaId());

    %>

        <tr>
            <td><%=h(def.getUserSchemaName())%></td><%
                if (null != DbScope.getDbScope(def.getDataSource()))
                {
            %>
            <td><labkey:link text="view data" href="<%=h(urlView)%>" /></td>
            <% if (isAdmin) {%><td><labkey:link text="edit definition" href="<%=h(urlEdit)%>" /></td><%}%>
            <td><labkey:link text="reload" href="<%=h(urlReload)%>" /></td>
            <% if (isAdmin) {%><td><labkey:link text="delete" href="<%=h(urlDelete)%>" /></td><%}%><%
                }
                else
                {
            %>
            <td>&nbsp;</td>
            <% if (isAdmin) {%><td>&nbsp;</td><%}%>
            <td>&nbsp;</td>
            <% if (isAdmin) {%><td><labkey:link text="delete" href="<%=h(urlDelete)%>" /></td><%}%>
            <td><div class="labkey-error">Not available: can't connect to <%=h(def.getDataSource())%></div></td>
            <%
                }
            %>
        </tr><%
    } %>
    </table><%
} %>
<br>
<%
    if (isAdmin)
    { %>
    <labkey:link href="<%= new ActionURL(QueryController.InsertExternalSchemaAction.class, c)%>" text="define new schema"/>
    <!--TODO: Enable bulk publish/unpublish labkey:link href="<%= new ActionURL(QueryController.InsertMultipleExternalSchemasAction.class, c)%>" text="define multiple new schemas"/--><%
    }

    if (defs.size() > 1)
    { %>
    <labkey:link href="<%= new ActionURL(QueryController.ReloadAllUserSchemas.class, c)%>" text="reload all schemas"/><%
    }
    %>


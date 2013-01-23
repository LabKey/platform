<%
/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.DbScope" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.query.controllers.QueryController" %>
<%@ page import="org.labkey.query.controllers.QueryController.QueryUrlsImpl" %>
<%@ page import="org.labkey.query.persist.ExternalSchemaDef" %>
<%@ page import="org.labkey.query.persist.QueryManager" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.query.persist.LinkedSchemaDef" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<h2>External Schemas</h2>
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
    boolean isAdmin = getUser().isAdministrator();
    QueryUrlsImpl urls = new QueryUrlsImpl();

    List<ExternalSchemaDef> defs = Arrays.asList(QueryManager.get().getExternalSchemaDefs(c));
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
        <div class="labkey-message"><%

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

    for (ExternalSchemaDef def : defs)
    {
        ActionURL urlEdit = urls.urlUpdateExternalSchema(c, def);
        ActionURL urlView = urls.urlSchemaBrowser(c, def.getUserSchemaName());
        ActionURL urlReload = urlEdit.clone();
        urlReload.setAction(QueryController.ReloadExternalSchemaAction.class);
        ActionURL urlDelete = urls.urlDeleteExternalSchema(c, def);

    %>

        <tr>
            <td><%=h(def.getUserSchemaName())%></td><%
                if (null != DbScope.getDbScope(def.getDataSource()))
                {
            %>
            <td><labkey:link text="view schema" href="<%=h(urlView)%>" /></td>
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
    <labkey:link href="<%= new ActionURL(QueryController.InsertExternalSchemaAction.class, c)%>" text="new external schema"/>
    <!--TODO: Enable bulk publish/unpublish labkey:link href="<%= new ActionURL(QueryController.InsertMultipleExternalSchemasAction.class, c)%>" text="define multiple new schemas"/--><%
    }

    if (defs.size() > 1)
    { %>
    <labkey:link href="<%= new ActionURL(QueryController.ReloadAllUserSchemas.class, c)%>" text="reload all schemas"/><%
    }
    %>


<h2>Linked Schemas</h2>
<p>
    Linked schemas can be created by refrencing an existing LabKey schema in the current or different folder.
    The linked schema may expose some or all of the tables from the original schema.  The linked tables
    may be filtered such that only a subset of the rows are available.
</p>
<%
    List<LinkedSchemaDef> linkedSchemas = Arrays.asList(QueryManager.get().getLinkedSchemaDefs(c));
    if (linkedSchemas.isEmpty()) { %>
<p>There are no linked schemas defined in this folder.</p>
<% }
else
{
    Collections.sort(linkedSchemas, new Comparator<LinkedSchemaDef>()
    {
        @Override
        public int compare(LinkedSchemaDef def1, LinkedSchemaDef def2)
        {
            return def1.getUserSchemaName().compareToIgnoreCase(def2.getUserSchemaName());
        }
    }); %>

        <table>
    <%

    for (LinkedSchemaDef linkedSchema : linkedSchemas)
    {
        ActionURL urlView = urls.urlSchemaBrowser(c, linkedSchema.getUserSchemaName());
        ActionURL urlEdit = new ActionURL(QueryController.EditLinkedSchemaAction.class, c).addParameter("externalSchemaId", Integer.toString(linkedSchema.getExternalSchemaId()));
        ActionURL urlDelete = new ActionURL(QueryController.DeleteLinkedSchemaAction.class, c).addParameter("externalSchemaId", Integer.toString(linkedSchema.getExternalSchemaId()));
    %>

        <tr>
            <td><%=h(linkedSchema.getUserSchemaName())%></td>
            <td><labkey:link text="view schema" href="<%=h(urlView)%>" /></td>
            <% if (isAdmin) {%><td><labkey:link text="edit definition" href="<%=h(urlEdit)%>" /></td><%}%>
            <% if (isAdmin) {%><td><labkey:link text="delete" href="<%=h(urlDelete)%>" /></td><%}%>
        </tr><%
    } %>
    </table><%
}
%>
<br>
<% if (isAdmin) { %>
<labkey:link href="<%= new ActionURL(QueryController.InsertLinkedSchemaAction.class, c)%>" text="new linked schema"/>
<% } %>

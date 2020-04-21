<%
/*
 * Copyright (c) 2013-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.query.controllers.QueryController" %>
<%@ page import="org.labkey.query.controllers.QueryController.QueryUrlsImpl" %>
<%@ page import="org.labkey.query.persist.ExternalSchemaDef" %>
<%@ page import="org.labkey.query.persist.LinkedSchemaDef" %>
<%@ page import="org.labkey.query.persist.QueryManager" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<h3>External Schemas</h3>
<p>
    Site administrators can define external schemas to make data stored in any PostgreSQL, Microsoft SQL Server, SAS, MySQL,
    Oracle, or Amazon Redshift relational database available for viewing, querying, and editing via LabKey Server.
</p>
<%
    if (getUser().hasSiteAdminPermission())
    {
%>
<p>
    This feature should be used with great caution since, depending on your configuration, any user with access to this folder
    could view and modify arbitrary data in your databases. Defining any of the standard LabKey schemas as an external schema
    is strongly discouraged since it bypasses LabKey's security and data integrity checks. Also, the LabKey schemas may change
    in future releases, so queries written directly against LabKey schemas may stop working after an upgrade.
</p>
<%
    }

    Container c = getContainer();
    boolean isAdmin = c.hasPermission(getUser(), AdminOperationsPermission.class);
    QueryUrlsImpl urls = new QueryUrlsImpl();

    List<ExternalSchemaDef> defs = QueryManager.get().getExternalSchemaDefs(c);
    if (defs.isEmpty()) { %>
<p>There are no external schemas defined in this folder.</p>
<% }
else
{
    defs.sort(Comparator.comparing(ExternalSchemaDef::getUserSchemaName, String.CASE_INSENSITIVE_ORDER));

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

    <table class='labkey-data-region-legacy labkey-show-borders'>
        <tr>
            <td class='labkey-column-header' style='min-width:80px;'>Name</td>
            <td class='labkey-column-header' style='min-width:80px;'>Data Source</td>
            <td class='labkey-column-header' style='min-width:80px;'>Source Database Schema</td>
            <td class='labkey-column-header' colspan="<%=isAdmin ? 5 : 3%>">&nbsp;</td>
        </tr>
    <%

    int i = 0;
    for (ExternalSchemaDef def : defs)
    {
        ActionURL urlEdit = urls.urlUpdateExternalSchema(c, def);
        ActionURL urlView = urls.urlSchemaBrowser(c, def.getUserSchemaName());
        ActionURL urlReload = urls.urlReloadExternalSchema(c, def);
        ActionURL urlDelete = urls.urlDeleteSchema(c, def);

    %>
        <tr class='<%=getShadeRowClass(i)%>'>
            <td><%=h(def.getUserSchemaName())%></td>
            <td><%=h(def.getDataSource())%></td>
            <td><%=h(def.getSourceSchemaName())%></td><%
                if (null != DbScope.getDbScope(def.getDataSource()))
                {
            %>
            <td class="labkey-noborder"><%=link("view schema", urlView)%></td>
            <% if (isAdmin) {%><td class="labkey-noborder"><%=link("edit", urlEdit)%></td><%}%>
            <td class="labkey-noborder"><%=link("reload", urlReload).usePost()%></td>
            <% if (isAdmin) {%><td class="labkey-noborder"><%=link("delete", urlDelete)%></td><%}%>
            <td class="labkey-noborder">&nbsp;</td><%  // This column is used for "can't connect" messages (see below)
                }
                else
                {
            %>
            <td class="labkey-noborder">&nbsp;</td>
            <% if (isAdmin) {%><td class="labkey-noborder">&nbsp;</td><%}%>
            <td class="labkey-noborder">&nbsp;</td>
            <% if (isAdmin) {%><td class="labkey-noborder"><%=link("delete", urlDelete)%></td><%}%>
            <td class="labkey-noborder"><div class="labkey-error">Not available: can't connect to <%=h(def.getDataSource())%></div></td>
            <%
                }
            %>
        </tr><%
        i++;
    } %>
    </table><%
} %>
<br>
<%
    if (isAdmin)
    { %>
    <%=link("new external schema", urls.urlInsertExternalSchema(c))%>
    <%
    }
    if (defs.size() > 1)
    { %>
    <%=link("reload all schemas", QueryController.ReloadAllUserSchemas.class).usePost()%><%
    }
    %>


<h3>Linked Schemas</h3>
<p>
    Site administrators can create linked schemas by referencing an existing LabKey schema in the current or different folder.
    The linked schema may expose some or all of the tables and queries from the original schema.
    The linked tables and queries may be filtered such that only a subset of the rows and/or columns are available.
</p>
<%
    List<LinkedSchemaDef> linkedSchemas = QueryManager.get().getLinkedSchemaDefs(c);
    if (linkedSchemas.isEmpty()) { %>
<p>There are no linked schemas defined in this folder.</p>
<% }
else
{
    linkedSchemas.sort(Comparator.comparing(LinkedSchemaDef::getUserSchemaName, String.CASE_INSENSITIVE_ORDER)); %>

    <table class='labkey-data-region-legacy labkey-show-borders'>
        <tr>
            <td class='labkey-column-header' style='min-width:80px;'>Name</td>
            <td class='labkey-column-header' style='min-width:80px;'>Schema Template</td>
            <td class='labkey-column-header' style='min-width:80px;'>Source Container</td>
            <td class='labkey-column-header' style='min-width:80px;'>Source LabKey Schema</td>
            <td class='labkey-column-header' colspan="<%=isAdmin ? 5 : 3%>">&nbsp;</td>
        </tr>
    <%

    int i = 0;
    for (LinkedSchemaDef linkedSchema : linkedSchemas)
    {
        ActionURL urlView = urls.urlSchemaBrowser(c, linkedSchema.getUserSchemaName());
        ActionURL urlEdit = new ActionURL(QueryController.EditLinkedSchemaAction.class, c).addParameter("externalSchemaId", Integer.toString(linkedSchema.getExternalSchemaId()));
        ActionURL urlDelete = urls.urlDeleteSchema(c, linkedSchema);

        Container sourceContainer = linkedSchema.lookupSourceContainer();

        ActionURL urlSourceView = null;
        if (sourceContainer != null && linkedSchema.getSourceSchemaName() != null)
        {
            urlSourceView = urls.urlSchemaBrowser(sourceContainer, linkedSchema.getSourceSchemaName());
        }

    %>
        <tr class='<%=getShadeRowClass(i)%>'>
            <td><%=h(linkedSchema.getUserSchemaName())%></td>
            <td><%=h(linkedSchema.getSchemaTemplate())%></td>
            <td><%=h(sourceContainer != null ? sourceContainer.getPath() : linkedSchema.getSourceContainerId())%></td>
            <td>
                <% if (urlSourceView != null) { %>
                <a href="<%=h(urlSourceView)%>"><%=h(linkedSchema.getSourceSchemaName())%></a>
                <% } else { %>
                <%=h(linkedSchema.getSourceSchemaName())%>
                <% } %>
            </td>
            <td class="labkey-noborder"><%=link("view schema", urlView)%></td>
            <% if (isAdmin) {%><td class="labkey-noborder"><%=link("edit", urlEdit)%></td><%}%>
            <% if (isAdmin) {%><td class="labkey-noborder"><%=link("delete", urlDelete)%></td><%}%>
        </tr><%
        i++;
    } %>
    </table><%
}
%>
<br>
<% if (isAdmin) { %>
<%=link("new linked schema", QueryController.InsertLinkedSchemaAction.class)%>
<% } %>

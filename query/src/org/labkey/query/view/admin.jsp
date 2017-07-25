<%
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
    Administrators can define external schemas to make data stored in any PostgreSQL, Microsoft SQL Server, SAS, MySQL,
    or Oracle relational database available for viewing, querying, and editing via LabKey Server. This feature should be
    used with great caution since, depending on your configuration, any user with access to this folder could view and
    modify arbitrary data in your databases.
</p>
<p>
    Defining any of the standard LabKey schemas as an external schema is strongly discouraged since it bypasses LabKey's
    security and data integrity checks. Also, the database schema may change in future releases, so queries written
    directly against LabKey schemas may stop working after an upgrade.
</p>
<%
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
        i++;
        ActionURL urlEdit = urls.urlUpdateExternalSchema(c, def);
        ActionURL urlView = urls.urlSchemaBrowser(c, def.getUserSchemaName());
        ActionURL urlReload = urlEdit.clone();
        urlReload.setAction(QueryController.ReloadExternalSchemaAction.class);
        ActionURL urlDelete = urls.urlDeleteExternalSchema(c, def);

    %>
        <tr class='<%=getShadeRowClass(i % 2 == 1)%>'>
            <td><%=h(def.getUserSchemaName())%></td>
            <td><%=h(def.getDataSource())%></td>
            <td><%=h(def.getSourceSchemaName())%></td><%
                if (null != DbScope.getDbScope(def.getDataSource()))
                {
            %>
            <td class="labkey-noborder"><labkey:link text="view schema" href="<%=h(urlView)%>" /></td>
            <% if (isAdmin) {%><td class="labkey-noborder"><labkey:link text="edit" href="<%=h(urlEdit)%>" /></td><%}%>
            <td class="labkey-noborder"><labkey:link text="reload" href="<%=h(urlReload)%>" /></td>
            <% if (isAdmin) {%><td class="labkey-noborder"><labkey:link text="delete" href="<%=h(urlDelete)%>" /></td><%}%>
            <td class="labkey-noborder">&nbsp;</td><%
                }
                else
                {
            %>
            <td class="labkey-noborder">&nbsp;</td>
            <% if (isAdmin) {%><td class="labkey-noborder">&nbsp;</td><%}%>
            <td class="labkey-noborder">&nbsp;</td>
            <% if (isAdmin) {%><td class="labkey-noborder"><labkey:link text="delete" href="<%=h(urlDelete)%>" /></td><%}%>
            <td class="labkey-noborder"><div class="labkey-error">Not available: can't connect to <%=h(def.getDataSource())%></div></td>
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
    <%
    }

    if (defs.size() > 1)
    { %>
    <labkey:link href="<%= new ActionURL(QueryController.ReloadAllUserSchemas.class, c)%>" text="reload all schemas"/><%
    }
    %>


<h3>Linked Schemas</h3>
<p>
    Linked schemas can be created by referencing an existing LabKey schema in the current or different folder.
    The linked schema may expose some or all of the tables and queries from the original schema.
    The linked tables and queries may be filtered such that only a subset of the rows are available.
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
        i++;
        ActionURL urlView = urls.urlSchemaBrowser(c, linkedSchema.getUserSchemaName());
        ActionURL urlEdit = new ActionURL(QueryController.EditLinkedSchemaAction.class, c).addParameter("externalSchemaId", Integer.toString(linkedSchema.getExternalSchemaId()));
        ActionURL urlDelete = new ActionURL(QueryController.DeleteLinkedSchemaAction.class, c).addParameter("externalSchemaId", Integer.toString(linkedSchema.getExternalSchemaId()));

        Container sourceContainer = linkedSchema.lookupSourceContainer();

        ActionURL urlSourceView = null;
        if (sourceContainer != null && linkedSchema.getSourceSchemaName() != null)
        {
            urlSourceView = urls.urlSchemaBrowser(sourceContainer, linkedSchema.getSourceSchemaName());
        }

    %>
        <tr class='<%=getShadeRowClass(i % 2 == 1)%>'>
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
            <td class="labkey-noborder"><labkey:link text="view schema" href="<%=h(urlView)%>" /></td>
            <% if (isAdmin) {%><td class="labkey-noborder"><labkey:link text="edit" href="<%=h(urlEdit)%>" /></td><%}%>
            <% if (isAdmin) {%><td class="labkey-noborder"><labkey:link text="delete" href="<%=h(urlDelete)%>" /></td><%}%>
        </tr><%
    } %>
    </table><%
}
%>
<br>
<% if (isAdmin) { %>
<labkey:link href="<%= new ActionURL(QueryController.InsertLinkedSchemaAction.class, c)%>" text="new linked schema"/>
<% } %>

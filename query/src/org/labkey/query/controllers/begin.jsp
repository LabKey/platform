<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.query.*" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.query.QueryParam" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    QueryForm form = (QueryForm) getForm();
    QueryService svc = QueryService.get();
    UserSchema currentSchema = form.getSchema();
%>
<% DefaultSchema folderSchema = DefaultSchema.get(getUser(), getContainer()); %>
<p>Welcome to the Query Module.</p>

<p>Schemas in this folder:<br>
    <%
        for (String schemaName : folderSchema.getUserSchemaNames())
        {
            if (currentSchema == null || !schemaName.equals(currentSchema.getSchemaName()))
            {
                ActionURL urlSchema = new ActionURL("query", QueryAction.schema.name(), getContainer().getPath());
                urlSchema.addParameter(QueryParam.schemaName, schemaName);
    %>
                <labkey:link text="<%=schemaName%>" href="<%=urlSchema%>"/>
            <% }
            else
            { %>
            <b><%=h(schemaName)%>
            </b>
            <%}%>
    <br>
    <%}
    %>
</p>
<%
    if (currentSchema != null)
    { %>
<table class="normal">
    <tr>
        <th colspan="7">User-defined queries in the schema: <%=h(currentSchema.getSchemaName())%>
        </th>
    </tr>
    <% for (QueryDefinition query : svc.getQueryDefs(getContainer(), currentSchema.getSchemaName()).values())
    {
    %>
    <tr>
        <td>
            <%=h(query.getName())%>
        </td>
        <td>
            <labkey:button text="Run" href="<%=currentSchema.urlFor(QueryAction.executeQuery, query)%>"/>
        </td>
        <% if (!query.getContainer().equals(getContainer()))
        {
            ActionURL urlSchema = currentSchema.urlFor(QueryAction.schema);
            urlSchema.setExtraPath(query.getContainer().getPath());
        %>
        <td colspan="4">
            Inherited from
            <labkey:link href="<%=h(urlSchema)%>"
                       text="<%=h(query.getContainer().getPath())%>"/>
        </td>
        <% }
        else
        { %>
        <td>
            <labkey:button text="Design" alt="<%="Design " + query.getName()%>" href="<%=currentSchema.urlFor(QueryAction.designQuery, query)%>"/>
        </td>
        <td>
            <labkey:button text="Source" alt="<%="Source " + query.getName()%>" href="<%=currentSchema.urlFor(QueryAction.sourceQuery, query)%>"/>
        </td>
        <td><% if (query.canEdit(getUser()))
        { %>
            <labkey:button text="Delete" alt="<%="Delete " + query.getName()%>" href="<%=currentSchema.urlFor(QueryAction.deleteQuery, query)%>"/>
            <% } %></td>
        <td>
            <labkey:button text="Properties"
                         alt="<%="Properties " + query.getName()%>"
                         href="<%=currentSchema.urlFor(QueryAction.propertiesQuery, query)%>"/>
        </td>
        <% } %>
        <td><%
            try
            {
                if (query.getParseErrors(currentSchema).size() != 0)
            { %>
                <span style="color:red">(has errors)</span>
                <% }
                }
                catch (Throwable t)
                {
                %>
                <span style="color:red">An exception occurred: <%=h(t.toString())%></span>
                <%}
                %>

            <% if (query.getDescription() != null) { %>
            <i><%=h(query.getDescription())%></i>
            <% }%>
        </td>

    </tr>
    <% } %>
</table>
<labkey:button text="Create New Query" href="<%=currentSchema.urlFor(QueryAction.newQuery)%>"/>


<p>
    <table class="normal">
        <tr><th colspan="2">Built-in tables</th></tr>
        <%
            for (String name : currentSchema.getTableNames())
            {
                QueryDefinition def = currentSchema.getQueryDefForTable(name);
        %>
        <tr><td>
        <a href="<%=h(def.urlFor(QueryAction.executeQuery, getContainer()))%>"><%=h(name)%></a></td>
        <td>
        <% if (def.getDescription() != null) { %>
        <i><%=h(def.getDescription())%></i>
        <% } %>
        </td></tr>
        <%}%>

    </table>
</p>


<% } %>



<% if (getContainer().hasPermission(getUser(), ACL.PERM_ADMIN))
{%>
    <labkey:link href="<%=new ActionURL("query", "admin", getContainer())%>" text="Schema Administration" />
<% } %>
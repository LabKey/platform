<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.util.TreeMap" %>
<%@ page import="java.util.TreeSet" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    QueryForm form = (QueryForm) getForm();
    QueryService svc = QueryService.get();
    UserSchema currentSchema = form.getSchema();

    Map<String, QueryDefinition> queries = new TreeMap<String, QueryDefinition>(svc.getQueryDefs(getContainer(), currentSchema.getSchemaName()));
%>
<table class="normal">
    <tr>
        <th colspan="7">User-defined queries in the schema:</th>
    </tr>
    <%
    if (queries.size() == 0)
    {
        %><tr><td colspan="7"><i>No queries defined.</i></td></tr><%
    }

    for (QueryDefinition query : queries.values())
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
            ActionURL urlSchema = currentSchema.urlFor(QueryAction.begin);
            urlSchema.setExtraPath(query.getContainer().getPath());
        %>
        <td colspan="4">
            Inherited from project
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
        <td>
<%--
            try
            {
                if (query.getParseErrors(currentSchema).size() != 0)
                {
                    %><span style="color:red">(has errors)</span><%
                }
            }
            catch (Throwable t)
            {
                %><span style="color:red">An exception occurred: <%=h(t.toString())%></span><%
            } --%>
<%
            if (query.getDescription() != null)
            {
                %><i><%=h(query.getDescription())%></i><%
            }
%>
        </td>

    </tr>
    <% } %>
</table>

<% if (form.getSchema().getTableAndQueryNames(false).size() > 0) { %>
    <labkey:button text="Create New Query" href="<%=currentSchema.urlFor(QueryAction.newQuery)%>"/>
<%  } %>


<p>
    <table class="normal">
        <tr><th colspan="2">Built-in tables</th></tr>
        <%
            Set<String> tableNames = new TreeSet<String>(currentSchema.getTableNames());
            if (tableNames.size() == 0)
            {
                %><tr><td colspan="2"><i>No tables defined.</i></td></tr><%
            }

            for (String name : tableNames)
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

<% if (getUser().isAdministrator())
{%>
    <labkey:link href="<%=new ActionURL("query", "admin", getContainer())%>" text="Schema Administration" />
<% } %>
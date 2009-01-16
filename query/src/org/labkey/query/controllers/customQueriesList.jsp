<%
/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
<%@ page import="java.util.TreeMap" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    QueryForm form = (QueryForm) HttpView.currentModel();
    ViewContext context = HttpView.currentContext();
    QueryService svc = QueryService.get();
    UserSchema currentSchema = form.getSchema();

    Map<String, QueryDefinition> queries = new TreeMap<String, QueryDefinition>(String.CASE_INSENSITIVE_ORDER);
    queries.putAll(svc.getQueryDefs(context.getContainer(), currentSchema.getSchemaName()));
%>
<table>
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
            <a href="<%= h(currentSchema.urlFor(QueryAction.executeQuery, query))%>"><%=h(query.getName())%></a>
        </td>
        <% if (!query.getContainer().equals(context.getContainer()))
        {
            ActionURL urlSchema = currentSchema.urlFor(QueryAction.begin);
            urlSchema.setContainer(query.getContainer());
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
            [<a title="<%="Design " + query.getName()%>" href="<%=currentSchema.urlFor(QueryAction.designQuery, query)%>">design query</a>]
        </td>
        <td>
            [<a title="<%="Source " + query.getName()%>" href="<%=currentSchema.urlFor(QueryAction.sourceQuery, query)%>">edit source</a>]
        </td>
        <td><% if (query.canEdit(context.getUser()))
        { %>
            [<a title="<%="Delete " + query.getName()%>" href="<%=currentSchema.urlFor(QueryAction.deleteQuery, query)%>">delete</a>]
            <% } %></td>
        <td>
            [<a title="<%="Properties " + query.getName()%>" href="<%=currentSchema.urlFor(QueryAction.propertiesQuery, query)%>">edit properties</a>]
        </td>
        <% } %>
        <td>
<%--
            try
            {
                if (query.getParseErrors(currentSchema).size() != 0)
                {
                    %><span class="labkey-error">(has errors)</span><%
                }
            }
            catch (Throwable t)
            {
                %><span class="labkey-error">An exception occurred: <%=h(t.toString())%></span><%
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
<p>
    [<a title="Create New Query" href="<%=currentSchema.urlFor(QueryAction.newQuery)%>">create new query</a>]
</p>
<%  } %>

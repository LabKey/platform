<%
/*
 * Copyright (c) 2008 LabKey Corporation
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
<%@ page import="java.util.Set" %>
<%@ page import="java.util.TreeSet" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.query.controllers.QueryControllerSpring" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    QueryForm form = (QueryForm) HttpView.currentModel();
    ViewContext context = HttpView.currentContext();
    UserSchema currentSchema = form.getSchema();
%>

<p>
    <table>
        <%
            Set<String> tableNames = new TreeSet<String>(currentSchema.getTableNames());
            if (tableNames.size() == 0)
            {
                %><tr><td colspan="4"><i>No tables defined.</i></td></tr><%
            }

            for (String name : tableNames)
            {
                QueryDefinition def = currentSchema.getQueryDefForTable(name);
        %>
        <tr>
            <td><a href="<%= h(def.urlFor(QueryAction.executeQuery, context.getContainer()) )%>"><%=h(name)%></a></td>
            <td><labkey:button text="Run" href="<%= def.urlFor(QueryAction.executeQuery, context.getContainer()) %>"/></td>
            <% if (context.hasPermission(ACL.PERM_ADMIN)) { %>
                <td><labkey:button text="Edit Metadata as XML" href="<%= def.urlFor(QueryAction.sourceQuery, context.getContainer()) %>"/></td>
                <td><labkey:button text="Edit Metadata with GUI" href="<%= def.urlFor(QueryAction.metadataQuery, context.getContainer()) %>"/></td>
            <% } %>
            <td>
                <% if (def.getDescription() != null) { %>
                <i><%=h(def.getDescription())%></i>
                <% } %>
            </td>
        </tr>
        <%}%>

    </table>
</p>

<% if (context.getContainer().hasPermission(context.getUser(), ACL.PERM_ADMIN))
{%>
    <labkey:link href="<%=new ActionURL(QueryControllerSpring.AdminAction.class, context.getContainer())%>" text="Schema Administration" />
<% } %>
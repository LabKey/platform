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
<%@ page import="org.labkey.api.audit.AuditLogService" %>
<%@ page import="org.labkey.api.exp.list.ListDefinition" %>
<%@ page import="org.labkey.api.exp.list.ListService" %>
<%@ page import="org.labkey.api.lists.permissions.DesignListPermission" %>
<%@ page import="org.labkey.api.security.SecurityPolicy" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.experiment.controllers.list.ListController" %>
<%@ page import="java.util.*" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    Map<String, ListDefinition> map = ListService.get().getLists(getContainer());
    User user = getViewContext().getUser();
    SecurityPolicy policy = getViewContext().getContainer().getPolicy();
%>

<% if (map.isEmpty()) { %>
<p>There are no user-defined lists in this folder.</p>
<% } else {%>
    <table>
        <tr>
            <th colspan="4">User defined lists in this folder.</th>
        </tr>
   <% for (ListDefinition def : new TreeSet<ListDefinition>(map.values())) { %>
        <tr>
        <td>
            <%=h(def.getName())%>
        </td>
        <td>
            <labkey:link href="<%=def.urlShowData()%>" text="view data" />
        </td>
<% if (policy.hasPermission(user, DesignListPermission.class)) { %>
        <td>
            <labkey:link href="<%=def.urlShowDefinition()%>" text="view design" />
        </td>
<% } %>
<% if (AuditLogService.get().isViewable()) { %>
        <td>
            <labkey:link href="<%=def.urlShowHistory()%>" text="view history" />
        </td>
<% } %>            
        <td>
            <%=h(def.getDescription())%>
        </td>
        </tr>
<% }%>
    </table>
    <%} %>
<% if (policy.hasPermission(user, DesignListPermission.class)) { %>
    <labkey:button text="Create New List" href="<%=h(urlFor(ListController.NewListDefinitionAction.class))%>" />
<% } %>
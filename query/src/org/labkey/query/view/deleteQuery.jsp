<%--
/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
--%>
<%@ page extends="org.labkey.query.controllers.Page" %>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.query.QueryDefinition" %>
<%@ page import="java.util.Collection" %>
<%@ page import="org.labkey.query.controllers.QueryController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    QueryController.DeleteQueryAction action = (QueryController.DeleteQueryAction) HttpView.currentModel();
    QueryDefinition queryDef = action._queryDef;
    Collection<String> dependents = queryDef == null ? null : queryDef.getDependents(getUser());
%>
<labkey:errors></labkey:errors>
<p>Are you sure you want to delete the query '<%=h(action._form.getQueryName())%>'?</p>

<% if (dependents != null && dependents.size() > 0) { %>
The following depend upon this query:
<ul>
    <% for (String dependent : dependents) { %>
    <li><%=h(dependent)%></li>
    <% } %>
</ul>
<% } %>


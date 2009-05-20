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
<%@ page import="org.labkey.api.defaults.SetDefaultValuesListAction" %>
<%@ page import="org.labkey.api.exp.list.ListDefinition" %>
<%@ page import="org.labkey.api.exp.property.Domain" %>
<%@ page import="org.labkey.api.exp.property.DomainProperty" %>
<%@ page import="org.labkey.api.lists.permissions.DesignListPermission" %>
<%@ page import="org.labkey.api.security.SecurityPolicy" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.experiment.controllers.list.ListController" %>
<%@ page import="org.labkey.experiment.controllers.list.ListDefinitionForm" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib"%>
<%
    ListDefinitionForm form = (ListDefinitionForm) __form;
    ListDefinition list = form.getList();
    Domain domain = list.getDomain();
    User user = getViewContext().getUser();
    SecurityPolicy policy = getViewContext().getContainer().getPolicy();
%>
<table>
    <tr><td colspan="2"><b>List Properties</b></td></tr>
    <tr><td>Name:</td><td><%=h(list.getName())%></td></tr>
    <tr><td>Description:</td><td><%=h(list.getDescription())%></td></tr>
    <tr><td>Key Type:</td><td><%=h(list.getKeyType().getLabel())%></td></tr>
    <tr><td>Key Name:</td><td><%=h(list.getKeyName())%></td></tr>
    <tr><td>Title Field:</td><td><%=h(list.getTitleColumn())%></td></tr>
    <tr><td>Discussion Links:</td><td><%=list.getDiscussionSetting().getText()%></td></tr>
    <tr><td>Allow Delete:</td><td><%=list.getAllowDelete() ? "Yes" : "No"%></td></tr>
    <tr><td>Allow Import:</td><td><%=list.getAllowUpload() ? "Yes" : "No"%></td></tr>
    <tr><td>Allow Export &amp; Print:</td><td><%=list.getAllowExport() ? "Yes" : "No"%></td></tr>
</table>
<% if (policy.hasPermission(user, DesignListPermission.class)) {
    ActionURL setDefaultsURL = new ActionURL(SetDefaultValuesListAction.class, getContainer());
    setDefaultsURL.addParameter("domainId", list.getDomain().getTypeId());
    setDefaultsURL.addParameter("returnUrl", getViewContext().getActionURL().getLocalURIString());
%>
    <labkey:link href="<%=list.urlEditDefinition()%>" text="edit design" />
    <labkey:link href="<%=list.urlFor(ListController.Action.deleteListDefinition)%>" text="delete list" />
    <labkey:link href="<%= setDefaultsURL.getLocalURIString() %>" text="set default values" />
<% } %>
    <labkey:link href="<%=list.urlShowData()%>" text="view data" />
<% if (policy.hasPermission(user, UpdatePermission.class) && list.getAllowUpload()) { %>
    <labkey:link href="<%=list.urlFor(ListController.Action.uploadListItems)%>" text="import data" />
<% } %>
<br><br>
<% if (domain.getProperties().length == 0) { %>
    <p>No fields have been defined.</p>
<% } else { %>
<table>
    <tr><td colspan="2"><b>List Fields</b></td></tr>
<% for (DomainProperty property : domain.getProperties()) { %>
    <tr><td><%=h(property.getName())%></td><td><%=h(property.getType().getLabel())%></td></tr>
<% } %>
</table>

<% } %>

<% if (policy.hasPermission(user, DesignListPermission.class)) { %>
    <labkey:link href="<%=list.getDomain().urlEditDefinition(false, true, true)%>" text="edit fields"/>
<% } %>

<%
/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.user.DeleteUsersBean" %>
<%@ page import="org.labkey.core.user.UserController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<DeleteUsersBean> me = (JspView<DeleteUsersBean>) HttpView.currentView();
    DeleteUsersBean bean = me.getModelBean();
    User currentUser = getUser();
    ActionURL urlPost = getViewContext().cloneActionURL();
    urlPost.deleteParameters();

    ActionURL deactivateUsersUrl = new ActionURL(UserController.DeactivateUsersAction.class, getContainer());
%>
<p>Are sure you want to <span style="font-weight:bold;color: #FF0000">permanently delete</span>
the following <%=bean.getUsers().size() > 1 ? "users" : "user"%>?
This action cannot be undone.</p>
    <ul>
    <%
        for (User user : bean.getUsers())
        {
            %><li><%=h(user.getDisplayName(currentUser))%></li><%
        }
    %>
    </ul>
<labkey:form action="<%=urlPost.getEncodedLocalURIString()%>" method="post" name="deleteUsersForm">
    <%
        for (User user : bean.getUsers())
        {
            %><input type="hidden" name="userId" value="<%=user.getUserId()%>"/><%
        }
    %>
    <%= button("Permanently Delete").submit(true) %>
    <%= button("Cancel").href(bean.getCancelUrl()) %>
</labkey:form>
<%
    boolean canDeactivate = false;

    for (User user : bean.getUsers())
    {
        if (user.isActive())
        {
            canDeactivate = true;
            break;
        }
    }

    if (canDeactivate) {
%>
<br/>
<labkey:form action="<%=h(deactivateUsersUrl)%>" method="post" name="deactivateUsersForm">
    <%
        for (User user : bean.getUsers())
        {
            %><input type="hidden" name="userId" value="<%=user.getUserId()%>"/><%
        }
    %>
    <p><span style="font-weight:bold">Note:</span> you may also
    <a href="#" onclick="document.deactivateUsersForm.submit();return false;">deactivate <%=bean.getUsers().size() > 1 ? "these users" : "this user"%></a>
    instead of deleting them.
    Deactivated users may not login, but their information will be preserved
    for display purposes, and their group memberships will be preserved in case
    they are re-activated at a later time.</p>
    <p><%= button(bean.getUsers().size() > 1 ? "Deactivate Users" : "Deactivate User").submit(true) %></p>
</labkey:form>
<% } %>
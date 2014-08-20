<%
/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.user.DeactivateUsersBean" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<DeactivateUsersBean> me = (JspView<DeactivateUsersBean>) HttpView.currentView();
    DeactivateUsersBean bean = me.getModelBean();
    User currentUser = getUser();
    ActionURL urlPost = getViewContext().cloneActionURL();
    urlPost.deleteParameters();
%>
<p>Are sure you want to <b><%=bean.isActivate() ? "re-activate" : "deactivate"%></b>
    the following <%=bean.getUsers().size() > 1 ? "users" : "user"%>?</p>
    <ul>
    <%
        for (User user : bean.getUsers())
        {
            %><li><%=h(user.getDisplayName(currentUser))%></li><%
        } 
    %>
    </ul>
<labkey:form action="<%=urlPost.getEncodedLocalURIString()%>" method="post">
    <input type="hidden" name="redirUrl" value="<%=bean.getRedirUrl().getEncodedLocalURIString()%>"/>
    <%
        for (User user : bean.getUsers())
        {
            %><input type="hidden" name="userId" value="<%=user.getUserId()%>"/><%
        }
    %>
    <%= button(bean.isActivate() ? "Re-activate" : "Deactivate").submit(true) %>
    <%= button("Cancel").href(bean.getRedirUrl()) %>
</labkey:form>
<% if (bean.isActivate()) { %>
<p><b>Note:</b> Re-activated users will be able to login normally, and all their previous
    group memberships will be preserved.</p>
<% } else { %>
<p><b>Note:</b> Deactivated users will no longer be able to login. However,
their information will be preserved for display purposes, and their group memberships
will be preserved in case they are re-activated at a later time.</p>
<% } %>
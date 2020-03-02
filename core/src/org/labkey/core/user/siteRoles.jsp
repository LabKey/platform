<%
    /*
     * Copyright (c) 2017-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.security.UserManager"%>
<%@ page import="org.labkey.api.security.roles.Role"%>
<%@ page import="org.labkey.api.util.HtmlString"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="java.util.Set"%>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<Set<Role>> me = (JspView<Set<Role>>) HttpView.currentView();
    Set<Role> roles = me.getModelBean();

    String userIdStr = getActionURL().getParameter("userId");
    int userId = 0;
    if (userIdStr != null)
    {
        userId = Integer.parseInt(userIdStr);
    }
%>

<br>
<labkey:panel type="portal" id="site-roles" className="lk-sg-section">
    <h4 class="labkey-page-section-header">Site Level Permissions : <%=HtmlString.of(UserManager.getEmailForId(userId))%></h4>
    <%
        for (Role role : roles)
        {
    %>
        <li><%=HtmlString.of(role.getDisplayName())%></li>
    <%
        }
    %>
</labkey:panel>

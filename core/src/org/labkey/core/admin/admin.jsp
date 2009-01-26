<%
/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.module.Module" %>
<%@ page import="org.labkey.api.settings.AdminConsole" %>
<%@ page import="org.labkey.api.settings.AdminConsole.AdminLink" %>
<%@ page import="org.labkey.api.settings.AdminConsole.SettingsLinkType" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.common.util.Pair" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.core.user.UserController" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<AdminController.AdminBean> me = (HttpView<AdminController.AdminBean>) HttpView.currentView();
    AdminController.AdminBean bean = me.getModelBean();

    ViewContext context = HttpView.currentContext();
    Container c = context.getContainer();
    String contextPath = context.getContextPath();
%>
<table class="labkey-admin-console"><tr>
<td>

<table><%
    for (SettingsLinkType type : SettingsLinkType.values())
    { %>

    <tr><td colspan="2"><b><%=type.name()%></b></td></tr><%
        for (AdminLink link : AdminConsole.getLinks(type))
        { %>
    <tr><td colspan="2">[<a href="<%=h(link.getUrl())%>"><%=h(link.getText())%></a>]</td></tr><%
        } %>
    <tr><td colspan="2">&nbsp;</td></tr><%
    }
%>
    <tr><td colspan="2"><%
        include(new UserController.ImpersonateView(c, true), out);
    %>
    </td></tr>

    <tr><td colspan="2">&nbsp;</td></tr>

    <tr><td colspan="2"><b>Active Users in the Last Hour</b></td></tr><%

    for (Pair<String, Long> pair : bean.active)
    {
        %><tr><td><%=h(pair.getKey())%></td><td><%=pair.getValue()%> minutes ago</td></tr><%
    }
%></table>
</td>

<td>
<table class="labkey-data-region">
    <tr><td colspan="2"><b>Core Database Configuration</b></td></tr>
    <tr><td>Server URL</td><td><%=bean.scope.getURL() %></td></tr>
    <tr><td>Product Name</td><td><%=bean.scope.getDatabaseProductName() %></td></tr>
    <tr><td>Product Version</td><td><%=bean.scope.getDatabaseProductVersion() %></td></tr>
    <tr><td>JDBC Driver Name</td><td><%=bean.scope.getDriverName() %></td></tr>
    <tr><td>JDBC Driver Version</td><td><%=bean.scope.getDriverVersion() %></td></tr>

    <tr><td>&nbsp;</td></tr>

    <tr><td colspan="2"><b>Runtime Information</b></td></tr>
    <tr><td>Mode</td><td><%=bean.mode%></td></tr>
    <tr><td>Servlet Container</td><td><%=bean.servletContainer%></td></tr>
    <tr><td>Java Runtime</td><td><%=bean.javaVersion%></td></tr>
    <tr><td>Java Home</td><td><%=bean.javaHome%></td></tr>
    <tr><td>Username</td><td><%=bean.userName%></td></tr>
    <tr><td>User Home Dir</td><td><%=bean.userHomeDir%></td></tr>
    <tr><td>Webapp Dir</td><td><%=bean.webappDir%></td></tr>
    <tr><td>OS</td><td><%=bean.osName%></td></tr>

    <tr><td>&nbsp;</td></tr>
</table>
<table class="labkey-data-region">
    <tr><td colspan="4"><b>Module Information</b></td></tr><%

    for (Module module : bean.modules)
    {%>
    <tr class="labkey-header">
        <td valign="middle">
            <a href="#" onclick="return toggleLink(this, false);">
                <img src="<%= contextPath %>/_images/plus.gif">
            </a>
        </td>
        <td colspan="3"><%=h(module.getName())%> <%=h(module.getFormattedVersion())%></td>
    </tr><%
        for (Map.Entry<String, String> entry : module.getProperties().entrySet())
        {%>
    <tr style="display:none">
        <td />
        <td>&nbsp;&nbsp;</td>
        <td><%=h(entry.getKey())%></td>
        <td><%=h(entry.getValue())%></td>
    </tr><%
        }
    }%>
</table>

</td>
</tr></table>

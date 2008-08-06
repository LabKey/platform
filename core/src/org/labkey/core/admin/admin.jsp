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
<%@ page import="org.labkey.api.module.Module" %>
<%@ page import="org.labkey.api.security.AuthenticationManager" %>
<%@ page import="org.labkey.api.settings.AdminConsole" %>
<%@ page import="org.labkey.api.settings.AdminConsole.*" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.common.util.Pair" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<AdminController.AdminBean> me = (HttpView<AdminController.AdminBean>) HttpView.currentView();
    AdminController.AdminBean bean = me.getModelBean();

    ViewContext context = HttpView.currentContext();
    org.labkey.api.security.User user = context.getUser();
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
    <tr><td colspan="2">
    <form method="get" action="impersonate.view">
        <table>
        <tr><td><b>Impersonate User</b></td></tr>
        <tr><td><%
            if (user.isImpersonated())
            {
        %>
        Already impersonating; click <a href="<%=h(AuthenticationManager.getLogoutURL())%>">here</a> to change back to <%=h(user.getImpersonatingUser().getDisplayName(context))%>.<%
            }
            else
            {
        %>
            <select id="email" name="email" style="width:200"><%
                for (String email : bean.emails)
                {%>
                <option value="<%=h(email)%>" <%=(email.equals(user.getEmail())) ? "selected" : ""%>><%=h(email)%></option ><%
                }
            %>
            </select><br>
            <input style="vertical-align:bottom" type=image src="<%=PageFlowUtil.buttonSrc("Impersonate")%>"><%
            }
            %>
        </td></tr>
        </table>
    </form>
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
    <tr><td>Server URL</td><td><%=bean.schema.getURL() %></td></tr>
    <tr><td>Product Name</td><td><%=bean.schema.getDatabaseProductName() %></td></tr>
    <tr><td>Product Version</td><td><%=bean.schema.getDatabaseProductVersion() %></td></tr>
    <tr><td>JDBC Driver Name</td><td><%=bean.schema.getDriverName() %></td></tr>
    <tr><td>JDBC Driver Version</td><td><%=bean.schema.getDriverVersion() %></td></tr>

    <tr><td>&nbsp;</td></tr>

    <tr><td colspan="2"><b>Runtime Information</b></td></tr>
    <tr><td>Mode</td><td><%=bean.mode%></td></tr>
    <tr><td>Servlet Container</td><td><%=bean.servletContainer%></td></tr>
    <tr><td>Java Runtime</td><td><%=bean.javaVersion%></td></tr>
    <tr><td>Username</td><td><%=bean.userName%></td></tr>
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
        <td><%=h(module.getName())%></td>
        <td>Version</td>
        <td><%=module.getFormattedVersion()%></td>
    </tr><%
        for (String property : module.getMetaData().keySet())
        {%>
    <tr style="display:none">
        <td></td>
        <td></td>
        <td><%=h(property)%></td>
        <td><%=h(module.getMetaData().get( property ))%></td>
    </tr><%
        }
    }%>
</table>

</td>
</tr></table>
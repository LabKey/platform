<%
/*
 * Copyright (c) 2004-2014 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.CoreSchema" %>
<%@ page import="org.labkey.api.module.Module" %>
<%@ page import="org.labkey.api.settings.AdminConsole" %>
<%@ page import="org.labkey.api.settings.AdminConsole.AdminLink" %>
<%@ page import="org.labkey.api.settings.AdminConsole.SettingsLinkType" %>
<%@ page import="org.labkey.api.util.GUID" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<AdminController.AdminBean> me = (HttpView<AdminController.AdminBean>) HttpView.currentView();
    AdminController.AdminBean bean = me.getModelBean();
    Container c = getContainer();
%>
<table class="labkey-admin-console"><tr>
<td>

    <table><%
    for (SettingsLinkType type : SettingsLinkType.values())
    { %>

    <tr><td colspan="2"><b><%=h(type.name())%></b></td></tr><%
        for (AdminLink link : AdminConsole.getLinks(type))
        { %>
    <tr><td colspan="2"><%=textLink(h(link.getText()), link.getUrl())%></td></tr><%
        } %>
    <tr><td colspan="2">&nbsp;</td></tr><%
    } %>
    </table>

</td>
<td>

    <table>
    <%
        String location=null;
        try
        {
            Class cls = CoreSchema.getInstance().getSchema().getScope().getDelegateClass();
            location = cls.getProtectionDomain().getCodeSource().getLocation().toString();
        }
        catch (Exception x)
        {
        }
    %>
        <tr><td colspan="2"><b>Core Database Configuration</b></td></tr>
        <tr><td class="labkey-form-label">Server URL</td><td id="databaseServerURL"><%=h(bean.scope.getURL())%></td></tr>
        <tr><td class="labkey-form-label">Product Name</td><td id="databaseProductName"><%=h(bean.scope.getDatabaseProductName())%></td></tr>
        <tr><td class="labkey-form-label">Product Version</td><td id="databaseProductVersion"><%=h(bean.scope.getDatabaseProductVersion())%></td></tr>
        <tr><td class="labkey-form-label">JDBC Driver Name</td><td id="databaseDriverName"><%=h(bean.scope.getDriverName())%></td></tr>
        <tr><td class="labkey-form-label">JDBC Driver Version</td><td id="databaseDriverVersion"><%=h(bean.scope.getDriverVersion())%></td></tr><%
        if (null != location)
        {
            %><tr><td class="labkey-form-label">JDBC Driver Location</td><td id="databaseDriverLocation"><%=h(location)%></td></tr><%
        }
        %><tr><td>&nbsp;</td></tr>

        <tr><td colspan="2"><b>Runtime Information</b></td></tr>
        <tr><td class="labkey-form-label">Mode</td><td><%=h(bean.mode)%></td></tr>
        <tr><td class="labkey-form-label">Asserts</td><td><%=h(bean.asserts)%></td></tr>
        <tr><td class="labkey-form-label">Servlet Container</td><td><%=h(bean.servletContainer)%></td></tr>
        <tr><td class="labkey-form-label">Java Runtime</td><td><%=h(bean.javaVersion)%></td></tr>
        <tr><td class="labkey-form-label">Java Home</td><td><%=h(bean.javaHome)%></td></tr>
        <tr><td class="labkey-form-label">Username</td><td><%=h(bean.userName)%></td></tr>
        <tr><td class="labkey-form-label">User Home Dir</td><td><%=h(bean.userHomeDir)%></td></tr>
        <tr><td class="labkey-form-label">Webapp Dir</td><td><%=h(bean.webappDir)%></td></tr>
        <tr><td class="labkey-form-label">OS</td><td><%=h(bean.osName)%></td></tr>
        <tr><td class="labkey-form-label">Working Dir</td><td><%=h(bean.workingDir)%></td></tr>
        <tr><td class="labkey-form-label">Server GUID</td><td><%=h(bean.serverGuid)%></td></tr>
        <tr><td class="labkey-form-label">Server Time</td><td><%=h(DateUtil.formatDateTime(c))%></td></tr>
        <tr><td>&nbsp;</td></tr>
    </table>
    <table>
        <tr><td colspan="2"><b>Module Information</b>&nbsp;&nbsp;<%=textLink("Module Details", new ActionURL(AdminController.ModulesAction.class, c))%></td></tr><%

        for (Module module : bean.modules)
        {
            String guid = GUID.makeGUID();
            %>
        <tr class="labkey-header">
            <td valign="middle" width="9">
                <a id="<%= h(guid) %>" onclick="return LABKEY.Utils.toggleLink(this, false);">
                    <img src="<%=getWebappURL("_images/plus.gif")%>">
                </a>
            </td>
            <td>
                <span onclick="return LABKEY.Utils.toggleLink(document.getElementById('<%= h(guid) %>'), false);"><%=h(module.getName())%> <%=h(module.getFormattedVersion())%></span>
            </td>
        </tr>
        <tr style="display:none">
            <td width="9"></td>
            <td style="padding-left: 2em">
                <% if (!StringUtils.isEmpty(module.getDescription()))
                {
                    %><div style="padding-left:6px;"><%=h(module.getDescription())%></div><%
                }
                %><table cellpadding="0"><%
                    for (Map.Entry<String, String> entry : new TreeMap<>(module.getProperties()).entrySet())
                    {
                    %><tr>
                        <td nowrap="true" class="labkey-form-label"><%=h(entry.getKey())%></td>
                        <td nowrap="true"><%=h(entry.getValue())%></td>
                    </tr><%
                    } %>
                </table>
            </td>
        </tr><%
        }%>
    </table>

</td>
<td>

    <table>
        <tr><td colspan="2"><b>Active Users in the Last Hour</b></td></tr><%

        for (Pair<String, Long> pair : bean.active)
        {
            %><tr><td><%=h(pair.getKey())%></td><td><%=pair.getValue()%> minutes ago</td></tr><%
        }

    %>
    </table>

</td></tr>
</table>

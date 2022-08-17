<%
/*
 * Copyright (c) 2011-2018 LabKey Corporation
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
<%@ page import="org.labkey.api.files.FileContentService" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController.NewInstallSiteSettingsForm" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    NewInstallSiteSettingsForm bean = ((JspView<NewInstallSiteSettingsForm>) HttpView.currentView()).getModelBean();

    boolean isStartupPropSet = FileContentService.get().isFileRootSetViaStartupProperty();
    boolean isAppFileSystemSet = null != AppProps.getInstance().getFileSystemRoot() && AppProps.getInstance().getFileSystemRoot().isDirectory();
%>

<labkey:errors/>

<labkey:form method="POST" id="newInstallSettingsForm">
<% if (!(isAppFileSystemSet && isStartupPropSet)) { %>
    <h3 style="margin-bottom: 2px;"><label for="rootPath">Files Location</label></h3>
    <div style="margin-bottom: 10px;">
        This is where LabKey Server stores and looks for data files. The server will
        automatically create subdirectories to match the organization within
        LabKey Server. You can later configure the server to look in other file locations.
        <br/>
        <input autofocus="autofocus" type="text" id="rootPath" name="rootPath" style="width: 100%; max-width: 40em;" value="<%=h(bean.getRootPath())%>">
    </div>
<%}%>
    <h3 style="margin-bottom: 2px;"><label for="siteName">Site Name</label></h3>
    <div style="margin-bottom: 10px;">
        This is displayed in the header of each page and in emails sent by the server.
        <br/>
        <input type="text" id="siteName" name="siteName" style="width: 100%; max-width: 40em;" value="<%=h(bean.getSiteName())%>">
    </div>

    <h3 style="margin-bottom: 2px;"><label for="notificationEmail">Notification Email Address</label></h3>
    <div style="margin-bottom: 10px;">
        This is the &quot;from&quot; address used when sending notification
        emails. You may wish to set this to an address other than your own.
        <br/>
        <input type="text" id="notificationEmail" name="notificationEmail" style="width: 100%; max-width: 40em;" value="<%=h(bean.getNotificationEmail())%>">
    </div>

    <h3 style="margin-bottom: 2px;"><label>Error and Usage Reporting</label></h3>
    <div style="margin-bottom: 10px;">
    <%
        if (!AppProps.getInstance().isDevMode())
        {
    %>
        This server is configured to report exception and basic usage information to the LabKey team to identify bugs and prioritize product enhancements. No confidential data is submitted.<br>
        We strongly recommend this basic level of reporting. You can review the data submitted and
    <%
        }
        else
        {
    %>
        Since this server is running in dev mode, <%=h(AppProps.getInstance().isSelfReportExceptions() ?
            "exceptions will be reported to the local server and usage reporting has been disabled" :
            "exception and usage reporting have both been disabled")%>. You can
    <%
        }
    %>adjust these settings via the Site Settings page in the Admin Console.
        <br/>
    </div>
    <%= button("Next").submit(true) %>
</labkey:form>
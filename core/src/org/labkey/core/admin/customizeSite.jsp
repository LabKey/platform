<%
/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.settings.AppProps"%>
<%@ page import="org.labkey.api.util.FolderDisplayMode" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.SystemMaintenance" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%=formatMissedErrors("form")%>
<%
    AdminController.SiteSettingsBean bean = ((JspView<AdminController.SiteSettingsBean>)HttpView.currentView()).getModelBean();
    AppProps appProps = AppProps.getInstance();
%>
<script type="text/javascript">
function testNetworkDrive()
{
    var preferenceForm = document.forms['preferences'];
    var networkDriveForm = document.forms['networkdrivetest'];
    if (preferenceForm.networkDriveLetter.value.length == 0)
    {
        alert("Please specify your drive letter before testing.");
        try {preferenceForm.networkDriveLetter.focus();} catch(x){}
        return;
    }

    if (preferenceForm.networkDrivePath.value.length == 0)
    {
        alert("Please specify your drive path before testing.");
        try {preferenceForm.networkDrivePath.focus();} catch(x){}
        return;
    }
    networkDriveForm.networkDriveLetter.value = preferenceForm.networkDriveLetter.value;
    networkDriveForm.networkDrivePath.value = preferenceForm.networkDrivePath.value;
    networkDriveForm.networkDriveUser.value = preferenceForm.networkDriveUser.value;
    networkDriveForm.networkDrivePassword.value = preferenceForm.networkDrivePassword.value;

    networkDriveForm.submit();
}

function testMascot()
{
    var preferenceForm = document.forms['preferences'];
    var mascotForm = document.forms['mascottest'];
    if (preferenceForm.mascotServer.value.length == 0)
    {
        alert("Please specify your mascot server before testing.");
        try {preferenceForm.mascotServer.focus();} catch(x){}
        return;
    }
    mascotForm.mascotServer.value = preferenceForm.mascotServer.value;
    mascotForm.mascotUserAccount.value = preferenceForm.mascotUserAccount.value;
    mascotForm.mascotUserPassword.value = preferenceForm.mascotUserPassword.value;
    mascotForm.mascotHTTPProxy.value = preferenceForm.mascotHTTPProxy.value;

    mascotForm.action = "<%= request.getContextPath() %>/MS2/mascotTest.view";
    mascotForm.submit();
}
    
function testSequest()
{
    var preferenceForm = document.forms['preferences'];
    var sequestForm = document.forms['sequesttest'];
    if (preferenceForm.sequestServer.value.length == 0)
    {
        alert("Please specify your Sequest server before testing.");
        try {preferenceForm.sequestServer.focus();} catch(x){}
        return;
    }
    sequestForm.sequestServer.value = preferenceForm.sequestServer.value;

    sequestForm.action = "<%= request.getContextPath() %>/ms2/sequestTest.view";
    sequestForm.submit();
}
</script>

<form name="preferences" enctype="multipart/form-data" method="post">
<input type="hidden" name="upgradeInProgress" value="<%=bean.upgradeInProgress ? 1 : 0%>" />

<table>
<%
if (bean.upgradeInProgress)
{%>
<tr>
    <td><p>You can use this page to customize your LabKey Server installation. If you prefer to customize it later, you can reach this page again by clicking <b>Manage Site->Admin Console->Customize Site</b>.</p>
Click the Save button at any time to accept the current settings and continue.</td>
</tr>
<%}%>
<tr>
    <td><%=PageFlowUtil.generateSubmitButton("Save")%></td>
</tr>
</table>

<table>
<tr>
    <td colspan=2>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Set system email, log in, and support properties (<%=bean.helpLink%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line><img height=1 width=1 src="<%=request.getContextPath()%>/_.gif"></td></tr>
<tr>
    <td class="labkey-form-label">System default domain (default domain for user log in)</td>
    <td><input type="text" name="defaultDomain" size="50" value="<%= h(appProps.getDefaultDomain()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Base server url (used to create links in emails sent by the system)</td>
    <td><input type="text" name="baseServerUrl" size="50" value="<%= h(appProps.getBaseServerUrl()) %>"></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>


<tr>
    <td colspan=2>Automatically check for updates to LabKey Server, and
        report anonymous usage statistics to the LabKey team (<%=bean.helpLink%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line><img height=1 width=1 src="<%=request.getContextPath()%>/_.gif"></td></tr>
<tr>
    <td class="labkey-form-label" valign="top" style="padding-top:2;">Check for updates and report usage to labkey.org.
        All data is transmitted securely over SSL.</td>
    <td>
    <table>
        <tr>
            <td valign="top"><input type="radio" name="usageReportingLevel" value="NONE" <%="NONE".equals(appProps.getUsageReportingLevel().toString()) ? "checked" : ""%>></td>
            <td><b>OFF</b> - Do not report any usage or check for updates</td>
        </tr>
        <tr>
            <td valign="top"><input type="radio" name="usageReportingLevel" value="LOW" <%="LOW".equals(appProps.getUsageReportingLevel().toString()) ? "checked" : ""%>></td>
            <td><b>ON, low</b> - Check for updates and report system information</td>
        </tr>
        <tr>
            <td valign="top"><input type="radio" name="usageReportingLevel" value="MEDIUM" <%="MEDIUM".equals(appProps.getUsageReportingLevel().toString()) ? "checked" : ""%>></td>
            <td><b>ON, medium</b> - Check for updates, report system information, organization, and administrator information from this page</td>
        </tr>
    </table>
    </td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td colspan=2>Automatically report exceptions to the LabKey team (<%=bean.helpLink%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line><img height=1 width=1 src="<%=request.getContextPath()%>/_.gif"></td></tr>
<tr>
    <td class="labkey-form-label" valign="top">Report exceptions to www.labkey.org. All data is transmitted securely over SSL.</td>
    <td>
        <table>
            <tr>
                <td valign="top"><input type="radio" name="exceptionReportingLevel" value="NONE" <%="NONE".equals(appProps.getExceptionReportingLevel().toString()) ? "checked" : ""%>></td>
                <td><b>OFF</b> - Do not report exceptions</td>
            </tr>
            <tr>
                <td valign="top"><input type="radio" name="exceptionReportingLevel" value="LOW" <%="LOW".equals(appProps.getExceptionReportingLevel().toString()) ? "checked" : ""%>></td>
                <td><b>ON, low</b> - Include anonymous system and exception information</td>
            </tr>
            <tr>
                <td valign="top"><input type="radio" name="exceptionReportingLevel" value="MEDIUM" <%="MEDIUM".equals(appProps.getExceptionReportingLevel().toString()) ? "checked" : ""%>></td>
                <td><b>ON, medium</b> - Include anonymous system and exception information, as well as the URL that triggered the exception</td>
            </tr>
            <tr>
                <td valign="top"><input type="radio" name="exceptionReportingLevel" value="HIGH" <%="HIGH".equals(appProps.getExceptionReportingLevel().toString()) ? "checked" : ""%>></td>
                <td><b>ON, high</b> - Include the above, plus the user's email address. The user will be contacted only for assistance in reproducing the bug, if necessary</td>
            </tr>
        </table>
    </td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Customize LabKey system properties (<%=bean.helpLink%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line><img height=1 width=1 src="<%=request.getContextPath()%>/_.gif"></td></tr>
<tr>
    <td class="labkey-form-label">Default Life Sciences Identifier (LSID) authority</td>
    <td><input type="text" name="defaultLsidAuthority" size="50" value="<%= h(appProps.getDefaultLsidAuthority()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Log memory usage frequency, in minutes (for debugging, set to 0 to disable)</td>
    <td><input type="text" name="memoryUsageDumpInterval" size="4" value="<%= h(appProps.getMemoryUsageDumpInterval()) %>"></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>System maintenance (<%=bean.helpLink%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line><img height=1 width=1 src="<%=request.getContextPath()%>/_.gif"></td></tr>
<tr>
    <td class="labkey-form-label">Perform regular system maintenance</td>
    <td>
        <table>
            <tr>
                <td valign="top"><input type="radio" name="systemMaintenanceInterval" value="never" <%="never".equals(appProps.getSystemMaintenanceInterval()) ? "checked" : ""%>></td>
                <td><b>Never</b> - Do not perform regular system &amp; database maintenance</td>
            </tr>
            <tr>
                <td valign="top"><input type="radio" name="systemMaintenanceInterval" value="daily" <%="daily".equals(appProps.getSystemMaintenanceInterval()) ? "checked" : ""%>></td>
                <td><b>Daily</b> - Perform regular system &amp; database maintenance every day at <input type="text" name="systemMaintenanceTime" value="<%=SystemMaintenance.formatSystemMaintenanceTime(appProps.getSystemMaintenanceTime())%>" size="4">.  Enter this time in 24-hour format (e.g., 0:30 for 12:30AM, 14:00 for 2:00PM).</td>
            </tr>
        </table>
    </td>
</tr>
<tr>
    <td></td>
    <td>[<a href="runSystemMaintenance.view" target="systemMaintenance">Run system maintenance now</a>]</td>
</tr>

<tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Configure SSL (<%=bean.helpLink%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line><img height=1 width=1 src="<%=request.getContextPath()%>/_.gif"></td></tr>
<tr>
    <td class="labkey-form-label">Require SSL connections (users must connect via SSL)</td>
    <td><input type="checkbox" name="sslRequired" <%=appProps.isSSLRequired() ? "checked" : ""%>></td>
</tr>
<tr>
    <td class="labkey-form-label">SSL port number (specified in server config file)</td>
    <td><input type="text" name="sslPort" value="<%=appProps.getSSLPort()%>" size="6"></td>
</tr>

<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td colspan=2>Configure pipeline settings (<%=bean.helpLink%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line><img height=1 width=1 src="<%=request.getContextPath()%>/_.gif"></td></tr>
<tr>
    <td class="labkey-form-label">Pipeline tools directory</td>
    <td><input type="text" name="pipelineToolsDirectory" size="50" value="<%= h(appProps.getPipelineToolsDirectory()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Enable Perl pipeline</td>
    <td><input type="checkbox" name="perlPipelineEnabled" <%=appProps.isPerlPipelineEnabled() ? "checked" : ""%>></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Map network drive (Windows only) (<%=bean.helpLink%>)</td>
</tr>
<tr>
    <td class="labkey-form-label">Drive letter</td>
    <td><input type="text" name="networkDriveLetter" value="<%= appProps.getNetworkDriveLetter() %>" size="1" maxlength="1"></td>
</tr>
<tr>
    <td class="labkey-form-label">Path</td>
    <td><input type="text" name="networkDrivePath" value="<%= appProps.getNetworkDrivePath() %>"></td>
</tr>
<tr>
    <td class="labkey-form-label">User</td>
    <td><input type="text" name="networkDriveUser" value="<%= appProps.getNetworkDriveUser() %>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Password</td>
    <td><input type="text" name="networkDrivePassword" value="<%= appProps.getNetworkDrivePassword() %>"></td>
</tr>
<tr>
    <td></td>
    <td>[<a href="javascript:testNetworkDrive()">Test network drive settings</a>] - Note: Do not test if the drive is currently being accessed from within LabKey Server.</td>
</tr>

<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td colspan=2>Configure file system server (<%=bean.ftpHelpLink%>)</td>
</tr>
<tr>
    <td class="labkey-form-label">Server name</td>
    <td><input type="text" name="pipelineFTPHost" size="64" value="<%=appProps.getPipelineFTPHost()%>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Port</td>
    <td><input type="text" name="pipelineFTPPort" size="5" value="<%=appProps.getPipelineFTPPort()%>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Use SSL to connect (FTP server must be configured)</td>
    <td><input type="checkbox" name="pipelineFTPSecure" <%=appProps.isPipelineFTPSecure() ? "checked" : ""%>></td>
</tr>
<!--
<tr>
    <td></td>
    <td>[<a href="javascript:testPipelineFTP()">Test FTP Settings</a>]
</td>
</tr>
-->

<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td colspan=2>Configure Mascot settings (<%=bean.helpLink%>)</td>
</tr>
<tr>
    <td class="labkey-form-label">Mascot server</td>
    <td><input type="text" name="mascotServer" size="64" value="<%=appProps.getMascotServer()%>"></td>
</tr>
<tr>
    <td class="labkey-form-label">User</td>
    <td><input type="text" name="mascotUserAccount" size="50" value="<%=appProps.getMascotUserAccount()%>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Password</td>
    <td><input type="password" name="mascotUserPassword" size="50" value="<%=appProps.getMascotUserPassword()%>"></td>
</tr>
<tr>
    <td class="labkey-form-label">HTTP Proxy URL</td>
    <td><input type="text" name="mascotHTTPProxy" size="64" value="<%=appProps.getMascotHTTPProxy()%>"></td>
</tr>
<tr>
    <td></td>
    <td>[<a href="javascript:testMascot()">Test Mascot settings</a>]
</td>
</tr>

<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td colspan=2>Configure Sequest settings (<%=bean.helpLink%>)</td>
</tr>
<tr>
    <td class="labkey-form-label">Sequest server</td>
    <td><input type="text" name="sequestServer" size="64" value="<%=appProps.getSequestServer()%>"></td>
</tr>
<tr>
    <td></td>
    <td>[<a href="javascript:testSequest()">Test Sequest settings</a>]
</td>
</tr>

<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td colspan=2>Configure microarray settings (<%=bean.helpLink%>)</td>
</tr>
<tr>
    <td class="labkey-form-label">Microarray feature extraction server</td>
    <td><input type="text" name="microarrayFeatureExtractionServer" size="64" value="<%=appProps.getMicroarrayFeatureExtractionServer()%>"></td>
</tr>

<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td colspan=2>Configure caBIG&trade; (<%=bean.helpLink%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line><img height=1 width=1 src="<%=request.getContextPath()%>/_.gif"></td></tr>
<tr>
    <td class="labkey-form-label">Allow publishing folders to caBIG&trade;</td>
    <td><input type="checkbox" name="caBIGEnabled" <%=appProps.isCaBIGEnabled() ? "checked" : ""%>></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td colspan=2>Put web site in administrative mode (<%=bean.helpLink%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line><img height=1 width=1 src="<%=request.getContextPath()%>/_.gif"></td></tr>
<tr>
    <td class="labkey-form-label">Admin only mode (only site admins may log in)</td>
    <td><input type="checkbox" name="adminOnlyMode" <%=appProps.isUserRequestedAdminOnlyMode() ? "checked" : ""%>></td>
</tr>
<tr>
    <td class="labkey-form-label" valign="top">Message to users when site is in admin-only mode<br/>(Wiki formatting allowed)</td>
    <td><textarea id="adminOnlyMessage" name="adminOnlyMessage" cols="60" rows="3"><%= h(appProps.getAdminOnlyMessage()) %></textarea></td>
</tr>

<tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td><%=PageFlowUtil.generateSubmitButton("Save")%></td>
</tr>
</table>
</form>

<form name="mascottest" action="mascotTest.view" enctype="multipart/form-data" method="post"  <% if (!bean.testInPage) { %> target="_new" <% } %> >
    <input type="hidden" name="upgradeInProgress" value="<%=bean.upgradeInProgress ? 0 : 1%>" />
    <input type="hidden" name="mascotServer" value="" />
    <input type="hidden" name="mascotUserAccount" value="" />
    <input type="hidden" name="mascotUserPassword" value="" />
    <input type="hidden" name="mascotHTTPProxy" value="" />
</form>
<form name="sequesttest" action="sequestTest.view" enctype="multipart/form-data" method="post" <% if (!bean.testInPage) { %> target="_new" <% } %> >
    <input type="hidden" name="upgradeInProgress" value="<%=bean.upgradeInProgress ? 0 : 1%>" />
    <input type="hidden" name="sequestServer" value="" />
</form>
<form name="networkdrivetest" action="showNetworkDriveTest.view" enctype="multipart/form-data" method="post" target="_new">
    <input type="hidden" name="upgradeInProgress" value="<%=bean.upgradeInProgress ? 0 : 1%>" />
    <input type="hidden" name="networkDriveLetter" value="" />
    <input type="hidden" name="networkDrivePath" value="" />
    <input type="hidden" name="networkDriveUser" value="" />
    <input type="hidden" name="networkDrivePassword" value="" />
</form>

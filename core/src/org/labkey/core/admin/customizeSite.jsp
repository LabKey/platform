<%
/*
 * Copyright (c) 2005-2014 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.settings.AppProps"%>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Objects" %>
<%@ page import="java.io.File" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%=formatMissedErrors("form")%>
<%
    AdminController.SiteSettingsBean bean = ((JspView<AdminController.SiteSettingsBean>)HttpView.currentView()).getModelBean();
    AppProps.Interface appProps = AppProps.getInstance();
%>
<script type="text/javascript">

var testNetworkDrive;
var testMascot;
var submitSystemMaintenance;

(function(){

    testNetworkDrive = function()
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

    testMascot = function()
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

        mascotForm.action = LABKEY.ActionURL.buildURL("ms2","mascotTest","/");
        mascotForm.submit();
    }

    submitSystemMaintenance = function()
    {
        document.forms['systemMaintenance'].submit();
    }
})();
</script>

<form name="preferences" enctype="multipart/form-data" method="post"><labkey:csrf />
<input type="hidden" name="upgradeInProgress" value="<%=bean.upgradeInProgress ? 1 : 0%>" />

<table>
<%
if (bean.upgradeInProgress)
{%>
<tr>
    <td><p>You can use this page to customize your LabKey Server installation. If you prefer to customize it later, you can reach this page again by clicking <b>Admin->Site->Admin Console->Site Settings</b>.</p>
Click the Save button at any time to accept the current settings and continue.</td>
</tr>
<%}%>
<tr>
    <td><%= button("Save").submit(true) %></td>
</tr>
</table>

<table>
<tr>
    <th style="width: 35em;"></th>
    <th></th>
</tr>
<tr>
    <td colspan=2>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Set default domain for user sign in and base server url (<%=bean.helpLink%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label">System default domain (default domain for user log in)</td>
    <td><input type="text" id="defaultDomain" name="defaultDomain" size="50" value="<%= h(appProps.getDefaultDomain()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Base server url (used to create links in emails sent by the system)</td>
    <td><input type="text" name="baseServerUrl" size="50" value="<%= h(appProps.getBaseServerUrl()) %>"></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>


<tr>
    <td colspan=2>Automatically check for updates to LabKey Server and
        report anonymous usage statistics to the LabKey team (<%=bean.helpLink%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label" valign="top" style="padding-top:2;">Check for updates and report usage to labkey.org.
        All data is transmitted securely over SSL.</td>
    <td>
    <table>
        <tr>
            <td valign="top"><input type="radio" name="usageReportingLevel" value="NONE"<%=checked("NONE".equals(appProps.getUsageReportingLevel().toString()))%>></td>
            <td><b>OFF</b> - Do not report any usage or check for updates</td>
        </tr>
        <tr>
            <td valign="top"><input type="radio" name="usageReportingLevel" value="LOW"<%=checked("LOW".equals(appProps.getUsageReportingLevel().toString()))%>></td>
            <td><b>ON, low</b> - Check for updates and report system information</td>
        </tr>
        <tr>
            <td valign="top"><input type="radio" name="usageReportingLevel" value="MEDIUM"<%=checked("MEDIUM".equals(appProps.getUsageReportingLevel().toString()))%>></td>
            <td><b>ON, medium</b> - Check for updates, report system information, organization, and administrator information from this page</td>
        </tr>
    </table>
    </td>
</tr>
<tr>
    <td class="labkey-form-label" valign="top" style="padding-top:2;">Site administrator to use for Medium level reporting</td>
    <td>
        <select name="administratorContactEmail">
        <% List<Pair<Integer, String>> members = org.labkey.api.security.SecurityManager.getGroupMemberNamesAndIds("Administrators");
        for (Pair<Integer,String> member : members) { %>
              <option value="<%=h(member.getValue())%>"<%=selected(Objects.equals(member.getValue(), appProps.getAdministratorContactEmail()))%>><%=h(member.getValue())%></option>
        <% } %>
        </select>
    </td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td colspan=2>Automatically report exceptions to the LabKey team (<%=bean.helpLink%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label" valign="top">Report exceptions to www.labkey.org. All data is transmitted securely over SSL.</td>
    <td>
        <table>
            <tr>
                <td valign="top"><input type="radio" name="exceptionReportingLevel" value="NONE"<%=checked("NONE".equals(appProps.getExceptionReportingLevel().toString()))%>></td>
                <td><b>OFF</b> - Do not report exceptions</td>
            </tr>
            <tr>
                <td valign="top"><input type="radio" name="exceptionReportingLevel" value="LOW"<%=checked("LOW".equals(appProps.getExceptionReportingLevel().toString()))%>></td>
                <td><b>ON, low</b> - Include anonymous system and exception information</td>
            </tr>
            <tr>
                <td valign="top"><input type="radio" name="exceptionReportingLevel" value="MEDIUM"<%=checked("MEDIUM".equals(appProps.getExceptionReportingLevel().toString()))%>></td>
                <td><b>ON, medium</b> - Include anonymous system and exception information, as well as the URL that triggered the exception</td>
            </tr>
            <tr>
                <td valign="top"><input type="radio" name="exceptionReportingLevel" value="HIGH"<%=checked("HIGH".equals(appProps.getExceptionReportingLevel().toString()))%>></td>
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
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label">Default Life Sciences Identifier (LSID) authority</td>
    <td><input type="text" name="defaultLsidAuthority" size="50" value="<%= h(appProps.getDefaultLsidAuthority()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Log memory usage frequency, in minutes (for debugging, set to 0 to disable)</td>
    <td><input type="text" name="memoryUsageDumpInterval" size="4" value="<%= h(appProps.getMemoryUsageDumpInterval()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Maximum file size, in bytes, to allow in database BLOBs</td>
    <td><input type="text" name="maxBLOBSize" size="10" value="<%= h(appProps.getMaxBLOBSize()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Require ExtJS v3.4.1 be loaded on each page</td>
    <td><input type="checkbox" name="ext3Required"<%=checked(appProps.isExt3Required())%>></td>
</tr>
<tr>
    <td class="labkey-form-label">Require ExtJS v3.x based Client API be loaded on each page</td>
    <td><input type="checkbox" name="ext3Required"<%=checked(appProps.isExt3Required())%>></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Configure SSL (<%=bean.helpLink%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label">Require SSL connections (users must connect via SSL)</td>
    <td><input type="checkbox" name="sslRequired"<%=checked(appProps.isSSLRequired())%>></td>
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
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label">Pipeline tools<%= helpPopup("Pipeline Tools", "A '" + File.pathSeparator + "' separated list of directories on the web server containing executables that are run for pipeline jobs (e.g. TPP or XTandem)") %></td>
    <td><input type="text" name="pipelineToolsDirectory" size="50" value="<%= h(appProps.getPipelineToolsDirectory()) %>"></td>
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
    <td><input type="password" name="networkDrivePassword" value="<%= appProps.getNetworkDrivePassword() %>"></td>
</tr>
<tr>
    <td></td>
    <td><%=textLink("Test network drive settings", "javascript:testNetworkDrive()")%> - Note: Do not test if the drive is currently being accessed from within LabKey Server.</td>
</tr>

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
    <td><input type="text" name="mascotUserAccount" size="50" value="<%=appProps.getMascotUserAccount()%>" autocomplete="off"></td>
</tr>
<tr>
    <td class="labkey-form-label">Password</td>
    <td><input type="password" name="mascotUserPassword" size="50" value="<%=appProps.getMascotUserPassword()%>" autocomplete="off"></td>
</tr>
<tr>
    <td class="labkey-form-label">HTTP Proxy URL</td>
    <td><input type="text" name="mascotHTTPProxy" size="64" value="<%=appProps.getMascotHTTPProxy()%>"></td>
</tr>
<tr>
    <td></td>
    <td><%=textLink("Test Mascot settings", "javascript:testMascot()")%>
</td>
</tr>

<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td colspan=2>Ribbon Bar Message (<%=text(bean.helpLink)%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label">Display Message</td>
    <td><input type="checkbox" name="showRibbonMessage"<%=checked(appProps.isShowRibbonMessage())%>></td>
</tr>
<tr>
    <td class="labkey-form-label">Message HTML</td>
    <td><textarea id="ribbonMessageHtml" name="ribbonMessageHtml" cols="60" rows="3"><%=h(appProps.getRibbonMessageHtml())%></textarea></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td colspan=2>Put web site in administrative mode (<%=text(bean.helpLink)%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label">Admin only mode (only site admins may log in)</td>
    <td><input type="checkbox" name="adminOnlyMode"<%=checked(appProps.isUserRequestedAdminOnlyMode())%>></td>
</tr>
<tr>
    <td class="labkey-form-label" valign="top">Message to users when site is in admin-only mode<br/>(Wiki formatting allowed)</td>
    <td><textarea id="adminOnlyMessage" name="adminOnlyMessage" cols="60" rows="3"><%= h(appProps.getAdminOnlyMessage()) %></textarea></td>
</tr>

<tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td><%= button("Save").submit(true) %></td>
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
<form name="networkdrivetest" action="<%=h(buildURL(AdminController.ShowNetworkDriveTestAction.class))%>" enctype="multipart/form-data" method="post" target="_new">
    <input type="hidden" name="upgradeInProgress" value="<%=bean.upgradeInProgress ? 0 : 1%>" />
    <input type="hidden" name="networkDriveLetter" value="" />
    <input type="hidden" name="networkDrivePath" value="" />
    <input type="hidden" name="networkDriveUser" value="" />
    <input type="hidden" name="networkDrivePassword" value="" />
</form>

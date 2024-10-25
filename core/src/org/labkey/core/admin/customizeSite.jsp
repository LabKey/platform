<%
/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.UserManager"%>
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.MothershipReport" %>
<%@ page import="org.labkey.api.util.UsageReportingLevel" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="java.io.File" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Objects" %>
<%@ page import="java.util.TreeMap" %>
<%@ page import="static org.labkey.api.security.SecurityManager.SECONDS_PER_DAY" %>
<%@ page import="static org.labkey.api.util.ExceptionReportingLevel.*" %>
<%@ page import="static org.labkey.api.settings.SiteSettingsProperties.*" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%=formatMissedErrors("form")%>
<%
    AdminController.SiteSettingsBean bean = ((JspView<AdminController.SiteSettingsBean>)HttpView.currentView()).getModelBean();
    AppProps appProps = AppProps.getInstance();
    boolean hasAdminOpsPerms = getContainer().hasPermission(getUser(), AdminOperationsPermission.class);
%>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">

var submitSystemMaintenance;

(function(){

    submitSystemMaintenance = function()
    {
        document.forms['systemMaintenance'].submit();
    }
})();

var enableExceptionTest = function() {
    var el = document.getElementById('testExceptionReport');
    var level = document.querySelector('input[name="exceptionReportingLevel"]:checked').value;
    enableTestButton(el, level);
};

var enableTestButton = function(el, level) {
    if ("NONE" == level)
    {
        LABKEY.Utils.addClass(el, 'labkey-disabled-button');
    }
    else
    {
        LABKEY.Utils.removeClass(el, 'labkey-disabled-button');
    }
};

var testUsageReport = function() {
    testMothershipReport('CheckForUpdates', '<%=UsageReportingLevel.ON%>', true);
};

var testExceptionReport = function(download) {
    var level = document.querySelector('input[name="exceptionReportingLevel"]:checked').value;
    testMothershipReport('ReportException', level, download);
};

var testMothershipReport = function (type, level, download) {
    var params = { type: type, level: level };
    if (download) {
        params.download = true;
    }
    var url = LABKEY.ActionURL.buildURL("admin", "testMothershipReport", null, params);
    if (download) {
        window.location = url;
    }
    else {
        window.open(url, '_blank', 'noopener noreferrer');
    }
};
</script>

<labkey:form name="preferences" enctype="multipart/form-data" method="post">
<input type="hidden" name="upgradeInProgress" value="<%=bean._upgradeInProgress ? 1 : 0%>" />

<table>
<%
if (bean._upgradeInProgress)
{%>
<tr>
    <td><p>You can use this page to customize your LabKey Server installation. If you prefer to customize it later, you can reach this page again by clicking <strong>Admin->Site->Admin Console->Site Settings</strong>.</p>
Click the Save button at any time to accept the current settings and continue.</td>
</tr>
<%}%>
<tr>
    <td>
        <%= hasAdminOpsPerms ? button("Save").submit(true) : HtmlString.EMPTY_STRING %>
        <%= button(!hasAdminOpsPerms ? "Done" : "Cancel").href(new AdminController.AdminUrlsImpl().getAdminConsoleURL()) %>
    </td>
</tr>
</table>

<table class="lk-fields-table">
<tr>
    <th style="width: 35em;"></th>
    <th></th>
</tr>
<%=getTroubleshooterWarning(hasAdminOpsPerms, HtmlString.unsafe("<tr>\n" +
        "        <td colspan=2>&nbsp;</td>\n" +
        "    </tr>\n" +
        "    <tr>\n" +
        "        <td colspan=2>"), HtmlString.unsafe("</td>\n" +
        "    </tr>"))%>
<tr>
    <td colspan=2>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Set site administrators (<%=bean.getSiteSettingsHelpLink("siteadmins")%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label" valign="top">Primary site administrator</td>
    <td>
        <select name="<%=administratorContactEmail%>" id="<%=administratorContactEmail%>">
            <%
                List<User> siteAdmins = UserManager.getSiteAdmins();
                String selectedAdminEmail = appProps.getAdministratorContactEmail(false);
                for (User siteAdmin : siteAdmins) { %>
                    <option value="<%=h(siteAdmin.getEmail())%>"<%=selected(Objects.equals(siteAdmin.getEmail(), selectedAdminEmail))%>><%=h(siteAdmin.getEmail())%></option>
            <% } %>
        </select>
    </td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>URL settings (<%=bean.getSiteSettingsHelpLink("url")%>)</td>

</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label">Base server URL (used to create links in emails sent by the system)</td>
    <td><input type="text" name="<%=baseServerURL%>" id="<%=baseServerURL%>" size="50" value="<%= h(appProps.getBaseServerUrl()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Use "path first" urls (/home/project-begin.view)</td>
    <td><labkey:checkbox id="<%=useContainerRelativeURL.name()%>" name="<%=useContainerRelativeURL.name()%>" checked="<%= appProps.getUseContainerRelativeURL() %>" value="true" /></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>


<tr>
    <td colspan=2>Automatically check for updates to LabKey Server and
        report usage statistics to LabKey. (<%=bean.getSiteSettingsHelpLink("usage")%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label" style="vertical-align: top">Check for updates and report usage statistics to the LabKey team.<br>
        LabKey uses this data to prioritize LabKey Server enhancements. Turn this on to ensure the
        features you use are maintained and improved over time.<br>All data is transmitted securely over HTTPS.
    </td>
    <td>
        <table>
<%
        if (MothershipReport.shouldReceiveMarketingUpdates())
        {
%>
            <tr>
                <td><span>Update checks and usage reporting are automatically <strong>on</strong> for servers running a LabKey Server Community Edition.</span></td>

            </tr>
<%
        }
        else
        {
%>
            <tr>
                <td style="vertical-align: top">
                    <label>
                        <labkey:input formGroup="false" type="radio" name="<%=(usageReportingLevel.name())%>" id='<%=(usageReportingLevel + "1")%>' onChange="enableUsageTest();"
                               value="<%=UsageReportingLevel.NONE%>" checked="<%=(appProps.getUsageReportingLevel() == UsageReportingLevel.NONE)%>" />
                        <strong>OFF</strong> - Do not check for updates or report any usage data.
                    </label>
                </td>
            </tr>
            <tr>
                <td style="vertical-align: top">
                    <label>
                        <labkey:input formGroup="false" type="radio" name="<%=(usageReportingLevel.name())%>" id='<%=(usageReportingLevel + "2")%>' onChange="enableUsageTest();"
                               value="<%=UsageReportingLevel.ON%>" checked="<%=(appProps.getUsageReportingLevel() == UsageReportingLevel.ON)%>" />
                        <strong>ON</strong> - Check for updates and report system information, usage data, and organization details.
                    </label>
                </td>
            </tr>
<%
        }
%>
            <tr>
                <td style="padding: 5px 0 5px;" colspan="2">
                            <%=link("View", AdminController.ViewUsageStatistics.class)%>
                            <%=button("Download").id("testUsageReportDownload").onClick("testUsageReport(); return false;")%>
                    Generate an example usage report. <strong>No data will be submitted.</strong></td>
            </tr>
        </table>
    </td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td colspan=2>Automatically report exceptions (<%=bean.getSiteSettingsHelpLink("exception")%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label" style="vertical-align: top">Report exceptions to the LabKey team who will use this information to identify and fix product issues encountered on your deployment.<br>All data is transmitted securely over HTTPS.</td>
    <td>
        <table>
            <tr>
                <td valign="top">
                    <label>
                        <labkey:input formGroup="false" type="radio" name="<%=exceptionReportingLevel.name()%>" onChange="enableExceptionTest();" id='<%=(exceptionReportingLevel + "1")%>' value="<%=NONE%>" checked="<%=(appProps.getExceptionReportingLevel() == NONE)%>" />
                        <strong>OFF</strong> - Do not report exceptions.
                    </label>
                </td>
            </tr>
            <tr>
                <td valign="top">
                    <label>
                        <labkey:input formGroup="false" type="radio" name="<%=exceptionReportingLevel.name()%>" onChange="enableExceptionTest();" id='<%=(exceptionReportingLevel + "2")%>' value="<%=LOW%>" checked="<%=(appProps.getExceptionReportingLevel() == LOW)%>" />
                        <strong>ON, low</strong> - Include anonymous system and exception information.
                    </label>
                </td>
            </tr>
            <tr>
                <td valign="top">
                    <label>
                        <labkey:input formGroup="false" type="radio" name="<%=exceptionReportingLevel.name()%>" onChange="enableExceptionTest();" id='<%=(exceptionReportingLevel + "3")%>' value="<%=MEDIUM%>" checked="<%=(appProps.getExceptionReportingLevel() == MEDIUM)%>" />
                        <strong>ON, medium</strong> - Include anonymous system and exception information, as well as the URL that triggered the exception.
                    </label>
                </td>
            </tr>
            <tr>
                <td valign="top">
                    <label>
                        <labkey:input formGroup="false" type="radio" name="<%=exceptionReportingLevel.name()%>" onChange="enableExceptionTest();" id='<%=(exceptionReportingLevel + "4")%>' value="<%=HIGH%>" checked="<%=(appProps.getExceptionReportingLevel() == HIGH)%>" />
                        <strong>ON, high</strong> - Include the above, plus the user's email address. The user will be contacted only for assistance in reproducing the bug, if necessary.
                    </label>
                </td>
            </tr>
            <tr >
                <td style="padding: 5px 0 5px;" colspan="2">
                    <%=button("View").id("testExceptionReport").onClick("testExceptionReport(false); return false;").enabled(appProps.getExceptionReportingLevel() != NONE)%>
                    <%=button("Download").id("testExceptionReportDownload").onClick("testExceptionReport(true); return false;").enabled(appProps.getExceptionReportingLevel() != NONE)%>
                    Generate an example report for the selected level. <strong>No data will be submitted.</strong></td>
            </tr>
        </table>
    </td>
</tr>
<%-- Only show this option if the mothership module has enabled it --%>
<% if (bean._showSelfReportExceptions) { %>
<tr>
    <td class="labkey-form-label" valign="top">Report exceptions to the local server</td>
    <td>
        <label for="<%=selfReportExceptions%>">
            <input type="checkbox" name="<%=selfReportExceptions%>" id="<%=selfReportExceptions%>"<%=checked(appProps.isSelfReportExceptions())%>/> Self-reporting is always at the "high" level described above
        </label>
    </td>
</tr>
<% } %>
<tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Customize LabKey system properties (<%=bean.getSiteSettingsHelpLink("props")%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label">Log memory usage frequency, in minutes (for debugging, set to 0 to disable)</td>
    <td><input type="text" name="<%=memoryUsageDumpInterval%>" id="<%=memoryUsageDumpInterval%>" size="4" value="<%=appProps.getMemoryUsageDumpInterval()%>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Maximum file size, in bytes, to allow in database BLOBs</td>
    <td><input type="text" name="<%=maxBLOBSize%>" id="<%=maxBLOBSize%>" size="10" value="<%=appProps.getMaxBLOBSize()%>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Require ExtJS v3.4.1 be loaded on each page</td>
    <td><input type="checkbox" name="<%=ext3Required%>" id="<%=ext3Required%>"<%=checked(appProps.isExt3Required())%>></td>
</tr>
<tr>
    <td class="labkey-form-label">Require ExtJS v3.x based Client API be loaded on each page</td>
    <td><input type="checkbox" name="<%=ext3APIRequired%>" id="<%=ext3APIRequired%>"<%=checked(appProps.isExt3APIRequired())%>></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Configure Security (<%=bean.getSiteSettingsHelpLink("security")%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label">Require SSL connections (users must connect via SSL)</td>
    <td><input type="checkbox" name="<%=sslRequired%>" id="<%=sslRequired%>"<%=checked(appProps.isSSLRequired())%>></td>
</tr>
<tr>
    <td class="labkey-form-label">SSL port number (specified in server config file)</td>
    <td><input type="text" name="<%=sslPort%>" id="<%=sslPort%>" value="<%=appProps.getSSLPort()%>" size="6"></td>
</tr>

<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td colspan=2>Configure API Keys (<%=bean.getSiteSettingsHelpLink("apiKey")%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label">Let users create API keys</td>
    <td><labkey:checkbox id="<%=allowApiKeys.name()%>" name="<%=allowApiKeys.name()%>" checked="<%=AppProps.getInstance().isAllowApiKeys()%>" value="true"/></td>
</tr>
<tr>
    <td class="labkey-form-label">Expire API keys</td>
<%
    final int currentExpiration = AppProps.getInstance().getApiKeyExpirationSeconds();
    Map<Integer, String> expirationOptions = new TreeMap<>(Comparator.comparing(key -> key));
    expirationOptions.put(-1, "Never");
    if (AppProps.getInstance().isDevMode())
    {
        expirationOptions.put(10, "10 seconds - for testing purposes only");
    }
    for (int days : new int[]{7, 30, 90, 180, 365})
        expirationOptions.put(days * SECONDS_PER_DAY, days + " days");

    // If current expiration is non-standard (perhaps set by a startup property) then add it, formatting label as a duration
    if (!expirationOptions.containsKey(currentExpiration))
        expirationOptions.put(currentExpiration, DateUtil.formatDuration(1000L * currentExpiration));
%>
    <td>
    <%=
        select()
            .name(apiKeyExpirationSeconds.name())
            .id(apiKeyExpirationSeconds.name())
            .addOptions(expirationOptions)
            .selected(currentExpiration)
            .className(null)
    %>
    </td>
</tr>
<tr>
    <td class="labkey-form-label">Let users create session keys</td>
    <td><labkey:checkbox id="<%=allowSessionKeys.name()%>" name="<%=allowSessionKeys.name()%>" checked="<%=appProps.isAllowSessionKeys()%>" value="true"/></td>
</tr>

<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td colspan=2>Configure pipeline settings (<%=bean.getSiteSettingsHelpLink("pipeline")%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label">Pipeline tools<%= helpPopup("Pipeline Tools", "A '" + File.pathSeparator + "' separated list of directories on the web server containing executables that are run for pipeline jobs (e.g. TPP or XTandem)") %></td>
    <td><input type="text" name="<%=pipelineToolsDirectory%>" id="<%=pipelineToolsDirectory%>" size="50" value="<%= h(appProps.getPipelineToolsDirectory()) %>"></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Ribbon Bar Message (<%=bean.getSiteSettingsHelpLink("ribbon")%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label">Display message</td>
    <td><input type="checkbox" name="<%=showRibbonMessage%>" id="<%=showRibbonMessage%>"<%=checked(appProps.isShowRibbonMessage())%>></td>
</tr>
<tr>
    <td class="labkey-form-label">Message HTML</td>
    <td><textarea name="<%=ribbonMessage%>" id="<%=ribbonMessage%>" cols="60" rows="3"><%=h(appProps.getRibbonMessage())%></textarea></td>
</tr>

<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td colspan=2>Put web site in administrative mode (<%=bean.getSiteSettingsHelpLink("adminonly")%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label">Admin only mode (only site admins may log in)</td>
    <td><input type="checkbox" name="<%=adminOnlyMode%>" id="<%=adminOnlyMode%>"<%=checked(appProps.isUserRequestedAdminOnlyMode())%>></td>
</tr>
<tr>
    <td class="labkey-form-label" valign="top">Message to users when site is in admin-only mode<br/>(Wiki formatting allowed)</td>
    <td><textarea id="adminOnlyMessage" name="<%=adminOnlyMessage%>" cols="60" rows="3"><%= h(appProps.getAdminOnlyMessage()) %></textarea></td>
</tr>

<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td colspan=2>HTTP security settings (<%=bean.getSiteSettingsHelpLink("http")%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label">X-Frame-Options</td>
    <td><select name="<%=XFrameOption%>" id="<%=XFrameOption%>">
        <% String option = appProps.getXFrameOption(); %>
        <%-- BREAKS GWT <option value="DENY" <%=selectedEq("DENY",option)%>>DENY</option> --%>
        <option value="SAMEORIGIN" <%=selectedEq("SAMEORIGIN",option)%>>SAMEORIGIN</option>
        <option value="ALLOW" <%=selectedEq("ALLOW",option)%>>Allow</option></select></td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label">Include a <code>Server</code> HTTP header in responses</td>
    <td><labkey:checkbox id="<%=includeServerHttpHeader.name()%>" name="<%=includeServerHttpHeader.name()%>" checked="<%=AppProps.getInstance().isIncludeServerHttpHeader()%>" value="true"/></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td colspan=2>Customize navigation options (<%=bean.getSiteSettingsHelpLink("nav")%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label">
        Always include inaccessible parent folders in project menu when child folder is accessible<%=helpPopup("Project menu access",
            "Unchecking this will only allow users to see folders in the project menu where they have permissions to see the root project and all parent folders.")%>
    </td>
    <td><input type="checkbox" name="<%=navAccessOpen%>" id="<%=navAccessOpen%>"<%=checked(appProps.isNavigationAccessOpen())%>></td>
</tr>
<tr><td>&nbsp;</td></tr>
<tr><td>&nbsp;</td></tr>
<tr><td>&nbsp;</td></tr>
<tr>
    <td>
        <%= hasAdminOpsPerms ? button("Save").submit(true) : HtmlString.EMPTY_STRING %>
        <%= button(!hasAdminOpsPerms ? "Done" : "Cancel").href(new AdminController.AdminUrlsImpl().getAdminConsoleURL()) %>
    </td>
</tr>
</table>
</labkey:form>

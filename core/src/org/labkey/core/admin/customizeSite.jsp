<%
/*
 * Copyright (c) 2008-2026 LabKey Corporation
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
<%@ page import="org.labkey.core.admin.AdminController.SiteSettingsBean" %>
<%@ page import="java.io.File" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Objects" %>
<%@ page import="java.util.TreeMap" %>
<%@ page import="java.util.stream.Stream" %>
<%@ page import="static org.labkey.api.security.SecurityManager.SECONDS_PER_DAY" %>
<%@ page import="static org.labkey.api.util.ExceptionReportingLevel.*" %>
<%@ page import="static org.labkey.api.settings.SiteSettingsProperties.*" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%=formatMissedErrors("form")%>
<%
    JspView<SiteSettingsBean> view = HttpView.currentView();
    SiteSettingsBean bean = view.getModelBean();
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
    if ("NONE" === level)
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
<%=getTroubleshooterWarning(hasAdminOpsPerms, HtmlString.unsafe("""
        <tr>
            <td colspan=2>&nbsp;</td>
        </tr>
        <tr>
            <td colspan=2>"""), HtmlString.unsafe("</td>\n" +
        "</tr>"))%>
<tr>
    <td colspan=2>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Set site administrator (<%=bean.getSiteSettingsHelpLink("siteadmins")%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label" style="vertical-align: top"><label for="<%=administratorContactEmail%>">Primary site administrator</label></td>
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
    <td colspan=2>URL setting (<%=bean.getSiteSettingsHelpLink("url")%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label"><label for="<%=baseServerURL%>">Base server URL (used to create links in emails sent by the system)</label></td>
    <td><input type="text" name="<%=baseServerURL%>" id="<%=baseServerURL%>" size="50" value="<%= h(appProps.getBaseServerUrl()) %>"></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>


<tr>
    <td colspan=2>Automatically check for updates and report usage statistics. (<%=bean.getSiteSettingsHelpLink("usage")%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label" style="vertical-align: top">Check for updates and report usage statistics to the LabKey team.<br>
        LabKey uses this data to prioritize enhancements. Turn this on to ensure the
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
                    <label for="<%=h(usageReportingLevel + "1")%>">
                        <labkey:input formGroup="false" type="radio" name="<%=(usageReportingLevel.name())%>" id='<%=(usageReportingLevel + "1")%>'
                               value="<%=UsageReportingLevel.NONE%>" checked="<%=(appProps.getUsageReportingLevel() == UsageReportingLevel.NONE)%>" />
                        <strong>Off</strong>: Do not check for updates or report any usage data.
                    </label>
                </td>
            </tr>
            <tr>
                <td style="vertical-align: top">
                    <label for="<%=h(usageReportingLevel + "2")%>">
                        <labkey:input formGroup="false" type="radio" name="<%=(usageReportingLevel.name())%>" id='<%=(usageReportingLevel + "2")%>'
                               value="<%=UsageReportingLevel.ON%>" checked="<%=(appProps.getUsageReportingLevel() == UsageReportingLevel.ON)%>" />
                        <strong>On</strong>: Report system information, usage data, and organization details, and show messages when important upgrades are available.
                    </label>
                </td>
            </tr>
            <tr>
                <td style="vertical-align: top">
                    <label for="<%=h(usageReportingLevel + "3")%>">
                        <labkey:input formGroup="false" type="radio" name="<%=(usageReportingLevel.name())%>" id='<%=(usageReportingLevel + "3")%>'
                                      value="<%=UsageReportingLevel.ON_WITHOUT_UPGRADE_MESSAGE%>" checked="<%=(appProps.getUsageReportingLevel() == UsageReportingLevel.ON_WITHOUT_UPGRADE_MESSAGE)%>" />
                        <strong>Report only</strong>: Report system information, usage data, and organization details, but do not show upgrade messages.
                    </label>
                </td>
            </tr>
<%
        }
%>
            <tr>
                <td style="padding: 5px 0 5px;" colspan="2">
                            <%=link("View", AdminController.ViewUsageStatisticsAction.class)%>
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
                <td style="vertical-align: top">
                    <label for="<%=h(exceptionReportingLevel + "1")%>">
                        <labkey:input formGroup="false" type="radio" name="<%=exceptionReportingLevel.name()%>" onChange="enableExceptionTest();" id='<%=(exceptionReportingLevel + "1")%>' value="<%=NONE%>" checked="<%=(appProps.getExceptionReportingLevel() == NONE)%>" />
                        <strong>Off</strong>: Do not report exceptions.
                    </label>
                </td>
            </tr>
            <tr>
                <td style="vertical-align: top">
                    <label for="<%=h(exceptionReportingLevel + "2")%>">
                        <labkey:input formGroup="false" type="radio" name="<%=exceptionReportingLevel.name()%>" onChange="enableExceptionTest();" id='<%=(exceptionReportingLevel + "2")%>' value="<%=LOW%>" checked="<%=(appProps.getExceptionReportingLevel() == LOW)%>" />
                        <strong>Low</strong>: Include anonymous system and exception information.
                    </label>
                </td>
            </tr>
            <tr>
                <td style="vertical-align: top">
                    <label for="<%=h(exceptionReportingLevel + "3")%>">
                        <labkey:input formGroup="false" type="radio" name="<%=exceptionReportingLevel.name()%>" onChange="enableExceptionTest();" id='<%=(exceptionReportingLevel + "3")%>' value="<%=MEDIUM%>" checked="<%=(appProps.getExceptionReportingLevel() == MEDIUM)%>" />
                        <strong>Medium</strong>: Include anonymous system and exception information, as well as the URL that triggered the exception.
                    </label>
                </td>
            </tr>
            <tr>
                <td style="vertical-align: top">
                    <label for="<%=h(exceptionReportingLevel + "4")%>">
                        <labkey:input formGroup="false" type="radio" name="<%=exceptionReportingLevel.name()%>" onChange="enableExceptionTest();" id='<%=(exceptionReportingLevel + "4")%>' value="<%=HIGH%>" checked="<%=(appProps.getExceptionReportingLevel() == HIGH)%>" />
                        <strong>High</strong>: Include the above, plus the user's email address. The user will be contacted only for assistance in reproducing the bug, if necessary.
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
    <td class="labkey-form-label" style="vertical-align: top">Report exceptions to the local server</td>
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
    <td class="labkey-form-label"><label for="<%=memoryUsageDumpInterval%>">Log memory usage frequency, in minutes (for debugging; set to 0 to disable)</label></td>
    <td><input type="text" name="<%=memoryUsageDumpInterval%>" id="<%=memoryUsageDumpInterval%>" size="4" value="<%=appProps.getMemoryUsageDumpInterval()%>"></td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="<%=readOnlyHttpRequestTimeout%>">Timeout for read-only HTTP requests, in seconds<%=helpPopup("Read-only HTTP request timeout",
        "After the timeout, resources like database connections and spawned processes will be killed to abort processing the request. Set to 0 to disable the timeout.")%></label></td>
    <td><input type="text" name="<%=readOnlyHttpRequestTimeout%>" id="<%=readOnlyHttpRequestTimeout%>" size="4" value="<%=appProps.getReadOnlyHttpRequestTimeout()%>"></td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="<%=maxBLOBSize%>">Maximum file size, in bytes, to allow in database BLOBs</label></td>
    <td><input type="text" name="<%=maxBLOBSize%>" id="<%=maxBLOBSize%>" size="10" value="<%=appProps.getMaxBLOBSize()%>"></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Configure Security (<%=bean.getSiteSettingsHelpLink("security")%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label"><label for="<%=sslRequired%>">Require HTTPS connections (users must connect via SSL/TLS)</label></td>
    <td><input type="checkbox" name="<%=sslRequired%>" id="<%=sslRequired%>"<%=checked(appProps.isSSLRequired())%>></td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="<%=sslPort%>">HTTPS port number (specified in <%= h(AppProps.getInstance().getWebappConfigurationFilename()) %>)</label></td>
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
    <td class="labkey-form-label"><label for="<%=allowApiKeys%>">Let users create API keys</label></td>
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
    Stream.of(7, 30, 90, 180, 365)
        .forEach(days -> expirationOptions.put(days * SECONDS_PER_DAY, days + " days"));

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
    <td class="labkey-form-label"><label for="<%=allowSessionKeys%>">Let users create session keys</label></td>
    <td><labkey:checkbox id="<%=allowSessionKeys.name()%>" name="<%=allowSessionKeys.name()%>" checked="<%=appProps.isAllowSessionKeys()%>" value="true"/></td>
</tr>

<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td colspan=2>Customize terms-of-use frequency (<%=bean.getSiteSettingsHelpLink("terms")%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label"><label for="<%=termsOfUseFrequencySeconds%>">Require terms-of-use acceptance</label></td>
<%
    final int currentTermsFrequency = AppProps.getInstance().getTermsOfUseFrequencySeconds();
    Map<Integer, String> termsFrequencyOptions = new TreeMap<>(Comparator.comparing(key -> key));
    termsFrequencyOptions.put(0, "Every sign-in");
    if (appProps.isDevMode())
        termsFrequencyOptions.put(60, "Once a minute"); // For testing
    termsFrequencyOptions.put(SECONDS_PER_DAY, "Once a day");
    Stream.of(7, 30, 90, 180, 365)
        .forEach(days -> termsFrequencyOptions.put(days * SECONDS_PER_DAY, "Every " + days + " days"));

    // If current value is non-standard (perhaps set by a startup property) then add it, formatting label as a duration
    if (!termsFrequencyOptions.containsKey(currentTermsFrequency))
        termsFrequencyOptions.put(currentTermsFrequency, DateUtil.formatDuration(1000L * currentTermsFrequency));
%>
    <td>
    <%=
        select()
            .name(termsOfUseFrequencySeconds.name())
            .id(termsOfUseFrequencySeconds.name())
            .addOptions(termsFrequencyOptions)
            .selected(currentTermsFrequency)
            .className(null)
    %>
    </td>
</tr>

<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td colspan=2>Configure pipeline settings (<%=bean.getSiteSettingsHelpLink("pipeline")%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label"><label for="<%=pipelineToolsDirectory%>">Pipeline tools</label><%= helpPopup("Pipeline Tools", "A '" + File.pathSeparator + "' separated list of directories on the web server containing executables that are run for pipeline jobs (e.g. TPP or XTandem)") %></td>
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
    <td class="labkey-form-label"><label for="<%=showRibbonMessage%>">Display message</label></td>
    <td><input type="checkbox" name="<%=showRibbonMessage%>" id="<%=showRibbonMessage%>"<%=checked(appProps.isShowRibbonMessage())%>></td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="<%=ribbonMessage%>">Message HTML</label></td>
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
    <td class="labkey-form-label"><label for="<%=adminOnlyMode%>">Admin only mode (only site admins may log in)</label></td>
    <td><input type="checkbox" name="<%=adminOnlyMode%>" id="<%=adminOnlyMode%>"<%=checked(appProps.isUserRequestedAdminOnlyMode())%>></td>
</tr>
<tr>
    <td class="labkey-form-label" style="vertical-align: top"><label for="<%=adminOnlyMessage%>">Message to users when site is in admin-only mode<br/>(Wiki formatting allowed)</label></td>
    <td><textarea id="<%=adminOnlyMessage%>" name="<%=adminOnlyMessage%>" cols="60" rows="3"><%= h(appProps.getAdminOnlyMessage()) %></textarea></td>
</tr>

<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td colspan=2>HTTP security settings (<%=bean.getSiteSettingsHelpLink("http")%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label"><label for="<%=includeServerHttpHeader%>">Include a <code>Server</code> HTTP header in responses</label></td>
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
        <label for="<%=navAccessOpen%>">Always include inaccessible parent folders in project menu when child folder is accessible</label><%=helpPopup("Project menu access",
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

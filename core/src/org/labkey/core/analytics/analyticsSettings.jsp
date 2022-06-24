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
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.analytics.AnalyticsController" %>
<%@ page import="org.labkey.core.analytics.AnalyticsServiceImpl" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    AnalyticsController.SettingsForm settingsForm = (AnalyticsController.SettingsForm) HttpView.currentModel();
    boolean hasAdminOpsPerms = getContainer().hasPermission(getUser(), AdminOperationsPermission.class);
%>
<%=getTroubleshooterWarning(hasAdminOpsPerms, HtmlString.unsafe("<br>"))%>
<p>You can configure your LabKey Server to include JavaScript that reports usage information to a web analytics system.</p>

<labkey:errors/>
<labkey:form action="<%=new ActionURL(AnalyticsController.BeginAction.class, ContainerManager.getRoot())%>" method="POST">
    <table style="width: 60em;">
        <tr>
            <td style="vertical-align: top;">
                <labkey:checkbox onChange="disableCheckboxes()" name="ff_trackingStatus" checked="<%= settingsForm.ff_trackingStatus.contains(AnalyticsServiceImpl.TrackingStatus.ga4FullUrl)%>" value="<%= AnalyticsServiceImpl.TrackingStatus.ga4FullUrl.toString() %>" id="ga4fullURL" />
            </td>
            <td style="padding-left: 1em;"><strong><label for="ga4fullURL">Google Analytics 4</label></strong>
                <p>
                    This is the most recent version of Google Analytics directly supported by LabKey Server.
                    It always reports the full page URL, regardless of the folder's permissions.
                </p>
            </td>
        </tr>
        <tr>
            <td></td>
            <td style="padding-left: 1em;">
                <p>
                    GA4 reporting is based on a Measurement ID, which typically start with <code>G-</code>.
                    Create your own Measurement ID by signing up with Google Analytics.
                </p>
                <p>
                    <label for="ff_accountId">Measurement ID</label>:
                    <input <%=unsafe(hasAdminOpsPerms?"":"disabled=\"disabled\"")%> type="text" id="ff_measurementId" name="ff_measurementId" value="<%=h(settingsForm.ff_measurementId)%>"/>
                </p>
            </td>
        </tr>

        <tr><td>&nbsp;</td></tr>

        <tr>
            <td style="vertical-align: top;">
                <labkey:checkbox onChange="disableCheckboxes()" name="ff_trackingStatus" checked="<%= settingsForm.ff_trackingStatus.contains(AnalyticsServiceImpl.TrackingStatus.enabled)%>" value="<%= AnalyticsServiceImpl.TrackingStatus.enabled.toString() %>" id="modifiedURL" />
            </td>

            <td style="padding-left: 1em;">
                <strong><label for="modifiedURL">Universal Google Analytics, with redacted URL</label></strong>
                <p >
                    Google has deprecated this version of Google Analytics. It stops accepting new data on July 1, 2023.
                </p>
                <p>
                    The full page URL will only be reported when it is accessible to Guest users.
                    When a project/folder is secured, LabKey Server will report a unique identifier
                    (the folder's EntityId, available through Admin->Folder->Management->Information)
                    instead of the folder path. Additionally, HTTP GET parameters will
                    be stripped. Both are efforts to ensure that sensitive data is not included.
                </p>
            </td>
        </tr>
        <tr>
            <td style="vertical-align: top">
                <strong><label><labkey:checkbox onChange="disableCheckboxes()" name="ff_trackingStatus" checked="<%= settingsForm.ff_trackingStatus.contains(AnalyticsServiceImpl.TrackingStatus.enabledFullURL)%>" value="<%= AnalyticsServiceImpl.TrackingStatus.enabledFullURL.toString() %>" id="fullURL" />
            </td>

            <td style="padding-left: 1em;">
                <strong><label for="fullURL">Universal Google Analytics, with full URL</label></strong>
                <p>
                    Google has deprecated this version of Google Analytics. It stops accepting new data on July 1, 2023.
                </p>
                <p>Always report the full page URL, regardless of the folder's permissions.</p></td>
        </tr>
        <tr>
            <td></td>
            <td style="padding-left: 1em;">
                <p>
                    Universal Google Analytics reporting is based on an Account
                    ID. LabKey monitors the Account ID
                    <code><%=h(AnalyticsServiceImpl.DEFAULT_ACCOUNT_ID)%></code> to understand usage and prioritize
                    development efforts. You can get own Account ID by signing up with Google Analytics.
                </p>
                <p>
                    <label for="ff_accountId">Account ID</label>:
                    <input <%=unsafe(hasAdminOpsPerms?"":"disabled=\"disabled\"")%> type="text" id="ff_accountId" name="ff_accountId" value="<%=h(settingsForm.ff_accountId)%>"/>
                </p>
            </td>
        </tr>

        <tr><td>&nbsp;</td></tr>

        <tr>
            <td style="vertical-align: top">
                <labkey:checkbox name="ff_trackingStatus" checked="<%= settingsForm.ff_trackingStatus.contains(AnalyticsServiceImpl.TrackingStatus.script)%>" value="<%= AnalyticsServiceImpl.TrackingStatus.script.toString() %>" id="customScript" />
            </td>
            <td style="padding-left: 1em;">
                <strong><label for="customScript">Custom JavaScript Analytics></label></strong>
                <p>
                    Add <label for="ff_trackingScript">custom analytics script</label> to the <code>&lt;head&gt;</code> of every page.  Include required <code>&lt;script&gt;</code> tags.
                </p>
                <p>
                    <strong>NOTE:</strong> You can mess up your site if you make a mistake here.  You may want to bookmark this page to aid in making corrections, just in case.
                </p>
                <textarea <%=unsafe(hasAdminOpsPerms?"":"disabled=\"disabled\"")%> style="width:100%; height:15em;" id="ff_trackingScript" name="ff_trackingScript"><%=h(settingsForm.ff_trackingScript)%></textarea>
            </td>
        </tr>

        <tr><td>&nbsp;</td></tr>

        <tr>
            <td></td>
            <td style="padding-left: 1em;">
                <%
                    String doneBtnText = "done";
                    if(hasAdminOpsPerms)
                    {
                        doneBtnText = "cancel";
                %>
                <labkey:button text="submit"/>
                <%
                    }
                %>
                <labkey:button text="<%=doneBtnText%>" href="<%=urlProvider(AdminUrls.class).getAdminConsoleURL()%>" />
            </td>
        </tr>
    </table>

</labkey:form>

<script>
    function disableCheckboxes() {
        document.getElementById('fullURL').disabled = document.getElementById('modifiedURL').checked;
        document.getElementById('modifiedURL').disabled = document.getElementById('fullURL').checked;
    }

    disableCheckboxes();
</script>
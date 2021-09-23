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
<p>Your LabKey Server can be configured to add JavaScript to your web site so that usage information will be sent to a web analytics system.</p>

<labkey:errors/>
<labkey:form action="<%=new ActionURL(AnalyticsController.BeginAction.class, ContainerManager.getRoot())%>" method="POST">
    <table>
        <tr>
            <td style="vertical-align: top; white-space: nowrap">
                <strong><label><labkey:radio name="ff_trackingStatus" value="<%=AnalyticsServiceImpl.TrackingStatus.disabled%>" currentValue="<%=settingsForm.ff_trackingStatus%>"/> Off</label></strong>
            </td>
            <td style="padding-left: 1em;"><p>Do not use analytics tracking on this server.</p></td>
        </tr>

        <tr>
            <td style="vertical-align: top; white-space: nowrap; padding-top: 2em;">
                <strong><label><labkey:radio name="ff_trackingStatus" value="<%=AnalyticsServiceImpl.TrackingStatus.enabled%>" currentValue="<%=settingsForm.ff_trackingStatus%>"/> Google Analytics with modified URL</label></strong>
            </td>

            <td style="padding-left: 1em; padding-top: 2em;">
                <p style="width: 60em;">
                    The full page URL will only be reported when it is accessible to Guest users.
                    When a project/folder is secured, LabKey Server will report a unique identifier
                    (the folder's EntityId, available through Admin->Folder->Management->Information)
                    instead of the folder path. Additionally, HTTP GET parameters will
                    be stripped. Both are efforts to ensure that sensitive data is not included.
                </p>
            </td>
        </tr>
        <tr>
            <td style="vertical-align: top; white-space: nowrap">
                <strong><label><labkey:radio name="ff_trackingStatus" value="<%=AnalyticsServiceImpl.TrackingStatus.enabledFullURL%>" currentValue="<%=settingsForm.ff_trackingStatus%>"/> Google Analytics with full URL</label></strong>
            </td>

            <td style="padding-left: 1em;"><p>Always report the full page URL, regardless of the folder's permissions.</p></td>
        </tr>
        <tr>
            <td></td>
            <td style="padding-left: 1em;">
                <p style="width: 60em;">
                    <a href="https://www.google.com/analytics">Google Analytics</a> reporting is based on an Account
                    ID. LabKey monitors the Account ID
                    <code><%=h(AnalyticsServiceImpl.DEFAULT_ACCOUNT_ID)%></code> to understand usage and prioritize
                    development efforts. You can get own Account ID by signing up with Google Analytics.
                </p>
                <p style="width: 60em;">
                    <label for="ff_accountId">Account ID</label>:
                    <input <%=text(hasAdminOpsPerms?"":"disabled=\"disabled\"")%> type="text" id="ff_accountId" name="ff_accountId" value="<%=h(settingsForm.ff_accountId)%>"/>
                </p>
            </td>
        </tr>

        <tr>
            <td style="vertical-align: top; white-space: nowrap; padding-top: 2em">
                <strong><label><labkey:radio name="ff_trackingStatus" value="<%=AnalyticsServiceImpl.TrackingStatus.script%>" currentValue="<%=settingsForm.ff_trackingStatus%>"/>&nbsp;Custom</label></strong>
            </td>
            <td style="padding-left: 1em; padding-top: 2em;">
                <p style="width: 60em;">
                    Add <label for="ff_trackingScript">custom analytics script</label> to the <code>&lt;head&gt;</code> of every page.  Include required <code>&lt;script&gt;</code> tags.
                </p>
                <p style="width: 60em;">
                    <strong>NOTE:</strong> You can mess up your site if you make a mistake here.  You may want to bookmark this page to aid in making corrections, just in case.
                </p>
                <textarea <%=text(hasAdminOpsPerms?"":"disabled=\"disabled\"")%> style="width:100%; height:15em;" id="ff_trackingScript" name="ff_trackingScript"><%=h(settingsForm.ff_trackingScript)%></textarea>
            </td>
        </tr>
    </table>

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
</labkey:form>

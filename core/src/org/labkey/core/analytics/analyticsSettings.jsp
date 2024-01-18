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
                <labkey:checkbox name="ff_trackingStatus" checked="<%= settingsForm.ff_trackingStatus.contains(AnalyticsServiceImpl.TrackingStatus.ga4FullUrl)%>" value="<%= AnalyticsServiceImpl.TrackingStatus.ga4FullUrl.toString() %>" id="ga4fullURL" />
            </td>
            <td style="padding-left: 1em;"><strong><label for="ga4fullURL">Google Analytics 4</label></strong>
                <p>
                    This is the most recent version of Google Analytics directly supported by LabKey Server.
                    It reports using the full page URL.
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
                    <label for="ff_measurementId">Measurement ID</label>:
                    <input <%=unsafe(hasAdminOpsPerms?"":"disabled=\"disabled\"")%> type="text" id="ff_measurementId" name="ff_measurementId" value="<%=h(settingsForm.ff_measurementId)%>"/>
                </p>
            </td>
        </tr>

        <tr><td>&nbsp;</td></tr>

        <tr>
            <td style="vertical-align: top">
                <labkey:checkbox name="ff_trackingStatus" checked="<%= settingsForm.ff_trackingStatus.contains(AnalyticsServiceImpl.TrackingStatus.script)%>" value="<%= AnalyticsServiceImpl.TrackingStatus.script.toString() %>" id="customScript" />
            </td>
            <td style="padding-left: 1em;">
                <strong><label for="customScript">Custom JavaScript Analytics</label></strong>
                <p>
                    Add <label for="ff_trackingScript">custom analytics script</label> to the <code>&lt;head&gt;</code> of every page. Include required <code>&lt;script&gt;</code> tags. If the server enforces a Content Security Policy, script blocks may need a nonce to function: <code>&lt;script nonce="\${SCRIPT_NONCE:htmlEncode}"&gt;</code>.
                </p>
                <p>
                    <strong>NOTE:</strong> You can mess up your site if you make a mistake here. You may want to bookmark this page to aid in making corrections, just in case.
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
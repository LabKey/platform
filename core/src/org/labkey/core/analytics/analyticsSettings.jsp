<%
/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.core.analytics.AnalyticsController" %>
<%@ page import="org.labkey.core.analytics.AnalyticsServiceImpl" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    AnalyticsController.SettingsForm settingsForm = (AnalyticsController.SettingsForm) HttpView.currentModel();
    boolean hasAdminOpsPerms = getContainer().hasPermission(getUser(), AdminOperationsPermission.class);
%>
<p>Your LabKey Server can be configured to add JavaScript to your HTML pages, so that information about how your users
    use your server will be sent to Google Analytics.</p>
<p>When enabled, the project/folder path will only be reported when it is accessible to Guest users. When a project/folder is secure,
    LabKey Server will report a GUID instead (which can be translated to the human-readable path). Additionally, HTTP GET parameters will
    be stripped. Both are efforts to ensure that sensitive data is not sent to Google Analytics.</p>

<labkey:errors/>
<labkey:form action="<%=h(new ActionURL(AnalyticsController.BeginAction.class, ContainerManager.getRoot()))%>" method="POST">
    <table>
        <tr>
            <td valign=top nowrap><labkey:radio name="ff_trackingStatus" value="<%=AnalyticsServiceImpl.TrackingStatus.disabled%>" currentValue="<%=settingsForm.ff_trackingStatus%>"/>&nbsp;OFF</td>
            <td><p style="margin-top:0">Do NOT add Google Analytics tracking script to pages on this web site.</p></td>
        </tr>

        <tr>
            <td valign=top nowrap><labkey:radio name="ff_trackingStatus" value="<%=AnalyticsServiceImpl.TrackingStatus.enabled%>" currentValue="<%=settingsForm.ff_trackingStatus%>"/>&nbsp;ON</td>
            <td><p style="margin-top:0">Add Google Analytics tracking script to pages on this web site.
                <p>If you have opted to use Google Analytics, you must provide an Account ID.
                    If you use the Account ID <code><%=h(AnalyticsServiceImpl.DEFAULT_ACCOUNT_ID)%></code>,
                    your data will be sent to LabKey. They would love to see how your users are using your
                    server.  However, if you would like to see this data yourself, or you would like to keep this information private
                    from LabKey, you should sign up for your own
                    <a href="http://www.google.com/analytics">Google Analytics</a> account and use the Account ID that they give you.
                </p><p>Google Analytics Account ID:<br>
                    <input <%=text(hasAdminOpsPerms?"":"disabled=\"disabled\"")%> type="text" name="ff_accountId" value="<%=h(settingsForm.ff_accountId)%>"/>
                </p>
            </td>
        </tr>

        <tr>
            <td valign=top nowrap><labkey:radio name="ff_trackingStatus" value="<%=AnalyticsServiceImpl.TrackingStatus.script%>" currentValue="<%=settingsForm.ff_trackingStatus%>"/>&nbsp;CUSTOM</td>
            <td><p style="margin-top:0">Add custom script to the head of every  page.  Include required &lt;script&gt; tags.</p>
                <p><b>NOTE:</b> You can mess up your site if you make a mistake here.  You may want to take this opportunity to bookmark this page.  Just in case.</p>
                <p><textarea <%=text(hasAdminOpsPerms?"":"disabled=\"disabled\"")%> style="width:600px; height:400px;" name="ff_trackingScript"><%=h(settingsForm.ff_trackingScript)%></textarea></p></td>
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
    <labkey:button text="<%=h(doneBtnText)%>" href="<%=urlProvider(AdminUrls.class).getAdminConsoleURL()%>" />
</labkey:form>

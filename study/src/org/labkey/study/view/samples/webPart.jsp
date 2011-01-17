<%
/*
 * Copyright (c) 2006-2011 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.security.User"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.ViewContext"%>
<%@ page import="org.labkey.api.view.WebPartView"%>
<%@ page import="org.labkey.study.SampleManager" %>
<%@ page import="org.labkey.study.samples.SamplesWebPart" %>
<%@ page import="org.labkey.study.samples.settings.RepositorySettings" %>
<%@ page import="org.labkey.study.security.permissions.ManageRequestSettingsPermission" %>
<%@ page import="org.labkey.study.security.permissions.RequestSpecimensPermission" %>
<%@ page import="org.labkey.study.controllers.samples.ShowSearchAction" %>
<%@ page import="org.labkey.study.controllers.samples.SpecimenController.*" %>
<%@ page import="org.labkey.study.controllers.samples.ShowUploadSpecimensAction" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    ViewContext currentContext = HttpView.currentContext();
    SamplesWebPart.SamplesWebPartBean bean = (SamplesWebPart.SamplesWebPartBean) HttpView.currentView().getModelBean();

    Container c = currentContext.getContainer();
    User user = currentContext.getUser();

    boolean shoppingCart = SampleManager.getInstance().isSpecimenShoppingCartEnabled(c);
    RepositorySettings settings = SampleManager.getInstance().getRepositorySettings(c);

    if (bean.isWide())
    {
%>
<table width="100%" class="labkey-manage-display">
    <tr>
        <td valign="top" width="30%">
<%
    }
%>
    <% WebPartView.startTitleFrame(out, "Search", null, "100%", null); %>
<a href="<%=h(new ActionURL(ShowSearchAction.class, c).addParameter("showVials", "false"))%>">Search For Specimens</a><br>
<a href="<%=h(new ActionURL(ShowSearchAction.class, c).addParameter("showVials", "true"))%>">Search For Vials</a><br>
<%
    WebPartView.endTitleFrame(out);
    WebPartView.startTitleFrame(out, "Vials by Primary Type", null, "100%", null); %>
<%= bean.getPrimaryTypeListHtml() %>
<%
    WebPartView.endTitleFrame(out);

    if (bean.isWide())
    {
%>
    </td>
    <td valign="top" width="45%">
<%
    }

    WebPartView.startTitleFrame(out, "Vials by Derivative", null, "100%", null); %>
<%=bean.getDerivativeTypeListHtml() %>
<%
    WebPartView.endTitleFrame(out);

    if (bean.isWide())
    {
%>
    </td>
    <td valign="top" width="25%">
<%
    }

    WebPartView.startTitleFrame(out, "View All Specimens", null, "100%", null); %>
<a href="<%=h(new ActionURL(SamplesAction.class, c).addParameter("showVials", "false"))%>">By Specimen</a><br>
<a href="<%=h(new ActionURL(SamplesAction.class, c).addParameter("showVials", "true"))%>">By Vial</a><br>
<%
    WebPartView.endTitleFrame(out);

    if (settings.isEnableRequests())
    {
        WebPartView.startTitleFrame(out, "Specimen Requests", null, "100%", null); %>
<a href="<%=new ActionURL(ViewRequestsAction.class, c)%>">View Existing Requests</a><br>
<%
        if (shoppingCart && c.hasPermission(user, RequestSpecimensPermission.class))
        {
%>
<a href="<%=new ActionURL(ShowCreateSampleRequestAction.class, c)%>">Create New Request</a><br>
<%      }

        WebPartView.endTitleFrame(out);
    }

    WebPartView.startTitleFrame(out, "Specimen Reports", null, "100%", null); %>
<a href="<%=new ActionURL(AutoReportListAction.class, c)%>">View Available Reports</a><br>
<%
    WebPartView.endTitleFrame(out);

    if (c.hasPermission(user, ManageRequestSettingsPermission.class))
    {
        WebPartView.startTitleFrame(out, "Administration", null, "100%", null);

        if (settings.isSimple())
        {
%>
<a href="<%=new ActionURL(ShowUploadSpecimensAction.class, c)%>">Import Specimens</a>
<%
        }
        else
        {
%>
<a href="<%=new ActionURL(ManageStatusesAction.class, c)%>">Manage Statuses</a><br>
<a href="<%=new ActionURL(ManageActorsAction.class, c)%>">Manage Actors and Groups</a><br>
<a href="<%=new ActionURL(ManageDefaultReqsAction.class, c)%>">Manage Default Requirements</a><br>
<a href="<%=new ActionURL(ManageRequestInputsAction.class, c)%>">Manage New Request Form</a><br>
<a href="<%=new ActionURL(ManageNotificationsAction.class, c)%>">Manage Notifications</a><br>
<%
        }

        WebPartView.endTitleFrame(out);
    }

    if (bean.isWide())
    {
%>
    </td>
</tr>
</table>
<%
    }
%>

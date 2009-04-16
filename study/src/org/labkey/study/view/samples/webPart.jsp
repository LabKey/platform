<%
/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.security.ACL"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.ViewContext"%>
<%@ page import="org.labkey.api.view.WebPartView"%>
<%@ page import="org.labkey.study.SampleManager"%>
<%@ page import="org.labkey.study.model.SpecimenTypeSummary" %>
<%@ page import="org.labkey.study.samples.SamplesWebPart" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    ViewContext currentContext = HttpView.currentContext();
    SamplesWebPart.SamplesWebPartBean bean = (SamplesWebPart.SamplesWebPartBean) HttpView.currentView().getModelBean();
    ActionURL url = currentContext.cloneActionURL();
    url.setPageFlow("Study-Samples");
    ActionURL vialsURL = url.clone().addParameter("showVials", "true");

    SpecimenTypeSummary summary = SampleManager.getInstance().getSpecimenTypeSummary(currentContext.getContainer());
    boolean shoppingCart = SampleManager.getInstance().isSpecimenShoppingCartEnabled(currentContext.getContainer());
    SampleManager.RepositorySettings settings = SampleManager.getInstance().getRepositorySettings(currentContext.getContainer());

%>
<%
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
<a href="<%=h(vialsURL.setAction("showSearch").replaceParameter("showVials", "false"))%>">Search For Specimens</a><br>
<a href="<%=h(vialsURL.setAction("showSearch").replaceParameter("showVials", "true"))%>">Search For Vials</a><br>
<% WebPartView.endTitleFrame(out); %>

<% WebPartView.startTitleFrame(out, "Vials by Primary Type", null, "100%", null); %>
<%= bean.getPrimaryTypeListHtml() %>
<% WebPartView.endTitleFrame(out); %>

<%
    if (bean.isWide())
    {
%>
    </td>
    <td valign="top" width="45%">
<%
    }
%>
<% WebPartView.startTitleFrame(out, "Vials by Derivative", null, "100%", null); %>
<%=bean.getDerivativeTypeListHtml() %>
<% WebPartView.endTitleFrame(out); %>
<%
    if (bean.isWide())
    {
%>
    </td>
    <td valign="top" width="25%">
<%
    }
%>
<% WebPartView.startTitleFrame(out, "View All Specimens", null, "100%", null); %>
<a href="<%=h(vialsURL.setAction("samples").replaceParameter("showVials", "false"))%>">By Specimen</a><br>
<a href="<%=h(vialsURL.setAction("samples").replaceParameter("showVials", "true"))%>">By Vial</a><br>
<%
    url.deleteParameters();
    WebPartView.endTitleFrame(out);
%>
<%
if (settings.isEnableRequests())
{
%>
<% WebPartView.startTitleFrame(out, "Specimen Requests", null, "100%", null); %>
<a href="<%= url.setAction("viewRequests") %>">View Existing Requests</a><br>
<%
    if (shoppingCart)
    {
%>
<a href="<%= url.setAction("showCreateSampleRequest") %>">Create New Request</a><br>
<%  }
    WebPartView.endTitleFrame(out);
}
%>
<% WebPartView.startTitleFrame(out, "Specimen Reports", null, "100%", null); %>
<a href="<%= url.setAction("autoReportList") %>">View Available Reports</a><br>
<%
WebPartView.endTitleFrame(out);
%>
<%
    if (currentContext.getContainer().hasPermission(currentContext.getUser(), ACL.PERM_ADMIN))
    {
%>
<% WebPartView.startTitleFrame(out, "Administration", null, "100%", null); %>
<%
        if (settings.isSimple())
        {
%>
<a href="<%=url.setAction("showUploadSpecimens")%>">Import Specimens</a>
<%
        }
        else
        {
%>
<a href="<%= url.setAction("manageStatuses") %>">Manage Statuses</a><br>
<a href="<%= url.setAction("manageActors") %>">Manage Actors and Groups</a><br>
<a href="<%= url.setAction("manageDefaultReqs") %>">Manage Default Requirements</a><br>
<a href="<%= url.setAction("manageRequestInputs") %>">Manage New Request Form</a><br>
<a href="<%= url.setAction("manageNotifications") %>">Manage Notifications</a><br>
<%
        }
%>
<% WebPartView.endTitleFrame(out); %>
<%
    }
%>
<%
    if (bean.isWide())
    {
%>
    </td>
</tr>
</table>
<%
    }
%>

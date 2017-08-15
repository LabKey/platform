<%
/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission"%>
<%@ page import="org.labkey.api.study.StudyService"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.study.model.LocationImpl" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.reports.ExportExcelReport" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController" %>
<%@ page import="java.io.Writer" %>
<%@ page import="org.labkey.api.view.template.FrameFactoryClassic" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<StudyImpl> view = (JspView<StudyImpl>)HttpView.currentView();
    StudyImpl s = view.getModelBean();
    User user = (User)request.getUserPrincipal();
    List<LocationImpl> locations = s.getLocations();
    boolean isAdmin = user.hasRootAdminPermission() || s.getContainer().hasPermission(user, AdminPermission.class);
 %>

<div width="600px">
Spreadsheet Export allows you to export data from one location exclusively, or to export all data from all locations simultaneously.
Before you export, you can select the source location or locations using the "Location" drop-down menu.
<ul>
<li><b>All Locations</b>. If you select "All Locations" from the dropdown "Location" menu, you will export all data for all
    <%= h(StudyService.get().getSubjectNounPlural(getContainer())) %> across all locations.</li>
<li ><b>Single Site</b>. If you select a particular location from the "Site" menu, you will export only data associated with the chosen location.</li>
</ul>
</div>
<% if (isAdmin)
{ %><%
    FrameFactoryClassic.startTitleFrame(out, "Administrative Options", null, "600", null);
%>
As an administrator you can export via the "Export" button or save a view definition to the server via the "Save" button.<br><br>
When you save the view definition, it will be listed in the reports and views web part. Each time a user clicks on the view, the current data will be downloaded.
The saved view can also be secured so that only a subset of users (e.g. users from the particular location) can see it.<br><br>

    Requirements for retrieving data for a single location:
    <ol>
    <li>You must have imported a Specimen Archive in order for the "Locations" dropdown to list locations. The Specimen Archive defines a list of locations for your Study. </li>
    <li>You must associate ParticipantIDs with CurrentSiteIds via a "Participant Dataset". This step allows participant data records to be mapped to locations.</li>
    </ol> See the <%=helpLink("exportExcel", "help page")%> for more information.
<%
        FrameFactoryClassic.endTitleFrame(out);
    }

%>
<%
    FrameFactoryClassic.startTitleFrame(out, "Configure", null, "600", null);
%>
    <labkey:form action="<%=h(buildURL(ReportsController.ExportExcelAction.class))%>" method="GET">
    <table><tr><th class="labkey-form-label">Site</th><td><select <%= text(isAdmin ? "onChange='siteId_onChange(this)'" : "")%> id=locationId name=locationId><option value="0">ALL</option>
<%
for (LocationImpl location : locations)
{
    String label = location.getLabel();
    if (label == null || label.length() == 0)
        label = "" + location.getRowId();
    %><option value="<%=location.getRowId()%>"><%=h(label)%></option><%
}
%></select></td></tr>
<%  if (isAdmin)
    {
    %>
        <tr><th class="labkey-form-label">Report name</th><td><input style="width:250;" type=text id=label name=label value=""></td></tr><%
    } %>
</table>
<%= button("Export").submit(true) %>
        <% if (isAdmin)
        {   %>
            <input type=hidden name=reportType value="<%=text(ExportExcelReport.TYPE)%>">
            <input type=hidden id=params name=params value="locationId=-1">
            <%= button("Save").submit(true).onClick("this.form.action=" + qh(buildURL(ReportsController.SaveReportAction.class)) + ";") %>

        <script type="text/javascript">
        var sites = {};
        sites['0'] = 'ALL';
            <%
            for (LocationImpl location : locations)
            {
                String label = location.getLabel();
                if (label == null || label.length() == 0)
                    label = "" + location.getRowId();
                %>sites['<%=location.getRowId()%>']=<%=PageFlowUtil.jsString(label)%>;<%
                out.print(text("\n"));
            }%>

        siteId_onChange(document.getElementById("locationId"));

        function siteId_onChange(select)
        {
            var paramsInput = document.getElementById("params");
            paramsInput.value = "locationId=" + select.value;
            var labelInput = document.getElementById("label");
            labelInput.value = "Export to worksheet: " + sites[select.value];
        }
        </script>
        <%}%>

</labkey:form>
<%

    FrameFactoryClassic.endTitleFrame(out);
%>






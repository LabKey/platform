<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.reports.report.view.ManageReportsBean" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.study.controllers.reports.StudyManageReportsBean" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.io.Writer" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>

<link rel="stylesheet" href="<%=request.getContextPath()%>/_yui/build/container/assets/container.css" type="text/css"/>
<link rel="stylesheet" href="<%=request.getContextPath()%>/utils/dialogBox.css" type="text/css"/>
<script type="text/javascript">LABKEY.requiresYahoo("yahoo");</script>
<script type="text/javascript">LABKEY.requiresYahoo("event");</script>
<script type="text/javascript">LABKEY.requiresYahoo("dom");</script>
<script type="text/javascript">LABKEY.requiresYahoo("dragdrop");</script>
<script type="text/javascript">LABKEY.requiresYahoo("animation");</script>
<script type="text/javascript">LABKEY.requiresYahoo("container");</script>
<script type="text/javascript">LABKEY.requiresScript("utils/dialogBox.js");</script>
<script type="text/javascript">
    var dialogHelper;
    var descriptionDialogHelper;

    function init()
    {
        dialogHelper = new LABKEY.widget.DialogBox("renameDialog",{width:"300px", height:"100px"});
        descriptionDialogHelper = new LABKEY.widget.DialogBox("descriptionDialog",{width:"400px", height:"200px"});
    }
    YAHOO.util.Event.addListener(window, "load", init);

    function renameReport(id, name)
    {
        var renameDiv = YAHOO.util.Dom.get('renameDialog');
        renameDiv.style.display = "";

        var reportId = YAHOO.util.Dom.get('renameReportId');
        var reportName = YAHOO.util.Dom.get('renameReportName');

        reportId.value = id;
        reportName.value = name;

        dialogHelper.render();
        dialogHelper.center();
        dialogHelper.show();
    }

    function editDescription(id, description)
    {
        var reportId = YAHOO.util.Dom.get('descReportId');
        var reportDescription = YAHOO.util.Dom.get('descReportDescription');

        var descriptionDiv = YAHOO.util.Dom.get('descriptionDialog');
        descriptionDiv.style.display = "";

        reportId.value = id;
        reportDescription.value = description;

        descriptionDialogHelper.render();
        descriptionDialogHelper.center();
        descriptionDialogHelper.show();
    }
</script>

<%
    JspView<StudyManageReportsBean> me = (JspView<StudyManageReportsBean>) HttpView.currentView();
    StudyManageReportsBean bean = me.getModelBean();

    ViewContext context = HttpView.currentContext();
    ActionURL url = context.cloneActionURL();
    User user = context.getUser();

    boolean isAdmin = bean.getAdminView() && (user.isAdministrator() || context.getContainer().hasPermission(user, ACL.PERM_ADMIN));
    int maxColumns = 3;
    int reportsPerColumn = (bean.getReportCount() + 1) / maxColumns;
    int reportCount = 0;
%>

<%
    if (bean.getAdminView())
    {
%>
<div style="display:none;" id="renameDialog">
    <div class="hd">Rename View</div>
    <div class="bd">
        <form action="<%=new ActionURL(ReportsController.RenameReportAction.class, context.getContainer())%>" method="post" onsubmit="dialogHelper.hide();">
            <input type="hidden" id="renameReportId" name="reportId" value=""/>
            <table class="labkey-form">
                <tr><td>View name:</td></tr>
                <tr><td width="275"><input id="renameReportName" name="reportName" style="width:100%" value=""></td></tr>
                <tr><td><input type="image" src="<%=PageFlowUtil.buttonSrc("Rename")%>"></td></tr>
            </table>
        </form>
    </div>
</div>

<div  style="display:none;" id="descriptionDialog">
    <div class="hd">View Description</div>
    <div class="bd">
        <form action="<%=new ActionURL(ReportsController.ReportDescriptionAction.class, context.getContainer())%>" method="post" onsubmit="descriptionDialogHelper.hide();">
            <input type="hidden" id="descReportId" name="reportId" value=""/>
            <table class="labkey-form">
                <tr><td>View Description:</td></tr>
                <tr><td width="370"><textarea id="descReportDescription" name="reportDescription" style="width:100%" rows="6"></textarea></td></tr>
                <tr><td><input type="image" src="<%=PageFlowUtil.buttonSrc("Update")%>"></td></tr>
            </table>
        </form>
    </div>
</div>
<%
    }
%>

<table>
<%
    if (bean.getErrors() != null)
    {
        for (ObjectError e : (List<ObjectError>) bean.getErrors().getAllErrors())
        {
            %><tr><td colspan=3><font class="labkey-error"><%=h(HttpView.currentContext().getMessage(e))%></font></td></tr><%
        }
    }
%>
</table>

<%
    if (bean.getIsWideView())
    {
        out.print("<table width=\"100%\"><tr><td valign=\"top\">");
        maxColumns--;
    }
    else if (bean.getAdminView())
        out.print("<table class=\"labkey-form\">");

    Map<String, List<ManageReportsBean.ReportRecord>> live = bean.getViews();
    for (Map.Entry<String, List<ManageReportsBean.ReportRecord>> entry : live.entrySet()) {

        if (entry.getValue().isEmpty())
            continue;
        if (bean.getIsWideView() && reportCount >= reportsPerColumn && maxColumns > 0)
        {
            out.print("</td><td valign=\"top\">");
            reportCount = 0;
            maxColumns--;
        }
        startReportSection(out, entry.getKey(), bean);
        for (ManageReportsBean.ReportRecord rec : entry.getValue())
        {
            StudyManageReportsBean.StudyReportRecord r = (StudyManageReportsBean.StudyReportRecord)rec;
            reportCount++;
%>
            <tr><td><a href="<%=h(r.getDisplayURL())%>" <%=r.getTooltip() != null ? "title='" + h(r.getTooltip()) + "'" : ""%>><%=h(r.getName())%></a></td>
<%
        if (isAdmin) {
%>
            <td>&nbsp;<%=r.getSharedURL()%></td>
            <td colspan="1">&nbsp;&nbsp;[<a href="<%=h(r.getDeleteURL())%>" onclick="return confirm('Permanently delete the selected view?');">delete</a>]</td>
            <td>&nbsp;<%=(r.getReport() != null) ? "[<a href=\"javascript:renameReport(" + r.getReport().getDescriptor().getReportId() + "," + hq(r.getName()) + ");\">rename</a>]" : createConversionLink(r.getConversionURL(), "rename")%></td>
            <td>&nbsp;<%=(r.getReport() != null) ? "[<a href=\"javascript:editDescription(" + r.getReport().getDescriptor().getReportId() + "," + hq(StringUtils.trimToEmpty(r.getReport().getDescriptor().getReportDescription())) + ");\">edit description</a>]" : createConversionLink(r.getConversionURL(), "edit description")%></td>
            <td>&nbsp;<%=(r.getReport() != null) ? r.getPermissionsURL() : createConversionLink(r.getConversionURL(), "permissions")%></td>
<%
            if (r.getEditURL() != null) {
%>
                <td>&nbsp;<%="[<a href=\"" + r.getEditURL() + "\">edit</a>]"%></td>
<%          }
        }
%>
            </tr>
<%
        }
        endReportSection(out, bean);
    }
%>

    <%
    if (bean.getStaticReports().size() > 0)
    {
        if (bean.getIsWideView() && reportCount >= reportsPerColumn && maxColumns > 0)
        {
            out.print("</td><td valign=\"top\">");
            reportCount = 0;
            maxColumns--;
        }
        startReportSection(out, "Static Reports", bean);
        for (ManageReportsBean.ReportRecord rec : bean.getStaticReports())
        {
            StudyManageReportsBean.StudyReportRecord r = (StudyManageReportsBean.StudyReportRecord)rec;
            reportCount++;

    %>
            <tr><td><a href="<%=h(r.getDisplayURL())%>" <%=r.getTooltip() != null ? "title='" + r.getTooltip() + "'" : ""%>><%=h(r.getName())%></a></td>
    <%
            if (isAdmin) {
    %>
            <td>&nbsp;<%=r.getSharedURL()%></td>
            <td colspan="1">&nbsp;&nbsp;[<a href="<%=h(r.getDeleteURL())%>" onclick="return confirm('Permanently delete the selected report?');">delete</a>]</td>
            <td>&nbsp;<%=(r.getReport() != null) ? "[<a href=\"javascript:renameReport(" + r.getReport().getDescriptor().getReportId() + "," + hq(r.getReport().getDescriptor().getReportName()) + ");\">rename</a>]" : "&nbsp;"%></td>
            <td>&nbsp;<%=r.getPermissionsURL()%></td>
    <%
            }
    %>
            </tr>
    <%
        }
        endReportSection(out, bean);
    } // end any static reports

    Report enrollmentReport = bean.getEnrollmentReport(false);
    if (enrollmentReport != null || bean.getAdminView())
    {
        if (bean.getIsWideView() && reportCount >= reportsPerColumn && maxColumns > 0)
            out.print("</td><td valign=\"top\">");

        startReportSection(out, "Enrollment View", bean);
        if (enrollmentReport != null) {
    %>
            <tr><td><a href="<%=url.relativeUrl("enrollmentReport.view", null, "Study-Reports")%>">Enrollment</a></td>
    <%
            if (isAdmin) {
    %>
            <td></td><td colspan="1">&nbsp;&nbsp;[<a href="<%=h(new ActionURL("Study-Reports", "deleteReport.view", context.getContainer()).addParameter("reportId", String.valueOf(enrollmentReport.getDescriptor().getReportId())))%>" onclick="return confirm('Permanently delete the selected view?');">delete</a>]</td>
    <%
            }
    %>
            </tr>
    <%
        } else if (isAdmin) {
    %>
            <tr><td>[<a href="<%=url.relativeUrl("enrollmentReport.view", null, "Study-Reports")%>">new enrollment view</a>]</td></tr>
    <%
        }
        endReportSection(out, bean);
    }
    if (bean.getIsWideView())
        out.println("</td></tr></table>");
    else if (bean.getAdminView())
        out.println("</table>");
%>

<%
    if (isAdmin) {
%>
        <table class="labkey-form">
            <tr><td>&nbsp</td></tr>
            <tr><td colspan="4">
            [<a href="<%= h(url.relativeUrl("createQueryReport.view", null, "Study-Reports")) %>">new grid view</a>]
            &nbsp;[<a href="<%= h(url.relativeUrl("createCrosstabReport.view", null, "Study-Reports")) %>">new crosstab view</a>]
            &nbsp;[<a href="<%=h(url.relativeUrl("exportExcelConfigure.view", null, "Study-Reports"))%>">export to workbook (.xls)</a>]
            &nbsp;[<a href="<%=h(url.relativeUrl("showUploadReport", null, "Study-Reports"))%>">upload new static report</a>]
            &nbsp;[<a href="<%=h(bean.getCustomizeParticipantViewURL())%>">customize participant view</a>]
            </td></tr>
        </table>
<%
    }
    else if (user.isAdministrator() || context.getContainer().hasPermission(user, ACL.PERM_ADMIN)) {
%>
        <table class="labkey-form">
            <tr><td>&nbsp</td></tr>
            <tr><td colspan="4">
            [<a href="<%= h(url.relativeUrl("manageReports.view", null, "Study-Reports")) %>">Manage Reports and Views</a>]
            </td></tr>
        </table>
<%
    }
%>


<%!
    void startReportSection(Writer out, String title, StudyManageReportsBean bean) throws Exception
    {
        if (!bean.getAdminView())
        {
            WebPartView.startTitleFrame(out, title, null, "100%", null);
            out.write("<table class=\"labkey-form\">");
        }
        else
        {
            out.write("<tr width=\"100%\"><td colspan=\"7\" class=\"labkey-announcement-title\" align=left><span>");
            out.write(PageFlowUtil.filter(title));
            out.write("</span></td></tr>");
            out.write("<tr width=\"100%\" style=\"height:1;\"><td colspan=\"7\" class=\"labkey-title-area-line\"><img height=\"1\" width=\"1\" src=\"" + AppProps.getInstance().getContextPath() + "/_.gif\"></td></tr>");
        }
    }

    void endReportSection(Writer out, StudyManageReportsBean bean) throws Exception
    {
        if (!bean.getAdminView())
        {
            out.write("</table>");
            WebPartView.endTitleFrame(out);
        }
    }

    String createConversionLink(String conversionURL, String linkName)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("[<a href=\"");
        sb.append(conversionURL);
        sb.append("\" onclick=\"return confirm('Before this action can be performed, this custom grid view must be converted to a grid report. Proceed?');\">");
        sb.append(linkName);
        sb.append("</a>]");

        return sb.toString();
    }
%>
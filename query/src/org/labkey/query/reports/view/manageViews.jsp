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
<%@ page import="org.labkey.api.reports.report.view.ManageReportsBean" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.io.Writer" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.query.reports.ReportsController" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

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
    JspView<ManageReportsBean> me = (JspView<ManageReportsBean>) HttpView.currentView();
    ManageReportsBean bean = me.getModelBean();
    ViewContext context = HttpView.currentContext();
%>

<labkey:errors/>

<div style="display:none;" id="renameDialog">
    <div class="hd">Rename View</div>
    <div class="bd">
        <form action="<%=new ActionURL(ReportsController.RenameReportAction.class, context.getContainer())%>" method="post" onsubmit="dialogHelper.hide();">
            <input type="hidden" id="renameReportId" name="reportId" value=""/>
            <table>
                <tr><td>View name:</td></tr>
                <tr><td width="275"><input id="renameReportName" name="reportName" width="100%" value=""></td></tr>
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
            <table>
                <tr><td>View Description:</td></tr>
                <tr><td width="370"><textarea id="descReportDescription" name="reportDescription" style="width: 100%;" rows="6"></textarea></td></tr>
                <tr><td><input type="image" src="<%=PageFlowUtil.buttonSrc("Update")%>"></td></tr>
            </table>
        </form>
    </div>
</div>

<table>
<%
    Map<String, List<ManageReportsBean.ReportRecord>> live = bean.getViews();
    for (Map.Entry<String, List<ManageReportsBean.ReportRecord>> entry : live.entrySet()) {

        if (entry.getValue().isEmpty())
            continue;
        startReportSection(out, entry.getKey());
        for (ManageReportsBean.ReportRecord r : entry.getValue())
        {
%>
            <tr>
<%          if (r.getDisplayURL() != null) { %>
                <td><a href="<%=h(r.getDisplayURL())%>" <%=r.getTooltip() != null ? "title='" + h(r.getTooltip()) + "'" : ""%>><%=h(r.getName())%></a></td>
<%          } else { %>
                <td><%=h(r.getName())%></td>
<%          } %>
                <td>&nbsp;&nbsp;<%=r.getCreatedBy() != null ? r.getCreatedBy().getDisplayName(context) : ""%></td>
                <td>&nbsp;&nbsp;<%=r.isShared() ? "yes" : "no"%></td>
<%
            if (r.getReport().getDescriptor().canEdit(context))
            {
%>
            <td>&nbsp;<%="[<a href=\"" + h(r.getDeleteURL()) + "\" onclick=\"return confirm('Permanently delete the selected view?');\">delete</a>]"%></td>
            <td>&nbsp;<%="[<a href=\"javascript:renameReport(" + r.getReport().getDescriptor().getReportId() + "," + hq(r.getName()) + ");\">rename</a>]"%></td>
            <td>&nbsp;<%="[<a href=\"javascript:editDescription(" + r.getReport().getDescriptor().getReportId() + "," + hq(StringUtils.trimToEmpty(r.getReport().getDescriptor().getReportDescription())) + ");\">edit description</a>]"%></td>
<%
            } else {
%>
            <td/><td/><td/>
<%          }

            if (r.getEditURL() != null) { %>
                <td>&nbsp;<%="[<a href=\"" + r.getEditURL() + "\">edit</a>]"%></td>
<%          } %>
            </tr>
<%
        }
    }
%>
</table>

<%!
    void startReportSection(Writer out, String title) throws Exception
    {
        out.write("<tr width=\"100%\"><td colspan=\"7\" class=\"labkey-announcement-title\" align=left><span>");
        out.write(PageFlowUtil.filter(title));
        out.write("</span></td></tr>");
        out.write("<tr width=\"100%\"><td colspan=\"7\" class=\"labkey-title-area-line\"><img height=\"1\" width=\"1\" src=\"" + AppProps.getInstance().getContextPath() + "/_.gif\"></td></tr>");
        out.write("<tr><td align=\"center\" class=\"labkey-form-label\">title</td><td align=\"center\" class=\"labkey-form-label\">created by</td><td align=\"center\" class=\"labkey-form-label\">public</td></tr>");
    }
%>
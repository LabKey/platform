<%
/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
<%@ page import="org.apache.commons.lang.StringUtils"%>
<%@ page import="org.labkey.api.pipeline.PipeRoot"%>
<%@ page import="org.labkey.api.pipeline.PipelineService"%>
<%@ page import="org.labkey.api.reports.Report"%>
<%@ page import="org.labkey.api.reports.report.RReportDescriptor"%>
<%@ page import="org.labkey.api.reports.report.ReportDescriptor" %>
<%@ page import="org.labkey.api.reports.report.ReportIdentifier" %>
<%@ page import="org.labkey.api.reports.report.ReportUrls" %>
<%@ page import="org.labkey.api.reports.report.view.RReportBean" %>
<%@ page import="org.labkey.api.reports.report.view.ReportUtil" %>
<%@ page import="org.labkey.api.reports.report.view.RunRReportView" %>
<%@ page import="org.labkey.api.reports.report.view.RunReportView" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    JspView<RReportBean> me = (JspView<RReportBean>) HttpView.currentView();
    RReportBean bean = me.getModelBean();
    List<Report> sharedReports = ReportUtil.getAvailableSharedRScripts(HttpView.currentContext(), bean);
    List<String> includedReports = bean.getIncludedReports();
    String renderAction = (String)HttpView.currentRequest().getAttribute("renderAction");
    ViewContext context = HttpView.currentContext();

    // the url for the execute script button
    ActionURL executeUrl = context.cloneActionURL().replaceParameter(TabStripView.TAB_PARAM, RunReportView.TAB_VIEW).
            replaceParameter(RunReportView.CACHE_PARAM, String.valueOf(bean.getReportId()));

    boolean readOnly = (Boolean)HttpView.currentRequest().getAttribute("readOnly");
    boolean isAdmin = context.getContainer().hasPermission(context.getUser(), AdminPermission.class);

    // is this report associated with a query view
    boolean hasData = bean.getQueryName() != null || bean.getSchemaName() != null;

    PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(HttpView.currentContext().getContainer());
%>

<link rel="stylesheet" href="<%=request.getContextPath()%>/_yui/build/container/assets/container.css" type="text/css"/>
<link rel="stylesheet" href="<%=request.getContextPath()%>/utils/dialogBox.css" type="text/css"/>
<script type="text/javascript">LABKEY.requiresYahoo("yahoo");</script>
<script type="text/javascript">LABKEY.requiresYahoo("event");</script>
<script type="text/javascript">LABKEY.requiresYahoo("dom");</script>
<script type="text/javascript">LABKEY.requiresYahoo("dragdrop");</script>
<script type="text/javascript">LABKEY.requiresYahoo("animation");</script>
<script type="text/javascript">LABKEY.requiresYahoo("container");</script>
<script type="text/javascript">LABKEY.requiresScript("utils/dialogBox.js");</script>
<script type="text/javascript">LABKEY.requiresScript('completion.js');</script>
<script type="text/javascript">
    var dialogHelper;

    function init()
    {
        dialogHelper = new LABKEY.widget.DialogBox("saveDialog",{width:"375px", height:"120px"});
        dialogHelper.showEvent.subscribe(function(){YAHOO.util.Dom.get('reportName').focus()}, this, true);
<%
        if (pipelineRoot == null) {
%>
        var checkBox = YAHOO.util.Dom.get('runInBackground');
        checkBox.disabled = true;
<%
        }
%>
    }
    YAHOO.util.Event.addListener(window, "load", init);

    function saveReport()
    {
        LABKEY.setSubmit(true);
        var saveDiv = YAHOO.util.Dom.get('saveDialog');
        saveDiv.style.display = "";

        document.getElementById('renderReport').action = '<%=new ActionURL("reports", "saveRReport", HttpView.currentContext().getContainer())%>';

        var reportName = YAHOO.util.Dom.get('reportName');
        if (reportName.value == null || reportName.value.length == 0)
        {
            dialogHelper.render();
            dialogHelper.center();
            dialogHelper.show();
        }
        else
        {
            document.getElementById('renderReport').submit();
        }
    }

    function doSaveReport(save)
    {
        var name = YAHOO.util.Dom.get('reportName').value.trim();
        if (save && name.length == 0)
        {
            alert("The View name cannot be blank.");
        }
        else
        {
            dialogHelper.hide();
            if (save)
            {
                document.getElementById('renderReport').submit();
            }
            else
            {
                document.getElementById('renderReport').action = '<%=renderAction%>';
                name.value = "";
            }
        }
    }

    function runScript()
    {
        LABKEY.setSubmit(true);
        document.getElementById('renderReport').submit();
    }

    function downloadData()
    {
        LABKEY.setSubmit(true);
        window.location = '<%=bean.getReport().getDownloadDataURL(HttpView.currentContext())%>';
        LABKEY.setSubmit(false);
    }

</script>

<labkey:errors/>

<form id="renderReport" action="<%=renderAction%>" method="post">
    <table class="labkey-wp">
        <tr class="labkey-wp-header"><th align="left">R View Builder</th></tr>
        <tr><td>Create an R script to be executed on the server:<br/></td></tr>
<%
        if (hasData) {
%>
        <tr><td><a href="javascript:void(0)" onclick="javascript:downloadData()">Download input data
            <%=PageFlowUtil.helpPopup("Download input data", "LabKey Server automatically exports your chosen dataset into " +
                    "a data frame called: labkey.data. You can download it to help with the development of your R script.")%></a> <br/><br/></td></tr>
<%
        }
%>
        <tr><td>
            <textarea id="script"
                      name="script"
                      <% if(readOnly){ %>readonly="true"<% } %>
                      style="width: 100%;"
                      cols="120"
                      wrap="on"
                      rows="20"><%=StringUtils.trimToEmpty(bean.getScript())%></textarea>
        </td></tr>
        <tr><td>
<%          if (!readOnly)
            {
                if (renderAction == null)
                    out.println(PageFlowUtil.generateButton("Execute Script", "javascript:void(0)", "javascript:switchTab('" + executeUrl.getLocalURIString() + "', saveChanges)"));
                else
                    out.println(PageFlowUtil.generateButton("Execute Script", "javascript:void(0)", "javascript:runScript()"));
                if (!context.getUser().isGuest())
                    out.println(PageFlowUtil.generateButton("Save View", "javascript:void(0)", "javascript:saveReport()"));
            }
%>
        </td></tr>
<%
    if (!readOnly)
    {
        if (isAdmin)
            out.println("<tr><td><input type=\"checkbox\" name=\"shareReport\" " + (bean.isShareReport() ? "checked" : "") + " onchange=\"LABKEY.setDirty(true);return true;\">Make this view available to all users.</td></tr>");
        out.println("<tr><td><input type=\"checkbox\" id=\"runInBackground\" name=\"" + RReportDescriptor.Prop.runInBackground.name() + "\" " + (bean.isRunInBackground() ? "checked" : "") + " onchange=\"LABKEY.setDirty(true);return true;\">Run this view in the background as a pipeline job.</td></tr>");
        if (isAdmin)
        {
            out.print("<tr><td><input type=\"checkbox\" name=\"inheritable\" " + (bean.isInheritable() ? "checked" : "") + " onchange=\"LABKEY.setDirty(true);return true;\">Make this view available in child folders.");
            out.print(PageFlowUtil.helpPopup("Available in child folders", "If this check box is selected, this view will be available in data grids of child folders " +
                "where the schema and table are the same as this data grid."));
            out.println("</td></tr>");
        }
    }

    if (!readOnly && !sharedReports.isEmpty())
    {
%>
        <tr><td>&nbsp;</td></tr>
        <tr class="labkey-wp-header"><th align="left">Shared Scripts</th></tr>
        <tr><td><i>You can execute any of the following scripts as part of your current script by calling: source('&lt;Script Name&gt;.r') after checking the box next to the &lt;Script Name&gt; you plan to use.</i></td></tr>
<%
        for (Report report : sharedReports)
        {%>
            <tr><td><input type="checkbox" name="<%=RReportDescriptor.Prop.includedReports%>"
                                    onchange="LABKEY.setDirty(true);return true;" 
                                    value="<%=report.getDescriptor().getReportId()%>"
                                    <%=isScriptIncluded(report.getDescriptor().getReportId(), includedReports) ? "checked" : ""%>>
                <%=report.getDescriptor().getProperty(ReportDescriptor.Prop.reportName)%>
            </td></tr>
        <%}
    }
%>
    </table>
    <input type="hidden" name="<%=ReportDescriptor.Prop.reportType%>" value="<%=bean.getReportType()%>">
    <input type="hidden" name="queryName" value="<%=StringUtils.trimToEmpty(bean.getQueryName())%>">
    <input type="hidden" name="viewName" value="<%=StringUtils.trimToEmpty(bean.getViewName())%>">
    <input type="hidden" name="schemaName" value="<%=StringUtils.trimToEmpty(bean.getSchemaName())%>">
    <input type="hidden" name="dataRegionName" value="<%=StringUtils.trimToEmpty(bean.getDataRegionName())%>">
    <input type="hidden" name="redirectUrl" value="<%=h(bean.getRedirectUrl())%>">
    <% if(null != bean.getReportId()) { %>
        <input type="hidden" name="reportId" value="<%=bean.getReportId()%>">
    <% } %>
    <input type="hidden" name="cacheKey" value="<%=RunRReportView.getReportCacheKey(bean.getReportId(), HttpView.currentContext().getContainer())%>">
    <input type="hidden" name="showDebug" value="true">
    <input type="hidden" name="<%=RReportDescriptor.Prop.scriptExtension%>" value="<%=StringUtils.trimToEmpty(bean.getScriptExtension())%>">

    <div style="display:none;" id="saveDialog">
        <div class="hd">Save View</div>
        <div class="bd">
            <table>
                <tr><td>View name:</td></tr>
                <tr><td width="275"><input id="reportName" name="reportName" style="width: 100%;" value="<%=StringUtils.trimToEmpty(bean.getReportName())%>"></td></tr>
                <tr><td>&nbsp;</td></tr>
                <tr><td>
                    <%=PageFlowUtil.generateButton("Save", "javascript:void(0)", "javascript:doSaveReport(true)")%> 
                    <%=PageFlowUtil.generateButton("Cancel", "javascript:void(0)", "javascript:doSaveReport(false)")%>
            </table>
        </div>
    </div>
<!--
</form>
-->

<%!
    public boolean isScriptIncluded(ReportIdentifier id, List<String> includedScripts) {
        return includedScripts.contains(String.valueOf(id));
    }
%>

<script type="text/javascript">
    // javascript to help manage report dirty state across tabs and across views.
    //
    function saveChanges(destinationURL)
    {
        LABKEY.setSubmit(true);
        if (LABKEY.isDirty() || pageDirty())
        {
            var form = document.getElementById('renderReport');
            var length = form.elements.length;
            var pairs = [];
            var regexp = /%20/g;

            // urlencode the form data for the post
            for (var i=0; i < length; i++)
            {
                var e = form.elements[i];
                if (e.name && !(e.type=="radio"&&e.selected==false) && !(e.type=="checkbox"&&e.checked==false))
                {
                    if (e.value)
                    {
                        var pair = encodeURIComponent(e.name).replace(regexp, "+") + '=' +
                                   encodeURIComponent(e.value).replace(regexp, "+");
                        pairs.push(pair);
                    }
                }
            }
            var ajax = new AJAXInteraction(pairs.join('&'), destinationURL);
            ajax.send();
        }
        else
        {
            if (destinationURL)
                window.location = destinationURL;
        }
    }

    function AJAXInteraction(url, redirectURL)
    {
        this.url = url;
        var redirectURL = redirectURL;
        var req = init();
        req.onreadystatechange = processRequest;

        function init()
        {
            if (window.XMLHttpRequest)
                return new XMLHttpRequest();
            else if (window.ActiveXObject)
                return new ActiveXObject("Microsoft.XMLHTTP");
        }

        this.send = function()
        {
            req.open("POST", "<%=PageFlowUtil.urlProvider(ReportUrls.class).urlSaveRReportState(HttpView.currentContext().getContainer()).getLocalURIString()%>");
            req.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
            req.send(url);
        }

        function processRequest()
        {
            if (req.readyState == 4 && req.status == 200)
            {
                if (redirectURL)
                    window.location = redirectURL;
            }
        }
    }

    var origScript = byId("script").value;
    function pageDirty()
    {
        var script = byId("script");
        if (script && origScript != script.value)
            return true;
        return false;
    }
</script>

<%
/*
 * Copyright (c) 2007-2011 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.pipeline.PipeRoot"%>
<%@ page import="org.labkey.api.pipeline.PipelineService"%>
<%@ page import="org.labkey.api.reports.Report"%>
<%@ page import="org.labkey.api.reports.report.ReportDescriptor" %>
<%@ page import="org.labkey.api.reports.report.ReportIdentifier" %>
<%@ page import="org.labkey.api.reports.report.ReportUrls" %>
<%@ page import="org.labkey.api.reports.report.ScriptReport" %>
<%@ page import="org.labkey.api.reports.report.ScriptReportDescriptor" %>
<%@ page import="org.labkey.api.reports.report.view.RunReportView" %>
<%@ page import="org.labkey.api.reports.report.view.ScriptReportBean" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.TabStripView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<ScriptReportBean> me = (JspView<ScriptReportBean>) HttpView.currentView();
    ViewContext context = me.getViewContext();
    Container c = context.getContainer();
    ScriptReportBean bean = me.getModelBean();
    ScriptReport report = (ScriptReport)bean.getReport();
    List<Report> sharedReports = report.getAvailableSharedScripts(context, bean);
    List<String> includedReports = bean.getIncludedReports();

    // url for the execute script button
    ActionURL executeUrl = context.cloneActionURL().replaceParameter(TabStripView.TAB_PARAM, RunReportView.TAB_VIEW).
            replaceParameter(RunReportView.CACHE_PARAM, String.valueOf(bean.getReportId()));

    boolean readOnly = bean.isReadOnly();
    boolean isAdmin = c.hasPermission(context.getUser(), AdminPermission.class);

    // is this report associated with a query view?
    boolean hasData = bean.getQueryName() != null || bean.getSchemaName() != null;

    PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(c);
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
<script type="text/javascript">LABKEY.requiresScript("/editarea/edit_area_full.js");</script>
<script type="text/javascript">
    var dialogHelper;

    function init()
    {
        dialogHelper = new LABKEY.widget.DialogBox("saveDialog",{width:"375px", height:"120px"});
        dialogHelper.showEvent.subscribe(function(){YAHOO.util.Dom.get('reportName').focus()}, this, true);
<%
        if (pipelineRoot == null)
        {
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
        updateScript();

        var saveDiv = YAHOO.util.Dom.get('saveDialog');
        saveDiv.style.display = "";

        document.getElementById('renderReport').action = '<%=urlProvider(ReportUrls.class).urlSaveScriptReport(c)%>';
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
                document.getElementById('renderReport').action = '<%=bean.getRenderURL()%>';
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
        window.location = '<%=report.getDownloadDataURL(context)%>';
        LABKEY.setSubmit(false);
    }

    function updateScript()
    {
        if (document.getElementById("script") && document.getElementById("edit_area_toggle_checkbox_script") && document.getElementById("edit_area_toggle_checkbox_script").checked)
        {
            document.getElementById("script").value = editAreaLoader.getValue("script");
        }
    }

    // javascript to help manage report dirty state across tabs and across views.
    //
    function saveChanges(destinationURL)
    {
        LABKEY.setSubmit(true);
        updateScript();

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

                if (e.name && !(e.type=="radio" && !e.selected) && !(e.type=="checkbox" && !e.checked))
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
            req.open("POST", "<%=urlProvider(ReportUrls.class).urlSaveScriptReportState(c).getLocalURIString()%>");
            req.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
            req.send(url);
        };

        function processRequest()
        {
            if (req.readyState == 4 && req.status == 200)
            {
                if (redirectURL)
                    window.location = redirectURL;
            }
        }
    }

    var origScript;

    Ext.onReady(function()
    {
        origScript = byId("script").value;
    });

    function pageDirty()
    {
        var script = byId("script");
        return script && origScript != script.value;
    }
</script>

<%!
    public boolean isScriptIncluded(ReportIdentifier id, List<String> includedScripts) {
        return includedScripts.contains(String.valueOf(id));
    }
%>

<labkey:errors/>

<form id="renderReport" action="<%=bean.getRenderURL()%>" method="post">
    <table class="labkey-wp">
        <tr class="labkey-wp-header"><th align="left"><%=h(report.getTypeDescription())%> Builder</th></tr>
        <tr><td>
            <table width="100%">
                <tr><td>This script will be executed <%=h(report.getExecutionLocation())%>:</td>
<%
        if (hasData)
        {
%>
        <td align="right"><a href="javascript:void(0)" onclick="downloadData()">Download input data</a>
            <%=PageFlowUtil.helpPopup("Download input data", report.getDownloadDataHelpMessage())%></td>
<%
        }
%>
                </tr>
            </table>
        </td></tr>
        <tr><td>
            <textarea id="script"
                name="script"<%
                if (readOnly)
                { %>
                readonly="true"<% } %>
                style="width: 100%;"
                cols="120"
                wrap="on"
                rows="20"><%=h(StringUtils.trimToEmpty(bean.getScript()))%></textarea>
            <script type="text/javascript">
                Ext.EventManager.on('script', 'keydown', handleTabsInTextArea);
                editAreaLoader.init({
                    id: "script",<%
                    if (null != report.getEditAreaSyntax())
                    { %>
                    syntax: "<%=report.getEditAreaSyntax()%>",<%
                    } %>
                    start_highlight: true
                });
            </script>
        </td></tr>
<%
    if (!readOnly)
    {
        if (isAdmin)
        {
            out.println("<tr><td><input type=\"checkbox\" name=\"shareReport\" " + (bean.isShareReport() ? "checked" : "") + " onchange=\"LABKEY.setDirty(true);return true;\">Make this view available to all users.</td></tr>");
            out.print("<tr><td><input type=\"checkbox\" name=\"inheritable\" " + (bean.isInheritable() ? "checked" : "") + " onchange=\"LABKEY.setDirty(true);return true;\">Make this view available in child folders.");
            out.print(PageFlowUtil.helpPopup("Available in child folders", "If this check box is selected, this view will be available in data grids of child folders " +
                "where the schema and table are the same as this data grid."));
            out.println("</td></tr>");
        }

        if (report.supportsPipeline())
            out.println("<tr><td><input type=\"checkbox\" id=\"runInBackground\" name=\"" + ScriptReportDescriptor.Prop.runInBackground.name() + "\" " + (bean.isRunInBackground() ? "checked" : "") + " onchange=\"LABKEY.setDirty(true);return true;\">Run this view in the background as a pipeline job.</td></tr>");

        if (!sharedReports.isEmpty())
        {
%>
        <tr><td>&nbsp;</td></tr>
        <tr class="labkey-wp-header"><th align="left">Shared Scripts</th></tr>
        <tr><td><i>You can execute any of the following scripts as part of your current script by calling: source('&lt;Script Name&gt;.r') after checking the box next to the &lt;Script Name&gt; you plan to use.</i></td></tr>
<%
            for (Report sharedReport : sharedReports)
            {%>
            <tr><td><input type="checkbox" name="<%=ScriptReportDescriptor.Prop.includedReports%>"
                                    onchange="LABKEY.setDirty(true);return true;"
                                    value="<%=sharedReport.getDescriptor().getReportId()%>"
                                    <%=isScriptIncluded(sharedReport.getDescriptor().getReportId(), includedReports) ? "checked" : ""%>>
                <%=sharedReport.getDescriptor().getProperty(ReportDescriptor.Prop.reportName)%>
            </td></tr>
            <%}
        }
    }
%>
        <tr><td>
<%
    if (!readOnly)
    {
        if (bean.getRenderURL() == null)
            out.println(PageFlowUtil.generateButton("Execute Script", "javascript:void(0)", "javascript:switchTab('" + h(executeUrl) + "', saveChanges)"));
        else
            out.println(PageFlowUtil.generateButton("Execute Script", "javascript:void(0)", "javascript:runScript()"));

        if (!context.getUser().isGuest())
            out.println(PageFlowUtil.generateButton("Save View", "javascript:void(0)", "javascript:saveReport()"));
    }
%>
        </td></tr>
    </table>
    <input type="hidden" name="<%=ReportDescriptor.Prop.reportType%>" value="<%=bean.getReportType()%>">
    <input type="hidden" name="queryName" value="<%=StringUtils.trimToEmpty(bean.getQueryName())%>">
    <input type="hidden" name="viewName" value="<%=StringUtils.trimToEmpty(bean.getViewName())%>">
    <input type="hidden" name="schemaName" value="<%=StringUtils.trimToEmpty(bean.getSchemaName())%>">
    <input type="hidden" name="dataRegionName" value="<%=StringUtils.trimToEmpty(bean.getDataRegionName())%>">
    <input type="hidden" name="redirectUrl" value="<%=h(bean.getRedirectUrl())%>"><%
    if (null != bean.getReportId()) { %>
    <input type="hidden" name="reportId" value="<%=bean.getReportId()%>">
    <% } %>
    <input type="hidden" name="cacheKey" value="<%=org.labkey.api.reports.report.view.ReportDesignerSessionCache.getReportCacheKey(bean.getReportId(), c)%>">
    <input type="hidden" name="showDebug" value="true">
    <input type="hidden" name="<%=ScriptReportDescriptor.Prop.scriptExtension%>" value="<%=StringUtils.trimToEmpty(bean.getScriptExtension())%>">

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
                </td></tr>
            </table>
        </div>
    </div>
<!--
</form>
-->

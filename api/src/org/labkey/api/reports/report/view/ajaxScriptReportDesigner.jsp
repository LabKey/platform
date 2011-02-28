<%
/*
 * Copyright (c) 2011 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.reports.ReportService" %>
<%@ page import="org.labkey.api.reports.report.ReportDescriptor" %>
<%@ page import="org.labkey.api.reports.report.ReportIdentifier" %>
<%@ page import="org.labkey.api.reports.report.ReportUrls" %>
<%@ page import="org.labkey.api.reports.report.ScriptReport" %>
<%@ page import="org.labkey.api.reports.report.ScriptReportDescriptor" %>
<%@ page import="org.labkey.api.reports.report.view.AjaxScriptReportView.Mode" %>
<%@ page import="org.labkey.api.reports.report.view.ScriptReportBean" %>
<%@ page import="org.labkey.api.reports.report.view.*" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ScriptReportBean> me = (JspView<ScriptReportBean>)HttpView.currentView();
    ViewContext ctx = getViewContext();
    Container c = ctx.getContainer();
    ScriptReportBean bean = me.getModelBean();
    ScriptReport report = (ScriptReport)bean.getReport();
    List<Report> sharedReports = report.getAvailableSharedScripts(ctx, bean);
    List<String> includedReports = bean.getIncludedReports();
    String helpHtml = report.getDesignerHelpHtml();
    boolean readOnly = bean.isReadOnly();
    Mode mode = bean.getMode();
    boolean isAdmin = c.hasPermission(ctx.getUser(), AdminPermission.class);

    // Use simple element ids for create/update (easier for saving & testing), but tack on a unique integer to every id
    // when viewing a report, since the element ids are global and multiple reports could be rendered on the same page.
    String uid = (mode.allowsMultiplePerPage() ? "_" + UniqueID.getServerSessionScopedUID() : "");
    String scriptId = "script" + uid;
    String viewDivId = "viewDiv" + uid;

    // We might be rendering within a data region or on a page with multiple reports... so we need a different data region
    // name for the data tab.
    String dataTabRegionName = "reportRegion" + uid;

    ActionURL viewURL = urlProvider(ReportUrls.class).urlViewScriptReport(c);
    viewURL.addParameters(ctx.getActionURL().getParameters());

    //replaceParameter(RunReportView.CACHE_PARAM, String.valueOf(bean.getReportId())); ??

    viewURL.replaceParameter(ReportDescriptor.Prop.reportId, String.valueOf(bean.getReportId()));
%>
<script type="text/javascript">LABKEY.requiresScript("/editarea/edit_area_full.js");</script>
<script type="text/javascript">

// Since multiple reports could be rendered on the same page, use an anonymous function to provide a separate namespace
// for all the properties and methods.  The Save button needs to call saveReport(), so new up a class and return an
// object that provides outside access the save method.
var f_scope<%=uid%> = new (function() {
    var previousScript = null;
    var previousViewURL = null;
    var scriptText;
    var viewDivExtElement;
    var dataDivExtElement = null;
    var tabsDivExtElement;
    var baseViewURL = <%=q(viewURL.toString())%>;
    var qwpFirstLoad = true;

    Ext.onReady(function(){
        scriptText = document.getElementById("<%=scriptId%>");
        viewDivExtElement = Ext.get("<%=viewDivId%>");
        tabsDivExtElement = Ext.get("tabsDiv<%=uid%>");

        var tabs = new Ext.TabPanel({
            renderTo: 'tabsDiv<%=uid%>',
            width: 1000,
            height: 600,
            activeTab: 0,
            frame: true,
            plain: true,
            defaults: {autoScroll: true},
            items: [<%

                if (mode.showSource())
                {
                %>{
                    title: 'Source',
                    contentEl: 'scriptDiv<%=uid%>'
                },<%
                }
                %>{
                    title: 'View',
                    contentEl: '<%=viewDivId%>',
                    listeners: {activate: activateViewTab}
                },{
                    title: 'Data',
                    contentEl: 'dataDiv<%=uid%>',
                    listeners: {activate: activateDataTab}
                }<%

                if (mode.showHelp() && null != helpHtml)
                {
                %>,{
                    title: 'Help',
                    contentEl: 'reportHelpDiv<%=uid%>'
                }<%
                }
                %>
            ]
        });

        tabs.strip.applyStyles({'background':'#ffffff'});
    });

    function activateViewTab(tab)
    {
        updateScript();
        var currentScript = scriptText.value;
        var currentViewURL = getViewURL();

        // Reload the report only if script or data filter/sort has changed since previous load  
        if (currentScript != previousScript || currentViewURL != previousViewURL)
        {
            tabsDivExtElement.mask("Loading report results...", "x-mask-loading");
            previousScript = currentScript;
            previousViewURL = currentViewURL;

            var config = {
                parameters: {script: currentScript}
            };

            Ext.Ajax.request({
                url: currentViewURL,
                method: 'POST',
                success: viewSuccess,
                failure: viewFailure,
                jsonData: config.parameters,
                headers: {
                    'Content-Type' : 'application/json'
                }
            });
        }
    }

    // Build up an AJAX url that includes all the report rendering parameters.  Ideally, we would AJAX post
    // the entire form instead of creating a custom URL this way, but this lets us track changes more easily.
    // CONSIDER: AJAX post the form and track dirty on the form, data filters, data sorts, etc.
    function getViewURL()
    {
        var dr = LABKEY.DataRegions[<%=q(dataTabRegionName)%>];
        var url = dr ? addFilterAndSort(baseViewURL, dr) : baseViewURL;

        url = addIncludeScripts(url);
        url = addRunInBackground(url);

        return url;
    }

    // TODO: Move this (or something like it) into DataRegion.js
    function addFilterAndSort(url, dr)
    {
        var prefix = dr.name + '.';
        var params = LABKEY.ActionURL.getParameters(dr.requestURL);

        for (var name in params)
        {
            if (name.substr(0, prefix.length) == prefix)
            {
                // Translate "Data" tab dataregion params to use view tab dataregion name 
                var newName = <%=q(bean.getDataRegionName() + ".")%> + name.substr(prefix.length);
                url = url + '&' + newName + '=' + params[name];
            }
        }

        return url;
    }

    // Add parameters for any "Shared Scripts" that are checked
    function addIncludeScripts(url)
    {
        var includes = document.getElementsByName("<%=ScriptReportDescriptor.Prop.includedReports%>");

        for (var i = 0; i < includes.length; i++)
            if (includes[i].checked)
                url = url + '&<%=ScriptReportDescriptor.Prop.includedReports%>=' + includes[i].value;

        return url;
    }

    // Add run-in-background param if present and checked
    function addRunInBackground(url)
    {
        var runInBackground = document.getElementById("runInBackground");

        if (runInBackground && runInBackground.checked)
            return url + '&<%=ScriptReportDescriptor.Prop.runInBackground%>=1';
        else
            return url;
    }

    function viewSuccess(response)
    {
        // Update the view div with the returned HTML, and make sure scripts are run
        viewDivExtElement.update(response.responseText, true);
        tabsDivExtElement.unmask();
    }

    function viewFailure()
    {
        viewDivExtElement.update("Failed to retrieve report results");
        tabsDivExtElement.unmask();
        previousScript = null;
        previousViewURL = null;
    }

    function activateDataTab(tab)
    {
        // Load the data grid on demand, since it's not usually needed.
        if (null == dataDivExtElement)
        {
            dataDivExtElement = Ext.get('dataDiv<%=uid%>');
            tabsDivExtElement.mask("Loading data grid...", "x-mask-loading");

            new LABKEY.QueryWebPart({
                schemaName: <%=q(bean.getSchemaName())%>,
                queryName: <%=q(bean.getQueryName())%>,<%
                if (null != bean.getViewName())        // Note: leaving out viewName vs. viewName:null have different results!  Bug?
                {
                %>
                viewName: <%=q(bean.getViewName())%>,<%
                }
                %>
                dataRegionName: <%=q(dataTabRegionName)%>,
                buttonBarPosition: 'none',
                frame: 'none',
                showDetailsColumn: false,
                showUpdateColumn: false,
                renderTo: dataDivExtElement,
                success: dataSuccess,
                failure: dataFailure});
        }
    }

    function dataSuccess()
    {
        tabsDivExtElement.unmask();

        // On first load of the QWP, initialize the "previous view URL" to match the current dataregion state.  This
        // prevents an unnecessary refresh of the report in scenarios like "Source" -> "View" -> "Data" -> "View".
        if (qwpFirstLoad)
        {
            previousViewURL = getViewURL();
            qwpFirstLoad = false;
        }
    }

    function dataFailure()
    {
        dataDivExtElement.update("Failed to retrieve data grid.");
        tabsDivExtElement.unmask();
        dataDivExtElement = null;  // Request the data grid again next time
    }

    function updateScript()
    {
        if (scriptText && document.getElementById("edit_area_toggle_checkbox_<%=scriptId%>") && document.getElementById("edit_area_toggle_checkbox_<%=scriptId%>").checked)
        {
            scriptText.value = editAreaLoader.getValue("<%=scriptId%>");
        }
    }

    function saveAs(btn, name)
    {
        if (btn == "ok")
        {
            var reportName = document.getElementById('reportName');
            reportName.value = name;
            submit();
        }
    }

    function submit()
    {
        // TODO: LABKEY.setSubmit(true);
        document.getElementById('renderReport').submit();
    }

    // Need to make this function "public" -- callable by the Save button
    return {
        saveReport: function() {
            updateScript();

            document.getElementById('renderReport').action = '<%=urlProvider(ReportUrls.class).urlSaveScriptReport(c)%>';
            var reportName = document.getElementById('reportName');

            if (reportName.value == null || reportName.value.length == 0)
            {
                Ext.MessageBox.show({
                    title: 'Save View',
                    msg: 'Please enter a view name:',
                    buttons: {ok:'Save', cancel:'Cancel'},
                    fn: saveAs,
                    minWidth: Ext.MessageBox.minPromptWidth,
                    prompt: true
                });
            }
            else
            {
                document.getElementById('renderReport').submit();
            }
        }
    };
});
</script>

<%!
    public boolean isScriptIncluded(ReportIdentifier id, List<String> includedScripts)
    {
        return includedScripts.contains(String.valueOf(id));
    }
%>

<div id="tabsDiv<%=uid%>" class="extContainer">
    <div id="<%=viewDivId%>" class="x-hide-display">
    </div>
    <div id="scriptDiv<%=uid%>" class="x-hide-display">
        <form id="renderReport" method="post">
        <table width="100%">
            <tr><td width="100%">
                <textarea id="<%=scriptId%>"
                    name="<%=scriptId%>"<%
                    if (readOnly)
                    { %>
                    readonly="true"<% } %>
                    style="width: 100%;"
                    cols="120"
                    wrap="on"
                    rows="25"><%=h(StringUtils.trimToEmpty(bean.getScript()))%></textarea><%

            if (!readOnly)
            {
            %>
                <script type="text/javascript">
                    Ext.EventManager.on('<%=scriptId%>', 'keydown', handleTabsInTextArea);
                    editAreaLoader.init({
                        id: "<%=scriptId%>",<%
                    if (null != report.getEditAreaSyntax())
                    { %>
                        syntax: "<%=report.getEditAreaSyntax()%>",<%
                    } %>
                        start_highlight: true
                    });
                </script><%
            }
            %>
            </td></tr><%

            if (!readOnly)
            {
                if (isAdmin)
                {
            %>
            <tr><td>
                <input type="checkbox" name="shareReport"<%=bean.isShareReport() ? " checked" : ""%>> Make this view available to all users
            </td></tr>
            <tr><td>
                <input type="checkbox" name="inheritable"<%=bean.isInheritable() ? " checked" : ""%>> Make this view available in child folders
                    <%=helpPopup("Available in child folders", "If this check box is selected, this view will be available in data grids of child folders " +
                "where the schema and table are the same as this data grid.")%>
            </td></tr><%
                }
            }

                if (report.supportsPipeline())
                {
            %>
            <tr><td>
                <input type="checkbox" id="runInBackground" name="<%=ScriptReportDescriptor.Prop.runInBackground.name()%>"<%=bean.isRunInBackground() ? " checked" : ""%>> Run this view in the background as a pipeline job
            </td></tr><%
                }

                if (isAdmin || report.supportsPipeline())
                { %>
            <tr><td>&nbsp;</td></tr><%
                }

                if (!sharedReports.isEmpty())
                {
    %>
            <tr class="labkey-wp-header"><th align="left" colspan="2">Shared Scripts</th></tr>
            <tr><td colspan="2"><i>You can execute any of the following scripts as part of your current script by calling: source('&lt;Script Name&gt;.r') after checking the box next to the &lt;Script Name&gt; you plan to use.</i></td></tr><%
                    for (Report sharedReport : sharedReports)
                    { %>
            <tr><td><input type="checkbox" name="<%=ScriptReportDescriptor.Prop.includedReports%>"<%
                // TODO: Check dirty: onchange="LABKEY.setDirty(true);return true;" %> value="<%=sharedReport.getDescriptor().getReportId()
                %>"<%=isScriptIncluded(sharedReport.getDescriptor().getReportId(), includedReports) ? " checked" : ""
                %>> <%=sharedReport.getDescriptor().getProperty(ReportDescriptor.Prop.reportName)%></td></tr><%
                    } %>
            <tr><td>&nbsp;</td></tr><%

                }

                for (ReportService.ViewFactory vf : ReportService.get().getViewFactories())
                {
                    String extraFormHtml = vf.getExtraFormHtml(ctx, bean);

                    if (null != extraFormHtml)
                        out.print(extraFormHtml);
                }

                if (!ctx.getUser().isGuest())
                { %>
            <tr><td>
                <input type="hidden" name="<%=ReportDescriptor.Prop.reportType%>" value="<%=bean.getReportType()%>">
                <input type="hidden" name="queryName" value="<%=StringUtils.trimToEmpty(bean.getQueryName())%>">
                <input type="hidden" name="viewName" value="<%=StringUtils.trimToEmpty(bean.getViewName())%>">
                <input type="hidden" name="schemaName" value="<%=StringUtils.trimToEmpty(bean.getSchemaName())%>">
                <input type="hidden" name="dataRegionName" value="<%=StringUtils.trimToEmpty(bean.getDataRegionName())%>">
                <input type="hidden" name="redirectUrl" value="<%=h(bean.getRedirectUrl())%>"><%
                if (null != bean.getReportId()) { %>
                <input type="hidden" name="reportId" value="<%=bean.getReportId()%>">
                <% } %>
                <input type="hidden" name="cacheKey" value="<%=ReportDesignerSessionCache.getReportCacheKey(bean.getReportId(), c)%>">
                <input type="hidden" name="showDebug" value="true">
                <input type="hidden" name="<%=ScriptReportDescriptor.Prop.scriptExtension%>" value="<%=StringUtils.trimToEmpty(bean.getScriptExtension())%>">
                <input type="hidden" name="reportName" id="reportName" value="<%=StringUtils.trimToEmpty(bean.getReportName())%>">
                <%=generateButton("Save", "javascript:void(0)", "javascript:f_scope" + uid + ".saveReport()")%>
            </td></tr><%
                }
    %>
        </table>
        </form>
    </div>
    <div id="dataDiv<%=uid%>" class="x-hide-display">
    </div><%
    if (mode.showHelp() && null != helpHtml)
    {
    %>
    <div id="reportHelpDiv<%=uid%>" class="x-hide-display">
<%=helpHtml%>
    </div>
    <%
    }
    %>
</div>
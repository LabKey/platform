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
<%@ page import="org.labkey.api.reports.report.ScriptReport" %>
<%@ page import="org.labkey.api.reports.report.ScriptReportDescriptor" %>
<%@ page import="org.labkey.api.reports.report.view.AjaxScriptReportView.Mode" %>
<%@ page import="org.labkey.api.reports.report.view.ReportDesignerSessionCache" %>
<%@ page import="org.labkey.api.reports.report.view.ScriptReportBean" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.reports.report.RReport" %>
<%@ page import="org.labkey.api.reports.report.ReportUrls" %>
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
    boolean sourceAndHelp = mode.showSourceAndHelp(ctx.getUser());

    // Mode determines whether we need unique IDs on all the HTML elements
    String uid = mode.getUniqueID();
    String scriptId = "script" + uid;
    String viewDivId = "viewDiv" + uid;

    // We might be rendering within a data region or on a page with multiple reports... so we need a different
    // data region name for the data tab.
    String dataTabRegionName = "reportRegion" + uid;

    ActionURL saveURL = urlProvider(ReportUrls.class).urlAjaxSaveScriptReport(c);
    ActionURL initialViewURL = urlProvider(ReportUrls.class).urlViewScriptReport(c);
    ActionURL baseViewURL = initialViewURL.clone();
    Pair<String, String>[] params = ctx.getActionURL().getParameters();

    // Initial view URL uses all parameters
    initialViewURL.addParameters(params);

    // Base view URL strips off sort and filter parameters (we'll get them from the data tab)
    for (Pair<String, String> pair : params)
    {
        String name = pair.getKey();

        if (name.equals(bean.getDataRegionName() + ".sort"))
            continue;

        if (name.startsWith(bean.getDataRegionName() + ".") && name.contains("~"))
            continue;

        baseViewURL.addParameter(name, pair.getValue());
    }

    initialViewURL.replaceParameter(ReportDescriptor.Prop.reportId, String.valueOf(bean.getReportId()));
    baseViewURL.replaceParameter(ReportDescriptor.Prop.reportId, String.valueOf(bean.getReportId()));
%>
<script type="text/javascript">LABKEY.requiresScript("/editarea/edit_area_full.js");</script>
<script type="text/javascript">
    if (<%=!readOnly%>)
        window.onbeforeunload = LABKEY.beforeunload();

// Since multiple reports could be rendered on the same page, use an anonymous function to provide a separate namespace
// for all the properties and methods.  The Save button needs to call saveReport(), so new up a class and return an
// object that provides outside access to saveReport().
var f_scope<%=uid%> = new (function() {
    var previousScript = null;
    var previousViewURL = null;
    var scriptText;
    var viewDivExtElement;
    var dataDivExtElement = null;
    var tabsDivExtElement;
    var downloadLink;
    var downloadHelp;
    var initialViewURL = <%=q(initialViewURL.toString())%>;   // Use original URL filter/sort params if data tab hasn't been used
    var baseViewURL = <%=q(baseViewURL.toString())%>;     // URL with filter and sort stripped out; we'll use filter/sort from data tab instead
    var dataFirstLoad = true;
    var dataRegion = null;
    var readOnly = <%=readOnly%>;

    Ext.onReady(function(){
        scriptText = document.getElementById("<%=scriptId%>");
        viewDivExtElement = Ext.get("<%=viewDivId%>");
        tabsDivExtElement = Ext.get("tabsDiv<%=uid%>");
        downloadLink = document.getElementById("downloadLink<%=uid%>");
        downloadHelp = document.getElementById("downloadHelp<%=uid%>");

        var activeTab = 0;
        var items = new Array();

        items.push({title: 'View', autoHeight: true, contentEl: '<%=viewDivId%>', listeners: {activate: activateViewTab}});
        items.push({title: 'Data', autoHeight: true, contentEl: 'dataTabDiv<%=uid%>', listeners: {activate: activateDataTab}});

        var sourceAndHelp = <%=sourceAndHelp%>;
        var help = <%=null != helpHtml%>;
        var preferSourceTab = <%=mode.preferSourceTab()%>;

        if (sourceAndHelp)
        {
            items.push({title: 'Source', autoHeight: true, contentEl: 'scriptDiv<%=uid%>', listeners: {render: activateSourceTab}});

            if (preferSourceTab)
                activeTab = 2;

            if (help)
                items.push({title: 'Help', autoHeight: true, contentEl: 'reportHelpDiv<%=uid%>'});
        }

        var tabs = new Ext.TabPanel({
            renderTo: 'tabsDiv<%=uid%>',
            autoHeight: true,
            width: 1000,
            activeTab: activeTab,
            frame: true,
            plain: true,
            defaults: {autoScroll: true},
            listeners: {beforetabchange: beforeTabChange},
            boxMinWidth: 500,
            items: items
        });

        tabs.strip.applyStyles({'background':'#ffffff'});

        var _resize = function(w, h) {
            if (!tabs.rendered)
                return;
            var padding = [40,50];
            var xy = tabs.el.getXY();
            var size = {
                width : Math.max(100,w-xy[0]-padding[0]),
                height : Math.max(100,h-xy[1]-padding[1])};
            tabs.setSize(size);
            tabs.doLayout();
        };

        Ext.EventManager.onWindowResize(_resize);
        Ext.EventManager.fireWindowResize();
    });

    function beforeTabChange()
    {
        // Work around editarea.js + Ext TabPanel focus issue on Firefox. See #11727.
        // Do this only on Firefox, since it steals focus from the editarea. 
        if (Ext.isGecko)
            Ext.get("bogusTextArea").focus();
    }

    function activateSourceTab(tab)
    {
        if (!readOnly)
        {
            Ext.EventManager.on('<%=scriptId%>', 'keydown', handleTabsInTextArea);
            editAreaLoader.init({
                id: "<%=scriptId%>",
                toolbar: "search, go_to_line, |, undo, redo, |, select_font,|, change_smooth_selection, highlight, reset_highlight, word_wrap, |, help",<%
            if (null != report.getEditAreaSyntax())
            { %>
                syntax: "<%=report.getEditAreaSyntax()%>",<%
            } %>
                start_highlight: true,
                change_callback: "LABKEY.setDirty(true);"  // JavaScript string to eval, NOT a function
            });
        }
    }

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
        var url = dataRegion ? addFilterAndSort(baseViewURL, dataRegion) : initialViewURL;

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
        // Load the data grid on demand, since it may not be needed.
        if (null == dataDivExtElement)
        {
            dataDivExtElement = Ext.get('dataDiv<%=uid%>');
            var url = <%=q(initialViewURL.toString())%>;
            var dataRegionName = <%=q(bean.getDataRegionName())%>;
            var removeableFilters = getFilterArray(url, dataRegionName);
            var sort = getSort(url, dataRegionName);

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
                removeableFilters: removeableFilters,
                removeableSort: sort,
                buttonBarPosition: 'none',
                frame: 'none',
                showDetailsColumn: false,
                showUpdateColumn: false,
                showRecordSelectors: false,
                renderTo: dataDivExtElement,
                maskEl: tabsDivExtElement,
                success: dataSuccess,
                failure: dataFailure
            });
        }
    }

    // Create an array of LABKEY.Filter objects from the filter parameters on the URL
    // TODO: Move this to Filter.js?
    function getFilterArray(url, dataRegionName)
    {
        var params = LABKEY.ActionURL.getParameters(url);
        var filterArray = new Array();

        for (var paramName in params)
        {
            // Look for parameters that have the right prefix
            if (paramName.indexOf(dataRegionName + ".") == 0)
            {
                var tilde = paramName.indexOf("~");

                if (tilde != -1)
                {
                    var columnName = paramName.substring(dataRegionName.length + 1, tilde);
                    var filterName = paramName.substring(tilde + 1);
                    var filterType = LABKEY.Filter.getFilterTypeForURLSuffix(filterName);
                    var values = params[paramName];
                    if (!Ext.isArray(values))
                    {
                        values = [values];
                    }

                    filterArray.push(LABKEY.Filter.create(columnName, values, filterType));
                }
            }
        }

        return filterArray;
    }

    // Pull the sort parameter off the URL
    function getSort(url, dataRegionName)
    {
        var params = LABKEY.ActionURL.getParameters(url);
        return params[dataRegionName + "." + "sort"];
    }

    function dataSuccess(dr)
    {
        if (dr)
        {
            dataRegion = dr;
            updateDownloadLink(true);

            // On first load of the QWP, initialize the "previous view URL" to match the current dataregion state.  This
            // prevents an unnecessary refresh of the report in scenarios like "Source" -> "View" -> "Data" -> "View".
            if (dataFirstLoad)
            {
                dataFirstLoad = false;
                previousViewURL = getViewURL();
            }
        }
    }

    function dataFailure()
    {
        updateDownloadLink(false);
        dataDivExtElement.update("Failed to retrieve data grid.");
        dataDivExtElement = null;  // Request the data grid again next time
    }

    function updateDownloadLink(success)
    {
        if (downloadLink)
        {
            downloadLink.href = success ? <%=q(report.getDownloadDataURL(ctx).toString())%> : "";
            downloadLink.innerHTML = success ? "Download input data" : "";

            if (downloadHelp)
            {
                downloadHelp.innerHTML = <%=q(helpPopup("Download input data", report.getDownloadDataHelpMessage()))%>;
            }
        }
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
            submit(null);
        }
        else
        {
            // If user hits cancel then clear the submit bit (which will re-instate the dirty bit)
            LABKEY.setSubmit(false);
        }
    }

    function submit(previousName)
    {
        var form = document.getElementById('renderReport');

        Ext.Ajax.request({
            url: <%=q(saveURL.toString())%>,
            method: 'POST',
            success: saveSuccess,
            failure: saveFailure,
            form: form
        });

        // This clears the name in the form on first save, just in case the save fails (e.g., due to existing report by same name)
        document.getElementById('reportName').value = previousName;
    }

    function saveSuccess(response)
    {
        var bean = Ext.util.JSON.decode(response.responseText);

        if (bean.success)
        {
            window.location = bean.redirect;
        }
        else
        {
            saveFailure(response);
        }
    }

    function saveFailure(response)
    {
        var message = '';

        if (response.responseText)
        {
            var bean = Ext.util.JSON.decode(response.responseText);
            var errors = bean.errors;

            if (errors && errors.length > 0)
            {
                for (var i = 0; i < errors.length; i++)
                {
                    message = message + errors[i].message + '\n';
                }

                Ext.Msg.alert('Save Failed', message);
                return;
            }
        }

        if (response.statusText)
        {
            message = response.statusText;
        }

        Ext.Msg.alert('Save Failed', message);
    }

    // Need to make this function "public" -- callable by the Save button
    return {
        saveReport: function() {
            updateScript();

            LABKEY.setSubmit(true);
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
                submit(reportName.value);
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
<textarea cols=0 rows=0 id="bogusTextArea" style="height:0;width:0;left:-10px;top:-10px;position:absolute;"></textarea>
<div id="tabsDiv<%=uid%>" class="extContainer">
    <div id="<%=viewDivId%>" class="x-hide-display">
    </div>
    <div id="dataTabDiv<%=uid%>" class="x-hide-display">
        <table width="100%"><%
            if (sourceAndHelp && report instanceof RReport)
            {
            %>
            <tr><td width="100%">
                <a href="" id="downloadLink<%=uid%>"></a><span id="downloadHelp<%=uid%>"></span>
            </td></tr><%
            }
            %>
            <tr><td width="100%">
                <div id="dataDiv<%=uid%>">
                </div>
            </td></tr>
        </table>
    </div>
    <div id="scriptDiv<%=uid%>" class="x-hide-display">
        <form id="renderReport" method="post">
        <table width="100%">
            <tr><td width="100%">
                <textarea id="<%=scriptId%>" onchange="LABKEY.setDirty(true);return true;"
                    name="<%=scriptId%>"<%
                    if (readOnly)
                    { %>
                    readonly="true"<% } %>
                    style="width:100%;margin:0;padding:0;"
                    cols="120"
                    wrap="on"
                    rows="25"><%=h(StringUtils.trimToEmpty(bean.getScript()))
                %></textarea>
            </td></tr><%

            if (!readOnly)
            {
                if (isAdmin)
                {
            %>
            <tr><td>
                <input type="checkbox" name="shareReport"<%=bean.isShareReport() ? " checked" : ""%> onchange="LABKEY.setDirty(true);return true;"> Make this view available to all users
            </td></tr>
            <tr><td>
                <input type="checkbox" name="inheritable"<%=bean.isInheritable() ? " checked" : ""%> onchange="LABKEY.setDirty(true);return true;"> Make this view available in child folders
                    <%=helpPopup("Available in child folders", "If this check box is selected, this view will be available in data grids of child folders " +
                "where the schema and table are the same as this data grid.")%>
            </td></tr><%
                }
            }

                if (report.supportsPipeline())
                {
            %>
            <tr><td>
                <input type="checkbox" id="runInBackground" name="<%=ScriptReportDescriptor.Prop.runInBackground.name()%>"<%=bean.isRunInBackground() ? " checked" : ""
                    %> onchange="LABKEY.setDirty(true);return true;"> Run this view in the background as a pipeline job
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
            <tr><td><input type="checkbox" name="<%=ScriptReportDescriptor.Prop.includedReports%>" value="<%=sharedReport.getDescriptor().getReportId()
                %>"<%=isScriptIncluded(sharedReport.getDescriptor().getReportId(), includedReports) ? " checked" : ""
                %> onchange="LABKEY.setDirty(true);return true;"> <%=sharedReport.getDescriptor().getProperty(ReportDescriptor.Prop.reportName)%></td></tr><%
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
            </td></tr>
            <tr><td>&nbsp;</td></tr><%
                }
    %>
        </table>
        </form>
    </div><%
    if (sourceAndHelp && null != helpHtml)
    {
    %>
    <div id="reportHelpDiv<%=uid%>" class="x-hide-display">
<%=helpHtml%>
    </div>
    <%
    }
    %>
</div>
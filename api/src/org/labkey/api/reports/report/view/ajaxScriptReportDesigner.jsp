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
<%@ page import="org.labkey.api.reports.report.ReportDescriptor" %>
<%@ page import="org.labkey.api.reports.report.ReportIdentifier" %>
<%@ page import="org.labkey.api.reports.report.ReportUrls" %>
<%@ page import="org.labkey.api.reports.report.ScriptReport" %>
<%@ page import="org.labkey.api.reports.report.ScriptReportDescriptor" %>
<%@ page import="org.labkey.api.reports.report.view.ScriptReportBean" %>
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
    String uid = "_" + UniqueID.getServerSessionScopedUID();
    boolean readOnly = bean.isReadOnly();
    boolean designer = bean.isDesigner();
    boolean isAdmin = c.hasPermission(ctx.getUser(), AdminPermission.class);

    ActionURL previewURL = urlProvider(ReportUrls.class).urlPreviewScriptReport(c);
    previewURL.addParameters(ctx.getActionURL().getParameters());
%>
<script type="text/javascript">LABKEY.requiresScript("/editarea/edit_area_full.js");</script>
<script type="text/javascript">
    var previousScript<%=uid%> = null;
    var scriptText<%=uid%>;
    var previewDivExtElement<%=uid%>;
    var dataDivExtElement<%=uid%> = null;
    var tabsDivExtElement<%=uid%>;
    var basePreviewURL<%=uid%> = <%=q(previewURL.toString())%>;
    var previousPreviewURL<%=uid%> = null;

    Ext.onReady(function(){
        scriptText<%=uid%> = document.getElementById("script<%=uid%>");
        previewDivExtElement<%=uid%> = Ext.get("previewDiv<%=uid%>");
        tabsDivExtElement<%=uid%> = Ext.get("tabsDiv<%=uid%>");

        var tabs = new Ext.TabPanel({
            renderTo: 'tabsDiv<%=uid%>',
            width: 1000,
            height: 600,
            activeTab: 0,
            frame:true,
            plain:true,
            defaults:{autoScroll: true},
            items:[<%

                if (designer)
                {
                %>{
                    title: 'Source',
                    contentEl: 'scriptDiv<%=uid%>'
                },<%
                }
                %>{
                    title: '<%=designer ? "Preview" : "View"%>',
                    contentEl: 'previewDiv<%=uid%>',
                    listeners: {activate: activatePreviewTab}
                },{
                    title: 'Data',
                    contentEl: 'dataDiv<%=uid%>',
                    listeners: {activate: activateDataTab}
                }<%

                if (designer && null != helpHtml)
                {
                %>,{
                    title: 'Help',
                    contentEl: 'helpDiv<%=uid%>'
                }
                <%
                }
                %>
            ]
        });

        tabs.strip.applyStyles({'background':'#ffffff'});
    });

    function activatePreviewTab(tab)
    {
        updateScript();
        var newScript = scriptText<%=uid%>.value;

        var dr = LABKEY.DataRegions[<%=q(bean.getDataRegionName())%>];
        var previewURL = dr ? addFilterAndSort(basePreviewURL<%=uid%>, dr) : basePreviewURL<%=uid%>;

        // Reload the report only if script or data filter/sort has changed since previous load  
        if (newScript != previousScript<%=uid%> || previewURL != previousPreviewURL<%=uid%>)
        {
            tabsDivExtElement<%=uid%>.mask("Loading report results...", "x-mask-loading");
            previousScript<%=uid%> = newScript;
            previousPreviewURL<%=uid%> = previewURL;

            var config = {
                parameters: {script: newScript}
            };

            Ext.Ajax.request({
                url: previewURL,
                method: 'POST',
                success: previewSuccess,
                failure: previewFailure,
                jsonData: config.parameters,
                headers: {
                    'Content-Type' : 'application/json'
                }
            });
        }
    }

    // TODO: Move this (or something like it) into DataRegion.js
    function addFilterAndSort(url, dr)
    {
        var prefix = dr.name + '.';
        var params = LABKEY.ActionURL.getParameters(dr.requestURL);

        for (var name in params)
            if (name.substr(0, prefix.length) == prefix)
                url = url + '&' + name + '=' + params[name];

        return url;
    }

    function previewSuccess(response)
    {
        // Update the preview div with the returned HTML, and make sure scripts are run
        previewDivExtElement<%=uid%>.update(response.responseText, true);
        tabsDivExtElement<%=uid%>.unmask();
    }

    function previewFailure()
    {
        previewDivExtElement<%=uid%>.update("Failed to retrieve report results");
        tabsDivExtElement<%=uid%>.unmask();
        previousScript<%=uid%> = null;
        previousPreviewURL<%=uid%> = null;
    }

    function activateDataTab(tab)
    {
        // Load the data grid on demand, since it's not usually needed.
        if (null == dataDivExtElement<%=uid%>)
        {
            dataDivExtElement<%=uid%> = Ext.get('dataDiv<%=uid%>');
            tabsDivExtElement<%=uid%>.mask("Loading data grid...", "x-mask-loading");

            new LABKEY.QueryWebPart({
                schemaName: <%=q(bean.getSchemaName())%>,
                queryName: <%=q(bean.getQueryName())%>,
                viewName: <%=q(bean.getViewName())%>,
                dataRegionName: <%=q(bean.getDataRegionName())%>,
                buttonBarPosition: 'none',
                frame: 'none',
                showDetailsColumn: false,
                showUpdateColumn: false,
                renderTo: dataDivExtElement<%=uid%>,
                success: dataSuccess,
                failure: dataFailure});
        }
    }

    function dataSuccess()
    {
        tabsDivExtElement<%=uid%>.unmask();
    }

    function dataFailure()
    {
        dataDivExtElement<%=uid%>.update("Failed to retrieve data grid.");
        tabsDivExtElement<%=uid%>.unmask();
        dataDivExtElement<%=uid%> = null;  // Request the data grid again next time
    }

    function updateScript()
    {
        if (scriptText<%=uid%> && document.getElementById("edit_area_toggle_checkbox_script<%=uid%>") && document.getElementById("edit_area_toggle_checkbox_script<%=uid%>").checked)
        {
            scriptText<%=uid%>.value = editAreaLoader.getValue("script<%=uid%>");
        }
    }
</script>

<%!
    public boolean isScriptIncluded(ReportIdentifier id, List<String> includedScripts)
    {
        return includedScripts.contains(String.valueOf(id));
    }
%>

<div id="tabsDiv<%=uid%>" class="extContainer">
    <div id="previewDiv<%=uid%>" class="x-hide-display">
    </div>
    <div id="scriptDiv<%=uid%>" class="x-hide-display">
        <table width="100%">
            <tr><td width="100%">
                <textarea id="script<%=uid%>"
                    name="script<%=uid%>"<%
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
                    Ext.EventManager.on('script<%=uid%>', 'keydown', handleTabsInTextArea);
                    editAreaLoader.init({
                        id: "script<%=uid%>",<%
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
                <input type="checkbox" name="shareReport"<%=bean.isShareReport() ? " checked" : ""%>>Make this view available to all users
            </td></tr>
            <tr><td>
                <input type="checkbox" name="inheritable"<%=bean.isInheritable() ? " checked" : ""%>>Make this view available in child folders
                    <%=helpPopup("Available in child folders", "If this check box is selected, this view will be available in data grids of child folders " +
                "where the schema and table are the same as this data grid.")%>
            </td></tr><%
                }

                if (report.supportsPipeline())
                {
            %>
            <tr><td>
                <input type="checkbox" id="runInBackground" name="<%=ScriptReportDescriptor.Prop.runInBackground.name()%>"<%=bean.isInheritable() ? " checked" : ""%>>Run this view in the background as a pipeline job
            </td></tr><%
                }

                if (!sharedReports.isEmpty())
                {
    %>
            <tr><td>&nbsp;</td></tr>
            <tr class="labkey-wp-header"><th align="left" colspan="2">Shared Scripts</th></tr>
            <tr><td colspan="2"><i>You can execute any of the following scripts as part of your current script by calling: source('&lt;Script Name&gt;.r') after checking the box next to the &lt;Script Name&gt; you plan to use.</i></td></tr>
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
        </table>
    </div>
    <div id="dataDiv<%=uid%>" class="x-hide-display">
    </div><%
    if (designer && null != helpHtml)
    {
    %>
    <div id="helpDiv<%=uid%>" class="x-hide-display">
        <%=helpHtml%>
    </div>
    <%
    }
    %>
</div>
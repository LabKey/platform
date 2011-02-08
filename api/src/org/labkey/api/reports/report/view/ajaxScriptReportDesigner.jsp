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
<%@ page import="org.labkey.api.reports.report.ReportUrls" %>
<%@ page import="org.labkey.api.reports.report.ScriptReport" %>
<%@ page import="org.labkey.api.reports.report.view.ScriptReportBean" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ScriptReportBean> me = (JspView<ScriptReportBean>)HttpView.currentView();
    ViewContext ctx = getViewContext();
    ScriptReportBean bean = me.getModelBean();
    ScriptReport report = (ScriptReport)bean.getReport();
    String helpHtml = report.getDesignerHelpHtml();
    String uid = "_" + UniqueID.getServerSessionScopedUID();
    boolean readOnly = bean.isReadOnly();

    ActionURL previewURL = urlProvider(ReportUrls.class).urlPreviewScriptReport(ctx.getContainer());
    previewURL.addParameters(ctx.getActionURL().getParameters());
%>
<script type="text/javascript">LABKEY.requiresScript("/editarea/edit_area_full.js");</script>
<script type="text/javascript">
    var previousScript = null;
    var scriptDiv<%=uid%>;
    var previewDivExtElement<%=uid%>;
    var dataDivExtElement<%=uid%> = null;
    var tabsDivExtElement<%=uid%>;

    Ext.onReady(function(){
        var tabs = new Ext.TabPanel({
            renderTo: 'tabsDiv<%=uid%>',
            width: 1000,
            height: 600,
            activeTab: 1,
            frame:true,
            plain:true,
            defaults:{autoScroll: true},
            items:[{
                    title: 'Preview',
                    contentEl: 'previewDiv<%=uid%>',
                    listeners: {activate: activatePreviewTab}
                },{
                    title: 'Source',
                    contentEl: 'scriptDiv<%=uid%>'
                },{
                    title: 'Data',
                    contentEl: 'dataDiv<%=uid%>',
                    listeners: {activate: activateDataTab}
                }<%

                if (null != helpHtml)
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

        scriptDiv<%=uid%> = document.getElementById("script<%=uid%>");
        previewDivExtElement<%=uid%> = Ext.get("previewDiv<%=uid%>");
        tabsDivExtElement<%=uid%> = Ext.get('tabsDiv<%=uid%>');
    });

    function activatePreviewTab(tab)
    {
        updateScript();
        var newScript = scriptDiv<%=uid%>.value;

        if (newScript != previousScript)
        {
            tabsDivExtElement<%=uid%>.mask("Loading report results...", "x-mask-loading");
            previousScript = newScript;

            var config = {
                parameters: {script: newScript}
            };

            Ext.Ajax.request({
                url : <%=q(previewURL.toString())%>,
                method : 'POST',
                success: previewSuccess,
                failure: previewFailure,
                jsonData : config.parameters,
                headers : {
                    'Content-Type' : 'application/json'
                }
            });
        }
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
        previousScript = null;
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
        if (scriptDiv<%=uid%> && document.getElementById("edit_area_toggle_checkbox_script<%=uid%>") && document.getElementById("edit_area_toggle_checkbox_script<%=uid%>").checked)
        {
            scriptDiv<%=uid%>.value = editAreaLoader.getValue("script<%=uid%>");
        }
    }
</script>

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
                    rows="25"><%=h(StringUtils.trimToEmpty(bean.getScript()))%></textarea>
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
                </script>
            </td></tr>
            <tr><td><input type="checkbox">Make this view available to all users.</td></tr>
            <tr><td><input type="checkbox">Here's another checkbox.</td></tr>
        </table>
    </div>
    <div id="dataDiv<%=uid%>" class="x-hide-display">
    </div><%
    if (null != helpHtml)
    {
    %>
    <div id="helpDiv<%=uid%>" class="x-hide-display">
        <%=helpHtml%>
    </div>
    <%
    }
    %>
</div>
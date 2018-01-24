<%
/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
<%@ page import="com.fasterxml.jackson.databind.ObjectMapper" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.views.DataViewProvider" %>
<%@ page import="org.labkey.api.query.QueryView" %>
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.reports.report.JavaScriptReport" %>
<%@ page import="org.labkey.api.reports.report.RReport" %>
<%@ page import="org.labkey.api.reports.report.ReportDescriptor" %>
<%@ page import="org.labkey.api.reports.report.ReportUrls" %>
<%@ page import="org.labkey.api.reports.report.ScriptReport" %>
<%@ page import="org.labkey.api.reports.report.view.AjaxScriptReportView.Mode" %>
<%@ page import="org.labkey.api.reports.report.view.ReportUtil" %>
<%@ page import="org.labkey.api.reports.report.view.ScriptReportBean" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.roles.ProjectAdminRole" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("clientapi/ext4");
        dependencies.add("scriptreporteditor");
    }
%>
<%
    JspView<ScriptReportBean> me = (JspView<ScriptReportBean>)HttpView.currentView();
    ViewContext ctx = getViewContext();
    Container c = getContainer();
    User user = getUser();
    ScriptReportBean bean = me.getModelBean();
    ScriptReport report = (ScriptReport) bean.getReport(ctx);
    List<String> includedReports = bean.getIncludedReports();
    String helpHtml = report.getDesignerHelpHtml();
    boolean readOnly = bean.isReadOnly() || !report.canEdit(user, c);
    Mode mode = bean.getMode();
    boolean sourceAndHelp = mode.showSourceAndHelp(ctx.getUser()) || bean.isSourceTabVisible();
    String knitrFormat = bean.getKnitrFormat() != null ? bean.getKnitrFormat() : "None";
    boolean useGetDataApi = report.getReportId() == null || bean.isUseGetDataApi();
    ActionURL saveURL = urlProvider(ReportUrls.class).urlAjaxSaveScriptReport(c);
    ActionURL initialViewURL = urlProvider(ReportUrls.class).urlViewScriptReport(c);
    ActionURL baseViewURL = initialViewURL.clone();
    Pair<ActionURL, Map<String, Object>> externalEditorSettings = urlProvider(ReportUrls.class).urlAjaxExternalEditScriptReport(getViewContext(), report);
    List<Pair<String, String>> params = getActionURL().getParameters();

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

    String renderId = "report-design-panel-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
    ObjectMapper jsonMapper = new ObjectMapper();

    List<Map<String, Object>> sharedScripts = new ArrayList<>();
    for (Report r : report.getAvailableSharedScripts(ctx, bean))
    {
        Map<String, Object> script = new HashMap<>();
        ReportDescriptor desc = r.getDescriptor();

        script.put("name", desc.getReportName());
        script.put("reportId", String.valueOf(desc.getReportId()));
        script.put("included", includedReports.contains(desc.getReportId().toString()));

        sharedScripts.add(script);
    }

    // TODO, add an action to get this information
    Map<String, Object> reportConfig = new HashMap<>();

    // Since we are writing to the JS object via HTML these user-defined props need to be escaped
    reportConfig.put("schemaName", h(bean.getSchemaName()));
    reportConfig.put("queryName", h(bean.getQueryName()));
    reportConfig.put("viewName", h(bean.getViewName()));
    reportConfig.put("dataRegionName", h(StringUtils.defaultString(bean.getDataRegionName(), QueryView.DATAREGIONNAME_DEFAULT)));
    reportConfig.put("reportType", bean.getReportType());
    reportConfig.put("reportId", bean.getReportId() != null ? bean.getReportId().toString() : null);
    reportConfig.put("reportAccess", bean.getReportAccess());
    reportConfig.put("shareReport", bean.isShareReport());
    reportConfig.put("sourceTabVisible", bean.isSourceTabVisible());
    reportConfig.put("runInBackground", bean.isRunInBackground());
    reportConfig.put("supportsPipeline", report.supportsPipeline());

    // must be project admin (or above to share a report to child folders
    reportConfig.put("allowInherit", user.hasRootAdminPermission() || ReportUtil.isInRole(user, c, ProjectAdminRole.class));
    reportConfig.put("inheritable", bean.isInheritable());
    reportConfig.put("editAreaSyntax", report.getEditAreaSyntax());

    reportConfig.put("knitrOptions", report instanceof RReport);
    reportConfig.put("knitrFormat", knitrFormat);
    reportConfig.put("useDefaultOutputFormat", bean.isUseDefaultOutputFormat());
    reportConfig.put("scriptDependencies", bean.getScriptDependencies());

    reportConfig.put("javascriptOptions", report instanceof JavaScriptReport);
    reportConfig.put("useGetDataApi", useGetDataApi);

    reportConfig.put("thumbnailOptions", report.supportsDynamicThumbnail());
    if (report.supportsDynamicThumbnail())
        reportConfig.put("thumbnailType", bean.getThumbnailType() != null ? bean.getThumbnailType() : DataViewProvider.EditInfo.ThumbnailType.AUTO.name());

    StudyService svc = StudyService.get();
    reportConfig.put("studyOptions", (report instanceof RReport) && (svc != null && svc.getStudy(c) != null));
    reportConfig.put("filterParam", bean.getFilterParam());
    reportConfig.put("cached", bean.isCached());

    reportConfig.put("helpHtml", helpHtml);
%>

<script type="text/javascript">

    /**
     * Callback to run the script report panel after all dependent scripts are loaded. Issue : 25130
     */
    function createScriptReportPanel() {

        Ext4.onReady(function(){

            var externalEditSettings;
            <% if (null != externalEditorSettings) {
                Map<String, Object> externalConfig = externalEditorSettings.getValue();
            %>
                externalEditSettings = {};
                externalEditSettings.url = <%=q(externalEditorSettings.getKey().getLocalURIString())%>;
                externalEditSettings.name = <%=q((String) externalConfig.get("name"))%>;
                externalEditSettings.finishUrl = <%=q(externalConfig.get("finishUrl").toString())%>;
                externalEditSettings.externalWindowTitle = <%=q(externalConfig.containsKey("externalWindowTitle") ? (String) externalConfig.get("externalWindowTitle"): "")%>;

            <% if (externalConfig.containsKey("editing") && (boolean) externalConfig.get("editing")) { %>
                externalEditSettings.isEditing = true;
                externalEditSettings.redirectUrl = <%=q(externalConfig.containsKey("redirectUrl") ? externalConfig.get("redirectUrl").toString(): "")%>;
                externalEditSettings.externalUrl = <%=q(externalConfig.containsKey("externalUrl") ? externalConfig.get("externalUrl").toString(): "")%>;
           <% } %>

                var externalEditWarning = '';

                <% if (externalConfig.containsKey("warningMsg")) { %>
                    externalEditWarning = <%=q((String) externalConfig.get("warningMsg"))%>;
                <% } %>

                if (externalEditWarning)
                {
                    document.getElementById('script-report-editor-msg').innerHTML = '<span class="script-report-editor-msg">' + externalEditWarning + '</span>';
                }

            <% } %>
            var panel = Ext4.create('LABKEY.ext4.ScriptReportPanel', {
                renderTo        : <%=q(renderId)%>,
                readOnly        : <%=readOnly%>,
                minHeight       : 500,
                minWidth        : 500,
                initialURL      : <%=q(initialViewURL.getLocalURIString())%>,
                saveURL         : <%=q(saveURL.getLocalURIString())%>,
                externalEditSettings: externalEditSettings,
                baseURL         : <%=q(baseViewURL.getLocalURIString())%>,
                preferSourceTab : <%=mode.preferSourceTab()%>,
                sourceAndHelp   : <%=sourceAndHelp%>,
                redirectUrl     : <%=q(bean.getRedirectUrl())%>,
                sharedScripts   : <%=text(jsonMapper.writeValueAsString(sharedScripts))%>,
                reportConfig    : <%=text(jsonMapper.writeValueAsString(reportConfig))%>,
                script          : <%=q(StringUtils.trimToEmpty(bean.getScript()))%>,
                htmlEncodedProps: true
            });

            var _LOCK = false; // prevents recursive panel adjustments
            panel.on('afterlayout', function(p) {
                if (!_LOCK) {
                    _LOCK = true;
                    var elWidth = Ext4.Element.getViewWidth();
                    if (p.getWidth() > elWidth) {
                        p.setWidth(elWidth - 50);
                    }
                    _LOCK = false;
                }
            });
        });
    }
</script>

<labkey:scriptDependency callback="createScriptReportPanel" scope="this"/>
<div id="script-report-editor-msg" class="text-warning"></div>
<div id="<%= h(renderId)%>" class="script-report-editor"></div>



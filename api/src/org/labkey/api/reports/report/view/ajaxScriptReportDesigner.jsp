<%
/*
 * Copyright (c) 2011-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.reports.report.view.ScriptReportDesignBean" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.roles.ProjectAdminRole" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.LinkedList" %>
<%@ page import="static java.lang.Boolean.TRUE" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ListIterator" %>
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
    JspView<ScriptReportDesignBean> me = (JspView<ScriptReportDesignBean>)HttpView.currentView();
    ViewContext ctx = getViewContext();
    Container c = getContainer();
    User user = getUser();
    ScriptReportDesignBean bean = me.getModelBean();
    ScriptReport report = bean.getReport(ctx);
    List<String> includedReports = bean.getIncludedReports();
    String helpHtml = report.getDesignerHelpHtml();
    boolean readOnly = bean.isReadOnly() || !report.canEdit(user, c);
    boolean allowShareReport = report.allowShareButton(user, c);
    Mode mode = bean.getMode();
    boolean sourceAndHelp = mode.showSourceAndHelp(ctx) || bean.isSourceTabVisible();
    String knitrFormat = bean.getKnitrFormat() != null ? bean.getKnitrFormat() : "None";
    boolean useGetDataApi = report.getReportId() == null || TRUE==bean.isUseGetDataApi();
    ActionURL saveURL = urlProvider(ReportUrls.class).urlAjaxSaveScriptReport(c);
    ActionURL initialViewURL = readOnly ? urlProvider(ReportUrls.class).urlViewScriptReport(c) : urlProvider(ReportUrls.class).urlDesignScriptReport(c);
    ActionURL baseViewURL = initialViewURL.clone();
    Pair<ActionURL, Map<String, Object>> externalEditorSettings = urlProvider(ReportUrls.class).urlAjaxExternalEditScriptReport(getViewContext(), report);
    List<Pair<String, String>> params = new LinkedList<>(getActionURL().getParameters());

    // Initial view URL uses all parameters
    bean.addParameters(initialViewURL, params);

    // Base view URL strips off sort and filter parameters (we'll get them from the data tab)
    ListIterator<Pair<String, String>> iter = params.listIterator();

    while (iter.hasNext())
    {
        Pair<String, String> pair = iter.next();
        String name = pair.getKey();

        if (name.equals(bean.getDataRegionName() + ".sort"))
            iter.remove();
        else if (name.startsWith(bean.getDataRegionName() + ".") && name.contains("~"))
            iter.remove();
    }

    bean.addParameters(baseViewURL, params);

    initialViewURL.replaceParameter(ReportDescriptor.Prop.reportId, String.valueOf(bean.getReportId()));
    baseViewURL.replaceParameter(ReportDescriptor.Prop.reportId, String.valueOf(bean.getReportId()));

    String renderId = "report-design-panel-" + getRequestScopedUID();
    ObjectMapper jsonMapper = new ObjectMapper();

    List<Map<String, Object>> sharedScripts = new ArrayList<>();
    for (Report r : report.getAvailableSharedScripts(ctx, bean))
    {
        Map<String, Object> script = new HashMap<>();
        ReportDescriptor desc = r.getDescriptor();

        script.put("name", PageFlowUtil.filter(desc.getReportName()));
        script.put("reportId", String.valueOf(desc.getReportId()));
        script.put("included", includedReports.contains(desc.getReportId().toString()));

        sharedScripts.add(script);
    }

    // TODO, add an action to get this information
    Map<String, Object> reportConfig = new HashMap<>();

    // Since we are writing to the JS object via HTML these user-defined props need to be escaped. But these need to be
    // Strings, not HtmlStrings, so use PageFlowUtil.filter().
    reportConfig.put("schemaName", PageFlowUtil.filter(bean.getSchemaName()));
    reportConfig.put("queryName", PageFlowUtil.filter(bean.getQueryName()));
    reportConfig.put("viewName", PageFlowUtil.filter(bean.getViewName()));
    reportConfig.put("dataRegionName", PageFlowUtil.filter(StringUtils.defaultString(bean.getDataRegionName(), QueryView.DATAREGIONNAME_DEFAULT)));
    reportConfig.put("reportType", bean.getReportType());
    reportConfig.put("reportId", bean.getReportId() != null ? bean.getReportId().toString() : null);
    reportConfig.put("reportAccess", bean.getReportAccess());
    reportConfig.put("shareReport", bean.isShareReport());
    reportConfig.put("sourceTabVisible", bean.isSourceTabVisible());
    reportConfig.put("runInBackground", bean.isRunInBackground());
    reportConfig.put("supportsPipeline", report.supportsPipeline());
    reportConfig.put("isModuleBased", report.getDescriptor().isModuleBased());

    // must be project admin (or above to share a report to child folders
    reportConfig.put("allowInherit", user.hasRootAdminPermission() || ReportUtil.isInRole(user, c, ProjectAdminRole.class));
    reportConfig.put("inheritable", bean.isInheritable());
    reportConfig.put("editAreaSyntax", report.getEditAreaSyntax());

    reportConfig.put("knitrOptions", report instanceof RReport);
    reportConfig.put("knitrFormat", knitrFormat);
    reportConfig.put("useDefaultOutputFormat", bean.isUseDefaultOutputFormat());
    reportConfig.put("rmarkdownOutputOptions", bean.getRmarkdownOutputOptions());
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

    <labkey:loadClientDependencies>

        Ext4.onReady(function(){

            var externalEditSettings;
            <% if (null != externalEditorSettings) {
                Map<String, Object> externalConfig = externalEditorSettings.getValue();
            %>
                externalEditSettings = {};
                externalEditSettings.url = <%=q(externalEditorSettings.getKey())%>;
                externalEditSettings.name = <%=q((String) externalConfig.get("name"))%>;
                externalEditSettings.finishUrl = <%=q(externalConfig.get("finishUrl") != null ? externalConfig.get("finishUrl").toString(): null)%>;
                externalEditSettings.externalWindowTitle = <%=q(externalConfig.containsKey("externalWindowTitle") ? (String) externalConfig.get("externalWindowTitle"): "")%>;
                externalEditSettings.redirectUrl = <%=q(externalConfig.containsKey("redirectUrl") ? externalConfig.get("redirectUrl").toString(): "")%>;
                externalEditSettings.externalUrl = <%=q(externalConfig.containsKey("externalUrl") ? externalConfig.get("externalUrl").toString(): "")%>;
                externalEditSettings.isEditing = <%=externalConfig.containsKey("editing") && (boolean) externalConfig.get("editing")%>;
                externalEditSettings.isDocker = <%=(Boolean)externalConfig.get("isDocker")%>;
            <% } %>

            var panel = Ext4.create('LABKEY.ext4.ScriptReportPanel', {
                renderTo        : <%=q(renderId)%>,
                readOnly        : <%=readOnly%>,
                allowShareReport: <%=allowShareReport%>,
                minHeight       : 500,
                minWidth        : 500,
                initialURL      : <%=q(initialViewURL)%>,
                saveURL         : <%=q(saveURL)%>,
                externalEditSettings: externalEditSettings,
                baseURL         : <%=q(baseViewURL)%>,
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
    </labkey:loadClientDependencies>
</script>

<div id="script-report-editor-msg">
<%
    for (HtmlString msg : bean.getWarnings())
    {
        %><div class="labkey-warning-messages"><%=msg%></div><br><%
    }
%>
</div>
<div id="<%= h(renderId)%>" class="script-report-editor"></div>



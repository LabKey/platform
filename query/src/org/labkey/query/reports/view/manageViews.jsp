<%
/*
 * Copyright (c) 2007-2013 LabKey Corporation
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
<%@ page import="org.json.JSONArray" %>
<%@ page import="org.labkey.api.reports.report.RReport" %>
<%@ page import="org.labkey.api.reports.report.view.RReportBean" %>
<%@ page import="org.labkey.api.reports.report.view.ReportUtil" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.query.reports.ReportsController" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("ext3"));
        resources.add(ClientDependency.fromFilePath("/reports/rowExpander.js"));
        resources.add(ClientDependency.fromFilePath("/reports/manageViews.js"));
        return resources;
    }
%>
<%
    JspView<ReportsController.ViewsSummaryForm> me = (JspView<ReportsController.ViewsSummaryForm>) HttpView.currentView();
    ReportsController.ViewsSummaryForm form = me.getModelBean();

    ViewContext context = HttpView.currentContext();

    RReportBean bean = new RReportBean();
    bean.setReportType(RReport.TYPE);
    bean.setRedirectUrl(context.getActionURL().getLocalURIString());

    JSONArray reportButtons = ReportUtil.getCreateReportButtons(context);
%>
<labkey:errors/>
<i><p id="filterMsg"></p></i>
<div id="viewsGrid" class="extContainer"></div>
<script type="text/javascript">

    Ext.onReady(function()
    {
        var gridConfig = {
            renderTo: 'viewsGrid',
            border  : false,
            isAdmin : <%=context.hasPermission(AdminPermission.class)%>,
            <% if (form.getSchemaName() != null && form.getQueryName() != null) { %>
                baseQuery: {
                    schemaName: <%=PageFlowUtil.jsString(form.getSchemaName())%>,
                    queryName: <%=PageFlowUtil.jsString(form.getQueryName())%>,
                    baseFilterItems: <%=PageFlowUtil.jsString(form.getBaseFilterItems())%>
                },
                filterDiv: 'filterMsg',
            <% } %>
            container : <%=PageFlowUtil.jsString(context.getContainer().getPath())%>,
            createMenu :<%=reportButtons%>
        };
        
        var panel = new LABKEY.ViewsPanel(gridConfig);
        var grid = panel.show();
        var _resize = function(w,h) {
            LABKEY.Utils.resizeToViewport(grid, w, -1); // don't fit to height
        };
        Ext.EventManager.onWindowResize(_resize);
    });
    
</script>

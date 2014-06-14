<%
    /*
     * Copyright (c) 2011-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.query.reports.ReportContentDigestProviderImpl.ReportDigestForm" %>
<%@ page import="org.labkey.api.reports.report.ReportUrls" %>
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.reports.model.ReportInfo" %>
<%@ page import="org.labkey.api.reports.model.ViewCategory" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    ReportDigestForm form = ((JspView<ReportDigestForm>)HttpView.currentView()).getModelBean();

    ActionURL emailPrefs = PageFlowUtil.urlProvider(ReportUrls.class).urlManageNotifications(form.getContainer());
    ActionURL folderUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(form.getContainer());
    Map<Integer, ViewCategory> viewCategoryMap = form.getViewCategoryMap();
%>
<html>
<head>
    <style>
        th, td
        {
            text-align:left;
        }
    </style>
    <base href="<%=h(AppProps.getInstance().getBaseServerUrl() + AppProps.getInstance().getContextPath())%>"/>
    <%=PageFlowUtil.getStylesheetIncludes(form.getContainer(), form.getUser())%>
</head>
<body>
<table width="100%">
    <tr><td>Summary of notifications of reports and dataset changes for folder <a href="<%=h(folderUrl.getURIString())%>"><%=h(form.getContainer().getPath())%></a>.</td></tr>
</table>
<hr size="1"/>
<br>

<table width="900px">
    <tr><th>Report</th><th>Category</th><th>Modified</th></tr>
    <%
        int i = 0;
        for (ReportInfo reportInfo : form.getReports())
        {
            String rowCls = (i++ % 2 == 0) ? "labkey-row" : "labkey-alternate-row";
    %>
            <tr class="<%=text(rowCls)%>">
                <td><%=h(reportInfo.getName())%></td>
                <td><%=h(viewCategoryMap.get(reportInfo.getCategoryId()).getLabel())%></td>
                <td><%=h(DateUtil.formatDateTime(form.getContainer(), reportInfo.getModified()))%></td>
            </tr>
    <%
        }
    %>

    <tr><th></th><th></th><th></th></tr>
    <tr><th>Dataset</th><th>Category</th><th>Modified</th></tr>
    <%
        int j = 0;
        for (ReportInfo reportInfo : form.getDatasets())
        {
            String rowCls = (j++ % 2 == 0) ? "labkey-row" : "labkey-alternate-row";
    %>
    <tr class="<%=text(rowCls)%>">
        <td><%=h(reportInfo.getName())%></td>
        <td><%=h(viewCategoryMap.get(reportInfo.getCategoryId()).getLabel())%></td>
        <td><%=h(DateUtil.formatDateTime(form.getContainer(), reportInfo.getModified()))%></td>
    </tr>
    <%
        }
    %>
</table>
<br>
<hr size="1"/>

<table width="100%">
    <tr><td>You have received this email because
        you are signed up to receive notifications about changes to reports and datasets at <a href="<%=h(folderUrl.getURIString())%>"><%= h(form.getContainer().getPath()) %></a>.
        If you no longer wish to receive these notifications you can <a href="<%=h(emailPrefs.getURIString())%>">change your email preferences</a>.
    </td></tr>
</table>
</body>
</html>

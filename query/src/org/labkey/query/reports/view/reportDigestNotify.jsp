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
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.study.StudyUrls" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    ReportDigestForm form = ((JspView<ReportDigestForm>)HttpView.currentView()).getModelBean();

    ActionURL emailPrefs = PageFlowUtil.urlProvider(ReportUrls.class).urlManageNotifications(form.getContainer());
    ActionURL folderUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(form.getContainer());
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

<table width="1020px">
    <tr><th>&nbsp;&nbsp;</th><th>Name</th><th>Type</th><th>Last Modified</th><th>Status</th></tr>
    <%
    for (Map.Entry<ViewCategory, List<ReportInfo>> catEntry : form.getReports().entrySet())
    {
    %>
    <tr><th colspan="5">Category '<%=h(catEntry.getKey().getLabel())%>'</th></tr>
    <%
        int i = 0;
        for (ReportInfo reportInfo : catEntry.getValue())
        {
            String rowCls = (i++ % 2 == 0) ? "labkey-row" : "labkey-alternate-row";
            ActionURL url = "Dataset".equalsIgnoreCase(reportInfo.getType()) ?
                    PageFlowUtil.urlProvider(StudyUrls.class).getDatasetURL(form.getContainer(), reportInfo.getRowId()) :
                    reportInfo.getReport().getRunReportURL(getViewContext());
    %>
            <tr class="<%=text(rowCls)%>">
                <td>&nbsp;&nbsp;</td>
                <td>
                    <%if (null != url) {%> <a href='<%=h(url.getURIString())%>'> <%}%>
                        <%=h(reportInfo.getName())%>
                    <%if (null != url) {%> </a> <%}%>
                </td>
                <td><%=h(reportInfo.getType())%></td>
                <td><%=h(DateUtil.formatDateTime(form.getContainer(), reportInfo.getModified()))%></td>
                <td><%=h(reportInfo.getStatus())%></td>
            </tr>
    <%
        }
    %>

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

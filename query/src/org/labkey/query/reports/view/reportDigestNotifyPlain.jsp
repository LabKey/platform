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
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.query.reports.ReportContentDigestProviderImpl.ReportDigestForm" %>
<%@ page import="org.labkey.api.reports.model.ReportInfo" %>
<%@ page import="org.labkey.api.reports.model.ViewCategory" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    ReportDigestForm form = ((JspView<ReportDigestForm>)HttpView.currentView()).getModelBean();
    Map<Integer, ViewCategory> viewCategoryMap = form.getViewCategoryMap();
%>

    Summary of notifications of reports and dataset changes for folder <%=text(form.getContainer().getPath())%>

    <%
    for (ReportInfo reportInfo : form.getReports())
    {%>
    Report '<%=text(reportInfo.getName())%>' (Category '<%=text(viewCategoryMap.get(reportInfo.getCategoryId()).getLabel())%>') Modified <%=text(DateUtil.formatDateTime(form.getContainer(), reportInfo.getModified()))%><%}%>

    <%
    for (ReportInfo reportInfo : form.getDatasets())
    {%>
    Dataset '<%=text(reportInfo.getName())%>' (Category '<%=text(viewCategoryMap.get(reportInfo.getCategoryId()).getLabel())%>') Modified <%=text(DateUtil.formatDateTime(form.getContainer(), reportInfo.getModified()))%><%}%>


    You have received this email because you are signed up to receive notifications about changes to
    reports and datasets at <%=text(form.getContainer().getPath()) %>.
    If you no longer wish to receive these notifications you can change your email preferences.


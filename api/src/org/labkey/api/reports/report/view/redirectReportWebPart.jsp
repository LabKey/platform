<%
/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.reports.model.ReportPropsManager" %>
<%@ page import="org.labkey.api.reports.model.ViewCategory" %>
<%@ page import="org.labkey.api.reports.report.RedirectReport" %>
<%@ page import="org.labkey.api.reports.report.view.ReportUtil" %>
<%@ page import="org.labkey.api.security.UserManager" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    RedirectReport report = (RedirectReport)getModelBean();

    String url = report.getUrl(getContainer());
    Map<String, String> reportURLAttributes =  report.getRunReportTarget() != null ?
            Collections.singletonMap("target", report.getRunReportTarget()) :
            Collections.emptyMap();

    URLHelper thumbnailURL = ReportUtil.getThumbnailUrl(getContainer(), report);

    String name = report.getDescriptor().getReportName();
    String description = report.getDescriptor().getReportDescription();
    String type = report.getTypeDescription();

    Integer authorId = report.getDescriptor().getAuthor();
    if (authorId == null)
    {
        Object o = ReportPropsManager.get().getPropertyValue(report.getEntityId(), getContainer(), "author");
        if (o instanceof Double)
            authorId = ((Double)o).intValue();
    }
    String author = null;
    if (authorId != null && authorId > 0)
        author = UserManager.getDisplayNameOrUserId(authorId, getUser());

    String category = null;
    ViewCategory viewCategory = report.getDescriptor().getCategory(getContainer());
    if (viewCategory != null)
        category = viewCategory.getLabel();

%>
<a href='<%=h(url)%>'>
<img style='width:100px; height: auto; float: left;' src='<%=h(thumbnailURL)%>'>
</a>
<table style='padding-left: 1em;'>
    <% if (name != null) { %> <tr><td><b>Name:</b></td><td><%=h(name)%></td></tr> <% } %>
    <% if (author != null) { %> <tr><td><b>Author:</b></td><td><%=h(author)%></td></tr> <% } %>
    <% if (category != null) { %> <tr><td><b>Category:</b></td><td><%=h(category)%></td></tr> <% } %>
    <% if (type != null) { %> <tr><td><b>Type:</b></td><td><%=h(type)%></td></tr> <% } %>
    <% if (description != null) { %> <tr><td><b>Description:</b></td><td><%=h(description)%></td></tr> <% } %>
    <tr><td colspan=2>
        <%=PageFlowUtil.textLink("view report", url, null, null, reportURLAttributes)%>
    </td></tr>
</table>
<div style='clear: both;'></div>


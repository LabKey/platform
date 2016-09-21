<%
    /*
     * Copyright (c) 2006-2015 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ page import="org.labkey.issue.actions.DeleteIssueListAction" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.query.QueryAction" %>
<%@ page import="org.labkey.issue.query.IssuesQuerySchema" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<IssuesController.BuildSummaryBean> me = (JspView<IssuesController.BuildSummaryBean>)HttpView.currentView();
    IssuesController.BuildSummaryBean bean = me.getModelBean();
    ActionURL cancelURL = QueryService.get().urlFor(getUser(), getContainer(), QueryAction.executeQuery, "issues", IssuesQuerySchema.TableType.IssueListDef.name());

%>
    Build summary things and stuff
    <br><%

    for(IssuesController.BuildIssue buildIssue : bean.getBuildIssues())
    {
        out.println("<br>Issue ID: " + buildIssue.getIssueId() + "<br>");
        out.println("Title: " + buildIssue.getTitle() + "<br>");
        out.println("Client: " + buildIssue.getClient() + "<br>");
        out.println("Area: " + buildIssue.getArea() + "<br>");
        out.println("Milestone: " + buildIssue.getMilestone() + "<br>");
        out.println("Status: " + buildIssue.getStatus() + "<br>");
        out.println("Type: " + buildIssue.getType() + "<br>");
        out.println("Resolved: " + buildIssue.getResolved() + "<br>");
        out.println("Closed: " + buildIssue.getClosed() + "<br>");
    }

    %>

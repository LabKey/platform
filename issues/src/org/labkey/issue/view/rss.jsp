<%
/*
 * Copyright (c) 2005-2017 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ page import="org.labkey.issue.model.Issue" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%
    JspView<IssuesController.RssBean> me = (JspView<IssuesController.RssBean>) HttpView.currentView();
    IssuesController.RssBean bean = me.getModelBean();
    Container c = getContainer();
    User user = getUser();
    LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
    AppProps app = AppProps.getInstance();

    getViewContext().getResponse().setContentType("text/xml");

%><rss version="2.0">
<channel>
    <title><%=h(laf.getShortName())%>: Issues - <%=h(c.getPath())%> </title>
    <link><%=h(app.getHomePageActionURL())%></link>
    <description><%=h(laf.getShortName())%>: Issues</description>
<%
for (Issue issue : bean.issues)
{ %>
    <item>
        <title><%=issue.getIssueId()%>: <%=h(issue.getMilestone()) %> <%=h(issue.getStatus())%> <%=h(issue.getAssignedToName(user))%> <%=h(issue.getTitle())%></title>
        <link><%=text(bean.filteredURLString)%><%=issue.getIssueId()%></link>
        <guid><%=text(bean.filteredURLString)%><%=issue.getIssueId()%></guid>
        <pubDate><%=issue.getCreated()%></pubDate>
        <description>
openedby <%=h(issue.getCreatedByName(user))%>
priority <%=issue.getPriority()%>
type <%=h(issue.getType())%>
area <%=h(issue.getArea())%>
        </description>
    </item>
<% } %>
</channel>
</rss>

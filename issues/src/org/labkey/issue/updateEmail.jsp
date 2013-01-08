<%
/*
 * Copyright (c) 2005-2013 LabKey Corporation
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
%><%@ page import="org.labkey.api.data.Container"
%><%@ page import="org.labkey.api.util.HString"
%><%@ page import="org.labkey.api.util.PageFlowUtil"
%><%@ page import="org.labkey.api.view.HttpView"
%><%@ page import="org.labkey.api.view.JspView" 
%><%@ page import="org.labkey.issue.IssuesController"
%><%@ page import="org.labkey.issue.model.Issue"
%>
<%@ page extends="org.labkey.api.jsp.JspBase"
%><%
    JspView<IssuesController.UpdateEmailPage> me = (JspView<IssuesController.UpdateEmailPage>)HttpView.currentView();
    IssuesController.UpdateEmailPage bean = me.getModelBean();
    Container c = getViewContext().getContainer();

    String changeComment = "(No change comment)";
    String modifiedBy = "(unknown)";
    Issue.Comment lastComment = bean.issue.getLastComment();

    if (lastComment != null)
    {
        modifiedBy = lastComment.getCreatedByName(me.getViewContext().getUser());
        changeComment = lastComment.getComment();
    }

    if (bean.isPlain)
    {
        %>You can review this issue from this URL: <%=text(bean.url)%><%
    }
    else
    {

%><html>
<head>
<base href="<%=h(getViewContext().getActionURL().getBaseServerURI() + getViewContext().getContextPath())%>">
<%=PageFlowUtil.getStylesheetIncludes(c, getViewContext().getUser())%>
</head>
<body><%
        %>You can review this issue here: <a href="<%=h(bean.url)%>"><%=h(bean.url)%></a><br/><%
        %>Modified by: <%=h(modifiedBy)%><br/><%
        %><%=text(changeComment)%>
</body>
</html>
<%
    }
%>

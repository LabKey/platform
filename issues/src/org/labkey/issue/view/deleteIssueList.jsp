<%
    /*
     * Copyright (c) 2016 LabKey Corporation
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
    JspView<DeleteIssueListAction.DeleteIssueListForm> me = (JspView<DeleteIssueListAction.DeleteIssueListForm>)HttpView.currentView();
    DeleteIssueListAction.DeleteIssueListForm bean = me.getModelBean();
    ActionURL cancelURL = QueryService.get().urlFor(getUser(), getContainer(), QueryAction.executeQuery, "issues", IssuesQuerySchema.TableType.IssueListDef.name());

%>

<labkey:form method="post">
    You are about to delete the following issue list(s):
    <p/>
    <table><%
    for (int i=0; i < bean.getIssueDefNames().size(); i++)
    {%>
        <tr><td><b><%=h(bean.getIssueDefNames().get(i))%></b></td><td>&nbsp;<%=bean.getRowCounts().get(i)%> total issues.</td></tr><%
    }
    for (int i=0; i < bean.getIssueDefNames().size(); i++)
    {%>
        <input type="hidden" name="issueDefId" value="<%=bean.getIssueDefId().get(i)%>"><%
    }%>
    </table><p/>
    Are you sure you want to permanently delete the issue list(s)?<p/>
    <%= button("Delete").submit(true) %>
    <%= button("Cancel").href(cancelURL) %>
</labkey:form>
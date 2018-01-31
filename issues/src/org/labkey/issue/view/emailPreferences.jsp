<%
/*
 * Copyright (c) 2006-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.data.DataRegion"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ page import="org.labkey.issue.model.IssueListDef" %>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page import="org.labkey.issue.view.IssuesListView" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<IssuesController.EmailPrefsForm> me = (JspView<IssuesController.EmailPrefsForm>)HttpView.currentView();
    Container c = getContainer();
    IssuesController.EmailPrefsForm bean = me.getModelBean();
    int emailPrefs = bean.getSavedPrefs();
    String message = bean.getMessage();
    int issueId = bean.getIssueId();
    IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(c, IssueListDef.DEFAULT_ISSUE_LIST_NAME);
    String indefArticle = names.getIndefiniteSingularArticle();

    if (message != null)
    {
        %><b><%=h(message)%></b><p/><%
    }
%>
<labkey:errors/>
<labkey:form action="<%= new ActionURL(IssuesController.EmailPrefsAction.class, c).addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, bean.getIssueDefName()) %>" method="post">
    <input type="checkbox" value="1" name="emailPreference"<%=checked((emailPrefs & IssueManager.NOTIFY_ASSIGNEDTO_OPEN) != 0)%>>
    Send me email when <%=h(indefArticle)%> <%=h(names.singularName)%> is opened and assigned to me<br>
    <input type="checkbox" value="2" name="emailPreference"<%=checked((emailPrefs & IssueManager.NOTIFY_ASSIGNEDTO_UPDATE) != 0)%>>
    Send me email when <%=h(indefArticle)%> <%=h(names.singularName)%> that's assigned to me is modified<br>
    <input type="checkbox" value="4" name="emailPreference"<%=checked((emailPrefs & IssueManager.NOTIFY_CREATED_UPDATE) != 0)%>>
    Send me email when <%=h(indefArticle)%> <%=h(names.singularName)%> I opened is modified<br>
    <input type="checkbox" value="16" name="emailPreference"<%=checked((emailPrefs & IssueManager.NOTIFY_SUBSCRIBE) != 0)%>>
    Send me email when any <%=h(names.singularName)%> is created or modified<br>
    <hr/>
    <input type="checkbox" value="8" name="emailPreference"<%=checked((emailPrefs & IssueManager.NOTIFY_SELF_SPAM) != 0)%>>
    Send me email notifications when I create or modify <%=h(indefArticle)%> <%=h(names.singularName)%><br>
    <br>
    <%= button("Update").submit(true) %><%
    if (issueId > 0)
    {
        %><%= button("Back to " + names.singularName).href(IssuesController.issueURL(c, IssuesController.DetailsAction.class).addParameter("issueId", bean.getIssueId())) %><%
    }
    else
    {
        %><%= button("View Grid").href(IssuesController.issueURL(c, IssuesController.ListAction.class).addParameter(DataRegion.LAST_FILTER_PARAM, "true")) %><%
    }
%>
</labkey:form>
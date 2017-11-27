<%
    /*
     * Copyright (c) 2006-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.security.permissions.InsertPermission"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page import="org.labkey.issue.view.IssuesListView" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Container c = getContainer();
    String issueListDef = (String)getModelBean();
    IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(c, issueListDef);

    if (request.getParameter("error") != null)
    {
%>
<span class="labkey-error"><%=h(request.getParameter("error"))%></span><br/>
<%  } %>
<div style="display:inline-block;margin-bottom:10px;">
    <% if (c.hasPermission(getUser(), InsertPermission.class)) { %>
    <div style="float:left;">
        <%= button("New " + names.singularName).href(new ActionURL(IssuesController.InsertAction.class, c).addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, issueListDef))%>
    </div>
    <% } %>
    <div style="float:left;margin-left:20px;">
        <labkey:form name="jumpToIssue" action="<%= new ActionURL(IssuesController.JumpToIssueAction.class, c) %>" layout="inline">
            <div class="input-group">
                <labkey:input name="issueId" formGroup="false" placeholder="ID # or Search Term"/>
                <div class="input-group-btn">
                    <%= button("Search").addClass("btn btn-default").iconCls("search").submit(true) %>
                </div>
            </div>
        </labkey:form>
    </div>
</div>
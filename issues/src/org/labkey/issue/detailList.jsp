<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

<%@ page import="java.util.*"%>
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.data.DataRegion"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.ViewContext"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.issue.IssuesController"%>
<%@ page import="org.labkey.issue.model.Issue"%>
<%@ page import="org.labkey.issue.IssuePage" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.data.DataRegionSelection" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<IssuePage> me = (JspView<IssuePage>) HttpView.currentView();
    ViewContext context = me.getViewContext();
    IssuePage bean = me.getModelBean();

    final List<Issue> issueList = bean.getIssueList();
    final Container c = context.getContainer();
    Issue issue = null;
    String issueId = null;

    ActionURL printLink = context.cloneActionURL().replaceParameter("_print", "1");
    if (bean.getDataRegionSelectionKey() != null)
        printLink.replaceParameter(DataRegionSelection.DATA_REGION_SELECTION_KEY, bean.getDataRegionSelectionKey());
%>

<form name="jumpToIssue" action="jumpToIssue.view" method="get">
<table border=0 cellspacing=2 cellpadding=2><tr>
    <td><%= textLink("new issue", IssuesController.issueURL(context.getContainer(), "insert").addParameter(DataRegion.LAST_FILTER_PARAM, "true"))%></td>
    <td><%= textLink("view grid", IssuesController.issueURL(context.getContainer(), "list").addParameter(DataRegion.LAST_FILTER_PARAM, "true"))%></td>
    <td><%= textLink("print", printLink)%></td>
    <td>&nbsp;&nbsp;&nbsp;Jump to issue: <input type="text" size="5" name="issueId"/></td>
</tr></table>
</form>

<%
    for (ListIterator<Issue> iterator = issueList.listIterator(); iterator.hasNext(); )
    {
        issue = iterator.next();
        issueId = Integer.toString(issue.getIssueId());
%>
<table width=640>
    <tr><td class="wpTitle" colspan="3"><%=issueId + " : " + h(issue.getTitle())%></td></tr>
    <tr>
        <td valign="top" width="34%"><table>
            <tr><td class="ms-searchform">Status</td><td class="ms-vb"><%=h(issue.getStatus())%></td></tr>
            <tr><td class="ms-searchform">Assigned&nbsp;To</td><td class="ms-vb"><%=h(issue.getAssignedToName(context))%></td></tr>
            <tr><td class="ms-searchform">Type</td><td class="ms-vb"><%=h(issue.getType())%></td></tr>
            <tr><td class="ms-searchform">Area</td><td class="ms-vb"><%=h(issue.getArea())%></td></tr>
            <tr><td class="ms-searchform">Priority</td><td class="ms-vb"><%=bean._toString(issue.getPriority())%></td></tr>
            <tr><td class="ms-searchform">Milestone</td><td class="ms-vb"><%=h(issue.getMilestone())%></td></tr>
        </table></td>
        <td valign="top" width="33%"><table>
            <tr><td class="ms-searchform">Opened&nbsp;By</td><td class="ms-vb"><%=h(issue.getCreatedByName(context))%></td></tr>
            <tr><td class="ms-searchform">Opened</td><td class="ms-vb"><%=bean.writeDate(issue.getCreated())%></td></tr>
            <tr><td class="ms-searchform">Resolved By</td><td class="ms-vb"><%=h(issue.getResolvedByName(context))%></td></tr>
            <tr><td class="ms-searchform">Resolved</td><td class="ms-vb"><%=bean.writeDate(issue.getResolved())%></td></tr>
            <tr><td class="ms-searchform">Resolution</td><td class="ms-vb"><%=h(issue.getResolution())%></td></tr>
<%
            if (bean.isEditable("resolution") || !"open".equals(issue.getStatus()) && null != issue.getDuplicate())
            {
%>
                <tr><td class="ms-searchform">Duplicate</td><td class="ms-vb">
                <%=bean.writeInput("duplicate", null == issue.getDuplicate() ? null : issue.getDuplicate().toString())%>
                </td></tr>
<%
            }
%>
            <%=bean.writeCustomColumn(c.getId(), "int1", bean._toString(issue.getInt1()), IssuesController.ISSUE_NONE)%>
            <%=bean.writeCustomColumn(c.getId(), "int2", bean._toString(issue.getInt2()), IssuesController.ISSUE_NONE)%>
        </table></td>
        <td valign="top" width="33%"><table>
            <tr><td class="ms-searchform">Changed&nbsp;By</td><td class="ms-vb"><%=h(issue.getModifiedByName(context))%></td></tr>
            <tr><td class="ms-searchform">Changed</td><td class="ms-vb"><%=bean.writeDate(issue.getModified())%></td></tr>
            <tr><td class="ms-searchform">Closed&nbsp;By</td><td class="ms-vb"><%=h(issue.getClosedByName(context))%></td></tr>
            <tr><td class="ms-searchform">Closed</td><td class="ms-vb"><%=bean.writeDate(issue.getClosed())%></td></tr>

            <%=bean.writeCustomColumn(c.getId(), "string1", issue.getString1(), IssuesController.ISSUE_STRING1)%>
            <%=bean.writeCustomColumn(c.getId(), "string2", issue.getString2(), IssuesController.ISSUE_STRING2)%>
        </table></td>
    </tr>
</table>
<%
        if (bean.getCallbackURL() != null)
        {
            %><input type="hidden" name="callbackURL" value="<%=bean.getCallbackURL()%>"/><%
        }

        for (Issue.Comment comment : issue.getComments())
        {
%>
        <hr><table width="100%"><tr><td align="left" class="ms-vb"><b>
        <%=bean.writeDate(comment.getCreated())%>
        </b></td><td align="right" class="ms-vb"><b>
        <%=h(comment.getCreatedByName(context))%>
        </b></td></tr></table>
        <%=comment.getComment()%>
        <%=bean.renderAttachments(context, comment)%>
<%
        }
%>
<p>&nbsp;</p>
<p>&nbsp;</p>
<%
    }
%>
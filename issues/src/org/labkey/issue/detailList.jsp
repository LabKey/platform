<%
/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.util.HString" %>
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
<table><tr>
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
    <tr><td colspan="3"><%=issueId + " : " + h(issue.getTitle())%></td></tr>
    <tr>
        <td valign="top" width="34%"><table>
            <tr><td class="labkey-form-label">Status</td><td><%=h(issue.getStatus())%></td></tr>
            <tr><td class="labkey-form-label">Assigned&nbsp;To</td><td><%=h(issue.getAssignedToName(context))%></td></tr>
            <tr><td class="labkey-form-label">Type</td><td><%=h(issue.getType())%></td></tr>
            <tr><td class="labkey-form-label">Area</td><td><%=h(issue.getArea())%></td></tr>
            <tr><td class="labkey-form-label">Priority</td><td><%=bean._toString(issue.getPriority())%></td></tr>
            <tr><td class="labkey-form-label">Milestone</td><td><%=h(issue.getMilestone())%></td></tr>
        </table></td>
        <td valign="top" width="33%"><table>
            <tr><td class="labkey-form-label">Opened&nbsp;By</td><td><%=h(issue.getCreatedByName(context))%></td></tr>
            <tr><td class="labkey-form-label">Opened</td><td><%=bean.writeDate(issue.getCreated())%></td></tr>
            <tr><td class="labkey-form-label">Resolved By</td><td><%=h(issue.getResolvedByName(context))%></td></tr>
            <tr><td class="labkey-form-label">Resolved</td><td><%=bean.writeDate(issue.getResolved())%></td></tr>
            <tr><td class="labkey-form-label">Resolution</td><td><%=h(issue.getResolution())%></td></tr>
<%
            if (bean.isEditable("resolution") || !"open".equals(issue.getStatus()) && null != issue.getDuplicate())
            {
%>
                <tr><td class="labkey-form-label">Duplicate</td><td>
                <%=bean.writeInput(new HString("duplicate"), HString.valueOf(issue.getDuplicate()))%>
                </td></tr>
<%
            }
%>
            <%=bean.writeCustomColumn(c, new HString("int1"), HString.valueOf(issue.getInt1()), IssuesController.ISSUE_NONE)%>
            <%=bean.writeCustomColumn(c, new HString("int2"), HString.valueOf(issue.getInt2()), IssuesController.ISSUE_NONE)%>
        </table></td>
        <td valign="top" width="33%"><table>
            <tr><td class="labkey-form-label">Changed&nbsp;By</td><td><%=h(issue.getModifiedByName(context))%></td></tr>
            <tr><td class="labkey-form-label">Changed</td><td><%=bean.writeDate(issue.getModified())%></td></tr>
            <tr><td class="labkey-form-label">Closed&nbsp;By</td><td><%=h(issue.getClosedByName(context))%></td></tr>
            <tr><td class="labkey-form-label">Closed</td><td><%=bean.writeDate(issue.getClosed())%></td></tr>

            <%=bean.writeCustomColumn(c, new HString("string1"), issue.getString1(), IssuesController.ISSUE_STRING1)%>
            <%=bean.writeCustomColumn(c, new HString("string2"), issue.getString2(), IssuesController.ISSUE_STRING2)%>
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
        <hr><table width="100%"><tr><td align="left"><b>
        <%=bean.writeDate(comment.getCreated())%>
        </b></td><td align="right"><b>
        <%=h(comment.getCreatedByName(context))%>
        </b></td></tr></table>
        <%=comment.getComment().getSource()%>
        <%=bean.renderAttachments(context, comment)%>
<%
        }
%>
<p>&nbsp;</p>
<p>&nbsp;</p>
<%
    }
%>

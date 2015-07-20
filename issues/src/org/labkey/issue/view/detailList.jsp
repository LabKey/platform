<%
/*
 * Copyright (c) 2007-2015 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ContainerManager"%>
<%@ page import="org.labkey.api.data.DataRegion"%>
<%@ page import="org.labkey.api.data.DataRegionSelection"%>
<%@ page import="org.labkey.api.security.User"%>
<%@ page import="org.labkey.api.security.permissions.ReadPermission"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.issue.ColumnType" %>
<%@ page import="org.labkey.issue.model.IssuePage" %>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ page import="org.labkey.issue.model.Issue" %>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page import="java.util.Set" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<IssuePage> me = (JspView<IssuePage>) HttpView.currentView();
    ViewContext context = getViewContext();
    IssuePage bean = me.getModelBean();

    Set<String> issueIds = bean.getIssueIds();
    final Container c = getContainer();
    final User user = getUser();
    IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(c);

    ActionURL printLink = context.cloneActionURL().replaceParameter("_print", "1");
    if (bean.getDataRegionSelectionKey() != null)
        printLink.replaceParameter(DataRegionSelection.DATA_REGION_SELECTION_KEY, bean.getDataRegionSelectionKey());


    if (!bean.isPrint())
    {
%>
<labkey:form name="jumpToIssue" action="<%=h(buildURL(IssuesController.JumpToIssueAction.class))%>" method="get">
<table><tr>
    <td><%= textLink("new " + names.singularName.getSource().toLowerCase(), IssuesController.issueURL(c, IssuesController.InsertAction.class).addParameter(DataRegion.LAST_FILTER_PARAM, "true"))%></td>
    <td><%= textLink("view grid", IssuesController.issueURL(c, IssuesController.ListAction.class).addParameter(DataRegion.LAST_FILTER_PARAM, "true"))%></td>
    <td><%= textLink("print", printLink)%></td>
    <td>&nbsp;&nbsp;&nbsp;Jump to <%=h(names.singularName)%>: <input type="text" size="5" name="issueId"/></td>
</tr></table>
</labkey:form>
<%
    }

    for (String issueId : issueIds )
    {
        Issue issue = IssueManager.getIssue(null, Integer.parseInt(issueId));
        boolean hasReadPermission = ContainerManager.getForId(issue.getContainerId()).hasPermission(getUser(), ReadPermission.class);

        if (!hasReadPermission)
            continue;

        bean.setIssue(issue);
%>
<table width=640>
    <tr><td colspan="3"><h3><%=h(issueId)%> : <%=h(issue.getTitle())%></h3></td></tr>
    <tr>
        <td valign="top" width="34%"><table>
            <tr><td class="labkey-form-label">Status</td><td><%=h(issue.getStatus())%></td></tr>
            <tr><td class="labkey-form-label">Assigned&nbsp;To</td><td><%=h(issue.getAssignedToName(user))%></td></tr>
            <tr><td class="labkey-form-label">Type</td><td><%=h(issue.getType())%></td></tr>
            <tr><td class="labkey-form-label">Area</td><td><%=h(issue.getArea())%></td></tr>
            <tr><td class="labkey-form-label">Priority</td><td><%=h(issue.getPriority())%></td></tr>
            <tr><td class="labkey-form-label">Milestone</td><td><%=h(issue.getMilestone())%></td></tr>
        </table></td>
        <td valign="top" width="33%"><table>
            <tr><td class="labkey-form-label">Opened&nbsp;By</td><td><%=h(issue.getCreatedByName(user))%></td></tr>
            <tr><td class="labkey-form-label">Opened</td><td><%=h(bean.writeDate(issue.getCreated()))%></td></tr>
            <tr><td class="labkey-form-label">Resolved By</td><td><%=h(issue.getResolvedByName(user))%></td></tr>
            <tr><td class="labkey-form-label">Resolved</td><td><%=h(bean.writeDate(issue.getResolved()))%></td></tr>
            <tr><td class="labkey-form-label">Resolution</td><td><%=h(issue.getResolution())%></td></tr>
<%
            if (bean.isEditable("resolution") || !"open".equals(issue.getStatus()) && null != issue.getDuplicate())
            {
%>
                <tr><td class="labkey-form-label">Duplicate</td><td>
                <%=text(bean.writeInput("duplicate", String.valueOf(issue.getDuplicate()), 10))%>
                </td></tr>
<%
            }
%>
            <%=text(bean.writeCustomColumn(ColumnType.INT1, 10, false))%>
            <%=text(bean.writeCustomColumn(ColumnType.INT2, 10, false))%>
            <%=text(bean.writeCustomColumn(ColumnType.STRING1, 10, false))%>
        </table></td>
        <td valign="top" width="33%"><table>
            <tr><td class="labkey-form-label">Changed&nbsp;By</td><td><%=h(issue.getModifiedByName(user))%></td></tr>
            <tr><td class="labkey-form-label">Changed</td><td><%=h(bean.writeDate(issue.getModified()))%></td></tr>
            <tr><td class="labkey-form-label">Closed&nbsp;By</td><td><%=h(issue.getClosedByName(user))%></td></tr>
            <tr><td class="labkey-form-label">Closed</td><td><%=h(bean.writeDate(issue.getClosed()))%></td></tr>

            <%=text(bean.writeCustomColumn(ColumnType.STRING2, 20, false))%>
            <%=text(bean.writeCustomColumn(ColumnType.STRING3, 20, false))%>
            <%=text(bean.writeCustomColumn(ColumnType.STRING4, 20, false))%>
            <%=text(bean.writeCustomColumn(ColumnType.STRING5, 20, false))%>
        </table></td>
    </tr>
</table>
<%
        if (bean.getCallbackURL() != null)
        {
            %><input type="hidden" name="callbackURL" value="<%=h(bean.getCallbackURL())%>"/><%
        }

        for (Issue.Comment comment : issue.getComments())
        {
%>
        <hr><table width="100%"><tr><td align="left"><b>
        <%=h(bean.writeDate(comment.getCreated()))%>
        </b></td><td align="right"><b>
        <%=h(comment.getCreatedByName(user))%>
        </b></td></tr></table>
        <%=text(comment.getComment())%>
        <%=text(bean.renderAttachments(context, comment))%>
<%
        }
%>
<br>
<br>
<%
    }
%>

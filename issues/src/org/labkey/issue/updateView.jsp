<%
/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.data.DataRegion"%>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.issue.ColumnType" %>
<%@ page import="org.labkey.issue.IssuePage" %>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ page import="org.labkey.issue.model.Issue" %>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page import="org.labkey.issue.model.IssueManager.EntryTypeNames" %>
<%@ page import="org.springframework.validation.BindException" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<IssuePage> me = (JspView<IssuePage>) HttpView.currentView();
    ViewContext context = me.getViewContext();
    IssuePage bean = me.getModelBean();
    final Issue issue = bean.getIssue();
    final Container c = context.getContainer();
    final User user = context.getUser();
    final String focusId = (0 == issue.getIssueId() ? "title" : "comment");
    int emailPrefs = IssueManager.getUserEmailPreferences(context.getContainer(), user.getUserId());
    final String popup = getNotifyHelpPopup(emailPrefs, issue.getIssueId(), IssueManager.getEntryTypeNames(c));

    BindException errors = bean.getErrors();
    ActionURL completionUrl = new ActionURL(IssuesController.CompleteUserAction.class, c);
    ActionURL cancelURL;

    if (issue.getIssueId() > 0)
    {
        cancelURL = IssuesController.issueURL(context.getContainer(), IssuesController.DetailsAction.class).addParameter("issueId", issue.getIssueId());
    }
    else
    {
        cancelURL = IssuesController.issueURL(context.getContainer(), IssuesController.ListAction.class).addParameter(DataRegion.LAST_FILTER_PARAM, "true");
    }
%>

<script type="text/javascript">
    var numberRe = /[0-9]/;
    function filterNumber(e, input)
    {
        if (e.isSpecialKey())
            return true;

        var cc = String.fromCharCode(e.getCharCode());
        if (!cc)
            return true;

        if (!numberRe.test(cc))
        {
            if (e.stopPropagation) {
                e.stopPropagation();
            } else {
                e.cancelBubble = true;
            }
            if (e.preventDefault) {
                e.preventDefault();
            } else {
                e.returnValue = false;
            }
            return false;
        }

        return true;
    }
</script>
<form method="POST" onsubmit="LABKEY.setSubmit(true); return true;" enctype="multipart/form-data" action="<%=IssuesController.issueURL(context.getContainer(), bean.getAction())%>">

    <table>
    <%
        if (null != errors && 0 != errors.getErrorCount())
        {
            for (ObjectError e : (List<ObjectError>) errors.getAllErrors())
            {
                %><tr><td colspan=3><font class="labkey-error"><%=h(context.getMessage(e))%></font></td></tr><%
            }
        }
    if (!bean.getRequiredFields().isEmpty())
    {
        %><tr><td>Fields marked with an asterisk <span class="labkey-error">*</span> are required.</td></tr><%
    }
    %>
    </table>

    <table>
        <tr>
            <td align="right" valign="top"><%=PageFlowUtil.generateSubmitButton("Save", null, "name=\"" + bean.getAction() + "\"", true, true)%><%= generateButton("Cancel", cancelURL)%></td>
        </tr>
        <tr>
<%
            if (0 == issue.getIssueId())
            {
%>
                <td class="labkey-form-label"><%=bean.getLabel("Title")%></td>
<%
            } else {
%>
                <td class="labkey-form-label">Issue <%=issue.getIssueId()%></td>
<%
            }
%>
                <td colspan="3">
                <%=bean.writeInput("title", issue.getTitle(), "id=title tabindex=\"1\" style=\"width:100%;\"")%>
                </td></tr>
            <tr>
                <td class="labkey-form-label"><%=bean.getLabel("Status")%></td><td><%=h(issue.getStatus())%></td>
                <td rowspan="6" valign="top">
                    <table>
                        <tr><td class="labkey-form-label"><%=bean.getLabel("Opened")%></td><td nowrap="true"><%=text(bean.writeDate(issue.getCreated()))%> by <%=h(issue.getCreatedByName(user))%></td></tr>
                        <tr><td class="labkey-form-label">Changed</td><td nowrap="true"><%=text(bean.writeDate(issue.getModified()))%> by <%=h(issue.getModifiedByName(user))%></td></tr>
                        <tr><td class="labkey-form-label"><%=bean.getLabel("Resolved")%></td><td nowrap="true"><%=text(bean.writeDate(issue.getResolved()))%><%=text(issue.getResolvedBy() != null ? " by " : "")%> <%=h(issue.getResolvedByName(user))%></td></tr>
                        <tr><td class="labkey-form-label"><%=bean.getLabel(ColumnType.RESOLUTION)%></td><td><%=bean.writeSelect(ColumnType.RESOLUTION, 2)%></td></tr>
        <% if (bean.isEditable("resolution") || !"open".equals(issue.getStatus())) { %>
                        <tr><td class="labkey-form-label">Duplicate</td><td>
                        <% if (bean.isEditable("duplicate")) {
                                if("Duplicate".equals(issue.getResolution()))
                                {
                                    //Enabled duplicate field.
                        %>
                                    <%=bean.writeInput("duplicate", issue.getDuplicate() == null ? null : String.valueOf(issue.getDuplicate()), "tabindex=\"2\"")%>
                        <%
                                }
                                else
                                {
                                    //Disabled duplicate field.
                        %>
                                    <%=bean.writeInput("duplicate", issue.getDuplicate() == null ? null : String.valueOf(issue.getDuplicate()), "tabindex=\"2\" disabled")%>
                        <%
                                }
                        %>
                            <script type="text/javascript">
                                var duplicateInput = document.getElementsByName('duplicate')[0];
                                var duplicateOrig = duplicateInput.value;
                                var resolutionSelect = document.getElementById('resolution');
                                function updateDuplicateInput()
                                {
                                    // The options don't have an explicit value set, so look for the display text instead of
                                    // the value
                                    if (resolutionSelect.selectedIndex >= 0 &&
                                        resolutionSelect.options[resolutionSelect.selectedIndex].text == 'Duplicate')
                                    {
                                        duplicateInput.disabled = false;
                                    }
                                    else
                                    {
                                        duplicateInput.disabled = true;
                                        duplicateInput.value = duplicateOrig;
                                    }
                                }
                                if (window.addEventListener)
                                    resolutionSelect.addEventListener('change', updateDuplicateInput, false);
                                else if (window.attachEvent)
                                    resolutionSelect.attachEvent('onchange', updateDuplicateInput);
                                Ext.EventManager.on(duplicateInput, 'keypress', filterNumber);
                            </script>
                        <%
                            }
                            else
                            {
                                if(issue.getDuplicate() != null)
                                {
                        %>
                            <a href="<%=IssuesController.getDetailsURL(context.getContainer(), issue.getDuplicate(), false)%>"><%=issue.getDuplicate()%></a>
                        <%
                                }
                            }
                        %>
                        </td></tr>
        <% } %>
                        <%=text(bean.writeCustomColumn(ColumnType.INT1, 2))%>
                        <%=text(bean.writeCustomColumn(ColumnType.INT2, 2))%>
                        <%=text(bean.writeCustomColumn(ColumnType.STRING1, 2))%>
                    </table>
                </td>
                <td valign="top" rowspan="6"><table>
                    <tr><td class="labkey-form-label">Closed</td><td><%=text(bean.writeDate(issue.getClosed()))%><%=text(issue.getClosedBy() != null ? " by " : "")%><%=h(issue.getClosedByName(user))%></td></tr>
    <%
                if (bean.isEditable("notifyList"))
                {
    %>
                    <tr>
                        <td class="labkey-form-label-nowrap"><%=text(bean.getLabel("NotifyList"))%><%=text(popup)%><br/><br/>
    <%
                        if (issue.getIssueId() == 0)
                        {
    %>
                            <%= textLink("email prefs", IssuesController.issueURL(context.getContainer(), IssuesController.EmailPrefsAction.class).getLocalURIString(), null, null)%>
    <%
                        } else {
    %>
                            <%= textLink("email prefs", IssuesController.issueURL(context.getContainer(), IssuesController.EmailPrefsAction.class).addParameter("issueId", issue.getIssueId()).getLocalURIString(), null, null)%>
    <%
                        }
    %>
                        </td>
                        <td>
                            <labkey:autoCompleteTextArea name="notifyList" id="notifyList" url="<%=h(completionUrl.getLocalURIString())%>" rows="4" tabindex="3" cols="30" value="<%=PageFlowUtil.filter(bean.getNotifyListString(false).toString())%>"/>
                        </td>
                    </tr>
    <%
                } else {
    %>
                    <tr><td class="labkey-form-label">Notify</td><td><%=bean.getNotifyList()%></td></tr>
    <%
                }
    %>
                    <%=text(bean.writeCustomColumn(ColumnType.STRING2, 3))%>
                    <%=text(bean.writeCustomColumn(ColumnType.STRING3, 3))%>
                    <%=text(bean.writeCustomColumn(ColumnType.STRING4, 3))%>
                    <%=text(bean.writeCustomColumn(ColumnType.STRING5, 3))%>
                </table></td>
            </tr>
        <tr><td class="labkey-form-label"><%=bean.getLabel("AssignedTo")%></td><td><%=bean.writeSelect("assignedTo", String.valueOf(issue.getAssignedTo()), issue.getAssignedToName(user), bean.getUserOptions(), 1)%></td></tr>
        <tr><td class="labkey-form-label"><%=bean.getLabel(ColumnType.TYPE)%></td><td><%=bean.writeSelect(ColumnType.TYPE, 1)%></td></tr>
        <tr><td class="labkey-form-label"><%=bean.getLabel(ColumnType.AREA)%></td><td><%=bean.writeSelect(ColumnType.AREA, 1)%></td></tr>
        <tr><td class="labkey-form-label"><%=bean.getLabel(ColumnType.PRIORITY)%></td><td><%=bean.writeSelect(ColumnType.PRIORITY, 1)%></td></tr>
        <tr><td class="labkey-form-label"><%=bean.getLabel(ColumnType.MILESTONE)%></td><td><%=bean.writeSelect(ColumnType.MILESTONE, 1)%></td></tr>
        <tr><td class="labkey-form-label">Comment</td>
            <td colspan="3">
                <textarea id="comment" name="comment" cols="150" rows="20" style="width: 99%;" onchange="LABKEY.setDirty(true);return true;" tabindex="1"><%=bean.getBody() != null ? h(bean.getBody()) : ""%></textarea>
            </td></tr>
        <tr>
            <td align="right" valign="top"><%=PageFlowUtil.generateSubmitButton("Save", null, "tabindex=\"5\" name=\"" + bean.getAction() + "\"", true, true)%><%= PageFlowUtil.generateButton("Cancel", cancelURL, null, "tabIndex=\"5\"")%></td>
        </tr>
    </table>

    <table>
        <tr><td><table id="filePickerTable"></table></td></tr>
        <tr><td><a href="javascript:addFilePicker('filePickerTable','filePickerLink')" id="filePickerLink"><img src="<%=h(context.getRequest().getContextPath())%>/_images/paperclip.gif">Attach a file</a></td></tr>
    </table>
    
<%
    if (bean.getCallbackURL() != null)
    {
%>
    <input type="hidden" name="callbackURL" value="<%=h(bean.getCallbackURL())%>"/>
<%
    }

    for (Issue.Comment comment : issue.getComments())
    {
%>
        <hr><table width="100%"><tr><td align="left"><b>
        <%=text(bean.writeDate(comment.getCreated()))%>
        </b></td><td align="right"><b>
        <%=h(comment.getCreatedByName(user))%>
        </b></td></tr></table>
        <%=text(comment.getComment())%>
        <%=text(bean.renderAttachments(context, comment))%>
<%
    }
%>
    <input type="hidden" name=".oldValues" value="<%=PageFlowUtil.encodeObject(bean.getPrevIssue())%>">
    <input type="hidden" name="action" value="<%=h(bean.getAction().getName())%>">
    <input type="hidden" name="issueId" value="<%=issue.getIssueId()%>">
</form>
<script type="text/javascript" for="window" event="onload">try {document.getElementById(<%=q(focusId)%>).focus();} catch (x) {}</script>
<script type="text/javascript">

var origComment = document.getElementById("comment").value;
var origNotify = <%=q(bean.getNotifyListString(false).toString())%>;

function isDirty()
{
    var comment = document.getElementById("comment");
    if (comment && origComment != comment.value)
        return true;
    var notify = document.getElementById("notifyList");
    if (notify && origNotify != notify.value)
        return true;
    return false;
}

window.onbeforeunload = LABKEY.beforeunload(isDirty);
</script>

<%!
    String getNotifyHelpPopup(int emailPrefs, int issueId, EntryTypeNames names)
    {
        String indefArticle = names.getIndefiniteSingularArticle();
        String description = h(indefArticle) + " " + h(names.singularName);

        StringBuilder sb = new StringBuilder();
        sb.append("Email notifications can be controlled via either this notification list (one email address per line) ");
        sb.append("or your user <a href=\"" + h(buildURL(IssuesController.EmailPrefsAction.class)));
        if (issueId != 0)
        {
            sb.append("issueId=").append(issueId);
        }
        sb.append("\">email preferences</a>. ");
        if (emailPrefs != 0)
        {
            sb.append("Your current preferences are to receive notification emails when:<br>");
            sb.append("<ul>");
            if ((emailPrefs & IssueManager.NOTIFY_ASSIGNEDTO_OPEN) != 0)
                sb.append("<li>" + description + " is opened and assigned to you</li>");
            if ((emailPrefs & IssueManager.NOTIFY_ASSIGNEDTO_UPDATE) != 0)
                sb.append("<li>" + description + " that's assigned to you is modified</li>");
            if ((emailPrefs & IssueManager.NOTIFY_CREATED_UPDATE) != 0)
                sb.append("<li>" + description + " you opened is modified</li>");
            if ((emailPrefs & IssueManager.NOTIFY_SUBSCRIBE) != 0)
                sb.append("<li>any " + h(names.singularName) + " is created or modified</li>");
            if ((emailPrefs & IssueManager.NOTIFY_SELF_SPAM) != 0)
                sb.append("<li>you create or modify " + description + "</li>");
            sb.append("</ul>");
        }
        return PageFlowUtil.helpPopup("Email Notifications", sb.toString(), true);
    }
%>

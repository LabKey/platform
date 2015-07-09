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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.data.DataRegion"%>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.issue.ColumnType" %>
<%@ page import="org.labkey.issue.model.IssuePage" %>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ page import="org.labkey.issue.model.Issue" %>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page import="org.labkey.issue.model.IssueManager.EntryTypeNames" %>
<%@ page import="org.springframework.validation.BindException" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("Ext4"));
        return resources;
    }
%>
<%
    final JspView<IssuePage> me = (JspView<IssuePage>) HttpView.currentView();
    final IssuePage bean = me.getModelBean();
    final Issue issue = bean.getIssue();
    final ViewContext context = getViewContext();
    final Container c = getContainer();
    final User user = getUser();
    final String focusId = bean.isInsert() ? "title" : "comment";
    final int emailPrefs = IssueManager.getUserEmailPreferences(c, user.getUserId());
    final String popup = getNotifyHelpPopup(emailPrefs, issue.getIssueId(), IssueManager.getEntryTypeNames(c));

    BindException errors = bean.getErrors();
    ActionURL completionUrl = new ActionURL(IssuesController.CompleteUserAction.class, c);
    ActionURL cancelURL;

    if (issue.getIssueId() > 0)
    {
        cancelURL = IssuesController.issueURL(c, IssuesController.DetailsAction.class).addParameter("issueId", issue.getIssueId());
    }
    else
    {
        cancelURL = IssuesController.issueURL(c, IssuesController.ListAction.class).addParameter(DataRegion.LAST_FILTER_PARAM, "true");
    }

    List<ColumnType> extraOptions = new ArrayList<>();
    for (ColumnType type : Arrays.asList(ColumnType.TYPE, ColumnType.AREA, ColumnType.PRIORITY, ColumnType.MILESTONE))
    {
        if (bean.hasKeywords(type))
        {
            extraOptions.add(type);
        }
    }

    //this is the rowspan used for the 2nd and 3rd columns
    int rowSpan = 2 + extraOptions.size();
%>

<script type="text/javascript">
    function filterRe(e, input, re)
    {
        if (e.isSpecialKey())
            return true;

        var cc = String.fromCharCode(e.getCharCode());
        if (!cc)
            return true;

        if (!re.test(cc))
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

    function filterNumber(e, input)
    {
        return filterRe(e, input, /[-\d]/);
    }

    function filterCommaSepNumber(e, input)
    {
        return filterRe(e, input, /^[\d,\s]+$/);
    }
</script>
<labkey:form method="POST" onsubmit="LABKEY.setSubmit(true); return true;" enctype="multipart/form-data" action="<%=IssuesController.issueURL(c, bean.getAction())%>">

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
            <td align="right" valign="top"><%= button("Save").submit(true).attributes("name=\"" + bean.getAction() + "\"").disableOnClick(true) %><%= button("Cancel").href(cancelURL) %></td>
        </tr>
        <tr>
<%
            if (bean.isInsert())
            {
%>
                <td class="labkey-form-label"><%=text(bean.getLabel("Title", true))%></td>
<%
            } else {
%>
                <td class="labkey-form-label">Issue <%=issue.getIssueId()%></td>
<%
            }
%>
                <td colspan="3">
                <%=text(bean.writeInput("title", issue.getTitle(), "id=title tabindex=\"1\" style=\"width:100%;\""))%>
                </td></tr>
            <tr>
                <td class="labkey-form-label"><%=text(bean.getLabel("Status", true))%></td><td><%=h(issue.getStatus())%></td>
                <td rowspan="<%=h(rowSpan)%>" valign="top">
                    <table>
                        <tr><td class="labkey-form-label"><%=text(bean.getLabel("Opened", true))%></td><td nowrap="true"><%=h(bean.writeDate(issue.getCreated()))%> by <%=h(issue.getCreatedByName(user))%></td></tr>
                        <tr><td class="labkey-form-label">Changed</td><td nowrap="true"><%=h(bean.writeDate(issue.getModified()))%> by <%=h(issue.getModifiedByName(user))%></td></tr>
                        <tr><td class="labkey-form-label"><%=text(bean.getLabel("Resolved", true))%></td><td nowrap="true"><%=h(bean.writeDate(issue.getResolved()))%><%=text(issue.getResolvedBy() != null ? " by " : "")%> <%=h(issue.getResolvedByName(user))%></td></tr>
                        <tr><td class="labkey-form-label"><%=text(bean.getLabel(ColumnType.RESOLUTION, true))%></td><td><%=text(bean.writeSelect(ColumnType.RESOLUTION, 2))%></td></tr>
        <% if (bean.isEditable("resolution") || !"open".equals(issue.getStatus())) { %>
                        <tr><td class="labkey-form-label">Duplicate</td><td>
                        <% if (bean.isEditable("duplicate")) {
                                if("Duplicate".equals(issue.getResolution()))
                                {
                                    //Enabled duplicate field.
                        %>
                                    <%=text(bean.writeInput("duplicate", issue.getDuplicate() == null ? null : String.valueOf(issue.getDuplicate()), "tabindex=\"2\""))%>
                        <%
                                }
                                else
                                {
                                    //Disabled duplicate field.
                        %>
                                    <%=text(bean.writeInput("duplicate", issue.getDuplicate() == null ? null : String.valueOf(issue.getDuplicate()), "tabindex=\"2\" disabled"))%>
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
                                Ext4.EventManager.on(duplicateInput, 'keypress', filterNumber);
                            </script>
                        <%
                            }
                            else
                            {
                                if(issue.getDuplicate() != null)
                                {
                        %>
                            <a href="<%=IssuesController.getDetailsURL(c, issue.getDuplicate(), false)%>"><%=issue.getDuplicate()%></a>
                        <%
                                }
                            }
                        %>
                        </td></tr>
        <% } %>
                        <tr><td class="labkey-form-label"><%=text(bean.getLabel(ColumnType.RELATED, false))%></td><td>
                        <%=text(bean.writeInput("related", issue.getRelated() == null ? null : issue.getRelated(), "id=related tabindex=\"2\""))%>

                        <script type="text/javascript">
                            Ext4.EventManager.on(document.getElementsByName('related')[0], 'keypress', filterCommaSepNumber);
                        </script>

                        <%=text(bean.writeCustomColumn(ColumnType.INT1, 2, true))%>

                        <script type="text/javascript">
                            var els = document.getElementsByName('int1');
                            if (els.length > 0)
                                Ext4.EventManager.on( els[0], 'keypress', filterNumber);
                        </script>

                        <%=text(bean.writeCustomColumn(ColumnType.INT2, 2, true))%>

                        <script type="text/javascript">
                            var els = document.getElementsByName('int2');
                            if (els.length > 0)
                                Ext4.EventManager.on( els[0], 'keypress', filterNumber);
                        </script>

                        <%=text(bean.writeCustomColumn(ColumnType.STRING1, 2, true))%>
                    </table>
                </td>
                <td valign="top" rowspan="<%=h(rowSpan)%>"><table style="width: 100%;">
                    <tr><td class="labkey-form-label">Closed</td><td><%=text(bean.writeDate(issue.getClosed()))%><%=text(issue.getClosedBy() != null ? " by " : "")%><%=h(issue.getClosedByName(user))%></td></tr>
    <%
                if (bean.isEditable("notifyList"))
                {
    %>
                    <tr>
                        <td class="labkey-form-label-nowrap"><%=text(bean.getLabel("NotifyList", true))%><%=text(popup)%><br/><br/>
    <%
                        if (bean.isInsert())
                        {
    %>
                            <%= textLink("email prefs", IssuesController.issueURL(c, IssuesController.EmailPrefsAction.class))%>
    <%
                        } else {
    %>
                            <%= textLink("email prefs", IssuesController.issueURL(c, IssuesController.EmailPrefsAction.class).addParameter("issueId", issue.getIssueId()))%>
    <%
                        }
    %>
                        </td>
                        <td>
                            <labkey:autoCompleteTextArea name="notifyList" id="notifyList" url="<%=h(completionUrl.getLocalURIString())%>" rows="4" tabindex="3" cols="40" value="<%=h(bean.getNotifyListString(false))%>"/>
                        </td>
                    </tr>
    <%
                } else {
    %>
                    <tr><td class="labkey-form-label">Notify</td><td><%=text(bean.getNotifyList())%></td></tr>
    <%
                }
    %>
                    <%=text(bean.writeCustomColumn(ColumnType.STRING2, 3, true))%>
                    <%=text(bean.writeCustomColumn(ColumnType.STRING3, 3, true))%>
                    <%=text(bean.writeCustomColumn(ColumnType.STRING4, 3, true))%>
                    <%=text(bean.writeCustomColumn(ColumnType.STRING5, 3, true))%>
                </table></td>
            </tr>
        <tr><td class="labkey-form-label"><%=text(bean.getLabel("AssignedTo", true))%></td><td><%=bean.writeSelect("assignedTo", String.valueOf(issue.getAssignedTo()), issue.getAssignedToName(user), bean.getUserOptions(), 1)%></td></tr>
        <%
            for (ColumnType type : extraOptions)
            {
                    %><tr><td class="labkey-form-label"><%=text(bean.getLabel(type, true))%></td><td><%=text(bean.writeSelect(type, 1))%></td></tr><%
            }
        %>
        <tr><td class="labkey-form-label"><%=bean.getLabel("Comment", bean.isInsert())%></td>
            <td colspan="3">
                <textarea id="comment" name="comment" cols="150" rows="20" style="width: 99%;" onchange="LABKEY.setDirty(true);return true;" tabindex="1"><%=h(bean.getBody())%></textarea>
            </td></tr>
        <tr>
            <td align="right" valign="top"><%= button("Save").submit(true).attributes("tabindex=\"5\" name=\"" + bean.getAction() + "\"").disableOnClick(true) %><%= PageFlowUtil.button("Cancel").href(cancelURL).attributes("tabIndex=\"5\"") %></td>
        </tr>
    </table>

    <table>
        <tr><td><table id="filePickerTable"></table></td></tr>
        <tr><td><a href="javascript:addFilePicker('filePickerTable','filePickerLink')" id="filePickerLink"><img src="<%=getWebappURL("_images/paperclip.gif")%>">Attach a file</a></td></tr>
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
        <%=h(bean.writeDate(comment.getCreated()))%>
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
</labkey:form>
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

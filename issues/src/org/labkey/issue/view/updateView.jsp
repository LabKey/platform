<%
    /*
     * Copyright (c) 2008-2026 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ColumnInfo"%>
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.data.DataRegion" %>
<%@ page import="org.labkey.api.exp.property.DomainProperty" %>
<%@ page import="org.labkey.api.security.SecurityUrls" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.HtmlStringBuilder" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ page import="org.labkey.issue.IssuesController.DetailsAction" %>
<%@ page import="org.labkey.issue.IssuesController.EmailPrefsAction" %>
<%@ page import="org.labkey.issue.IssuesController.ListAction" %>
<%@ page import="org.labkey.issue.model.IssueListDef" %>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page import="org.labkey.issue.model.IssueManager.EntryTypeNames" %>
<%@ page import="org.labkey.issue.model.IssueObject" %>
<%@ page import="org.labkey.issue.model.IssuePage" %>
<%@ page import="org.labkey.issue.view.IssuesListView" %>
<%@ page import="org.springframework.validation.BindException" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.stream.Stream" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
        dependencies.add("Ext4");
    }

    HtmlString getNotifyHelpPopup(int emailPrefs, int issueId, EntryTypeNames names)
    {
        String indefArticle = names.getIndefiniteSingularArticle();
        String description = h(indefArticle) + " " + h(names.singularName);

        StringBuilder sb = new StringBuilder();
        sb.append("Email notifications can be controlled via this notification list (one email address per line)");

        if (!getUser().isGuest())
        {
            ActionURL url = urlFor(EmailPrefsAction.class);
            if (issueId != 0)
            {
                url.addParameter("issueId", issueId);
            }
            sb.append(" or your user <a href=\"").append(h(url));
            sb.append("\">email preferences</a>. ");
        }
        if (emailPrefs != 0)
        {
            sb.append("Your current preferences are to receive notification emails when:<br>");
            sb.append("<ul>");
            if ((emailPrefs & IssueManager.NOTIFY_ASSIGNEDTO_OPEN) != 0)
                sb.append("<li>").append(description).append(" is opened and assigned to you</li>");
            if ((emailPrefs & IssueManager.NOTIFY_ASSIGNEDTO_UPDATE) != 0)
                sb.append("<li>").append(description).append(" that's assigned to you is modified</li>");
            if ((emailPrefs & IssueManager.NOTIFY_CREATED_UPDATE) != 0)
                sb.append("<li>").append(description).append(" you opened is modified</li>");
            if ((emailPrefs & IssueManager.NOTIFY_SUBSCRIBE) != 0)
                sb.append("<li>any ").append(h(names.singularName)).append(" is created or modified</li>");
            if ((emailPrefs & IssueManager.NOTIFY_SELF_SPAM) != 0)
                sb.append("<li>you create or modify ").append(description).append("</li>");
            sb.append("</ul>");
        }
        return helpPopup("Email Notifications", HtmlString.unsafe(sb.toString())).getHtmlString();
    }
%>
<%
    final JspView<IssuePage> me = HttpView.currentView();
    final IssuePage bean = me.getModelBean();
    final IssueObject issue = bean.getIssue();
    final ViewContext context = getViewContext();
    final Container c = getContainer();
    final User user = getUser();
    final String focusId = bean.isInsert() ? "title" : "comment";
    final int emailPrefs = IssueManager.getUserEmailPreferences(c, user.getUserId());
    IssueListDef issueListDef = IssueManager.getIssueListDef(getContainer(), issue.getIssueDefName());
    if (issueListDef == null)
        issueListDef = IssueManager.getIssueListDef(issue);

    BindException errors = bean.getErrors();
    ActionURL completionUrl = urlProvider(SecurityUrls.class).getCompleteUserReadURL(c);
    ActionURL cancelURL;

    if (bean.getReturnUrl() != null)
    {
        cancelURL = bean.getReturnUrl();
    }
    else if (issue.getIssueId() > 0)
    {
        cancelURL = IssuesController.issueURL(c, DetailsAction.class).addParameter("issueId", issue.getIssueId());
    }
    else
    {
        cancelURL = IssuesController.issueURL(c, ListAction.class).addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, issue.getIssueDefName()).addParameter(DataRegion.LAST_FILTER_PARAM, "true");
    }

    // create collections for additional custom columns and distribute them evenly in the form
    Map<String, DomainProperty> propertyMap = bean.getCustomColumnConfiguration().getPropertyMap();
    List<DomainProperty> column1Props = new ArrayList<>();
    List<DomainProperty> column2Props = new ArrayList<>();

    EntryTypeNames names = IssueManager.getEntryTypeNames(c, issueListDef.getName());

    final HtmlString popup = getNotifyHelpPopup(emailPrefs, issue.getIssueId(), names);

    // todo: don't include if the lookup is empty (was previously IssuePage.hasKeywords)
    List<DomainProperty> extraColumns = Stream.of("type", "area", "priority", "milestone")
        .filter(propertyMap::containsKey)
        .map(propertyMap::get)
        .toList();

    //this is the rowspan used for the 2nd and 3rd columns
    int rowSpan = 2 + extraColumns.size();

    int i=0;
    for (DomainProperty prop : bean.getCustomColumnConfiguration().getCustomProperties())
    {
        boolean showField = bean.isInsert() && prop.isShownInInsertView() || !bean.isInsert() && prop.isShownInUpdateView();
        if (showField)
        {
            if ((i++ % 2) == 0)
                column1Props.add(prop);
            else
                column2Props.add(prop);
        }
    }
%>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    (function($){

        onSubmit = function(){
            $("input[name='dirty']").val(isDirty());
            // Issue:53907, disable all submit buttons to prevent multiple submits
            $('a[data-submitid]').addClass('labkey-disabled-button');

            LABKEY.setSubmit(true);
        };

        isDirty = function (){
            let origNotify = <%=q(bean.getNotifyListString(false).toString())%>;
            let notify = document.getElementById("notifyList");
            if (notify && origNotify != notify.value)
                return true;
            return LABKEY.isDirty();
        };

        filterCommaSepNumber = function(e, input){
            return filterRe(e, input, /^[\d,\s]+$/);
        };

        filterRe = function(e, input, re){
            if (e.isSpecialKey())
                return true;

            var cc = String.fromCharCode(e.getCharCode());
            if (!cc)
                return true;

            if (!re.test(cc))
            {
                if (e.stopPropagation) {
                    e.stopPropagation();
                }
                else {
                    e.cancelBubble = true;
                }
                if (e.preventDefault) {
                    e.preventDefault();
                }
                else {
                    e.returnValue = false;
                }
                return false;
            }

            return true;
        };

        initialize = function(){
            let startColOneIdx = 11;
            let startColTwoIdx = 21;
            // map of field name (lowercase) to tab index
            let fieldMap = {
                'title': 1,
                'assignedto': 2,
                'type': 3,
                'area': 4,
                'priority': 5,
                'milestone': 6,
                'comment': 7,
                'resolution': 8,
                'duplicate': 9,
                'related': 10
            };

            // custom field mapping
            <%
            for (DomainProperty prop : column1Props)
            {%>
                fieldMap[<%=q(ColumnInfo.legalNameFromName(prop.getName()).toLowerCase())%>] = startColOneIdx++;<%
            }
            for (DomainProperty prop : column2Props)
            {%>
                fieldMap[<%=q(ColumnInfo.legalNameFromName(prop.getName()).toLowerCase())%>] = startColTwoIdx++;<%
            }
            %>

            registerEvents(fieldMap);
            LABKEY.setDirty(<%=bean.isDirty()%>)
        };

        // Add dirty tracking to form fields and set tab order
        registerEvents = function(fieldMap){

            $('.form-control').filter(function() {
                let name = $(this).attr('name');
                if (name) {
                    let nameLower = name.toLowerCase();
                    if (fieldMap.hasOwnProperty(nameLower)){
                        let inputSelect = '.form-control[name=' + name + ']';
                        $(inputSelect).attr("tabindex", fieldMap[nameLower]).on("change", function(){LABKEY.setDirty(true);});
                    }
                }
                return false;
            });
        };

        LABKEY.Utils.onReady(function(){
            initialize();
            window.onbeforeunload = LABKEY.beforeunload(isDirty);
        });

    })(jQuery);

</script>
<labkey:form method="POST" onsubmit="onSubmit();" enctype="multipart/form-data" layout="horizontal">
    <table><%
        if (null != errors && 0 != errors.getErrorCount())
        {
            for (ObjectError e : errors.getAllErrors())
            {%>
        <tr><td colspan=3><font class="labkey-error"><%=h(context.getMessage(e))%></font></td></tr><%
                }
            }
            if (!bean.getRequiredFields().isEmpty())
            {%>
        <tr><td>Fields marked with an asterisk * are required.</td></tr><%
            }
        %>
    </table>
    <div class="labkey-button-bar-separate">
        <%= button("Save").submit(true).name(bean.getAction().name()) %>
        <%= button("Cancel").href(cancelURL).onClick("LABKEY.setSubmit(true);")%>
    </div>
    <table class="lk-fields-table" style="margin-top:10px;">
        <tr><%
            if (bean.isInsert())
            {%>
            <%=bean.renderLabel(propertyMap.get("Title"), getViewContext())%><%
            }
            else
            {%>
            <%=bean.renderLabel(h(names.singularName + " " + issue.getIssueId()))%><%
            }%>
            <td colspan="3">
                <%=bean.writeInput("title", issue.getTitle(), builder->builder.id("title").addStyle("width:100%"))%>
            </td></tr>
        <tr>
            <%=bean.renderLabel(bean.getLabel("Status", false))%><td><%=h(issue.getStatus())%></td>
            <td rowspan="<%=rowSpan%>" valign="top">
                <table class="lk-fields-table">
                    <tr><%=bean.renderLabel(bean.getLabel("Opened", false))%><td nowrap="true"><%=h(bean.writeDate(issue.getCreated()))%> by <%=h(issue.getCreatedByName(user))%></td></tr>
                    <tr><%=bean.renderLabel(bean.getLabel("Changed", false))%><td nowrap="true"><%=h(bean.writeDate(issue.getModified()))%> by <%=h(issue.getModifiedByName(user))%></td></tr>
                    <tr><%=bean.renderLabel(bean.getLabel("Resolved", false))%><td nowrap="true"><%=h(bean.writeDate(issue.getResolved()))%><%=h(issue.getResolvedBy() != null ? " by " : "")%> <%=h(issue.getResolvedByName(user))%></td></tr>
                    <%=bean.renderColumn(propertyMap.get("resolution"), getViewContext(), bean.isVisible("resolution"), bean.isReadOnly("resolution"))%>
                    <%
                    if (bean.isVisible("duplicate"))
                    {%>
                        <tr><%=bean.renderLabel(bean.getLabel("Duplicate", false))%><td><%
                        if("Duplicate".equals(issue.getResolution()) && !bean.isReadOnly("duplicate"))
                        {
                            //Enabled duplicate field.%>
                            <%=bean.writeInput("duplicate", issue.getDuplicate() == null ? null : String.valueOf(issue.getDuplicate()), builder->builder.type("number").minValue("1"))%><%
                        }
                        else
                        {
                            //Disabled duplicate field.%>
                            <%=bean.writeInput("duplicate", issue.getDuplicate() == null ? null : String.valueOf(issue.getDuplicate()), builder->builder.disabled(true))%><%
                        }%>
                        <script type="text/javascript" nonce="<%=getScriptNonce()%>">
                            var duplicateInput = document.getElementsByName('duplicate')[0];
                            var duplicateOrig = duplicateInput.value;
                            var resolutionSelect = document.getElementsByName('Resolution')[0];
                            function updateDuplicateInput()
                            {
                                // The options don't have an explicit value set, so look for the display text instead of
                                // the value
                                if (resolutionSelect.selectedIndex >= 0 &&
                                        resolutionSelect.options[resolutionSelect.selectedIndex].text === 'Duplicate')
                                {
                                    duplicateInput.disabled = false;
                                }
                                else
                                {
                                    duplicateInput.disabled = true;
                                    duplicateInput.value = duplicateOrig;
                                }
                                LABKEY.setDirty(true);
                            }
                            if (resolutionSelect){
                                if (window.addEventListener)
                                    resolutionSelect.addEventListener('change', updateDuplicateInput, false);
                                else if (window.attachEvent)
                                    resolutionSelect.attachEvent('onchange', updateDuplicateInput);
                            }
                        </script>
                    </td></tr><%
                    }
                    else
                    {
                        if (issue.getDuplicate() != null)
                        {%>
                            <a href="<%=h(IssuesController.getDetailsURL(c, issue.getDuplicate(), false))%>"><%=issue.getDuplicate()%></a><%
                        }
                    }%>
                    <tr><%=bean.renderLabel(bean.getLabel("Related", false))%><td>
                        <%=bean.writeInput("related", issue.getRelated() == null ? null : issue.getRelated(), builder->builder.id("related"))%>

                        <script type="text/javascript" nonce="<%=getScriptNonce()%>">
                            Ext4.EventManager.on(document.getElementsByName('related')[0], 'keypress', filterCommaSepNumber);
                        </script><%

                    for (DomainProperty prop : column1Props)
                    {%>
                        <%=bean.renderColumn(prop, getViewContext(), true, false)%><%
                    }%>
                </table>
            </td>
            <td valign="top" rowspan="<%=rowSpan%>"><table class="lk-fields-table" style="width: 100%;">
                <tr><%=bean.renderLabel(bean.getLabel("Closed", false))%><td><%=h(bean.writeDate(issue.getClosed()))%><%=unsafe(issue.getClosedBy() != null ? " by " : "")%><%=h(issue.getClosedByName(user))%></td></tr><%
            if (bean.isVisible("notifyList"))
            {%>
                <tr>
                    <%
                        HtmlStringBuilder builder = HtmlStringBuilder.of(bean.getLabel("NotifyList", true))
                            .append(popup)
                            .unsafeAppend("<br/><br/>");

                        if (!user.isGuest())
                        {
                            if (bean.isInsert())
                                builder.append(link("email prefs", IssuesController.issueURL(c, EmailPrefsAction.class)));
                            else
                                builder.append(link("email prefs", IssuesController.issueURL(c, EmailPrefsAction.class).addParameter("issueId", issue.getIssueId())));
                        }
                    %>
                    <%=bean.renderLabel(builder.getHtmlString())%>
                    <td>
                        <labkey:autoCompleteTextArea name="notifyList" id="notifyList" url="<%=completionUrl%>" rows="4" tabindex="20" cols="40" value="<%=bean.getNotifyListString(false)%>"/>
                    </td>
                </tr><%
            }
            else
            {%>
                <tr><%=bean.renderLabel(bean.getLabel("Notify", false))%><td><%=bean.getNotifyList()%></td></tr><%
            }
            for (DomainProperty prop : column2Props)
            {%>
                <%=bean.renderColumn(prop, getViewContext(), true, false)%><%
            }%>
            </table></td>
        </tr>
        <%=bean.renderColumn(propertyMap.get("assignedTo"), getViewContext(), bean.isVisible("assignedTo"), bean.isReadOnly("assignedTo"))%>
        <%
            for (DomainProperty prop : extraColumns)
            {%>
                <%=bean.renderColumn(prop, getViewContext(), true, false)%><%
            }%>
        <tr>
            <%=bean.renderLabel(bean.getLabel("Comment", bean.isInsert()))%>
            <td colspan="3">
                <textarea id="comment" name="comment" class="form-control" cols="150" rows="20"><%=h(bean.getBody())%></textarea>
            </td>
        </tr>
    </table>
    <table style="margin-top:10px;">
        <tr><td><table id="filePickerTable"></table></td></tr>
        <% addHandler("filePickerLink", "click", "addFilePicker('filePickerTable','filePickerLink'); return false;"); %>
        <tr><td><a href="#" id="filePickerLink"><img src="<%=getWebappURL("_images/paperclip.gif")%>">Attach a file</a></td></tr>
    </table>
    <div class="labkey-button-bar-separate" style="margin-bottom:10px">
        <%= button("Save").submit(true).name(String.valueOf(bean.getAction())) %>
        <%= button("Cancel").href(cancelURL).onClick("LABKEY.setSubmit(true);")%>
    </div>
    <% final Collection<IssueObject.CommentObject> comments = issue.getCommentObjects();
        if (!comments.isEmpty()) { boolean firstComment = true; %>
    <labkey:panel>
<%
    for (IssueObject.CommentObject comment : comments) {
        if (firstComment) {
            firstComment = false;
        }
        else { %><hr><% } %>
    <table width="100%"><tr><td align="left"><b>
        <%=h(bean.writeDate(comment.getCreated()))%>
    </b></td><td align="right"><b>
        <%=h(comment.getCreatedByName(user))%>
    </b></td></tr>
    </table>
    <div style="word-break: break-word; overflow-wrap: break-word">
        <%=comment.getHtmlComment()%>
        <%=bean.renderAttachments(context, comment)%>
    </div><%
    }%>
    </labkey:panel>
    <% }

    if (bean.getCallbackURL() != null)
    {%>
        <input type="hidden" name="callbackURL" value="<%=h(bean.getCallbackURL())%>"/><%
    }

    if (bean.getReturnUrl() != null)
    {%>
        <%= generateReturnUrlFormField(bean.getReturnUrl()) %> <%
    }%>
    <input type="hidden" name="action" value="<%=h(bean.getAction().name())%>">
    <input type="hidden" name="dirty" value="false">
    <%
    // ensure that issueDefName is available in the form post
    if (context.getActionURL().getParameter("issueDefName") == null)
    {%>
        <input type="hidden" name="issueDefName" value="<%=h(issueListDef.getName())%>">
    <%
    }%>
</labkey:form>
<script type="text/javascript" nonce="<%=getScriptNonce()%>" for="window" event="onload">try {document.getElementById(<%=q(focusId)%>).focus();} catch (x) {}</script>

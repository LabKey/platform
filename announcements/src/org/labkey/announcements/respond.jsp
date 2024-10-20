<%
/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.announcements.AnnouncementsController"%>
<%@ page import="org.labkey.announcements.AnnouncementsController.AnnouncementForm" %>
<%@ page import="org.labkey.announcements.AnnouncementsController.BaseInsertView" %>
<%@ page import="org.labkey.announcements.AnnouncementsController.CompleteUserAction" %>
<%@ page import="org.labkey.announcements.model.ModeratorReview" %>
<%@ page import="org.labkey.api.announcements.DiscussionService" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.wiki.WikiRendererType" %>
<%@ page import="java.util.Arrays" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("announcements/discuss");
    }
%>
<%
    HttpView<BaseInsertView.InsertBean> me = (HttpView<BaseInsertView.InsertBean>) HttpView.currentView();
    BaseInsertView.InsertBean bean = me.getModelBean();

    DiscussionService.Settings settings = bean.settings;
    AnnouncementForm form = bean.form;

    Container c = getContainer();
    User user = getUser();

    ActionURL respondUrl = AnnouncementsController.getRespondURL(c);
    ActionURL completeUserUrl = urlFor(CompleteUserAction.class);

%><%=formatMissedErrors("form")%>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    function onSubmit(form){
        LABKEY.setSubmit(true);
        return LABKEY.discuss.validate(form);
    }

    window.onbeforeunload = LABKEY.beforeunload(LABKEY.isDirty());
</script>

<labkey:form method="POST" enctype="multipart/form-data" action="<%=respondUrl%>" onsubmit="return onSubmit(this);">
<labkey:input type="hidden" name="cancelUrl" value="<%=bean.cancelURL%>" />
<%=generateReturnUrlFormField(bean.cancelURL)%>
<labkey:input type="hidden" name="fromDiscussion" value="<%=bean.fromDiscussion%>" />
<div style="max-width: 1050px;">
<table style="width: 100%;" class="lk-fields-table">
<%

ModeratorReview mr = ModeratorReview.get(settings.getModeratorReview());
if (!mr.isApproved(c, user, false /* Not a new thread */))
{
    %><tr><td colspan="3"><span class="labkey-message">Note: This response will not be posted immediately; it will appear after the content has been reviewed.</span><br><br></td></tr><%
}

if (settings.isTitleEditable())
{
    %><tr><td class="labkey-form-label">Title * <%=helpPopup("Title", "This field is required.") %></td><td colspan="2"><labkey:input type="text" size="60" maxLength="255" name="title" value='<%=form.get("title")%>' onChange="LABKEY.setDirty(true);"/></td></tr><%
}
else
{
    %><tr><td colspan="2"><labkey:input type="hidden" name="title" value='<%=form.get("title")%>'/></td></tr><%
}

if (settings.hasStatus())
{
    addHandler("status", "change", "LABKEY.setDirty(true);");
    %><tr><td class="labkey-form-label">Status</td><td colspan="2"><%=bean.statusSelect%></td></tr><%
}

if (settings.hasAssignedTo())
{
    addHandler("assignedTo", "change", "LABKEY.setDirty(true);");
    %><tr><td class="labkey-form-label">Assigned&nbsp;To</td><td colspan="2"><%=bean.assignedToSelect%></td></tr><%
}

if (settings.hasMemberList())
{
    addHandler("memberListInput", "change", "LABKEY.setDirty(true);");
    %><tr><td class="labkey-form-label">Notify</td><td><labkey:autoCompleteTextArea name="memberListInput" id="memberListInput" rows="5" cols="40" url="<%=completeUserUrl%>" value="<%=bean.memberList%>"/></td><td><i><%
    if (settings.isSecureWithoutEmailOn())
    {
        %> This <%=h(settings.getConversationName().toLowerCase())%> is private; only editors and the users on this list can view it. These users will also<%
    }
    else
    {
        %> The users on the notify list<%
    }
    %> receive email notifications of new posts to this <%=h(settings.getConversationName().toLowerCase())%>.<br><br>Enter one or more email addresses, each on its own line.</i></td></tr><%
}

if (settings.hasExpires())
{
    %><tr><td class="labkey-form-label">Expires</td><td><labkey:input type="text" size="23" name="expires" value='<%=form.get("expires")%>' onChange="LABKEY.setDirty(true);"/></td><td><i>Expired messages are not deleted, they are just no longer shown on the Portal page.</i></td></tr><%
}

%>
    <tr>
        <td class='labkey-form-label'>Body</td>
        <td colspan='2' style="width: 100%;">
            <ul class="nav nav-tabs" id="messageTabs" role="tablist">
                <li class="nav-item active">
                    <a class="nav-link" id="source-tab" data-toggle="tab" href="#source" role="tab" aria-controls="source" aria-selected="true">Source</a>
                </li>
                <li class="nav-item">
                    <a class="nav-link" id="preview-tab" data-toggle="tab" href="#preview" role="tab" aria-controls="preview" aria-selected="false">Preview</a>
                </li>
            </ul>
            <div class="tab-content" id="messageTabsContent">
                <div class="tab-pane active" id="source" role="tabpanel" aria-labelledby="source-tab">
                    <% addHandler("body", "change", "LABKEY.setDirty(true);"); %>
                    <textarea cols='120' rows='15' id="body" name='body' style="width: 100%;"><%=h(form.get("body"))%></textarea>
                </div>
                <div class="tab-pane message-preview form-control" id="preview" role="tabpanel" aria-labelledby="preview-tab">
                </div>
            </div>
            <labkey:input type="hidden" name="parentId" value="<%=bean.parentAnnouncementModel.getEntityId()%>"/>
        </td>
    </tr><%
    
if (settings.hasFormatPicker())
{
%><tr>
    <td class="labkey-form-label">Render As</td>
    <td colspan="2">
        <%=bean.renderAsSelect%>
     </td>
</tr>
<%}%>
    <tr>
        <td class="labkey-form-label">Attachments</td>
        <td colspan="2">
            <table id="filePickerTable"></table>
            <table>
                <tbody>
                <% addHandler("filePickerLink", "click", "addFilePicker('filePickerTable','filePickerLink'); return false;"); %>
                <tr><td><a href="#" id="filePickerLink"><img src="<%=getWebappURL("_images/paperclip.gif")%>">&nbsp;Attach a file</a></td></tr>
                </tbody>
            </table>
        </td>
    </tr><%
        if (null != bean.emailUsers)
        {
    %>
    <tr><td colspan="3" style="padding-top: 8px;color:green;">An email notification of this post will be sent to <%=bean.emailUsers%> people.</td></tr><%
        }
    %>
</table></div>
<br>&nbsp;<%= button("Submit").submit(true).id("submitButton").disableOnClick(true) %>&nbsp;<%
if (null != bean.cancelURL)
{
    %><%= button("Cancel").href(bean.cancelURL).onClick("LABKEY.setSubmit(true);") %><%
}
else
{
    %><%= generateBackButton("Cancel") %>
    <%
}
%>
</labkey:form>
<br>
<%
    for (WikiRendererType renderer : WikiRendererType.values()) {
%>
<div class="help-<%=renderer%>" style="display:none">
    <% me.include(renderer.getSyntaxHelpView(), out); %>
</div>
<%
    }
%>
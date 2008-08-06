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
<%@ page import="org.labkey.announcements.AnnouncementsController"%>
<%@ page import="org.labkey.announcements.AnnouncementsController.BaseInsertView.InsertBean"%>
<%@ page import="org.labkey.announcements.model.AnnouncementManager" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.wiki.WikiRendererType" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<InsertBean> me = (HttpView<InsertBean>) HttpView.currentView();
    InsertBean bean = me.getModelBean();

    Container c = HttpView.currentContext().getContainer();
    AnnouncementManager.Settings settings = bean.settings;
    AnnouncementsController.AnnouncementForm form = bean.form;
    ActionURL cancelUrl = bean.cancelURL;
    String insertUrl = AnnouncementsController.getInsertURL(c).getEncodedLocalURIString();
%>
<%=formatMissedErrors("form")%>
<script type="text/javascript">
function validateForm(form)
{
    var trimmedTitle = form.title.value.trim();

    if (trimmedTitle.length > 0)
        return true;

    alert("Title must not be blank");
    return false;
}
</script>
<form method=post enctype="multipart/form-data" action="<%=insertUrl%>" onSubmit="return validateForm(this)">
<input type=hidden name=cancelUrl value="<%=h(null != cancelUrl ? cancelUrl.getLocalURIString() : null)%>">
<input type=hidden name=fromDiscussion value="<%=bean.fromDiscussion%>">
<input type=hidden name=allowMultipleDiscussions value="<%=bean.allowMultipleDiscussions%>">
<table>
  <tr><td class='labkey-form-label'>Title</td><td colspan="2"><input type='text' size='60' id="title" name='title' value="<%=h(form.get("title"))%>"></td></tr><%
    if (settings.hasStatus())
    {
        %><tr><td class='labkey-form-label'>Status</td><td colspan="2"><%=bean.statusSelect%></td></tr><%
    }
    if (settings.hasAssignedTo())
    {
        %><tr><td class='labkey-form-label'>Assigned&nbsp;To</td><td colspan="2"><%=bean.assignedToSelect%></td></tr><%
    }
    if (settings.hasMemberList())
    {
        %><tr><td class='labkey-form-label'>Members</td><td><%=bean.memberList%></td><td width="100%"><i><%
        if (settings.isSecure())
        {
            %> This <%=settings.getConversationName().toLowerCase()%> is private; only editors and the users on this list can view it.  These users will also<%
        }
        else
        {
            %> The users on the member list<%
        }
        %> receive email notifications of new posts to this <%=settings.getConversationName().toLowerCase()%>.<br><br>Enter one or more email addresses, each on its own line.</i></td></tr><%
    }
    if (settings.hasExpires())
    {
        %><tr><td class='labkey-form-label'>Expires</td><td><input type='text' size='23' name='expires' value='<%=h(form.get("expires"))%>' ></td><td width="100%"><i>By default the Expires field is set to one month from today. <br>Expired messages are not deleted, they are just no longer shown on the Portal page.</i></td></tr><%
    }
    %><tr><td class='labkey-form-label'>Body</td><td colspan='2' style="width: 100%;"><textarea cols='120' rows='15' id="body" name='body' style="width: 100%;"><%=h(form.get("body"))%></textarea></td></tr><%
    if (settings.hasFormatPicker())
    {
        %><tr><td class="labkey-form-label">Render As</td><td colspan="2">
        <select name="rendererType"><%
            for (WikiRendererType type : bean.renderers)
            {
                String value = type.name();
                String displayName = type.getDisplayName();
                String selected = type == bean.currentRendererType ? "selected " : "";
        %><option <%=selected%>value="<%=h(value)%>"><%=h(displayName)%></option><%
            }
        %></select></td></tr><%
    }
  %><tr><td colspan=3 align=left></td></tr>                   
</table>
<%
if (bean.allowBroadcast)
{
%><table width=100%>
  <tr class="labkey-wp-header">
    <td title="Admin Broadcast" colspan="2" nowrap>
      <div class="labkey-wp-title"><span>Admin Broadcast</span></div>
    </td>
  </tr>
  <tr>
    <td width="2%"><input type="checkbox" name="broadcast"></td>
    <td>Send this message as email to all site users (site admins only)</td>
  </tr>
</table><%
}%>
<table width=100%>
	<tr class="labkey-wp-header">
		<td title="Attachments" width="100%" nowrap>
		<div class="labkey-wp-title"><span>Attachments</span></div>
		</td>
	</tr>
	<tr><td>
        <table id="filePickerTable">
        </table>
	</td>
	</tr>
    <tr><td>
      <table>
        <tr><td colspan=2><a href="javascript:addFilePicker('filePickerTable','filePickerLink')" id="filePickerLink"><img src="<%=request.getContextPath()%>/_images/paperclip.gif">Attach a file</a></td></tr>
      </table>
    </td></tr>
</table>
<br>&nbsp;<input type=image src="<%=PageFlowUtil.submitSrc()%>" value="Insert">&nbsp;<%
if (null != cancelUrl)
{
    %><%=PageFlowUtil.buttonLink("Cancel", cancelUrl)%><%
}
else
{
    %><input type=image src="<%=PageFlowUtil.buttonSrc("Cancel")%>" value="Cancel" onclick="javascript:window.history.back(); return false;"><%
}
%>
<input type=hidden name="discussionSrcIdentifier" value="<%=h(form.get("discussionSrcIdentifier"))%>"><input type=hidden name="discussionSrcURL" value="<%=h(form.get("discussionSrcURL"))%>">
</form>
<p/>
<% me.include(bean.currentRendererType.getSyntaxHelpView(), out); %>

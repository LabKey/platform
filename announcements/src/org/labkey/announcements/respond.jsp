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
<%@ page import="org.labkey.announcements.AnnouncementsController.*" %>
<%@ page import="org.labkey.announcements.model.AnnouncementManager" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.wiki.WikiRendererType" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<BaseInsertView.InsertBean> me = (HttpView<BaseInsertView.InsertBean>) HttpView.currentView();
    BaseInsertView.InsertBean bean = me.getModelBean();

    AnnouncementManager.Settings settings = bean.settings;
    AnnouncementForm form = bean.form;

    Container c = me.getViewContext().getContainer();

    String respondUrl = AnnouncementsController.getRespondURL(c).getEncodedLocalURIString();
%><%=formatMissedErrors("form")%>
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
<form method=post enctype="multipart/form-data" action="<%=respondUrl%>" onSubmit="return validateForm(this)">
<input type="hidden" name="cancelUrl" value="<%=h(bean.cancelURL)%>">
<input type="hidden" name="fromDiscussion" value="<%=bean.fromDiscussion%>">
<table><%

if (settings.isTitleEditable())
{
    %><tr><td class="labkey-form-label">Title</td><td colspan="2"><input type="text" size="60" name="title" value="<%=h(form.get("title"))%>"></td></tr><%
}
else
{
    %><tr><td colspan="2"><input type="hidden" name="title" value="<%=h(form.get("title"))%>"></td></tr><%
}

if (settings.hasStatus())
{
    %><tr><td class="labkey-form-label">Status</td><td colspan="2"><%=bean.statusSelect%></td></tr><%
}

if (settings.hasAssignedTo())
{
    %><tr><td class="labkey-form-label">Assigned&nbsp;To</td><td colspan="2"><%=bean.assignedToSelect%></td></tr><%
}

if (settings.hasMemberList())
{
    %><tr><td class="labkey-form-label">Members</td><td><%=bean.memberList%></td><td style="width:100%;"><i><%
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
    %><tr><td class="labkey-form-label">Expires</td><td><input type="text" size="23" name="expires" value="<%=h(form.get("expires"))%>" ></td><td style="width:100%;"><i>Expired messages are not deleted, they are just no longer shown on the Portal page.</i></td></tr><%
}

%>
    <tr>
    <td class="labkey-form-label">Body</td><td colspan=2 style="width:100%;"><textarea cols="60" rows="15" id="body" name="body" style="width:100%;"><%=h(form.get("body"))%></textarea>
        <input type="hidden" name="parentId" value="<%=bean.parentAnnouncement.getEntityId()%>"/>
    </td>
</tr><%
    
if (settings.hasFormatPicker())
{
%><tr>
    <td class="labkey-form-label">Render As</td>
    <td colspan="2">
        <select name="rendererType">
              <%
                  for (WikiRendererType type : bean.renderers)
                  {
                      String value = type.name();
                      String displayName = type.getDisplayName();
                      String selected = type == bean.currentRendererType ? "selected " : "";
                  %>
                      <option <%=selected%> value="<%=h(value)%>"><%=h(displayName)%></option>
                  <%
              }%>
        </select>
    </td>
</tr>
<%}%>

<tr><td colspan="3"></td></tr>
</table>
<table class="labkey-form" width=100%>
	<tr class="labkey-wp-header">
		<td title="Attachments" style="width:100%;" nowrap>
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
        <tr><td colspan=2><a href="javascript:addFilePicker('filePickerTable', 'filePickerLink')" id="filePickerLink"><img src="<%=request.getContextPath()%>/_images/paperclip.gif">Attach a file</a></td></tr>
      </table>
    </td></tr>
</table>
<br>&nbsp;<input type=image src="<%=PageFlowUtil.submitSrc()%>" value="Insert">&nbsp;<a href="<%=h(bean.cancelURL)%>"><img src="<%=PageFlowUtil.buttonSrc("Cancel")%>"></a>
</form>
<br>
<% me.include(bean.currentRendererType.getSyntaxHelpView(), out); %>

<%
/*
 * Copyright (c) 2007-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.announcements.DiscussionService" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.wiki.WikiRendererType" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("announcements/discuss.css");
        dependencies.add("announcements/discuss.js");
    }
%>
<%
    HttpView<BaseInsertView.InsertBean> me = (HttpView<BaseInsertView.InsertBean>) HttpView.currentView();
    BaseInsertView.InsertBean bean = me.getModelBean();

    DiscussionService.Settings settings = bean.settings;
    AnnouncementForm form = bean.form;

    Container c = getContainer();

    String respondUrl = AnnouncementsController.getRespondURL(c).getEncodedLocalURIString();
    ActionURL completeUserUrl = new ActionURL(AnnouncementsController.CompleteUserAction.class, getContainer());

%><%=formatMissedErrors("form")%>
<labkey:form method="POST" enctype="multipart/form-data" action="<%=respondUrl%>" onsubmit="return LABKEY.discuss.validate(this)">
<input type="hidden" name="cancelUrl" value="<%=h(bean.cancelURL)%>">
<%=generateReturnUrlFormField(bean.cancelURL)%>
<input type="hidden" name="fromDiscussion" value="<%=bean.fromDiscussion%>">
<div style="max-width: 1050px;"><table style="width: 100%;" class="lk-fields-table"><%

if (settings.isTitleEditable())
{
    %><tr><td class="labkey-form-label">Title * <%= PageFlowUtil.helpPopup("Title", "This field is required.") %></td><td colspan="2"><input type="text" size="60" maxlength="255" name="title" value="<%=h(form.get("title"))%>"></td></tr><%
}
else
{
    %><tr><td colspan="2"><input type="hidden" name="title" value="<%=h(form.get("title"))%>"></td></tr><%
}

if (settings.hasStatus())
{
    %><tr><td class="labkey-form-label">Status</td><td colspan="2"><%=text(bean.statusSelect)%></td></tr><%
}

if (settings.hasAssignedTo())
{
    %><tr><td class="labkey-form-label">Assigned&nbsp;To</td><td colspan="2"><%=text(bean.assignedToSelect)%></td></tr><%
}

if (settings.hasMemberList())
{
    %><tr><td class="labkey-form-label">Members</td><td><labkey:autoCompleteTextArea name="memberListInput" id="memberListInput" rows="5" cols="40" url="<%=h(completeUserUrl)%>" value="<%=h(bean.memberList)%>"/></td><td width="100%"><i><%
    if (settings.isSecure())
    {
        %> This <%=h(settings.getConversationName().toLowerCase())%> is private; only editors and the users on this list can view it.  These users will also<%
    }
    else
    {
        %> The users on the member list<%
    }
    %> receive email notifications of new posts to this <%=h(settings.getConversationName().toLowerCase())%>.<br><br>Enter one or more email addresses, each on its own line.</i></td></tr><%
}

if (settings.hasExpires())
{
    %><tr><td class="labkey-form-label">Expires</td><td><input type="text" size="23" name="expires" value="<%=h(form.get("expires"))%>" ></td><td width="100%"><i>Expired messages are not deleted, they are just no longer shown on the Portal page.</i></td></tr><%
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
                    <textarea cols='120' rows='15' id="body" name='body' style="width: 100%;"><%=h(form.get("body"))%></textarea>
                </div>
                <div class="tab-pane message-preview form-control" id="preview" role="tabpanel" aria-labelledby="preview-tab">
                </div>
            </div>
            <input type="hidden" name="parentId" value="<%=h(bean.parentAnnouncementModel.getEntityId())%>"/>
        </td>
    </tr><%
    
if (settings.hasFormatPicker())
{
%><tr>
    <td class="labkey-form-label">Render As</td>
    <td colspan="2">
        <select name="rendererType" id="rendererType">
              <%
                  for (WikiRendererType type : bean.renderers)
                  {
                      String value = type.name();
                      String displayName = type.getDisplayName();
                  %>
                      <option<%=selected(type == bean.currentRendererType)%> value="<%=h(value)%>"><%=h(displayName)%></option>
                  <%
              }%>
        </select>
    </td>
</tr>
<%}%>
    <tr>
        <td class="labkey-form-label">Attachments</td>
        <td colspan="2">
            <table id="filePickerTable"></table>
            <table>
                <tbody>
                <tr><td><a href="javascript:addFilePicker('filePickerTable','filePickerLink')" id="filePickerLink"><img src="<%=getWebappURL("_images/paperclip.gif")%>">&nbsp;Attach a file</a></td></tr>
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
    %><%= button("Cancel").href(bean.cancelURL) %><%
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
<div class="help-<%=renderer.name()%>" style="display:none">
    <% me.include(renderer.getSyntaxHelpView(), out); %>
</div>
<%
    }
%>
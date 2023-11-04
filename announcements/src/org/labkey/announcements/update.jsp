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
<%@ page import="org.labkey.announcements.AnnouncementsController"%>
<%@ page import="org.labkey.announcements.AnnouncementsController.AnnouncementUpdateView"%>
<%@ page import="org.labkey.announcements.AnnouncementsController.AnnouncementUpdateView.UpdateBean" %>
<%@ page import="org.labkey.announcements.model.AnnouncementModel" %>
<%@ page import="org.labkey.api.announcements.DiscussionService" %>
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.wiki.WikiRendererType" %>
<%@ page import="java.util.Arrays" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("announcements/discuss");
    }
%>
<%
    AnnouncementUpdateView me = (AnnouncementUpdateView) HttpView.currentView();
    UpdateBean bean = me.getModelBean();

    AnnouncementModel ann = bean.annModel;
    DiscussionService.Settings settings = bean.settings;
    ActionURL baseUrl = getViewContext().cloneActionURL().deleteParameters();
    ActionURL completeUserUrl = new ActionURL(AnnouncementsController.CompleteUserAction.class, getContainer());
%>
<%=formatMissedErrors("form")%>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    function onSubmit(form){
        LABKEY.setSubmit(true);
        return LABKEY.discuss.validate(form);
    }

    window.onbeforeunload = LABKEY.beforeunload(LABKEY.isDirty());
</script>

<labkey:form method="post" action='<%=baseUrl.setAction(AnnouncementsController.UpdateAction.class)%>' enctype="multipart/form-data" onsubmit="return onSubmit(this);">
<labkey:input type="hidden" name="rowId" value="<%=ann.getRowId()%>"/>
<labkey:input type="hidden" name="entityId" value="<%=ann.getEntityId()%>"/>
<%=generateReturnUrlFormField(bean.returnURL)%>
<table><%

if (settings.isTitleEditable())
{
    %><tr><td class='labkey-form-label'>Title * <%= helpPopup("Title", "This field is required.") %></td><td colspan="2"><labkey:input name="title" size="60" maxLength="255" value="<%=ann.getTitle()%>" onChange="LABKEY.setDirty(true);"/></td></tr><%
}

if (settings.hasStatus())
{
    addHandler("status", "change", "LABKEY.setDirty(true);");
    %><tr><td class='labkey-form-label'>Status</td><td colspan="2"><%=bean.statusSelect%></td></tr><%
}

if (settings.hasAssignedTo())
{
    addHandler("assignedTo", "change", "LABKEY.setDirty(true);");
    %><tr><td class='labkey-form-label'>Assigned&nbsp;To</td><td colspan="2"><%=bean.assignedToSelect%></td></tr><%
}

if (settings.hasMemberList())
{
    addHandler("memberListInput", "change", "LABKEY.setDirty(true);");
    %><tr>
        <td class="labkey-form-label">Notify</td>
        <td><labkey:autoCompleteTextArea name="memberListInput" id="memberListInput" rows="5" cols="30" url="<%=completeUserUrl%>" value="<%=bean.memberList%>"/></td>
        <td><i><%
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
    %><tr><td class="labkey-form-label">Expires</td><td><labkey:input id="expired" name="expires" size="23" value="<%=DateUtil.formatDate(getContainer(), ann.getExpires())%>" onChange="LABKEY.setDirty(true);"/></td><td><i>Expired messages are not deleted, they are just no longer shown on the Portal page.</i></td></tr><%
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
                    <textarea cols='120' rows='15' id="body" name='body' style="width: 100%;"><%=h(ann.getBody()) %></textarea>
                </div>
                <div class="labkey-wiki tab-pane message-preview form-control" id="preview" role="tabpanel" aria-labelledby="preview-tab">
                </div>
            </div>
        </td>
    </tr>
<%
    if (settings.hasFormatPicker())
    { %>
  <tr>
    <td class="labkey-form-label">Render As</td>
    <td colspan="2">
        <%=select()
            .name("rendererType")
            .id("rendererType")
            .addOptions(Arrays.stream(bean.renderers).map(WikiRendererType::getDisplayName))
            .selected(bean.currentRendererType.getDisplayName())
            .onChange("LABKEY.setDirty(true);")
            .className(null)
        %>
    </td>
  </tr>
<%  } %>
  <tr>
    <td class='labkey-form-label'>Attachments</td>
    <td colspan="2">
        <table id="filePickerTable">
            <tbody>
                <%
                    int x = -1;
                    String id;
                    for (Attachment att : ann.getAttachments())
                {
                    x++;
                    id = makeId("remove_");
                    %><tr id="attach-<%=x%>">
                        <td><img src="<%=getWebappURL(att.getFileIcon())%>" alt="logo"/>&nbsp;<%= h(att.getName()) %></td>
                        <td><%= link("remove").onClick("LABKEY.discuss.removeAttachment(" + q(ann.getEntityId()) + "," + q(att.getName()) + "," +  q("attach-"+x) + ");") %></td>
                    </tr><%
                }
                %>
            </tbody>
        </table>
        <table>
            <tbody>
                <tr><td><a href="#" id="filePickerLink"><img src="<%=getWebappURL("_images/paperclip.gif")%>">&nbsp;Attach a file</a></td></tr>
                <% addHandler("filePickerLink", "click", "addFilePicker('filePickerTable','filePickerLink'); return false;"); %>
            </tbody>
        </table>
	</td>
  </tr>
  <tr>
    <td colspan=3 align=left>
      <table>
        <tr>
          <td><%= button("Submit").submit(true).id("submitButton").disableOnClick(true) %>
             &nbsp;<%=generateBackButton("Cancel")%></td>
        </tr>
      </table>
    </td>
  </tr>
</table>
</labkey:form>
<p/>
<%
    for (WikiRendererType renderer : WikiRendererType.values()) {
%>
<div class="help-<%=renderer%>" style="display:none">
    <% me.include(renderer.getSyntaxHelpView(), out); %>
</div>
<%
    }
%>

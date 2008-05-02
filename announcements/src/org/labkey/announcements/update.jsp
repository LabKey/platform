<%@ page import="org.labkey.announcements.AnnouncementsController.AnnouncementUpdateView"%>
<%@ page import="org.labkey.announcements.AnnouncementsController.AnnouncementUpdateView.UpdateBean"%>
<%@ page import="org.labkey.announcements.model.Announcement" %>
<%@ page import="org.labkey.announcements.model.AnnouncementManager" %>
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.wiki.WikiRendererType" %>
<%@ page import="org.labkey.api.action.ReturnUrlForm" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    AnnouncementUpdateView me = (AnnouncementUpdateView) HttpView.currentView();
    UpdateBean bean = me.getModelBean();

    Announcement ann = bean.ann;
    AnnouncementManager.Settings settings = bean.settings;
%>
<%=formatMissedErrors("form")%>
<form method="post" action="update.post">
<input type="hidden" name="rowId" value="<%=ann.getRowId()%>">
<input type="hidden" name="entityId" value="<%=ann.getEntityId()%>">
<input type="hidden" name=".oldValues" value="<%=PageFlowUtil.encodeObject(ann)%>">
<input type="hidden" name="<%=ReturnUrlForm.Params.returnUrl%>" value="<%=h(bean.returnURL)%>">
<table><%

if (settings.isTitleEditable())
{
    %><tr><td class='ms-searchform'>Title</td><td class='ms-vb' colspan="2"><input name="title" size="60" value="<%=h(ann.getTitle())%>"></td></tr><%
}

if (settings.hasStatus())
{
    %><tr><td class='ms-searchform'>Status</td><td class='ms-vb' colspan="2"><%=bean.statusSelect%></td></tr><%
}

if (settings.hasAssignedTo())
{
    %><tr><td class='ms-searchform'>Assigned&nbsp;To</td><td class='ms-vb' colspan="2"><%=bean.assignedToSelect%></td></tr><%
}

if (settings.hasMemberList())
{
    %><tr><td class="ms-searchform">Members</td><td class="normal"><%=bean.memberList%></td><td style="width:100%;"><i><%
    if (settings.isSecure())
    {
        %> This <%=settings.getConversationName().toLowerCase()%> is private; only editors and the users on this list can view it.  These users will also<%
    }
    else
    {
        %> The users on the member list<%
    }
    %> receive email notifications of new posts to this <%=h(settings.getConversationName().toLowerCase())%>.<br><br>Enter one or more email addresses, each on its own line.</i></td></tr><%
}

if (settings.hasExpires())
{
    %><tr><td class="ms-searchform">Expires</td><td class="normal"><input name="expires" size="23" value="<%=h(DateUtil.formatDate(ann.getExpires()))%>"></td><td style="width:100%;"><i>Expired messages are not deleted, they are just no longer shown on the Portal page.</i></td></tr><%
}

%>
  <tr>
    <td class='ms-searchform'>Body</td>
    <td class='ms-vb' style="width:100%;" colspan="2">
        <textarea cols="120" rows ="15" name='body' style="width:100%;"><%=h(ann.getBody())%></textarea>
    </td>
  </tr>
<%
    if (settings.hasFormatPicker())
    { %>
  <tr>
    <td class="ms-searchform">Render As</td>
    <td class="normal" colspan="2">
      <select name="rendererType"><%
          for (WikiRendererType type : bean.renderers)
          {
              String value = type.name();
              String displayName = type.getDisplayName();
              String selected = type == bean.currentRendererType ? "selected " : "";
      %>
        <option <%=selected%>value="<%=h(value)%>"><%=h(displayName)%></option><%
        } %>
      </select>
    </td>
  </tr>
<%  } %>
  <tr>
    <td class='ms-searchform'>Attachments</td>
    <td class="normal" colspan="2">
<%
    for (Attachment attach : ann.getAttachments())
    {
        out.print(h(attach.getName()));
        bean.deleteURL.setFileName(attach.getName());
        out.print(" [<a href=\"#deleteAttachment\" onClick=\"window.open('");
        out.print(h(bean.deleteURL));
        out.print("', null, 'height=200,width=450', false);\" style=\"color:green;\">");
        out.print("Delete");
        out.print("</a>]");
        out.print("<br>\n");
    }
%>
    [<a href="#addAttachment" onclick="window.open('<%=h(bean.addAttachmentURL)%>',null,'height=200,width=550', false);" style="color:green;">add attachment</a>]
	</td>
  </tr>
  <tr>
    <td colspan=3 align=left>
      <table border=0 cellspacing=2 cellpadding=0>
        <tr>
          <td><input type='image' src='<%=PageFlowUtil.buttonSrc("Submit")%>' name='update.post' value='Submit' onClick='this.form.action="update.post";this.form.method="post";' >&nbsp;<%=PageFlowUtil.buttonLink("Cancel", bean.returnURL)%></td>
        </tr>
      </table>
    </td>
  </tr>
</table>
</form>
<p/>
<% me.include(bean.currentRendererType.getSyntaxHelpView(), out); %>

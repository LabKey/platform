<%@ page import="org.labkey.announcements.AnnouncementsController.AnnouncementWebPart" %>
<%@ page import="org.labkey.announcements.AnnouncementsController.AnnouncementWebPart.MessagesBean" %>
<%@ page import="org.labkey.announcements.model.Announcement" %>
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    AnnouncementWebPart me = (AnnouncementWebPart) HttpView.currentView();
    MessagesBean bean = me.getModelBean();
    Container c = me.getViewContext().getContainer();
%>
<!--ANNOUNCEMENTS-->
<table width="100%" cellpadding=0>
    <tr>
        <td class="normal" style="padding-top:2px; width:40%;" align="left"><%
            if (null != bean.insertURL)
            {
        %>[<a href="<%=bean.insertURL%>">new <%=bean.settings.getConversationName().toLowerCase()%></a>]&nbsp;<%
            }
            if (null != bean.listURL)
            {
        %>[<a href="<%=bean.listURL%>">view list</a>]<%
    }
%></td>
        <td class="normal" style="padding-top:2px; width:20%;" align="center"><%=bean.filterText%></td>
        <td class="normal" style="padding-top:2px; width:40%;" align="right"><%
            if (null != bean.emailPrefsURL)
            {
        %>[<a href="<%=bean.emailPrefsURL%>">email&nbsp;preferences</a>]<%
            }
            if (null != bean.emailManageURL)
            {
        %>&nbsp;[<a href="<%=bean.emailManageURL%>">email&nbsp;admin</a>]<%
            }
            if (null != bean.customizeURL)
            {
        %>&nbsp;[<a href="<%=bean.customizeURL%>">customize</a>]<%
    }
%></td>
    </tr><%
    if (0 == bean.announcements.length)
    {%>
    <tr><td colspan=3 class=normal style="padding-top:4px;">No <%=bean.filterText.replace("all ", "")%></td></tr><%
    }
    for (Announcement a : bean.announcements)
    { %>
    <tr>
        <td class="normal" style="padding-top:14; padding-bottom:2; width:40%;" align="left"><span class="ms-announcementtitle"><a href="<%=h(a.getThreadUrl(c))%>rowId=<%=a.getRowId()%>"><%=h(a.getTitle())%></a></span><%
        if (a.getResponseCount() > 0)
            out.print(" (" + a.getResponseCount() + (a.getResponseCount() == 1 ? "&nbsp;response)" : "&nbsp;responses)"));
        %></td>
        <td class="normal" style="padding-top:14; padding-bottom:2; width:20%;" align="center"><%=h(a.getCreatedByName(bean.includeGroups, me.getViewContext()))%></td>
        <td class="normal" style="padding-top:14; padding-bottom:2; width:40%;" align="right" nowrap><%=DateUtil.formatDateTime(a.getCreated())%></td>
    </tr>
    <tr style="height:1;"><td colspan=3 class="ms-titlearealine"><img height=1 width=1 src="<%=request.getContextPath()%>/_.gif"></td></tr>
    <tr><td colspan=3 class="normal"><%=a.translateBody(c)%></td></tr>
<%
    if (a.getAttachments().size() > 0)
        { %>
    <tr><td colspan=3 class="normal"><%
        for (Attachment d : a.getAttachments())
        {
    %>
        <a href="<%=h(d.getDownloadUrl("announcements"))%>"><img border=0 src="<%=request.getContextPath()%><%=d.getFileIcon()%>">&nbsp;<%=d.getName()%></a>&nbsp;<%
            }
        %>
    </td></tr>
<%      } %>    <tr><td class="normal" style="padding-bottom:4;" colspan=3 align="left">[<a href="<%=h(a.getThreadUrl(c))%>rowId=<%=a.getRowId()%>">view <%=bean.settings.getConversationName().toLowerCase()%><%if (null != bean.insertURL)
{%> or respond<%}%></a>]</td></tr>
<%
    }
%></table>
<!--/ANNOUNCEMENTS-->

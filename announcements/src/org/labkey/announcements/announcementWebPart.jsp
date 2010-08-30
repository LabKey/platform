<%
/*
 * Copyright (c) 2007-2010 LabKey Corporation
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
<%@ page import="org.labkey.announcements.AnnouncementsController.AnnouncementWebPart" %>
<%@ page import="org.labkey.announcements.AnnouncementsController.AnnouncementWebPart.MessagesBean" %>
<%@ page import="org.labkey.announcements.model.AnnouncementModel" %>
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
<table style="table-layout:fixed;width:100%">
    <tr>
        <td width="40%" align="left"><%
            if (null != bean.insertURL)
            {
        %>[<a href="<%=bean.insertURL%>">new <%=bean.settings.getConversationName().toLowerCase()%></a>]&nbsp;<%
            }
            if (null != bean.listURL)
            {
        %>[<a href="<%=bean.listURL%>">view list</a>]<%
    }
%></td>
        <td  width="20%" align="center"><%=bean.filterText%></td>
        <td  width="40%" align="right"><%
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
    if (0 == bean.announcementModels.length)
    {%>
    <tr><td colspan=3 style="padding-top:4px;">No <%=bean.filterText.replace("all ", "")%></td></tr><%
    }
    for (AnnouncementModel a : bean.announcementModels)
    { %>
    <tr>
        <td class="labkey-announcement-title labkey-force-word-break" width="40%" align="left"><span><a href="<%=h(a.getThreadURL(c))%>rowId=<%=a.getRowId()%>"><%=h(a.getTitle())%></a></span><%
        if (a.getResponseCount() > 0)
            out.print(" (" + a.getResponseCount() + (a.getResponseCount() == 1 ? "&nbsp;response)" : "&nbsp;responses)"));
        %></td>
        <td class="labkey-announcement-title" width="20%" align="center"><%=h(a.getCreatedByName(bean.includeGroups, me.getViewContext()))%></td>
        <td class="labkey-announcement-title" width="40%" align="right" nowrap><%=DateUtil.formatDateTime(a.getCreated())%></td>
    </tr>
    <tr><td colspan=3 class="labkey-title-area-line"></td></tr>
    <tr><td colspan=3 class="labkey-force-word-break"><%=a.translateBody(c)%></td></tr>
<%
    if (a.getAttachments().size() > 0)
        { %>
    <tr><td colspan=3><%
        for (Attachment d : a.getAttachments())
        {
    %>
        <a href="<%=h(d.getDownloadUrl("announcements"))%>"><img src="<%=request.getContextPath()%><%=d.getFileIcon()%>">&nbsp;<%=d.getName()%></a>&nbsp;<%
            }
        %>
    </td></tr>
<%      } %>    <tr><td style="padding-bottom:4;" colspan=3 align="left">[<a href="<%=h(a.getThreadURL(c))%>rowId=<%=a.getRowId()%>">view <%=bean.settings.getConversationName().toLowerCase()%><%if (null != bean.insertURL)
{%> or respond<%}%></a>]</td></tr>
<%
    }
%></table>
<!--/ANNOUNCEMENTS-->

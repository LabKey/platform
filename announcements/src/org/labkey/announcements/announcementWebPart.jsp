<%
/*
 * Copyright (c) 2007-2015 LabKey Corporation
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
<%@ page import="org.labkey.announcements.AnnouncementsController.DownloadAction" %>
<%@ page import="org.labkey.announcements.model.AnnouncementModel" %>
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    AnnouncementWebPart me = (AnnouncementWebPart) HttpView.currentView();
    MessagesBean bean = me.getModelBean();
    Container c = getContainer();
    User user = getUser();
%>
<!--ANNOUNCEMENTS-->
<table style="width:100%">
    <tr>
        <td>
            <div style="text-align: left"><%
            if (null != bean.insertURL)
            {
        %><%= button("New").href(bean.insertURL) %><%
            }
%></div>
            <div style="padding-top: 5px;">Showing: <%=h(bean.filterText)%></div>
        </td>
    </tr><%
    if (0 == bean.announcementModels.length)
    {%>
    <tr><td colspan=3 style="padding-top:4px;">No <%=h(bean.filterText.replace("all ", ""))%></td></tr><%
    }
    for (AnnouncementModel a : bean.announcementModels)
    { %>
    <tr>
        <td class="labkey-announcement-title labkey-force-word-break" colspan=3 align="left"><span><a class="announcement-title-link" href="<%=h(a.getThreadURL(c))%>rowId=<%=a.getRowId()%>"><%=h(a.getTitle())%></a></span></td>
    </tr>
    <tr>
        <td width="40%" align="left"><%
        if (a.getResponseCount() > 0)
            out.print(text(" (" + a.getResponseCount() + (a.getResponseCount() == 1 ? "&nbsp;response)" : "&nbsp;responses)")));
        %></td>
        <td width="20%" align="center"><%=text(a.getCreatedByName(bean.includeGroups, user, true))%></td>
        <td width="40%" align="right" nowrap><%=formatDateTime(a.getCreated())%></td>
    </tr>
    <tr><td colspan=3 class="labkey-title-area-line"></td></tr>
    <tr><td colspan=3 class="labkey-force-word-break"><%=h(a.translateBody(c))%></td></tr>
<%
    if (a.getAttachments().size() > 0)
        { %>
    <tr><td colspan=3><%
        for (Attachment d : a.getAttachments())
        {
    %>
        <a href="<%=h(d.getDownloadUrl(DownloadAction.class))%>"><img src="<%=getContextPath()%><%=h(d.getFileIcon())%>">&nbsp;<%=h(d.getName())%></a>&nbsp;<%
            }
        %>
    </td></tr>
<%      } %>    <tr><td style="padding-bottom:4px;" colspan=3 align="left"><%=textLink("view " + bean.settings.getConversationName().toLowerCase() + (null != bean.insertURL ? " or respond" : ""), a.getThreadURL(c) + "rowId=" + a.getRowId())%></td></tr>
<%
    }
%></table>
<!--/ANNOUNCEMENTS-->

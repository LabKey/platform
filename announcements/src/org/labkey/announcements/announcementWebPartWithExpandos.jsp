<%
/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
<%@ page import="org.labkey.announcements.AnnouncementsController" %>
<%@ page import="org.labkey.announcements.AnnouncementsController.AnnouncementWebPart" %>
<%@ page import="org.labkey.announcements.AnnouncementsController.AnnouncementWebPart.MessagesBean" %>
<%@ page import="org.labkey.announcements.model.AnnouncementModel" %>
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
    AnnouncementWebPart me = (AnnouncementWebPart) HttpView.currentView();
    MessagesBean bean = me.getModelBean();
    Container c = getContainer();
    User user = getUser();
    String tableId = "table" + getRequestScopedUID();
    int maxHeight=120;
%>
<style type="text/css">
DIV.message-container
{
    position:relative;
}
DIV.message-overflow
{
    display:none;
}
DIV.message-more
{
    position:absolute; bottom:0; right:0
}
DIV.message-less
{
    text-align:right;
}
.message-more A, .message-less A, .message-more A:hover, .message-less A:hover
{
    background-color:#e0e0e0; opacity:0.8;
}
/* long message collapsed */
TD.message-collapsed DIV.message-container
{
    max-height:<%=maxHeight%>px;
    overflow-y:hidden;
}
TD.message-collapsed .message-more
{
    display:inline-block;
}
TD.message-collapsed .message-less
{
    display:none;
}
TD.message-collapsed DIV.message-overflow
{
    display:inline-block;
    -ms-filter: progid:DXImageTransform.Microsoft.gradient(gradientType=0,startColor=0,endColoStr=white);
    background-image: -webkit-gradient(linear,left top,left bottom,from(rgba(255, 255, 255, 0)),to(rgba(255, 255, 255, 1.0)));
    background-image: -moz-linear-gradient(top center,rgba(255, 255, 255, 0) 20%,rgba(255, 255, 255, 1.0) 95%);
    bottom: 0;
    filter: progid:DXImageTransform.Microsoft.gradient(gradientType=0,startColor=0,endColorStr=white);
    height: 50px;
    position: absolute;
    width: 100%;
}
/* long message expanded */
TD.message-collapsed div.message-container
{
}
TD.message-expanded .message-more
{
    display:none;
}
TD.message-expanded .message-less
{
    display:inline-block;
}
/* animated expanding... */
TD.message-expanding DIV.message-container
{
    overflow-y:hidden;
}
/* short message */
TD.message-short div.message-container
{
}
TD.message-short .message-less, TD.message-short .message-more
{
    display:none;    
}
</style>
<script type="text/javascript">

    var messageMore, messageLess;
    Ext4.onReady(function() {

        messageMore = function(elem) {
            var more = Ext4.get(elem);
            var parent = more.parent("TD.message");
            parent.removeCls("message-collapsed");
            parent.addCls("message-expanded");
            return false;
        };

        messageLess = function(elem) {
            var more = Ext4.get(elem);
            var parent = more.parent("TD.message");
            parent.removeCls("message-expanded");
            parent.addCls("message-collapsed");
            return false;
        };

        var messageFixup = function(e) {
            var container = Ext4.get(e);
            var parent = container.parent("TD.message");
            var text = Ext4.fly(Ext4.query("DIV.message-text", parent.dom)[0]);
            if (parent.hasCls("message-expanded"))
                return;
            if (text.dom.scrollHeight <= <%=maxHeight%>)
            {
                parent.removeCls("message-collapsed");
                parent.addCls("message-short");
            }
            else
            {
                parent.removeCls("message-short");
                parent.addCls("message-collapsed");
            }
        };

        var messageOnResize = function(id) {
            var table = Ext4.get(id);
            var messages = Ext4.query("DIV.message-container", table.dom);
            Ext4.each(messages, messageFixup);
        };

        messageOnResize(<%=q(tableId)%>);
        Ext4.EventManager.onWindowResize(function(){messageOnResize(<%=q(tableId)%>);});
    });
</script>
<!--ANNOUNCEMENTS-->
<table style="table-layout: fixed; width: 100%;" id="<%=tableId%>">
    <tr>
        <td colspan="3">
            <div style="text-align: left"><%
            if (null != bean.insertURL && !bean.isPrint)
            {
        %><%= button("New").href(bean.insertURL) %><%
            }
%></div>
            <div style="padding-top: 5px;">Showing: <%=h(bean.filterText)%></div>
        </td>
    </tr><%

if (bean.announcementModels.isEmpty())
{
    %><tr><td colspan=3 style="padding-top:4px;">No <%=h(bean.filterText.replace("all ", ""))%></td></tr><%
}

for (AnnouncementModel a : bean.announcementModels)
{
    %><tr>
        <td class="labkey-announcement-title labkey-force-word-break" colspan=3 align="left"><span><a class="announcement-title-link" href="<%=h(a.getThreadURL(c))%>rowId=<%=a.getRowId()%>"><%=h(a.getTitle())%></a></span></td>
    </tr>
    <tr>
        <td width="40%" align="left"><%
        if (a.getResponseCount() > 0)
            out.print(" (" + a.getResponseCount() + (a.getResponseCount() == 1 ? "&nbsp;response)" : "&nbsp;responses)"));
        %></td>
        <td width="20%" align="center" class="message-creator"><%=text(a.getCreatedByName(bean.includeGroups, user, true, false))%></td>
        <td width="40%" align="right" nowrap><%=formatDateTime(a.getCreated())%></td>
    </tr>
    <tr><td colspan=3 class="labkey-title-area-line"></td></tr>
    <tr><td colspan=3 class="message labkey-force-word-break <%=bean.isPrint?"message-expanded":"message-collapsed"%>">
        <div class="message-container">
            <div class="message-text"><%=a.translateBody()%></div><%
            if (!bean.isPrint)
            {
                %><div class="message-overflow"><div class="message-more"><div class="labkey-wp-text-buttons"><a href="#more" style="font-weight:normal;" onclick="return messageMore(this);">more&#9660;</a></div></div></div><%
                %><table width="100%"><tr><td align="right"><div class="message-less"><div class="labkey-wp-text-buttons"><a href="#less" style="font-weight:normal;" onclick="return messageLess(this);">less&#9650;</a></div></div></td></tr></table><%
            }            
        %></div>
    </tr><%

    if (!a.getAttachments().isEmpty())
    {
        %><tr><td colspan=3><%
        for (Attachment d : a.getAttachments())
        {
            ActionURL downloadURL = AnnouncementsController.getDownloadURL(a, d.getName());
            %><a href="<%=h(downloadURL)%>"><img src="<%=getWebappURL(d.getFileIcon())%>">&nbsp;<%=h(d.getName())%></a>&nbsp;<%
        }
        %></td></tr><%
    }
    if (!bean.isPrint)
    {
        %><tr><td style="padding-bottom:4px;" colspan=3 align="left"><%=textLink("view " + bean.settings.getConversationName().toLowerCase() + (null != bean.insertURL ? " or respond" : ""), a.getThreadURL(c) + "rowId=" + a.getRowId())%></td></tr><%
    }
}
%></table>
<!--/ANNOUNCEMENTS-->
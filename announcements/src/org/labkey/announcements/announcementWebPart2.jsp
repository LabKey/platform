<%
/*
 * Copyright (c) 2007-2011 LabKey Corporation
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
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="java.util.Collections" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    AnnouncementWebPart me = (AnnouncementWebPart) HttpView.currentView();
    MessagesBean bean = me.getModelBean();
    Container c = me.getViewContext().getContainer();
    User user = me.getViewContext().getUser();
    String contextPath = me.getViewContext().getContextPath();
    String tableId = "table" + getRequestScopedUID();
    int maxHeight=200;

%>
<style type="text/css">
div.message-container
{
    position:relative;
}
.message-overflow
{
    display:none;
}
.message-more a, .message-less a
{
    background-color:#eeeeee; opacity:1;
}
/* long message collapsed */
td.message-collapsed div.message-container
{
    max-height:<%=maxHeight%>px;
    overflow-y:hidden;
/*    border:solid 1px red; */
}
td.message-collapsed .message-more
{
    display:inline-block;
}
td.message-collapsed .message-less
{
    display:none;
}
td.message-collapsed div.message-overflow
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
td.message-collapsed div.message-container
{
/*    border:solid 1px blue; */
}
td.message-expanded .message-more
{
    display:none;
}
td.message-expanded .message-less
{
    display:inline-block;
    background-color:#00ff00; opacity:1;
}
/* short message */
td.message-short div.message-container
{
/*    border:solid 1px green; */
}
td.message-short .message-less, td.message-short .message-more
{
    display:none;    
}
</style>
<script type="text/javascript">
function messageMore(elem)
{
    var more=Ext.get(elem);
    var parent = more.parent("TD");
    parent.removeClass("message-collapsed");
    parent.addClass("message-expanded");
}
function messageLess(elem)
{
    var more=Ext.get(elem);
    var parent = more.parent("TD");
    parent.removeClass("message-expanded");
    parent.addClass("message-collapsed");
}
function messageFixup(e)
{
    var container = Ext.get(e);
    var parent = container.parent("TD");
    var text = Ext.fly(Ext.query("DIV.message-text", container.dom)[0]);
    console.debug("fixup " + parent.getHeight() + " " + container.getHeight() + " " + text.dom.scrollHeight + " " + text.getHeight());
    if (parent.hasClass("message-expanded"))
        return;
    if (text.dom.scrollHeight <= <%=maxHeight%>)
    {
        parent.removeClass("message-collapsed");
        parent.addClass("message-short");
    }
    else
    {
        parent.removeClass("message-short");
        parent.addClass("message-collapsed");
    }
}
function messageOnResize(id)
{
    var table = Ext.get(id);
    var messages = Ext.query("DIV.message-container", table.dom);
    Ext.each(messages, messageFixup);
}
Ext.onReady(function(){messageOnResize(<%=q(tableId)%>);});
Ext.EventManager.onWindowResize(function(){messageOnResize(<%=q(tableId)%>);});
</script>
<!--ANNOUNCEMENTS-->
<table style="width:100%" id="<%=tableId%>">
    <tr>
        <td>
            <div style="text-align: left"><%
            if (null != bean.insertURL)
            {
        %><%= generateButton("New", bean.insertURL)%><%
            }
%></div>
            <div style="padding-top: 5px;">Showing: <%=bean.filterText.replace(" ", "&nbsp;")%></div>
        </td>
    </tr><%
    if (0 == bean.announcementModels.length)
    {%>
    <tr><td colspan=3 style="padding-top:4px;">No <%=bean.filterText.replace("all ", "")%></td></tr><%
    }
    for (AnnouncementModel a : bean.announcementModels)
    { %>
    <tr>
        <td class="labkey-announcement-title labkey-force-word-break" colspan=3 align="left"><span><a href="<%=h(a.getThreadURL(c))%>rowId=<%=a.getRowId()%>"><%=h(a.getTitle())%></a></span></td>
    </tr>
    <tr>
        <td width="40%" align="left"><%
        if (a.getResponseCount() > 0)
            out.print(" (" + a.getResponseCount() + (a.getResponseCount() == 1 ? "&nbsp;response)" : "&nbsp;responses)"));
        %></td>
        <td width="20%" align="center"><%=h(a.getCreatedByName(bean.includeGroups, user))%></td>
        <td width="40%" align="right" nowrap><%=DateUtil.formatDateTime(a.getCreated())%></td>
    </tr>
    <tr><td colspan=3 class="labkey-title-area-line"></td></tr>
    <tr><td colspan=3 class="labkey-force-word-break message-collapsed">
        <div class="message-container">
            <div class="message-text"><%=a.translateBody(c)%></div>
            <div class="message-overflow"><div class="message-more"><div style="position:absolute; bottom:0; right:0" class="labkey-wp-text-buttons"><a href="#more" onclick="messageMore(this)">more&gt;&gt;</a></div></div></div>
            <div class="message-less"><div style="position:absolute; bottom:0; right:0" class="labkey-wp-text-buttons"><a href="#less" onclick="messageLess(this)">&lt;&lt;less</a></div></div>
        </div>
<%
    if (a.getAttachments().size() > 0)
        { %>
    <tr><td colspan=3><%
        for (Attachment d : a.getAttachments())
        {
    %>
        <a href="<%=h(d.getDownloadUrl(DownloadAction.class))%>"><img src="<%=request.getContextPath()%><%=d.getFileIcon()%>">&nbsp;<%=d.getName()%></a>&nbsp;<%
            }
        %>
    </td></tr>
<%      } %>    <tr><td style="padding-bottom:4px;" colspan=3 align="left"><%=textLink("view " + bean.settings.getConversationName().toLowerCase() + (null != bean.insertURL ? " or respond" : ""), a.getThreadURL(c) + "rowId=" + a.getRowId())%></td></tr>
<%
    }
%></table>
<!--/ANNOUNCEMENTS-->

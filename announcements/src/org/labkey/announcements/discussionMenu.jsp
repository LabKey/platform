<%
/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
<%@ page import="org.labkey.announcements.model.AnnouncementModel" %>
<%@ page import="org.labkey.announcements.model.DiscussionServiceImpl" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="java.util.Set" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    DiscussionServiceImpl.PickerView me = (DiscussionServiceImpl.PickerView) HttpView.currentView();
    Container c = getContainer();
    User user = getUser();

    boolean isGuest = user.isGuest();
    boolean isAdmin = !isGuest && c.hasPermission(user, AdminPermission.class);
    boolean canInsert = !isGuest && c.hasPermission(user, InsertPermission.class);

    AnnouncementModel[] announcementModels = me.announcementModels;
    if (null == announcementModels)
        announcementModels = new AnnouncementModel[0];
    URLHelper pageURL = me.pageURL;

    boolean longFormat = false;
    Set<String> menuItems = new HashSet<>();

    if (me.allowMultipleDiscussions)
    {
        for (AnnouncementModel a : announcementModels)
            longFormat |= !menuItems.add(a.getCreatedByName(user) + "|" + DateUtil.formatDate(c, a.getCreated()));
    }
%>
<style><!--
.discuss-discuss-icon
{
    background-image:url(<%=getContextPath()%>/_images/message.png);
}
.discuss-email-icon
{
    background-image:url(<%=getContextPath()%>/_images/email.png);
}

--></style>
<script type="text/javascript">
if (discussionMenu)
    discussionMenu.menu.destroy();

var discussionMenu = {};
(function(){
    discussionMenu.pageUrl = <%=PageFlowUtil.jsString(pageURL.clone().deleteScopeParameters("discussion").getLocalURIString())%>;
    discussionMenu.emailUrl = 'mailto:?subject=<%=PageFlowUtil.encode(me.title)%>&body=<%=PageFlowUtil.encode(me.pageURL.getURIString())%>';
    discussionMenu.emailPreferencesUrl = <%=PageFlowUtil.jsString(me.emailPreferencesURL.getLocalURIString())%>;
    discussionMenu.adminEmailUrl = <%=PageFlowUtil.jsString(me.adminEmailURL.getLocalURIString())%>;
    discussionMenu.customizeUrl = <%=PageFlowUtil.jsString(me.customizeURL.getLocalURIString())%>;
    discussionMenu.hideUrl = <%=PageFlowUtil.jsString(pageURL.deleteScopeParameters("discussion").addParameter("discussion.hide", "true").getLocalURIString())%>
    discussionMenu.config =
    {
        id:'menuDiscussionMenu',
        cls:'extContainer',
        items:[<%

        String comma = "";
        if (me.allowMultipleDiscussions)
        {
            for (AnnouncementModel a : announcementModels)
            {
                String title = a.getTitle();
                String help = a.getCreatedByName(user) + ' ' + (longFormat ? DateUtil.formatDateTime(c, a.getCreated()) : DateUtil.formatDate(c, a.getCreated()));
                %><%=text(comma)%>{text:<%=PageFlowUtil.jsString(title)%>,helptext:<%=PageFlowUtil.jsString(help)%>,href:discussionMenu.pageUrl+'&discussion.id=<%=a.getRowId()%>#discussionArea'}<%
                comma = ",";
            }
        }
        else if (announcementModels.length > 0)
        {
            if (me.isDiscussionVisible)
            {
                %><%=text(comma)%>{text:'Hide discussion',href:discussionMenu.hideUrl},<%
            }
            else
            {
                AnnouncementModel a = announcementModels[0];
                %><%=text(comma)%>{text:'Show discussion',href:discussionMenu.pageUrl+'&discussion.id=<%=a.getRowId()%>#discussionArea'},<%
            }
            comma = ",";
        }
        if ((me.allowMultipleDiscussions || announcementModels.length == 0) && canInsert)
        {
            %><%=text(comma)%>{text:'Start <%=h(me.allowMultipleDiscussions ? "new " : "")%>discussion',href:discussionMenu.pageUrl+'&discussion.start=true#discussionArea', iconCls:'discuss-discuss-icon'}<%
            comma = ",";
        }
        // if (true)
        {
            %><%=text(comma)%>{text:'Start email discussion', href:discussionMenu.emailUrl, iconCls:'discuss-email-icon'}<%
            comma = ",'-',";
        }
        if (!isGuest)
        {
            %><%=text(comma)%>{text:'Email preferences',href:discussionMenu.emailPreferencesUrl}<%
            comma = ",";
        }
        if (isAdmin)
        {
            %><%=text(comma)%>{text:'Email admin',href:discussionMenu.adminEmailUrl}
            ,{text:'Customize',href:discussionMenu.customizeUrl}<%
            comma = ",";
        }
        %>]
    };

    function onShow()
    {
        if (!discussionMenu.menu)
            discussionMenu.menu = new Ext.menu.Menu(discussionMenu.config);
        discussionMenu.menu.show('discussionMenuToggle');
    }

    Ext4.onReady(function(){Ext4.get("discussionMenuToggle").on("click", onShow)});
})();
</script>
<span id=discussionMenuToggle><%
    if (announcementModels.length > 0 && me.allowMultipleDiscussions)
    {
        %><%=PageFlowUtil.textLink("see discussions (" + announcementModels.length + ")", "#", "return false;", "")%><%
    }
    else
    {
        %><%=PageFlowUtil.textLink("discussion", "#", "return false;", "")%><%
    }
%></span>

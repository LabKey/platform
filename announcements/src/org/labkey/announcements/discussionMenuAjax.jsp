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
<%@ page import="org.labkey.announcements.AnnouncementsController" %>
<%@ page import="org.labkey.announcements.model.AnnouncementModel" %>
<%@ page import="org.labkey.announcements.model.DiscussionServiceImpl" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="java.util.Set" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("Ext3"));
        return resources;
    }
%>
<%
    DiscussionServiceImpl.PickerView me = (DiscussionServiceImpl.PickerView) HttpView.currentView();
    Container c = getContainer();
    User user = getUser();
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

    String discussionAreaToggleId = "discussionAreaToggle" + getRequestScopedUID();
%>

<script type="text/javascript">
(function(){

    var discussionAreaId = <%=q(me.discussionAreaId)%>;
    var discussionAreaToggleId = <%=q(discussionAreaToggleId)%>;

    if (discussionMenu)
        discussionMenu.menu.destroy();

    var discussionMenu = {};

    function _hideMenu()
    {
        if (discussionMenu.menu)
            discussionMenu.menu.setVisible(false);
    }

    function _loadDiscussionArea(menu)
    {
        var e = Ext.get(discussionAreaId);
        if (!e)
            return true;
        e.load({url:menu.hrefAjax, text:"Loading..."});
        e.removeClass("labkey-hidden");
        if (menu.hashBang)
            window.location = "#!" + menu.hashBang;
        return false;
    }

    function _showDiscussion(menu,event)
    {
        _hideMenu();
        if (!menu.hrefAjax)
            return true;
        return _loadDiscussionArea(menu);
    }

    function _startDiscussion(menu,event)
    {
        _hideMenu();
        if (!menu.hrefAjax)
            return true;
        return _loadDiscussionArea(menu);
    }

    discussionMenu.pageUrl = <%=PageFlowUtil.jsString(pageURL.getLocalURIString())%>;
    discussionMenu.emailPreferencesUrl = <%=PageFlowUtil.jsString(me.emailPreferencesURL.getLocalURIString())%>;
    discussionMenu.adminEmailUrl = <%=PageFlowUtil.jsString(me.adminEmailURL.getLocalURIString())%>;
    discussionMenu.customizeUrl = <%=PageFlowUtil.jsString(me.customizeURL.getLocalURIString())%>;
    discussionMenu.hideUrl = <%=PageFlowUtil.jsString(pageURL.deleteScopeParameters("discussion").addParameter("discussion.hide", "true").getLocalURIString())%>
    discussionMenu.config =
    {
        id:'menuDiscussionMenu',
        cls:'extContainer',
        items:[<%

        String comma = "\n";
        if (me.allowMultipleDiscussions)
        {
            for (AnnouncementModel a : announcementModels)
            {
                String title = a.getTitle();
                String help = a.getCreatedByName(user) + ' ' + (longFormat ? DateUtil.formatDateTime(c, a.getCreated()) : DateUtil.formatDate(c, a.getCreated()));
                String href = pageURL.getLocalURIString() + "&discussion.id=" + a.getRowId() + "#discussionArea";
                String hrefAjax = new ActionURL(AnnouncementsController.ThreadBareAction.class, c).addParameter("rowId", a.getRowId()).getLocalURIString();
                String hashBang = "discussion.id=" + a.getRowId();
                %><%=text(comma)%>{text:<%=q(title)%>,helptext:<%=q(help)%>,href:<%=q(href)%>,hrefAjax:<%=q(hrefAjax)%>,hashBang:<%=q(hashBang)%>,listeners:{click:_showDiscussion}}<%
                comma = "\n,";
            }
        }
        else if (announcementModels.length > 0)
        {
            if (me.isDiscussionVisible)
            {
                %><%=text(comma)%>{text:'Hide discussion',href:discussionMenu.hideUrl}<%
                comma = "\n,";
            }
            else
            {
                AnnouncementModel a = announcementModels[0];
                %><%=text(comma)%>{text:'Show discussion',href:discussionMenu.pageUrl+'&discussion.id=<%=a.getRowId()%>#discussionArea'},<%
                comma = "\n,";
            }
        }
        if ((me.allowMultipleDiscussions || announcementModels.length == 0) && c.hasPermission(getUser(), InsertPermission.class))
        {
            %><%=text(comma)%>{text:'Start <%=text(me.allowMultipleDiscussions ? "new " : "")%>discussion',href:discussionMenu.pageUrl+'&discussion.start=true#discussionArea'},<%
            comma = "\n,";
        }
        %>'-'<%=text(comma)%>{text:'Email preferences',href:discussionMenu.emailPreferencesUrl}<%
        comma = "\n,";
        if (c.hasPermission(getUser(), AdminPermission.class))
        {
            %>
            <%=text(comma)%>{text:'Email admin',href:discussionMenu.adminEmailUrl},
            {text:'Customize',href:discussionMenu.customizeUrl}<%
            comma = "\n,";
        }
        %>]
    };

    function onShow()
    {
        if (!discussionMenu.menu)
            discussionMenu.menu = new Ext.menu.Menu(discussionMenu.config);
        discussionMenu.menu.show(discussionAreaToggleId);
    }

    Ext.onReady(function(){Ext.get(discussionAreaToggleId).on("click", onShow)});

})();
</script>
<span id="<%=h(discussionAreaToggleId)%>"><%
    if (announcementModels.length > 0 && me.allowMultipleDiscussions)
    {
        %><%=PageFlowUtil.textLink("see discussions (" + announcementModels.length + ")", "#", "return false;", "")%><%
    }
    else
    {
        %><%=PageFlowUtil.textLink("discussion", "#", "return false;", "")%><%
    }
%></span>
<%
/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
<%@ page import="org.labkey.announcements.model.Announcement" %>
<%@ page import="org.labkey.announcements.model.DiscussionServiceImpl" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="java.util.Set" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%
    DiscussionServiceImpl.PickerView me = (DiscussionServiceImpl.PickerView) HttpView.currentView();
    ViewContext context = me.getViewContext();
    Container c = context.getContainer();
    Announcement[] announcements = me.announcements;
    if (null == announcements)
        announcements = new Announcement[0];
    URLHelper pageURL = me.pageURL;

    boolean longFormat = false;
    Set<String> menuItems = new HashSet<String>();

    if (me.allowMultipleDiscussions)
    {
        for (Announcement a : announcements)
            longFormat |= !menuItems.add(a.getCreatedByName(me.getViewContext()) + "|" + DateUtil.formatDate(a.getCreated()));
    }
%>
<script type="text/javascript">
LABKEY.requiresMenu();
</script>

<script type="text/javascript">
if (discussionMenu)
    discussionMenu.menu.destroy();

var discussionMenu = {};
(function(){
    discussionMenu.pageUrl = <%=PageFlowUtil.jsString(pageURL.getLocalURIString())%>;
    discussionMenu.emailPreferencesUrl = <%=PageFlowUtil.jsString(me.emailPreferencesURL.getLocalURIString())%>;
    discussionMenu.adminEmailUrl = <%=PageFlowUtil.jsString(me.adminEmailURL.getLocalURIString())%>;
    discussionMenu.customizeUrl = <%=PageFlowUtil.jsString(me.customizeURL.getLocalURIString())%>;
    discussionMenu.hideUrl = <%=PageFlowUtil.jsString(pageURL.deleteScopeParameters("discussion").addParameter("discussion.hide", "true").getLocalURIString())%>
    discussionMenu.model = [[<%

        if (me.allowMultipleDiscussions)
        {
            for (Announcement a : announcements)
            {
                String title = a.getTitle();
                String help = a.getCreatedByName(me.getViewContext()) + ' ' + (longFormat ? DateUtil.formatDateTime(a.getCreated()) : DateUtil.formatDate(a.getCreated()));
                %>{text:<%=PageFlowUtil.jsString(title)%>,helptext:<%=PageFlowUtil.jsString(help)%>,url:discussionMenu.pageUrl+'&discussion.id=<%=a.getRowId()%>#discussionArea'},<%
            }
        }
        else if (announcements.length > 0)
        {
            if (me.isDiscussionVisible)
            {
                %>{text:'Hide discussion',url:discussionMenu.hideUrl},<%
            }
            else
            {
                Announcement a = announcements[0];
                %>{text:'Show discussion',url:discussionMenu.pageUrl+'&discussion.id=<%=a.getRowId()%>#discussionArea'},<%
            }
        }
        if ((me.allowMultipleDiscussions || announcements.length == 0) && c.hasPermission(context.getUser(), InsertPermission.class))
        {
            %>{text:'Start <%=me.allowMultipleDiscussions ? "new " : ""%>discussion',url:discussionMenu.pageUrl+'&discussion.start=true#discussionArea'},
            <%
        }
        %>],[{text:'Email preferences',url:discussionMenu.emailPreferencesUrl}<%
        if (c.hasPermission(context.getUser(), AdminPermission.class))
        {
            %>
        ,
    {text:'Email admin',url:discussionMenu.adminEmailUrl},
    {text:'Customize',url:discussionMenu.customizeUrl}
        <%
        }
        %>]];

    discussionMenu.showEvent = function(event)
    {
        YAHOO.util.Event.stopPropagation(event);
        var span = document.getElementById("discussionMenuToggle");
        var xy = YAHOO.util.Dom.getXY(span);
        discussionMenu.menu.moveTo(xy[0] + span.offsetWidth, xy[1]);
        discussionMenu.menu.show();
    };

    discussionMenu.init = function()
    {
        YAHOO.widget.MenuItem.prototype.IMG_ROOT = LABKEY.yahooRoot + "/menu/assets/";
        discussionMenu.menu = new YAHOO.widget.ContextMenu("menuDiscussionMenu", {context:['discussionMenuToggle','tl','tr'], position:'dynamic', visible:false});
        discussionMenu.menu.addItems(discussionMenu.model);
        discussionMenu.menu.render(document.body);
        YAHOO.util.Event.addListener("discussionMenuToggle", "mousedown", discussionMenu.showEvent);
    };
})();

    if (LABKEY.isDocumentClosed)
        discussionMenu.init();
    else
        YAHOO.util.Event.addListener(window, "load", discussionMenu.init);

</script>
<span id=discussionMenuToggle>[<a href="#" onclick="return false;"><%

    if (announcements.length > 0)
    {
        if (me.allowMultipleDiscussions)
        {
            %>see discussions (<%=announcements.length%>)<%
        }
        else
        {
            %>discussion<%
        }
    }
    else
    {
        %>discuss this<%
    }
    %></a>]</span>
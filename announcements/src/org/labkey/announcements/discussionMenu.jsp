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
<%@ page import="org.jetbrains.annotations.NotNull" %>
<%@ page import="org.labkey.announcements.model.AnnouncementModel" %>
<%@ page import="org.labkey.announcements.model.DiscussionServiceImpl" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.PopupMenuView" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    DiscussionServiceImpl.PickerView me = (DiscussionServiceImpl.PickerView) HttpView.currentView();
    Container c = getContainer();
    User user = getUser();

    boolean isGuest = user.isGuest();
    boolean isAdmin = !isGuest && c.hasPermission(user, AdminPermission.class);
    boolean canInsert = !isGuest && c.hasPermission(user, InsertPermission.class);

    @NotNull List<AnnouncementModel> discussions = me.discussions;
    URLHelper pageURL = me.pageURL;

    String toggleId = "discussionAreaToggle" + getRequestScopedUID();
    String pageUrl = pageURL.clone().deleteScopeParameters("discussion").getLocalURIString();
    String emailUrl = "mailto:?subject=" + PageFlowUtil.encode(me.title) + "&body=" + PageFlowUtil.encode(me.pageURL.getURIString());
    String emailPreferencesUrl = me.emailPreferencesURL.getLocalURIString();
    String adminEmailUrl = me.adminEmailURL.getLocalURIString();
    String customizeUrl = me.customizeURL.getLocalURIString();
    String hideUrl = pageURL.deleteScopeParameters("discussion").addParameter("discussion.hide", "true").getLocalURIString();

    NavTree menu = new NavTree();
    if (me.allowMultipleDiscussions)
    {
        for (AnnouncementModel a : discussions)
        {
            String title = a.getTitle();
            menu.addChild(title, pageUrl + "&discussion.id=" + a.getRowId() + "#discussionArea");
        }
    }
    else if (!discussions.isEmpty())
    {
        if (me.isDiscussionVisible)
        {
            menu.addChild("Hide discussion", hideUrl);
        }
        else
        {
            AnnouncementModel a = discussions.get(0);
            menu.addChild("Show discussion", pageUrl + "&discussion.id=" + a.getRowId() + "#discussionArea");
        }
    }
    if ((me.allowMultipleDiscussions || discussions.isEmpty()) && canInsert)
    {
        menu.addChild("Start" + (me.allowMultipleDiscussions ? " new " : "") + "discussion", pageUrl + "&discussion.start=true#discussionArea", null, "fa fa-comments");
    }
    menu.addChild("Start email discussion", emailUrl, null, "fa fa-envelope");
    menu.addSeparator();

    if (!isGuest)
    {
        menu.addChild("Email preferences", emailPreferencesUrl);
    }
    if (isAdmin)
    {
        menu.addChild("Email Admin", adminEmailUrl);
        menu.addChild("Admin", customizeUrl);
    }
%>
<div id="<%=h(toggleId)%>" class="lk-menu-drop dropdown discussion-toggle">
    <a class="labkey-link labkey-text-link" data-toggle="dropdown">Discussions</a>
<%
    out.write("<ul class=\"dropdown-menu dropdown-menu-right\">");
    PopupMenuView.renderTree(menu, out);
    out.write("</ul>");
%>
</div>


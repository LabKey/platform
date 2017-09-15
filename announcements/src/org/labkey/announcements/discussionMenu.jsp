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
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Set" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.PopupMenuView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        if (!PageFlowUtil.useExperimentalCoreUI())
        {
            dependencies.add("Ext4");
        }
    }
%>
<%
    DiscussionServiceImpl.PickerView me = (DiscussionServiceImpl.PickerView) HttpView.currentView();
    Container c = getContainer();
    User user = getUser();

    boolean isGuest = user.isGuest();
    boolean isAdmin = !isGuest && c.hasPermission(user, AdminPermission.class);
    boolean canInsert = !isGuest && c.hasPermission(user, InsertPermission.class);

    @NotNull List<AnnouncementModel> discussions = me.discussions;
    URLHelper pageURL = me.pageURL;

    boolean longFormat = false;
    Set<String> menuItems = new HashSet<>();

    if (me.allowMultipleDiscussions)
    {
        for (AnnouncementModel a : discussions)
            longFormat |= !menuItems.add(a.getCreatedByName(user) + "|" + DateUtil.formatDate(c, a.getCreated()));
    }

    String toggleId = "discussionAreaToggle" + getRequestScopedUID();

    if (PageFlowUtil.useExperimentalCoreUI())
    {
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
<%
    }
    else
    {
%>
<style type="text/css">
.discuss-discuss-icon {
    background-image:url(<%=getContextPath()%>/_images/message.png);
}
.discuss-email-icon {
    background-image:url(<%=getContextPath()%>/_images/email.png);
}
</style>
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
                            items:[<%

        String comma = "";
        if (me.allowMultipleDiscussions)
        {
            for (AnnouncementModel a : discussions)
            {
                String title = a.getTitle();
                String help = a.getCreatedByName(user) + ' ' + (longFormat ? DateUtil.formatDateTime(c, a.getCreated()) : DateUtil.formatDate(c, a.getCreated()));
                %><%=text(comma)%>{text:<%=PageFlowUtil.jsString(title)%>,helptext:<%=PageFlowUtil.jsString(help)%>,href:discussionMenu.pageUrl+'&discussion.id=<%=a.getRowId()%>#discussionArea'}<%
                comma = ",";
            }
        }
        else if (!discussions.isEmpty())
        {
            if (me.isDiscussionVisible)
            {
                %><%=text(comma)%>{text:'Hide discussion',href:discussionMenu.hideUrl},<%
            }
            else
            {
                AnnouncementModel a = discussions.get(0);
                %><%=text(comma)%>{text:'Show discussion',href:discussionMenu.pageUrl+'&discussion.id=<%=a.getRowId()%>#discussionArea'},<%
            }
            comma = ",";
        }
        if ((me.allowMultipleDiscussions || discussions.isEmpty()) && canInsert)
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
            ,{text:'Admin',href:discussionMenu.customizeUrl}<%
            comma = ",";
        }
        %>]
    };
        var toggleId = <%=PageFlowUtil.jsString(toggleId)%>;

        Ext4.onReady(function(){Ext4.get(toggleId).on('click', function() {
            if (!discussionMenu.menu) {
                discussionMenu.menu = Ext4.create('Ext.menu.Menu', discussionMenu.config);
            }
            discussionMenu.menu.showBy(Ext4.get(toggleId));
        })});
    })();
</script>
<span id="<%=h(toggleId)%>" class="discussion-toggle"><%
    if (!discussions.isEmpty() && me.allowMultipleDiscussions)
    {
%><%=PageFlowUtil.textLink("see discussions (" + discussions.size() + ")", "#", "return false;", "")%><%
    }
    else
    {
%><%=PageFlowUtil.textLink("discussion", "#", "return false;", "")%><%
    }
%></span>
<%
    }
%>


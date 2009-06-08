<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.core.user.UserController" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.core.security.SecurityController" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.security.roles.FolderAdminRole" %>
<%@ page import="org.labkey.api.security.*" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.security.SecurityManager" %>
<%@ page import="org.labkey.api.view.menu.ProjectAdminMenu" %>
<%@ page import="org.labkey.api.security.roles.ProjectAdminRole" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%
/*
 * Copyright (c) 2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.                                                                                                                     F
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
%>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView me = (JspView)HttpView.currentView();
    Container c = getViewContext().getContainer();
    Container project = c.getProject();
    Container root = ContainerManager.getRoot();
    User user = getViewContext().getUser();
%>

<script type="text/javascript">
LABKEY.requiresCss("SecurityAdmin.css");
LABKEY.requiresScript("SecurityAdmin.js", true);

var isFolderAdmin = <%=c.hasPermission(user, ACL.PERM_ADMIN) ? "true" : "false"%>;
var isProjectAdmin = <%=project != null && project.hasPermission(user, ACL.PERM_ADMIN) ? "true" : "false"%>;
var isSiteAdmin = <%= user.isAdministrator() ? "true" : "false" %>;
</script>
<script type="text/javascript">
var $ = Ext.get;
var $h = Ext.util.Format.htmlEncode;
var $dom = Ext.DomHelper;

$('bodypanel').addClass('extContainer');

var securityCache = new SecurityCache({
    root:<%=PageFlowUtil.jsString(root.getId())%>,
    project:<%=project==null?"null":PageFlowUtil.jsString(root.getId())%>,
    folder:<%=PageFlowUtil.jsString(c.getId())%>
});
</script>

<%--
    TABS
--%>


<div id="tabBoxDiv" class="extContainer"><i>Loading...</i></div>
<script type="text/javascript">

var tabItems = [];
var tabPanel = null;

Ext.onReady(function(){

    for (var i=0 ; i<tabItems.length ; i++)
        tabItems[i].contentEl = $(tabItems[i].contentEl);

    tabPanel = new Ext.TabPanel({
        autoScroll : true,
        activeItem : 0,
        border : false,
        defaults: {style : {padding:'5px'}},
        items : tabItems
    });

    Ext.EventManager.onWindowResize(function(w,h)
    {
        if (!tabPanel.rendered || !tabPanel.el)
            return;
        var xy = tabPanel.el.getXY();
        var size = {
            width : Math.max(100,w-xy[0]-20),
            height : Math.max(100,h-xy[1]-20)};
        tabPanel.setSize(size);
        tabPanel.doLayout();
    });

    $('tabBoxDiv').update('');
    tabPanel.render('tabBoxDiv');
    tabPanel.strip.applyStyles({'background':'#ffffff'});

    Ext.EventManager.fireWindowResize();
});
</script>

<div style="display:none;">
<%--
    PERMISSIONS
--%>
<% if (!c.isRoot())
{%>
    <div id="permissionsFrame" class="extContainer"></div>
    <script type="text/javascript">
    tabItems.push({contentEl:'permissionsFrame', title:'Permissions', autoHeight:true});
    Ext.onReady(function()
    {
        var policyEditor = new PolicyEditor({cache:securityCache, border:false, isSiteAdmin:isSiteAdmin, isProjectAdmin:isSiteAdmin,
            resourceId:LABKEY.container.id});
        policyEditor.render($('permissionsFrame'));
    });
    </script>
<%}%>

<%--
    GROUPS
--%>

    <div id="groupsFrame"></div>
    <div id="siteGroupsFrame"></div>
    <script type="text/javascript">
    function showPopup(group)
    {
        var canEdit = !group.Container && isSiteAdmin || group.Container && isProjectAdmin;
        var w = new UserInfoPopup({userId:group.UserId, cache:securityCache, policy:null, modal:true, canEdit:canEdit});
        w.show();
    }

    function makeGroupsPanel(project,canEdit,ct)
    {
        var formId = 'newGroupForm' + (project?'':'Site');
        var groupsList = new GroupPicker({cache:securityCache, width:200, border:false, autoHeight:true, projectId:project});
        groupsList.on("select", function(list,group){
            showPopup(group);
        });

        var items = [];
        if (canEdit)
            items.push({border:false, html:'<input id="' + (formId + '$input')+ '" type="text" size="30" name="name"><br><a id="' + (formId + '$submit') + '" class="labkey-button" href="#"" ><span>Create new group</span></a>'});
        items.push(groupsList);
        
        var groupsPanel = new Ext.Panel({
            border : false,
            //border : true, style:{border:'solid 1px red'},
            //autoScroll:true,
            autoHeight:true,
            items : items
        });
        groupsPanel._adjustSize = function()
        {
            if (!this.rendered)
                return;
            if (!this.autoHeight)
            {
                var sz = tabPanel.body.getSize();
                this.setSize(sz.width-10,sz.height-10);
                var btm = sz.height + tabPanel.body.getX();
                groupsList.setSize(200,btm-groupsList.el.getX());
                this.doLayout();
            }
        };
        groupsPanel.render(ct);
        groupsPanel._adjustSize();
        tabPanel.on("bodyresize", groupsPanel._adjustSize, groupsPanel);
        tabPanel.on("activate", groupsPanel._adjustSize, groupsPanel);

        if (canEdit)
        {
            var inputEl = $(formId + '$input');
            var btnEl = $(formId + '$submit');
            var submit = function()
            {
                securityCache.createGroup((project||'/'), inputEl.getValue(), function(group)
                {
                    showPopup(group);
                });
            };
            inputEl.addKeyListener(13, submit);
            btnEl.on("click", submit);
        }
        return groupsPanel;
    };

    if (<%=c.isRoot() ? "false" : "true"%>)
        tabItems.push({contentEl:'groupsFrame', title:<%=PageFlowUtil.jsString("Groups for project " + (null != c.getProject() ? c.getProject().getName() : ""))%>, autoScroll:true});
    if (isSiteAdmin)
        tabItems.push({contentEl:'siteGroupsFrame', title:'Site Groups', autoScroll:true});

    Ext.onReady(function()
    {
        if (<%=c.isRoot() ? "false" : "true"%>)
            makeGroupsPanel(<%=PageFlowUtil.jsString(null == project ? "" : project.getId())%>, isProjectAdmin, 'groupsFrame');
        if (isSiteAdmin)
            makeGroupsPanel(null, isSiteAdmin, 'siteGroupsFrame');
    });

    </script>

<%--
    IMPERSONATE
--%>
<%
    if (user.isAdministrator() || project != null && project.hasPermission(user, AdminPermission.class))
    {
        UserController.ImpersonateView impersonateView = new UserController.ImpersonateView(user.isAdministrator() ? root : null!=project ? project : c, false);
        if (impersonateView.hasUsers())
        {
            %><script type="text/javascript">
            tabItems.push({contentEl:'impersonateFrame', title:'Impersonate', autoHeight:true});
            </script>
            <div id="impersonateFrame"><%
            impersonateView.setFrame(WebPartView.FrameType.NONE);
            me.include(impersonateView,out);
            %></div><%
        }
    }
%>


<%--
    MODULE SECURITY
--%>

<%
        List<SecurityManager.ViewFactory> factories = SecurityManager.getViewFactories();

        int counter = 0;
        for (SecurityManager.ViewFactory factory : factories)
        {
            WebPartView view = (WebPartView)factory.createView(getViewContext());
            if (null == view)
                continue;
            counter++;
            String id = "moduleSecurityView" + counter;
            %><div id=<%=id%>><%
            view.setFrame(WebPartView.FrameType.NONE);
            me.include(view,out);
            %></div>
            <script type="text/javascript">
                tabItems.push({contentEl:<%=PageFlowUtil.jsString(id)%>, title:<%=PageFlowUtil.jsString(view.getTitle())%>, autoHeight:true});
            </script>
            <%
        }
%>
</div>

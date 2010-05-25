<%
/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.core.user.UserController" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.core.security.SecurityController" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.security.*" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.security.SecurityManager" %>
<%@ page import="org.labkey.api.security.roles.RoleManager" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    SecurityController.FolderPermissions me = (SecurityController.FolderPermissions)HttpView.currentView();
    ActionURL doneURL = me.doneURL;
    if (null == doneURL)
        doneURL = PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(getViewContext().getContainer());
    Container c = getViewContext().getContainer();
    Container project = c.getProject();
    Container root = ContainerManager.getRoot();
    User user = getViewContext().getUser();
%>
<style type="text/css">
    .x-tree-node-leaf .x-tree-node-icon{
        background-image:url(<%=getViewContext().getContextPath()%>/ext-3.2.1/resources/images/default/tree/folder.gif);
    }
    .x-tree-node-current {
        font-weight: bold;
    }
</style>
<script type="text/javascript">
LABKEY.requiresCss("SecurityAdmin.css");
LABKEY.requiresScript("SecurityAdmin.js", true);
</script>
<script type="text/javascript">
var isFolderAdmin = <%=c.hasPermission(user, AdminPermission.class) ? "true" : "false"%>;
var isProjectAdmin = <%=project != null && project.hasPermission(user, AdminPermission.class) ? "true" : "false"%>;
var isSiteAdmin = <%= user.isAdministrator() ? "true" : "false" %>;
var isRoot = <%= c.isRoot() ? "true" : "false" %>;

var $ = Ext.get;
var $h = Ext.util.Format.htmlEncode;
var $dom = Ext.DomHelper;

//$('bodypanel').addClass('extContainer');

var securityCache = null;

Ext.onReady(function(){
    securityCache = new SecurityCache({
        root:<%=PageFlowUtil.jsString(root.getId())%>,
        project:<%=project==null?"null":PageFlowUtil.jsString(project.getId())%>,
        folder:<%=PageFlowUtil.jsString(c.getId())%>
    });
});

var policyEditor = null;
</script>

<%--
    TABS
--%>
<% if (!c.isRoot()) { %>
<div id="titleDiv" class="labkey-nav-page-header" style="padding: 5px">Permissions for <%=h(c.getPath())%></div>
<% } %>
<div id="buttonDiv" style="padding:5px;"></div>
<div id="tabBoxDiv" class="extContainer"><i>Loading...</i></div>
<div style="font-style:italic">* indicates permissions are inherited</div>
<script type="text/javascript">

var tabItems = [];
var tabPanel = null;
var borderPanel = null;
var doneURL = <%=doneURL==null?"null":PageFlowUtil.jsString(doneURL.getLocalURIString())%>;

function done()
{
    if (!policyEditor || !policyEditor.isDirty())
        window.location = doneURL;
    else
    {
        policyEditor.save(false, function(){
            LABKEY.setSubmit(true);
            window.location = doneURL;
        });
    }
}
function save()
{
    if (policyEditor)
        policyEditor.save();
}

function cancel()
{
    window.location = doneURL;
}

var autoScroll = true;

Ext.onReady(function(){

    var doneBtn = new Ext.Button({text: (isRoot ? 'Done' : 'Save and Finish'), style:{display:'inline'}, handler:done});
    doneBtn.render($('buttonDiv'));
    if (!isRoot)
    {
        $('buttonDiv').createChild('&nbsp;');
        var saveBtn = new Ext.Button({text:'Save', style:{display:'inline'}, handler:save});
        saveBtn.render($('buttonDiv'));

        var cancelBtn = new Ext.Button({text:'Cancel', style:{display:'inline'}, handler:cancel});
        $('buttonDiv').createChild('&nbsp;');
        cancelBtn.render($('buttonDiv'));
    }

    for (var i=0 ; i<tabItems.length ; i++)
        tabItems[i].contentEl = $(tabItems[i].contentEl);

    var items = [];
    items.push(
        {
            activeItem : 0,
            autoScroll : autoScroll,
            autoHeight : !autoScroll,
            border:true,
            defaults: {style : {padding:'5px'}},
            id : 'folderPermissionsTabPanel',
            items : tabItems,
            plain:true,
            region:'center',
            xtype:'tabpanel'
        });
<% if (!c.isRoot()) { %>
    items.push(new Ext.tree.TreePanel({
            loader: new Ext.tree.TreeLoader({
                dataUrl: LABKEY.ActionURL.buildURL("core", "getExtSecurityContainerTree.api"),
                baseParams: {requiredPermission: '<%=RoleManager.getPermission(AdminPermission.class).getUniqueName()%>'}
            }),
            root: new Ext.tree.AsyncTreeNode({
                id: '<%=project.getRowId()%>',
                expanded: true,
                expandable: false,
                text: <%=PageFlowUtil.jsString(project.getName())%>,
                href: <%=PageFlowUtil.jsString(new ActionURL(SecurityController.ProjectAction.class, project).getLocalURIString())%>,
                cls: '<%=project.equals(c) ? "x-tree-node-current" : ""%>'
            }),
            enableDrag: false,
            useArrows: true,
            autoScroll: true,
            width: 140,
            region: 'west',
            split: true,
            title: 'Folders',
            border: true
        }));
<% } %>

    borderPanel = new Ext.Panel({
        layout:'border',
        border:false,
        items:items
    });

    $('tabBoxDiv').update('');
    borderPanel.render('tabBoxDiv');

    tabPanel = Ext.ComponentMgr.get('folderPermissionsTabPanel');
    tabPanel.strip.applyStyles({'background':'#ffffff'});
    tabPanel.stripWrap.applyStyles({'background':'#ffffff'});

    Ext.EventManager.onWindowResize(function(w,h)
    {
        if (!borderPanel.rendered || !borderPanel.el)
            return;
        var xy = borderPanel.el.getXY();
        var size = {
            width : Math.max(400,w-xy[0]-60),
            height : Math.max(300,h-xy[1]-80)};
        borderPanel.setSize(size.width, autoScroll ? size.height : undefined);
        borderPanel.doLayout();
    });
    Ext.EventManager.fireWindowResize();
});
</script>

<%--
    PERMISSIONS
--%>
    <div id="permissionsFrame" class="extContainer x-hide-display"></div>
    <script type="text/javascript">
    tabItems.push({contentEl:'permissionsFrame', title:'Permissions', autoHeight:true});
    Ext.onReady(function()
    {
        policyEditor = new PolicyEditor({cache:securityCache, border:false, isSiteAdmin:isSiteAdmin, isProjectAdmin:isProjectAdmin,
            resourceId:LABKEY.container.id});
        policyEditor.render($('permissionsFrame'));
    });
    </script>

<%--
    GROUPS
--%>

    <div id="groupsFrame" class="x-hide-display"></div>
    <div id="siteGroupsFrame" class="x-hide-display"></div>
    <script type="text/javascript">
    function showPopup(group, groupsList)
    {
        var canEdit = !group.Container && isSiteAdmin || group.Container && isProjectAdmin;
        var w = new UserInfoPopup({userId:group.UserId, cache:securityCache, policy:null, modal:true, canEdit:canEdit});
        w.on("close", function(){groupsList.onDataChanged();});
        w.show();
    }

    function makeGroupsPanel(project,canEdit,ct)
    {
        var formId = 'newGroupForm' + (project?'':'Site');
        var groupsList = new GroupPicker({cache:securityCache, width:200, border:false, autoHeight:true, projectId:project});
        groupsList.on("select", function(list,group){
            showPopup(group, groupsList);
        });

        var items = [];
        if (canEdit)
            items.push({border:false, html:'<input id="' + (formId + '$input')+ '" type="text" size="30" name="name"><br><a id="' + (formId + '$submit') + '" class="labkey-button" href="#"" ><span>Create new group</span></a>'});
        items.push(groupsList);
        
        var groupsPanel = new Ext.Panel({
            border : false,
            //border : true, style:{border:'solid 1px red'},
            autoScroll:autoScroll,
            autoHeight:!autoScroll,
            items : items
        });
        groupsPanel._adjustSize = function()
        {
            if (!this.rendered)
                return;
            if (!this.autoHeight)
            {
                var sz = tabPanel.body.getSize();
                this.setSize(sz.width-20,sz.height-20);
                var btm = sz.height + tabPanel.body.getX();
                groupsList.setSize(200,btm-groupsList.el.getX());
                this.doLayout();
            }
        };
        groupsPanel.render(ct);

        if (autoScroll)
        {
            groupsPanel._adjustSize();
            tabPanel.on("bodyresize", groupsPanel._adjustSize, groupsPanel);
            tabPanel.on("activate", groupsPanel._adjustSize, groupsPanel);
        }

        if (canEdit)
        {
            var inputEl = $(formId + '$input');
            var btnEl = $(formId + '$submit');
            var submit = function()
            {
                securityCache.createGroup((project||'/'), inputEl.getValue(), function(group)
                {
                    showPopup(group, groupsList);
                });
            };
            inputEl.addKeyListener(13, submit);
            btnEl.on("click", submit);
        }
        return groupsPanel;
    };

    if (<%=c.isRoot() ? "false" : "true"%>)
        tabItems.push({contentEl:'groupsFrame', title:<%=PageFlowUtil.jsString("Groups for project " + (null != c.getProject() ? c.getProject().getName() : ""))%>, autoScroll:autoScroll, autoHeight: !autoScroll});
    if (isSiteAdmin)
        tabItems.push({contentEl:'siteGroupsFrame', title:'Site Groups', autoScroll:autoScroll, autoHeight: !autoScroll});

    Ext.onReady(function()
    {
        securityCache.onReady(function()
        {
            if (<%=c.isRoot() ? "false" : "true"%>)
                makeGroupsPanel(<%=PageFlowUtil.jsString(null == project ? "" : project.getId())%>, isProjectAdmin, 'groupsFrame');
            if (isSiteAdmin)
                makeGroupsPanel(null, isSiteAdmin, 'siteGroupsFrame');
        });
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
            <div id="impersonateFrame" class="x-hide-display"><%
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
            %><div id='<%=id%>' class="x-hide-display"><%
            view.setFrame(WebPartView.FrameType.NONE);
            me.include(view,out);
            %></div>
            <script type="text/javascript">
                tabItems.push({contentEl:<%=PageFlowUtil.jsString(id)%>, title:<%=PageFlowUtil.jsString(view.getTitle())%>, autoHeight:true});
            </script>
            <%
        }
%>

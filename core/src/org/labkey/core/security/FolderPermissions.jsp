<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.core.user.UserController" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.core.security.SecurityController" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.security.roles.FolderAdminRole" %>
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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
%>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView me = (JspView)HttpView.currentView();
    Container c = getViewContext().getContainer();
    Container project = c.getProject();
    User user = getViewContext().getUser();
%>

<script type="text/javascript">
LABKEY.requiresCss("SecurityAdmin.css");
LABKEY.requiresScript("SecurityAdmin.js", true);

var isFolderAdmin = <%=c.hasPermission(user, ACL.PERM_ADMIN) ? "true" : "false"%>;
var isProjectAdmin = <%=project.hasPermission(user, ACL.PERM_ADMIN) ? "true" : "false"%>;
var isSiteAdmin = <%= user.isAdministrator() ? "true" : "false" %>;
</script>
<script type="text/javascript">
var $ = Ext.get;
var $h = Ext.util.Format.htmlEncode;
var $dom = Ext.DomHelper;

$('bodypanel').addClass('extContainer');

// shared cache
var viewport = new Ext.Viewport();
var securityCache = new SecurityCache({});

</script>

<%--
    TABS
--%>


<div id="tabBoxDiv" class="extContainer"></div>
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

    viewport.on("resize", function(v,w,h)
    {
        if (!this.rendered || !this.el)
            return;
        var xy = tabPanel.el.getXY();
        var size = {
            width : Math.max(100,w-xy[0]-8),
            height : Math.max(100,h-xy[1]-8)};
        tabPanel.setSize(size);
        tabPanel.doLayout();
    });
    
    tabPanel.render('tabBoxDiv');
    tabPanel.strip.applyStyles({'background':'#ffffff'});

    var sz = viewport.getSize();
    viewport.fireResize(sz.width, sz.height);
});
</script>

<div style="display:none;">
<%--
    PERMISSIONS
--%>
<div id="permissionsFrame" class="extContainer"></div>
<script type="text/javascript">
tabItems.push({contentEl:'permissionsFrame', title:'Permissions', autoHeight:true});
Ext.onReady(function()
{
    var policyEditor = new PolicyEditor({cache:securityCache, border:false, isSiteAdmin:isSiteAdmin, isProjectAdmin:isSiteAdmin,
        resourceId:LABKEY.container.id});
    policyEditor.render($('permissionsFrame'));

//    tabPanel.on("beforetabchange", function(panel, newTab, currentTab)
//    {
//        if (currentTab.contentEl.id != 'permissionsFrame')
//            return true;
//        if (!policyEditor.isDirty())
//            return true;
//        Ext.MessageBox.alert("Save Changes", "Please save changes (refresh page to discard)");
//        return false;
//    });
});
</script>


<%--
    GROUPS
--%>
<%
    if (project.hasPermission(user, ACL.PERM_ADMIN))
    {
        String title;
        if (null == c || c.isRoot())
            title = "Site Groups";
        else
            title = "Groups for project " + c.getProject().getName();

        if (1==1)
        {
            %><div id="groupsFrame"></div>
            <div id="siteGroupsFrame"></div>
            <script type="text/javascript">
            function makeGroupsPanel(project,ct)
            {
                var newGroupForm = null;
                var groupsList = new GroupPicker({cache:securityCache, width:200, border:false, autoScroll:true, projectId:project});
                groupsList.on("select", function(list,group){
                    var canEdit = !group.Container && isSiteAdmin || group.Container && isProjectAdmin;
                    var w = new UserInfoPopup({userId:group.UserId, cache:this.cache, policy:null, modal:true, canEdit:canEdit});
                    w.show();
                });

                var formId = 'newGroupForm' + (project?'':'Site');
                var action = LABKEY.ActionURL.buildURL('security','newGroupExt.post',project);
                var groupsPanel = new Ext.Panel({
                    border : false, // border : true, style:{border:'solid 1px red'},
                    height: 400, width:800,
                    items :[
                            {border:false, html:'<input id="' + (formId + '$input')+ '" type="text" size="30" name="name"><br><a id="' + (formId + '$submit') + '" class="labkey-button" href="#"" ><span>Create new group</span></a>'},
                            groupsList
                        ]
                });
                groupsPanel._adjustSize = function()
                {
                    if (this.rendered)
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

                // UNDONE: use security api (Security.js)
                var inputEl = $(formId + '$input');
                var btnEl = $(formId + '$submit');
                var submit = function()
                {
                    securityCache.createGroup(project, inputEl.getValue());
                };
                inputEl.addKeyListener(13, submit);
                btnEl.on("click", submit);
                return groupsPanel;
            };


            tabItems.push({contentEl:'groupsFrame', title:<%=PageFlowUtil.jsString(title)%>, autoHeight:true});
            if (isSiteAdmin)
                tabItems.push({contentEl:'siteGroupsFrame', title:'Site Groups', autoHeight:true});

            Ext.onReady(function()
            {
                var groupsPanel = makeGroupsPanel(<%=PageFlowUtil.jsString(project.getId())%>, 'groupsFrame');
                if (isSiteAdmin)
                    var sitePanel = makeGroupsPanel(null, 'siteGroupsFrame');
            });
            
            </script><%
        }
        else // groups.jsp 
        {
            // UNDONE: support expanding specified group
            JspView<SecurityController.GroupsBean> groupsView = new JspView<SecurityController.GroupsBean>("/org/labkey/core/security/groups.jsp",
                    new SecurityController.GroupsBean(getViewContext(), null, null), null);
            %><script type="text/javascript">
            tabItems.push({contentEl:'groupsFrame', title:<%=PageFlowUtil.jsString(title)%>, autoHeight:true});
            </script>
            <div id="groupsFrame"><%
            groupsView.setFrame(WebPartView.FrameType.NONE);
            me.include(groupsView,out);
            %></div><%
        }
    }
%>


<%--
    IMPERSONATE
--%>
<%
    if (project.hasPermission(user, ACL.PERM_ADMIN))
    {
        UserController.ImpersonateView impersonateView = new UserController.ImpersonateView(project, false);
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
</div>

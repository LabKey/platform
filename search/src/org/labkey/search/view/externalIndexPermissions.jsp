<%
/*
 * Copyright (c) 2009-2015 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.SecurableResource" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.search.SearchController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SecurableResource> me = (JspView<SecurableResource>)HttpView.currentView();
    Container c = getContainer();
    ActionURL doneURL = new ActionURL(SearchController.AdminAction.class, c);
    Container project = c.getProject();
    Container root = ContainerManager.getRoot();
    User user = getUser();
    SecurableResource resource = me.getModelBean();
%>
<style type="text/css">
    .x-tree-node-leaf .x-tree-node-icon{
        background-image:url(<%=getViewContext().getContextPath()%>/<%=PageFlowUtil.extJsRoot()%>/resources/images/default/tree/folder.gif);
    }
    .x-tree-node-current {
        font-weight: bold;
    }
</style>
<script type="text/javascript">
LABKEY.requiresCss("SecurityAdmin.css");
LABKEY.requiresScript("SecurityAdmin.js");
Ext.QuickTips.init();
</script>
<script type="text/javascript">
var isFolderAdmin = <%=c.hasPermission(user, AdminPermission.class) ? "true" : "false"%>;
var isProjectAdmin = <%=project != null && project.hasPermission(user, AdminPermission.class) ? "true" : "false"%>;
var isSiteAdmin = <%= user.isSiteAdmin() ? "true" : "false" %>;
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
<div id="titleDiv" class="labkey-nav-page-header" style="padding: 5px">Permissions for <%=h(resource.getResourceDescription())%></div>
<div id="buttonDiv" style="padding:5px;"></div>
<div id="tabBoxDiv" class="extContainer"><i>Loading...</i></div>
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
        policyEditor = new PolicyEditor({cache:securityCache, border:false, isSiteAdmin:isSiteAdmin, isProjectAdmin:isProjectAdmin, canInherit:false,
            resourceId:"<%=resource.getResourceId()%>"});
        policyEditor.render($('permissionsFrame'));
    });
    </script>


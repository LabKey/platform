<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.core.user.UserController" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.core.security.SecurityController" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
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
    var policyEditor = new PolicyEditor({securityCache:securityCache, border:false});
    securityCache.onReady(function(){
        policyEditor.setResource(LABKEY.container.id);
    });
    policyEditor.render($('permissionsFrame'));
});
</script>


<%--
    GROUPS
--%>
<%
    if (project.hasPermission(user, ACL.PERM_ADMIN))
    {
        // UNDONE: support expanding specified group
        JspView<SecurityController.GroupsBean> groupsView = new JspView<SecurityController.GroupsBean>("/org/labkey/core/security/groups.jsp",
                new SecurityController.GroupsBean(getViewContext(), null, null), null);
        String title;
        if (null == c || c.isRoot())
            title = "Site Groups";
        else
            title = "Groups for project " + c.getProject().getName();
        %><script type="text/javascript">
        tabItems.push({contentEl:'groupsFrame', title:<%=PageFlowUtil.jsString(title)%>, autoHeight:true});
        </script>
        <div id="groupsFrame"><%
        groupsView.setFrame(WebPartView.FrameType.NONE);
        me.include(groupsView,out);
        %></div><%
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
            ((WebPartView)impersonateView).setFrame(WebPartView.FrameType.NONE);
            me.include(impersonateView,out);
            %></div><%
        }
    }
%>
</div>
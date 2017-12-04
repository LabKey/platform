<%
/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.security.SecurityManager" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.roles.RoleManager" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.core.security.SecurityController" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.security.permissions.UserManagementPermission" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Permissions");
    }
%>
<%
    SecurityController.FolderPermissionsView me = (SecurityController.FolderPermissionsView)HttpView.currentView();
    Container c = getContainer();
    ActionURL doneURL = me.doneURL;
    if (null == doneURL)
    {
        if (c.isRoot())
            doneURL = new ActionURL(AdminController.ShowAdminAction.class,c);
        else
            doneURL = urlProvider(ProjectUrls.class).getStartURL(c);
    }
    Container project = c.getProject();
    Container root = ContainerManager.getRoot();
    User user = getUser();
%>
<style type="text/css">

    h3 {
        margin: 0;
        padding: 0;
        font-size: 100%;
    }

    div.rolepanel {
        border: 1px solid #B4B4B4;
        border-bottom: none;
    }

    div.last {
        border-bottom: 1px solid;
    }

    div.rolehover {
        background-color: #F4F4F4;
    }

    span.closeicon {
        background-image: url(<%=request.getContextPath()%>/ext-4.2.1/resources/ext-theme-classic-sandbox/images/tools/tool-sprites.gif);
    }

    td.tree-node-selected {
        font-weight: bold;
    }
</style>
<%--
    TABS
--%>
<% if (!c.isRoot()) { %>
<div id="titleDiv" class="labkey-nav-page-header" style="padding: 5px">Permissions for <%=h(c.getPath())%></div>
<% } %>
<div id="tabBoxDiv"></div>
<% if (!c.isRoot()) { %>
<div style="font-style:italic;">* indicates permissions are inherited</div>
<% } %>
<script type="text/javascript">

var viewTabs = [];

Ext4.onReady(function(){
    Ext4.QuickTips.init();

    var editor = Ext4.create('Security.panel.PermissionEditor', {
        renderTo: 'tabBoxDiv',
        minHeight: 450,
        isSiteRoot: <%= c.isRoot() ? "true" : "false" %>,
        isRootUserManager: <%= user.hasRootPermission(UserManagementPermission.class) ? "true" : "false" %>,
        isProjectRoot: <%=(c.isProject())?"true":"false"%>,
        isProjectAdmin: <%=project != null && project.hasPermission(user, AdminPermission.class) ? "true" : "false"%>,
        canInherit: <%=(!c.isProject() && !c.isRoot())?"true":"false"%>,
        securityCache: Ext4.create('Security.util.SecurityCache', {
            root: <%=PageFlowUtil.jsString(root.getId())%>,
            project: <%=project==null?"null":PageFlowUtil.jsString(project.getId())%>,

            // 16762 - Provide a projectPath, should be considered for folder
            projectPath: <%=project==null?"null":PageFlowUtil.jsString(project.getPath())%>,
            folder: <%=PageFlowUtil.jsString(c.getId())%>,
            global: true
        }),
        autoResize: {
            skipHeight: false
        },
        doneURL: <%=doneURL==null?"null":PageFlowUtil.jsString(doneURL.getLocalURIString())%>
    <% if (!c.isRoot()) { %>
        ,treeConfig: {
           requiredPermission: '<%=RoleManager.getPermission(AdminPermission.class).getUniqueName()%>',
           showContainerTabs: true,
           project: {
               id : '<%=project.getRowId()%>',
               name: <%=PageFlowUtil.jsString(project.getName())%>,
               securityHref: <%=PageFlowUtil.qh(new ActionURL(SecurityController.PermissionsAction.class, project).getLocalURIString())%>
           }
        }
    <% } %>
    });

    editor.on('afterlayout', function() {
        var tPanel = editor.getTabPanel();
        var contentId = 'impersonateFrame';
        var contentEl = Ext4.get(contentId);
        if (contentEl) {
            tPanel.add({contentEl:contentId, title:'Impersonate', autoHeight:true, deferredRender:false});
        }
        for (var v=0; v < viewTabs.length; v++)
        {
            tPanel.add(viewTabs[v]);
        }
    }, this, {single: true});

    Ext4.EventManager.fireResize();
});

</script>

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
        %><div id='<%=id%>' class="x4-hide-display"><%
        view.setFrame(WebPartView.FrameType.NONE);
        me.include(view,out);
        %></div>
        <script type="text/javascript">
            viewTabs.push({contentEl:<%=PageFlowUtil.jsString(id)%>, title:<%=PageFlowUtil.jsString(view.getTitle())%>, autoScroll:true, autoHeight: false});
        </script>
        <%
    }
%>

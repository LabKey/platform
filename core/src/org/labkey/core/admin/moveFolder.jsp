<%
/*
 * Copyright (c) 2005-2011 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.core.admin.AdminController.ManageFoldersForm" %>
<%@ page import="org.labkey.core.admin.AdminController.MoveFolderTreeView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.security.roles.RoleManager" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    MoveFolderTreeView me = (MoveFolderTreeView) HttpView.currentView();
    ManageFoldersForm form = me.getModelBean();
    ViewContext ctx = me.getViewContext();
    Container c = ctx.getContainer();
    Container project = c.getProject();

    String name = form.getName();
    if (null == name)
        name = c.getName();
    String containerType = (c.isProject() ? "project" : "folder");
%>
<style type="text/css">
    .x-tree-node-leaf .x-tree-node-icon{
        background-image:url(<%=ctx.getContextPath()%>/<%=PageFlowUtil.extJsRoot()%>/resources/images/default/tree/folder.gif);
    }

    .x-tree-selected a.x-tree-node-anchor span {
        font-weight: bold;
    }

    .button-bar {
        padding-bottom: 8px;
    }
</style>
<table class="button-bar">
    <tr>
        <td><%=generateButton("Confirm Move", "#", "action('confirmmove');")%></td>
    </tr>
</table>
<table class="">
    <tr>
        <td><form name="moveAddAlias" action="showMoveFolderTree.view">
        <input type="checkbox" onchange="document.forms.moveAddAlias.submit()" name="addAlias" <% if (form.isAddAlias()) { %>checked<% } %>> Add a folder alias for the folder's current location. This will make links that still target the old folder location continue to work.
        <% if (form.isShowAll()) { %>
            <input type="hidden" name="showAll" value="1" />
        <% } %>
    </form></td>
    </tr>
</table>
<div id="folderdiv" class="extContainer"></div>
<script type="text/javascript">
    LABKEY.requiresClientAPI(true);
</script>
<script type="text/javascript">

    var folderTree;
    var selectedNode;
    var _init = false;

    var actionMap = {
        confirmmove : 'moveFolder'
    };

    function init() {

        Ext.QuickTips.init();

        folderTree = new Ext.tree.TreePanel({
            loader : new Ext.tree.TreeLoader({
                dataUrl : LABKEY.ActionURL.buildURL('core', 'getExtContainerAdminTree.api'),
                baseParams : {move: true, requiredPermission : <%=PageFlowUtil.jsString(RoleManager.getPermission(AdminPermission.class).getUniqueName())%>}
            }),
            root : new Ext.tree.AsyncTreeNode({
                id : <%= PageFlowUtil.jsString(Integer.toString(project.getParent().getRowId()))%>,
                expanded : true,
                editable : true,
                expandable : true,
                text : <%=PageFlowUtil.jsString(project.getParent().getName())%>,
                <%=project.equals(c) ? "cls : 'x-tree-node-current'" : ""%>
            }),
            rootVisible: false,
            enableDrag: false,
            useArrows : true,
            autoScroll: true,
            title : 'Please select a new parent for Folder \'' + <%=PageFlowUtil.jsString(c.getName())%> + '\'.',
            border: true
        });

        folderTree.on('click', function(node, event) {
            selectedNode = node;
        });

        var folderPanel = new Ext.Panel({
            layout : 'fit',
            border : false,
            renderTo: 'folderdiv',
            items : [folderTree]
        });

        var _resize = function(w, h) {
            if (!folderPanel.rendered)
                return;
            var padding = [60,60];
            var xy = folderPanel.el.getXY();
            var size = {
                width : Math.max(100,w-xy[0]-padding[0]),
                height : Math.max(100,h-xy[1]-padding[1])};
            folderPanel.setSize(size);
            folderPanel.doLayout();
        };

        Ext.EventManager.onWindowResize(_resize);
        Ext.EventManager.fireWindowResize();

        _init = true;
    }

    function action(actionType) {
        if (_init && selectedNode && actionType) {
            actionType = actionType.toLowerCase();
            if (actionMap[actionType]) {
                var params = {};
                if (actionType == 'confirmmove'){
                    params['target'] = selectedNode.attributes.containerPath;
                }
                var url = LABKEY.ActionURL.buildURL('admin', actionMap[actionType], LABKEY.ActionURL.containerPath, params);
                window.location = url;
            }
            else {
                console.error("'" + actionType + "' is not a valid action.");
            }
        }
    }

    Ext.onReady(init);
</script>

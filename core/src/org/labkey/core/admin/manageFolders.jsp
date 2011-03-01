<%
/*
 * Copyright (c) 2006-2010 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ContainerManager"%>
<%@ page import="org.labkey.api.security.permissions.AdminPermission"%>
<%@ page import="org.labkey.api.util.ContainerTreeSelected"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.ViewContext"%>
<%@ page import="org.labkey.core.admin.AdminController.*"%>
<%@ page import="java.util.List"%>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.security.roles.RoleManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    HttpView<ManageFoldersForm> me = (HttpView<ManageFoldersForm>) HttpView.currentView();
    final ViewContext ctx = me.getViewContext();
    final Container c = ctx.getContainer();
    final ActionURL currentUrl = ctx.cloneActionURL();
    final ContainerTreeSelected ct = new ContainerTreeSelected(c.getProject().getName(), ctx.getUser(), AdminPermission.class, currentUrl, "managefolders");
    Container parent = c.getParent();
    Container project = c.getProject();
    List<Container> siblings = parent == null ? null : parent.getChildren();
    boolean hasSiblings = siblings != null && siblings.size() > 1;

    ct.setCurrent(c);
    ct.setInitialLevel(1);
%>
<style type="text/css">
    .x-tree-node-leaf .x-tree-node-icon{
        background-image:url(<%=ctx.getContextPath()%>/<%=PageFlowUtil.extJsRoot()%>/resources/images/default/tree/folder.gif);
    }

    .x-tree-selected a.x-tree-node-anchor span {
        font-weight: bold;
    }
</style>
<div id="folderdiv" class="extContainer"></div>
<script type="text/javascript">

    var folderTree;
    var selectedFolder;
    var _init = false;

    var actions = {
        alias  : 'folderAliases',
        create : 'createFolder',
        move   : 'showMoveFolderTree',
        order  : 'reorderFolders',
        remove : 'deleteFolder',
        rename : 'renameFolder'
    };
    
    function init() {

        Ext.QuickTips.init();

        folderTree = new Ext.tree.TreePanel({           
            loader : new Ext.tree.TreeLoader({
                dataUrl : LABKEY.ActionURL.buildURL('core', 'getExtContainerAdminTree.api'),
                baseParams : {requiredPermission : <%=PageFlowUtil.jsString(RoleManager.getPermission(AdminPermission.class).getUniqueName())%>}
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
            border: true,
            tbar : [new Ext.Button({text: 'Rename', ref: '../rename', handler : function(){ action('rename'); }}),
                    new Ext.Button({text: 'Move', ref: '../move', handler : function(){ action('move'); }}),
                    new Ext.Button({text: 'Create Subfolder', ref: '../create', handler : function(){ action('create'); }}),
                    new Ext.Button({text: 'Delete', ref: '../remove', handler : function(){ action('remove'); }}),
                    new Ext.Button({text: 'Aliases', ref: '../alias', handler : function(){ action('alias'); }})]
        });

        /*
         * select node with id - this is only called once
         * after the tree is rendered for the first time.
         * After that the listener itself is unregistered.
         * Credit: http://www.sourcepole.ch/2010/9/28/understanding-what-s-going-on-in-extjs
         */
        function select_node(node) {
            node.eachChild( function(child) {
                if(child.attributes.id == <%= c.getRowId() %>) {
                    child.select();
                    validateFolder(child);
                }
            });
        }
        
        folderTree.on('expandnode', select_node);
        folderTree.on('click', validateFolder);

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
        if (_init && selectedFolder && actionType) {
            actionType = actionType.toLowerCase();
            if (actions[actionType]) {
                window.location = LABKEY.ActionURL.buildURL('admin', actions[actionType], selectedFolder.attributes.containerPath);
            }
            else {
                console.error("'" + actionType + "' is not a valid action.");
            }
        }
    }

    function validateFolder(folder) {

        if (folder)
            selectedFolder = folder;
        else
            console.error("Failed to retrieve the selected folder.");

        folderTree.rename.setDisabled(selectedFolder.attributes.notModifiable);
        folderTree.move.setDisabled(selectedFolder.attributes.notModifiable);
        folderTree.remove.setDisabled(selectedFolder.attributes.notModifiable);        
    }

    Ext.onReady(init);
</script>
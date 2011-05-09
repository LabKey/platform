<%
/*
 * Copyright (c) 2006-2011 LabKey Corporation
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
<%@ page import="org.labkey.api.security.permissions.AdminPermission"%>
<%@ page import="org.labkey.api.security.roles.RoleManager"%>
<%@ page import="org.labkey.api.util.ContainerTreeSelected"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.ViewContext"%>
<%@ page import="org.labkey.core.admin.AdminController.ManageFoldersForm"%>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    HttpView<ManageFoldersForm> me = (HttpView<ManageFoldersForm>) HttpView.currentView();
    final ViewContext ctx = me.getViewContext();
    final Container c = ctx.getContainer();
    final ActionURL currentUrl = ctx.cloneActionURL();
    Container project = c.getProject();
    final ContainerTreeSelected ct = new ContainerTreeSelected(project.getName(), ctx.getUser(), AdminPermission.class, currentUrl, "managefolders");

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
        rename : 'renameFolder',
        reorder: 'reorderFolders'
    };
    
    function init() {

        Ext.QuickTips.init();

        Ext.Ajax.timeout = 300000; // 5 minutes.

        folderTree = new Ext.tree.TreePanel({           
            loader : new Ext.tree.TreeLoader({
                dataUrl : LABKEY.ActionURL.buildURL('core', 'getExtContainerAdminTree.api'),
                baseParams : {requiredPermission : <%=PageFlowUtil.jsString(RoleManager.getPermission(AdminPermission.class).getUniqueName())%>}
            }),

            root : {
                id : <%= PageFlowUtil.jsString(Integer.toString(project.getParent().getRowId()))%>,
                nodeType : 'async',
                expanded : true,
                editable : true,
                expandable : true,
                draggable : false,
                text : <%=PageFlowUtil.jsString(project.getParent().getName())%>
                <%=project.equals(c) ? ", cls : 'x-tree-node-current'" : ""%>
            },

            listeners : {
                contextmenu    : onRightClick,
                dblclick       : onDblClick,
                beforenodedrop : onBeforeNodeDrop,
                nodedragover   : onNodeDragOver
            },

            contextMenu : new Ext.menu.Menu({
                cls   : 'extContainer',
                items : [{
                    text : 'Delete'
                },{
                    id      : 'sort-alpha',
                    text    : 'Display Subfolders Alphabetically',
                    handler : function(item, e) {
                        var node = item.parentMenu.contextNode;
                        if (node && node.childNodes.length) {

                            mask('Reordering Folders...');
                            Ext.Ajax.request({
                                url     : LABKEY.ActionURL.buildURL('admin', 'reorderFolders.api', node.childNodes[0].attributes.containerPath),
                                method  : 'POST',
                                params  : {resetToAlphabetical : true},
                                success : function(){
                                    folderTree.getLoader().load(node);
                                    unmask();
                                },
                                failure : function(){ unmask(); }
                            });
                            
                        }
                    }
                }]
            }),

            rootVisible: false,
            enableDD: true,
            containerScroll : true,
            animate : true,
            useArrows : true,
            autoScroll: true,
            border: true,
            tbar : [new Ext.Button({text: 'Rename', ref: '../rename', handler : function(){ action('rename'); }}),
                    new Ext.Button({text: 'Move', ref: '../move', handler : function(){ action('move'); }}),
                    new Ext.Button({text: 'Create Subfolder', ref: '../create', handler : function(){ action('create'); }}),
                    new Ext.Button({text: 'Delete', ref: '../remove', handler : function(){ action('remove'); }}),
                    new Ext.Button({text: 'Aliases', ref: '../alias', handler : function(){ action('alias'); }}),
                    new Ext.Button({text: 'Change Display Order', ref: '../reorder', handler : function(){ action('reorder'); }})]
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
                    ensureVisible(child);
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
            if (selectedFolder){ ensureVisible(selectedFolder); }            
        };

        Ext.EventManager.onWindowResize(_resize);
        Ext.EventManager.fireWindowResize();

        _init = true;
    }

    function ensureVisible(node){
        node.ensureVisible(function(){
            node.select();
            validateFolder(node);
        });
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

    function onSuccess(){
        unmask();
    }

    function onFailure(){
        unmask();
    }

    function mask(message) {
        if (message) {
            Ext.get('folderdiv').mask(message);
        }
        else { Ext.get('folderdiv').mask('Moving Folders. This could take a few minutes...'); }
    }

    function unmask() {
        Ext.get('folderdiv').unmask();
    }

    function onRightClick(node, e){
        node.select();
        var c = folderTree.contextMenu;
        c.contextNode = node;
        c.showAt(e.getXY());
    }

    function onDblClick(e){
        var attr = e.attributes;
        for (var a in attr) {
            if (!Ext.isObject(attr[a]) && !Ext.isArray(attr[a])) {
                console.info(a + ": " + attr[a]);
            }
        }
    }

    function onBeforeNodeDrop(e){
        var s = e.dropNode;

        var target = calculateTarget(e);
        var d = target.leaf ? target.parentNode : target;

        e.confirmed = undefined;
        e.oldParent = s.parentNode;

        mask();

        // Reorder
        if (target == s.parentNode) {

            var eTarget = e.target;
            var order = "";
            var sep = "";
            
            // The event contains the above/below nodes so that should give correct order
            for (var j = 0; j < target.childNodes.length; j++) {
                if (target.childNodes[j] == s) { continue; }
                if (target.childNodes[j] == eTarget) {
                    if (e.point == 'above') {
                        order += sep + s.text + ";" + eTarget.text;                       
                    }
                    else if (e.point == 'below') {
                        order += sep + eTarget.text + ";" + s.text;
                    }
                    else { return false; /* shouldn't ever get here. */ }
                }
                else { order += sep + target.childNodes[j].text; }
                sep = ";";
            }
            
            console.info('Order: ' + order);
            Ext.Ajax.request({
                url     : LABKEY.ActionURL.buildURL('admin', 'reorderFolders.api', s.containerPath),
                method  : 'POST',
                params  : {order : order, resetToAlphabetical : false},
                success : function() {
                    unmask();
                },
                failure     : function() {
                    alert('Failed to Reorder.');
                    unmask();
                }
            });
        }
        else {

            // Make move request
            LABKEY.Security.moveContainer({
                container : s.attributes.containerPath,
                parent    : d.attributes.containerPath,
                success   : function(response){
                    if (response.success) {
                        s.attributes.containerPath = response.newPath;
                    }

                    // reload the subtree
                    folderTree.getLoader().load(s);

                    onSuccess(response);
                },
                failure   : onFailure
            });

        }
    }

    // The event is cancelled if the drag is invalid.
    function onNodeDragOver(e) {
        var node = e.dropNode;
        if (node.attributes.isProject || node.attributes.notModifiable) {
            console.info('Failed on isProject/notModifiable');
            e.cancel = true;
        }

        target = calculateTarget(e);
        
        // Check matching name FIX so it works as reorder in same subfolder
        var children = target.childNodes;
        var nodeName = node.text.toLowerCase();
        for (var i = 0; i < children.length; i++) {
            if (children[i].text.toLowerCase() == nodeName) {
                if (target == node.parentNode) {
                    console.info('Attempt to reorder.');
                    return;
                }
                console.info('Failed on matching child name.');
                e.cancel = true;
            }
        }
    }

    // Gives the correct target folder based on the drag/drop action. This should be used by all methods
    // that incorporate drag/drop in folder tree.
    function calculateTarget(e) {

        if (!e.target) {
            console.error('Target not provided by event.');
            e.cancel = true; // attempt to salvage event
            return null;
        }
        
        var target = e.target;
        console.info('Target is ' + target.attributes.containerPath);
        
        // Use event.point to determine correct target node
        var pt = e.point;
        if (pt) {
            if (pt == 'above' || pt == 'below'){
                // check if same parent, check if root node -- cannot elevate to project
                if (target.parentNode) {
                    target = target.parentNode;
                    console.info('Target set to ' + target.attributes.containerPath);
                    if (target.attributes.containerPath === undefined) {
                        console.info('ContainerPath undefined.');
                        e.cancel = true;
                    }
                }
                // different parent equates to move (and reorder?)
            }
        }

        return target;
    }

    Ext.onReady(init);
</script>

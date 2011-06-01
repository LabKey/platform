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
<%@ page import="org.labkey.api.settings.AppProps" %>
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
    LABKEY.requiresScript("Ext.ux.MultiSelectTreePanel.js");
</script>
<script type="text/javascript">    
    
    function init() {

        Ext.QuickTips.init();

        Ext.Ajax.timeout = 300000; // 5 minutes.

        var selectedFolder;
        var m = Ext.MessageBox.buttonText;
        
        var actions = {
            alias  : 'folderAliases',
            create : 'createFolder',
            move   : 'showMoveFolderTree',
            order  : 'reorderFolders',
            remove : 'deleteFolder',
            rename : 'renameFolder',
            reorder: 'reorderFolders'
        };

        var debug = <%= AppProps.getInstance().isDevMode() %>;
        function r(m){ if (debug) { console.info(m); }}
        /*
        LABKEY.ext.FMTreeNodeUI = Ext.extend(Ext.tree.TreeNodeUI, {
            appendDDGhost : function(ghostNode) {
                var ghostEl = document.createElement('div');
                ghostEl.innerHTML = "<p>CREATE ME GHOST</p>";
                ghostNode.appendChild(ghostEl);
            }
        });

        var loader = new Ext.tree.TreeLoader({
            createNode : function(attr) {
                attr.uiProvider = LABKEY.ext.FMTreeNodeUI;
                return Ext.tree.TreeLoader.prototype.createNode.call(this, attr);
            },
            dataUrl    : LABKEY.ActionURL.buildURL('core', 'getExtContainerAdminTree.api'),
            baseParams : {requiredPermission : <%=PageFlowUtil.jsString(RoleManager.getPermission(AdminPermission.class).getUniqueName())%>}
        });
        */

        var folderTree = new Ext.ux.MultiSelectTreePanel({
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
                text : 'LabKey Server Projects'
                <%=project.equals(c) ? ", cls : 'x-tree-node-current'" : ""%>
            },
            
            listeners : {
                contextmenu    : onRightClick,
                expandnode     : select_node,
                click          : validateFolder,
                dblclick       : onDblClick,
                beforenodedrop : onBeforeNodeDrop,
                nodedragover   : onNodeDragOver,
                nodedrop       : function() { folderTree.info.update(''); },
                enddrag        : function() { folderTree.info.update(''); }
            },

            contextMenu : new Ext.menu.Menu({
                cls   : 'extContainer',
                items : [
                    {text: 'Aliases',          handler : function(i,e){ action('alias'); } },
                    {text: 'Create Subfolder', handler : function(i,e){ action('create'); } },
                    {text: 'Delete',           handler : function(i,e){ action('remove'); }, sensitive : true },
                    {text: 'Display Subfolders Alphabetically', handler : function(item, e) {
                        var node = item.parentMenu.contextNode;
                        if (node && node.childNodes.length) {
                            reorderFolders(node.childNodes[0], undefined, true, node);
                        }
                    }, id: 'sort-alpha' },
                    {text: 'Rename',           handler : function(i,e){ action('rename'); }, sensitive : true }
                ],
                listeners : {
                    beforeshow : function(menu) {
                        var node = menu.contextNode;
                        var items = menu.items;
                        items.each(function(item, idx, len){
                            if (node.attributes.notModifiable){
                                item.setDisabled(item.sensitive);
                            }
                            else { item.enable(); }
                            return true;
                        }, this);
                    }
                }
            }),

            rootVisible: true,
            enableDD: true,
            containerScroll : true,
            animate : true,
            useArrows : true,
            autoScroll: true,
            border: true,
            tbar : [
                {text: 'Aliases',              ref: '../alias',   handler : function(){ action('alias'); }},
                {text: 'Change Display Order', ref: '../reorder', handler : function(){ action('reorder'); }},
                {text: 'Create Subfolder',     ref: '../create',  handler : function(){ action('create'); }},
                {text: 'Delete',               ref: '../remove',  handler : function(){ action('remove'); }},
                {text: 'Move',                 ref: '../move',    handler : function(){ action('move'); }},
                {text: 'Rename',               ref: '../rename',  handler : function(){ action('rename'); }}
            ],
            buttonAlign : 'left',
            fbar : [{
                xtype  : 'box',
                ref    : '../info',
                autoEl : { tag: 'div', html : '&nbsp;' }
            }]
        });
        
        /*
         * select node with id - this is only called once
         * after the tree is rendered for the first time.
         * After that the listener itself is unregistered.
         * Credit: http://www.sourcepole.ch/2010/9/28/understanding-what-s-going-on-in-extjs
         */
        function select_node(node) {
            r('called select node');
            node.eachChild( function(child) {
                if(child.attributes.id == <%= c.getRowId() %>) {
                    ensureVisible(child);
                }
            });
        }

        var folderPanel = new Ext.Panel({
            renderTo: 'folderdiv',
            layout  : 'fit',
            border  : false,
            items   : [folderTree]
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

        function action(actionType) {
            if (selectedFolder && actionType) {
                actionType = actionType.toLowerCase();
                if (actions[actionType]) {
                    window.location = LABKEY.ActionURL.buildURL('admin', actions[actionType], selectedFolder.attributes.containerPath);
                }
                else { console.error("'" + actionType + "' is not a valid action."); }
            }
        }

        function ensureVisible(node){
            node.ensureVisible(function(){
                node.select();
                validateFolder(node);
            });
        }

        function validateFolder(node, e) {

            r('node ' + node.attributes.containerPath);
            if (e && e.nodeDrop && e.nodeDrop.length){
                r(e.nodeDrop.length + ' selected.');
            }
            if (node)
                selectedFolder = node;
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
            if (message) { Ext.get('folderdiv').mask(message); }
            else { Ext.get('folderdiv').mask('Moving Folders. This could take a few minutes...'); }
            folderTree.info.update('');
        }

        function unmask() {
            Ext.get('folderdiv').unmask();
            folderTree.info.update('');
        }

        function onRightClick(node, e){
            node.select();
            validateFolder(node); // 12264: Right-click doesn't select in manage folders tree
            var c = folderTree.contextMenu;
            c.contextNode = node;
            c.showAt(e.getXY());
        }

        function onDblClick(e){
            var attr = e.attributes;
            for (var a in attr) {
                if (!Ext.isObject(attr[a]) && !Ext.isArray(attr[a])) {
                    r(a + ": " + attr[a]);
                }
            }
        }

        var confirmation = false;
        
        function onBeforeNodeDrop(e){

            r('Processing ' + e.dropNode.length + ' folders.');

            var c = 0;
            var exeSet = [];
            var isMove = false;
            
            for (var n=0; n < e.dropNode.length; n++) {
                
                exeSet.push(function(f) {
                    r('f is ' + f);
                    r('Working on ' + e.dropNode[f].attributes.containerPath);
                    var s = e.dropNode[f];

                    var target = calculateTarget(e);
                    var d = target.leaf ? target.parentNode : target;

                    e.oldParent = s.parentNode;

                    // Reorder
                    if (target == s.parentNode && !isMove) {

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

                        reorderFolders(s, order, null, null, successHandler);
                    }
                    else {

                        isMove = true;
                        r('bubbling ' + s.attributes.containerPath);
                        s.bubble(function(ps){
                            if (ps.attributes.isProject){
                                d.bubble(function(pd){
                                    if (pd.attributes.isProject) {
                                        r(ps.attributes.containerPath + " is the project root.");
                                        r(pd.attributes.containerPath + " is the project target.");

                                        function move(){

                                            // Make move request
                                            mask("Moving Folders. This could take a few minutes...");
                                            LABKEY.Security.moveContainer({
                                                container : s.attributes.containerPath,
                                                parent    : d.attributes.containerPath,
                                                success   : function(response){
                                                    r('SUCCESS!');
                                                    if (response.success) {
                                                        r('PATH RESET! ' + response.newPath);
                                                        s.attributes.containerPath = response.newPath;
                                                    }

                                                    // reload the subtree
                                                    folderTree.getLoader().load(s);

                                                    onSuccess(response);
                                                    successHandler();
                                                },
                                                failure   : failureHandler
                                            });

                                        }

                                        if (ps.attributes.id == pd.attributes.id || confirmation) { move(); }
                                        else {
                                            confirmation = true;
                                            m.yes = 'Confirm Move';
                                            m.no  = 'Cancel';
                                            Ext.Msg.confirm('Change Project', 'You are moving folder \'' + s.attributes.text + '\' from one project to another. ' +
                                                    'This will remove all permissions settings from this folder, any subfolders, and any other configurations. ' +
                                                    '<br/><b>This action cannot be undone.</b>', function(btn, text){
                                                if (btn == 'yes'){ move(); }
                                                else {
                                                    // Rollback
                                                    folderTree.getLoader().load(ps);
                                                    folderTree.getLoader().load(pd);
                                                    ps.expand();
                                                }
                                            });
                                        }

                                        return false; // stop bubble
                                    }
                                });
                                return false; // stop bubble
                            }
                        });
                    }
                }); // end function

            } // end loop
            
            function executeNext() {
                var currentFn = exeSet[c];
                c = c+1;
                if ((c-1) < e.dropNode.length) {
                    currentFn(c-1);
                }
                else {
                    // Done - reset state
                    confirmation = false;
                }
            }

            function failureHandler(){ alert("FAILED!"); }
            
            function successHandler(data, response, opts) { executeNext(); }
            
            executeNext();
        }

        // The event is cancelled if the drag is invalid.
        function onNodeDragOver(e) {

            function nodeDragOver(e) {
                var node = e.dropNode;
                if (node.attributes.isProject || node.attributes.notModifiable) {
                    folderTree.info.update('');
                    e.cancel = true;
                }

                target = calculateTarget(e);

                // Check matching name FIX so it works as reorder in same subfolder
                var children = target.childNodes;
                var nodeName = node.text.toLowerCase();
                for (var i = 0; i < children.length; i++) {
                    if (children[i].text.toLowerCase() == nodeName) {
                        if (target == node.parentNode && !node.attributes.isProject) {
                            folderTree.info.update('Change display order of /' + nodeName + ' in ' + node.parentNode.attributes.containerPath);
                            return;
                        }
                        folderTree.info.update('/' + nodeName + ' already exists in ' + node.parentNode.attributes.containerPath);
                        e.cancel = true;
                    }
                }

                if (node.attributes.isProject && target == node.parentNode && (e.point == 'above' || e.point == 'below')) {
                    folderTree.info.update('Change display order of /' + nodeName + ' in LabKey Server Projects.');
                    e.cancel = false;
                }
            }
            
            var f;
            for (var n=0; n < e.dropNode.length; n++){
                f = e;
                f.dropNode = f.dropNode[n];
                nodeDragOver(f);
                e.cancel = f.cancel;
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
            folderTree.info.update('Move to ' + target.attributes.containerPath);

            // Use event.point to determine correct target node
            var pt = e.point;
            if (pt) {
                if (pt == 'above' || pt == 'below'){
                    // check if same parent, check if root node -- cannot elevate to project
                    if (target.parentNode) {
                        target = target.parentNode;
                        r('Target set to ' + target.attributes.containerPath);
                        if (target.attributes.containerPath === undefined) {
                            folderTree.info.update('');
                            e.cancel = true;
                        }
                    }
                    // different parent equates to move (and reorder?)
                }
            }

            return target;
        }

        function reorderFolders(s, order, alpha, alphaNode, successFn) {
            mask();

            function reorder(){

                var params = {};
                if (order) {params.order = order;}
                if (alpha) {params.resetToAlphabetical = true;}

                Ext.Ajax.request({
                    url     : LABKEY.ActionURL.buildURL('admin', 'reorderFoldersApi.api', s.attributes.containerPath),
                    method  : 'POST',
                    params  : params,
                    success : function() {

                        if (alpha) {
                            folderTree.getLoader().load(alphaNode);
                            alphaNode.expand();
                        }
                        else{
                            if (s.attributes.isProject) {
                                folderTree.getLoader().load(s.parentNode);
                                folderTree.getRootNode().expand();
                            }
                        }
                        unmask();
                        if(successFn) { successFn(); }
                    },
                    failure : function() {
                        Ext.Msg.alert('Error Reordering.', 'Failed to Reorder.');
                        unmask();
                    }
                });

            }

            if (confirmation) { reorder(); }
            else {
                confirmation = true;
                m.yes = 'Confirm Reorder';
                m.no  = 'Cancel';
                Ext.Msg.confirm('Change Display Order', 'Please confirm that you would like to reorder.', function(btn, text, z){
                    if (btn == 'yes') { reorder(); }
                    else {
                        // Rollback UI movement
                        if (s.parentNode) {
                            var node = s.parentNode;
                            folderTree.getLoader().load(node);
                            node.expand();
                        }
                    }
                    unmask();
                });
            }
        }
    }

    Ext.onReady(init);
</script>

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
<%@ page import="org.labkey.api.settings.AppProps"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.ViewContext"%>
<%@ page import="org.labkey.core.admin.AdminController.ManageFoldersForm"%>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    HttpView<ManageFoldersForm> me = (HttpView<ManageFoldersForm>) HttpView.currentView();
    ViewContext ctx = me.getViewContext();
    Container c = ctx.getContainer();
    Container project = c.getProject();
%>
<style type="text/css">
    .x-tree-node-leaf .x-tree-node-icon{
        background-image:url(<%=ctx.getContextPath()%>/<%=PageFlowUtil.extJsRoot()%>/resources/images/default/tree/folder.gif);
    }

    .x-tree-selected a.x-tree-node-anchor span {
        font-weight: bold;
    }

    li.tree-node-ghost {
        list-style-type: none;
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
            nodedrop       : function() { folderTree.r(''); },
            enddrag        : function() { folderTree.r(''); }
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
            {text: 'Rename',               ref: '../rename',  handler : function(){ action('rename'); }},
            {xtype: 'box', ref : '../info', autoEl : { tag : 'div', html : '&nbsp;'}}
        ],
        buttonAlign : 'left',
        r : function(msg, error) {
            var _msg = "<span";
            if (error) _msg += " style = \"color: red;\"";
            _msg += ">" + msg + "</span>";
            folderTree.info.update(_msg);
        }
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

        var _n = folderTree.getSelectionModel().getSelectedNodes();
        var tool = folderTree.getTopToolbar();

        if (node)
            selectedFolder = node;
        else
            console.error("Failed to retrieve the selected folder.");

        if (_n.length > 1)
            tool.disable();
        else {
            tool.enable();
            folderTree.rename.setDisabled(selectedFolder.attributes.notModifiable);
            folderTree.move.setDisabled(selectedFolder.attributes.notModifiable);
            folderTree.remove.setDisabled(selectedFolder.attributes.notModifiable);
        }

        folderTree.info.enable();
    }

    function onSuccess(){
        unmask();
    }

    function onFailure(){
        unmask();
    }

    function mask(message) {
        if (message) { Ext.get('folderdiv').mask(message); }
        folderTree.r('');
    }

    function unmask() {
        Ext.get('folderdiv').unmask();
        folderTree.r('');
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

                var _t = calculateTarget(e.dropNode[f], e.target, e.point);
                if (_t.cancel) e.cancel = true;
                var target = _t.target;

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

                    reorderFolders(s, order, false, null, successHandler);
                }
                else {

                    isMove = true;
                    function move(){

                        // Make move request
                        mask("Moving Folders. This could take a few minutes...");
                        LABKEY.Security.moveContainer({
                            container : s.attributes.containerPath,
                            parent    : d.attributes.containerPath,
                            success   : function(response){
                                if (response.success) {
                                    s.attributes.containerPath = response.newPath;

                                    // 12406 - reload the subtree
                                    if (f == (e.dropNode.length-1)) {
                                        if (s.parentNode) {
                                            var p = s.parentNode;
                                            folderTree.getLoader().load(p);
                                            p.expand();
                                        }
                                    }

                                    onSuccess(response);
                                    successHandler();
                                }
                                else { failureHandler(response); }
                            },
                            failure   : failureHandler
                        });

                    }

                    s.bubble(function(ps){
                        if (ps.attributes.isProject){
                            d.bubble(function(pd){
                                if (pd.attributes.isProject) {
                                    r(ps.attributes.containerPath + " is the project root.");
                                    r(pd.attributes.containerPath + " is the project target.");

                                    m.yes = 'Confirm Move';
                                    m.no  = 'Cancel';

                                    if (confirmation) move();
                                    else if (ps.attributes.id == pd.attributes.id) {
                                        var _t, _msg;
                                        if (e.dropNode.length > 1) {
                                            _t = 'Move Folders';
                                            _msg = 'You are moving multiple folders. Are you sure you would like to move these folders?';
                                        }
                                        else {
                                            _t = 'Move Folder';
                                            _msg = 'You are moving folder \'' + s.attributes.text + '\'. Are you sure you would like to move this folder?';
                                        }

                                        Ext.Msg.confirm(_t, _msg,
                                                function(btn, text){
                                                    confirmation = true;
                                                    if (btn == 'yes'){ move(); }
                                                    else {
                                                        // Rollback
                                                        confirmation = false;
                                                        folderTree.getLoader().load(ps);
                                                        folderTree.getLoader().load(pd);
                                                        ps.expand();
                                                    }
                                                });
                                    }
                                    else {
                                        Ext.Msg.confirm('Change Project', 'You are moving folder \'' + s.attributes.text + '\' from one project to another. ' +
                                                'This will remove all permissions settings from this folder, any subfolders, and any other configurations. ' +
                                                '<br/><b>This action cannot be undone.</b>',
                                                function(btn, text){
                                                    confirmation = true;
                                                    if (btn == 'yes'){ move(); }
                                                    else {
                                                        // Rollback
                                                        confirmation = false;
                                                        folderTree.getLoader().load(ps);
                                                        folderTree.getLoader().load(pd);
                                                        ps.expand();
                                                    }
                                                });
                                    }
                                    r('stopping bubble');
                                    return false; // stop bubble
                                }
                            });
                            r('stopping bubble');
                            return false; // stop bubble
                        }
                        if (ps.parent == null) r('stopping on ' + ps.attributes.containerPath);
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
                r('confirmation reset');
                confirmation = false;
            }
        }

        function failureHandler(response, opts){
            if (response && response.errors) {
                var errors = response.errors;
                var _msg = "";
                for (var i=0; i < errors.length; i++) {
                    _msg += errors[i].msg + "\n";
                }
                Ext.Msg.alert('Failed to Move', _msg);
            }
            else {
                Ext.Msg.alert('Operation Failed', 'Failed to complete move.');
            }
            unmask();
        }

        function successHandler(data, response, opts) { executeNext(); }

        executeNext();
    }

    // The event is cancelled if the drag is invalid.
    function onNodeDragOver(e) {

        function nodeDragOver(node, target, point) {

            if (node.attributes.isProject || node.attributes.notModifiable) {
                folderTree.r('');
                return true;
            }

            var t = calculateTarget(node, target, point);
            if (t.cancel) return true;

            // Check matching name FIX so it works as reorder in same subfolder
            var children = t.target.childNodes;
            var nodeName = node.text.toLowerCase();
            for (var i = 0; i < children.length; i++) {
                if (children[i].text.toLowerCase() == nodeName) {
                    if (t.target == node.parentNode && !node.attributes.isProject) {
                        folderTree.r('Change display order of /' + nodeName + ' in ' + node.parentNode.attributes.containerPath);
                        return false;
                    }
                    folderTree.r('/' + nodeName + ' already exists in ' + node.parentNode.attributes.containerPath, true);
                    return true;
                }
            }

            if (node.attributes.isProject && t.target == node.parentNode && (e.point == 'above' || e.point == 'below')) {
                folderTree.r('Change display order of /' + nodeName + ' in LabKey Server Projects.');
                return false;
            }

            return false;
        }

        // cycle over all selected nodes
        var path = e.dropNode[0].parentNode.attributes.containerPath;
        for (var n=0; n < e.dropNode.length; n++){
            if (e.dropNode[n].parentNode.attributes.containerPath != path) {
                e.cancel = true;
                folderTree.r('You may only multi-select folders at the same level within a single project.', true);
                return;
            }
            if (nodeDragOver(e.dropNode[n], e.target, e.point)) {
                e.cancel = true;
                return;
            }
        }
    }

    // Gives the correct target folder based on the drag/drop action. This should be used by all methods
    // that incorporate drag/drop in folder tree.
    // Returns an object containing the target node and whether to cancel the event
    function calculateTarget(node, target, point) {

        if (!target)
            return {target: undefined, cancel: true};

        folderTree.r('Move to ' + target.attributes.containerPath);

        // Use event.point to determine correct target node
        if (point) {
            if (point == 'above' || point == 'below'){
                // check if same parent, check if root node -- cannot elevate to project
                if (target.parentNode) {
                    target = target.parentNode;
                    r('Target set to ' + target.attributes.containerPath);
                    if (target.attributes.containerPath === undefined) {
                        folderTree.r('');
                        return {target: target, cancel: true};
                    }
                }
                // different parent equates to move (and reorder?)
            }
        }

        return {target: target, cancel: false};
    }

    function reorderFolders(s, order, alpha, alphaNode, successFn) {

        function reorder(){

            var params = {};
            if (order) {params.order = order;}
            params.resetToAlphabetical = alpha;

            mask();
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
            Ext.Msg.confirm('Change Display Order', 'Are you sure you want to change the folder display order?', function(btn, text, z){
                if (btn == 'yes') { reorder(); }
                else {
                    confirmation = false; // 12402
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

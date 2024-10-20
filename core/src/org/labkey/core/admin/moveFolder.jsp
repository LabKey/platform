<%
/*
 * Copyright (c) 2005-2017 Fred Hutchinson Cancer Research Center
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.roles.RoleManager" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.admin.AdminController.MoveFolderAction" %>
<%@ page import="org.labkey.core.admin.AdminController.MoveFolderTreeView" %>
<%@ page import="org.springframework.validation.Errors" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="org.labkey.api.util.JavaScriptFragment" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("clientapi/ext3");
    }
%>
<%
    MoveFolderTreeView me = (MoveFolderTreeView) HttpView.currentView();
    ViewContext ctx = getViewContext();
    Container c = getContainer();
    Container project = c.getProject();
%>
<style type="text/css">
    .x-tree-node-leaf .x-tree-node-icon{
        background-image:url(<%=getExt3Image("/tree/folder.gif")%>);
    }

    .x-tree-selected a.x-tree-node-anchor span {
        font-weight: bold;
    }

    .button-bar {
        padding-bottom: 8px;
    }
</style>
<%
    Errors errors = me.getErrors();
    if (null != errors && 0 != errors.getErrorCount())
    {
%>
        <table><%
        for (ObjectError e : errors.getAllErrors())
        {
            %><tr><td colspan=3><font class="labkey-error"><%=h(ctx.getMessage(e))%></font></td></tr><%
        }%>
        </table>
<%
    }
%>
<labkey:form id="moveFolderForm" action="<%=urlFor(MoveFolderAction.class)%>" method="POST">
<input type="hidden" id="targetFolder" name="target" value="">
<table class="button-bar">
    <tr>
        <td><%= button("Confirm Move").onClick("action('confirmmove');").id("confirm-move-btn").style("display: none;") %></td>
        <td><%=generateBackButton("Cancel")%></td>
    </tr>
</table>
<table class="">
    <tr><td>
        <input type="hidden" name="<%=getSpringFieldMarker()%>addAlias"><input type="checkbox" id='cb_move_folder_alias' name="addAlias" checked> Add a folder alias for the folder's current location. This ensures that links targeting the old folder location continue to work.
    </td></tr>
</table>
<div id="folderdiv" class="extContainer"></div>
</labkey:form>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">

    var folderTree;
    var selectedNode;
    var _init = false;

    var actionMap = {
        confirmmove : 'moveFolder'
    };

    function init() {

        Ext.QuickTips.init();

        // Move Button -- Disable
        var moveBtn = Ext.get('confirm-move-btn');
        moveBtn.addClass('labkey-disabled-button');
        moveBtn.show();

        folderTree = new Ext.tree.TreePanel({
            loader : new Ext.tree.TreeLoader({
                dataUrl : LABKEY.ActionURL.buildURL('core', 'getExtContainerAdminTree.api'),
                baseParams : {move: true, requiredPermission : <%=q(RoleManager.getPermission(AdminPermission.class).getUniqueName())%>, showContainerTabs: false}
            }),
            root : new Ext.tree.AsyncTreeNode({
                id : <%= q(Integer.toString(project.getParent().getRowId()))%>,
                expanded : true,
                editable : true,
                expandable : true,
                text : <%=q(project.getParent().getName())%>,
                <%=JavaScriptFragment.unsafe(project.equals(c) ? "cls : 'x-tree-node-current'" : "")%>
            }),
            rootVisible: false,
            enableDrag: false,
            useArrows : true,
            autoScroll: true,
            title : 'Please select a new parent for Folder \'' + <%=q(c.getName())%> + '\'.',
            border: true
        });

        folderTree.on('click', function(node, event) {
            moveBtn.replaceClass('labkey-disabled-button', 'labkey-button');
            selectedNode = node;
        });

        var folderPanel = new Ext.Panel({
            layout : 'fit',
            border : false,
            renderTo: 'folderdiv',
            items : [folderTree]
        });

        Ext.EventManager.onWindowResize(function(w, h) {
            LABKEY.ext.Utils.resizeToViewport(folderPanel, w, h);
        });
        Ext.EventManager.fireWindowResize();

        _init = true;
    }

    function action(actionType)
    {
        if (_init && selectedNode && actionType)
        {
            actionType = actionType.toLowerCase();
            if (actionMap[actionType])
            {
                if (actionType === 'confirmmove') {
                    Ext.get('targetFolder').dom.value = selectedNode.attributes.containerPath;
                    Ext.get('moveFolderForm').dom.submit();
                }
            }
            else
            {
                console.error("'" + actionType + "' is not a valid action.");
            }
        }
    }

    Ext.onReady(init);
</script>

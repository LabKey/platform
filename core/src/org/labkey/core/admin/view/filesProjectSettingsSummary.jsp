<%
/*
 * Copyright (c) 2011-2013 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<script type="text/javascript">
    LABKEY.requiresScript("TreeGrid.js");
    Ext.QuickTips.init();
</script>

<table width="100%">
    <tr><td colspan="2" class="labkey-announcement-title"><span>Project View for File Web Parts</span></td></tr>
    <tr><td colspan="2" class="labkey-title-area-line"></td></tr>
    <tr><td>File web parts can be viewed on a project/folder basis. A file web part for a specific folder can
        be browsed or have its email notifications administered by selecting an item in the tree view and
        clicking the appropriate button on the toolbar.
    </td></tr>
    <tr><td>&nbsp;</td></tr>
    <tr><td><div id="viewsGrid" class="extContainer"></div></td></tr>
</table>

<script type="text/javascript">

    function configSelected(node)
    {
        if (node != undefined && node.attributes.configureURL != undefined)
        {
            window.location = node.attributes.configureURL;
        }
    }

    function browseSelected(node)
    {
        if (node != undefined && node.attributes.browseURL != undefined)
        {
            window.location = node.attributes.browseURL;
        }
    }

    Ext.onReady(function()
    {
        var loader =  new Ext.ux.tree.TreeGridLoader({
                dataUrl: LABKEY.ActionURL.buildURL("filecontent", "fileContentProjectSummary", this.container),
                requestMethod: 'POST',
                baseParams: {rootContainer: LABKEY.container.id}
            });

        var idConfigBtn = Ext.id();
        var idBrowseBtn = Ext.id();

        // show the file root browser
        var tree = new Ext.ux.tree.TreeGrid({
            rootVisible:false,
            autoScroll:true,
            loader: loader,
            columns:[{
                header:'Project folders',
                width:450,
                dataIndex:'name'
            },{
                header:'File System Path',
                width:420,
                dataIndex:'path'
            }],
            tbar: [
                {text:'Expand All', tooltip: {text:'Expands all containers', title:'Expand All'}, listeners:{click:function(button, event) {tree.expandAll();}}},
                {text:'Collapse All', tooltip: {text:'Collapses all containers', title:'Collapse All'}, listeners:{click:function(button, event) {tree.collapseAll();}, scope:this}},
                {text:'Configure Selected', id:idConfigBtn, tooltip: {text:'Configure settings for the selected web part', title:'Configure Selected'}, disabled: true, listeners:{click:function(button, event) {configSelected(tree.getSelectionModel().getSelectedNode());}, scope:this}},
                {text:'Browse Selected', id:idBrowseBtn, tooltip: {text:'Browse files from the selected web part', title:'Browse Selected'}, disabled: true, listeners:{click:function(button, event) {browseSelected(tree.getSelectionModel().getSelectedNode());}, scope:this}}
            ],
            dataUrl: LABKEY.ActionURL.buildURL("filecontent", "fileContentSummary", this.container, {node: LABKEY.container.id}),
            listeners: {dblclick: function(node){browseSelected(node);}}
        });

        tree.getSelectionModel().on('selectionchange', function(sm, node){
            var enable = node.attributes.browseURL;
            Ext.getCmp(idConfigBtn).setDisabled(!enable);
            Ext.getCmp(idBrowseBtn).setDisabled(!enable);
        }, this);

        var panel = new Ext.Panel({
            layout: 'fit',
            renderTo: 'viewsGrid',
            items: [tree],
            height: 250
        });
    });
</script>


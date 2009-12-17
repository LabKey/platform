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
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.FilesSiteSettingsAction" %>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<script type="text/javascript">
    LABKEY.requiresExtJs(true);
    LABKEY.requiresClientAPI(true);
    LABKEY.requiresScript("ColumnTree.js");
</script>

<%
    FilesSiteSettingsAction.FileSettingsForm bean = ((JspView<FilesSiteSettingsAction.FileSettingsForm>)HttpView.currentView()).getModelBean();
%>

<labkey:errors/>
<form action="filesSiteSettings.view" method="post">
    <table>
        <tr><td colspan=2>Configure file system</td></tr>
        <tr><td class="labkey-form-label">File root <%=PageFlowUtil.helpPopup("File root", "Set a site level file root. " +
                "When a site level file root is set, each folder for every project has a corresponding subdirectory in the file system." +
                " A site level file root may be overridden at the project level from 'Project Settings'.")%></td>
            <td><input type="text" name="rootPath" size="64" value="<%=h(bean.getRootPath())%>"></td>
        </tr>

        <tr><td>&nbsp;</td></tr>
        <tr>
            <td><%=PageFlowUtil.generateSubmitButton("Save")%>&nbsp;
                <%=PageFlowUtil.generateButton("Cancel", PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL())%>
            </td>
        </tr>
    </table>
</form>

<script type="text/javascript">

    function pathRenderer(data, cellMetaData, record, rowIndex, colIndex, store)
    {
        if (record.browseURL != undefined)
        {
            return '<a href="' + record.browseURL + '">' + data + '</a>';
        }
        else
            return data;
    }

    function containerRenderer(data, cellMetaData, record, rowIndex, colIndex, store)
    {
        if (record.configureURL != undefined)
        {
            return '<a href="' + record.configureURL + '">' + data + '</a>';
        }
        else
            return data;
    }

    function configSelected(node)
    {
        if (node != undefined && node.attributes.configureURL != undefined)
        {
            window.location = node.attributes.configureURL;
        }
    }

    Ext.onReady(function(){
        var tree = new Ext.tree.ColumnTree({
            width: 625,
            height: 500,
            rootVisible:false,
            autoScroll:true,
            title: 'File Roots',
            renderTo: 'viewsGrid',

            columns:[{
                header:'Container',
                width:330,
                dataIndex:'name'
            },{
                header:'Path',
                width:220,
                dataIndex:'path'
            },{
                header:'Default',
                width:75,
                dataIndex:'default'
            }],

            tbar: [
                {text:'Expand All', tooltip: {text:'Expands all containers', title:'Expand All'}, listeners:{click:function(button, event) {tree.expandAll();}}},'-',
                {text:'Collapse All', tooltip: {text:'Collapses all containers', title:'Collapse All'}, listeners:{click:function(button, event) {tree.collapseAll();}, scope:this}},'-',
                {text:'Configure Selected', tooltip: {text:'Configure settings for the selected root', title:'Configure Selected'}, listeners:{click:function(button, event) {configSelected(tree.getSelectionModel().getSelectedNode());}, scope:this}},'-',
            ],

            loader: new Ext.tree.TreeLoader({
                dataUrl: LABKEY.ActionURL.buildURL("filecontent", "fileContentSummary", this.container),
                uiProviders:{
                    'col': Ext.tree.ColumnNodeUI
                }
            }),

            listeners: {dblclick: function(node){configSelected(node);}},

            root: new Ext.tree.AsyncTreeNode({
                text:'Container'
            })
        });
    });
</script>

<%
    WebPartView.startTitleFrame(out, "Summary view for File Roots");
%>
<table>
    <tr><td>File roots, named file sets and pipeline roots can be viewed on a folder basis. The 'Default' column
        indicates whether the root is derived from the site default root (set above) or has been overriden. To view or
        manage files, click on the path link. To configure the root, select a root and click on the configure button in the toolbar.
    </td></tr>
    <tr><td>&nbsp;</td></tr>
    <tr><td><div id="viewsGrid" class="extContainer"></div></td></tr>
</table>
<%
    WebPartView.endTitleFrame(out);
%>



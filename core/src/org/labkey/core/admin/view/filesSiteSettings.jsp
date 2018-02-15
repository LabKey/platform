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
<%@ page import="org.apache.commons.lang3.StringUtils"%>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.admin.FileListAction" %>
<%@ page import="org.labkey.core.admin.FileSettingsForm" %>
<%@ page import="org.labkey.core.admin.FilesSiteSettingsAction" %>
<%@ page import="org.labkey.api.premium.PremiumService" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
    FileSettingsForm bean = ((JspView<FileSettingsForm>)HttpView.currentView()).getModelBean();
%>

<labkey:errors/>
<labkey:form action="<%=h(buildURL(FilesSiteSettingsAction.class))%>" method="post">
    <input type="hidden" name="upgrade" value="<%=bean.isUpgrade()%>">
    <table width="80%">
        <tr><td colspan="2" class="labkey-announcement-title"><span>Site-Level File Root</span></td></tr>
        <tr><td colspan="2" class="labkey-title-area-line"></td></tr>

        <% if (bean.isUpgrade()) { %>
        <tr><td colspan="2">Set the site-level root for this server installation, or use the default provided. If the
            location does not exist, LabKey Server will create it for you.<br/><br/>
            When a site-level file root is set, each folder for every project has a corresponding subdirectory in the file system.
            LabKey Server allows you to upload and process your data files, including flow, proteomics and
            study-related files. By default, LabKey stores your files in a standard directory structure. You can
            override this location for each project if you wish.
            </td></tr>
        <% } else { %>
        <tr><td colspan="2">When a site-level file root is set, each folder for every project has a corresponding subdirectory in the file system.
            If the root is changed, all files will be automatically moved to the new location.</td></tr>
        <% } %>
        <tr><td>&nbsp;</td></tr>
        
        <tr><td class="labkey-form-label">Site-Level&nbsp;File&nbsp;Root&nbsp;<%=helpPopup("File Root", "Set a site-level file root. " +
                "When a site-level file root is set, each folder for every project has a corresponding subdirectory in the file system." +
                " A site-level file root may be overridden at the project level from 'Project Settings'.")%></td>
            <td><input type="text" name="rootPath" size="64" value="<%=h(bean.getRootPath())%>"></td>
        </tr>
        <% if (AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_USER_FOLDERS)) {%>
        <tr>
            <td class="labkey-form-label">Home&nbsp;Directory&nbsp;File&nbsp;Root&nbsp;<%=helpPopup("User File Root", "Set the root folder for users personal folders. ")%></td>
            <td><input type="text" name="userRootPath" size="64" value="<%=h(StringUtils.defaultIfBlank(bean.getUserRootPath(), ""))%>"></td>
        </tr>
        <%}%>

        <tr>
        <%
            if (bean.isUpgrade()) {
        %>
            <tr><td>&nbsp;</td></tr>
            <td><%= button("Next").submit(true) %></td>
        <%
            } else {
        %>
            <tr><td colspan="2" class="labkey-announcement-title"><span>Alternative Webfiles Root</span></td></tr>
            <tr><td colspan="2" class="labkey-title-area-line"></td></tr>
            <tr><td colspan="2">Site level setting to enable/disable alternative webdav tree: _webfiles.</td></tr>

            <tr><td class="labkey-form-label">Enable _webfiles<%=helpPopup("_webfiles", "Alternative webdav tree with a compact representation of file root contents and child containers.")%></td>
                <td><input type=checkbox id="webfilesEnabled" name="webfilesEnabled" <%=checked(bean.isWebfilesEnabled())%> style="margin-left: 10px;"></td>
            </tr>

        <%
            if (PremiumService.get().isDisableFileUploadSupported()) {
        %>
            <tr><td colspan="2" class="labkey-announcement-title"><span>Disable file upload</span></td></tr>
            <tr><td colspan="2" class="labkey-title-area-line"></td></tr>
            <tr><td colspan="2">Site level setting to enable/disable file upload.</td></tr>

            <tr><td class="labkey-form-label">Disable file upload<%=helpPopup("Disable file upload", "If checked, file upload function through file browser and webdav will be disabled.")%></td>
                <td><input type=checkbox id="fileUploadDisabled" name="fileUploadDisabled" <%=checked(bean.isFileUploadDisabled())%> style="margin-left: 10px;"></td>
            </tr>
        <%
        }
        %>

        <tr><td>&nbsp;</td></tr>
        <td><%= button("Save").submit(true) %>&nbsp;
                    <%= button("Cancel").href(urlProvider(AdminUrls.class).getAdminConsoleURL()) %>
                </td>
        <%
            }
        %>
        </tr>


    </table>
</labkey:form>

<script type="text/javascript">
    var isUpgrade = <%=bean.isUpgrade()%>;

    Ext4.onReady(function(){

        // show the file root browser
        if (!isUpgrade)
        {
            Ext4.QuickTips.init();

            var store = Ext4.create('Ext.data.TreeStore', {
                fields: ['name', 'path', 'default', 'browseURL', 'configureURL'],
                proxy: {
                    type: 'ajax',
                    extraParams: {excludeWorkbooksAndTabs: true},
                    url: LABKEY.ActionURL.buildURL("filecontent", "fileContentSummary", this.container)
                },
                folderSort: true,
                autoLoad: true
            });

            var tree = Ext4.create('Ext.tree.Panel', {
                useArrows: true,
                rootVisible: false,
                store: store,
                multiSelect: false,
                singleExpand: false,

                autoScroll:true,
                columns:[{
                    xtype: 'treecolumn',
                    header:'Project',
                    width:330,
                    dataIndex:'name',
                    renderer: function(val){
                        return Ext4.util.Format.htmlEncode(val);
                    }
                },{
                    header:'Directory',
                    width:420,
                    dataIndex:'path',
                    renderer: 'htmlEncode'
                },{
                    header:'Default',
                    width:75,
                    dataIndex:'default'
                }],
                tbar: [{
                    text:'Expand All',
                    tooltip: {text:'Expands all containers', title:'Expand All'},
                    handler: function(button, event) {button.up('treepanel').expandAll();}
                },{
                    text:'Collapse All',
                    tooltip: {text:'Collapses all containers', title:'Collapse All'},
                    handler: function(button, event) {button.up('treepanel').collapseAll();}
                },{
                    text: 'Show Overridden Only',
                    tooltip: {text: 'Will show only nodes that have custom file or pipeline roots', title: 'Show Overridden Only'},
                    showOverridesOnly: false,
                    handler: function(button){
                        var tree = button.up('treepanel');
                        var showOverridesOnly = !button.showOverridesOnly;

                        if (showOverridesOnly){
                            button.setText('Show All');
                            button.setTooltip({
                                text: 'Will show all containers, including those that use a default file root',
                                title: 'Show All'
                            });
                        }
                        else {
                            button.setText('Show Overridden Only');
                            button.setTooltip({
                                text: 'Will show only nodes that have custom file or pipeline roots',
                                title: 'Show Overridden Only'
                            });
                        }
                        button.showOverridesOnly = showOverridesOnly;

                        tree.store.proxy.extraParams.showOverridesOnly = showOverridesOnly;
                        tree.store.load();
                    }
                },{
                    text:'Configure Selected',
                    tooltip: {text:'Configure settings for the selected root', title:'Configure Selected'},
                    listeners:{
                        click:function(button, event) {
                            var selected = tree.getSelectionModel().getSelection();
                            if (selected.length){
                                var node = selected[0];
                                if (node.get('configureURL')){
                                    window.location = node.get('configureURL');
                                }
                            }
                            else {
                                Ext4.Msg.alert('Error', 'No nodes selected');
                            }
                        },
                        scope:this
                    }
                },{
                    text:'Browse Selected',
                    tooltip: {text:'Browse files from the selected root', title:'Browse Selected'},
                    listeners:{
                        click:function(button, event) {
                            var tree = button.up('treepanel');
                            var selected = tree.getSelectionModel().getSelection();
                            if (selected.length){
                                tree.browseSelected(selected[0]);
                            }
                            else {
                                Ext4.Msg.alert('Error', 'No nodes selected');
                            }
                        }
                    }
                }],
                listeners: {
                    itemdblclick: function(panel, node, item){
                        this.browseSelected(node);
                    }
                },
                browseSelected: function(node){
                    if (node.get('browseURL')){
                        window.location = node.get('browseURL');
                    }
                }
        });

            var panel = Ext4.create('Ext.panel.Panel', {
                layout: 'fit',
                renderTo: 'viewsGrid',
                items: [tree],
                height: 500
            });
        }
    });
</script>

<% if (!bean.isUpgrade()) { %>
    <table width="80%">
        <tr><td colspan="2" class="labkey-announcement-title"><span>Summary View for File Directories</span></td></tr>
        <tr><td colspan="2" class="labkey-title-area-line"></td></tr>
        <tr><td>File directories, named file sets and pipeline directories can be viewed on a project/folder basis. The 'Default' column for @files
            indicates whether the directory is derived from the site-level file root (set above) or has been overridden. The 'Default' column for @pipeline
            indicates whether the directory is the same as the @files directory or has been overridden. To view or
            manage files in a directory, double click on a row or click on the 'Browse Selected' button. To configure an
            @file or an @pipeline directory, select the directory and click on the 'Configure Selected' button in the toolbar.
        </td></tr>
        <tr><td>For a complete list of all file paths referenced in the LabKey database, please use the <a href="<%=new ActionURL(FileListAction.class, ContainerManager.getRoot())%>">Files List</a>.
        </td></tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td><div id="viewsGrid" class="extContainer"></div></td></tr>
    </table>
<% } %>



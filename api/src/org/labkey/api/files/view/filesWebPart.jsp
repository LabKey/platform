<%
/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.files.view.FilesWebPart" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.premium.PremiumService" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("File");
    }
%>
<%
    JspView<FilesWebPart.FilesForm> me = (JspView) HttpView.currentView();
    FilesWebPart.FilesForm bean = me.getModelBean();
    Container c = getContainer();

    ActionURL projConfig = urlProvider(AdminUrls.class).getProjectSettingsFileURL(c);
    int height = null == bean.getHeight() ? 350 : bean.getHeight();

    if (!bean.isEnabled())
    {
%>
    File sharing has been disabled for this project. Sharing can be configured from the <a href="<%=projConfig%>">project settings</a> view.
<%
    }
    else if (!bean.isRootValid(c))
    {
        if (bean.isCloudRootPath())
        {
%>
    <span class="labkey-error">
        The cloud config for this web part is invalid. It may not exist or may have been configured incorrectly.<br>
        <%=text("Contact your administrator to address this problem.")%>
    </span>
<%
        }
        else
        {
%>
    <span class="labkey-error">
        The file root for this folder is invalid. It may not exist or may have been configured incorrectly.<br>
        <%=text(c.hasPermission(getUser(), AdminPermission.class) ? "File roots can be configured from the <a href=\"" + projConfig + "\">project settings</a> view." : "Contact your administrator to address this problem.")%>
    </span>
<%
        }
    }
    else
    {
%>
<div id="<%=h(bean.getContentId())%>"></div>
<script type="text/javascript">
    Ext4.onReady(function() {
        var buttonActions = [<% String sep = ""; for(FilesWebPart.FilesForm.actions action  : bean.getButtonConfig()) {%><%=text(sep)%>'<%=text(action.name())%>'<% sep = ","; }%>];
        var startDirectory;

        <% if (bean.getDirectory() != null) { %>
        startDirectory = <%=PageFlowUtil.jsString(bean.getDirectory().toString())%>;
        <% } %>

        var config = {
            renderTo: <%=q(bean.getContentId())%>,
            containerPath: <%=q(c.getPath())%>,
            fileSystem: Ext4.create('File.system.Webdav', {
                rootPath: <%=q(bean.getRootPath())%>,
                rootOffset: <%=q(bean.getRootOffset())%>,
                rootName: 'fileset'
            }),
            startDirectory: startDirectory,
            height: <%= height %>,
            folderTreeOptions: {
                hidden: <%=!bean.isShowFolderTree()%>,
                collapsed: <%=bean.isFolderTreeCollapsed()%>
            },
            disableGeneralAdminSettings: <%=bean.isDisableGeneralAdminSettings()%>,
            showDetails: <%=bean.isShowDetails()%>,
            expandUpload: <%=bean.isExpandFileUpload()%>,
            isPipelineRoot: <%=bean.isPipelineRoot()%>,
            adminUser: <%=c.hasPermission(getUser(), AdminPermission.class)%>,
            statePrefix: <%=q(bean.getStatePrefix())%>,
            actions: buttonActions,
            disableFileUpload: <%=PremiumService.get().isFileUploadDisabled()%>,
            autoResize: {
                skipHeight: <%=!bean.isAutoResize()%>,
                offsetY: 80,
                overrideMinWidth: true
            }
        };

        <% if (bean.isListing()) { %>
        Ext4.apply(config, {
            allowSelection: false,
            expandUpload: false,
            useServerActions: false,
            useServerFileProperties: false,
            showColumnHeaders: false,
            showFolderTree: false,
            showToolbar: true,
            showUpload: true,
            listDirectories: true,
            useNarrowUpload: true,
            disableContextMenu: true,
            actions: ['parentFolder', 'refresh', <%=c.hasPermission(getUser(), InsertPermission.class)%> ? 'upload' : '',  'manage'],
            columns: ['iconfacls', 'name'],
            folderTreeOptions: {
                hidden: true,
                collapsed: true
            }
        });
        <% } %>

        Ext4.create('File.panel.Browser', config);
    });
</script>
<%
    }
%>

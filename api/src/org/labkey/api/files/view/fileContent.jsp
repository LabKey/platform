<%
/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!

  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
      resources.add(ClientDependency.fromPath("FileUploadField.js"));
      resources.add(ClientDependency.fromPath("StatusBar.js"));
      resources.add(ClientDependency.fromPath("fileBrowser.js"));
      resources.add(ClientDependency.fromPath("Reorderer.js"));
      resources.add(ClientDependency.fromPath("ToolbarDroppable.js"));
      resources.add(ClientDependency.fromPath("ToolbarReorderer.js"));
      resources.add(ClientDependency.fromPath("ActionsAdmin.js"));
      resources.add(ClientDependency.fromPath("PipelineAction.js"));
      resources.add(ClientDependency.fromPath("FileProperties.js"));
      resources.add(ClientDependency.fromPath("FileContent.js"));
      return resources;
  }
%>

<style type="text/css">
    .x-layout-mini
    {
        display: none;
    }
</style>

<%
    FilesWebPart.FilesForm bean = (FilesWebPart.FilesForm)HttpView.currentModel();
    Container c = getContainer();

    ActionURL projConfig = c.isProject() ? urlProvider(AdminUrls.class).getProjectSettingsFileURL(c) : urlProvider(AdminUrls.class).getFolderManagementFileURL(c);
    int height = 350;
%>

<%  if (!bean.isEnabled()) { %>

    File sharing has been disabled for this <%=h(c.getContainerNoun())%>. Sharing can be configured from the <a href="<%=projConfig%>"><%=h(c.getContainerNoun())%> settings</a> view.

<%  } else if (!bean.isRootValid()) { %>

    <span class="labkey-error">
        The file root for this folder is invalid. It may not exist or may have been configured incorrectly.<br>
        <%=text(c.hasPermission(getUser(), AdminPermission.class) ? "File roots can be configured from the <a href=\"" + projConfig + "\">project settings</a> view." : "Contact your administrator to address this problem.")%>
    </span>

<%  } %>

<!-- Set a fixed height for this div so that the whole page doesn't relayout when the file browser renders into it -->
<div class="extContainer" style="height: <%= height %>px" id="<%=bean.getContentId()%>"></div>

<%  if (bean.isEnabled() && bean.isRootValid()) { %>

<script type="text/javascript">

    (function() {

        var loadFileContent = function() {

            function renderBrowser(rootPath, renderTo, isFolderTreeCollapsed, isPipelineRoot)
            {
                var autoResize = <%=bean.isAutoResize()%>;

                var buttonActions = [], prefix;

                <%
                for (FilesWebPart.FilesForm.actions action  : bean.getButtonConfig()) {
                %>
                    buttonActions.push('<%=action.name()%>');
                <%
                }
                %>

                var fileSystem = new LABKEY.FileSystem.WebdavFileSystem({
                    extraPropNames: ['description', 'actions'],

                    // extra props should model Ext.data.Field types
                    extraDataFields: [
                        {name: 'description', mapping: 'propstat/prop/description'},
                        {name: 'actionHref', mapping: 'propstat/prop/actions', convert : function (v, rec)
                        {
                            var result = [];
                            var actionsElements = Ext.DomQuery.compile('propstat/prop/actions').call(this, rec);
                            if (actionsElements.length > 0)
                            {
                                var actionElements = actionsElements[0].getElementsByTagName('action');
                                for (var i = 0; i < actionElements.length; i++)
                                {
                                    var action = new Object();
                                    var childNodes = actionElements[i].childNodes;
                                    for (var n = 0; n < childNodes.length; n++)
                                    {
                                        var childNode = childNodes[n];
                                        if (childNode.nodeName == 'message')
                                        {
                                            action.message = childNode.textContent || childNode.text;
                                        }
                                        else if (childNode.nodeName == 'href')
                                        {
                                            action.href = childNode.textContent || childNode.text;
                                        }
                                    }
                                    result[result.length] = action;
                                }
                            }
                            return result;
                        }}
                    ],
                    baseUrl:rootPath,
                    rootName:'fileset'
                });

                <%  if (bean.getStatePrefix() != null) { %>
                    prefix = '<%=bean.getStatePrefix()%>';
                <%  } %>

                var fileBrowser = new LABKEY.FilesWebPartPanel({
                    fileSystem: fileSystem,
                    helpEl:null,
                    resizable: false,
                    showAddressBar: <%=bean.isShowAddressBar()%>,
                    showFolderTree: <%=bean.isShowFolderTree()%>,
                    folderTreeCollapsed: isFolderTreeCollapsed,
                    expandFileUpload: <%=bean.isExpandFileUpload()%>,
                    disableGeneralAdminSettings: <%=bean.isDisableGeneralAdminSettings()%>,
                    showProperties: false,
                    showDetails: <%=bean.isShowDetails()%>,
                    containerPath: <%=q(c.getPath())%>,
                    allowChangeDirectory: true,
                    tbarItems: buttonActions,
                    isPipelineRoot: isPipelineRoot,
                    adminUser : <%=getContainer().hasPermission(getUser(), AdminPermission.class)%>,
                    statePrefix: prefix,
                    rootOffset: <%=PageFlowUtil.jsString(bean.getRootOffset())%>
                });

                var panel = new Ext.Panel({
                    layout: 'fit',
                    renderTo: renderTo,
                    border: false,
                    items: [fileBrowser],
                    height: <%= height %>,
                    boxMinHeight:<%= height %>,
                    boxMinWidth: 650
                });

                var _resize = function(w,h)
                {
                    LABKEY.ext.Utils.resizeToViewport(panel, w, h);
                };

                if (autoResize)
                {
                    Ext.EventManager.onWindowResize(_resize);
                    Ext.EventManager.fireWindowResize();
                }

                fileBrowser.start(<%=bean.getDirectory() != null ? q(bean.getDirectory().toString()) : ""%>);
            }

            /**
             * activate the Ext state manager (for directory persistence), but by default, make all components
             * not try to load state.
             */
            Ext.state.Manager.setProvider(new Ext.state.CookieProvider());
            Ext.override(Ext.Component,{
                stateful:false
            });

            Ext.BLANK_IMAGE_URL = LABKEY.contextPath + "/_.gif";
            Ext.QuickTips.init();

            renderBrowser(<%=q(bean.getRootPath())%>, <%=q(bean.getContentId())%>, <%=bean.isFolderTreeCollapsed()%>, <%=bean.isPipelineRoot()%>);
        };

        Ext.onReady(loadFileContent);

    })();

</script>
<%  } %>

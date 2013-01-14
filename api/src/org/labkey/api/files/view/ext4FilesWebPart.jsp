<%
/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%!

  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<ClientDependency>();
      resources.add(ClientDependency.fromFilePath("applet.js"));
      resources.add(ClientDependency.fromFilePath("FileUploadField.js"));
      resources.add(ClientDependency.fromFilePath("StatusBar.js"));
      resources.add(ClientDependency.fromFilePath("fileBrowser.js"));
      resources.add(ClientDependency.fromFilePath("Reorderer.js"));
      resources.add(ClientDependency.fromFilePath("ToolbarDroppable.js"));
      resources.add(ClientDependency.fromFilePath("ToolbarReorderer.js"));
      resources.add(ClientDependency.fromFilePath("ActionsAdmin.js"));
      resources.add(ClientDependency.fromFilePath("PipelineAction.js"));
      resources.add(ClientDependency.fromFilePath("FileProperties.js"));
      resources.add(ClientDependency.fromFilePath("FileContent.js"));

      resources.add(ClientDependency.fromFilePath("File"));

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
    ViewContext context = HttpView.currentContext();
    FilesWebPart.FilesForm bean = (FilesWebPart.FilesForm)HttpView.currentModel();

    Container c = context.getContainer();

    ActionURL projConfig = urlProvider(AdminUrls.class).getProjectSettingsFileURL(c);
    int height = 350;
%>

<%  if (!bean.isEnabled()) { %>

    File sharing has been disabled for this project. Sharing can be configured from the <a href="<%=projConfig%>">project settings</a> view.

<%  } else if (!bean.isRootValid()) { %>

    <span class="labkey-error">
        The file root for this folder is invalid. It may not exist or may have been configured incorrectly.<br>
        <%=c.hasPermission(context.getUser(), AdminPermission.class) ? "File roots can be configured from the <a href=\"" + projConfig + "\">project settings</a> view." : "Contact your administrator to address this problem."%>
    </span>

<%  } %>

<!-- Set a fixed height for this div so that the whole page doesn't relayout when the file browser renders into it -->
<div class="extContainer" style="height: <%= height %>px" id="<%=bean.getContentId()%>"></div>

<%  if (bean.isEnabled() && bean.isRootValid()) { %>

<script type="text/javascript">
    Ext4.onReady(function() {
        var fileSystem = Ext4.create('File.system.Webdav', {
            baseUrl  : <%=q(bean.getRootPath())%>,
            offsetUrl: <%=PageFlowUtil.jsString(bean.getRootOffset())%>,
            rootName:'fileset'
        });

        var fb = Ext4.create('File.panel.Browser', {
            adminUser : true,
            <%--renderTo : <%=q(bean.getContentId())%>,--%>
            renderTo : <%=q(bean.getContentId())%>,
            height: 350,
            showFolderTree: <%=bean.isShowFolderTree()%>,
            expandFolderTree: <%=!bean.isFolderTreeCollapsed()%>,
            disableGeneralAdminSettings: <%=bean.isDisableGeneralAdminSettings()%>,
            showDetails: <%=bean.isShowDetails()%>,
            <%--expandFileUpload: <%=bean.isExpandFileUpload()%>,--%>
            <%--disableGeneralAdminSettings: <%=bean.isDisableGeneralAdminSettings()%>,--%>
            <%--containerPath: <%=q(c.getPath())%>,--%>
            fileSystem: fileSystem,
            isWebPart: true,
            gridConfig : {
                selType : 'rowmodel'
            }
        });
    });
</script>

<%  } %>

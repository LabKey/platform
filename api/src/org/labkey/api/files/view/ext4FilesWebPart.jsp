<%
/*
 * Copyright (c) 2013 LabKey Corporation
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
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%!

  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
      resources.add(ClientDependency.fromFilePath("File"));
      return resources;
  }
%>
<%
    ViewContext context = HttpView.currentContext();
    FilesWebPart.FilesForm bean = (FilesWebPart.FilesForm)HttpView.currentModel();

    Container c = context.getContainer();

    ActionURL projConfig = urlProvider(AdminUrls.class).getProjectSettingsFileURL(c);
    int height = bean.getSize();
    if(height == 0)
        height = 350;
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
<div style="height: <%= height %>px" id="<%=h(bean.getContentId())%>"></div>
<%  if (bean.isEnabled() && bean.isRootValid()) { %>

<script type="text/javascript">
    Ext4.onReady(function() {
        var fileSystem = Ext4.create('File.system.Webdav', {
            baseUrl  : <%=q(bean.getRootPath())%>,
            offsetUrl: <%=q(bean.getRootOffset())%>,
            rootName : 'fileset'
        });

        var buttonActions = [];

        <%
        for (FilesWebPart.FilesForm.actions action  : bean.getButtonConfig()) {
        %>
        buttonActions.push('<%=text(action.name())%>');
        <%
        }
        %>

        var fb = Ext4.create('File.panel.Browser', {
            renderTo : <%=q(bean.getContentId())%>,
            containerPath : <%=q(c.getPath())%>,
            height: <%= height %>,
            showFolderTree: <%=bean.isShowFolderTree()%>,
            expandFolderTree: <%=!bean.isFolderTreeCollapsed()%>,
            disableGeneralAdminSettings: <%=bean.isDisableGeneralAdminSettings()%>,
            showDetails: <%=bean.isShowDetails()%>,
            expandUpload: <%=bean.isExpandFileUpload()%>,
            isPipelineRoot : <%=bean.isPipelineRoot()%>,
            adminUser : <%=getViewContext().getContainer().hasPermission(getViewContext().getUser(), AdminPermission.class)%>,
            fileSystem: fileSystem,
            tbarItems: buttonActions
        });

        var resize = function() {
            if (fb && fb.doLayout) { fb.doLayout(); }
        };

        Ext4.EventManager.onWindowResize(resize);
    });
</script>

<%  } %>

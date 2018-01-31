<%
/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.attachments.AttachmentDirectory" %>
<%@ page import="org.labkey.api.files.FileContentService" %>
<%@ page import="org.labkey.api.files.FileUrls" %>
<%@ page import="org.labkey.api.files.view.CustomizeFilesWebPartView" %>
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("File");
    }
%>
<%
    CustomizeFilesWebPartView me = (CustomizeFilesWebPartView) HttpView.currentView();
    CustomizeFilesWebPartView.CustomizeWebPartForm form = me.getModelBean();
    ViewContext ctx = getViewContext();
    ActionURL postUrl = form.getWebPart().getCustomizePostURL(ctx);
    FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
    Collection<AttachmentDirectory> attDirs = svc.getRegisteredDirectories(getContainer());

    List<String> cloudStoreNames = form.getEnabledCloudStores(getContainer());

    boolean small = false;
    boolean medium = false;
    boolean large = false;

    if (form.getSize() == 350)
        small = true;
    else if(form.getSize() == 650)
        medium = true;
    else
        large = true;

%>
<labkey:form action="<%=postUrl%>" method="POST" onsubmit="return setFileRoot();">
    You can configure this web part to show files from the default directory for this folder or
    from a directory configured by a site administrator.<br><br>

    <table>
        <tr>
            <td class="labkey-form-label">Title</td>
            <td><input type="text" name="title" style="margin: 1%" size="32" value="<%=h(form.getTitle())%>"> </td>
        </tr>

<%      if (HttpView.BODY.equals(form.getLocation())) { %>
            <tr>
                <td class="labkey-form-label">Folder Tree visible&nbsp;<%=PageFlowUtil.helpPopup("Folder Tree", "Checking this selection will display the folder tree by default. The user can toggle the show or hide state of the folder tree using a button on the toolbar.")%></td>
                <td><input type="checkbox" name="folderTreeVisible"<%=checked(form.isFolderTreeVisible())%>></td>
            </tr>
<%      } %>
        <tr>
            <td class="labkey-form-label">Webpart Size</td>
            <td>
                <input type="radio" name="size" value="350"<%=checked(small)%>/>Small<br>
                <input type="radio" name="size" value="650"<%=checked(medium)%>/>Medium<br>
                <input type="radio" name="size" value="1000"<%=checked(large)%>/>Large<br>
            </td>
        </tr>

        <tr><td></td></tr>
        <tr>
            <td class="labkey-form-label">File Root</td>
            <td>
                <input type="hidden" id="hidden-fileRoot" name="fileRoot">
                <div id="browserPanel" style="padding-bottom: 5px;"></div>
                <%          if (getContainer().hasPermission(getUser(), AdminOperationsPermission.class))
                {
                    ActionURL configUrl = urlProvider(FileUrls.class).urlShowAdmin(getContainer()); %>
                <a href="<%=h(configUrl)%>">Configure File Roots</a>
                <%          } %>

            </td>
        </tr>

        <tr><td></td><td><%= button("Submit").submit(true) %></td></tr>
    </table>
</labkey:form>

<script type="text/javascript">
    var fileTree;

    function setFileRoot() {
        if (fileTree) {
            var tree = fileTree.items.items[0];
            if (tree) {
                var selectedNodes = tree.getSelectionModel().getSelection();
                if (!selectedNodes || selectedNodes.length == 0) {
                    alert("Please select a node for File Root");
                    return false;
                }
                else {
                    var davNodeId = selectedNodes[0].data.id;
                    // trim the container prefix so that property can be exported/imported independent of container name
                    davNodeId = davNodeId.replace(LABKEY.container.path + '/', '');
                    document.getElementById("hidden-fileRoot").value = davNodeId;
                }
            }
        }
        return true;
    }

    Ext4.onReady(function() {
        var contextUrl = LABKEY.contextPath + "/_webdav";
        var containerPath = LABKEY.container.path;
        var rootPath = contextUrl + containerPath + '/';
        var rootOffset = null;
        var fileRootName = <%=q(form.getFileRoot())%>;
        if (fileRootName) {
            rootOffset = containerPath;
            if (fileRootName.substring(0, 1) !== '/')
                rootOffset += '/';
            rootOffset += fileRootName;
        }

        var fileSystem = Ext4.create('File.system.Webdav', {
            rootPath: rootPath,
            rootOffset: rootOffset,
            rootName: LABKEY.container.name
        });

        var config = {
            xtype : 'filebrowser',
            renderTo: 'browserPanel',
            itemId: 'browser',
            border : false,
            isWebDav  : true,
            useHistory: false,
            fileSystem : fileSystem,
            showUpload: false,
            minWidth: 350,
            height: 250,
            showFolderTreeOnly: true,
            folderTreeOptions: {
                hidden: false,
                collapsed: false,
                width: 350
            },
            expandToOffset: rootOffset !== null,
            useServerActions: false,
            listeners: {
                resize: function(vp) {
                    if (vp) {
                        var fb = vp.getComponent('browser');
                        if (fb) {
                            Ext4.defer(fb.detailCheck, 250, fb);
                        }
                    }
                }
            }
        };

        fileTree = Ext4.create('File.panel.Browser', config);
    });
</script>
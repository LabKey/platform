<%
/*
 * Copyright (c) 2009-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.files.FileContentService"%>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.util.FileUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="java.io.File" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    AdminController.FileManagementForm bean = ((JspView<AdminController.FileManagementForm>)HttpView.currentView()).getModelBean();

    // the default file root based on the root of the first ancestor with an override, or site root
    String defaultRoot = "";
    FileContentService service = ServiceRegistry.get().getService(FileContentService.class);
    File siteRoot = service.getSiteDefaultRoot();
    if (siteRoot != null)
    {
        File defaultRootFile = service.getDefaultRoot(getViewContext().getContainer(), false);
        if (defaultRootFile != null)
        {
            defaultRoot = FileUtil.getAbsoluteCaseSensitiveFile(defaultRootFile).getAbsolutePath();
        }
    }

    //b/c setting a custom file root potentially allows access to any files, we only allow
    //site admins to do this.  however, folder admin can disable sharing on a folder
    //if this folder already has a custom file root, only a site admin can make further changes
    User user = getViewContext().getUser();
    boolean canChangeFileSettings = getViewContext().getContainer().hasPermission(user, AdminPermission.class) || user.isSiteAdmin();
    if (AdminController.ProjectSettingsForm.FileRootProp.folderOverride.name().equals(bean.getFileRootOption()) && !getViewContext().getUser().isSiteAdmin())
    {
        canChangeFileSettings = false;
    }
    boolean canSetCustomFileRoot = getViewContext().getUser().isSiteAdmin();
%>

<%  if (bean.getConfirmMessage() != null) { %>
        <p class="labkey-message"><%= h(bean.getConfirmMessage()) %></p>
<%  } %>

<labkey:errors/>

<form action="" method="post">
    <%
        WebPartView.startTitleFrame(out, "Configure File Root ");
    %>

    <table>
        <tr><td></td></tr>
        <tr><td colspan="10">LabKey Server allows you to upload and process your data files, including flow, proteomics and study-related
            files. By default, LabKey stores your files in a standard directory structure. Site administrators can override this location for each
            folder if you wish.
        </td></tr>
        <tr><td></td></tr>
        <tr>
<%--
            <td class="labkey-form-label">File&nbsp;root&nbsp;<%=helpPopup("File root", "Set a project-level file root. " +
                "When a project-level file root is set, each folder for that project has a corresponding subdirectory in the file system.")%></td>
--%>
            <td>
                <table>
                    <tr><td><input <%=h(canChangeFileSettings ? "" : " disabled ")%>type="radio" name="fileRootOption" id="optionDisable" value="<%=AdminController.ProjectSettingsForm.FileRootProp.disable%>"
                                   <%=checked(AdminController.ProjectSettingsForm.FileRootProp.disable.name().equals(bean.getFileRootOption()))%>
                                   onclick="updateSelection();">
                        Disable file sharing for this <%=h(getViewContext().getContainer().getContainerNoun())%></td></tr>
                    <tr>
                        <td><input <%=h(canChangeFileSettings ? "" : " disabled ")%>type="radio" name="fileRootOption" id="optionSiteDefault" value="<%=AdminController.ProjectSettingsForm.FileRootProp.siteDefault%>"
                                   <%=checked(AdminController.ProjectSettingsForm.FileRootProp.siteDefault.name().equals(bean.getFileRootOption()))%>
                                   onclick="updateSelection();">
                            Use a default based on the site-level root</td>
                        <td><input type="text" id="rootPath" size="64" disabled="true" value="<%=h(defaultRoot)%>"></td>
                    </tr>
                    <tr>
                        <td><input <%=h(canChangeFileSettings && canSetCustomFileRoot ? "" : " disabled ")%>type="radio" name="fileRootOption" id="optionProjectSpecified" value="<%=AdminController.ProjectSettingsForm.FileRootProp.folderOverride%>"
                                   <%=checked(AdminController.ProjectSettingsForm.FileRootProp.folderOverride.name().equals(bean.getFileRootOption()))%>
                                   onclick="updateSelection();">
                            Use a <%=text(getViewContext().getContainer().getContainerNoun())%>-level file root</td>
                        <td><input type="text" id="folderRootPath" name="folderRootPath" size="64" value="<%=h(bean.getFolderRootPath())%>"></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr><td>&nbsp;</td></tr>
        <tr>
            <td><%=generateSubmitButton("Save")%></td>
        </tr>
    </table>
    <%
        WebPartView.endTitleFrame(out);
    %>
</form>

<script type="text/javascript">

    Ext4.onReady(function()
    {
        updateSelection();
    });

    function updateSelection()
    {
        if (document.getElementById('optionDisable').checked)
        {
            document.getElementById('folderRootPath').style.display = 'none';
            document.getElementById('rootPath').style.display = 'none';
        }
        if (document.getElementById('optionSiteDefault').checked)
        {
            document.getElementById('folderRootPath').style.display = 'none';
            document.getElementById('rootPath').style.display = '';
        }
        if (document.getElementById('optionProjectSpecified').checked)
        {
            document.getElementById('folderRootPath').style.display = '';
            document.getElementById('rootPath').style.display = 'none';
        }
    }
</script>


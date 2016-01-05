<%
/*
 * Copyright (c) 2009-2015 LabKey Corporation
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
<%@ page import="org.labkey.api.cloud.CloudStoreService"%>
<%@ page import="org.labkey.api.cloud.CloudUrls" %>
<%@ page import="org.labkey.api.files.FileContentService" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.util.FileUtil" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="java.io.File" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Collections" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.files.FileUrls" %>
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
        File defaultRootFile = service.getDefaultRoot(getContainer(), false);
        if (defaultRootFile != null)
        {
            defaultRoot = FileUtil.getAbsoluteCaseSensitiveFile(defaultRootFile).getAbsolutePath();
        }
    }

    //b/c setting a custom file root potentially allows access to any files, we only allow
    //site admins to do this.  however, folder admin can disable sharing on a folder
    //if this folder already has a custom file root, only a site admin can make further changes
    User user = getUser();
    boolean canChangeFileSettings = getContainer().hasPermission(user, AdminPermission.class) || user.isSiteAdmin();
    if (AdminController.ProjectSettingsForm.FileRootProp.folderOverride.name().equals(bean.getFileRootOption()) && !user.isSiteAdmin())
    {
        canChangeFileSettings = false;
    }
    boolean canSetCustomFileRoot = user.isSiteAdmin();

    CloudStoreService cloud = ServiceRegistry.get(CloudStoreService.class);
    Collection<String> storeNames = Collections.emptyList();
    if (cloud != null)
    {
        storeNames = cloud.getCloudStores();
    }
%>

<%  if (bean.getConfirmMessage() != null) { %>
        <p class="labkey-message"><%= h(bean.getConfirmMessage()) %></p>
<%  } %>

<labkey:errors/>

<labkey:form action="" method="post">
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
            <td>
                <table>
                    <tr><td><input <%=h(canChangeFileSettings ? "" : " disabled ")%>type="radio" name="fileRootOption" id="optionDisable" value="<%=AdminController.ProjectSettingsForm.FileRootProp.disable%>"
                                   <%=checked(AdminController.ProjectSettingsForm.FileRootProp.disable.name().equals(bean.getFileRootOption()))%>
                                   onclick="updateSelection();">
                        Disable file sharing for this <%=h(getContainer().getContainerNoun())%></td></tr>
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
                            Use a <%=text(getContainer().getContainerNoun())%>-level file root</td>
                        <td><input type="text" id="folderRootPath" name="folderRootPath" size="64" value="<%=h(bean.getFolderRootPath())%>"></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td>
                <br>
                <a href="<%=h(urlProvider(FileUrls.class).urlShowAdmin(getContainer()))%>">Manage Additional File Roots</a>
            </td>
        </tr>
    </table>


    <%
        if (cloud != null)
        {
            WebPartView.startTitleFrame(out, "Enable Cloud Stores ");
    %>

    <table>
        <tr><td></td></tr>
        <tr><td colspan="10">
            LabKey Server can store files in a cloud storage provider's blob store.
            Enable or disable the available cloud stores within this folder using the checkboxes below.
            The cloud module must be enabled within this folder and
            cloud accounts and stores must be first configured in the
            <a href="<%=PageFlowUtil.urlProvider(CloudUrls.class).adminURL()%>">site admin preferences</a>
            prior to enabling them within a folder.
            <br>
            <em>Cloud stores disabled at the site-level cannot be enabled within a folder.</em>
        </td></tr>
        <tr><td></td></tr>

        <%
            if (storeNames.isEmpty())
            {
                %><tr><td><em>No cloud stores have been created in the site admin preferences.</em></td></tr><%
            }
            else
            {
                for (String storeName : storeNames)
                {
                    boolean siteEnabled = cloud.isEnabled(storeName);
                    boolean containerEnabled = cloud.isEnabled(storeName, getContainer());
                    String id = "cloudStore_" + UniqueID.getRequestScopedUID(getViewContext().getRequest());
        %>
        <tr>
            <td <%=text(siteEnabled ? "" : "class='labkey-disabled'")%>>
                <input type="checkbox" id="<%=h(id)%>" name="enabledCloudStore" value="<%=h(storeName)%>" <%=checked(containerEnabled)%> <%=disabled(!siteEnabled)%>>
                <label for="<%=h(id)%>"><%=h(storeName)%></label>
            </td>
        </tr>
        <%
                }
            }
        %>

    </table>
    <% } %>

    <tr><td><%= button("Save").submit(true) %></td></tr>

    <%
        WebPartView.endTitleFrame(out);
    %>
</labkey:form>

<script type="text/javascript">
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
    updateSelection();
</script>


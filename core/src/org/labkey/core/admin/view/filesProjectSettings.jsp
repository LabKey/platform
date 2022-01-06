<%
/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.files.FileUrls" %>
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.core.admin.AdminController.FileRootProp" %>
<%@ page import="org.labkey.core.admin.AdminController.MigrateFilesOption" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    AdminController.FileManagementForm bean = ((JspView<AdminController.FileManagementForm>)HttpView.currentView()).getModelBean();

    // Issue 38439: get a copy of the original form bean in the error reshow case to use for toggling the migrateFilesRow message and select input
    AdminController.FileRootsForm origBean = new AdminController.FileRootsForm();
    AdminController.setFormAndConfirmMessage(getViewContext(), origBean);

    FileContentService service = FileContentService.get();
    if (null == service)
        throw new IllegalStateException("FileContentService not found.");

    // the default file root based on the root of the first ancestor with an override, or site root
    String currentCloudRootName = bean.getCloudRootName();
    FileContentService.DefaultRootInfo defaultRootInfo = service.getDefaultRootInfo(getContainer());
    String defaultRoot = defaultRootInfo.getPrettyStr();

    //b/c setting a custom file root potentially allows access to any files, we only allow
    //site admins (i.e. AdminOperationsPermission) to do this.  however, folder admin can disable sharing on a folder
    //if this folder already has a custom file root, only a site admin can make further changes
    User user = getUser();
    boolean hasAdminPerm = getContainer().hasPermission(user, AdminPermission.class);
    boolean hasAdminOpsPerm = getContainer().hasPermission(user, AdminOperationsPermission.class);
    boolean canChangeFileSettings = hasAdminPerm || hasAdminOpsPerm;
    if (AdminController.FileRootProp.folderOverride.name().equals(bean.getFileRootOption()) && !hasAdminOpsPerm)
    {
        canChangeFileSettings = false;
    }
    String originalFileRootOption =
            FileRootProp.disable.name().equals(origBean.getFileRootOption()) ? "Disabled" :
            FileRootProp.siteDefault.name().equals(origBean.getFileRootOption()) ? "Default based on project root" :
            FileRootProp.folderOverride.name().equals(origBean.getFileRootOption()) ? origBean.getFolderRootPath() :
            FileRootProp.cloudRoot.name().equals(origBean.getFileRootOption()) ? "/@cloud/" + origBean.getCloudRootName() : "";
    boolean isCurrentFileRootOptionDisable = FileRootProp.disable.name().equals(origBean.getFileRootOption());

    CloudStoreService cloud = CloudStoreService.get();
    Map<String, CloudStoreService.StoreInfo> storeInfos = cloud != null ? cloud.getStoreInfos(getContainer()) : Collections.emptyMap();

    String folderSetup = getActionURL().getParameter("folderSetup");
    boolean isFolderSetup = "true".equalsIgnoreCase(folderSetup);
    String cancelButtonText = isFolderSetup ? "Next" : "Cancel";
    URLHelper cancelButtonUrl = isFolderSetup && getActionURL().getReturnURL() != null
            ? getActionURL().getReturnURL()
            : getContainer().getStartURL(getUser());
    ActionURL redirectToPipeline = urlProvider(PipelineUrls.class).urlBegin(getContainer());
    boolean isCurrentFileRootCloud = FileRootProp.cloudRoot.name().equals(bean.getFileRootOption());
    boolean isCurrentFileRootManaged = !(isCurrentFileRootCloud &&
                                                null != storeInfos.get(bean.getCloudRootName()) && !storeInfos.get(bean.getCloudRootName()).isLabKeyManaged());
    String fileRootText = getContainer().isProject() ? "site-level file root" : getContainer().getParsedPath().size() == 2 ? "file root of the parent project" : "file root of the parent folder";
%>

<%  if (bean.getConfirmMessage() != null) { %>
        <p class="labkey-message">
            <%= h(bean.getConfirmMessage()) %>
            <% if (null != bean.getMigrateFilesOption() && !MigrateFilesOption.leave.name().equals(bean.getMigrateFilesOption()) && !FileRootProp.disable.name().equals(bean.getFileRootOption())) { %>
                <a id="redirectToPipeline" class="labkey-text-link" href="<%=h(redirectToPipeline)%>">View Pipeline Job</a>
            <% } %>
        </p>
<%  } %>

<labkey:errors/>

<labkey:form action="" method="post">
    <table>
        <tr><td colspan="10"><span><b><i> File Root</i></b></span></td></tr>
        <tr><td colspan="10">LabKey Server allows you to upload and process your data files, including flow, proteomics and study-related
            files. By default, LabKey stores your files in a standard directory structure. Site administrators can override this location for each
            folder if you wish.
        </td></tr>
        <tr><td></td></tr>
        <tr>
            <td>
                <table>
                    <tr style="height: 1.75em">
                        <td><input <%=h(canChangeFileSettings ? "" : " disabled ")%>
                                type="radio" name="fileRootOption" id="optionDisable" value="<%=FileRootProp.disable%>"
                                <%=checked(FileRootProp.disable.name().equals(bean.getFileRootOption()))%>
                                onclick="updateSelection(<%=h(!FileRootProp.disable.name().equals(bean.getFileRootOption()))%>);">
                            Disable file sharing for this <%=h(getContainer().getContainerNoun())%></td></tr>
                    <tr style="height: 1.75em">
                        <td><input <%=h(canChangeFileSettings ? "" : " disabled ")%>
                                type="radio" name="fileRootOption" id="optionSiteDefault" value="<%=FileRootProp.siteDefault%>"
                                <%=checked(FileRootProp.siteDefault.name().equals(bean.getFileRootOption()))%>
                                onclick="updateSelection(<%=h(!FileRootProp.siteDefault.name().equals(bean.getFileRootOption()))%>);">
                            Use a default based on the <%=h(fileRootText)%>:
                            <input type="text" id="rootPath" size="64" disabled="true" value="<%=h(defaultRoot)%>"></td>
                    </tr>
                    <tr style="height: 1.75em">
                        <td><input <%=h(canChangeFileSettings && hasAdminOpsPerm ? "" : " disabled ")%>
                                type="radio" name="fileRootOption" id="optionProjectSpecified" value="<%=FileRootProp.folderOverride%>"
                                <%=checked(FileRootProp.folderOverride.name().equals(bean.getFileRootOption()))%>
                                onclick="updateSelection(<%=h(!FileRootProp.folderOverride.name().equals(bean.getFileRootOption()))%>);">
                            Use a <%=text(getContainer().getContainerNoun())%>-level file root
                            <input type="text" id="folderRootPath" name="folderRootPath" size="64" onchange="onRootChange()" value="<%=h(bean.getFolderRootPath())%>"></td>
                    </tr>
                    <% if (cloud != null) { %>
                    <tr style="height: 1.75em">
                        <td><input <%=h(canChangeFileSettings && hasAdminOpsPerm ? "" : " disabled ")%>
                                type="radio" name="fileRootOption" id="optionCloudRoot" value="<%=FileRootProp.cloudRoot%>"
                                <%=checked(FileRootProp.cloudRoot.name().equals(bean.getFileRootOption()))%>
                                onclick="updateSelection(<%=h(!FileRootProp.cloudRoot.name().equals(bean.getFileRootOption()))%>);">
                            Use cloud-based file storage
                            <select name="cloudRootName" id="cloudRootName" onchange="updateSelection(true);">
                                <% for (CloudStoreService.StoreInfo storeInfo : storeInfos.values())
                                    if (storeInfo.isEnabledInContainer())
                                    { %>
                                        <option value="<%=h(storeInfo.getName())%>" <%=selected(storeInfo.getName().equalsIgnoreCase(currentCloudRootName))%>>
                                            <%=h(storeInfo.getName())%>
                                        </option>
                                <%  } %>
                            </select>
                        </td>
                    </tr>
                    <% } %>
                    <tr style="height: 1.75em" id="migrateFilesRow">
                        <td>
                            <span style="color: #FF0000">Proposed File Root change from '<%=h(originalFileRootOption)%>'.</span> Existing files:
                            <select name="migrateFilesOption" id="migrateFilesOption" <%=h(canChangeFileSettings ? "" : " disabled ")%>>
                                <option value="<%=MigrateFilesOption.leave%>" selected>
                                    Not copied or moved
                                </option>
                                <option value="<%=MigrateFilesOption.copy%>">
                                    Copied to new location
                                </option>
                                <option id="migrateMoveOption" value="<%=MigrateFilesOption.move%>">
                                    Moved to new location
                                </option>
                            </select>
                            <span id="notifyAboutPipeline" style="color: #FF0000"><br>
                                Changing File Root to cloud-based storage disables any pipeline override. Files at that location will remain.<br>
                                Not all pipeline providers support using cloud-based storage.
                            </span>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        <% if (hasAdminOpsPerm && !isFolderSetup && !isCurrentFileRootCloud) { %>
        <tr>
            <td>
                <a id="manageAdditionalFileRoots" class="labkey-text-link" href="<%=h(urlProvider(FileUrls.class).urlShowAdmin(getContainer()))%>">Manage Additional File Roots</a>
            </td>
        </tr>
        <% } %>
    </table>

    <%
        if (cloud != null)
        {
    %>

    <table style="margin-top: 10px">
        <tr><td colspan="10"><span><b><i>Cloud Stores</i></b></span></td></tr>
        <tr><td colspan="10">
            LabKey Server can store files in a cloud storage provider's blob store.
            Enable or disable the available cloud stores within this folder using the checkboxes below.
            The cloud module must be enabled within this folder and
            cloud accounts and stores must be first configured in the
            <a href="<%=h(urlProvider(CloudUrls.class).urlAdmin())%>">site admin preferences</a>
            prior to enabling them within a folder.
            <br>
            <em>Cloud stores disabled at the site-level cannot be enabled within a folder.</em>
        </td></tr>
        <tr><td></td></tr>

        <%
            if (storeInfos.isEmpty())
            {
                %><tr><td><em>No cloud stores have been created in the site admin preferences.</em></td></tr><%
            }
            else
            {
                for (CloudStoreService.StoreInfo storeInfo : storeInfos.values())
                {
                    String id = "cloudStore_" + getRequestScopedUID();
        %>
        <tr>
            <td <%=text(storeInfo.isEnabled() ? "" : "class='labkey-disabled'")%>>
                <input type="checkbox" id="<%=h(id)%>" name="enabledCloudStore" value="<%=h(storeInfo.getName())%>" <%=checked((storeInfo.isEnabledInContainer()))%> <%=disabled(!storeInfo.isEnabled())%>>
                <label for="<%=h(id)%>"><%=h(storeInfo.getName())%></label>
            </td>
        </tr>
        <%
                }
            }
        %>

    </table>
    <% }

    if (isFolderSetup)
    {
    %>
    <br/>
    <em>You must <b>Save</b> any changes before clicking <b>Next</b></em>.
    <br/>
    <%
    }
    %>
    <labkey:button text="Save"/>
    <labkey:button text="<%=cancelButtonText%>" href="<%=cancelButtonUrl%>"/>
    <input type="hidden" value="true">
</labkey:form>

<script type="text/javascript">
    function updateSelection(isChange)
    {
        var cloudRootName = document.getElementById('cloudRootName');
        var optionDisableChecked = document.getElementById('optionDisable').checked;
        if (optionDisableChecked)
        {
            document.getElementById('folderRootPath').style.display = 'none';
            document.getElementById('rootPath').style.display = 'none';
            if (cloudRootName)
                cloudRootName.style.display = 'none';
        }
        if (document.getElementById('optionSiteDefault').checked)
        {
            document.getElementById('folderRootPath').style.display = 'none';
            document.getElementById('rootPath').style.display = '';
            if (cloudRootName)
                document.getElementById('cloudRootName').style.display = 'none';
        }
        if (document.getElementById('optionProjectSpecified').checked)
        {
            document.getElementById('folderRootPath').style.display = '';
            document.getElementById('rootPath').style.display = 'none';
            if (cloudRootName)
                document.getElementById('cloudRootName').style.display = 'none';
        }
        var optionCloudRoot = document.getElementById('optionCloudRoot');
        if (optionCloudRoot && optionCloudRoot.checked)
        {
            document.getElementById('folderRootPath').style.display = 'none';
            document.getElementById('rootPath').style.display = 'none';
            if (cloudRootName)
                document.getElementById('cloudRootName').style.display = '';
        }

        updateMigrateFiles(isChange, optionDisableChecked);
    }

    function onRootChange()
    {
        var value = document.getElementById('folderRootPath').value;
        if (!value)
        {
            return;
        }

        var isChangeFromExisting = value != <%=q(bean.getFolderRootPath())%>;
        var optionDisableChecked = document.getElementById('optionDisable').checked;
        updateMigrateFiles(isChangeFromExisting, optionDisableChecked);
    }

    function updateMigrateFiles(isChange, optionDisableChecked)
    {
        var migrateFiles = document.getElementById('migrateFilesRow');
        if (!migrateFiles) {
            return;
        }

        var optionCloudRoot = document.getElementById('optionCloudRoot');
        var notifyAboutPipeline = document.getElementById('notifyAboutPipeline');
        if (isChange && !optionDisableChecked && !<%=isFolderSetup || isCurrentFileRootOptionDisable%>)
        {
            migrateFiles.style.display = '';
            if (notifyAboutPipeline)
            {
                if (optionCloudRoot && optionCloudRoot.checked)
                    notifyAboutPipeline.style.display = '';
                else
                    notifyAboutPipeline.style.display = 'none';
            }
            var migrateMoveOption = document.getElementById('migrateMoveOption');
            if (migrateMoveOption)
            {
                var isNewCloudRootManaged = false;  // TODO: must we prevent moving when switching *TO* unmanaged cloud root?
                if ((optionCloudRoot && optionCloudRoot.checked && isNewCloudRootManaged) || <%=!isCurrentFileRootManaged%>)
                {
                    migrateMoveOption.setAttribute('hidden', '');
                }
                else
                {
                    migrateMoveOption.removeAttribute('hidden');
                }
            }
        }
        else
        {
            migrateFiles.style.display = 'none';
            if (notifyAboutPipeline)
                notifyAboutPipeline.style.display = 'none';
        }
    }

    function onRootChange()
    {

        var value = document.getElementById('folderRootPath').value;
        var isChangeFromExisting = value != '<%=h(bean.getFolderRootPath())%>';

        var optionCloudRoot = document.getElementById('optionCloudRoot');
        var migrateFiles = document.getElementById('migrateFilesRow');
        if (migrateFiles)
        {
            if (isChangeFromExisting)
            {
                migrateFiles.style.display = '';
                var migrateMoveOption = document.getElementById('migrateMoveOption');
                if (migrateMoveOption)
                {
                    var isNewCloudRootManaged = false;  // TODO: must we prevent moving when switching *TO* unmanaged cloud root?
                    if ((optionCloudRoot && optionCloudRoot.checked && isNewCloudRootManaged) || <%=!isCurrentFileRootManaged%>)
                    {
                        migrateMoveOption.setAttribute('hidden', '');
                    }
                    else
                    {
                        migrateMoveOption.removeAttribute('hidden');
                    }
                }
            }
            else
            {
                migrateFiles.style.display = 'none';
                if (notifyAboutPipeline)
                    notifyAboutPipeline.style.display = 'none';
            }
        }
    }

    updateSelection(<%=!origBean.getFileRootOption().equals(bean.getFileRootOption())%>);
</script>


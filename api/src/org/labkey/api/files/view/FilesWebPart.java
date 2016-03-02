/*
 * Copyright (c) 2007-2015 LabKey Corporation
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

package org.labkey.api.files.view;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.cloud.CloudStoreService;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.FileUrls;
import org.labkey.api.files.FilesAdminOptions;
import org.labkey.api.files.MissingRootDirectoryException;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.AlwaysAvailableWebPartFactory;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.webdav.WebdavService;

import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * User: Mark Igra
 * Date: Jul 9, 2007
 * Time: 2:13:18 PM
 */
public class FilesWebPart extends JspView<FilesWebPart.FilesForm>
{
    public static final String PART_NAME = "Files";
    private static final Logger _log = Logger.getLogger(FilesWebPart.class);

    private boolean showAdmin = false;
    private String fileSet;
    private Container container;
    private boolean _isPipelineFiles;       // viewing @pipeline files

    private static final String JSP = "/org/labkey/api/files/view/filesWebPart.jsp";

    public FilesWebPart(Container c, @Nullable String fileSet)
    {
        super(JSP);
        container = c;
        setModelBean(new FilesForm());
        setFileSet(null);
        setTitle(PART_NAME);
        setTitleHref(PageFlowUtil.urlProvider(FileUrls.class).urlBegin(c));
        setBodyClass("labkey-wp-nopadding");

        if (fileSet != null)
        {
            if (fileSet.equals(FileContentService.PIPELINE_LINK))
            {
                _isPipelineFiles = true;
                PipeRoot root = PipelineService.get().findPipelineRoot(getViewContext().getContainer());
                if (root != null)
                {
                    getModelBean().setRootPath(root.getWebdavURL());
                    getModelBean().setRootDirectory(root.getRootPath());
                }
                setTitleHref(PageFlowUtil.urlProvider(PipelineUrls.class).urlBrowse(c));
                setTitle("Pipeline Files");
            }
            else if (fileSet.startsWith(CloudStoreService.CLOUD_NAME))
            {
                // UNDONE: Configure filebrowser to not expand by default since even listing store contents costs money.
                String storeName = fileSet.substring((CloudStoreService.CLOUD_NAME + "/").length());
                getModelBean().setRootPath(getRootPath(c, CloudStoreService.CLOUD_NAME, storeName));
                setTitle(storeName);
                setTitleHref(PageFlowUtil.urlProvider(FileUrls.class).urlBegin(c).addParameter("fileSetName", fileSet));
            }
            else
            {
                FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
                AttachmentDirectory dir = svc.getRegisteredDirectory(c, fileSet);

                if (dir != null)
                {
                    try
                    {
                        getModelBean().setRoot(dir);
                        getModelBean().setRootDirectory(dir.getFileSystemDirectory());
                    }
                    catch (MissingRootDirectoryException e)
                    {
                        // this should never happen
                        throw new RuntimeException(e);
                    }
                }
                getModelBean().setRootPath(getRootPath(c, FileContentService.FILE_SETS_LINK, fileSet));
                setTitle(fileSet);
                setFileSet(fileSet);
                setTitleHref(PageFlowUtil.urlProvider(FileUrls.class).urlBegin(c).addParameter("fileSetName", fileSet));
            }
        }

        init();
    }

    public FilesWebPart(ViewContext ctx, Portal.WebPart webPartDescriptor)
    {
        this(ctx.getContainer(), StringUtils.trimToNull(webPartDescriptor.getPropertyMap().get("fileSet")));

        CustomizeFilesWebPartView.CustomizeWebPartForm form = new CustomizeFilesWebPartView.CustomizeWebPartForm(webPartDescriptor);
        getModelBean().setFolderTreeCollapsed(!form.isFolderTreeVisible());

        String size = webPartDescriptor.getPropertyMap().get("size");
        if (size != null)
        {
            getModelBean().setHeight(Integer.parseInt(size));
        }

        if (form.getRootOffset() != null)
        {
            getModelBean().setRootOffset(form.getRootOffset());
        }

        String path = webPartDescriptor.getPropertyMap().get("path");
        if (null != path)
        {
            try
            {
                getModelBean().setDirectory(Path.decode(path));
            }
            catch (Throwable t)
            {
                _log.warn("improper path passed to webpart: [" + path + "]");
            }
        }

        boolean displayAsListing = WebPartFactory.LOCATION_RIGHT.equals(webPartDescriptor.getLocation());
        getModelBean().setListing(displayAsListing);
        if (displayAsListing)
        {
            getModelBean().setAutoResize(false);
        }

        setShowAdmin(container.hasPermission(ctx.getUser(), AdminPermission.class));
    }

    protected void init()
    {
        // Determine the security policy for this webpart
        SecurityPolicy policy = SecurityPolicyManager.getPolicy(getSecurableResource());
        getConfiguredForm(policy);
    }


    @Override
    public void setIsOnlyWebPartOnPage(boolean b)
    {
        if (null == getModelBean().getHeight())
            getModelBean().setAutoResize(b);
    }


    protected List<FilesForm.actions> getConfiguredActions(SecurityPolicy policy)
    {
        List<FilesForm.actions> actions = new ArrayList<>();
        ViewContext context = getViewContext();
        User user = context.getUser();

        // Navigation actions
        actions.add(FilesForm.actions.folderTreeToggle);
        actions.add(FilesForm.actions.parentFolder);
        actions.add(FilesForm.actions.refresh);

        // Actions not based on the current selection
        if (policy.hasPermission(user, InsertPermission.class))
            actions.add(FilesForm.actions.createDirectory);

        // Actions based on the current selection
        actions.add(FilesForm.actions.download);
        if (policy.hasPermission(user, DeletePermission.class))
            actions.add(FilesForm.actions.deletePath);

        if (policy.hasPermission(user, UpdatePermission.class))
        {
            actions.add(FilesForm.actions.renamePath);
            actions.add(FilesForm.actions.movePath);
        }

        if (policy.hasPermission(user, InsertPermission.class))
        {
            actions.add(FilesForm.actions.editFileProps);
            actions.add(FilesForm.actions.upload);
        }

        if (canDisplayPipelineActions() && container.hasPermission(user, InsertPermission.class))
        {
            actions.add(FilesForm.actions.importData);
        }

        // users can manage their email notification settings
        if (!user.isGuest())
            actions.add(FilesForm.actions.emailPreferences);

        if (container.hasPermission(user, AdminPermission.class))
        {
            actions.add(FilesForm.actions.auditLog);
            actions.add(FilesForm.actions.customize);
        }

        return actions;
    }

    protected FilesForm getConfiguredForm(SecurityPolicy policy)
    {
        FilesForm form = getModelBean();
        ViewContext context = getViewContext();
        User user = context.getUser();

        if (form == null)
        {
            form = new FilesForm();
            setModelBean(form);
        }
        FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);

        form.setShowAddressBar(true);
        form.setShowDetails(false);
        form.setShowFolderTree(true);
        form.setFolderTreeCollapsed(true);

        if (form.getRootPath() == null)
            form.setRootPath(getRootPath(getRootContext().getContainer(), FileContentService.FILES_LINK));

        form.setEnabled(!svc.isFileRootDisabled(getRootContext().getContainer()));
        form.setContentId("fileContent" + System.identityHashCode(this));

        if (policy.hasPermission(user, InsertPermission.class))
        {
            FilesAdminOptions options = svc.getAdminOptions(container);
            boolean expandUpload = BooleanUtils.toBooleanDefaultIfNull(options.getExpandFileUpload(), false);
            form.setExpandFileUpload(expandUpload);
        }

        if (canDisplayPipelineActions())
        {
            form.setPipelineRoot(true);
        }

        List<FilesForm.actions> actions = getConfiguredActions(policy);
        form.setButtonConfig(actions.toArray(new FilesForm.actions[actions.size()]));

        return form;
    }

    public static String getRootPath(Container c, @Nullable String davName)
    {
        return getRootPath(c, davName, null);
    }

    public static String getRootPath(Container c, @Nullable String davName, @Nullable String fileset)
    {
        String webdavPrefix = AppProps.getInstance().getContextPath() + "/" + WebdavService.getServletPath();
        String rootPath = webdavPrefix + c.getEncodedPath();

        if (davName != null)
        {
            rootPath += URLEncoder.encode(davName);
            if (fileset != null)
                rootPath += "/" + fileset;
        }

        if (!rootPath.endsWith("/"))
            rootPath += "/";

        return rootPath;
    }

    protected boolean canDisplayPipelineActions()
    {
        try
        {
            if (_isPipelineFiles)
                return true;

            // since pipeline actions operate on the pipeline root, if the file content and pipeline roots do not
            // reference the same location, then import and customize actions should be disabled

            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            AttachmentDirectory dir = svc.getMappedAttachmentDirectory(getViewContext().getContainer(), false);
            PipeRoot root = PipelineService.get().findPipelineRoot(getViewContext().getContainer());

            if (null != root && null != dir && root.getRootPath().equals(dir.getFileSystemDirectory()))
            {
                return true;
            }
        }
        catch (MissingRootDirectoryException e)
        {
            _log.error("Error determining whether pipeline actions can be shown", e);
        }
        return false;
    }

    protected SecurableResource getSecurableResource()
    {
        return getViewContext().getContainer();
    }

    public boolean isShowAdmin()
    {
        return showAdmin;
    }

    public void setShowAdmin(boolean showAdmin)
    {
        this.showAdmin = showAdmin;
    }

    public String getFileSet()
    {
        return fileSet;
    }

    public void setFileSet(String fileSet)
    {
        this.fileSet = fileSet;
        FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
        try {
            AttachmentDirectory dir;
            if (null == fileSet)
                dir = svc.getMappedAttachmentDirectory(container, false);
            else
                dir = svc.getRegisteredDirectory(container, fileSet);

            if (dir != null)
            {
                getModelBean().setRoot(dir);
                getModelBean().setRootDirectory(dir.getFileSystemDirectory());
            }
        }
        catch (MissingRootDirectoryException ex)
        {
            setModelBean(null);
        }
    }

    public static class Factory extends AlwaysAvailableWebPartFactory
    {
        public Factory()
        {
            super(PART_NAME, true, false, WebPartFactory.LOCATION_BODY, WebPartFactory.LOCATION_RIGHT);
        }

        public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
        {
            return new FilesWebPart(portalCtx, webPart);
        }

        @Override
        public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
        {
            return new CustomizeFilesWebPartView(webPart);
        }
    }

    public static class FilesForm
    {
        private AttachmentDirectory _root;
        private boolean _showAddressBar;
        private boolean _showFolderTree;
        private boolean _folderTreeCollapsed;
        private boolean _showDetails;
        private boolean _autoResize;
        private boolean _enabled;
        private actions[] _buttonConfig;
        private String _rootPath;
        private String _rootOffset; //path between root for container and _rootPath used for webdav (if rooted at subdir)
        private Path _directory;
        private String _contentId;
        private boolean _isPipelineRoot;
        private String _statePrefix;
        private File _rootDirectory;
        private boolean _expandFileUpload;
        private boolean _disableGeneralAdminSettings;
        private Integer _height = null;
        private boolean _isListing;

        public enum actions {
            download,
            deletePath,
            renamePath,
            movePath,
            refresh,
            uploadTool,
            configure,
            createDirectory,
            parentFolder,
            upload,
            importData,
            customize,
            folderTreeToggle,
            editFileProps,
            emailPreferences,
            auditLog,
        }

        public boolean isAutoResize()
        {
            return _autoResize;
        }

        public void setAutoResize(boolean autoResize)
        {
            _autoResize = autoResize;
        }

        public AttachmentDirectory getRoot()
        {
            return _root;
        }

        public void setRoot(AttachmentDirectory root)
        {
            _root = root;
        }

        public boolean isShowAddressBar()
        {
            return _showAddressBar;
        }

        public void setShowAddressBar(boolean showAddressBar)
        {
            _showAddressBar = showAddressBar;
        }

        public boolean isShowFolderTree()
        {
            return _showFolderTree;
        }

        public void setShowFolderTree(boolean showFolderTree)
        {
            _showFolderTree = showFolderTree;
        }

        public boolean isShowDetails()
        {
            return _showDetails;
        }

        public void setShowDetails(boolean showDetails)
        {
            _showDetails = showDetails;
        }

        public actions[] getButtonConfig()
        {
            return _buttonConfig;
        }

        public void setButtonConfig(actions[] buttonConfig)
        {
            _buttonConfig = buttonConfig;
        }

        public String getRootPath()
        {
            return _rootPath;
        }

        public void setRootPath(String rootPath)
        {
            _rootPath = rootPath;
        }

        public String getRootOffset()
        {
            return _rootOffset;
        }

        public void setRootOffset(String rootOffset)
        {
            _rootOffset = rootOffset;
        }

        public void setDirectory(Path path)
        {
            _directory = path;
        }

        public Path getDirectory()
        {
            return _directory;
        }

        public boolean isEnabled()
        {
            return _enabled;
        }

        public void setEnabled(boolean enabled)
        {
            _enabled = enabled;
        }

        public String getContentId()
        {
            return _contentId;
        }

        public void setContentId(String contentId)
        {
            _contentId = contentId;
        }

        public boolean isPipelineRoot()
        {
            return _isPipelineRoot;
        }

        public void setPipelineRoot(boolean pipelineRoot)
        {
            _isPipelineRoot = pipelineRoot;
        }

        public boolean isFolderTreeCollapsed()
        {
            return _folderTreeCollapsed;
        }

        public void setFolderTreeCollapsed(boolean folderTreeCollapsed)
        {
            _folderTreeCollapsed = folderTreeCollapsed;
        }

        public String getStatePrefix()
        {
            return _statePrefix;
        }

        public void setStatePrefix(String statePrefix)
        {
            _statePrefix = statePrefix;
        }

        public boolean isRootValid()
        {
            return (_rootDirectory != null && _rootDirectory.exists());
        }

        public File getRootDirectory()
        {
            return _rootDirectory;
        }

        public void setRootDirectory(File rootDirectory)
        {
            _rootDirectory = rootDirectory;
        }

        public boolean isExpandFileUpload()
        {
            return _expandFileUpload;
        }

        public void setExpandFileUpload(boolean expandFileUpload)
        {
            _expandFileUpload = expandFileUpload;
        }

        public boolean isDisableGeneralAdminSettings()
        {
            return _disableGeneralAdminSettings;
        }

        public void setDisableGeneralAdminSettings(boolean disableGeneralAdminSettings)
        {
            _disableGeneralAdminSettings = disableGeneralAdminSettings;
        }

        public Integer getHeight()
        {
            return _height;
        }

        public void setHeight(int h)
        {
            this._height = h;
        }

        public boolean isListing()
        {
            return _isListing;
        }

        public void setListing(boolean listing)
        {
            this._isListing = listing;
        }
    }
}

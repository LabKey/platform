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
import org.labkey.api.premium.PremiumService;
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
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.AlwaysAvailableWebPartFactory;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.webdav.WebdavService;

import java.net.URLEncoder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: Mark Igra
 * Date: Jul 9, 2007
 * Time: 2:13:18 PM
 */
public class FilesWebPart extends JspView<FilesWebPart.FilesForm>
{
    public static final String PART_NAME = "Files";
    public static final String DEFAULT_TITLE = "Files";
    public static final String TITLE_PROPERTY_NAME = "title";
    public static final String FILESET_PROPERTY_NAME = "fileSet"; // legacy file foot, could be null, @pipeline, @cloud*, or fileset names
    public static final String FILE_ROOT_PROPERTY_NAME = "fileRoot"; // new file root, could be any child node on the container's webdav tree
    public static final String SIZE_PROPERTY_NAME = "size";
    public static final String PATH_PROPERTY_NAME = "path";
    public static final String ROOT_OFFSET_PROPERTY_NAME = "rootOffset";
    public static final String FOLDER_TREE_VISIBLE_PROPERTY_NAME = "folderTreeVisible";

    private static final Logger _log = Logger.getLogger(FilesWebPart.class);

    private boolean showAdmin = false;
    private String fileSet;
    private Container container;
    private boolean _isPipelineFiles;       // viewing @pipeline files
    private boolean _isRootNotFilesPipeline;

    private static final String JSP = "/org/labkey/api/files/view/filesWebPart.jsp";

    public FilesWebPart(Container c, @Nullable String legacyFileRoot, @Nullable String fileRoot)
    {
        super(JSP);
        container = c;
        setModelBean(new FilesForm());
        setFileSet(null);
        setTitle(DEFAULT_TITLE);
        ActionURL titleHref = PageFlowUtil.urlProvider(FileUrls.class).urlBegin(c);
        if (fileRoot != null)
            titleHref.addParameter("fileRootName", fileRoot);
        setTitleHref(titleHref);
        setBodyClass("labkey-wp-nopadding");

        FileContentService svc = FileContentService.get();
        if (null == svc)
            throw new IllegalStateException("FileContentService not found.");

        if (fileRoot != null)
        {
            if (fileRoot.startsWith(FileContentService.PIPELINE_LINK))
            {
                _isPipelineFiles = true;
            }
            else if (fileRoot.startsWith(FileContentService.FILE_SETS_LINK)
                    || fileRoot.startsWith(CloudStoreService.CLOUD_NAME) && !svc.isCloudRoot(c)
                    || fileRoot.startsWith("@wiki"))
            {
                _isRootNotFilesPipeline = true;
            }

            getModelBean().setRootPath(getWebPartFolderRootPath(c, fileRoot));
        }
        else if (legacyFileRoot != null) // legacy file root
        {
            if (legacyFileRoot.equals(FileContentService.PIPELINE_LINK))
            {
                _isPipelineFiles = true;
                PipeRoot root = PipelineService.get().findPipelineRoot(getViewContext().getContainer());
                if (root != null)
                {
                    getModelBean().setRootPath(root.getWebdavURL());
                }
                setTitleHref(PageFlowUtil.urlProvider(PipelineUrls.class).urlBrowse(c));
                setTitle("Pipeline Files");
            }
            else if (legacyFileRoot.startsWith(CloudStoreService.CLOUD_NAME))
            {
                // UNDONE: Configure filebrowser to not expand by default since even listing store contents costs money.
                String storeName = legacyFileRoot.substring((CloudStoreService.CLOUD_NAME + "/").length());
                getModelBean().setRootPath(getRootPath(c, CloudStoreService.CLOUD_NAME, storeName));
                setTitle(storeName);
                setTitleHref(PageFlowUtil.urlProvider(FileUrls.class).urlBegin(c).addParameter("fileSetName", legacyFileRoot));
            }
            else
            {
                AttachmentDirectory dir = svc.getRegisteredDirectory(c, legacyFileRoot);

                if (dir != null)
                {
                    try
                    {
                        getModelBean().setRoot(dir);
                        getModelBean().setRootDirectory(dir.getFileSystemDirectoryPath());
                    }
                    catch (MissingRootDirectoryException e)
                    {
                        // this should never happen
                        throw new RuntimeException(e);
                    }
                }
                getModelBean().setRootPath(getRootPath(c, FileContentService.FILE_SETS_LINK, legacyFileRoot));
                setTitle(legacyFileRoot);
                setFileSet(legacyFileRoot);
                setTitleHref(PageFlowUtil.urlProvider(FileUrls.class).urlBegin(c).addParameter("fileSetName", legacyFileRoot));
            }
        }

        init();
    }

    public FilesWebPart(ViewContext ctx, Portal.WebPart webPartDescriptor)
    {
        this(ctx.getContainer(), StringUtils.trimToNull(webPartDescriptor.getPropertyMap().get(FILESET_PROPERTY_NAME)), StringUtils.trimToNull(webPartDescriptor.getPropertyMap().get(FILE_ROOT_PROPERTY_NAME)));

        CustomizeFilesWebPartView.CustomizeWebPartForm form = new CustomizeFilesWebPartView.CustomizeWebPartForm(webPartDescriptor);
        getModelBean().setFolderTreeCollapsed(!form.isFolderTreeVisible());

        String size = webPartDescriptor.getPropertyMap().get(SIZE_PROPERTY_NAME);
        if (size != null)
        {
            getModelBean().setHeight(Integer.parseInt(size));
        }

        String title = webPartDescriptor.getPropertyMap().get(TITLE_PROPERTY_NAME);
        if (title != null)
        {
            setTitle(title);
        }

        if (form.getRootOffset() != null)
        {
            getModelBean().setRootOffset(form.getRootOffset());
        }

        String path = webPartDescriptor.getPropertyMap().get(PATH_PROPERTY_NAME);
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
            if (!PremiumService.get().isFileUploadDisabled())
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

        form.setShowAddressBar(true);
        form.setShowDetails(false);
        form.setShowFolderTree(true);
        form.setFolderTreeCollapsed(true);

        FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
        if (null == svc)
            throw new IllegalStateException("FileContentService not found.");
        if (null == getRootContext())
            throw new IllegalStateException("Root content not found.");

        Container rootContextContainer = getRootContext().getContainer();
        if (form.getRootPath() == null)
        {
            if (svc.isCloudRoot(rootContextContainer))
                form.setRootPath(getRootPath(rootContextContainer, FileContentService.CLOUD_LINK, svc.getCloudRootName(rootContextContainer)));
            else
                form.setRootPath(getRootPath(rootContextContainer, FileContentService.FILES_LINK));
        }

        form.setEnabled(!svc.isFileRootDisabled(rootContextContainer));
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

    private static final Set<String> _allowedDavNames = new HashSet<>(Arrays.asList(
            FileContentService.CLOUD_LINK,
            FileContentService.FILE_SETS_LINK,
            FileContentService.FILES_LINK,
            FileContentService.PIPELINE_LINK
    ));

    public static String getRootPath(Container c, @Nullable String davName)
    {
        return getRootPath(c, davName, null);
    }

    public static String getRootPath(Container c, @Nullable String davName, @Nullable String fileset)
    {
        return getRootPath(c, davName, fileset, false);
    }

    public static String getRootPath(Container c, @Nullable String davName, @Nullable String fileset, boolean skipDavPrefix)
    {
        String relativePath = "";
        if (davName != null)
        {
            if (!_allowedDavNames.contains(davName))              // TODO and I don't think we need to encode
                throw new IllegalStateException("unrecognized DavName");
            relativePath += URLEncoder.encode(davName);
            if (fileset != null)
                relativePath += "/" + fileset;
        }

        return _getRootPath(c, relativePath, skipDavPrefix);
    }
    private static String _getRootPath(Container c, @Nullable String relativePath, boolean skipDavPrefix)
    {
        String webdavPrefix = skipDavPrefix ? "" : AppProps.getInstance().getContextPath() + "/" + WebdavService.getServletPath();
        String rootPath = webdavPrefix + c.getEncodedPath();

        if (!rootPath.endsWith("/"))
            rootPath += "/";

        if (null != relativePath)
            rootPath += relativePath;

        if (!rootPath.endsWith("/"))
            rootPath += "/";

        return rootPath;
    }

    public static String getWebPartFolderRootPath(Container c, @Nullable String folderFileRoot)
    {
        return _getRootPath(c, folderFileRoot, false);
    }

    protected boolean canDisplayPipelineActions()
    {
        try
        {
            if (_isPipelineFiles)
                return true;
            else if (_isRootNotFilesPipeline)
                return false;

            // since pipeline actions operate on the pipeline root, if the file content and pipeline roots do not
            // reference the same location, then import and customize actions should be disabled

            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            if (null == svc)
                throw new IllegalStateException("FileContentService not found.");
            AttachmentDirectory dir = svc.getMappedAttachmentDirectory(getViewContext().getContainer(), false);
            PipeRoot root = PipelineService.get().findPipelineRoot(getViewContext().getContainer());

            if (null != root && root.isValid() && null != dir && root.getRootNioPath().equals(dir.getFileSystemDirectoryPath()))
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
        if (null == svc)
            throw new IllegalStateException("FileContentService not found.");

        try {
            AttachmentDirectory dir;
            if (null == fileSet)
                dir = svc.getMappedAttachmentDirectory(container, false);
            else
                dir = svc.getRegisteredDirectory(container, fileSet);

            if (dir != null)
            {
                getModelBean().setRoot(dir);
                getModelBean().setRootDirectory(dir.getFileSystemDirectoryPath());
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
        private java.nio.file.Path _rootDirectory;
        private boolean _expandFileUpload;
        private boolean _disableGeneralAdminSettings;
        private Integer _height = null;
        private boolean _isListing;

        public enum actions
        {
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

        public boolean isRootValid(Container container)
        {
            if (isCloudRootPath())
            {
                return isCloudStoreEnabled(container);
            }
            return (_rootDirectory != null && Files.exists(_rootDirectory));
        }

        public java.nio.file.Path getRootDirectory()
        {
            return _rootDirectory;
        }

        public void setRootDirectory(java.nio.file.Path rootDirectory)
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

        private static final String CLOUD_PATTERN_ENCODED = "%40cloud/";
        private static final String CLOUD_PATTERN = "@cloud/";

        private boolean isCloudStoreEnabled(Container container)
        {
            int cloudIndex = StringUtils.indexOf(_rootPath, CLOUD_PATTERN);
            int cloudPatternLength = CLOUD_PATTERN.length();
            if (-1 == cloudIndex)
            {
                cloudIndex = StringUtils.indexOf(_rootPath, CLOUD_PATTERN_ENCODED);
                cloudPatternLength = CLOUD_PATTERN_ENCODED.length();
            }
            if (-1 != cloudIndex)
            {
                String config = StringUtils.substring(_rootPath, cloudIndex + cloudPatternLength);
                int slashIndex = StringUtils.indexOf(config, "/");
                if (-1 != slashIndex)
                    config = StringUtils.substring(config, 0, slashIndex);

                CloudStoreService service = CloudStoreService.get();
                if (service != null)
                    for (String store : service.getEnabledCloudStores(getContextContainer()))
                    {
                        if (config.equalsIgnoreCase(store))
                        {
                            // Store is enabled; if non-null then it exists
                            return service.containerFolderExists(store, container);
                        }
                    }
                return false;
            }
            return true;    // Not cloud store
        }

        public boolean isCloudRootPath()
        {
            return -1 != StringUtils.indexOf(_rootPath, CLOUD_PATTERN) ||
                   -1 != StringUtils.indexOf(_rootPath, CLOUD_PATTERN_ENCODED);
        }
    }
}

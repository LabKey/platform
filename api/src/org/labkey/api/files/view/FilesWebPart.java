/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.FileUrls;
import org.labkey.api.files.MissingRootDirectoryException;
import org.labkey.api.files.view.CustomizeFilesWebPartView;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.*;
import org.labkey.api.webdav.WebdavService;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Mark Igra
 * Date: Jul 9, 2007
 * Time: 2:13:18 PM
 */
public class FilesWebPart extends JspView<FilesWebPart.FilesForm>
{
    private boolean wide = true;
    private boolean showAdmin = false;
    private String fileSet;
    private Container container;

    private static final String JSP = "/org/labkey/api/files/view/fileContent.jsp";


    public FilesWebPart(Container c)
    {
        super(JSP);
        setModelBean(createConfig());
        container = c;
        setFileSet(null);
        setTitle("Files");
        setTitleHref(PageFlowUtil.urlProvider(FileUrls.class).urlBegin(c));
    }

    protected FilesWebPart(Container c, String fileSet)
    {
        this(c);

        if (fileSet != null)
        {
            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            AttachmentDirectory dir = svc.getRegisteredDirectory(c, fileSet);

            //this.fileSet = fileSet;
            getModelBean().setRoot(dir);
            getModelBean().setRootPath(c, "@files/" + fileSet);
            setTitle(fileSet);
            setTitleHref(PageFlowUtil.urlProvider(FileUrls.class).urlBegin(c).addParameter("fileSetName",fileSet));
        }
    }

    protected FilesForm createConfig()
    {
        FilesForm form = new FilesForm();

        form.setAllowChangeDirectory(false);
        form.setShowAddressBar(false);
        form.setShowDetails(false);
        form.setShowFolderTree(false);
        form.setRootPath(getRootContext().getContainer(), null);

        List<FilesForm.actions> actions = new ArrayList<FilesForm.actions>();

        if (getRootContext().getContainer().hasPermission(getRootContext().getUser(), InsertPermission.class))
            actions.add(FilesForm.actions.upload);

        actions.add(FilesForm.actions.parentFolder);
        actions.add(FilesForm.actions.download);
        actions.add(FilesForm.actions.deletePath);
        actions.add(FilesForm.actions.refresh);
        actions.add(FilesForm.actions.importData);

        if (getRootContext().getUser().isAdministrator())
            actions.add(FilesForm.actions.customize);

        form.setButtonConfig(actions.toArray(new FilesForm.actions[actions.size()]));

        return form;
    }

    public FilesWebPart(ViewContext ctx, Portal.WebPart webPartDescriptor)
    {
        this(ctx.getContainer(), StringUtils.trimToNull(webPartDescriptor.getPropertyMap().get("fileSet")));

        setWide(null == webPartDescriptor.getLocation() || HttpView.BODY.equals(webPartDescriptor.getLocation()));
        setShowAdmin(container.hasPermission(ctx.getUser(), AdminPermission.class));
        String path = webPartDescriptor.getPropertyMap().get("path");
    }

    public boolean isWide()
    {
        return wide;
    }

    public void setWide(boolean wide)
    {
        this.wide = wide;
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
        if (null == fileSet)
        {
            try
            {
                getModelBean().setRoot(svc.getMappedAttachmentDirectory(container, false));
            }
            catch (MissingRootDirectoryException ex)
            {
                setModelBean(null);
            }
        }
        else
            getModelBean().setRoot(svc.getRegisteredDirectory(container, fileSet));
    }

    public static class Factory extends AlwaysAvailableWebPartFactory
    {
        public Factory(String location)
        {
            super("Files", location, true, false);
        }

        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
        {
            return new FilesWebPart(portalCtx, webPart);
        }

        @Override
        public HttpView getEditView(Portal.WebPart webPart)
        {
            JspView editView = new CustomizeFilesWebPartView(webPart);

            return editView;
        }
    }

    public static class FilesForm
    {
        private AttachmentDirectory _root;
        private boolean _showAddressBar;
        private boolean _showFolderTree;
        private boolean _showDetails;
        private boolean _allowChangeDirectory;
        private boolean _autoResize;
        private actions[] _buttonConfig;
        private String _rootPath;

        public enum actions {
            download,
            deletePath,
            refresh,
            uploadTool,
            configure,
            createDirectory,
            parentFolder,
            upload,
            importData,
            customize,
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

        public boolean isAllowChangeDirectory()
        {
            return _allowChangeDirectory;
        }

        public void setAllowChangeDirectory(boolean allowChangeDirectory)
        {
            _allowChangeDirectory = allowChangeDirectory;
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

        public void setRootPath(Container c, String davName)
        {
/*
            AppProps props = AppProps.getInstance();
            StringBuilder baseServer = URLHelper.getBaseServer(props.getScheme(), props.getServerName(), props.getServerPort());
            String webdavPrefix = baseServer.append(props.getContextPath()).append("/").append(WebdavService.getServletPath()).toString();
*/
            String webdavPrefix = AppProps.getInstance().getContextPath() + "/" + WebdavService.getServletPath();
            String rootPath;

            if (davName != null)
                rootPath = webdavPrefix + c.getEncodedPath() + davName;
            else
                rootPath = webdavPrefix + c.getEncodedPath();

            if (!rootPath.endsWith("/"))
                rootPath += "/";
            
            _rootPath = rootPath;
        }
    }
}

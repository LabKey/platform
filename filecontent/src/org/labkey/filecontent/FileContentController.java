/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.filecontent;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiJsonWriter;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiResponseWriter;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.attachments.AttachmentForm;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.provider.FileSystemAuditProvider;
import org.labkey.api.cloud.CloudStoreService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.property.ValidatorContext;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.files.DirectoryPattern;
import org.labkey.api.files.FileContentDefaultEmailPref;
import org.labkey.api.files.FileContentEmailPref;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.FileUrls;
import org.labkey.api.files.FilesAdminOptions;
import org.labkey.api.files.MissingRootDirectoryException;
import org.labkey.api.files.UnsetRootDirectoryException;
import org.labkey.api.files.view.FilesWebPart;
import org.labkey.api.message.settings.AbstractConfigTypeProvider;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.notification.EmailService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationError;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AbstractActionPermissionTest;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.Portal;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.filecontent.message.FileEmailConfig;
import org.labkey.filecontent.message.ShortMessageDigest;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;
import org.springframework.web.servlet.ModelAndView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class FileContentController extends SpringActionController
{
    public enum RenderStyle
    {
        DEFAULT,     // call defaultRenderStyle
        FRAME,       // <iframe>                       (*)
        INLINE,      // use INCLUDE (INLINE is confusing, does NOT mean content-disposition:inline)
        INCLUDE,     // include html                   (text/html)
        PAGE,        // content-disposition:inline     (*)
        ATTACHMENT,  // content-disposition:attachment (*)
        TEXT,        // filtered                       (text/*)
        IMAGE        // <img>                          (image/*)
    }

    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(FileContentController.class);

    public FileContentController()
   {
       setActionResolver(_actionResolver);
   }


    public static class FileUrlsImpl implements FileUrls
    {
        @Override
        public ActionURL urlBegin(Container container)
        {
            return new ActionURL(BeginAction.class, container);
        }

        @Override
        public ActionURL urlShowAdmin(Container container)
        {
            return new ActionURL(ShowAdminAction.class, container);
        }

        @Override
        public ActionURL urlFileEmailPreference(Container container)
        {
            return new ActionURL(FileEmailPreferenceAction.class, container);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class SendFileAction extends SimpleViewAction<SendFileForm>
    {
        private WebdavResource _resource;

        @Override
        public ModelAndView getView(SendFileForm form, BindException errors) throws Exception
        {
            if (null == form.getFileName())
                throw new NotFoundException();

            String fileSet = StringUtils.trimToNull(form.getFileSet());
            AttachmentDirectory p;
            FileContentService svc = FileContentService.get();

            if (svc == null)
                throw new NotFoundException();

            if (null == fileSet)
            {
                p = svc.getMappedAttachmentDirectory(getContainer(), false);
            }
            else
                p = svc.getRegisteredDirectory(getContainer(), fileSet);

            if (null == p)
                throw new NotFoundException();

            java.nio.file.Path dir = p.getFileSystemDirectoryPath();
            if (null == dir)
            {
                if (getContainer().hasPermission(getUser(), AdminOperationsPermission.class))
                    return HttpView.redirect(new ActionURL(ShowAdminAction.class,getContainer()));
                else
                    throw new NotFoundException();
            }

            Path filePath = Path.parse(form.getFileName());
            Path path;

            // support both legacy and newer formats, new URLs looks like webdav URLs, while older formats assume files
            // are only served out of the root
            if (filePath.contains(FileContentService.FILES_LINK) || filePath.contains(FileContentService.FILE_SETS_LINK) ||
                    filePath.contains(FileContentService.PIPELINE_LINK))
            {
                path = WebdavService.getPath().append(getContainer().getParsedPath()).append(filePath);
            }
            // legacy format: named file set specified as parameter
            else if (fileSet != null)
            {
                path = WebdavService.getPath().append(getContainer().getParsedPath()).append(FileContentService.FILE_SETS_LINK).append(fileSet).append(filePath);
            }
            else
                path = WebdavService.getPath().append(getContainer().getParsedPath()).append(FileContentService.FILES_LINK).append(filePath);

            _resource = WebdavService.get().getResolver().lookup(path);

            if (_resource == null || !_resource.isFile())
                throw new NotFoundException();

            try {
                RenderStyle style = form.getRenderStyle();
                MimeMap mimeMap = new MimeMap();
                String mimeType = _resource.getContentType();

                if (style == RenderStyle.DEFAULT)
                {
                    style = defaultRenderStyle(_resource.getName());
                }
                else
                {
                    // verify legal RenderStyle
                    boolean canInline = mimeType.startsWith("text/") || mimeMap.isInlineImageFor(_resource.getName());
                    if (!canInline && !(RenderStyle.ATTACHMENT == style || RenderStyle.PAGE == style))
                        style = RenderStyle.PAGE;
                    if (RenderStyle.IMAGE == style && !mimeType.startsWith("image/"))
                        style = RenderStyle.PAGE;
                    if (RenderStyle.TEXT == style && !mimeType.startsWith("text/"))
                        style = RenderStyle.PAGE;
                }

                //FIX: 5523 - if renderAs is null and mimetype is HTML, default style to inline
                if (null == form.getRenderAs())
                {
                    if ("text/html".equalsIgnoreCase(mimeType))
                        style = RenderStyle.INCLUDE;
                }

                switch (style)
                {
                    case ATTACHMENT:
                    case PAGE:
                    {
                        getPageConfig().setTemplate(PageConfig.Template.None);

                        try
                        {
                        PageFlowUtil.streamFile(getViewContext().getResponse(),
                                Collections.singletonMap("Content-Type",_resource.getContentType()),
                                _resource.getName(),
                                _resource.getInputStream(getUser()),
                                RenderStyle.ATTACHMENT==style);
                        }
                        catch (FileNotFoundException x)
                        {
                            throw new NotFoundException(_resource.getName());
                        }
                        return null;
                    }
                    case FRAME:
                    {
                        URLHelper url = new URLHelper(HttpView.getContextURL());
                        url.replaceParameter("renderAs", FileContentController.RenderStyle.PAGE.toString());
                        return new IFrameView(url.getLocalURIString());
                    }
                    case INCLUDE:
                    case INLINE:
                    {
                        WebPartView webPart = new WebPartView(WebPartView.FrameType.DIV)
                        {
                            @Override
                            protected void renderView(Object model, PrintWriter out) throws Exception
                            {
                                try (InputStream fis = _resource.getInputStream(getUser()))
                                {
                                    if (null == _resource || !_resource.isFile())
                                        throw new FileNotFoundException();
                                    if (null == fis)
                                        throw new FileNotFoundException();
                                    IOUtils.copy(new InputStreamReader(fis), out);
                                }
                                catch (FileNotFoundException x)
                                {
                                    out.write("<span class='labkey-error'>file not found: " + PageFlowUtil.filter(_resource.getName()) + "</span>");
                                }
                            }
                        };
                        webPart.setTitle(_resource.getName());
                        return webPart;
                    }
                    case TEXT:
                    {
                        WebPartView webPart = new WebPartView(_resource.getName())
                        {
                            @Override
                            protected void renderView(Object model, PrintWriter out)
                            {
                                renderResourceContents(out, _resource);
                            }
                        };

                        NavTree navMenu = new NavTree();
                        navMenu.addChild(new NavTree("download " + _resource.getName(), form.getDownloadURL(getContainer())));
                        webPart.setNavMenu(navMenu);
                        return webPart;
                    }
                    case IMAGE:
                    {
                        URLHelper url = new URLHelper(HttpView.getContextURL());
                        url.replaceParameter("renderAs", FileContentController.RenderStyle.PAGE.toString());
                        return new ImgView(url.getLocalURIString());
                    }
                    default:
                        return null;
                }
            }
            catch (Exception e)
            {
                throw new NotFoundException("An error occurred with the requested file : " + e.getMessage());
            }
        }

        private void renderResourceContents(PrintWriter out, WebdavResource resource)
        {
            StringBuilder contents = new StringBuilder();
            String line;
            String newline = System.getProperty("line.separator");
            final int MAX_SIZE = 5000;
            int size = 0;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(getUser()))))
            {
                while ((line = reader.readLine()) != null)
                {
                    contents.append(line);
                    contents.append(newline);

                    if (size++ > MAX_SIZE)
                        break;
                }

                if (size > MAX_SIZE)
                {
                    StringBuilder sb = new StringBuilder();

                    sb.append("<span class='labkey-error'>");
                    sb.append("The requested file is too large to display on a page, only part of the file is shown. To download the entire file contents ");
                    sb.append("click on the download link below.").append("</span><br>");
                    sb.append("<br>").append("<a href=\"").append(resource.getHref(getViewContext())).append("?contentDisposition=attachment");
                    sb.append("\">download ").append(PageFlowUtil.filter(resource.getName())).append("</a>");
                    sb.append("<br><br>");

                    out.write(sb.toString());
                }

                out.write(PageFlowUtil.filter(contents.toString(), true, true));
            }
            catch (FileNotFoundException x)
            {
                out.write("<span class='labkey-error'>file not found: " + PageFlowUtil.filter(resource.getName()) + "</span>");
            }
            catch (IOException e)
            {
                out.write("<span class='labkey-error'>IOException: " + PageFlowUtil.filter(resource.getName()) + "</span>");
            }
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            String name = _resource == null ? "<not found>" : _resource.getName();
            new BeginAction(getViewContext()).addNavTrail(root);
            root.addChild(name);
        }
    }


   public static class SrcForm
   {
       private String src;

       public String getSrc()
       {
           return src;
       }

       public void setSrc(String src)
       {
           this.src = src;
       }
   }


   @RequiresPermission(ReadPermission.class)
   public class FrameAction extends SimpleViewAction<SrcForm>
   {
       @Override
       public ModelAndView getView(SrcForm srcForm, BindException errors)
       {
           String src = srcForm.getSrc();
           return new IFrameView(src);
       }

       @Override
       public void addNavTrail(NavTree root)
       {
       }
   }


    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleViewAction<FileContentForm>
    {
        public BeginAction()
        {
        }

        public BeginAction(ViewContext ctx)
        {
            setViewContext(ctx);
        }

        @Override
        public ModelAndView getView(FileContentForm form, BindException errors)
        {
            FilesWebPart part = new FilesWebPart(getContainer(), form.getFileSetName(), form.getFileRootName());

            if (null != form.getPath())
            {
                try
                {
                    Path path = Path.decode(form.getPath());
                    part.getModelBean().setDirectory(path);
                }
                catch (Throwable t)
                {
                }
            }
            if (null != form.getRootOffset())
            {
                try
                {
                    String offset = Path.decode(form.getRootOffset()).toString().replaceAll("^/", "").toString();
                    String path = part.getModelBean().getRootPath();
                    path += path.endsWith("/") ? "" : "/";
                    path += part.getModelBean().getRootPath().endsWith("/") ? "" : "/";
                    path += offset;
                    part.getModelBean().setRootPath(path);
                    part.getModelBean().setRootOffset(offset);
                }
                catch (Throwable t)
                {
                }
            }
            if (null != form.getFolderTreeVisible())
            {
                part.getModelBean().setFolderTreeCollapsed(!form.getFolderTreeVisible());
            }

            part.setFrame(WebPartView.FrameType.NONE);
            part.getModelBean().setAutoResize(true);
            part.getModelBean().setShowDetails(true);
            return part;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Manage Files", new ActionURL(BeginAction.class, getContainer()));
        }
    }


   @RequiresPermission(AdminOperationsPermission.class)
   public class ShowAdminAction extends FormViewAction<FileContentForm>
   {
       @Override
       public ModelAndView getView(FileContentForm form, boolean reshow, BindException errors)
       {
           FileContentService service = FileContentService.get();

           if (service != null)
           {
               if (null == form.getRootPath())
               {
                   java.nio.file.Path root = service.getFileRootPath(getContainer());
                   if (null != root)
                   {
                       URI uri = root.toUri();
                       String strPath = FileUtil.getAbsolutePath(getContainer(), uri);
                       if (!FileUtil.hasCloudScheme(uri))
                       {
                           try
                           {
                               NetworkDrive.ensureDrive(strPath);
                               strPath = FileUtil.getAbsoluteCaseSensitiveFile(new File(uri)).getAbsolutePath();
                           }
                           catch (Exception e)
                           {
                               logger.error("Could not get canonical path for " + strPath + ", using path as entered.", e);
                           }
                       }
                       form.setRootPath(strPath);
                   }
               }
           }
           return new JspView<>("/org/labkey/filecontent/view/configure.jsp", form, errors);
       }


       @Override
       public void validateCommand(FileContentForm target, Errors errors)
       {
       }

       @Override
       public boolean handlePost(FileContentForm fileContentForm, BindException errors)
       {
           return false;
       }

       @Override
       public ActionURL getSuccessURL(FileContentForm fileContentForm)
       {
           return null;
       }

       @Override
       public void addNavTrail(NavTree root)
       {
           new BeginAction(getViewContext()).addNavTrail(root);
           root.addChild("Administer File System Access");
       }
   }


   @RequiresPermission(AdminOperationsPermission.class)
   public class AddAttachmentDirectoryAction extends ShowAdminAction
   {
       public static final int MAX_NAME_LENGTH = 80;
       public static final int MAX_PATH_LENGTH = 255;

       @Override
       public boolean handlePost(FileContentForm form, BindException errors)
       {
           String name = StringUtils.trimToNull(form.getFileSetName());
           String path = StringUtils.trimToNull(form.getPath());
           FileContentService service = FileContentService.get();

           if (null == name)
              	errors.reject(SpringActionController.ERROR_MSG, "Please enter a label for the file set. ");
           else if (name.length() > MAX_NAME_LENGTH)
                errors.reject(SpringActionController.ERROR_MSG, "Name is too long, should be less than " + MAX_NAME_LENGTH +" characters.");
           else
           {
               AttachmentDirectory attDir = service.getRegisteredDirectory(getContainer(), name);
               if (null != attDir)
                   errors.reject(SpringActionController.ERROR_MSG, "A file set named "  + name + " already exists.");
           }
           if (null == path)
               errors.reject(SpringActionController.ERROR_MSG, "Please enter a full path to the file set.");
           else if (path.length() > MAX_PATH_LENGTH)
                errors.reject(SpringActionController.ERROR_MSG, "File path is too long, should be less than " + MAX_PATH_LENGTH + " characters.");

		   String message = "";
           if (errors.getErrorCount() == 0)
           {
               service.registerDirectory(getContainer(), name, path, false);             // TODO: S3
               message = "Directory successfully registered.";
               File dir = new File(path);
               if (!dir.exists())
                   message += " NOTE: Directory does not currently exist. An administrator will have to create it before it can be used.";
               form.setPath(null);
               form.setFileSetName(null);
           }
           java.nio.file.Path webRoot = null;

           if (service != null)
               webRoot = service.getFileRootPath(getContainer());
           form.setRootPath(webRoot == null ? null : FileUtil.getAbsolutePath(getContainer(), webRoot.toUri()));
           form.setMessage(StringUtils.trimToNull(message));
           setReshow(true);
           return errors.getErrorCount() == 0;
       }
   }


   @RequiresPermission(AdminOperationsPermission.class)
   public class DeleteAttachmentDirectoryAction extends ShowAdminAction
   {
       @Override
       public boolean handlePost(FileContentForm form, BindException errors)
       {
           String name = StringUtils.trimToNull(form.getFileSetName());
           FileContentService service = FileContentService.get();

           if (null == name)
           {
               errors.reject(SpringActionController.ERROR_MSG, "No name for fileset supplied.");
               return false;
           }
           AttachmentDirectory attDir = service.getRegisteredDirectory(getContainer(), name);
           if (null == attDir)
           {
               form.setMessage("Attachment directory named " + name + " not found");
           }
           else
           {
               service.unregisterDirectory(getContainer(), form.getFileSetName());
               form.setMessage("Directory was removed from list. Files were not deleted.");
               form.setPath(null);
               form.setFileSetName(null);
           }
           java.nio.file.Path webRoot = service.getFileRootPath(getContainer());
           form.setRootPath(webRoot == null ? null : FileUtil.getAbsolutePath(getContainer(), webRoot.toUri()));
           setReshow(true);

		   return true;
       }
   }

    static class NodeForm
    {
        private String _node;
        private String _rootContainer;
        private boolean _showOverridesOnly = false;
        private boolean _excludeNotInFolderNav = false;

        public String getNode()
        {
            return _node;
        }

        public void setNode(String node)
        {
            _node = node;
        }

        public String getRootContainer()
        {
            return _rootContainer;
        }

        public void setRootContainer(String rootContainer)
        {
            _rootContainer = rootContainer;
        }

        public boolean isShowOverridesOnly()
        {
            return _showOverridesOnly;
        }

        public void setShowOverridesOnly(boolean showOverridesOnly)
        {
            _showOverridesOnly = showOverridesOnly;
        }

        public boolean isExcludeNotInFolderNav()
        {
            return _excludeNotInFolderNav;
        }

        public void setExcludeNotInFolderNav(boolean excludeNotInFolderNav)
        {
            _excludeNotInFolderNav = excludeNotInFolderNav;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class FileContentSummaryAction extends FileTreeNodeAction
    {
        @Override
        public Set<Map<String, Object>> getChildren(NodeForm form, BindException errors)
        {
            Container c = ContainerManager.getForId(form.getNode());
            if (c == null)
                c = ContainerManager.getForId(form.getRootContainer());
            if (c == null)
                c = ContainerManager.getRoot();

            ActionURL browse = new ActionURL(BeginAction.class, c);
            ActionURL config = new ActionURL(FileContentController.ShowAdminAction.class, c);
            Set<Map<String, Object>> children = FileContentServiceImpl.getInstance().getNodes(form.isShowOverridesOnly(), browse.getEncodedLocalURIString(), config.getEncodedLocalURIString(), c);

            for (Container child : c.getChildren())
            {
                if (form.isExcludeNotInFolderNav() && !child.isInFolderNav())
                    continue;

                Map<String, Object> node = new HashMap<>();

                node.put("id", child.getId());
                node.put("name", child.getName());

                children.add(node);
            }
            return children;
        }
    }

    /**
     * Returns information for project file web part administrative information on a per project basis
     */
    @RequiresPermission(ReadPermission.class)
    public class FileContentProjectSummaryAction extends FileTreeNodeAction
    {
        private static final String NODE_LABEL = "file web part";

        @Override
        protected Set<Map<String, Object>> getChildren(NodeForm form, BindException errors)
        {
            Container c = ContainerManager.getForId(form.getNode());
            if (c == null)
                c = ContainerManager.getForId(form.getRootContainer());
            if (c == null)
                c = ContainerManager.getRoot();

            Set<Map<String, Object>> children = new LinkedHashSet<>();
            FileContentService svc = FileContentService.get();

            try
            {
                AttachmentDirectory root = svc.getMappedAttachmentDirectory(c, false);
                ActionURL browse = new ActionURL(BeginAction.class, c);

                if (root != null)
                {

                    Map<String, Object> node = FileContentServiceImpl.getInstance().createFileSetNode(c, NODE_LABEL, root.getFileSystemDirectoryPath());

                    if (containsFileWebPart(c))
                    {
                        ActionURL config = PageFlowUtil.urlProvider(AdminUrls.class).getFileRootsURL(c);

                        node.put("configureURL", config.getEncodedLocalURIString());
                        node.put("browseURL", browse.getEncodedLocalURIString());
                    }
                    else
                    {
                        node.put("path", "web part not added");                        
                    }
                    children.add(node);
                }
            }
            catch (MissingRootDirectoryException | UnsetRootDirectoryException e){}

            // include all child containers
            for (Container child : c.getChildren())
            {
                Map<String, Object> node = new HashMap<>();

                node.put("id", child.getId());
                node.put("name", child.getName());

                children.add(node);
            }
            return children;
        }

        private boolean containsFileWebPart(Container c)
        {
            SimpleFilter filter = SimpleFilter.createContainerFilter(c).addCondition(FieldKey.fromParts("name"), FilesWebPart.PART_NAME);

            return new TableSelector(Portal.getTableInfoPortalWebParts(), filter, null).exists();
        }
    }

    @RequiresPermission(ReadPermission.class)
    public abstract class FileTreeNodeAction extends ReadOnlyApiAction<NodeForm>
    {
        protected abstract Set<Map<String, Object>> getChildren(NodeForm form, BindException errors);

        @Override
        public final Object execute(NodeForm nodeForm, BindException errors)
        {
            return new ApiSimpleResponse(Collections.singletonMap("children", getChildren(nodeForm, errors)));
        }

        @Override
        protected ApiResponseWriter createResponseWriter() throws IOException
        {
            return new ApiJsonWriter(getViewContext().getResponse(), getContentTypeOverride())
            {
                @Override
                public void write(ApiResponse response) throws IOException
                {
                    // need to write out the json in a form that the ext tree loader expects
                    Map<String, ?> props = ((ApiSimpleResponse)response).getProperties();
                    if (props.containsKey("children"))
                    {
                        JSONArray json = new JSONArray((Collection<Object>)props.get("children"));
                        getWriter().write(json.toString(4));
                    }
                    else
                        super.write(response);
                }
            };
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class DesignerAction extends SimpleRedirectAction<ReturnUrlForm>
    {
        @Override
        public URLHelper getRedirectURL(ReturnUrlForm form)
        {
            ActionURL successUrl = null;
            FileContentService svc = FileContentService.get();
            if (null != svc)
            {
                String domainURI = svc.getDomainURI(getContainer());

                // TODO consider moving ignoreSqlUpdates() into ensureDomainDescriptor()
                try (var ignore = SpringActionController.ignoreSqlUpdates())
                {
                    OntologyManager.ensureDomainDescriptor(domainURI, FileContentServiceImpl.PROPERTIES_DOMAIN, getContainer());
                }

                Domain domain = PropertyService.get().getDomain(getContainer(), domainURI);
                if (domain != null)
                {
                    successUrl = domain.getDomainKind().urlEditDefinition(domain, getViewContext());
                    form.propagateReturnURL(successUrl);
                }
            }

            if (successUrl == null)
                throw new NotFoundException("Unable to find file properties domain.");

            return successUrl;
        }
    }

    public static class FilePropsForm implements CustomApiForm
    {
        private Map<String,Object> _props;

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            _props = props;
        }

        public Map<String,Object> getProps()
        {
            return _props;
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class UpdateFilePropsAction extends MutatingApiAction<FilePropsForm>
    {
        private List<Map<String, Object>> _files;

        @Override
        public ApiResponse execute(FilePropsForm form, BindException errors) throws Exception
        {
            TableInfo ti = ExpSchema.TableType.Data.createTable(new ExpSchema(getUser(), getContainer()), ExpSchema.TableType.Data.toString(), null);
            QueryUpdateService qus = ti.getUpdateService();

            try
            {
                qus.updateRows(getUser(), getContainer(), _files, null, null, null);
            }
            catch (QueryUpdateServiceException e)
            {
                errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
            }

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("success", !errors.hasErrors());
            return response;
        }

        private static final String FILE_PROP_ERROR = "%s : %s";

        @Override
        public void validateForm(FilePropsForm form, Errors errors)
        {
            _files = parseFromJSON(form.getProps());

            FileContentService svc = FileContentService.get();
            String uri = svc.getDomainURI(getContainer());
            DomainDescriptor dd = OntologyManager.getDomainDescriptor(uri, getContainer());

            if (dd != null)
            {
                Domain domain = PropertyService.get().getDomain(dd.getDomainId());
                if (domain != null)
                {
                    ValidatorContext validatorCache = new ValidatorContext(getContainer(), getUser());
                    List<ValidationError> validationErrors = new ArrayList<>();
                    List<? extends DomainProperty> domainProps = domain.getProperties();

                    for (Map<String, Object> fileProps : _files)
                    {
                        FileContentServiceImpl fileContentService = FileContentServiceImpl.getInstance();
                        WebdavResource resource = fileContentService.getResource(String.valueOf(fileProps.get("id")));
                        if (resource != null && !resource.getActions(getUser()).isEmpty())
                        {
                            errors.reject(ERROR_MSG, String.format(FILE_PROP_ERROR, resource.getName(), "has been previously processed, properties cannot be edited"));
                            return;
                        }

                        String name = String.valueOf(fileProps.get("name"));
                        for (DomainProperty dp : domainProps)
                        {
                            Object o = fileProps.get(dp.getName());
                            if (!validateProperty(dp, String.valueOf(o), validationErrors, validatorCache))
                            {
                                for (ValidationError ve : validationErrors)
                                    errors.reject(ERROR_MSG, String.format(FILE_PROP_ERROR, name, ve.getMessage()));
                                return;
                            }
                        }

                        // need this as a key for the query update service
                        File file = resource.getFile();
                        if (null != file)
                        {
                            File canonicalFile = FileUtil.getAbsoluteCaseSensitiveFile(resource.getFile());
                            String url = canonicalFile.toPath().toUri().toString();
                            fileProps.put(ExpDataTable.Column.DataFileUrl.name(), url);
                        }
                        else if (fileContentService.isCloudRoot(getContainer()))
                        {
                            java.nio.file.Path filePath = resource.getNioPath();
                            if (null != filePath)
                            {
                                fileProps.put(ExpDataTable.Column.DataFileUrl.name(), FileUtil.pathToString(filePath));
                            }
                        }
                    }
                }
            }
        }

        private List<Map<String, Object>> parseFromJSON(Map<String, Object> props)
        {
            List<Map<String, Object>> files = new ArrayList<>();

            if (props.containsKey("files"))
            {
                Object fileObj = props.get("files");
                if (fileObj instanceof JSONArray)
                {
                    JSONArray jarray = (JSONArray)fileObj;

                    for (int i=0; i < jarray.length(); i++)
                    {
                        Map<String, Object> fileProps = new HashMap<>();

                        JSONObject jobj = jarray.getJSONObject(i);
                        if (jobj != null)
                        {
                            for (Map.Entry<String, Object> entry : jobj.entrySet())
                            {
                                if (entry.getValue() instanceof String)
                                    fileProps.put(entry.getKey(), StringUtils.trimToNull((String)entry.getValue()));
                                else
                                    fileProps.put(entry.getKey(), entry.getValue());
                            }
                        }
                        files.add(fileProps);
                    }
                }
            }
            return files;
        }

        private boolean validateProperty(DomainProperty prop, Object value, List<ValidationError> errors, ValidatorContext validatorCache)
        {
            // Don't validate null values, #15683
            if (null == value)
                return true;

            boolean ret = true;

            for (IPropertyValidator validator : prop.getValidators())
            {
                if (!validator.validate(prop.getPropertyDescriptor(), value, errors, validatorCache)) ret = false;
            }
            return ret;
        }
    }

    public static class ResetType
    {
        private String _type;

        public String getType()
        {
            return _type;
        }

        public void setType(String type)
        {
            _type = type;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ResetFileOptionsAction extends MutatingApiAction<ResetType>
    {
        @Override
        public ApiResponse execute(ResetType form, BindException errors)
        {
            FileContentService svc = FileContentService.get();
            FilesAdminOptions options = svc.getAdminOptions(getContainer());

            if (form.getType().equalsIgnoreCase("tbar"))
            {
                options.setTbarConfig(Collections.emptyList());
                options.setGridConfig(null);
            }
            else if (form.getType().equalsIgnoreCase("actions"))
            {
                options.setPipelineConfig(Collections.emptyList());
            }
            else if (form.getType().equalsIgnoreCase("general"))
            {
                options.setExpandFileUpload(null);
            }

            svc.setAdminOptions(getContainer(), options);

            return new ApiSimpleResponse("success", true);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetEmailPrefAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object o, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            MessageConfigService.ConfigTypeProvider provider = MessageConfigService.get().getConfigType(FileEmailConfig.TYPE);
            MessageConfigService.UserPreference pref = provider.getPreference(getContainer(), getUser(), getContainer().getId());

            String prefWithDefault = EmailService.get().getEmailPref(getUser(), getContainer(),
                    new FileContentEmailPref(), new FileContentDefaultEmailPref());

            response.put("emailPref", pref != null ? pref.getEmailOptionId() : "-1");
            response.put("emailPrefDefault", prefWithDefault);
            response.put("success", true);

            return response;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetFileRootsAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object o, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            Map<String, String> ret = new HashMap<>();

            //default
            ret.put(FileContentService.FILES_LINK, WebdavService.getPath().append(getContainer().getParsedPath()).append(FileContentService.FILES_LINK).toString());

            //pipeline, if set
            PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
            if (root != null && root.isValid())
            {
                String webdavURL = root.getWebdavURL();
                if (null != webdavURL && webdavURL.contains(FileContentService.PIPELINE_LINK) && root.getContainer().equals(getContainer()))
                    ret.put(FileContentService.PIPELINE_LINK, WebdavService.getPath().append(getContainer().getParsedPath()).append(FileContentService.PIPELINE_LINK).toString());
            }

            //filesets
            FileContentService svc = FileContentService.get();
            for (AttachmentDirectory attDir : svc.getRegisteredDirectories(getContainer()))
            {
                ret.put(FileContentService.FILE_SETS_LINK + "/" + attDir.getLabel(), WebdavService.getPath().append(getContainer().getParsedPath()).append(FileContentService.FILE_SETS_LINK).append(attDir.getLabel()).toString());
            }

            CloudStoreService cloud = CloudStoreService.get();
            if (cloud != null)
            {
                for (String storeName : cloud.getEnabledCloudStores(getContainer()))
                {
                    ret.put(CloudStoreService.CLOUD_NAME + "/" + storeName, WebdavService.getPath().append(getContainer().getParsedPath()).append(CloudStoreService.CLOUD_NAME).append(storeName).toString());
                }
            }

            response.put("fileRoots", ret);
            response.put("success", true);

            return response;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class SetEmailPrefAction extends MutatingApiAction<EmailPrefForm>
    {
        @Override
        public ApiResponse execute(EmailPrefForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            MessageConfigService.ConfigTypeProvider provider = MessageConfigService.get().getConfigType(FileEmailConfig.TYPE);
            provider.savePreference(getUser(), getContainer(), getUser(), form.getEmailPref(), getContainer().getId());
            response.put("success", true);

            return response;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class SetDefaultEmailPrefAction extends MutatingApiAction<AbstractConfigTypeProvider.EmailConfigFormImpl>
    {
        @Override
        public ApiResponse execute(AbstractConfigTypeProvider.EmailConfigFormImpl form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            StringBuilder message = new StringBuilder("The current default has been updated to: ");

            //save the default settings
            EmailService.get().setDefaultEmailPref(getContainer(), new FileContentDefaultEmailPref(), String.valueOf(form.getDefaultEmailOption()));

            for (MessageConfigService.NotificationOption option : MessageConfigService.get().getConfigType(FileEmailConfig.TYPE).getOptions())
            {
                if (option.getEmailOptionId() == form.getDefaultEmailOption())
                {
                    message.append(option.getEmailOption());
                    break;
                }
            }
            response.put("success", true);
            response.put("message", message.toString());          

            return response;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ShowFilesHistoryAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            UserSchema schema = AuditLogService.getAuditLogSchema(getUser(), getContainer());

            if (schema != null)
            {
                QuerySettings settings = new QuerySettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT);
                settings.setQueryName(FileSystemAuditProvider.EVENT_TYPE);
                return schema.createView(getViewContext(), settings, errors);
            }
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("File History");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class FileEmailPreferenceAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new JspView("/org/labkey/filecontent/view/configureEmail.jsp");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Email Notification Preferences");
        }
    }

    @Marshal(Marshaller.Jackson)
    @RequiresSiteAdmin
    public class SendShortDigestAction extends MutatingApiAction
    {
        @Override
        public Object execute(Object o, BindException errors) throws Exception
        {
            ShortMessageDigest.getInstance().sendMessageDigest();

            return success("File content 15-minute digest sent");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetCustomPropertiesAction extends ReadOnlyApiAction<CustomPropertiesForm>
    {
        @Override
        public ApiResponse execute(CustomPropertiesForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            List<Map<String, Object>> rows = new ArrayList<>();
            TableInfo tableInfo = ExpSchema.TableType.Data.createTable(new ExpSchema(getUser(), getContainer()), ExpSchema.TableType.Data.toString(), null);

            // add lookup display columns
            List<ColumnInfo> columns = new ArrayList<>(tableInfo.getColumns());
            if (form.getCustomProperties() != null)
            {
                Arrays.stream(form.getCustomProperties())
                        .map(tableInfo::getColumn)
                        .filter(column -> null != column && column.getDisplayField() != null)
                        .map(ColumnInfo::getDisplayField)
                        .forEach(columns::add);
            }

            // Issue 38409: limit number of exp.data to be process to prevent OutOfMemoryError
            final int MAX_ROW_COUNT = 10000;

            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("dataFileUrl"), null, CompareType.NONBLANK);

            new TableSelector(tableInfo, columns, filter, null).setMaxRows(MAX_ROW_COUNT).forEachMap(data ->
            {
                Object encodedUrl = data.get("dataFileUrl");
                Map<String, Object> row = new HashMap<>();
                java.nio.file.Path dataFilePath = FileUtil.stringToPath(getContainer(), (String) encodedUrl);
                row.put("dataFileUrl", null != dataFilePath ? FileUtil.pathToString(dataFilePath) : null);
                row.put("rowId", data.get("RowId"));
                row.put("name", data.get("Name"));
                if (null != form.getCustomProperties())
                {
                    for (String property : form.getCustomProperties())
                    {
                        ColumnInfo column = tableInfo.getColumn(property);
                        if (null != column)
                        {
                            ColumnInfo displayColumn = column.getDisplayField();

                            Map<String, Object> map = new HashMap<>();
                            map.put("value", data.get(displayColumn == null ? column.getAlias() : displayColumn.getAlias()));
                            StringExpression url = column.getEffectiveURL();
                            if (null != url)
                                map.put("url", url.eval(data));

                            // Display value for a lookup has already been handled by Exp.Data
                            row.put(property, map);
                        }
                    }
                    rows.add(row);
                }
            });
            response.put("rows", rows);
            response.put("success", true);
            return response;
        }
    }

    @RequiresNoPermission
    public class GetZiploaderPatternsAction extends ReadOnlyApiAction
    {
        @Override
        public Object execute(Object o, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            if (getViewContext().getContainer().isRoot())
            {
                response.put("rows", 0);
                return response;
            }

            if (!getViewContext().hasPermission(ReadPermission.class))
            {
                throw new UnauthorizedException();
            }

            FileContentService svc = FileContentService.get();
            List<DirectoryPattern> directoryPatterns = new ArrayList<>();
            List<JSONObject> directoryPatternsJson = new ArrayList<>();

            if(null != svc)
            {
                directoryPatterns = svc.getZiploaderPatterns(getContainer());
            }

            for(DirectoryPattern directory: directoryPatterns)
            {
                directoryPatternsJson.add(directory.toJSON());
            }

            response.put("rows", directoryPatternsJson);
            response.put("success", true);

            return response;
        }
    }

    public static class CustomPropertiesForm
    {
        private String[] _customProperties;

        public String[] getCustomProperties()
        {
            return _customProperties;
        }

        public void setCustomProperties(String[] customProperties)
        {
            _customProperties = customProperties;
        }
    }

    public static class EmailPrefForm
    {
        int _emailPref;

        public int getEmailPref()
        {
            return _emailPref;
        }

        public void setEmailPref(int emailPref)
        {
            _emailPref = emailPref;
        }
    }

    public static class FileContentForm
    {
        private String rootPath;
        private String rootOffset;
        private String message;
        private String fileSetName;
        private String fileRootName;
        private String path;
        private Boolean folderTreeVisible;

        public String getRootPath()
        {
            return rootPath;
        }

        public void setRootPath(String rootPath)
        {
            this.rootPath = rootPath;
        }

        public String getMessage()
        {
            return message;
        }

        public void setMessage(String message)
        {
            this.message = message;
        }

        public String getFileSetName()
        {
            return fileSetName;
        }

        public void setFileSetName(String fileSetName)
        {
            this.fileSetName = fileSetName;
        }

        public void setPath(String path)
        {
            this.path = path;
        }

        public String getPath()
        {
            return path;
        }

        public String getRootOffset()
        {
            return rootOffset;
        }

        public void setRootOffset(String rootOffset)
        {
            this.rootOffset = rootOffset;
        }

        public Boolean getFolderTreeVisible()
        {
            return folderTreeVisible;
        }

        public void setFolderTreeVisible(Boolean folderTreeVisible)
        {
            this.folderTreeVisible = folderTreeVisible;
        }

        public String getFileRootName()
        {
            return fileRootName;
        }

        public void setFileRootName(String fileRootName)
        {
            this.fileRootName = fileRootName;
        }
    }

   public static class SendFileForm
   {
       private String fileName;
       private String renderAs;
       private String fileSet;

       public String getFileName()
       {
           return fileName;
       }

       public void setFileName(String fileName)
       {
           this.fileName = fileName;
       }

       public String getRenderAs()
       {
           return renderAs;
       }

       public void setRenderAs(String renderAs)
       {
           this.renderAs = renderAs;
       }

       public RenderStyle getRenderStyle()
       {
           if (null == renderAs)
               return FileContentController.RenderStyle.PAGE;

           //Will throw illegal argument exception for other values...
           return FileContentController.RenderStyle.valueOf(renderAs.toUpperCase());
       }

       public String getFileSet()
       {
           return fileSet;
       }

       public void setFileSet(String fileSet)
       {
           this.fileSet = fileSet;
       }

       public ActionURL getDownloadURL(Container c)
       {
           ActionURL url = new ActionURL(SendFileAction.class, c);
           url.addParameter("fileName", getFileName());
           url.addParameter("renderAs", RenderStyle.ATTACHMENT.name());
           if (!StringUtils.isEmpty(getFileSet()))
               url.addParameter("fileSet",getFileSet());
           return url;
       }
   }

    private static class IFrameView extends JspView<String>
    {
        public IFrameView(String url)
        {
			super("/org/labkey/filecontent/view/iframe.jsp", url);
        }
    }


    private static class ImgView extends HtmlView
    {
        public ImgView(String url)
        {
            super("\n<img src=\"" + PageFlowUtil.filter(url) + "\">");
        }
    }


    void addError(BindException errors, String msg)
    {
        errors.addError(new ObjectError("form", new String[] {"Error"}, new Object[]{msg}, msg));
    }


    private AttachmentDirectory getAttachmentParent(AttachmentForm form) throws NotFoundException
    {
        AttachmentDirectory attachmentParent;
        FileContentService svc = FileContentService.get();
        try
        {
            if (null == form.getEntityId() || form.getEntityId().equals(getContainer().getId()))
                attachmentParent = svc.getMappedAttachmentDirectory(getContainer(), true);
            else
                attachmentParent = svc.getRegisteredDirectoryFromEntityId(getContainer(), form.getEntityId());
        }
        catch (UnsetRootDirectoryException e)
        {
            throw new NotFoundException("The web root for this project is not set. Please contact an administrator.", e);
        }
        catch (MissingRootDirectoryException e)
        {
            throw new NotFoundException("The web root for this project is set to a non-existent directory. Please contact an administrator", e);
        }

        boolean exists = false;
        try
        {
            exists = Files.exists(attachmentParent.getFileSystemDirectoryPath());
        }
        catch (MissingRootDirectoryException ex)
        {
            /* */
        }
        if (!exists)
            throw new NotFoundException("Directory for saving file does not exist. Please contact an administrator.");

        return attachmentParent;
    }

    static MimeMap mimeMap = new MimeMap();

    public static RenderStyle defaultRenderStyle(String name)
    {
        MimeMap.MimeType mime = mimeMap.getMimeTypeFor(name);
        if (null == mime)
            return RenderStyle.PAGE;
        if (mime.isInlineImage())
            return RenderStyle.IMAGE;
        if (mimeMap.isOfficeDocumentFor(name))
            return RenderStyle.ATTACHMENT;
        if (name.endsWith(".body"))
            return RenderStyle.INCLUDE;
        if (mime.getContentType().equals("application/pdf"))
            return RenderStyle.PAGE;
        if (mime.getContentType().equals("text/html"))
            return RenderStyle.INCLUDE;
        if (mime.getContentType().startsWith("text/"))
            return RenderStyle.TEXT;
        return RenderStyle.PAGE;
    }

    public static class TestCase extends AbstractActionPermissionTest
    {
        @Override
        public void testActionPermissions()
        {
            User user = TestContext.get().getUser();
            assertTrue(user.hasSiteAdminPermission());

            FileContentController controller = new FileContentController();

            // @RequiresPermission(ReadPermission.class)
            assertForReadPermission(user,
                controller.new SendFileAction(),
                controller.new FrameAction(),
                controller.new BeginAction(),
                controller.new FileContentSummaryAction(),
                controller.new FileContentProjectSummaryAction(),
                controller.new GetEmailPrefAction(),
                controller.new GetFileRootsAction(),
                controller.new SetEmailPrefAction(),
                controller.new FileEmailPreferenceAction()
            );

            // @RequiresPermission(InsertPermission.class)
            assertForInsertPermission(user,
                controller.new UpdateFilePropsAction()
            );

            // @RequiresPermission(AdminPermission.class)
            assertForAdminPermission(user,
                controller.new DesignerAction(),
                controller.new ResetFileOptionsAction(),
                controller.new SetDefaultEmailPrefAction(),
                controller.new ShowFilesHistoryAction()
            );

            // @RequiresPermission(AdminOperationsPermission.class)
            assertForAdminOperationsPermission(user,
                controller.new ShowAdminAction(),
                controller.new AddAttachmentDirectoryAction(),
                controller.new DeleteAttachmentDirectoryAction()
            );

            // @RequiresSiteAdmin
            assertForRequiresSiteAdmin(user,
                controller.new SendShortDigestAction()
            );
        }
    }
}

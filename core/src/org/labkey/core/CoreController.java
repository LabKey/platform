/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

package org.labkey.core;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.*;
import org.labkey.api.admin.CoreUrls;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentCache;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.CacheableWriter;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerManager.ContainerParent;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.files.FileContentService;
import org.labkey.api.module.AllowedBeforeInitialUserIsSet;
import org.labkey.api.module.AllowedDuringUpgrade;
import org.labkey.api.security.IgnoresTermsOfUse;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.PageFlowUtil.Content;
import org.labkey.api.util.PageFlowUtil.NoContent;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.*;
import org.labkey.api.webdav.ModuleStaticResolverImpl;
import org.labkey.api.webdav.Resource;
import org.labkey.api.webdav.WebdavResolver;
import org.labkey.core.query.CoreQuerySchema;
import org.labkey.core.workbook.*;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * User: jeckels
 * Date: Jan 4, 2007
 */
public class CoreController extends SpringActionController
{
    private static final long SECS_IN_DAY = 60 * 60 * 24;
    private static final long MILLIS_IN_DAY = 1000 * SECS_IN_DAY;

    private static Map<Container, Content> _themeStylesheetCache = new ConcurrentHashMap<Container, Content>();
    private static Map<Container, Content> _customStylesheetCache = new ConcurrentHashMap<Container, Content>();
    private static Map<Container, Content> _combinedStylesheetCache = new ConcurrentHashMap<Container, Content>();
    private static Map<Content, Content> _setCombinedStylesheet = new ConcurrentHashMap<Content,Content>();
    

    private static ActionResolver _actionResolver = new DefaultActionResolver(CoreController.class);

    public CoreController()
    {
        setActionResolver(_actionResolver);
    }

    public static class CoreUrlsImpl implements CoreUrls
    {
        private ActionURL getRevisionURL(Class<? extends Controller> actionClass, Container c)
        {
            ActionURL url = new ActionURL(actionClass, c);
            url.addParameter("revision", AppProps.getInstance().getLookAndFeelRevision());
            return url;
        }

        public ActionURL getThemeStylesheetURL()
        {
            return getRevisionURL(ThemeStylesheetAction.class, ContainerManager.getRoot());
        }

        public ActionURL getThemeStylesheetURL(Container c)
        {
            Container project = c.getProject();
            LookAndFeelProperties laf = LookAndFeelProperties.getInstance(project);

            if (laf.hasProperties())
                return getRevisionURL(ThemeStylesheetAction.class, project);
            else
                return null;
        }

        public ActionURL getCustomStylesheetURL()
        {
            return getCustomStylesheetURL(ContainerManager.getRoot());
        }

        public ActionURL getCustomStylesheetURL(Container c)
        {
            Container settingsContainer = LookAndFeelProperties.getSettingsContainer(c);
            Content css;
            try
            {
                css = getCustomStylesheetContent(settingsContainer);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }

            if (css instanceof NoContent)
                return null;
            else
                return getRevisionURL(CustomStylesheetAction.class, settingsContainer);
        }

        public ActionURL getCombinedStylesheetURL(Container c)
        {
            Container s = LookAndFeelProperties.getSettingsContainer(c);
            return getRevisionURL(CombinedStylesheetAction.class, s);
        }
    }

    abstract class BaseStylesheetAction extends ExportAction
    {
        @Override
        public void checkPermissions() throws TermsOfUseException, UnauthorizedException
        {
            // Stylesheets can be retrieved always by anyone.  This do-nothing override is even more permissive than
            //  using ACL.PERM_NONE and @IgnoresTermsOfUse since it also allows access in the root container even
            //  when impersonation is limited to a specific project.
        }

        public void export(Object o, HttpServletResponse response, BindException errors) throws Exception
        {
            HttpServletRequest request = getViewContext().getRequest();
            Content content = getContent(request, response);

            // No custom stylesheet for this container
            if (content instanceof NoContent)
                return;

            response.setContentType(getContentType());

            response.setDateHeader("Expires", System.currentTimeMillis() + MILLIS_IN_DAY * 35);
            response.setHeader("Cache-Control", "private");
            response.setHeader("Pragma", "cache");
            response.setDateHeader("Last-Modified", content.modified);
            if (StringUtils.trimToEmpty(request.getHeader("Accept-Encoding")).contains("gzip") && null != content.compressed)
            {
                response.setHeader("Content-Encoding", "gzip");
                response.getOutputStream().write(content.compressed);
            }
            else
            {
                response.getOutputStream().write(content.encoded);
            }
        }

        String getContentType()
        {
            return "text/css";
        }

        abstract Content getContent(HttpServletRequest request, HttpServletResponse response) throws Exception;
    }


    @RequiresNoPermission
    @IgnoresTermsOfUse
    @AllowedDuringUpgrade
    public class ThemeStylesheetAction extends BaseStylesheetAction
    {
        Content getContent(HttpServletRequest request, HttpServletResponse response) throws Exception
        {
            Container c = getContainer();
            Content content = _themeStylesheetCache.get(c);
            Integer dependsOn = AppProps.getInstance().getLookAndFeelRevision();

            if (null == content || !dependsOn.equals(content.dependencies))
            {
                JspView view = new JspView("/org/labkey/core/themeStylesheet.jsp");
                view.setFrame(WebPartView.FrameType.NONE);
                content = PageFlowUtil.getViewContent(view, request, response);
                content.dependencies = dependsOn;
                content.encoded = compressCSS(content.content);
                _themeStylesheetCache.put(c, content);
            }
            return content;
        }
    }


    @RequiresNoPermission
    @IgnoresTermsOfUse
    public class CustomStylesheetAction extends BaseStylesheetAction
    {
        Content getContent(HttpServletRequest request, HttpServletResponse response) throws Exception
        {
            return getCustomStylesheetContent(getContainer());
        }
    }


    private static Content getCustomStylesheetContent(Container c) throws IOException, ServletException
    {
        Content content = _customStylesheetCache.get(c);
        Integer dependsOn = AppProps.getInstance().getLookAndFeelRevision();

        if (null == content || !dependsOn.equals(content.dependencies))
        {
            AttachmentParent parent = new ContainerParent(c);
            Attachment cssAttachment = AttachmentCache.lookupCustomStylesheetAttachment(parent);

            if (null == cssAttachment)
            {
                content = new NoContent(dependsOn);
            }
            else
            {
                CacheableWriter writer = new CacheableWriter();
                AttachmentService.get().writeDocument(writer, parent, cssAttachment.getName(), false);
                content = new Content(new String(writer.getBytes()));
                content.dependencies = dependsOn;
                content.compressed = compressCSS(content.content);
            }

            _customStylesheetCache.put(c, content);
        }

        return content;
    }


    @RequiresNoPermission
    @IgnoresTermsOfUse
    @AllowedDuringUpgrade
    @AllowedBeforeInitialUserIsSet
    public class CombinedStylesheetAction extends BaseStylesheetAction
    {
        Content getContent(HttpServletRequest request, HttpServletResponse response) throws Exception
        {
            Container c = LookAndFeelProperties.getSettingsContainer(getContainer());

            Content content = _combinedStylesheetCache.get(c);
            Integer dependsOn = AppProps.getInstance().getLookAndFeelRevision();

            if (null == content || !dependsOn.equals(content.dependencies) || AppProps.getInstance().isDevMode())
            {
                InputStream is = null;
                try
                {
                    // get the root resolver
                    WebdavResolver r = ModuleStaticResolverImpl.get(); //ServiceRegistry.get(WebdavResolver.class);

                    Resource stylesheet = r.lookup(new Path("stylesheet.css"));

                    Content root = getCustomStylesheetContent(ContainerManager.getRoot());
                    Content theme = c.isRoot() ? null : (new ThemeStylesheetAction().getContent(request,response));
                    Content custom = c.isRoot() ? null : getCustomStylesheetContent(c);
                    Resource extAll = r.lookup(Path.parse("/ext-2.2/resources/css/ext-all.css"));
                    Resource extPatches = r.lookup(Path.parse("/ext-2.2/resources/css/ext-patches.css"));
                    StringWriter out = new StringWriter();

                    _appendCss(out, extAll);
                    _appendCss(out, extPatches);
                    _appendCss(out, stylesheet);
                    _appendCss(out, root);
                    _appendCss(out, theme);
                    _appendCss(out, custom);

                    String css = out.toString();
                    content = new Content(css);
                    content.compressed = compressCSS(css);
                    content.dependencies = dependsOn;
                    // save space
                    content.content = null; out = null;

                    synchronized (_setCombinedStylesheet)
                    {
                        Content shared = content.copy();
                        shared.modified = 0;
                        shared.dependencies = "";
                        if (!_setCombinedStylesheet.containsKey(shared))
                        {
                            _setCombinedStylesheet.put(shared,shared);
                        }
                        else
                        {
                            shared = _setCombinedStylesheet.get(shared);
                            content.content = shared.content;
                            content.encoded = shared.encoded;
                            content.compressed = shared.compressed;
                        }
                    }
                    _combinedStylesheetCache.put(c, content);
                }
                finally
                {
                    IOUtils.closeQuietly(is);
                }
            }

            return content;
        }
    }


    void _appendCss(StringWriter out, Resource r)
    {
        if (null == r || !r.isFile())
            return;
        assert null != r.getFile();
        String s = PageFlowUtil.getFileContentsAsString(r.getFile());
        Path p = Path.parse(getViewContext().getContextPath()).append(r.getPath()).getParent();
        _appendCss(out, p, s);
    }


    void _appendCss(StringWriter out, Content content)
    {
        if (null == content || content instanceof NoContent)
            return;
        // relative URLs aren't really going to work (/labkey/core/container/), so path=null
        _appendCss(out, null, content.content);
    }
    

    void _appendCss(StringWriter out, Path p, String s)
    {
        if (null != p)
            s = s.replaceAll("url\\(\\s*([^/])", "url(" + p.toString("/","/") + "$1");
        out.write(s);
        out.write("\n");
    }
    

    private static byte[] compressCSS(String s)
    {
        String c = s;
        c = c.replaceAll("/\\*(?:.|[\\n\\r])*?\\*/", "");
        c = c.replaceAll("(?:\\s|[\\n\\r])+", " ");
        c = c.replaceAll("\\s*}\\s*", "}\r\n");
        return PageFlowUtil.gzip(c.trim());
    }


    static AtomicReference<Content> _combinedJavascript = new AtomicReference<Content>(); 

    @RequiresNoPermission
    @IgnoresTermsOfUse
    @AllowedDuringUpgrade
    @AllowedBeforeInitialUserIsSet
    public class CombinedJavascriptAction extends BaseStylesheetAction
    {
        Content getContent(HttpServletRequest request, HttpServletResponse response) throws Exception
        {
            Content ret = _combinedJavascript.get();
            if (null == ret)
            {
                // get the root resolver
                WebdavResolver r = ModuleStaticResolverImpl.get();
                
                List<String> scripts = new ArrayList<String>();
                LinkedHashSet<String> includes = new LinkedHashSet<String>();
                PageFlowUtil.getJavaScriptPaths(scripts, includes);
                List<String> concat = new ArrayList<String>();
                for (String path : scripts)
                {
                    Resource script = r.lookup(Path.parse(path));
                    assert(script.isFile());
                    if (!script.isFile())
                        continue;
                    concat.add("/* ---- " + path + " ---- */");
                    List<String> content = PageFlowUtil.getStreamContentsAsList(script.getInputStream(getUser()));
                    concat.addAll(content);
                }
                int len = 0;
                for (String s : concat)
                    len = s.length()+1;
                StringBuilder sb = new StringBuilder(len);
                for (String s : concat)
                {
                    String t = StringUtils.trimToNull(s);
                    if (t == null) continue;
                    if (t.startsWith("//"))
                        continue;
                    sb.append(t).append('\n');
                }
                ret = new Content(sb.toString());
                ret.content = null; sb = null; concat = null;
                ret.compressed = PageFlowUtil.gzip(ret.encoded);
                _combinedJavascript.set(ret);
            }
            return ret;
        }

        @Override
        String getContentType()
        {
            return "text/javascript";
        }
    }


    @RequiresNoPermission
    @IgnoresTermsOfUse
    public class ContainerRedirectAction extends SimpleRedirectAction<RedirectForm>
    {
        public ActionURL getRedirectURL(RedirectForm form) throws Exception
        {
            Container targetContainer = ContainerManager.getForId(form.getContainerId());
            if (targetContainer == null)
            {
                HttpView.throwNotFound();
            }
            ActionURL url = getViewContext().getActionURL().clone();
            url.deleteParameter("action");
            url.deleteParameter("pageflow");
            url.deleteParameter("containerId");
            url.setPageFlow(form.getPageflow());
            url.setAction(form.getAction());
            url.setContainer(targetContainer);
            return url;
        }
    }


    public static class RedirectForm
    {
        private String _containerId;
        private String _action;
        private String _pageflow;

        public String getAction()
        {
            return _action;
        }

        public void setAction(String action)
        {
            _action = action;
        }

        public String getContainerId()
        {
            return _containerId;
        }

        public void setContainerId(String containerId)
        {
            _containerId = containerId;
        }

        public String getPageflow()
        {
            return _pageflow;
        }

        public void setPageflow(String pageflow)
        {
            _pageflow = pageflow;
        }
    }

    public static class GetAttachmentIconForm
    {
        private String _extension;

        public String getExtension()
        {
            return _extension;
        }

        public void setExtension(String extension)
        {
            _extension = extension;
        }
    }

    @RequiresNoPermission
    public class GetAttachmentIconAction extends SimpleViewAction<GetAttachmentIconForm>
    {
        public ModelAndView getView(GetAttachmentIconForm form, BindException errors) throws Exception
        {
            String path = Attachment.getFileIcon(StringUtils.trimToEmpty(form.getExtension()));

            //open the file and stream it back to the client
            HttpServletResponse response = getViewContext().getResponse();
            response.setContentType(PageFlowUtil.getContentTypeFor(path));
            response.setHeader("Cache-Control", "public");
            response.setHeader("Pragma", "");

            byte[] buf = new byte[4096];
            InputStream is = ViewServlet.getViewServletContext().getResourceAsStream(path);
            OutputStream os = response.getOutputStream();

            try
            {
                for(int len; (len=is.read(buf))!=-1; )
                    os.write(buf,0,len);
            }
            finally
            {
                os.close();
                is.close();
            }

            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class ManageWorkbooksAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new WorkbookQueryView(getViewContext(), new CoreQuerySchema(getUser(), getContainer()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Manage Workbooks");
        }
    }

    public static class LookupWorkbookForm
    {
        private String _id;

        public String getId()
        {
            return _id;
        }

        public void setId(String id)
        {
            _id = id;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class LookupWorkbookAction extends SimpleViewAction<LookupWorkbookForm>
    {
        public ModelAndView getView(LookupWorkbookForm form, BindException errors) throws Exception
        {
            if (null == form.getId())
                throw new NotFoundException("You must supply the id of the workbook you wish to find.");

            //try to lookup based on id
            Container container = ContainerManager.getForRowId(form.getId());
            //if found, ensure it's a descendant of the current container, and redirect
            if (null != container && container.isDescendant(getContainer()))
                throw new RedirectException(container.getStartURL(getViewContext()));

            //next try to lookup based on name
            container = getContainer().findDescendant(form.getId());
            if (null != container)
                throw new RedirectException(container.getStartURL(getViewContext()));

            //otherwise, return a workbooks list with the search view
            HtmlView message = new HtmlView("<p class='labkey-error'>Could not find a workbook with id '" + form.getId() + "' in this folder or subfolders. Try searching or entering a different id.</p>");
            WorkbookQueryView wbqview = new WorkbookQueryView(getViewContext(), new CoreQuerySchema(getUser(), getContainer()));
            return new VBox(message, new WorkbookSearchView(wbqview), wbqview);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            //if a view ends up getting rendered, the workbook id was not found
            return root.addChild("Workbooks");
        }
    }

    public static class CreateWorkbookForm
    {
        private String _title;
        private String _description;

        public String getTitle()
        {
            return _title;
        }

        public void setTitle(String title)
        {
            _title = title;
        }

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class CreateWorkbookAction extends FormViewAction<CreateWorkbookForm>
    {
        private Container _newWorkbook;

        public void validateCommand(CreateWorkbookForm form, Errors errors)
        {
            String title = StringUtils.trimToNull(form.getTitle());
            if (null == title)
                errors.reject(null, "You must supply a title for the new workbook!");
        }

        public ModelAndView getView(CreateWorkbookForm createWorkbookForm, boolean reshow, BindException errors) throws Exception
        {
            CreateWorkbookBean bean = new CreateWorkbookBean();

            //suggest a name
            //per spec it should be "<user-display-name> YYYY-MM-DD"
            bean.setTitle(getViewContext().getUser().getDisplayName(getViewContext()) + " " + DateUtil.formatDate(new Date()));

            return new JspView<CreateWorkbookBean>("/org/labkey/core/workbook/createWorkbook.jsp", bean, errors);
        }

        public boolean handlePost(CreateWorkbookForm form, BindException errors) throws Exception
        {
            String name = StringUtils.trimToNull(form.getTitle());
            Container container = getContainer();

            _newWorkbook = ContainerManager.createWorkbook(container, name, StringUtils.trimToNull(form.getDescription()), getUser());
            _newWorkbook.setFolderType(new WorkbookFolderType());

            Portal.WebPart[] parts = Portal.getParts(_newWorkbook.getId());
            assert null != parts;
            for (Portal.WebPart part : parts)
            {
                if (part.getName().equalsIgnoreCase("Files"))
                {
                    part.setProperty("fileSet", FileContentService.PIPELINE_LINK);
                    Portal.updatePart(getUser(), part);
                }
            }

            return true;
        }

        public URLHelper getSuccessURL(CreateWorkbookForm form)
        {
            Container c = (null != _newWorkbook ? _newWorkbook : getContainer());
            return c.getStartURL(getViewContext());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Create New Workbook");
        }
    }

    public static class UpdateDescriptionForm
    {
        private String _description;

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
    public class UpdateDescriptionAction extends MutatingApiAction<UpdateDescriptionForm>
    {
        public ApiResponse execute(UpdateDescriptionForm form, BindException errors) throws Exception
        {
            String description =  StringUtils.trimToNull(form.getDescription());
            ContainerManager.updateDescription(getContainer(), description, getUser());
            return new ApiSimpleResponse("description", description);
        }
    }

    public static class UpdateTitleForm
    {
        private String _title;

        public String getTitle()
        {
            return _title;
        }

        public void setTitle(String title)
        {
            _title = title;
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
    public class UpdateTitleAction extends MutatingApiAction<UpdateTitleForm>
    {
        public ApiResponse execute(UpdateTitleForm form, BindException errors) throws Exception
        {
            String title =  StringUtils.trimToNull(form.getTitle());
            ContainerManager.updateTitle(getContainer(), title, getUser());
            return new ApiSimpleResponse("title", title);
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class MoveWorkbooksAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Container parentContainer = getViewContext().getContainer();
            Set<String> ids = DataRegionSelection.getSelected(getViewContext(), true);
            if (null == ids || ids.size() == 0)
                throw new RedirectException(parentContainer.getStartURL(getViewContext()));

            MoveWorkbooksBean bean = new MoveWorkbooksBean();
            for (String id : ids)
            {
                Container wb = ContainerManager.getForRowId(id);
                if (null != wb)
                    bean.addWorkbook(wb);
            }

            return new JspView<MoveWorkbooksBean>("/org/labkey/core/workbook/moveWorkbooks.jsp", bean, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Move Workbooks");
        }
    }

    public static class ExtContainerTreeForm
    {
        private String _node;

        public String getNode()
        {
            return _node;
        }

        public void setNode(String node)
        {
            _node = node;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetExtContainerTreeAction extends ApiAction<ExtContainerTreeForm>
    {
        public ApiResponse execute(ExtContainerTreeForm form, BindException errors) throws Exception
        {
            User user = getViewContext().getUser();
            JSONArray children = new JSONArray();

            Container parent = ContainerManager.getForRowId(form.getNode());
            if (null != parent)
            {
                for (Container child : parent.getChildren())
                {
                    if (!child.isWorkbook() && child.hasPermission(user, ReadPermission.class))
                    {
                        children.put(getContainerProps(child));
                    }
                }
            }

            HttpServletResponse resp = getViewContext().getResponse();
            resp.setContentType("application/json");
            resp.getWriter().write(children.toString());

            return null;
        }

        private JSONObject getContainerProps(Container c)
        {
            JSONObject props = new JSONObject();
            props.put("id", c.getRowId());
            props.put("text", c.getName());
            props.put("containerPath", c.getPath());
            if (c.equals(getViewContext().getContainer()))
                props.put("disabled", true);
            return props;
        }
    }

    public static class MoveWorkbookForm
    {
        public int _workbookId = -1;
        public int _newParentId = -1;

        public int getNewParentId()
        {
            return _newParentId;
        }

        public void setNewParentId(int newParentId)
        {
            _newParentId = newParentId;
        }

        public int getWorkbookId()
        {

            return _workbookId;
        }

        public void setWorkbookId(int workbookId)
        {
            _workbookId = workbookId;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class MoveWorkbookAction extends MutatingApiAction<MoveWorkbookForm>
    {
        public ApiResponse execute(MoveWorkbookForm form, BindException errors) throws Exception
        {
            if (form.getWorkbookId() < 0)
                throw new IllegalArgumentException("You must supply a workbookId parameter!");
            if (form.getNewParentId() < 0)
                throw new IllegalArgumentException("You must specify a newParentId parameter!");

            Container wb = ContainerManager.getForRowId(String.valueOf(form.getWorkbookId()));
            if (null == wb || !(wb.isWorkbook()) || !(wb.isDescendant(getViewContext().getContainer())))
                throw new IllegalArgumentException("No workbook found with id '" + form.getWorkbookId() + "'");

            Container newParent = ContainerManager.getForRowId(String.valueOf(form.getNewParentId()));
            if (null == newParent || newParent.isWorkbook())
                throw new IllegalArgumentException("No folder found with id '" + form.getNewParentId() + "'");

            if (wb.getParent().equals(newParent))
                throw new IllegalArgumentException("Workbook is already in the target folder.");

            //user must be allowed to create workbooks in the new parent folder
            if (!newParent.hasPermission(getViewContext().getUser(), InsertPermission.class))
                throw new UnauthorizedException("You do not have permission to move workbooks to the folder '" + newParent.getName() + "'.");

            //workbook name must be unique within parent
            if (newParent.hasChild(wb.getName()))
                throw new RuntimeException("Can't move workbook '" + wb.getTitle() + "' because another workbook or subfolder in the target folder has the same name.");

            ContainerManager.move(wb, newParent);

            return new ApiSimpleResponse("moved", true);
        }
    }
}

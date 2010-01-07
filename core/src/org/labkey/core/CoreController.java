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

package org.labkey.core;

import org.apache.commons.lang.StringUtils;
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
import org.labkey.api.module.AllowedDuringUpgrade;
import org.labkey.api.security.IgnoresTermsOfUse;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.PageFlowUtil.Content;
import org.labkey.api.util.PageFlowUtil.NoContent;
import org.labkey.api.view.*;
import org.labkey.core.workbook.WorkbookQueryView;
import org.labkey.core.workbook.WorkbookSearchView;
import org.labkey.core.workbook.CreateWorkbookBean;
import org.labkey.core.workbook.WorkbookFolderType;
import org.labkey.core.query.CoreQuerySchema;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

            response.setContentType("text/css");

            response.setDateHeader("Expires", System.currentTimeMillis() + MILLIS_IN_DAY * 35);
            response.setHeader("Cache-Control", "private");
            response.setHeader("Pragma", "cache");
            response.setDateHeader("Last-Modified", content.modified);
            if (StringUtils.trimToEmpty(request.getHeader("Accept-Encoding")).contains("gzip"))
            {
                response.setHeader("Content-Encoding", "gzip");
                response.getOutputStream().write(content.encoded);
            }
            else
            {
                response.getWriter().write(content.content);
            }
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
                content.encoded = compressCSS(content.content);
            }

            _customStylesheetCache.put(c, content);
        }

        return content;
    }


    private static byte[] compressCSS(String s)
    {
        String c = s.trim();
        // this works but probably unnecesary with gzip
        //c = c.replaceAll("\\s+", " ");
        //c = c.replaceAll("\\s*}\\s*", "}\r\n");
        return PageFlowUtil.gzip(c);
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
        private String _name;
        private String _description;

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
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
            String name = StringUtils.trimToNull(form.getName());
            if (null == name)
                errors.reject(null, "You must supply a name for the new workbook!");
            else
            {
                //ensure name is unique
                Container container = getContainer();
                if (container.hasChild(name))
                    errors.reject(null, "The name '" + name + "' has already been used for another workbook.");
            }
        }

        public ModelAndView getView(CreateWorkbookForm createWorkbookForm, boolean reshow, BindException errors) throws Exception
        {
            Container container = getContainer();
            CreateWorkbookBean bean = new CreateWorkbookBean();

            //suggest a name
            bean.setName(container.getNextWorkbookName());

            return new JspView<CreateWorkbookBean>("/org/labkey/core/workbook/createWorkbook.jsp", bean, errors);
        }

        public boolean handlePost(CreateWorkbookForm form, BindException errors) throws Exception
        {
            String name = StringUtils.trimToNull(form.getName());
            Container container = getContainer();

            _newWorkbook = ContainerManager.createWorkbook(container, name, StringUtils.trimToNull(form.getDescription()), getUser());
            _newWorkbook.setFolderType(new WorkbookFolderType());

            //parse and remember new prefix
            //according to spec, name can be in the following forms
            // <prefix>-N
            // <prefix> N
            //
            int pos = name.lastIndexOf('-');
            if (pos < 0)
                pos = name.lastIndexOf(" ");

            if (pos >= 0)
            {
                String prefix = name.substring(0, pos);
                container.setWorkbookNamePrefix(prefix);
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
}

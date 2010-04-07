/*
 * Copyright (c) 2004-2010 Fred Hutchinson Cancer Research Center
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

package org.labkey.portal;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.action.*;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.FolderType;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.*;
import org.labkey.api.view.template.HomeTemplate;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.webdav.WebdavService;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import java.util.*;


public class ProjectController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(ProjectController.class);

    public static class ProjectUrlsImpl implements ProjectUrls
    {
        public ActionURL getStartURL(Container container)
        {
            return new ActionURL(StartAction.class, container);
        }

        public ActionURL getBeginURL(Container container)
        {
            return new ActionURL(BeginAction.class, container);
        }

        public ActionURL getHomeURL()
        {
            return getStartURL(ContainerManager.getHomeContainer());
        }

        public ActionURL getCustomizeWebPartURL(Container c)
        {
            return new ActionURL(CustomizeWebPartAction.class, c);
        }

        public ActionURL getAddWebPartURL(Container c)
        {
            return new ActionURL(AddWebPartAction.class, c);
        }

        public ActionURL getCustomizeWebPartURL(Container c, Portal.WebPart webPart, ActionURL returnURL)
        {
            ActionURL url = getCustomizeWebPartURL(c);
            url.addParameter("pageId", webPart.getPageId());
            url.addParameter("index", String.valueOf(webPart.getIndex()));
            url.addReturnURL(returnURL);
            return url;
        }

        public ActionURL getMoveWebPartURL(Container c, Portal.WebPart webPart, int direction, ActionURL returnURL)
        {
            ActionURL url = new ActionURL(MoveWebPartAction.class, c);
            url.addParameter("pageId", webPart.getPageId());
            url.addParameter("index", String.valueOf(webPart.getIndex()));
            url.addParameter("direction", String.valueOf(direction));
            url.addReturnURL(returnURL);
            return url;
        }

        public ActionURL getDeleteWebPartURL(Container c, Portal.WebPart webPart, ActionURL returnURL)
        {
            ActionURL url = new ActionURL(DeleteWebPartAction.class, c);
            url.addParameter("pageId", webPart.getPageId());
            url.addParameter("index", String.valueOf(webPart.getIndex()));
            url.addReturnURL(returnURL);
            return url;
        }

        public ActionURL getExpandCollapseURL(Container c, String path, String treeId)
        {
            ActionURL url = new ActionURL(ExpandCollapseAction.class, c);
            url.addParameter("path", path);
            url.addParameter("treeId", treeId);

            return url;
        }
    }


    public ProjectController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }

    ActionURL homeURL()
    {
        return new ActionURL(BeginAction.class, ContainerManager.getHomeContainer());
    }

    ActionURL beginURL()
    {
        return new ActionURL(BeginAction.class, getContainer());
    }

    @RequiresNoPermission
    public class StartAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Container c = getContainer();

            if (c.isProject())
            {
                // This block is to handle the case where a user does not have permissions
                // to a project folder, but does have access to a subfolder.  In this case, we'd like
                // to let them see something at the project root (since this is where you land after
                // selecting the project from the projects menu), so we display an access-denied
                // message within the frame.  If the user isn't logged on, we simply show an
                // access-denied error.  This is necessary to force the login prompt to show up
                // for users with access who simply haven't logged on yet.  (brittp, 5.4.2007)
                if (!c.hasPermission(getUser(), ReadPermission.class))
                {
                    if (getUser().isGuest())
                        HttpView.throwUnauthorized();
                    return new HtmlView("You do not have permission to view this folder.<br>" +
                            "Please select another folder from the tree to the left.");
                }
            }
            return HttpView.redirect(c.getStartURL(getViewContext()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(getContainer().getName());
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Container c = getContainer();
            if (null == c)
            {
                HttpView.throwNotFound();
                return null;
            }
            
            boolean appendPath = true;
            String title;
            FolderType folderType = c.getFolderType();
            if (!FolderType.NONE.equals(c.getFolderType()))
            {
                title = folderType.getStartPageLabel(getViewContext());
            }
            else if (c.equals(ContainerManager.getHomeContainer()))
            {
                title = LookAndFeelProperties.getInstance(c).getShortName();
                appendPath = false;
            }
            else
                title = c.getName();

            ActionURL url = getViewContext().getActionURL();
            if (null == url || url.getExtraPath().equals("/"))
                return HttpView.redirect(homeURL());

            PageConfig page = getPageConfig();
            if (title != null)
                page.setTitle(title, appendPath);
            HttpView template = new HomeTemplate(getViewContext(), c, new VBox(), page, new NavTree[0]);

            Portal.populatePortalView(getViewContext(), c.getId(), template);
            
            getPageConfig().setTemplate(PageConfig.Template.None);
            return template;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }
    

    /**
     * Same as begin, but used for our home page. We retain the action
     * for compatibility with old bookmarks, but redirect so doesn't show
     * up any more...
     */
    @RequiresNoPermission
    public class HomeAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return HttpView.redirect(beginURL());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    static final Path pipeline = new Path("@pipeline");
    static final Path files = new Path("@files");

    @RequiresNoPermission
    public class FileBrowserAction extends RedirectAction
    {
        ActionURL _redirect;

        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return _redirect;
        }

        @Override
        public boolean doAction(Object o, BindException errors) throws Exception
        {
            String p = StringUtils.trimToEmpty(getViewContext().getRequest().getParameter("path"));
            Path path = Path.decode(p);
            Container c = getViewContext().getContainer();
            Path containerPath = c.getParsedPath();
            Path webdavPath = WebdavService.getPath().append(containerPath);

            if (path.startsWith(webdavPath))
                path = webdavPath.relativize(path);

            if (path.startsWith(pipeline))
            {
                path = pipeline.relativize(path);
                _redirect = new ActionURL("pipeline", "browse", c).addParameter("path",path.encode());
            }
            else if (path.startsWith(files))
            {
                path = files.relativize(path);
                _redirect = new ActionURL("filecontent", "begin", c).addParameter("path",path.encode());
            }
            else
            {
                HttpView.throwNotFound();
            }
            return true;
        }

        @Override
        public void validateCommand(Object target, Errors errors)
        {
        }
    }
    

    @RequiresPermissionClass(AdminPermission.class)
    public class MoveWebPartAction extends FormViewAction<MovePortletForm>
    {
        public void validateCommand(MovePortletForm target, Errors errors)
        {
        }

        public ModelAndView getView(MovePortletForm movePortletForm, boolean reshow, BindException errors) throws Exception
        {
            // UNDONE: this seems to be used a link, fix to make POST
            handlePost(movePortletForm,errors);
            return HttpView.redirect(getSuccessURL(movePortletForm));
        }

        public boolean handlePost(MovePortletForm form, BindException errors) throws Exception
        {
            Portal.WebPart[] parts = Portal.getParts(form.getPageId());
            if (null == parts)
                return true;

            //Find the portlet. Theoretically index should be 1-based & consecutive, but
            //code defensively.
            int i;
            int index = form.getIndex();
            for (i = 0; i < parts.length; i++)
                if (parts[i].getIndex() == index)
                    break;

            if (i == parts.length)
                return true;

            Portal.WebPart part = parts[i];
            String location = part.getLocation();
            if (form.getDirection() == Portal.MOVE_UP)
            {
                for (int j = i - 1; j >= 0; j--)
                {
                    if (location.equals(parts[j].getLocation()))
                    {
                        int newIndex = parts[j].getIndex();
                        part.setIndex(newIndex);
                        parts[j].setIndex(index);
                        break;
                    }
                }
            }
            else
            {
                for (int j = i + 1; j < parts.length; j++)
                {
                    if (location.equals(parts[j].getLocation()))
                    {
                        int newIndex = parts[j].getIndex();
                        part.setIndex(newIndex);
                        parts[j].setIndex(index);
                        break;
                    }
                }
            }

            Portal.saveParts(form.getPageId(), parts);
            return true;
        }

        public URLHelper getSuccessURL(MovePortletForm movePortletForm)
        {
            return movePortletForm.getReturnURLHelper(beginURL());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class AddWebPartAction extends FormViewAction<AddWebPartForm>
    {
        WebPartFactory _desc = null;
        Portal.WebPart _newPart = null;
        
        public void validateCommand(AddWebPartForm target, Errors errors)
        {
        }

        public ModelAndView getView(AddWebPartForm addWebPartForm, boolean reshow, BindException errors) throws Exception
        {
            // UNDONE: this seems to be used a link, fix to make POST
            handlePost(addWebPartForm,errors);
            return HttpView.redirect(getSuccessURL(addWebPartForm));
        }

        public boolean handlePost(AddWebPartForm form, BindException errors) throws Exception
        {
            _desc = Portal.getPortalPart(form.getName());
            if (null == _desc)
                return true;

            _newPart = Portal.addPart(getContainer(), _desc, form.getLocation());
            return true;
        }

        public URLHelper getSuccessURL(AddWebPartForm form)
        {
            if (null != _desc && _desc.isEditable() && _desc.showCustomizeOnInsert())
            {
                return new ActionURL(CustomizeWebPartAction.class, getContainer())
                        .addParameter("pageId", form.getPageId())
                        .addParameter("index", ""+_newPart.getIndex());
            }
            else
                return form.getReturnURLHelper();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class DeleteWebPartAction extends FormViewAction<CustomizePortletForm>
    {
        public void validateCommand(CustomizePortletForm target, Errors errors)
        {
        }

        public ModelAndView getView(CustomizePortletForm customizePortletForm, boolean reshow, BindException errors) throws Exception
        {
            // UNDONE: this seems to be used a link, fix to make POST
            handlePost(customizePortletForm,errors);
            return HttpView.redirect(getSuccessURL(customizePortletForm));
        }

        public boolean handlePost(CustomizePortletForm form, BindException errors) throws Exception
        {
            Portal.WebPart[] parts = Portal.getParts(form.getPageId());
            //Changed on us..
            if (null == parts || parts.length == 0)
                return true;

            ArrayList<Portal.WebPart> newParts = new ArrayList<Portal.WebPart>();
            int index = form.getIndex();
            for (Portal.WebPart part : parts)
                if (part.getIndex() != index)
                    newParts.add(part);

            Portal.saveParts(form.getPageId(), newParts.toArray(new Portal.WebPart[newParts.size()]));
            return true;
        }

        public URLHelper getSuccessURL(CustomizePortletForm customizePortletForm)
        {
            return customizePortletForm.getReturnURLHelper(beginURL());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresSiteAdmin()
    public class PurgeAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            int rows = Portal.purge();
            return new HtmlView("deleted " + rows + " portlets<br>");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    //
    // FORMS
    //

    public static class AddWebPartForm extends ReturnUrlForm
    {
        private String pageId;
        private String location;
        private String name;

        public String getPageId()
        {
            return pageId;
        }

        public void setPageId(String pageId)
        {
            this.pageId = pageId;
        }

        public String getLocation()
        {
            return location;
        }

        public void setLocation(String location)
        {
            this.location = location;
        }


        public void setName(String name)
        {
            this.name = name;
        }


        public String getName()
        {
            return this.name;
        }
    }

    public static class CustomizePortletForm extends ReturnUrlForm
    {
        private int index;
        private String pageId;

        public int getIndex()
        {
            return index;
        }

        public void setIndex(int index)
        {
            this.index = index;
        }

        public String getPageId()
        {
            return pageId;
        }

        public void setPageId(String pageId)
        {
            this.pageId = pageId;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class CustomizeWebPartAction extends FormViewAction<CustomizePortletForm>
    {
        Portal.WebPart _webPart;

        public void validateCommand(CustomizePortletForm target, Errors errors)
        {
        }

        public ModelAndView getView(CustomizePortletForm form, boolean reshow, BindException errors) throws Exception
        {
            _webPart = Portal.getPart(form.getPageId(), form.getIndex());
            if (null == _webPart)
            {
                if (errors.hasErrors())
                    return new JspView<Object>("/org/labkey/portal/customizeErrors.jsp", null, errors);
                else
                    return HttpView.redirect(beginURL());
            }

            WebPartFactory desc = Portal.getPortalPart(_webPart.getName());
            assert (null != desc);
            if (null == desc)
                return HttpView.redirect(beginURL());

            HttpView v = desc.getEditView(_webPart, getViewContext());
            if (null == v)
                return HttpView.redirect(beginURL());

            return v;
        }

        public boolean handlePost(CustomizePortletForm form, BindException errors) throws Exception
        {
            Portal.WebPart webPart = Portal.getPart(form.getPageId(), form.getIndex());
            if (null == webPart)
            {
                //the web part no longer exists--probably because another admin has deleted it
                errors.reject(null, "The web part you are trying to customize no longer exists. It may have been removed by another administrator.");
                return false;
            }

            // Security check -- subclasses may have overridden isEditable for certain types of users
            WebPartFactory desc = Portal.getPortalPart(webPart.getName());
            if (!desc.isEditable())
                throw new IllegalStateException("This is not an editable web part");

            populatePropertyMap(getViewContext().getRequest(), webPart);
            Portal.updatePart(getUser(), webPart);
            return true;
        }

        // UNDONE: use getBindPropertyValues()
        protected void populatePropertyMap(HttpServletRequest request, Portal.WebPart webPart)
        {
            Map<String, String> props = webPart.getPropertyMap();
            props.clear();

            // Loop once to find field markers -- this ensures, for example, that unchecked check boxes get set to 0
            Enumeration params = request.getParameterNames();

            while (params.hasMoreElements())
            {
                String name = (String) params.nextElement();

                if (name.startsWith(SpringActionController.FIELD_MARKER))
                    props.put(name.substring(SpringActionController.FIELD_MARKER.length()), "0");
            }

            params = request.getParameterNames();

            // TODO: Clean this up. Type checking... (though type conversion also must be done by the webpart)
            while (params.hasMoreElements())
            {
                String s = (String) params.nextElement();
                if (!"index".equals(s) && !"pageId".equals(s) && !"x".equals(s) && !"y".equals(s) && !ReturnUrlForm.Params.returnUrl.name().equals(s))
                {
                    String value = request.getParameter(s);
                    if (value != null && !"".equals(value.trim()))
                        webPart.getPropertyMap().put(s, value.trim());
                }
            }
        }

        public ActionURL getSuccessURL(CustomizePortletForm customizePortletForm)
        {
            Portal.WebPart webPart = Portal.getPart(customizePortletForm.getPageId(), customizePortletForm.getIndex());
            //TODO: This is a hack to get this working, should use returnUrl and pass through in webpart
            if ("menubar".equals(webPart.getLocation()))
            {
                ActionURL url = PageFlowUtil.urlProvider(AdminUrls.class).getProjectSettingsURL(getContainer());
                url.addParameter(TabStripView.TAB_PARAM, "menubar");
                return url;
            }
            return beginURL();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return (new BeginAction()).appendNavTrail(root)
                    .addChild("Customize " + (null != _webPart ? _webPart.getName() : "Web Part"));
        }
    }


    public static class MovePortletForm extends CustomizePortletForm
    {
        private int direction;

        public int getDirection()
        {
            return direction;
        }

        public void setDirection(int direction)
        {
            this.direction = direction;
        }
    }


    @RequiresNoPermission
    public static class ExpandCollapseAction extends SimpleViewAction<CollapseExpandForm>
    {
        public ModelAndView getView(CollapseExpandForm form, BindException errors) throws Exception
        {
            NavTreeManager.expandCollapsePath(getViewContext(), form.getTreeId(), form.getPath(), form.isCollapse());
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static class CollapseExpandForm
    {
        private boolean collapse;
        private String path;
        private String treeId;

        public boolean isCollapse()
        {
            return collapse;
        }

        public void setCollapse(boolean collapse)
        {
            this.collapse = collapse;
        }

        public String getTreeId()
        {
            return treeId;
        }

        public void setTreeId(String treeId)
        {
            this.treeId = treeId;
        }

        public String getPath()
        {
            return path;
        }

        public void setPath(String path)
        {
            this.path = path;
        }
    }

    private static CaseInsensitiveHashMap<WebPartView.FrameType> _frameTypeMap = new CaseInsensitiveHashMap<WebPartView.FrameType>();
    {
        _frameTypeMap.put("div", WebPartView.FrameType.DIV);
        _frameTypeMap.put("portal", WebPartView.FrameType.PORTAL);
        _frameTypeMap.put("none", WebPartView.FrameType.NONE);
        _frameTypeMap.put("false", WebPartView.FrameType.NONE);
        _frameTypeMap.put("0", WebPartView.FrameType.NONE);
        _frameTypeMap.put("dialog", WebPartView.FrameType.DIALOG);
        _frameTypeMap.put("left-nav", WebPartView.FrameType.LEFT_NAVIGATION);
        _frameTypeMap.put("leftNav", WebPartView.FrameType.LEFT_NAVIGATION);
        _frameTypeMap.put("left-navigation", WebPartView.FrameType.LEFT_NAVIGATION);
        _frameTypeMap.put("leftNavigation", WebPartView.FrameType.LEFT_NAVIGATION);
        _frameTypeMap.put("title", WebPartView.FrameType.TITLE);
    }

    public static class GetWebPartForm implements CustomApiForm
    {
        private Map<String,Object> _extendedProperites;

        public void bindProperties(Map<String, Object> props)
        {
            _extendedProperites = props;
        }

        public Map<String, Object> getExtendedProperites()
        {
            return _extendedProperites;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetWebPartAction extends ApiAction<GetWebPartForm>
    {
        public static final String PARAM_WEBPART = "webpart.name";

        public ApiResponse execute(GetWebPartForm form, BindException errors) throws Exception
        {
            HttpServletRequest request = getViewContext().getRequest();
            String qs = request.getQueryString();
            String webPartName = request.getParameter(PARAM_WEBPART);

            if(null == webPartName || webPartName.length() == 0)
                throw new IllegalArgumentException("You must provide a value for the " + PARAM_WEBPART + " parameter!");

            WebPartFactory factory = Portal.getPortalPartCaseInsensitive(webPartName);
            if(null == factory)
                throw new RuntimeException("Couldn't get the web part factory for web part '" + webPartName + "'!");

            Portal.WebPart part = factory.createWebPart();
            if(null == part)
                throw new RuntimeException("Couldn't create web part '" + webPartName + "'!");

            part.setProperties(qs);
            part.setExtendedProperties(form.getExtendedProperites());

            WebPartView view = Portal.getWebPartViewSafe(factory, getViewContext(), part);
            if (null == view)
                throw new RuntimeException("Couldn't create web part view for part '" + webPartName + "'!");

            String frame = StringUtils.trimToEmpty(request.getParameter("webpart.frame"));
            if(null != frame && frame.length() > 0 && _frameTypeMap.containsKey(frame))
                view.setFrame(_frameTypeMap.get(frame));

            String bodyClass = StringUtils.trimToEmpty(request.getParameter("webpart.bodyClass"));
            if(null != bodyClass && bodyClass.length() > 0)
                view.setBodyClass(bodyClass);

            String title = StringUtils.trimToEmpty(request.getParameter("webpart.title"));
            if(null != title && title.length() > 0)
                view.setTitle(title);

            String titleHref = StringUtils.trimToEmpty(request.getParameter("webpart.titleHref"));
            if(null != titleHref && titleHref.length() > 0)
                view.setTitleHref(titleHref);

            view.render(request, getViewContext().getResponse());
            return null;
        }
    }


    public static class GetContainersForm
    {
        private boolean _includeSubfolders = false;
        private int _depth = Integer.MAX_VALUE;

        public boolean isIncludeSubfolders()
        {
            return _includeSubfolders;
        }

        public void setIncludeSubfolders(boolean includeSubfolders)
        {
            _includeSubfolders = includeSubfolders;
        }

        public int getDepth()
        {
            return _depth;
        }

        public void setDepth(int depth)
        {
            _depth = depth;
        }
    }

    /**
     * Returns all contains visible to the current user
     */
    @RequiresNoPermission
    public class GetContainersAction extends ApiAction<GetContainersForm>
    {
        int _requestedDepth;

        public ApiResponse execute(GetContainersForm form, BindException errors) throws Exception
        {
            _requestedDepth = form.getDepth();
            ApiSimpleResponse response = new ApiSimpleResponse();
            response.putAll(getContainerProps(getViewContext().getContainer(),
                    getViewContext().getUser(), form.isIncludeSubfolders(), 0));
            return response;
        }

        protected Map<String, Object> getContainerProps(Container container, User user, boolean recurse, int depth)
        {
            Map<String, Object> containerProps = new HashMap<String,Object>();
            containerProps.put("name", container.getName());
            containerProps.put("id", container.getId());
            containerProps.put("path", container.getPath());
            containerProps.put("sortOrder", container.getSortOrder());
            containerProps.put("userPermissions", container.getPolicy().getPermsAsOldBitMask(user));
            containerProps.put("effectivePermissions", container.getPolicy().getPermissionNames(user));
            if (null != container.getDescription())
                containerProps.put("description", container.getDescription());
            containerProps.put("isWorkbook", container.isWorkbook());
            if (null != container.getTitle())
                containerProps.put("title", container.getTitle());

            //recurse into children if requested
            if (recurse && depth < _requestedDepth)
                containerProps.put("children", getContainers(container, user, recurse, depth+1));

            return containerProps;
        }

        protected List<Map<String,Object>> getContainers(Container parent, User user, boolean recurse, int depth)
        {
            List<Map<String,Object>> containersProps = new ArrayList<Map<String,Object>>();
            for(Container child : parent.getChildren())
            {
                if (!child.hasPermission(user, ReadPermission.class))
                    continue;

                containersProps.add(getContainerProps(child, user, recurse, depth));
            }

            return containersProps;
        }
    }


    @RequiresNoPermission
    public class IconAction extends SimpleViewAction<Object>
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            String name = StringUtils.trimToEmpty(getViewContext().getRequest().getParameter("name"));
            String path;
            if (name.endsWith("/"))
                path = "/" + PageFlowUtil.extJsRoot() + "/resources/images/default/tree/folder.gif";
            else
                path = Attachment.getFileIcon(name);

            // allow caching
            getViewContext().getResponse().setDateHeader("Expires", HeartBeat.currentTimeMillis() + 1000 * 60 * 60 * 24);
            getViewContext().getResponse().setHeader("Cache-Control", "private");

            RequestDispatcher d = getViewContext().getRequest().getRequestDispatcher(path);
            getPageConfig().setTemplate(PageConfig.Template.None);
            d.forward(getViewContext().getRequest(), getViewContext().getResponse());
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }
}

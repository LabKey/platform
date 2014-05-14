/*
 * Copyright (c) 2004-2014 Fred Hutchinson Cancer Research Center
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

package org.labkey.wiki;

import org.apache.commons.collections15.MultiMap;
import org.apache.commons.collections15.multimap.MultiHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.ExtFormAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentForm;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Table;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.DeveloperRole;
import org.labkey.api.security.roles.OwnerRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.ContainerTreeSelected;
import org.labkey.api.util.DiffMatchPatch;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TextExtractor;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.GridView;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.LinkBarView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NavTreeManager;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.Portal;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.HomeTemplate;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.wiki.FormattedHtml;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.wiki.model.Wiki;
import org.labkey.wiki.model.WikiEditModel;
import org.labkey.wiki.model.WikiTree;
import org.labkey.wiki.model.WikiVersion;
import org.labkey.wiki.model.WikiVersionsGrid;
import org.labkey.wiki.model.WikiView;
import org.labkey.wiki.permissions.IncludeScriptPermission;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class WikiController extends SpringActionController
{
    private static final Logger LOG = Logger.getLogger(WikiController.class);
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(WikiController.class);
    private static final boolean SHOW_CHILD_REORDERING = false;

    public WikiController()
    {
        setActionResolver(_actionResolver);
    }


    protected BaseWikiPermissions getPermissions()
    {
        //if we need to do anything more complicated, return various derived classes based on context
        return new BaseWikiPermissions(getUser(), getContainer());
    }


    @Override
    protected ModelAndView getTemplate(ViewContext context, ModelAndView mv, Controller action, PageConfig page)
    {
        ModelAndView template = super.getTemplate(context, mv, action, page);

        if (template instanceof HomeTemplate && !(action instanceof EditWikiAction))
        {
            WebPartView toc = new WikiTOC(context);
            page.addClientDependencies(toc.getClientDependencies());

            SearchService ss = ServiceRegistry.get().getService(SearchService.class);

            if (null == ss)
            {
                ((HomeTemplate)template).setView(WebPartFactory.LOCATION_RIGHT, toc);
            }
            else
            {
                WebPartView searchView = ss.getSearchView(false, 0, false, true);

                ((HomeTemplate)template).setView(WebPartFactory.LOCATION_RIGHT, new VBox(searchView, toc));
            }
        }

        return template;
    }


    public static class CustomizeWikiPartView extends JspView<Portal.WebPart>
    {
        private List<Container> _containerList;
        private Map<HString, HString> _containerNameTitleMap;

        public CustomizeWikiPartView(Portal.WebPart webPart)
        {
            super("/org/labkey/wiki/view/customizeWiki.jsp", webPart);
        }

        @Override
        public void prepareWebPart(Portal.WebPart webPart) throws ServletException
        {
            super.prepareWebPart(webPart);

            ViewContext context = getViewContext();

            try
            {
                //get all containers that include wiki pages
                _containerList = populateWikiContainerList(context);
                if (!_containerList.contains(context.getContainer()))
                    _containerList.add(0, context.getContainer());

                //get wiki page list for the currently stored container (or current container if null)
                Container cStored;

                if (webPart == null)
                    cStored = context.getContainer();
                else
                {
                    String id = webPart.getPropertyMap().get("webPartContainer");
                    //is this still a valid container id? if not, use current container
                    //also use the current container if the stored container doesn't
                    //have any pages left in it
                    cStored = id == null ? context.getContainer() : ContainerManager.getForId(id);

                    if (cStored == null || !_containerList.contains(cStored))
                    {
                        cStored = context.getContainer();

                        //reset the webPartContainer property so that customizeWiki.jsp selects the correct one in the UI
                        webPart.getPropertyMap().put("webPartContainer", cStored.getId());
                    }
                }

                _containerNameTitleMap = WikiSelectManager.getNameTitleMap(cStored);
            }
            catch(SQLException e)
            {
                throw new RuntimeException("Failed to populate container or page list.", e);
            }
        }

        public List<Container> getContainerList()
        {
            return _containerList;
        }

        public Map<HString, HString> getContainerNameTitleMap()
        {
            return _containerNameTitleMap;
        }
    }

    public static List<Container> populateWikiContainerList(ViewContext context)
            throws SQLException, ServletException
    {
        //retrieve all containers
        MultiMap<Container, Container> mm = ContainerManager.getContainerTree();

        //get wikis for containers recursively
        List<Container> children = new ArrayList<>();
        populateWikiContainerListRecursive(context, ContainerManager.getRoot(), children, mm);

        return children;
    }

    private static void populateWikiContainerListRecursive(ViewContext context, Container c, List<Container> children, MultiMap<Container, Container> mm)
            throws SQLException, ServletException
    {
        //get a list of containers in root and add to arraylist
        Collection<Container> arrCont = mm.get(c);

        //check for children
        if (arrCont != null)
        {
            for (Container cChild : arrCont)
            {
                //does user have permissions to read this container?
                //add this container if it contains any wiki pages or if it's the current container
                if (cChild.hasPermission(context.getUser(), ReadPermission.class) &&
                        (cChild.getId().equals(context.getContainer().getId()) || WikiSelectManager.hasPages(cChild)))
                {
                    children.add(cChild);
                }

                //check container's children
                populateWikiContainerListRecursive(context, cChild, children, mm);
            }
        }
    }


    private ActionURL getBeginURL(Container c)
    {
        return getPageURL(getDefaultPage(c), c);
    }


    public static ActionURL getPageURL(Container c, @Nullable HString name)
    {
        return getWikiURL(c, PageAction.class, name);
    }


    public static ActionURL getWikiURL(Container c, Class<? extends Controller> actionClass, @Nullable HString name)
    {
        ActionURL url = new ActionURL(actionClass, c);

        if (null != name)
            url.addParameter("name", name.getSource());

        return url;
    }


    /**
     * This method represents the point of entry into the pageflow
     */
    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {
        @SuppressWarnings("UnusedDeclaration")
        public BeginAction()
        {
        }

        public BeginAction(ViewContext ctx)
        {
            setViewContext(ctx);
        }

        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return HttpView.redirect(getUrl());
        }

        public ActionURL getUrl()
        {
            return getBeginURL(getContainer());
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Start Page", getUrl());
        }
    }


    public static Wiki getDefaultPage(Container c)
    {
        //look for page named "default"
        Wiki wiki = WikiSelectManager.getWiki(c, new HString("default"));

        //handle case where no page named "default" exists by getting first page in ordered list
        //note that this does not automatically display page selected on web part customize. should it?
        if (wiki == null)
        {
            if (!WikiSelectManager.hasPages(c))
            {
                wiki = new Wiki(c, new HString("default"));
            }
            else
            {
                HString firstName = WikiSelectManager.getPageNames(c).iterator().next();
                wiki = WikiSelectManager.getWiki(c, firstName);
            }
        }

        return wiki;
    }


    @RequiresPermissionClass(ReadPermission.class) //will test explicitly below
    public class DeleteAction extends ConfirmAction<WikiNameForm>
    {
        private Wiki _wiki = null;

        @Override
        public String getConfirmText()
        {
            return "Delete";
        }

        @Override
        public ModelAndView getConfirmView(WikiNameForm form, BindException errors) throws Exception
        {
            _wiki = WikiSelectManager.getWiki(getContainer(), form.getName());
            if (null == _wiki)
                throw new NotFoundException();

            BaseWikiPermissions perms = getPermissions();
            if (!perms.allowDelete(_wiki))
                throw new UnauthorizedException("You do not have permissions to delete this wiki page");

            return new JspView<>("/org/labkey/wiki/view/wikiDelete.jsp", _wiki);
        }

        @Override
        public boolean handlePost(WikiNameForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            _wiki = WikiSelectManager.getWiki(c, form.getName());
            if (null == _wiki)
                throw new NotFoundException();

            BaseWikiPermissions perms = getPermissions();
            if (!perms.allowDelete(_wiki))
                throw new UnauthorizedException("You do not have permissions to delete this wiki page");

            try
            {
                //delete page and all versions
                getWikiManager().deleteWiki(getUser(), c, _wiki);
            }
            catch (Table.OptimisticConflictException e)
            {
                // Issue 13549: if someone else already deleted the wiki, no need to throw exception
            }
            return true;
        }

        @Override
        public void validateCommand(WikiNameForm wikiNameForm, Errors errors)
        {
        }

        @Override
        public ActionURL getSuccessURL(WikiNameForm wikiNameForm)
        {
            return new BeginAction(getViewContext()).getUrl();
        }

        @Override
        public ActionURL getCancelUrl()
        {
            return new PageAction(getViewContext(), _wiki, null).getUrl();
        }

        @Override
        public ActionURL getFailURL(WikiNameForm wikiNameForm, BindException errors)
        {
            return new ManageAction(getViewContext(), _wiki).getUrl();
        }
    }


    @RequiresPermissionClass(ReadPermission.class) //will test explicitly below
    public class ManageAction extends FormViewAction<WikiManageForm>
    {
        private Wiki _wiki = null;
        private WikiVersion _wikiVersion = null;

        @SuppressWarnings({"UnusedDeclaration"})
        public ManageAction()
        {
        }

        ManageAction(ViewContext ctx, Wiki wiki)
        {
            setViewContext(ctx);
            _wiki = wiki;
        }

        public ModelAndView getView(WikiManageForm form, boolean reshow, BindException errors) throws Exception
        {
            HString name = form.getName();

            if (name == null || (errors != null && errors.getErrorCount()>0))
                name = form.getOriginalName();

            _wiki = WikiSelectManager.getWiki(getContainer(), name);

            if (null == _wiki)
                throw new NotFoundException();

            BaseWikiPermissions perms = getPermissions();

            if (!perms.allowUpdate(_wiki))
                throw new UnauthorizedException("You do not have permissions to manage this wiki page");

            ManageBean bean = new ManageBean();
            bean.wiki = _wiki;
            bean.pageNames = WikiSelectManager.getPageNames(getContainer());
            bean.siblings = WikiSelectManager.getChildren(getContainer(), _wiki.getParent());
            bean.possibleParents = WikiSelectManager.getPossibleParents(getContainer(), _wiki);
            bean.showChildren = SHOW_CHILD_REORDERING;

            HttpView manageView = new JspView<>("/org/labkey/wiki/view/wikiManage.jsp", bean, errors);
            getPageConfig().setFocusId("name");

            return manageView;
        }


        public class ManageBean
        {
            public Wiki wiki;
            public List<HString> pageNames;
            public Collection<WikiTree> siblings;
            public Set<WikiTree> possibleParents;
            public boolean showChildren;
        }


        public boolean handlePost(WikiManageForm form, BindException errors) throws Exception
        {
            HString originalName = form.getOriginalName();
            HString newName = form.getName();
            Container c = getContainer();
            _wiki = WikiSelectManager.getWiki(c, originalName);

            if (null == _wiki)
            {
                throw new NotFoundException();
            }

            BaseWikiPermissions perms = getPermissions();
            if (!perms.allowUpdate(_wiki))
                throw new UnauthorizedException("You do not have permissions to manage this wiki page");

            // Get the latest version based on previous properties
            WikiVersion versionOld = _wiki.getLatestVersion();

            // Now update wiki with newly submitted properties  TODO: Should clone wiki instead of changing cached copy (e.g., for concurrency and in case something goes wrong with update)
            _wiki.setName(newName);
            _wiki.setParent(form.getParent());
            _wiki.setShouldIndex(form.isShouldIndex());
            HString title = form.getTitle() == null ? newName : form.getTitle();

            //update version only if title has changed
            if (versionOld.getTitle().compareTo(title) != 0)
            {
                // Make a copy, otherwise we're changing the cached copy, and updateWiki() will think nothing changed.
                _wikiVersion = new WikiVersion(versionOld);
                _wikiVersion.setTitle(title);
            }
            else
            {
                _wikiVersion = null;
            }

            getWikiManager().updateWiki(getUser(), _wiki, _wikiVersion);

            if (SHOW_CHILD_REORDERING)
            {
                int[] childOrder = form.getChildOrderArray();
                if (childOrder.length > 0)
                    updateDisplayOrder(_wiki.children(), childOrder);
            }

            int[] siblingOrder = form.getSiblingOrderArray();

            if (siblingOrder.length > 0)
            {
                List<Wiki> siblings = WikiSelectManager.getChildWikis(getContainer(), _wiki.getParent());
                updateDisplayOrder(siblings, siblingOrder);
            }

            return true;
        }

        public void validateCommand(WikiManageForm wikiManageForm, Errors errors)
        {
            wikiManageForm.validate(errors);
        }

        public ActionURL getSuccessURL(WikiManageForm form)
        {
            HString nextAction = form.getNextAction();
            ActionURL nextPage = getViewContext().cloneActionURL();
            nextPage.setAction(nextAction == null || nextAction.isEmpty() ? "page" : nextAction.getSource());
            nextPage.deleteParameters();
            nextPage.addParameter("name", form.getName());
            return nextPage;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("wikiUserGuide#manage");
            if (null == _wikiVersion)
                _wikiVersion = _wiki.getLatestVersion();
            return (new PageAction(getViewContext(), _wiki, _wikiVersion).appendNavTrail(root))
                    .addChild("Manage \"" + _wikiVersion.getTitle().getSource() + "\"");
        }

        public ActionURL getUrl()
        {
            return new ActionURL(ManageAction.class, getContainer()).addParameter("name", _wiki.getName());
        }
    }


    private void updateDisplayOrder(List<Wiki> pages, int[] order)
    {
        if (!verifyOrder(pages, order))
        {
            for (int i = 0; i < order.length; i++)
            {
                for (Wiki sibling : pages)
                {
                    if (sibling.getRowId() == order[i])
                    {
                        sibling.setDisplayOrder(i + 1);
                        getWikiManager().updateWiki(getUser(), sibling, null);
                        break;
                    }
                }
            }
        }
    }


    private boolean verifyOrder(List<Wiki> wikis, int[] ids)
    {
        if (ids.length != wikis.size())
            return false;
        int i = 0;
        for (Wiki page : wikis)
        {
            if (page.getRowId() != ids[i++])
                return false;
        }
        return true;
    }

    private Wiki getWiki(AttachmentForm form) throws ServletException, SQLException
    {
        Wiki wiki = getWikiManager().getWikiByEntityId(getContainer(), form.getEntityId());
        if (null == wiki)
            throw new NotFoundException("Unable to find wiki page");

        return wiki;
    }


    //
    // CONSIDER: roll these up into one action with a switch!
    // (two actually, download() for backwards compatibility
    //
    // use FormViewAction since we need to handle files

    public abstract class AttachmentAction extends FormViewAction<AttachmentForm>
    {
        AttachmentAction()
        {
            super(AttachmentForm.class);
        }

        public ModelAndView getView(AttachmentForm form, boolean reshow, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.None);
            Wiki wiki = getWiki(form);
            return getAttachmentView(form, wiki);
        }

        public boolean handlePost(AttachmentForm attachmentForm, BindException errors) throws Exception
        {
            return true;
        }

        public ActionURL getSuccessURL(AttachmentForm attachmentForm)
        {
            return null;
        }

        public abstract ModelAndView getAttachmentView(AttachmentForm form, Wiki wiki) throws Exception;

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }

        public void validateCommand(AttachmentForm target, Errors errors)
        {
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class DownloadAction extends AttachmentAction
    {
        public ModelAndView getAttachmentView(final AttachmentForm form, final Wiki wiki) throws Exception
        {
            return new HttpView()
            {
                protected void renderInternal(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
                {
                    AttachmentService.get().download(response, wiki, form.getName());
                }
            };
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class PrintAllAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Container c = getContainer();
            Set<WikiTree> wikiTrees = WikiSelectManager.getWikiTrees(c);

            JspView v = new JspView<>("/org/labkey/wiki/view/wikiPrintAll.jsp", new PrintAllBean(wikiTrees));
            v.setFrame(WebPartView.FrameType.NONE);

            getPageConfig().setTemplate(PageConfig.Template.Print);

            return v;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Print all pages in " + getContainer().getPath());
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class PrintBranchAction extends SimpleViewAction<WikiNameForm>
    {
        private Wiki _rootWiki;

        public ModelAndView getView(WikiNameForm form, BindException errors) throws Exception
        {
            Container c = getContainer();

            if (null == form.getName() || form.getName().trim().length() == 0)
                throw new NotFoundException("You must supply a page name!");

            _rootWiki = WikiSelectManager.getWiki(c, form.getName());
            if (null == _rootWiki)
                throw new NotFoundException("The wiki page named '" + form.getName() + "' was not found.");

            // build a set of all descendants of the root page
            Set<WikiTree> wikiTrees = WikiSelectManager.getWikiTrees(c, _rootWiki);

            JspView v = new JspView<>("/org/labkey/wiki/view/wikiPrintAll.jsp", new PrintAllBean(wikiTrees));
            v.setFrame(WebPartView.FrameType.NONE);
            getPageConfig().setTemplate(PageConfig.Template.Print);

            return v;
        }
        
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Print of " + _rootWiki.getLatestVersion().getTitle() + " and Descendants");
        }
    }


    public class PrintAllBean
    {
        public Set<WikiTree> wikiTrees;
        public String displayName = getUser().getDisplayName(getUser());

        private PrintAllBean(Set<WikiTree> wikis)
        {
            this.wikiTrees = wikis;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class PrintRawAction extends SimpleViewAction<WikiNameForm>
    {
        private HString _name;

        public ModelAndView getView(WikiNameForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            _name = form.getName();
            if (_name == null)
                throw new NotFoundException();

            WikiTree tree = WikiSelectManager.getWikiTree(c, _name);

            //just want to re-use same jsp
            Set<WikiTree> wikis = Collections.singleton(tree);
            JspView v = new JspView<>("/org/labkey/wiki/view/wikiPrintRaw.jsp", new PrintRawBean(wikis));
            v.setFrame(WebPartView.FrameType.NONE);
            getPageConfig().setTemplate(PageConfig.Template.Print);

            return v;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Print Page '" + _name + "'");
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class PrintAllRawAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Container c = getContainer();
            Set<WikiTree> wikiTrees = WikiSelectManager.getWikiTrees(c);
            JspView v = new JspView<>("/org/labkey/wiki/view/wikiPrintRaw.jsp", new PrintRawBean(wikiTrees));
            v.setFrame(WebPartView.FrameType.NONE);
            getPageConfig().setTemplate(PageConfig.Template.Print);

            return v;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Print All Pages");
        }
    }


    private static List<Wiki> namesToWikis(Container c, List<HString> names)
    {
        LinkedList<Wiki> wikis = new LinkedList<>();

        for (HString name : names)
            wikis.add(WikiSelectManager.getWiki(c, name));

        return wikis;
    }


    public class PrintRawBean
    {
        public final Set<WikiTree> wikis;
        public final String displayName;

        private PrintRawBean(Set<WikiTree> wikis)
        {
            this.wikis = wikis;
            displayName = getUser().getDisplayName(getUser());
        }
    }


    public static class CopyWikiForm
    {
        private String _path;
        private String _sourceContainer;
        private String _destContainer;
        private HString _pageName;
        private boolean _overwrite;

        public HString getPageName()
        {
            return _pageName;
        }

        public void setPageName(HString pageName)
        {
            _pageName = pageName;
        }

        public String getPath()
        {
            return _path;
        }

        public void setPath(String path)
        {
            _path = path;
        }

        public String getSourceContainer()
        {
            return _sourceContainer;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSourceContainer(String sourceContainer)
        {
            _sourceContainer = sourceContainer;
        }

        public String getDestContainer()
        {
            return _destContainer;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setDestContainer(String destContainer)
        {
            _destContainer = destContainer;
        }

        public boolean isOverwrite()
        {
            return _overwrite;
        }

        public void setOverwrite(boolean overwrite)
        {
            _overwrite = overwrite;
        }
    }


    private Container getSourceContainer(String source) throws ServletException
    {
        Container cSource;
        if (source == null)
            cSource = getContainer();
        else
            cSource = ContainerManager.getForPath(source);
        return cSource;
    }


    private Container getDestContainer(String destContainer, String path)
    {
        if (destContainer == null)
        {
            destContainer = path;
            if (destContainer == null)
                return null;
        }

        //get the destination container  TODO: ensure?!?
        return ContainerManager.ensureContainer(destContainer);
    }


    private void displayWikiModuleInDestContainer(Container cDest) throws SQLException
    {
        Set<Module> activeModules = new HashSet<>(cDest.getActiveModules());
        Module module = ModuleLoader.getInstance().getModule("Wiki");

        if (module != null)
        {
            //add wiki to active modules
            activeModules.add(module);
            cDest.setActiveModules(activeModules, getUser());
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class CopyWikiAction extends FormViewAction<CopyWikiForm>
    {
        public ModelAndView getView(CopyWikiForm copyWikiForm, boolean reshow, BindException errors) throws Exception
        {
            return null;
        }

        public void validateCommand(CopyWikiForm copyWikiForm, Errors errors)
        {
        }

        public ActionURL getSuccessURL(CopyWikiForm copyWikiForm)
        {
            Container destContainer = getDestContainer(copyWikiForm.getDestContainer(), copyWikiForm.getPath());

            return getBeginURL(destContainer);
        }

        public boolean handlePost(CopyWikiForm form, BindException errors) throws Exception
        {
            //user must have admin perms on both source and destination container

            //Get source container. Handle both post and get cases.
            Container cSrc = getSourceContainer(form.getSourceContainer());
            //Get destination container. Handle both post and get cases.
            Container cDest = getDestContainer(form.getDestContainer(), form.getPath());

            //get page name if specified (indicates we are copying subtree)
            HString pageName = form.getPageName();
            //get selected page (top of subtree)
            Wiki parentPage = null;

            if (pageName != null)
            {
                parentPage = WikiSelectManager.getWiki(cSrc, pageName);

                if (parentPage == null)
                    throw new NotFoundException("No page named '" + pageName + "' exists in the source container.");
            }

            //UNDONE: currently overwrite option is not exposed in UI
            boolean overwrite = form.isOverwrite();

            if (cDest != null && cDest.hasPermission(getUser(), AdminPermission.class))
            {
                //get source wiki pages
                List<HString> srcPageNames;

                if (parentPage != null)
                    // TODO: make subtrees work; previously begetWikiManager().getSubTreePageList(cSrc, parentPage), now
                    // somethinge like WikiSelectManager.getDescendents(cSrc, name)
                    srcPageNames = WikiSelectManager.getPageNames(cSrc);
                else
                    srcPageNames = WikiSelectManager.getPageNames(cSrc);

                //get existing destination wiki page names
                List<HString> destPageNames = WikiSelectManager.getPageNames(cDest);

                //map source page row ids to new page row ids
                Map<Integer, Integer> pageIdMap = new HashMap<>();
                //shortcut for root topics
                pageIdMap.put(-1, -1);

                //copy each page in the list
                for (HString name : srcPageNames)
                {
                    Wiki srcWikiPage = WikiSelectManager.getWiki(cSrc, name);
                    getWikiManager().copyPage(getUser(), cSrc, srcWikiPage, cDest, destPageNames, pageIdMap, false);
                }

                //display the wiki module in the destination container
                displayWikiModuleInDestContainer(cDest);
            }

            return true;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class CopySinglePageAction extends SimpleRedirectAction<CopyWikiForm>
    {
        @Override
        public URLHelper getRedirectURL(CopyWikiForm form) throws Exception
        {
            HString pageName = form.getPageName();
            Container cSrc = getSourceContainer(form.getSourceContainer());
            Container cDest = getDestContainer(form.getDestContainer(), form.getPath());
            if (pageName == null || cSrc == null || cDest == null)
                throw new NotFoundException();

            //user must have admin perms on both source and destination container
            if (!cDest.hasPermission(getUser(), AdminPermission.class))
                throw new UnauthorizedException();

            Wiki srcPage = WikiSelectManager.getWiki(cSrc, pageName);
            if (srcPage == null)
                throw new NotFoundException();

            //get existing destination wiki names
            List<HString> destPageNames = WikiSelectManager.getPageNames(cDest);

            //copy single page
            Wiki newWikiPage = getWikiManager().copyPage(getUser(), cSrc, srcPage, cDest, destPageNames, null, form.isOverwrite());

            displayWikiModuleInDestContainer(cDest);

            return getPageURL(newWikiPage, cDest);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class CopyWikiLocationAction extends SimpleViewAction<CopyWikiForm>
    {
        public ModelAndView getView(CopyWikiForm form, BindException errors) throws Exception
        {
            //get projects and folders for which user has admin permissions
            Container c = getContainer();
            ActionURL currentURL = getViewContext().cloneActionURL();
            ContainerTreeSelected ct = new ContainerTreeSelected("/", getUser(), AdminPermission.class, currentURL);
            ct.setCurrent(c);
            ct.setInitialLevel(1);

            CopyBean bean = new CopyBean();
            bean.folderList = ct.render().toString();           // folder tree
            bean.destContainer = c.getPath();                   // hidden input
            bean.sourceContainer = form.getSourceContainer();   // hidden input
            Container sourceContainer = getSourceContainer(form.getSourceContainer());
            bean.cancelURL = null == sourceContainer ? getBeginURL(c) : getBeginURL(sourceContainer);

            setHelpTopic("wikiUserGuide#copy");
            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            getPageConfig().setShowHeader(true);

            return new JspView<>("/org/labkey/wiki/view/wikiCopy.jsp", bean);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            //setHelpTopic("wikiUserGuide#copy");
            return null;
        }
    }


    public class CopyBean
    {
        public String folderList;
        public String destContainer;
        public String sourceContainer;
        public ActionURL cancelURL;
    }


    private ActionURL getSourceURL(HString pageName, int version)
    {
        ActionURL url = new ActionURL(SourceAction.class, getContainer());
        url.addParameter("name", pageName);
        url.addParameter("version", version);
        return url;
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class SourceAction extends PageAction
    {
        public SourceAction()
        {
            super();
            _source = true;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class PageAction extends SimpleViewAction<WikiNameForm>
    {
        boolean _source = false;
        private Wiki _wiki = null;
        private WikiVersion _wikiversion = null;

        boolean isSource()
        {
            return _source;
        }

        public PageAction()
        {
        }

        public PageAction(ViewContext ctx, Wiki wiki, WikiVersion wikiversion)
        {
            setViewContext(ctx);
            _wiki = wiki;
            _wikiversion = wikiversion;
        }

        public ModelAndView getView(WikiNameForm form, BindException errors) throws Exception
        {
            HString name = null != form.getName() ? form.getName().trim() : null;
            //if there's no name parameter, find default page and reload with parameter.
            //default page is not necessarily same page displayed in wiki web part
            if (null == name || name.isEmpty())
                throw new RedirectException(new BeginAction(getViewContext()).getUrl());

            //page may be existing page, or may be new page
            _wiki = WikiSelectManager.getWiki(getContainer(), name);
            boolean existing = _wiki != null;

            if (null == _wiki)
            {
                _wiki = new Wiki(getContainer(), name);
                _wikiversion = new WikiVersion(name);
                //set new page title to be name.
                _wikiversion.setTitle(name);
                // check if this is a search result hit
                ServiceRegistry.get(SearchService.class).notFound(getViewContext().getActionURL());
            }
            else
            {
                int version = form.getVersion();

                if (0 == version)
                    _wikiversion = _wiki.getLatestVersion();
                else
                    _wikiversion = WikiSelectManager.getVersion(_wiki, version);

                if (_wikiversion == null)
                    throw new NotFoundException("Wiki version not found");
            }

            if (isSource())
            {
                if ("none".equals(getViewContext().getRequest().getParameter("_template")))
                {
                    getPageConfig().setTemplate(PageConfig.Template.None);
                    HtmlView html = new HtmlView(_wikiversion.getBody());
                    html.setFrame(WebPartView.FrameType.NONE);
                    html.setContentType("text/plain");
                    return html;
                }
                else
                {
                    return new HtmlView("<pre>\n"+PageFlowUtil.filter(_wikiversion.getBody())+"\n</pre>");
                }
            }
            else if (isPrint())
            {
                JspView<Wiki> view = new JspView<>("/org/labkey/wiki/view/wikiPrint.jsp", _wiki);
                view.setFrame(WebPartView.FrameType.NONE);
                return view;
            }
            else
            {
                WebPartView v = new WikiView(_wiki, _wikiversion, existing);

                // get discussion view
                if (existing)
                {
                    ActionURL pageUrl = new PageAction(getViewContext(), _wiki, _wikiversion).getUrl();
                    String discussionTitle = "discuss page - " +  _wikiversion.getTitle();
                    HttpView discussionView = getDiscussionView(_wiki.getEntityId(), pageUrl, discussionTitle);
                    v.setView("discussion", discussionView);
                    v.setShowTitle(false);
                }

                return v;
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction(getViewContext()).appendNavTrail(root);
            appendWikiTrail(root, _wiki);
            if (isSource())
                root.addChild("source");
            return root;
        }

        public void appendWikiTrail(NavTree root, Wiki wiki)
        {
            if (null == wiki)
                return;
            appendWikiTrail(root, wiki.getParentWiki());
            WikiVersion v = wiki.getLatestVersion();
            root.addChild(v == null ? wiki.getName() : v.getTitle(), getPageURL(wiki, getContainer()));
        }

        ActionURL getUrl()
        {
            return getPageURL(_wiki, getContainer());
        }
    }


    public static ActionURL getPageURL(Wiki wiki, Container c)
    {
        ActionURL url = new ActionURL(PageAction.class, c);
        return url.addParameter("name", wiki.getName());
    }


    private HttpView getDiscussionView(String objectId, ActionURL pageURL, String title) throws ServletException
    {
        DiscussionService.Service service = DiscussionService.get();
        return service.getDisussionArea(getViewContext(), objectId, pageURL, title, true, false);
    }


    private ActionURL getVersionURL(HString name)
    {
        ActionURL url = new ActionURL(VersionAction.class, getContainer());
        url.addParameter("name", name);
        return url;
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class VersionAction extends SimpleViewAction<WikiNameForm>
    {
        private Wiki _wiki = null;
        private WikiVersion _wikiversion = null;

        @SuppressWarnings({"UnusedDeclaration"})
        public VersionAction()
        {
        }

        public VersionAction(ViewContext ctx, Wiki wiki, WikiVersion wikiversion)
        {
            setViewContext(ctx);
            _wiki = wiki;
            _wikiversion = wikiversion;
        }

        public ModelAndView getView(WikiNameForm form, BindException errors) throws Exception
        {
            HString name = null != form.getName() ? form.getName().trim() : null;
            int version = form.getVersion();

            _wiki = WikiSelectManager.getWiki(getContainer(), name);
            if (null == _wiki)
                throw new NotFoundException();

            //return latest version first since it may be cached
            _wikiversion = _wiki.getLatestVersion();

            if (version > 0)
            {
                //if version requested is higher than latest version, throw not found
                if (version > _wikiversion.getVersion())
                    throw new NotFoundException();

                //if this is a valid version that is not the latest version, get that version
                if (version != _wikiversion.getVersion())
                    _wikiversion = WikiSelectManager.getVersion(_wiki, version);
            }

            if (null == _wikiversion)
                throw new NotFoundException();

            VersionBean bean = new VersionBean(_wiki, _wikiversion, getPermissions());

            getPageConfig().setNoIndex();
            getPageConfig().setNoFollow();
            return new JspView<>("/org/labkey/wiki/view/wikiVersion.jsp", bean);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            HString pageTitle = _wikiversion.getTitle();
            pageTitle = pageTitle.concat(" (Version " + _wikiversion.getVersion() + " of " + WikiSelectManager.getVersionCount(_wiki) + ")");

            return new VersionsAction(getViewContext(), _wiki, _wikiversion).appendNavTrail(root)
                    .addChild(pageTitle, getUrl());
        }

        public ActionURL getUrl()
        {
            ActionURL url = new ActionURL(VersionAction.class, getContainer());
            url.addParameter("name", _wiki.getName());
            url.addParameter("version", " "+ _wikiversion.getVersion());
            return url;
        }
    }


    public class VersionBean
    {
        public final Wiki wiki;
        public final HString title;
        public final WikiVersion wikiVersion;
        public final ActionURL pageURL;
        public final ActionURL versionsURL;
        public final ActionURL sourceURL;
        public final ActionURL makeCurrentURL;
        public final boolean hasReadPermission;
        public final boolean hasAdminPermission;
        public final boolean hasSetCurVersionPermission;
        public final String createdBy;
        public final Date created;
        public final String versionLink;            //base url for different versions of this page
        public final String compareLink;            //base url for comparing to another version

        private VersionBean(Wiki wiki, WikiVersion wikiVersion, BaseWikiPermissions perms)
        {
            this.wiki = wiki;
            this.wikiVersion = wikiVersion;

            title = wikiVersion.getTitle();
            pageURL = wiki.getPageURL();
            versionsURL = wiki.getVersionsURL();
            sourceURL = getSourceURL(wiki.getName(), wikiVersion.getVersion());
            makeCurrentURL = getMakeCurrentURL(wiki.getName(), wikiVersion.getVersion());
            hasReadPermission = perms.allowRead(wiki);
            hasAdminPermission = perms.allowAdmin();
            hasSetCurVersionPermission = perms.allowUpdate(wiki);
            createdBy = UserManager.getDisplayName(wikiVersion.getCreatedBy(), getUser());
            created = wikiVersion.getCreated();
            versionLink = getVersionURL(wiki.getName()).toString();
            compareLink = getCompareVersionsURL(wiki.getName()).toString();
        }
    }


    private ActionURL getCompareVersionsURL(HString name)
    {
        ActionURL url = new ActionURL(CompareVersionsAction.class, getContainer());
        url.addParameter("name", name);
        return url;
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class CompareVersionsAction extends SimpleViewAction<CompareForm>
    {
        private Wiki _wiki = null;
        private WikiVersion _wikiVersion1 = null;
        private WikiVersion _wikiVersion2 = null;

        public ModelAndView getView(CompareForm form, BindException errors) throws Exception
        {
            HString name = null != form.getName() ? form.getName().trim() : null;
            _wiki = WikiSelectManager.getWiki(getContainer(), name);

            if (null == _wiki)
                throw new NotFoundException();

            _wikiVersion1 = WikiSelectManager.getVersion(_wiki, form.getEarlierVersion());

            if (null == _wikiVersion1)
                throw new NotFoundException();

            _wikiVersion2 = WikiSelectManager.getVersion(_wiki, form.getLaterVersion());

            if (null == _wikiVersion2)
                throw new NotFoundException();

            DiffMatchPatch diffTool = new DiffMatchPatch();
            LinkedList<DiffMatchPatch.Diff> diffs = diffTool.diff_main(StringUtils.trimToEmpty(_wikiVersion1.getBody()), StringUtils.trimToEmpty(_wikiVersion2.getBody()));
            String htmlDiffs = diffTool.diff_prettyHtml(diffs);
            HtmlView htmlView = new HtmlView(htmlDiffs);
            htmlView.setTitle("Source Differences");

            TextExtractor te1 = new TextExtractor(_wikiVersion1.getHtml(getContainer(), _wiki));
            TextExtractor te2 = new TextExtractor(_wikiVersion2.getHtml(getContainer(), _wiki));
            diffs = diffTool.diff_main(te1.extract(), te2.extract());
            String textDiffs = diffTool.diff_prettyHtml(diffs);
            HtmlView textView = new HtmlView(textDiffs);
            textView.setTitle("Text Differences");

            getPageConfig().setNoIndex();
            getPageConfig().setNoFollow();
            return new VBox(htmlView, textView);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            HString pageTitle = _wikiVersion1.getTitle();
            pageTitle = pageTitle.concat(" (Comparing version " + _wikiVersion1.getVersion() + " to version " + _wikiVersion2.getVersion() + ")");

            return new VersionAction(getViewContext(), _wiki, _wikiVersion1).appendNavTrail(root).addChild(pageTitle);
        }
    }


    public static class CompareForm
    {
        private HString name;
        private int version1;
        private int version2;

        public HString getName()
        {
            return name;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setName(HString name)
        {
            this.name = name;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public int getVersion1()
        {
            return version1;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setVersion1(int version1)
        {
            this.version1 = version1;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public int getVersion2()
        {
            return version2;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setVersion2(int version2)
        {
            this.version2 = version2;
        }

        private int getEarlierVersion()
        {
            return version1 < version2 ? version1 : version2;
        }

        private int getLaterVersion()
        {
            return version1 < version2 ? version2 : version1;
        }
    }


    private ActionURL getVersionsURL(HString name)
    {
        ActionURL url = new ActionURL(VersionsAction.class, getContainer());
        url.addParameter("name", name);
        return url;
    }


    @RequiresPermissionClass(ReadPermission.class) //requires update or update_own; will check below
    public class VersionsAction extends SimpleViewAction<WikiNameForm>
    {
        Wiki _wiki;
        WikiVersion _wikiversion;

        @SuppressWarnings({"UnusedDeclaration"})
        public VersionsAction()
        {
        }

        public VersionsAction(ViewContext ctx, Wiki wiki, WikiVersion wikiversion)
        {
            setViewContext(ctx);
            _wiki = wiki;
            _wikiversion = wikiversion;
        }

        public ModelAndView getView(WikiNameForm form, BindException errors) throws Exception
        {
            HString wikiname = form.getName();
            if (wikiname == null)
                throw new NotFoundException();

            _wiki = WikiSelectManager.getWiki(getContainer(), wikiname);
            if (null == _wiki)
                throw new NotFoundException();

            _wikiversion = _wiki.getLatestVersion();

            BaseWikiPermissions perms = getPermissions();
            if (!perms.allowUpdate(_wiki))
                throw new UnauthorizedException("You do not have permissions to view the history for this page!");

            GridView gridView = new WikiVersionsGrid(_wiki, _wikiversion, errors);

            LinkBarView lb = new LinkBarView(
                    new Pair<>("return to page", getViewContext().cloneActionURL().setAction("page").toString()),
                    new Pair<>("view current version", getViewContext().cloneActionURL().setAction("version").toString())
                    );
            lb.setDrawLine(true);
            lb.setFrame(WebPartView.FrameType.NONE);
            lb.setTitle("History");

            VBox view = new VBox(lb, gridView);
            Wiki wiki = WikiSelectManager.getWiki(getContainer(), form.getName());
            if (wiki != null)
                view.addView(AttachmentService.get().getHistoryView(getViewContext(), wiki));
            getPageConfig().setNoIndex();
            getPageConfig().setNoFollow();
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("wikiUserGuide#history");
            return new PageAction(getViewContext(), _wiki,_wikiversion).appendNavTrail(root).
                    addChild("History for Page \"" + _wiki.getName() + "\"", getUrl());
        }

        public ActionURL getUrl()
        {
            return getVersionsURL(_wiki.getName());
        }
    }



    private ActionURL getMakeCurrentURL(HString pageName, int version)
    {
        ActionURL url = new ActionURL(MakeCurrentAction.class, getContainer());
        url.addParameter("name", pageName);
        url.addParameter("version", version);

        return url;
    }


    @RequiresPermissionClass(ReadPermission.class) //will check in code below
    public class MakeCurrentAction extends FormViewAction<WikiNameForm>
    {
        Wiki _wiki;
        WikiVersion _wikiversion;

        public ModelAndView getView(WikiNameForm wikiNameForm, boolean reshow, BindException errors) throws Exception
        {
            return null;
        }

        public boolean handlePost(WikiNameForm form, BindException errors) throws Exception
        {
            HString wikiName = form.getName();
            int version = form.getVersion();

            _wiki = WikiSelectManager.getWiki(getContainer(), wikiName);
            _wikiversion = WikiSelectManager.getVersion(_wiki, version);

            //per Britt and Adam, users with update perms on this page should be able
            //to set the current version--requiring admin doesn't make sense, as any
            //user with update perms could just as easily update the page to be the
            //same as some previous version
            BaseWikiPermissions perms = getPermissions();
            if (!perms.allowUpdate(_wiki))
                throw new UnauthorizedException("You do not have permission to set the current version of this page.");

            //update wiki & insert new wiki version
            getWikiManager().updateWiki(getUser(), _wiki, _wikiversion);
            return true;
        }

        public void validateCommand(WikiNameForm wikiNameForm, Errors errors)
        {
        }

        public ActionURL getSuccessURL(WikiNameForm wikiNameForm)
        {
            return new VersionAction(getViewContext(), _wiki,_wikiversion).getUrl();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresSiteAdmin
    public class PurgeAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            int rows = getWikiManager().purge();
            return new HtmlView("deleted " + rows + " pages<br>");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static class WikiManageForm
    {
        private HString _originalName;
        private HString _name;
        private HString _title;
        private int _parent;
        private HString _childOrder;
        private HString _siblingOrder;
        private HString _nextAction;
        private HString _containerPath;
        private boolean _shouldIndex;

        public HString getContainerPath()
        {
            return _containerPath;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setContainerPath(HString containerPath)
        {
            _containerPath = containerPath;
        }

        public HString getChildOrder()
        {
            return _childOrder;
        }

        public void setChildOrder(HString childIdList)
        {
            _childOrder = childIdList;
        }

       public boolean isShouldIndex()
       {
            return _shouldIndex;
       }

       public void setShouldIndex(boolean shouldIndex)
       {
            _shouldIndex = shouldIndex;
       }

        @SuppressWarnings({"UnusedDeclaration"})
        public HString getSiblingOrder()
        {
            return _siblingOrder;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSiblingOrder(HString siblingIdList)
        {
             _siblingOrder = siblingIdList;
        }

        private int[] breakIdList(HString list)
        {
            if (list == null)
                return new int[0];
            else
            {
                HString[] idStrs = list.split(",");
                int[] ids = new int[idStrs.length];
                for (int i = 0; i < idStrs.length; i++)
                    ids[i] = idStrs[i].parseInt();
                return ids;
            }
        }

        public int[] getSiblingOrderArray()
        {
            return breakIdList(_siblingOrder);
        }

        public int[] getChildOrderArray()
        {
            return breakIdList(_childOrder);
        }

        public HString getName()
        {
            return _name;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setName(HString name)
        {
            _name = name;
        }

        public HString getTitle()
        {
            return _title;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setTitle(HString title)
        {
            _title = title;
        }

        public HString getOriginalName()
        {
            return _originalName;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setOriginalName(HString name)
        {
            _originalName = name;
        }

        public int getParent()
        {
            return _parent;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setParent(int parent)
        {
            _parent = parent;
        }

        public HString getNextAction()
        {
            return _nextAction;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setNextAction(HString nextAction)
        {
            _nextAction = nextAction;
        }

        public void validate(Errors errors)
        {
            //check name
            HString newName = getName();
            HString oldName = getOriginalName();
            if (newName == null)
                errors.rejectValue("name", ERROR_MSG, "You must provide a name for this page.");
            else
            {
                //check for existing wiki with this name
                Container c = ContainerManager.getForPath(getContainerPath().getSource());
                if (!newName.equalsIgnoreCase(oldName) && WikiManager.get().wikiNameExists(c, newName))
                    errors.rejectValue("name", ERROR_MSG, "A page with the name '" + newName + "' already exists in this folder. Please choose a different name.");
            }
        }
    }


     public static class WikiNameForm
     {
         private HString _name;
         private HString _redirect;
         private int _parent;
         private int _version;

         public int getVersion()
         {
             return _version;
         }

         @SuppressWarnings({"UnusedDeclaration"})
         public void setVersion(int version)
         {
             _version = version;
         }

         public int getParent()
         {
             return _parent;
         }

         public void setParent(int parent)
         {
             _parent = parent;
         }

         public HString getName()
         {
             return _name;
         }

         @SuppressWarnings({"UnusedDeclaration"})
         public void setName(HString name)
         {
             _name = name;
         }

         public HString getRedirect()
         {
             return _redirect;
         }

         public void setRedirect(HString redirect)
         {
             _redirect = redirect;
         }
     }


    /**
     * Don't display a name for CreatedBy "0" (Guest)
     */
    public static class DisplayColumnCreatedBy extends DataColumn
    {
        public DisplayColumnCreatedBy(ColumnInfo col)
        {
            super(col);
        }

        public Object getValue(RenderContext ctx)
        {
            Map rowMap = ctx.getRow();
            String displayName = (String)rowMap.get("createdBy$displayName");
            return (null != displayName ? displayName : "Guest");
        }

        public Class getValueClass()
        {
            return String.class;
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            out.write(getValue(ctx).toString());
        }
    }


    public static class ContainerForm
    {
        private String _id;

        public String getId()
        {
            return _id;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setId(String id)
        {
            _id = id;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetPagesAction extends ApiAction<ContainerForm>
    {
        public ApiResponse execute(ContainerForm form, BindException errors) throws Exception
        {
            if (null == form.getId() || form.getId().length() == 0)
                throw new IllegalArgumentException("The id parameter must be set to a valid container id!");

            Container container = ContainerManager.getForId(form.getId());
            if (null == container)
                throw new IllegalArgumentException("The container id '" + form.getId() + "' is not valid.");
            
            Map<HString, HString> wikiMap = WikiSelectManager.getNameTitleMap(container);
            if (null == wikiMap)
                return new ApiSimpleResponse("pages", null);

            List<Map<String, String>> pages = new ArrayList<>(wikiMap.size());

            for (Map.Entry<HString, HString> entry : wikiMap.entrySet())
            {
                String name = entry.getKey().getSource();
                String title = entry.getValue().getSource();
                Map<String, String> pageMap = new HashMap<>();
                pageMap.put("name", name);
                pageMap.put("title", title);
                pages.add(pageMap);
            }

            return new ApiSimpleResponse("pages", pages);
        }
    }

    public static class EditWikiForm
    {
        private HString _name;
        private String _redirect;
        private String _cancel;
        private String _format;
        private String _defName;
        private int _webPartId;

        public HString getName()
        {
            return _name;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setName(HString name)
        {
            _name = name;
        }

        public String getRedirect()
        {
            return _redirect;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setRedirect(String redir)
        {
            _redirect = redir;
        }

        public String getCancel()
        {
            return _cancel;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setCancel(String cancel)
        {
            _cancel = cancel;
        }

        public String getFormat()
        {
            return _format;
        }

        public void setFormat(String format)
        {
            _format = format;
        }

        public String getDefName()
        {
            return _defName;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setDefName(String defName)
        {
            _defName = defName;
        }

        public int getWebPartId()
        {
            return _webPartId;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setWebPartId(int webPartId)
        {
            _webPartId = webPartId;
        }
    }

    @ActionNames("edit, editWiki")
    @RequiresPermissionClass(ReadPermission.class) //will check below
    public class EditWikiAction extends SimpleViewAction<EditWikiForm>
    {
        private WikiVersion _wikiVer = null;
        private Wiki _wiki = null;

        public ModelAndView getView(EditWikiForm form, BindException errors) throws Exception
        {
            //get the wiki
            Wiki wiki = null;
            WikiVersion curVersion = null;

            if (null != form.getName() && form.getName().length() > 0)
            {
                wiki = WikiSelectManager.getWiki(getContainer(), form.getName());
                if (null == wiki)
                    throw new NotFoundException("There is no wiki in the current folder named '" + form.getName() + "'!");

                //get the current version
                curVersion = wiki.getLatestVersion();
                if (null == curVersion)
                    throw new NotFoundException("Could not locate the current version of the wiki named '" + form.getName() + "'!");
            }

            //check permissions
            Container container = getContainer();
            User user = getUser();
            BaseWikiPermissions perms = new BaseWikiPermissions(user, container);

            if (null == wiki)
            {
                //if no wiki, this is an insert, so user must have insert perms
                if (!perms.allowInsert())
                    throw new UnauthorizedException("You do not have permissions to create new wiki pages in this folder!");
            }
            else
            {
                //updating wiki--use BaseWikiPermissions
                if (!perms.allowUpdate(wiki))
                    throw new UnauthorizedException("You do not have permissions to edit this wiki page!");
            }

            //get the user's editor preference
            Map<String, String> properties = PropertyManager.getProperties(getUser(),
                    getContainer(), SetEditorPreferenceAction.CAT_EDITOR_PREFERENCE);
            boolean useVisualEditor = !("false".equalsIgnoreCase(properties.get(SetEditorPreferenceAction.PROP_USE_VISUAL_EDITOR)));
            String defFormat = properties.get(SaveWikiAction.PROP_DEFAULT_FORMAT);
            if ((null == form.getFormat() || form.getFormat().length() == 0)
                    && null != defFormat && defFormat.length() > 0)
                form.setFormat(defFormat);

            WikiEditModel model = new WikiEditModel(container, wiki, curVersion,
                    form.getRedirect(), form.getCancel(), form.getFormat(), form.getDefName(), useVisualEditor,
                    form.getWebPartId(), user);

            //stash the wiki so we can build the nav trail
            _wiki = wiki;
            _wikiVer = curVersion;

            return new JspView<>("/org/labkey/wiki/view/wikiEdit.jsp", model);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("wikiUserGuide#edit");
            if (null != _wiki && null != _wikiVer)
            {
                ActionURL pageUrl = new ActionURL(WikiController.PageAction.class, getContainer());
                pageUrl.addParameter("name", _wiki.getName());
                return root.addChild(_wikiVer.getTitle(), pageUrl).addChild("Edit");
            }
            else
                return root.addChild("New Page");
        }
    }

    public static class SaveWikiForm
    {
        private GUID _entityId;
        private HString _name;
        private HString _title;
        private String _body;
        private Integer _parent = -1;
        private Integer _pageVersionId;
        private String _rendererType;
        private String _pageId;
        private int _index = -1;
        private int _webPartId = -1;
        private boolean _showAttachments = true;
        private boolean _shouldIndex = true;

        public GUID getEntityId()
        {
            return _entityId;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setEntityId(GUID entityId)
        {
            _entityId = entityId;
        }

        public HString getName()
        {
            return _name;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setName(HString name)
        {
            _name = name;
        }

        public HString getTitle()
        {
            return _title;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setTitle(HString title)
        {
            _title = title;
        }

        public String getBody()
        {
            return _body;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setBody(String body)
        {
            _body = body;
        }

        public boolean isNew()
        {
            return null == _entityId;
        }

        @SuppressWarnings({"UnusedDeclaration"})   // Used for bean population; our code uses getParentId() TODO: Merge these?
        public Integer getParent()
        {
            return _parent;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setParent(Integer parent)
        {
            _parent = parent;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public Integer getPageVersionId()
        {
            return null == _pageVersionId ? -1 : _pageVersionId;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setPageVersionId(Integer pageVersionId)
        {
            _pageVersionId = pageVersionId;
        }

        public int getParentId()
        {
            return null == _parent ? -1 : _parent;
        }

        public String getRendererType()
        {
            return _rendererType;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setRendererType(String rendererType)
        {
            _rendererType = rendererType;
        }

        public String getPageId()
        {
            return _pageId;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setPageId(String pageId)
        {
            _pageId = pageId;
        }

        public int getIndex()
        {
            return _index;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setIndex(int index)
        {
            _index = index;
        }

        public int getWebPartId()
        {
            return _webPartId;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setWebPartId(int webPartId)
        {
            _webPartId = webPartId;
        }

        public boolean isShowAttachments()
        {
            return _showAttachments;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setShowAttachments(boolean showAttachments)
        {
            _showAttachments = showAttachments;
        }

        public boolean isShouldIndex()
        {
            return _shouldIndex;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setShouldIndex(boolean shouldIndex)
        {
            _shouldIndex = shouldIndex;
        }
    }

    @RequiresPermissionClass(ReadPermission.class) //will check below
    public class SaveWikiAction extends ExtFormAction<SaveWikiForm>
    {
        public final static String PROP_DEFAULT_FORMAT = "defaultFormat";

        public ApiResponse execute(SaveWikiForm form, BindException errors) throws Exception
        {
            //if no entityId was passed, insert it
            if (form.isNew())
                return insertWiki(form);
            else
                return updateWiki(form);
        }

        public void validateForm(SaveWikiForm form, Errors errors)
        {
            User user = getUser();
            Container container = getContainer();
            HString name = null != form.getName() ? form.getName().trim() : null;

            //must have a name
            //cannot start with _ if not admin
            //if new, must be unique
            if (null == name || name.length() <= 0)
                errors.rejectValue("name", ERROR_MSG, "You must provide a name for this page.");
            else if (name.startsWith("_") && !container.hasPermission(getUser(), AdminPermission.class))
                errors.rejectValue("name", ERROR_MSG, "Wiki names starting with underscore are reserved for administrators.");

            // name and title max 255 chars
            if (null != name && name.length() > 255)
                errors.rejectValue("name", ERROR_MSG, "Wiki names must be < 256 characters.");
            if(null != form.getTitle() && form.getTitle().length() > 255)
                errors.rejectValue("title", ERROR_MSG, "Wiki titles must be < 256 characters.");

            //check to ensure that there is not an existing wiki with the same name
            //but different entity id (works for both insert and update case)
            if (null != name && name.length() > 0)
            {
                Wiki existingWiki = WikiSelectManager.getWiki(container, name);
                if (null != existingWiki && (null == form.getEntityId() || !HString.eq(existingWiki.getEntityId().toLowerCase(), form.getEntityId().toString().toLowerCase())))
                    errors.rejectValue("name", ERROR_MSG, "Page '" + name + "' already exists within this folder.");
            }

            SecurityPolicy policy = SecurityPolicyManager.getPolicy(getContainer());
            Set<Role> contextualRoles = new HashSet<>();

            //if HTML body, must be valid according to tidy
            if (null != form.getBody() && (null == form.getRendererType() || WikiRendererType.valueOf(form.getRendererType()) == WikiRendererType.HTML))
            {
                String body = form.getBody();
                ArrayList<String> tidyErrors = new ArrayList<>();

                if (user.isDeveloper())
                    contextualRoles.add(RoleManager.getRole(DeveloperRole.class));

                PageFlowUtil.validateHtml(body, tidyErrors,
                        policy.hasPermission(user, IncludeScriptPermission.class, contextualRoles));

                for (String err : tidyErrors)
                    errors.rejectValue("body", ERROR_MSG, err);
            }
        }

        protected ApiResponse insertWiki(SaveWikiForm form) throws Exception
        {
            Container c = getContainer();
            User user = getUser();
            SecurityPolicy policy = SecurityPolicyManager.getPolicy(c);
            if (!policy.hasPermission(user, InsertPermission.class))
                throw new UnauthorizedException("You do not have permissions to create a new wiki page in this folder!");

            HString wikiname = form.getName();
            LOG.debug("Inserting wiki " + wikiname);

            Wiki wiki = new Wiki(c, wikiname);
            wiki.setParent(form.getParentId());
            wiki.setShowAttachments(form.isShowAttachments());
            wiki.setShouldIndex(form.isShouldIndex());

            WikiVersion wikiversion = new WikiVersion(wikiname);

            //if user has not submitted title, use page name as title
            HString title = form.getTitle() == null ? wikiname : form.getTitle();
            wikiversion.setTitle(title);
            wikiversion.setBody(form.getBody());
            wikiversion.setRendererType(form.getRendererType());

            //insert new wiki and new version
            getWikiManager().insertWiki(getUser(), c, wiki, wikiversion, null);

            //if webPartId was sent, update the corresponding
            //web part to show the newly inserted page
            int webPartId = form.getWebPartId();

            if (webPartId > 0)
            {
                //get web part referenced by webPartId
                Portal.WebPart webPart = Portal.getPart(c, webPartId);
                if (null != webPart && webPart.getName().equals("Wiki"))
                {
                    webPart.setProperty("webPartContainer", c.getId());
                    webPart.setProperty("name", wikiname.getSource());
                    Portal.updatePart(getUser(), webPart);
                }
            }

            //save the user's new page format so we can use it
            //as the default for the next new page
            PropertyManager.PropertyMap properties = PropertyManager.getWritableProperties(
                    getUser(), getContainer(),
                    SetEditorPreferenceAction.CAT_EDITOR_PREFERENCE, true);
            properties.put(PROP_DEFAULT_FORMAT, wikiversion.getRendererTypeEnum().name());
            PropertyManager.saveProperties(properties);

            //return an API response containing the current wiki and version data
            ApiSimpleResponse resp = new ApiSimpleResponse("success", true);
            resp.put("wikiProps", getWikiProps(wiki, wikiversion));
            return resp;
        }

        protected HashMap<String, Object> getWikiProps(Wiki wiki, WikiVersion wikiversion)
        {
            HashMap<String, Object> wikiProps = new HashMap<>();
            wikiProps.put("entityId", wiki.getEntityId());
            wikiProps.put("rowId", wiki.getRowId());
            wikiProps.put("name", wiki.getName().getSource()); //HString source will be JS encoded
            wikiProps.put("title", wikiversion.getTitle().getSource());
            wikiProps.put("body", wikiversion.getBody()); //CONSIDER: do we really need to return the body here?
            wikiProps.put("rendererType", wikiversion.getRendererTypeEnum().name());
            wikiProps.put("parent", wiki.getParent());
            wikiProps.put("pageVersionId", wiki.getPageVersionId());
            wikiProps.put("showAttachments", wiki.isShowAttachments());
            wikiProps.put("shouldIndex", wiki.isShouldIndex());

            return wikiProps;
        }

        private ApiResponse updateWiki(SaveWikiForm form) throws Exception
        {
            User user = getUser();
            if (null == form.getEntityId())
                throw new IllegalArgumentException("The entityId parameter must be supplied.");

            Wiki wikiUpdate = getWikiManager().getWikiByEntityId(getContainer(), form.getEntityId().toString());
            if (wikiUpdate == null)
                throw new NotFoundException("Could not find the wiki page matching the passed id; if it was a valid page, it may have been deleted by another user.");

            // issue 12960: if the wiki was updated by a different user, don't allow the save
            if (!wikiUpdate.getPageVersionId().equals(form.getPageVersionId()))
                throw new NotFoundException("This wiki version has already been updated by a different user.");            

            LOG.debug("Updating wiki " + wikiUpdate.getName());

            SecurityPolicy policy = SecurityPolicyManager.getPolicy(getContainer());
            Set<Role> contextualRoles = new HashSet<>();
            if (wikiUpdate.getCreatedBy() == user.getUserId())
                contextualRoles.add(RoleManager.getRole(OwnerRole.class));

            if (!policy.hasPermission(user, UpdatePermission.class, contextualRoles))
                throw new UnauthorizedException("You are not allowed to edit this wiki page.");

            WikiVersion wikiversion = new WikiVersion(wikiUpdate.getLatestVersion());

            //if title is null, use name
            HString title = null == form.getTitle() || form.getTitle().isEmpty() ? form.getName() : form.getTitle();
            WikiRendererType currentRendererType = WikiRendererType.valueOf(form.getRendererType());

            //only insert new version if something has changed
            if (wikiUpdate.getName().trim().compareTo(form.getName()) != 0 ||
                    wikiversion.getTitle().trim().compareTo(title) != 0 ||
                    (null == wikiversion.getBody() && null != form.getBody()) ||
                    (null != wikiversion.getBody() && null == form.getBody()) ||
                    (null != wikiversion.getBody() && null != form.getBody() && wikiversion.getBody().compareTo(form.getBody().trim()) != 0) ||
                    !wikiversion.getRendererTypeEnum().equals(currentRendererType) ||
                    wikiUpdate.getParent() != form.getParentId() ||
                    wikiUpdate.isShowAttachments() != form.isShowAttachments() ||
                    wikiUpdate.isShouldIndex() != form.isShouldIndex())
            {
                wikiUpdate.setShowAttachments(form.isShowAttachments());
                wikiUpdate.setShouldIndex(form.isShouldIndex());
                wikiUpdate.setName(form.getName());
                wikiUpdate.setParent(form.getParentId());
                wikiversion.setTitle(title);
                wikiversion.setBody(form.getBody());
                wikiversion.setRendererTypeEnum(currentRendererType);
                getWikiManager().updateWiki(getUser(), wikiUpdate, wikiversion);
            }

            //return an API response containing the current wiki and version data
            ApiSimpleResponse resp = new ApiSimpleResponse("success", true);
            resp.put("wikiProps", getWikiProps(wikiUpdate, wikiversion));
            return resp;
        }
    }

    public static class AttachFilesForm
    {
        private String _entityId;
        private String[] _toDelete;

        public String getEntityId()
        {
            return _entityId;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setEntityId(String entityId)
        {
            _entityId = entityId;
        }

        public String[] getToDelete()
        {
            return _toDelete;
        }

        public void setToDelete(String[] toDelete)
        {
            _toDelete = toDelete;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class AttachFilesAction extends ApiAction<AttachFilesForm>
    {
        @SuppressWarnings({"UnusedDeclaration"})
        public AttachFilesAction()
        {
            super();

            //because this will typically be called from a hidden iframe
            //we must respond with a content-type of text/html or the
            //browser will prompt the user to save the response, as the
            //browser won't natively show application/json content-type
            setContentTypeOverride("text/html");
        }

        public ApiResponse execute(AttachFilesForm form, BindException errors) throws Exception
        {
            if (null == form.getEntityId() || form.getEntityId().length() == 0)
                throw new IllegalArgumentException("The entityId parameter is required!");

            //get the wiki using the entity id
            Wiki wiki = getWikiManager().getWikiByEntityId(getContainer(), form.getEntityId());
            if (null == wiki)
                throw new IllegalArgumentException("Could not find the wiki with entity id '" + form.getEntityId() + "'!");

            if (!(getViewContext().getRequest() instanceof MultipartHttpServletRequest))
                throw new IllegalArgumentException("You must use the 'multipart/form-data' mimetype when posting to attachFiles.api");

            Map<String, Object> warnings = new HashMap<>();

            String[] deleteNames = form.getToDelete();
            List<String> names;
            if (null == deleteNames || 0 == deleteNames.length)
                names = Collections.emptyList();
            else
                names = Arrays.asList(deleteNames);

            String message = getWikiManager().updateAttachments(getUser(), wiki, names, getAttachmentFileList());

            if (null != message)
            {
                warnings.put("files", message);
            }

            // uncache just this wiki
            WikiCache.uncache(getContainer(), wiki, false);

            //build the response
            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("success", true);
            if (warnings.size() > 0)
                resp.put("warnings", warnings);

            Wiki wikiUpdated = WikiSelectManager.getWiki(getContainer(), wiki.getName());
            assert(null != wikiUpdated);
            List<Object> attachments = new ArrayList<>();

            if (null != wikiUpdated.getAttachments())
            {
                for (Attachment att : wikiUpdated.getAttachments())
                {
                    Map<String, Object> attProps = new HashMap<>();
                    attProps.put("name", att.getName());
                    attProps.put("iconUrl", getViewContext().getContextPath() + att.getFileIcon());
                    attProps.put("downloadUrl", att.getDownloadUrl(DownloadAction.class).toString());
                    attachments.add(attProps);
                }
            }

            resp.put("attachments", attachments);
            return resp;
        }
    }


    public static class TransformWikiForm
    {
        private String _body;
        private String _fromFormat;
        private String _toFormat;

        public String getBody()
        {
            return _body;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setBody(String body)
        {
            _body = body;
        }

        public String getFromFormat()
        {
            return _fromFormat;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setFromFormat(String fromFormat)
        {
            _fromFormat = fromFormat;
        }

        public String getToFormat()
        {
            return _toFormat;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setToFormat(String toFormat)
        {
            _toFormat = toFormat;
        }
    }

    @RequiresNoPermission
    public class TransformWikiAction extends ApiAction<TransformWikiForm>
    {
        public ApiResponse execute(TransformWikiForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            String newBody = form.getBody();
            Container container = getContainer();

            //currently, we can only transform from wiki to HTML
            if (HString.eq(WikiRendererType.RADEOX.name(),form.getFromFormat())
                    && HString.eq(WikiRendererType.HTML.name(),form.getToFormat()))
            {
                Wiki wiki = new Wiki(container, new HString("_transform_temp",false));
                WikiVersion wikiver = new WikiVersion(new HString("_transform_temp",false));
                wikiver.setCacheContent(false);

                if (null != form.getBody())
                    wikiver.setBody(form.getBody());

                wikiver.setRendererType(form.getFromFormat());
                newBody = wikiver.getHtmlForConvert(getContainer(), wiki);
            }

            response.put("toFormat", form.getToFormat());
            response.put("fromFormat", form.getFromFormat());
            response.put("body", newBody);

            return response;
        }
    }

    public static class GetWikiTocForm
    {
        private HString _currentPage = null;

        public HString getCurrentPage()
        {
            return _currentPage;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setCurrentPage(HString currentPage)
        {
            _currentPage = currentPage;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetWikiTocAction extends ApiAction<GetWikiTocForm>
    {
        public ApiResponse execute(GetWikiTocForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            Container container = getContainer();

            NavTree[] toc = WikiSelectManager.getNavTree(container);

            List<Map<String, Object>> pageProps = getChildrenProps(toc);
            response.put("pages", pageProps);

            Set<String> expandedPaths = NavTreeManager.getExpandedPathsCopy(getViewContext(), WikiTOC.getNavTreeId(getViewContext()));
            applyExpandedState(pageProps, expandedPaths, form.getCurrentPage());

            //include info about the current container
            Map<String, Object> containerProps = new HashMap<>();
            containerProps.put("name", container.getName());
            containerProps.put("id", container.getId());
            containerProps.put("path", container.getPath());
            response.put("container", containerProps);

            //include the user's TOC displayed preference
            Map<String, String> properties = PropertyManager.getProperties(
                    getUser(), getContainer(),
                    SetEditorPreferenceAction.CAT_EDITOR_PREFERENCE);

            response.put("displayToc", "true".equals(properties.get(SetTocPreferenceAction.PROP_TOC_DISPLAYED)));

            return response;
        }

        public void applyExpandedState(List<Map<String,Object>> pages, Set<String> expandedPaths, HString currentPage)
        {
            if (null != expandedPaths)
            {
                for (String path : expandedPaths)
                {
                    if (null != path)
                        expandPath(path.split("/"), 1, pages, false); //start at index 1 since these paths all start with /
                }
            }

            if (null != currentPage)
            {
                Wiki wiki = WikiSelectManager.getWiki(getContainer(), currentPage);
                if (null != wiki && null != wiki.getLatestVersion())
                {
                    LinkedList<String> path = new LinkedList<>();
                    Wiki parent = wiki.getParentWiki();
                    while (null != parent && null != parent.getLatestVersion())
                    {
                        path.addFirst(parent.getName().toString());
                        parent = parent.getParentWiki();
                    }
                    
                    expandPath(path.toArray(new String[path.size()]), 0, pages, true);
                }
            }
        }

        protected void expandPath(String[] path, int idx, List<Map<String, Object>> pages, boolean expandAncestors)
        {
            if (null == pages || null == path || path.length == 0 || idx >= path.length)
                return;

            //find the propset in pages that matches the current path part
            path[idx] = PageFlowUtil.decode(path[idx]); //decode path part before comparing!

            for (Map<String, Object> pageProps : pages)
            {
                if (path[idx].equals(pageProps.get("title").toString()))
                {
                    //add the expanded property
                    if (expandAncestors || idx == (path.length - 1))
                        pageProps.put("expanded", true);

                    //recurse children
                    expandPath(path, idx + 1, (List<Map<String, Object>>)pageProps.get("children"), expandAncestors);
                }
            }
        }

        public List<Map<String, Object>> getChildrenProps(NavTree[] pages)
        {
            List<Map<String, Object>> ret = new ArrayList<>();

            for (NavTree page : pages)
            {
                Map<String, Object> props = new HashMap<>();
                ActionURL pageLink = new ActionURL(page.getHref());
                props.put("name", pageLink.getParameter("name"));
                props.put("title", page.getText());
                props.put("pageLink", page.getHref());

                if (page.hasChildren())
                    props.put("children", getChildrenProps(page.getChildren()));

                ret.add(props);
            }

            return ret;
        }
    }

    public static class SetEditorPreferenceForm
    {
        public boolean _useVisual;

        public boolean isUseVisual()
        {
            return _useVisual;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setUseVisual(boolean useVisual)
        {
            _useVisual = useVisual;
        }
    }

    @RequiresNoPermission
    public class SetEditorPreferenceAction extends ApiAction<SetEditorPreferenceForm>
    {
        public static final String CAT_EDITOR_PREFERENCE = "editorPreference";
        public static final String PROP_USE_VISUAL_EDITOR = "useVisualEditor";

        public ApiResponse execute(SetEditorPreferenceForm form, BindException errors) throws Exception
        {
            //save user's editor preference
            PropertyManager.PropertyMap properties = PropertyManager.getWritableProperties(
                    getUser(), getContainer(),
                    CAT_EDITOR_PREFERENCE, true);
            properties.put(PROP_USE_VISUAL_EDITOR, String.valueOf(form.isUseVisual()));
            PropertyManager.saveProperties(properties);

            return new ApiSimpleResponse("success", true);
        }
    }

    public static class SetTocPreferenceForm
    {
        private boolean _displayed = false;

        public boolean isDisplayed()
        {
            return _displayed;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setDisplayed(boolean displayed)
        {
            _displayed = displayed;
        }
    }

    @RequiresNoPermission
    public class SetTocPreferenceAction extends ApiAction<SetTocPreferenceForm>
    {
        public static final String PROP_TOC_DISPLAYED = "displayToc";

        public ApiResponse execute(SetTocPreferenceForm form, BindException errors)
        {
            //use the same category as editor preference to save on storage
            PropertyManager.PropertyMap properties = PropertyManager.getWritableProperties(
                    getUser(), getContainer(),
                    SetEditorPreferenceAction.CAT_EDITOR_PREFERENCE, true);
            properties.put(PROP_TOC_DISPLAYED, String.valueOf(form.isDisplayed()));
            PropertyManager.saveProperties(properties);

            return new ApiSimpleResponse("success", true);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class BackLinksAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Container c = getContainer();
            MultiMap<String, Wiki> mmap = new MultiHashMap<>();
            Set<WikiTree> trees = WikiSelectManager.getWikiTrees(c);
            WikiManager mgr = getWikiManager();

            for (WikiTree tree : trees)
            {
                Wiki wiki = WikiSelectManager.getWiki(c, tree.getRowId());
                FormattedHtml html = mgr.formatWiki(c, wiki, wiki.getLatestVersion());

                for (String name : html.getWikiDependencies())
                    mmap.put(name, wiki);
            }

            StringBuilder html = new StringBuilder();
            html.append(renderTable(c, mmap, "Links to valid wikis", true));
            html.append(renderTable(c, mmap, "Invalid links or links to non-wikis", false));

            return new HtmlView(html.toString());
        }

        private StringBuilder renderTable(Container c, MultiMap<String, Wiki> mmap, String title, boolean validWiki)
        {
            StringBuilder html = new StringBuilder();
            Set<String> names =  new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            names.addAll(mmap.keySet());

            for (String name : names)
            {
                Wiki page = WikiSelectManager.getWiki(c, new HString(name));

                if (null == page ^ validWiki)
                {
                    html.append("    <tr><td>");
                    html.append(getSimpleLink(name, getPageURL(c, new HString(name))));
                    Collection<Wiki> wikis = mmap.get(name);

                    html.append("</td><td>");
                    String sep = "";

                    for (Wiki wiki : wikis)
                    {
                        html.append(sep);
                        html.append(getSimpleLink(wiki.getName().getSource(), getPageURL(wiki, c)));
                        sep = ", ";
                    }

                    html.append("</td></tr>\n");
                }
            }

            if (html.length() > 0)
            {
                html.insert(0, "<table>\n<tr><td colspan=2>").append(PageFlowUtil.filter(title)).append("</td></tr>\n");
                html.append("</table><br>\n");
            }

            return html;
        }

        private String getSimpleLink(String name, ActionURL url)
        {
            return "<a href=\"" + PageFlowUtil.filter(url) + "\">" + PageFlowUtil.filter(name) + "</a>";
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Back Links");
        }
    }

    private WikiManager getWikiManager()
    {
        return WikiManager.get();
    }
}

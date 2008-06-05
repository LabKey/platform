/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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

import org.apache.beehive.netui.pageflow.FormData;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.collections15.MultiMap;
import org.apache.log4j.Logger;
import org.labkey.api.action.*;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.attachments.*;
import org.labkey.api.data.*;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.*;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.api.view.menu.NavTreeMenu;
import org.labkey.api.view.template.HomeTemplate;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.view.template.PrintTemplate;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.api.wiki.WikiRenderer;
import org.labkey.common.util.Pair;
import org.labkey.wiki.model.*;
import org.labkey.wiki.renderer.RadeoxRenderer;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URI;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.*;


public class WikiController extends SpringActionController
{
    static DefaultActionResolver _actionResolver = new DefaultActionResolver(WikiController.class);

    private static final boolean SHOW_CHILD_REORDERING = false;


    public WikiController()
    {
        super();
        setActionResolver(_actionResolver);
    }
    

    protected ActionURL wikiURL(String action)
    {
        return new ActionURL("wiki", action, getContainer());
    }

    protected BaseWikiPermissions getPermissions()
    {
        //if we need to anything more complicated, return various derived classes based on context
        return new BaseWikiPermissions(getUser(), getContainer());
    }


    @Override
    protected ModelAndView getTemplate(ViewContext context, ModelAndView mv, Controller action, PageConfig page)
    {
        ModelAndView template = super.getTemplate(context, mv, action, page);
        if (template instanceof HomeTemplate && !(action instanceof EditWikiAction))
        {
            WebPartView toc = new WikiTOC(context);

            if(mv instanceof Search.SearchResultsView)
            {
                ((HomeTemplate)template).setView("right", toc);
            }
            else
            {
                JspView<SearchViewContext> searchView = new JspView<SearchViewContext>("/org/labkey/wiki/view/wikiSearch.jsp",
                                                                                    new SearchViewContext(getViewContext()));
                searchView.setTitle("Search");
                ((HomeTemplate)template).setView("right", new VBox(searchView, toc));
            }
        }
        return template;
    }
    

    public static class CustomizeWikiPartView extends AbstractCustomizeWebPartView<Object>
    {
        public CustomizeWikiPartView()
        {
            super("/org/labkey/wiki/view/wiki_customize.gm");
        }

        @Override
        public void prepareWebPart(Object model) throws ServletException
        {
            super.prepareWebPart(model);

            ViewContext context = getViewContext();

            try
            {
                //get all containers that include wiki pages
                List<Container> allWikiContainers = populateWikiContainerList(context);
                addObject("containerList", allWikiContainers);

                //build map of containers and their associated sets of wiki pages
                Map<Container, List<Wiki>> containerMap = new LinkedHashMap<Container, List<Wiki>>();
                for (Container c : allWikiContainers)
                {
                    //get list of wiki pages for this container
                    List<Wiki> containerPageList = WikiManager.getPageList(c);
                    containerMap.put(c, containerPageList);

                    //track current container to fill list easily.
                    if(c.getId().equals(context.getContainer().getId()))
                        addObject("currentContainer", c);
                }
                addObject("mapEntries", containerMap);

                //get wiki page list for the currently stored container (or current container if null)
                Container cStored;
                Portal.WebPart webPart = (Portal.WebPart) context.get("webPart");
                if (webPart == null)
                    cStored = context.getContainer();
                else
                {
                    String id = webPart.getPropertyMap().get("webPartContainer");
                    //is this still a valid container id? if not, use current container
                    //also use the current container if the stored container doesn't
                    //have any pages left in it
                    cStored = id == null ? context.getContainer() : ContainerManager.getForId(id);
                    if (cStored == null || null == containerMap.get(cStored))
                    {
                        cStored = context.getContainer();
                        
                        //reset the webPartContainer property so that wiki_cusotmize.gm selects the correct one in the UI
                        webPart.getPropertyMap().put("webPartContainer", cStored.getId());
                    }
                }
                addObject("containerWikiList", containerMap.get(cStored));
            }
            catch(SQLException e)
            {
                throw new RuntimeException("Failed to populate container or page list.", e);
            }
        }
    }

    private static List<Container> populateWikiContainerList(ViewContext context)
            throws SQLException, ServletException
    {
        //retrieve all containers
        MultiMap<Container, Container> mm = ContainerManager.getContainerTree();

        //get wikis for containers recursively
        List<Container> children = new ArrayList<Container>();
        populateWikiContainerListRecursive(context, ContainerManager.getRoot(), children, mm);

        return children;
    }

    private static void populateWikiContainerListRecursive(ViewContext context, Container c, List<Container> children, MultiMap<Container, Container> mm)
            throws SQLException, ServletException
    {
        //get a list of containers in root and add to arraylist
        Collection<Container> arrCont = mm.get(c);

        //check for children
        if(arrCont != null)
        {
            for (Container cChild : arrCont)
            {
                //does user have permissions to read this container?
                if (cChild.hasPermission(context.getUser(), ACL.PERM_READ))
                {
                    //add this container if it contains any wiki pages or if it's the current container
                    if (cChild.getId().equals(context.getContainer().getId()) || WikiManager.getPageList(cChild).size() > 0)
                        children.add(cChild);
                    //check container's children
                    populateWikiContainerListRecursive(context, cChild, children, mm);
                }
            }
        }
    }

    public static class CustomizeWikiTOCPartView extends AbstractCustomizeWebPartView<Object>
    {
        public CustomizeWikiTOCPartView()
        {
            super("/org/labkey/wiki/view/wiki_customizetoc.gm");
        }

        @Override
        public void prepareWebPart(Object model) throws ServletException
        {
            super.prepareWebPart(model);

            try
            {
                List<Container> allContainers = populateWikiContainerList(getViewContext());
                addObject("containerList", allContainers);
                addObject("currentContainer", getViewContext().getContainer());
            }
            catch (SQLException e)
            {
                throw new RuntimeException("Failed to populate container or page list.", e);
            }
        }
    }


    /**
     * This method represents the point of entry into the pageflow
     */
    @RequiresPermission(ACL.PERM_READ)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return HttpView.redirect(getUrl());
        }

        public ActionURL getUrl()
        {
            String name = getDefaultPage(getContainer()).getName();
            return wikiURL("page").addParameter("name",name);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Start Page", getUrl());
        }
    }


    public static Wiki getDefaultPage(Container c)
    {
        //look for page named "default"
        Wiki wiki = WikiManager.getWiki(c, "default");
        //handle case where no page named "default" exists by getting first page in ordered list
        //note that this does not automatically display page selected on web part customize. should it?
        if(wiki == null)
        {
            List<Wiki> pageList = WikiManager.getPageList(c);
            if(pageList.size() == 0)
                wiki = new Wiki(c, "default");
            else
                wiki = pageList.get(0);
        }
        return wiki;
    }


    @RequiresPermission(ACL.PERM_READ) //will test explicitly below
    public class DeleteAction extends ConfirmAction<WikiNameForm>
    {
        Wiki _wiki = null;
        
        public String getConfirmText()
        {
            return "Delete";
        }

        public ModelAndView getConfirmView(WikiNameForm form, BindException errors) throws Exception
        {
            _wiki = WikiManager.getWiki(getContainer(), form.getName());
            if (null == _wiki)
                HttpView.throwNotFound();

            BaseWikiPermissions perms = getPermissions();
            if(!perms.allowDelete(_wiki))
                HttpView.throwUnauthorized("You do not have permissions to delete this wiki page");

            HttpView v = new GroovyView("/org/labkey/wiki/view/wiki_delete.gm");
            v.addObject("wiki", _wiki);
            return v;
        }

        public boolean handlePost(WikiNameForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            _wiki = WikiManager.getWiki(c, form.getName());
            if (null == _wiki)
                HttpView.throwNotFound();

            BaseWikiPermissions perms = getPermissions();
            if(!perms.allowDelete(_wiki))
                HttpView.throwUnauthorized("You do not have permissions to delete this wiki page");

            //delete page and all versions
            WikiManager.deleteWiki(getUser(), c, _wiki);
            return true;
        }

        public void validateCommand(WikiNameForm wikiNameForm, Errors errors)
        {
        }
        
        public ActionURL getSuccessURL(WikiNameForm wikiNameForm)
        {
            return new BeginAction().getUrl();
        }

        @Override
        public ActionURL getCancelUrl()
        {
            return new PageAction(_wiki,null).getUrl();
        }

        public ActionURL getFailURL(WikiNameForm wikiNameForm, BindException errors)
        {
            return new ManageAction(_wiki).getUrl();
        }
    }


    @RequiresPermission(ACL.PERM_READ) //will test explcitly below
    public class ManageAction extends FormViewAction<WikiManageForm>
    {
        Wiki _wiki = null;
        WikiVersion _wikiversion = null;

        public ManageAction()
        {
        }

        ManageAction(Wiki wiki)
        {
            _wiki = wiki;
        }

        public ModelAndView getView(WikiManageForm form, boolean reshow, BindException errors) throws Exception
        {
            String name = form.getName();
            if (name == null || (errors != null && errors.getErrorCount()>0))
                name = form.getOriginalName();

            _wiki = WikiManager.getWiki(getContainer(), name);
            if (null == _wiki)
                HttpView.throwNotFound();

            BaseWikiPermissions perms = getPermissions();
            if(!perms.allowUpdate(_wiki))
                HttpView.throwUnauthorized("You do not have permissions to manage this wiki page");

            HttpView manageView = new GroovyView("/org/labkey/wiki/view/wiki_manage.gm");

            List<Wiki> allWikis = WikiManager.getPageList(getContainer());
            List<Wiki> descendents = WikiManager.getDescendents(_wiki, true);
            List<Wiki> possibleParents = new ArrayList<Wiki>(allWikis.size() - descendents.size());

            // Nested loops is a nasty way to do this, but since the usual number of wiki pages won't be that large
            // (a couple hundred, tops?), this seems acceptable.  If performance suffers, we should look at using
            // better caching or a more complex query to pull the correct data from the DB in one call.
            for (Wiki page : allWikis)
            {
                boolean isDescendent = false;
                for (Wiki descendent : descendents)
                {
                    if (page.getRowId() == descendent.getRowId())
                    {
                        isDescendent = true;
                        break;
                    }
                }
                if (!isDescendent)
                    possibleParents.add(page);
            }

            List<Wiki> siblings = WikiManager.getWikisByParentId(getContainer().getId(), _wiki.getParent());
            manageView.addObject("wiki", _wiki);
            manageView.addObject("title", _wiki.latestVersion().getTitle());
            manageView.addObject("containerPath", getContainer().getPath());
            manageView.addObject("pages", allWikis);
            manageView.addObject("siblings", siblings);
            manageView.addObject("possibleParents", possibleParents);
            manageView.addObject("showChildren", SHOW_CHILD_REORDERING);
            manageView.addObject("_errors", errors);
            if (perms.allowDelete(_wiki))
                manageView.addObject("deleteLink", _wiki.getDeleteLink());
            else
                manageView.addObject("deleteLink", null);

            getPageConfig().setFocus("forms[0].name");
            return manageView;
        }

        public boolean handlePost(WikiManageForm form, BindException errors) throws Exception
        {
            String originalName = form.getOriginalName();
            String newName = form.getName();

            Container c = getContainer();
            _wiki = WikiManager.getWiki(c, originalName);
            if (null == _wiki)
            {
                HttpView.throwNotFound();
                return false;
            }

            BaseWikiPermissions perms = getPermissions();
            if(!perms.allowUpdate(_wiki))
                HttpView.throwUnauthorized("You do not have permissions to manage this wiki page");

            _wikiversion = WikiManager.getLatestVersion(_wiki);

            _wiki.setName(newName);
            _wiki.setParent(form.getParent());

            String title = form.getTitle() == null ? newName : form.getTitle();
            //update version only if title has changed
            if (_wikiversion.getTitle().compareTo(title) != 0)
                _wikiversion.setTitle(title);
            else
                _wikiversion = null;

            WikiManager.updateWiki(getUser(), _wiki, _wikiversion);

            if (SHOW_CHILD_REORDERING)
            {
                int[] childOrder = form.getChildOrderArray();
                if (childOrder.length > 0)
                    updateDisplayOrder(_wiki.getChildren(), childOrder);
            }
            int[] siblingOrder = form.getSiblingOrderArray();
            if (siblingOrder.length > 0)
            {
                List<Wiki> siblings = WikiManager.getWikisByParentId(getContainer().getId(), _wiki.getParent());
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
            String nextAction = form.getNextAction();
            if (nextAction == null || nextAction.length() == 0)
                nextAction = "page";

            ActionURL nextPage = getViewContext().cloneActionURL();
            nextPage.setAction(nextAction);
            nextPage.deleteParameters();
            nextPage.addParameter("name", form.getName());
            return nextPage;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (null == _wikiversion)
                _wikiversion = WikiManager.getLatestVersion(_wiki);
            return (new PageAction(_wiki,_wikiversion).appendNavTrail(root))
                    .addChild("Manage \"" + _wikiversion.getTitle() + "\"");
        }

        public ActionURL getUrl()
        {
            return wikiURL("manage").addParameter("name",_wiki.getName()).addParameter("rowId",""+_wiki.getRowId());
        }
    }


    private void updateDisplayOrder(List<Wiki> pages, int[] order) throws SQLException
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
                        WikiManager.updateWiki(getUser(), sibling, null);
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
        Wiki wiki = WikiManager.getWikiByEntityId(getContainer(), form.getEntityId());
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

    @RequiresPermission(ACL.PERM_UPDATE)
    public class ShowAddAttachmentAction extends AttachmentAction
    {
        public ModelAndView getAttachmentView(AttachmentForm form, Wiki wiki)
        {
            return AttachmentService.get().getAddAttachmentView(getContainer(), wiki);
        }
    }

    @RequiresPermission(ACL.PERM_UPDATE)
    public class AddAttachmentAction extends AttachmentAction
    {
        public ModelAndView getAttachmentView(AttachmentForm form, Wiki wiki) throws Exception
        {
            return AttachmentService.get().add(getUser(), wiki, getAttachmentFileList());
        }
    }

    @RequiresPermission(ACL.PERM_READ)
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

    @RequiresPermission(ACL.PERM_UPDATE)
    public class ShowConfirmDeleteAction extends AttachmentAction
    {
        public ModelAndView getAttachmentView(AttachmentForm form, Wiki wiki) throws Exception
        {
            return AttachmentService.get().getConfirmDeleteView(getContainer(), getViewContext().getActionURL(), wiki, form.getName());
        }
    }

    @RequiresPermission(ACL.PERM_READ) //will check programmatically
    public class DeleteAttachmentAction extends AttachmentAction
    {
        public ModelAndView getAttachmentView(AttachmentForm form, Wiki wiki) throws Exception
        {
            BaseWikiPermissions perms = getPermissions();
            if(!perms.allowUpdate(wiki))
                HttpView.throwUnauthorized("You do not have sufficient permissions to delete this attachment");

            ModelAndView ret = AttachmentService.get().delete(getUser(), wiki, form.getName());
            WikiCache.uncache(getContainer());
            return ret;
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class PrintAllAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Container c = getContainer();

            //get a list of wiki pages. Each wiki object contains only partial wiki data.
            //undone: change getPageList() to return simple list of full wiki pages, rather than half-assed wiki pages
            List<Wiki> wikiPageList = WikiManager.getPageList(c);

            //get wiki page with html and store in second list.
            List<Wiki> wikiContentList = new ArrayList<Wiki>();
            for (Wiki aWikiPageList : wikiPageList)
                wikiContentList.add(WikiManager.getWiki(c, aWikiPageList.getName()));

            GroovyView v = new GroovyView("/org/labkey/wiki/view/wiki_printall.gm");
            v.setFrame(WebPartView.FrameType.NONE);
            v.addObject("wikiPageList", wikiPageList);
            v.addObject("wikiContentList", wikiContentList);
            v.addObject("userEmail", getUser().toString());

            getPageConfig().setTemplate(PageConfig.Template.None);
            return new PrintTemplate(v, "Print all pages in " + getContainer().getPath());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class PrintRawAction extends SimpleViewAction<WikiNameForm>
    {
        public ModelAndView getView(WikiNameForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            String name = form.getName();
            if (name == null)
                HttpView.throwNotFound();

            Wiki wiki = WikiManager.getWiki(c, name);

            //just want to re-use same groovy file
            List<Wiki> wikiPageList = new ArrayList<Wiki>(1);
            wikiPageList.add(wiki);

            GroovyView v = new GroovyView("/org/labkey/wiki/view/wiki_printraw.gm");
            v.setFrame(WebPartView.FrameType.NONE);
            v.addObject("wikiPageList", wikiPageList);
            v.addObject("userEmail", getUser().toString());

            getPageConfig().setTemplate(PageConfig.Template.None);
            return new PrintTemplate(v, "Print Page '" + name + "'");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class PrintAllRawAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Container c = getContainer();
            //get a list of wiki pages. Each wiki object contains only partial wiki data.
            List<Wiki> wikiPageList = WikiManager.getPageList(c);

            GroovyView v = new GroovyView("/org/labkey/wiki/view/wiki_printraw.gm");
            v.setFrame(WebPartView.FrameType.NONE);
            v.addObject("wikiPageList", wikiPageList);
            v.addObject("userEmail", getUser().toString());

            getPageConfig().setTemplate(PageConfig.Template.None);
            return new PrintTemplate(v, "Print All Pages");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class CopyWikiForm extends FormData
    {
        private String _path;
        private String _sourceContainer;
        private String _destContainer;
        private String _pageName;
        private boolean _overwrite;

        public String getPageName()
        {
            return _pageName;
        }

        public void setPageName(String pageName)
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

        public void setSourceContainer(String sourceContainer)
        {
            _sourceContainer = sourceContainer;
        }

        public String getDestContainer()
        {
            return _destContainer;
        }

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


    private Wiki copyPage(Container cSrc, Wiki srcPage, Container cDest,
                          Map<String, Wiki> destPageMap, Map<Integer, Integer> pageIdMap)
            throws SQLException, IOException, AttachmentService.DuplicateFilenameException
    {
        return copyPage(cSrc, srcPage, cDest, destPageMap, pageIdMap, false);
    }


    //copies a single wiki page
    private Wiki copyPage(Container cSrc, Wiki srcPage, Container cDest, Map<String, Wiki> destPageMap,
                          Map<Integer, Integer> pageIdMap, boolean fOverwrite)
            throws SQLException, IOException, AttachmentService.DuplicateFilenameException
    {
        //get latest version
        WikiVersion srcLatestVersion = WikiManager.getLatestVersion(srcPage);

        //create new wiki page
        String srcName = srcPage.getName();
        String destName = srcName;
        Wiki destPage = WikiManager.getWiki(cDest, destName);

        //check whether name exists in destination wiki
        //if not overwriting, generate new name
        int i = 1;
        if (fOverwrite)
        {
            //can't overwrite if page does not exist
            if (!destPageMap.containsKey(destName))
                fOverwrite = false;
        }
        else
        {
            while(destPageMap.containsKey(destName))
                destName = srcName + i++;
        }

        //new wiki page
        Wiki newWikiPage = null;

        if (!fOverwrite)
        {
            newWikiPage = new Wiki(cDest, destName);
            newWikiPage.setDisplayOrder(srcPage.getDisplayOrder());

            //look up parent page via map
            if (pageIdMap != null)
            {
                Integer destParentId = pageIdMap.get(srcPage.getParent());
                if (destParentId != null)
                    newWikiPage.setParent(destParentId);
                else
                    newWikiPage.setParent(-1);
            }
        }

        //new wiki version
        WikiVersion newWikiVersion = new WikiVersion(destName);
        newWikiVersion.setTitle(srcLatestVersion.getTitle());
        newWikiVersion.setBody(srcLatestVersion.getBody());
        newWikiVersion.setRendererType(srcLatestVersion.getRendererType());

        //get attachments
        Wiki wikiWithAttachments = WikiManager.getWiki(cSrc, srcName);
        Collection<Attachment> attachments = wikiWithAttachments.getAttachments();
        List<AttachmentFile> files = AttachmentService.get().getAttachmentFiles(attachments);

        if (fOverwrite)
        {
            WikiManager.updateWiki(getUser(), destPage, newWikiVersion);
            AttachmentService.get().deleteAttachments(destPage);
            AttachmentService.get().addAttachments(getUser(), destPage, files);
        }
        else
        {
            //insert new wiki page in destination container
            WikiManager.insertWiki(getUser(), cDest, newWikiPage, newWikiVersion, files);

            //update destination page map
            destPageMap.put(destName, newWikiPage);

            //map source row id to dest row id
            if (pageIdMap != null)
            {
                pageIdMap.put(srcPage.getRowId(), newWikiPage.getRowId());
            }
        }
        return newWikiPage;
    }


    private Container getSourceContainer(String source)
            throws ServletException
    {
        Container cSource;
        if(source == null)
            cSource = getContainer();
        else
            cSource = ContainerManager.getForPath(source);
        return cSource;
    }


    private Container getDestContainer(String destContainer, String path)
            throws SQLException
    {
        if(destContainer == null)
        {
            destContainer = path;
            if(destContainer == null)
                return null;
        }

        //get the destination container
        Container cDest = ContainerManager.ensureContainer(destContainer);
        return cDest;
    }


    private void displayWikiModuleInDestContainer(Container cDest)
            throws SQLException
    {
        Set<Module> activeModules = new HashSet<Module>(cDest.getActiveModules());
        Module module = ModuleLoader.getInstance().getModule("Wiki");
        if (module != null)
        {
            //add wiki to active modules
            activeModules.add(module);
            cDest.setActiveModules(activeModules);
        }
    }


    @RequiresPermission(ACL.PERM_ADMIN)
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
            return new ActionURL("Wiki", "begin", copyWikiForm.getDestContainer());
        }

        public boolean handlePost(CopyWikiForm form, BindException errors) throws Exception
        {
            //user must have admin perms on both source and destination container

            //Get source container. Handle both post and get cases.
            Container cSrc = getSourceContainer(form.getSourceContainer());
            //Get destination container. Handle both post and get cases.
            Container cDest = getDestContainer(form.getDestContainer(), form.getPath());

            //get page name if specified (indicates we are copying subtree)
            String pageName = form.getPageName();
            //get selected page (top of subtree)
            Wiki parentPage = null;
            if (pageName != null)
            {
                parentPage = WikiManager.getWiki(cSrc, pageName);
                if (parentPage == null)
                    HttpView.throwNotFound("No page named '" + pageName + "' exists in the source container.");
            }

            //UNDONE: currently overwrite option is not exposed in UI
            boolean overwrite = form.isOverwrite();

            if (cDest != null && cDest.hasPermission(getUser(), ACL.PERM_ADMIN))
            {
                //get existing destination wiki pages
                Map<String, Wiki> destPageMap = WikiManager.getPageMap(cDest);

                //get source wiki pages
                List<Wiki> srcWikiPageList;
                if (parentPage != null)
                    srcWikiPageList = WikiManager.getSubTreePageList(cSrc, parentPage);
                else
                    srcWikiPageList = WikiManager.getPageList(cSrc);

                //map source page row ids to new page row ids
                Map<Integer, Integer> pageIdMap = new HashMap<Integer, Integer>();
                //shortcut for root topics
                pageIdMap.put(-1, -1);

                //copy each page in the list
                for(Wiki srcWikiPage : srcWikiPageList)
                {
                    copyPage(cSrc, srcWikiPage, cDest, destPageMap, pageIdMap);
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


    @RequiresPermission(ACL.PERM_ADMIN)
    public class CopySinglePageAction extends FormViewAction<CopyWikiForm>
    {
        public ModelAndView getView(CopyWikiForm copyWikiForm, boolean reshow, BindException errors) throws Exception
        {
            return null;
        }

        public boolean handlePost(CopyWikiForm form, BindException errors) throws Exception
        {
            //user must have admin perms on both source and destination container

            //get page name if specified (for single page copy)
            String pageName = form.getPageName();
            Container cSrc = getSourceContainer(form.getSourceContainer());
            Container cDest = getDestContainer(form.getDestContainer(), form.getPath());
            if (pageName == null || cSrc == null || cDest == null)
                HttpView.throwNotFound();
            if (!cDest.hasPermission(getUser(), ACL.PERM_ADMIN))
                HttpView.throwUnauthorized();
            Wiki srcPage = WikiManager.getWiki(cSrc, pageName);
            if (srcPage == null)
                HttpView.throwNotFound();

            //get existing destination wiki pages
            Map<String, Wiki> destPageMap = WikiManager.getPageMap(cDest);

            //copy single page
            Wiki newWikiPage = copyPage(cSrc, srcPage, cDest, destPageMap, null, form.isOverwrite());

            displayWikiModuleInDestContainer(cDest);

            ActionURL url = new ActionURL("Wiki", "page", cDest.getPath());
            url.addParameter("name", newWikiPage.getName());
            HttpView.throwRedirect(url);
            return true;
        }

        public void validateCommand(CopyWikiForm copyWikiForm, Errors errors)
        {
        }

        public ActionURL getSuccessURL(CopyWikiForm copyWikiForm)
        {
            return null;  //handlePost throws
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class CopyWikiLocationAction extends SimpleViewAction<CopyWikiForm>
    {
        public ModelAndView getView(CopyWikiForm form, BindException errors) throws Exception
        {
            //get projects and folders for which user has admin permissions
            Container c = getContainer();
            ActionURL currentUrl = getViewContext().cloneActionURL();
            ContainerTreeSelected ct = new ContainerTreeSelected("/", getUser(), ACL.PERM_ADMIN, currentUrl);
            ct.setCurrent(c);
            ct.setInitialLevel(1);

            HttpView v = new GroovyView("/org/labkey/wiki/view/wiki_copy.gm");
            //folder tree
            v.addObject("folderList", ct.render());
            //hidden inputs
            v.addObject("destContainer", c.getPath());
            v.addObject("sourceContainer", form.getSourceContainer());
            //cancel and return to source container
            v.addObject("cancelLink", ActionURL.toPathString("Wiki", "begin", form.getSourceContainer()));

            getPageConfig().setTemplate(PageConfig.Template.Dialog);

            return v;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermission(ACL.PERM_UPDATE)
    public class ShowUpdateAttachmentsAction extends SimpleViewAction<WikiDataForm>
    {
        Wiki _wiki;
        
        public ModelAndView getView(WikiDataForm form, BindException errors) throws Exception
        {
            String name = form.getName();
            String redirect = StringUtils.trimToEmpty(form.getRedirect());

            _wiki = WikiManager.getWiki(getContainer(), form.getName(), true);
            if (null == _wiki)
                HttpView.throwNotFound("Wiki page not found");

            GroovyView updateView = new GroovyView("/org/labkey/wiki/view/wiki_attachments.gm");
            updateView.setTitle("Update Attachments -- " + name);
            updateView.addObject("wiki", _wiki);

            DownloadURL attachmentURL = new DownloadURL("Wiki", getContainer().getPath(), _wiki.getEntityId(), null);
            if (redirect == null || redirect.length() == 0)
            {
                ActionURL pageView = getViewContext().cloneActionURL();
                pageView.setAction("page");
                redirect = pageView.getLocalURIString();
            }
            updateView.addObject("redirect", redirect);
            attachmentURL.setAction("showAddAttachment.view");
            updateView.addObject("addAttachmentURL", attachmentURL.getLocalURIString());
            attachmentURL.setAction("showConfirmDelete.view");
            updateView.addObject("deleteURLHelper", attachmentURL);
            return updateView;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return (new ManageAction(_wiki)).appendNavTrail(root)
                    .addChild("update page " + _wiki.getName());
        }
    }

    
    @RequiresPermission(ACL.PERM_READ)
    public class PageAction extends SimpleViewAction<WikiNameForm>
    {
        Wiki _wiki = null;
        WikiVersion _wikiversion = null;

        public PageAction()
        {
        }

        public PageAction(Wiki wiki, WikiVersion wikiversion)
        {
            _wiki = wiki;
            _wikiversion = wikiversion;
        }

        public ModelAndView getView(WikiNameForm form, BindException errors) throws Exception
        {
            String name = StringUtils.trimToEmpty(form.getName());
            //if there's no name parameter, find default page and reload with parameter.
            //default page is not necessarily same page displayed in wiki web part
            if (name.length() == 0)
                HttpView.throwRedirect(new BeginAction().getUrl());

            //page may be existing page, or may be new page
            _wiki = WikiManager.getWiki(getContainer(), name);
            boolean existing = _wiki != null;

            if (null == _wiki)
            {
                _wiki = new Wiki(getContainer(), name);
                _wikiversion = new WikiVersion(name);
                //set new page title to be name.
                _wikiversion.setTitle(name);
            }
            else
            {
                _wikiversion = WikiManager.getLatestVersion(_wiki);
                if (_wikiversion == null)
                    HttpView.throwNotFound();
            }

            if (isPrint())
            {
                JspView<Wiki> view = new JspView<Wiki>("/org/labkey/wiki/view/wikiPrint.jsp", _wiki);
                view.setFrame(WebPartView.FrameType.NONE);
                return view;
            }
            else
            {
                WebPartView v = new WikiView(_wiki, _wikiversion, existing);
                // get discussion view
                if (existing)
                {
                    ActionURL pageUrl = new PageAction(_wiki, _wikiversion).getUrl();
                    String discussionTitle = "discuss page - " +  _wikiversion.getTitle();
                    HttpView discussionView = getDiscussionView(_wiki.getEntityId(), pageUrl, discussionTitle);
                    v.setView("discussion", discussionView);
                    v.addObject("hasContent", existing);
                }
                return v;
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction().appendNavTrail(root);
            appendWikiTrail(root, _wiki);
            return root;
        }

        public void appendWikiTrail(NavTree root, Wiki wiki)
        {
            if (null == wiki)
                return;
            appendWikiTrail(root, wiki.getParentWiki());
            WikiVersion v = WikiManager.getLatestVersion(wiki, false);
            root.addChild(v == null ? wiki.getName() : v.getTitle(), wikiURL("page").addParameter("name", wiki.getName()));
        }

        ActionURL getUrl()
        {
            return wikiURL("page").addParameter("name", _wiki.getName());
        }
    }


    public static ActionURL getPageUrl(Wiki wiki)
    {
        ActionURL url = new ActionURL("Wiki", "page", wiki.lookupContainer());
        return url.addParameter("name", wiki.getName());
    }


    private HttpView getDiscussionView(String objectId, ActionURL pageURL, String title)
            throws ServletException
    {
        DiscussionService.Service service = DiscussionService.get();
        return service.getDisussionArea(getViewContext(), objectId, pageURL, title, true, false);
    }


    @RequiresPermission(ACL.PERM_READ)
    public class VersionAction extends SimpleViewAction<WikiNameForm>
    {
        Wiki _wiki = null;
        WikiVersion _wikiversion = null;

        public VersionAction()
        {
        }

        public VersionAction(Wiki wiki, WikiVersion wikiversion)
        {
            _wiki = wiki;
            _wikiversion = wikiversion;
        }
        
        public ModelAndView getView(WikiNameForm form, BindException errors) throws Exception
        {
            String name = StringUtils.trimToEmpty(form.getName());
            int version = form.getVersion();

            _wiki = WikiManager.getWiki(getContainer(), name);
            if (null == _wiki)
                HttpView.throwNotFound();

            BaseWikiPermissions perms = getPermissions();

            //return latest version first since it may be cached
            _wikiversion = WikiManager.getLatestVersion(_wiki);

            if (version > 0)
            {
                //if version requested is higher than latest version, throw not found
                if (version > _wikiversion.getVersion())
                    HttpView.throwNotFound();
                //if this is a valid version that is not the latest version, get that version
                if (version != _wikiversion.getVersion())
                    _wikiversion = WikiManager.getVersion(_wiki, version);
            }
            if (null == _wikiversion)
                HttpView.throwNotFound();

            HttpView versionView = new GroovyView("/org/labkey/wiki/view/wiki_version.gm");
            versionView.addObject("wiki", _wiki);
            versionView.addObject("title", _wikiversion.getTitle());
            versionView.addObject("wikiversion", _wikiversion);
            versionView.addObject("pageLink", _wiki.getPageLink());
            versionView.addObject("versionsLink", _wiki.getVersionsLink());
            versionView.addObject("makeCurrentLink", getViewContext().cloneActionURL().setAction("makeCurrent").toString());
            versionView.addObject("hasReadPermission", perms.allowRead(_wiki));
            versionView.addObject("hasAdminPermission", perms.allowAdmin());
            versionView.addObject("hasSetCurVersionPermission", perms.allowUpdate(_wiki));
            versionView.addObject("createdBy", UserManager.getDisplayName(_wikiversion.getCreatedBy(), getViewContext()));
            versionView.addObject("created",
                    DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG).format(_wikiversion.getCreated()));

            //base url for different versions of this page
            ActionURL versionURL = wikiURL("version", "name", name);
            versionView.addObject("versionLink", versionURL.toString());

            return versionView;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            String pageTitle = _wikiversion.getTitle();
            pageTitle += " (Version " + _wikiversion.getVersion() + " of " + WikiManager.getVersionCount(_wiki) + ")";

            return new VersionsAction(_wiki, _wikiversion).appendNavTrail(root)
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


    @RequiresPermission(ACL.PERM_READ) //requires update or update_own; will check below
    public class VersionsAction extends SimpleViewAction<WikiNameForm>
    {
        Wiki _wiki;
        WikiVersion _wikiversion;

        public VersionsAction() {}
        
        public VersionsAction(Wiki wiki, WikiVersion wikiversion)
        {
            _wiki = wiki;
            _wikiversion = wikiversion;
        }
        
        public ModelAndView getView(WikiNameForm form, BindException errors) throws Exception
        {
            String wikiname = form.getName();
            if (wikiname == null)
                HttpView.throwNotFound();
            _wiki = WikiManager.getWiki(getContainer(), wikiname);
            if (null == _wiki)
                HttpView.throwNotFound();
            _wikiversion = WikiManager.getLatestVersion(_wiki);

            BaseWikiPermissions perms = getPermissions();
            if(!perms.allowUpdate(_wiki))
                throw new UnauthorizedException("You do not have permissions to view the history for this page!");

            TableInfo tinfoVersions = CommSchema.getInstance().getTableInfoPageVersions();
            TableInfo tinfoPages = CommSchema.getInstance().getTableInfoPages();
            DataRegion dr = new DataRegion();

            //look up page name
            LookupColumn entityIdLookup = new LookupColumn(tinfoVersions.getColumn("PageEntityId"),
                    tinfoPages.getColumn("EntityId"), tinfoPages.getColumn("Name"));
            entityIdLookup.setCaption("Page Name");

            //look up container (for filter)
            LookupColumn containerLookup = new LookupColumn(tinfoVersions.getColumn("PageEntityId"),
                    tinfoPages.getColumn("EntityId"), tinfoPages.getColumn("Container"));
            DataColumn containerData = new DataColumn(containerLookup);
            containerData.setVisible(false);

            //version url
            DataColumn versionData = new DataColumn(tinfoVersions.getColumn("Version"));
            dr.addDisplayColumn(versionData);

            dr.addDisplayColumn(new DataColumn(tinfoVersions.getColumn("Title")));
            dr.addColumn(entityIdLookup);
            dr.addDisplayColumn(containerData);

            ColumnInfo colCreatedBy = tinfoVersions.getColumn("CreatedBy");
            // Set a custom renderer for the CreatedBy column
            DisplayColumn dc = new DisplayColumnCreatedBy(colCreatedBy);
            dr.addDisplayColumn(dc);

            dr.addDisplayColumn(new DataColumn(tinfoVersions.getColumn("Created")));

            //url displays version
            ActionURL urlVersion = wikiURL("version", "name", wikiname);
            String urlstring = urlVersion.toString() + "&version=${Version}";
            dr.getDisplayColumn("Version").setURL(urlstring);

            ButtonBar buttonBar = new ButtonBar();
            dr.setButtonBar(buttonBar);

            //filter on container and page name
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(containerLookup.getName(), getContainer().getId());
            filter.addCondition(entityIdLookup.getName(), wikiname);

            //sort DESC on version number
            Sort sort = new Sort("-version");

            GridView gridView = new GridView(dr);
            gridView.setFilter(filter);
            gridView.setSort(sort);

            LinkBarView lb = new LinkBarView(
                    new Pair<String, String>("return to page", getViewContext().cloneActionURL().setAction("page").toString()),
                    new Pair<String, String>("view versioned content", getViewContext().cloneActionURL().setAction("version").toString())
                    );
            lb.setDrawLine(true);
            lb.setTitle("History");

            VBox view = new VBox(lb, gridView);
            Wiki wiki = WikiManager.getWiki(getContainer(), form.getName(), true);
            if (wiki != null)
                view.addView(AttachmentService.get().getHistoryView(getViewContext(), wiki));
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return new PageAction(_wiki,_wikiversion).appendNavTrail(root).
                    addChild("History for Page \"" + _wiki.getName() + "\"", getUrl());
        }

        public ActionURL getUrl()
        {
            return wikiURL("versions").addParameter("name",_wiki.getName());
        }
    }


    @RequiresPermission(ACL.PERM_READ) //will check in code below
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
            String wikiName = form.getName();
            int version = form.getVersion();

            _wiki = WikiManager.getWiki(getContainer(), wikiName);
            _wikiversion = WikiManager.getVersion(_wiki, version);

            //per Britt and Adam, users with update perms on this page should be able
            //to set the current version--requiring admin doesn't make sense, as any
            //user with update perms could just as easily update the page to be the
            //same as some previous version
            BaseWikiPermissions perms = getPermissions();
            if(!perms.allowUpdate(_wiki))
                HttpView.throwUnauthorized("You do not have permission to set the current version of this page.");

            //update wiki & insert new wiki version
            WikiManager.updateWiki(getUser(), _wiki, _wikiversion);
            return true;
        }

        public void validateCommand(WikiNameForm wikiNameForm, Errors errors)
        {
        }

        public ActionURL getSuccessURL(WikiNameForm wikiNameForm)
        {
            return new VersionAction(_wiki,_wikiversion).getUrl();
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
            int rows = WikiManager.purge();
            return new HtmlView("deleted " + rows + " pages<br>");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static class WikiManageForm
    {
        private String _rowId;
        private String _originalName;
        private String _name;
        private String _title;
        private int _parent;
        private String _childOrder;
        private String _siblingOrder;
        private String _nextAction;
        private String _containerPath;

        public String getContainerPath()
        {
            return _containerPath;
        }

        public void setContainerPath(String containerPath)
        {
            _containerPath = containerPath;
        }

        public String getChildOrder()
        {
            return _childOrder;
        }

        public void setChildOrder(String childIdList)
        {
            _childOrder = childIdList;
        }

        public String getSiblingOrder()
        {
            return _siblingOrder;
        }

        public void setSiblingOrder(String siblingIdList)
        {
            _siblingOrder = siblingIdList;
        }

        private int[] breakIdList(String list)
        {
            if (list == null)
                return new int[0];
            else
            {
                String[] idStrs = list.split(",");
                int[] ids = new int[idStrs.length];
                for (int i = 0; i < idStrs.length; i++)
                    ids[i] = Integer.parseInt(idStrs[i]);
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

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public String getTitle()
        {
            return _title;
        }

        public void setTitle(String title)
        {
            _title = title;
        }

        public String getOriginalName()
        {
            return _originalName;
        }

        public void setOriginalName(String name)
        {
            _originalName = name;
        }

        public int getParent()
        {
            return _parent;
        }

        public void setParent(int parent)
        {
            _parent = parent;
        }

        public String getRowId()
        {
            return _rowId;
        }

        public void setRowId(String rowId)
        {
            _rowId = rowId;
        }

        public String getNextAction()
        {
            return _nextAction;
        }

        public void setNextAction(String nextAction)
        {
            _nextAction = nextAction;
        }

        public void validate(Errors errors)
        {
            //check name
            String newName = getName();
            String oldName = getOriginalName();
            if(newName == null)
                errors.rejectValue("name", ERROR_MSG, "You must provide a name for this page.");
            else
            {
                //check for existing wiki with this name
                Container c = ContainerManager.getForPath(getContainerPath());
                if (!newName.equalsIgnoreCase(oldName) && WikiManager.wikiNameExists(c, newName))
                    errors.rejectValue("name", ERROR_MSG, "A page with the name '" + newName + "' already exists in this folder. Please choose a different name.");
            }
        }
    }

    
     public static class WikiNameForm
     {
         private String _name;
         private String _redirect;
         private int _rowId;
         private int _parent;
         private int _version;

         public int getVersion()
         {
             return _version;
         }

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

         public String getName()
         {
             return _name;
         }

         public void setName(String name)
         {
             _name = name;
         }

         public String getRedirect()
         {
             return _redirect;
         }

         public void setRedirect(String redirect)
         {
             _redirect = redirect;
         }

         public int getRowId()
         {
             return _rowId;
         }

         public void setRowId(int rowId)
         {
             _rowId = rowId;
         }
     }

     public static class WikiDataForm extends WikiNameForm
     {
         private String _title;
         private String _body;
         private String _rendererType;
         private String _nextAction;
         private boolean _reshow;

         public String getBody()
         {
             return _body;
         }

         public void setBody(String body)
         {
             _body = body;
         }

         public String getTitle()
         {
             return _title;
         }

         public void setTitle(String title)
         {
             _title = title;
         }

         public String getRendererType()
         {
             return _rendererType;
         }

         public void setRendererType(String rendererType)
         {
             _rendererType = rendererType;
         }

         public void validate(Errors errors, boolean allowMaliciousContent)
         {
            //check body
            String body = StringUtils.trimToEmpty(getBody());
            setBody(body);
            if(body.length() == 0)
                errors.rejectValue("body", ERROR_MSG, "Page body cannot be empty. Delete page if you wish to remove page content.");
            else
            {
                // TODO: this validation call should move onto the renderer enum, but that's
                // too big a change for this point in our release cycle:
                ArrayList<String> tidyErrors = new ArrayList<String>();

                WikiRendererType renderer = WikiRendererType.valueOf(getRendererType());
                if (renderer == WikiRendererType.HTML)
                    body = PageFlowUtil.validateHtml(body, tidyErrors, allowMaliciousContent);

                for (String err : tidyErrors)
                    errors.rejectValue("body", ERROR_MSG, err);

                if (tidyErrors.isEmpty())
                {
                    // uncomment to replace with tidy output
                    // setBody(body);
                }
            }
         }

         public String getNextAction()
         {
             return _nextAction;
         }

         public void setNextAction(String nextAction)
         {
             _nextAction = nextAction;
         }

         public boolean isReshow()
         {
             return _reshow;
         }

         public void setReshow(boolean reshow)
         {
             _reshow = reshow;
         }
     }

    public static class CollapseExpandForm extends FormData
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

    @RequiresPermission(ACL.PERM_NONE)
    public class CollapseExpandAction extends FormViewAction<CollapseExpandForm>
    {

        public void validateCommand(CollapseExpandForm target, Errors errors)
        {
        }

        public ModelAndView getView(CollapseExpandForm form, boolean reshow, BindException errors) throws Exception
        {
            NavTreeManager.expandCollapsePath(getViewContext(), form.getTreeId(), form.getPath(), form.isCollapse());
            return null;
        }

        public boolean handlePost(CollapseExpandForm form, BindException errors) throws Exception
        {
            NavTreeManager.expandCollapsePath(getViewContext(), form.getTreeId(), form.getPath(), form.isCollapse());
            return true;
        }

        public ActionURL getSuccessURL(CollapseExpandForm collapseExpandForm)
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class WikiTOC extends NavTreeMenu
    {
        String _selectedLink;
        
        static private Container getTocContainer(ViewContext context)
        {
            //set specified web part container
            Container cToc;
            //get stored property value for source container for toc
            Object id = context.get("webPartContainer");
            //if no value is stored, use the current container
            if(id == null)
                cToc = context.getContainer();
            else
            {
                cToc = ContainerManager.getForId(id.toString());
                assert (cToc != null);
            }

            return cToc;
        }

        static private boolean isAllExpanded(NavTree[] nodes)
        {
            for ( NavTree node : nodes )
            {
                if (node.getChildCount() != 0)
                {
                    if (node.isCollapsed()) return false;
                    if (!isAllExpanded(node.getChildren())) return false;
                }
            }
            return true;
        }

        static private String getNavTreeId(ViewContext context)
        {
            Container cToc = getTocContainer(context);
            return "Wiki-TOC-" + cToc.getId();
        }

        static private NavTree[] getNavTree(ViewContext context)
        {
            Container cToc = getTocContainer(context);
            return createNavTree(WikiManager.getWikisByParentId(cToc.getId(), -1), getNavTreeId(context));
        }

        static private NavTree[] createNavTree(List<Wiki> pageList, String rootId)
        {
            ArrayList<NavTree> elements = new ArrayList<NavTree>();
            //add all pages to the nav tree
            for (Wiki page : pageList)
            {
                NavTree node = new NavTree(page.latestVersion().getTitle(), page.getPageLink(), true);
                node.addChildren(createNavTree(page.getChildren(), rootId));
                node.setId(rootId);
                elements.add(node);
            }
            return elements.toArray(new NavTree[0]);
        }

        public WikiTOC(ViewContext context)
        {
            super(context, "", null);
            setFrame(FrameType.PORTAL);
            setHighlightSelection(true);

            //set specified web part title
            Object title = context.get("title");
            if(title == null)
                title = "Pages";
            setTitle(title.toString());
        }

        private Wiki findSelectedPage(ViewContext context)
        {
            Container cToc = getTocContainer(context);
            //get the page list for the toc container
            List<Wiki> pageList = WikiManager.getPageList(cToc);

            //are there pages in page list?
            if (pageList.size() > 0)
            {
                //determine current page
                String pageViewName = context.getRequest().getParameter("name");
                //if no current page, determine the default page for the toc container
                if(null == pageViewName)
                    pageViewName = getDefaultPage(cToc).getName();

                if (null != pageViewName)
                    return WikiManager.getWiki(cToc, pageViewName);
            }
            return null;
        }

        @Override
        protected boolean matchPath(String link, ActionURL currentUrl, String pattern)
        {
            if (_selectedLink == null)  return false;
            return link.compareToIgnoreCase(_selectedLink) == 0;

        }

        @Override
        protected void renderView(Object model, PrintWriter out) throws Exception
        {
            ViewContext context = getViewContext();
            User user = context.getUser();
            Container c = context.getContainer();
            setId(getNavTreeId(context));
            setElements(context, getNavTree(context));

            if (null == c)
            {
                // WebPart case
                try
                {
                    c = getViewContext().getContainer(0);
                }
                catch (Exception x)
                {
                }
            }

            if (!c.hasPermission(user, ACL.PERM_READ))
            {
                if (user.isGuest())
                {
                    out.println("Please log in to see this data.");
                }
                else
                {
                    out.println("You do not have permission to see this data.");
                }

                return;
            }

            boolean isInWebPart = false;
            //is page being rendered in web part or in module?
            String pageUrl = context.getActionURL().getPageFlow();
            if (pageUrl.equalsIgnoreCase("Project"))
                isInWebPart = true;

            Container cToc = getTocContainer(context);

            //Should we show the option to expand all nodes?
            boolean showExpandOption = false;
            for (NavTree t : getElements())
            {
                if (t.getChildCount() != 0)
                {
                    showExpandOption = true;
                    break;
                }
            }

            //Generate a root node to simplify finding subtrees
            //NOTE:  This is an artifact of the detail that we can't use the
            //NavTreeMenu (this) as the root because it won't recurse into its children
            //See NavTreeMenu.findSubtree
            
            NavTree root = new NavTree();
            root.setId(this.getId());
            root.addChildren(getElements());

            Wiki selectedPage = findSelectedPage(context);

            //remember the link to the selected page so we can highlight it appropriately if we are not in
            //a web-part context
            if (null != selectedPage && !isInWebPart)
                this._selectedLink = selectedPage.getPageLink();


            //Make sure the path to the current page is expanded
            //FIX: per 5246, we will no longer expand the children of the current page by default
            if (null != selectedPage)
            {
                String path = "";
                Wiki page = selectedPage;
                boolean expandLeaf =  selectedPage.getChildren().size() != 0 ? true : false;
                Stack<String> stkPages = new Stack<String>();

                page = page.getParentWiki ();
                 while (null != page )
                {
                    stkPages.push(page.latestVersion().getTitle());
                    page = page.getParentWiki ();
                }

                while (!stkPages.empty())
                {
                    path = path + "/" + NavTree.escapeKey(stkPages.pop());
                    NavTree node = root.findSubtree(path);
                    //Don't add it to the expand collapse set, since this would slowly collect
                    //every node we've ever visited.  This way we'll only remember the state
                    //if the user explicitly visits a node

                    //NavTreeManager.expandCollapsePath(context, getId(), path, false);

                    //Instead, we'll just expand it manually
                    if (node != null)
                        node.setCollapsed(false);
                }
            }

            //Apply the current expand state
            NavTreeManager.applyExpandState(root, context);


            String nextLink = null, prevLink = null;
            if (null != selectedPage)
            {
                //get next and previous links
                //UNDONE: consolidate wiki code so wiki object is always the same
                //(so we can use pageList here rather than creating a second list)
                List<String> nameList = WikiManager.getWikiNameList(cToc);
                if(nameList.contains(selectedPage.getName()))
                {
                    //determine where this page is in the ordered wiki page list
                    int pageIndex = nameList.indexOf(selectedPage.getName());
                    //if it's not the first page in the list, display the previous link
                    if(pageIndex > 0)
                    {
                        Wiki wikiPrev = WikiManager.getWiki(cToc, nameList.get(pageIndex - 1));
                        prevLink = wikiPrev.getPageLink();
                    }
                    //if it's not the last page in the list, display the next link
                    if(pageIndex < nameList.size() - 1)
                    {
                        Wiki wikiNext = WikiManager.getWiki(cToc, nameList.get(pageIndex + 1));
                        nextLink = wikiNext.getPageLink();
                    }
                }
            }

            //output only this one if wiki contains no pages
            boolean bHasInsert = cToc.hasPermission(user, ACL.PERM_INSERT),
                    bHasCopy = (cToc.hasPermission(user, ACL.PERM_ADMIN) && getElements().length > 0) ? true : false,
                    bHasPrint = ((!isInWebPart || cToc.hasPermission(user, ACL.PERM_INSERT)) && getElements().length > 0) ?
                                    true : false;

            if (bHasInsert || bHasCopy || bHasPrint)
            {
                out.println("<table width=\"100%\" cellpadding=0>");
                out.println("<tr>");
                out.println("<td  style=\"height:16;\">");
            }

            if (bHasInsert)
            {
                out.print("[<a class=\"link\" href=\"");
                out.print(new ActionURL(NewPageAction.class, cToc).getLocalURIString());
                out.print("\">new page</a>]&nbsp;");
            }
            if (bHasCopy)
            {
                URLHelper copyUrl = new ActionURL("Wiki", "copyWikiLocation", cToc.getPath());
                //pass in source container as a param.
                copyUrl.addParameter("sourceContainer", cToc.getPath());

                out.print("[<a class=\"link\" href=\"");
                out.print(PageFlowUtil.filter(copyUrl.toString()));
                out.print("\">copy pages</a>]&nbsp;");
            }
            if (bHasPrint)
            {
                out.print("[<a class=\"link\" href=\"");
                out.print(PageFlowUtil.filter(ActionURL.toPathString("Wiki", "printAll", cToc.getPath())));
                out.print("\" target=\"_blank\">print all</a>]");
            }
            if (bHasInsert || bHasCopy || bHasPrint)
            {
                out.println("");
                out.println("</td></tr>");
                out.println("</table>");
            }


            out.println("<div id=\"NavTree-"+ getId() +"\">");
            super.renderView(model, out);
            out.println("</div>");
            if (getElements().length > 1)
            {
                out.println("<br>");
                out.println("<table width=\"100%\" cellpadding=0>");
                out.println("<tr>\n<td>");

                if(prevLink != null)
                {
                    out.print("<a href=\"");
                    out.print(PageFlowUtil.filter(prevLink));
                    out.println("\">[previous]</a>");
                }
                else
                    out.println("[previous]");

                if(nextLink != null)
                {
                    out.print("<a href=\"");
                    out.print(PageFlowUtil.filter(nextLink));
                    out.println("\">[next]</a>");
                }
                else
                    out.println("[next]");

                if (showExpandOption)
                {
                    out.println("</td></tr><tr><td>&nbsp;</td></tr><tr><td>");
                    out.println("[<a class=\"link\" onclick=\"adjustAllTocEntries('NavTree-"+getId()+"', true, true)\" href=\"javascript:;\">expand&nbsp;all</a>]");
                    out.println("[<a class=\"link\" onclick=\"adjustAllTocEntries('NavTree-"+getId()+"', true, false)\" href=\"javascript:;\">collapse&nbsp;all</a>]");
                }
                out.println("</td>\n</tr>\n</table>");
            }
        }

    }

    /**
     * Search Action Implementation
     */
    @RequiresPermission(ACL.PERM_READ)
    public class SearchAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Container c = getViewContext().getContainer();
            String searchTerm = (String)getProperty("search", "");
            boolean includeSubfolders = getProperty("includeSubfolders", "off").equals("on");

            Module module = ModuleLoader.getInstance().getCurrentModule();
            List<Search.Searchable> l = new ArrayList<Search.Searchable>();
            l.add((Search.Searchable)module);

            getPageConfig().setHelpTopic(new HelpTopic("search", HelpTopic.Area.DEFAULT));

            return new Search.SearchResultsView(c, l, searchTerm, new ActionURL("Wiki", "search", c), getUser(), includeSubfolders, true);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Wiki Search Results");
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


    ActionURL wikiURL(String action, String... args) throws ServletException
    {
        ActionURL url = new ActionURL("Wiki", action, getContainer());
        for (int i=0 ; i<args.length ; i+=2)
            url.addParameter(args[i], args[i+1]);
        return url;
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class CheckForBrokenLinksAction extends SimpleViewAction<BrokenLinkForm>
    {
        public ModelAndView getView(BrokenLinkForm form, BindException errors) throws Exception
        {
            ActionURL baseURL = getViewContext().cloneActionURL().deleteParameters().setAction((String)null);
            StringBuilder sb = new StringBuilder();
            testLinksForAllPages(getContainer(), baseURL, sb, form.getRecurse());

            if (0 == sb.length())
                sb.append("No broken links found");

            sb.insert(0, "<table>\n");
            sb.append("</table>\n");
            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            return new HtmlView(sb.toString());
        }


        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    private static void testLinksForAllPages(Container c, ActionURL baseURL, StringBuilder sb, boolean recurse) throws SQLException
    {
        List<Wiki> wikis = WikiManager.getPageList(c);

        for (Wiki wiki2 : wikis)
        {
            // We want a full wiki with attachments so rendering doesn't end up caching bogus HTML (e.g., lacking links
            // to attached content)
            Wiki fullWiki = WikiManager.getWiki(c, wiki2.getName());

            // Could be null if wikis have been deleted/renamed while link checker is running
            if (null == fullWiki)
                continue;

            WikiVersion latest = fullWiki.latestVersion();
            String html = latest.getHtml(c, fullWiki);
            Collection<String> errorStrings = new ArrayList<String>();
            Document doc = PageFlowUtil.convertHtmlToDocument(html, errorStrings);

            List<Pair<String, String>> unreachableLinks = new ArrayList<Pair<String, String>>();
            testLinks(doc.getDocumentElement(), baseURL, unreachableLinks);

            if (!unreachableLinks.isEmpty())
            {
                sb.append("<tr><td colspan=2><b>").append(recurse ? c.getPath() + ": " : "").append(latest.getTitle()).append(" (").append(fullWiki.getName()).append(")").append("</b><br></td></tr>\n");

                for (Pair<String, String> link : unreachableLinks)
                    sb.append("<tr><td>").append(link.first).append("</td><td>").append(link.second).append("</td></tr>\n");

                sb.append("<tr><td colspan=2>&nbsp;</td></tr>\n");
            }
        }

        if (recurse)
        {
            for (Container child : c.getChildren())
                testLinksForAllPages(child, baseURL.clone().setExtraPath(child.getPath()), sb, true);
        }
    }


    private static void testLinks(Node node, ActionURL baseURL, List<Pair<String, String>> unreachableLinks)
    {
        if ("a".equals(node.getLocalName()))
        {
            String failureMessage = null;
            HttpMethod method = null;
            String sourceHref = null;

            try
            {
                sourceHref = node.getAttributes().getNamedItem("href").getNodeValue();
                URI uri = new URI(sourceHref);
                String urlString;

                if (uri.isAbsolute())
                {
                    urlString = sourceHref;
                }
                else
                {
                    // Add base server to /<context path>/wiki/container/page
                    if (sourceHref.startsWith("/"))
                    {
                        urlString = AppProps.getInstance().getBaseServerUrl() + sourceHref;
                    }
                    // Add base server, context path, controller & container to ../xyz
                    else
                    {
                        urlString = baseURL.getURIString() + sourceHref;
                    }
                }

                // Ignore javascript: & mailto: links
                if (urlString.startsWith("javascript:") || urlString.startsWith("mailto:"))
                    return;

                HttpClient client = new HttpClient();
                HttpMethodParams params = new HttpMethodParams();
                params.setSoTimeout(30000);    // Wait no more than 30 seconds
                method = new GetMethod(urlString);
                method.setParams(params);
                method.setFollowRedirects(true);
                int statusCode = client.executeMethod(method);
                String contents = method.getResponseBodyAsString();

                if (HttpStatus.SC_OK != statusCode)
                {
                    failureMessage = method.getStatusLine().toString();
                }
                else
                {
                    if (contents.indexOf("This page has no content.") != -1)
                    {
                        failureMessage = "Wiki page does not exist";
                    }
                }
            }
            catch(Exception e)
            {
                failureMessage = e.toString();
            }
            finally
            {
                if (null != method)
                    method.releaseConnection();

                if (null != failureMessage)
                    unreachableLinks.add(new Pair<String, String>(sourceHref, failureMessage));
            }
        }
        else
        {
            NodeList children = node.getChildNodes();

            for (int i = 0; i < children.getLength(); i++)
            {
                Node child = children.item(i);

                if (null != child)
                    testLinks(child, baseURL, unreachableLinks);
            }
        }
    }

    public static class BrokenLinkForm
    {
        private boolean _recurse = false;

        public boolean getRecurse()
        {
            return _recurse;
        }

        public void setRecurse(boolean recurse)
        {
            _recurse = recurse;
        }
    }

    public static class ContainerForm
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

    @RequiresPermission(ACL.PERM_READ)
    public class GetPagesAction extends ApiAction<ContainerForm>
    {
        public ApiResponse execute(ContainerForm form, BindException errors) throws Exception
        {
            if(null == form.getId() || form.getId().length() == 0)
                throw new IllegalArgumentException("The id parameter must be set to a valid container id!");

            Container container = ContainerManager.getForId(form.getId());
            if(null == container)
                throw new IllegalArgumentException("The container id '" + form.getId() + "' is not valid.");
            
            List<Wiki> wikiList = WikiManager.getPageList(container);
            if(null == wikiList)
                return new ApiSimpleResponse("pages", null);

            List<Map<String,String>> pages = new ArrayList<Map<String,String>>(wikiList.size());
            for(Wiki wiki : wikiList)
            {
                Map<String,String> pageMap = new HashMap<String,String>();
                pageMap.put("name", wiki.getName());
                if(wiki.latestVersion() != null)
                    pageMap.put("title", wiki.latestVersion().getTitle());
                pages.add(pageMap);
            }

            return new ApiSimpleResponse("pages", pages);
        }
    }

    public static class EditWikiForm
    {
        private String _name;
        private String _redirect;
        private String _format;
        private String _defName;
        private String _pageId;
        private int _index;

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public String getRedirect()
        {
            return _redirect;
        }

        public void setRedirect(String redir)
        {
            _redirect = redir;
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

        public void setDefName(String defName)
        {
            _defName = defName;
        }

        public String getPageId()
        {
            return _pageId;
        }

        public void setPageId(String pageId)
        {
            _pageId = pageId;
        }

        public int getIndex()
        {
            return _index;
        }

        public void setIndex(int index)
        {
            _index = index;
        }
    }

    @RequiresPermission(ACL.PERM_READ) //will check below
    public class EditWikiAction extends SimpleViewAction<EditWikiForm>
    {
        private WikiVersion _wikiVer = null;
        private Wiki _wiki = null;

        public ModelAndView getView(EditWikiForm form, BindException errors) throws Exception
        {
            //get the wiki
            Wiki wiki = null;
            WikiVersion curVersion = null;

            if(null != form.getName() && form.getName().length() > 0)
            {
                wiki = WikiManager.getWiki(getViewContext().getContainer(), form.getName());
                if(null == wiki)
                    throw new NotFoundException("There is no wiki in the current folder named '" + form.getName() + '!');

                //get the current version
                curVersion = wiki.latestVersion();
                if(null == curVersion)
                    throw new NotFoundException("Could not locate the current version of the wiki named '" + form.getName() + "'!");
            }

            //check permissions
            Container container = getViewContext().getContainer();
            User user = getViewContext().getUser();
            BaseWikiPermissions perms = new BaseWikiPermissions(user, container);
            if(null == wiki)
            {
                //if no wiki, this is an insert, so user must have insert perms
                if(!container.hasPermission(user, ACL.PERM_INSERT))
                    throw new UnauthorizedException("You do not have permissions to create new wiki pages in this folder!");
            }
            else
            {
                //updating wiki--use BaseWikiPermissions
                if(!perms.allowUpdate(wiki))
                    throw new UnauthorizedException("You do not have permissions to edit this wiki page!");
            }

            //get the user's editor preference
            Map properties = PropertyManager.getProperties(getUser().getUserId(),
                    getContainer().getId(), SetEditorPreferenceAction.CAT_EDITOR_PREFERENCE, true);
            boolean useVisualEditor = !("false".equalsIgnoreCase((String)properties.get(SetEditorPreferenceAction.PROP_USE_VISUAL_EDITOR)));

            WikiEditModel model = new WikiEditModel(container, wiki, curVersion,
                    form.getRedirect(), form.getFormat(), form.getDefName(), useVisualEditor,
                    form.getPageId(), form.getIndex(), user);

            //cache the wiki so we can build the nav trail
            _wiki = wiki;
            _wikiVer = curVersion;

            return new JspView<WikiEditModel>("/org/labkey/wiki/view/wikiEdit.jsp", model);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if(null != _wiki && null != _wikiVer)
            {
                ActionURL pageUrl = new ActionURL(WikiController.PageAction.class, getViewContext().getContainer());
                pageUrl.addParameter("name", _wiki.getName());
                return root.addChild(_wikiVer.getTitle(), pageUrl).addChild("Edit");
            }
            else
                return root.addChild("New Page");
        }
    }

    public static class SaveWikiForm
    {
        private String _entityId;
        private Integer _rowId = -1;
        private String _name;
        private String _title;
        private String _body;
        private Integer _parent = -1;
        private String _rendererType;
        private String _pageId;
        private int _index = -1;

        public String getEntityId()
        {
            return _entityId;
        }

        public void setEntityId(String entityId)
        {
            _entityId = entityId;
        }

        public Integer getRowId()
        {
            return _rowId;
        }

        public void setRowId(Integer rowId)
        {
            _rowId = rowId;
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public String getTitle()
        {
            return _title;
        }

        public void setTitle(String title)
        {
            _title = title;
        }

        public String getBody()
        {
            return _body;
        }

        public void setBody(String body)
        {
            _body = body;
        }

        public boolean isNew()
        {
            return null == _entityId || _entityId.length() <= 0;
        }

        public Integer getParent()
        {
            return _parent;
        }

        public void setParent(Integer parent)
        {
            _parent = parent;
        }

        public int getParentId()
        {
            return null == _parent ? -1 : _parent.intValue();
        }

        public String getRendererType()
        {
            return _rendererType;
        }

        public void setRendererType(String rendererType)
        {
            _rendererType = rendererType;
        }

        public String getPageId()
        {
            return _pageId;
        }

        public void setPageId(String pageId)
        {
            _pageId = pageId;
        }

        public int getIndex()
        {
            return _index;
        }

        public void setIndex(int index)
        {
            _index = index;
        }
    }

    @RequiresPermission(ACL.PERM_READ) //will check below
    public class SaveWikiAction extends ApiAction<SaveWikiForm>
    {
        public ApiResponse execute(SaveWikiForm form, BindException errors) throws Exception
        {
            //if no entityId was passed, insert it
            if(form.isNew())
                return insertWiki(form, errors);
            else
                return updateWiki(form, errors);
        }

        public void validateForm(SaveWikiForm form, Errors errors)
        {
            Container container = getViewContext().getContainer();
            String name = form.getName();

            //must have a name
            //cannot start with _ if not admin
            //if new, must be unique
            if(null == name || name.length() <= 0)
                errors.rejectValue("name", ERROR_MSG, "You must provide a name for this page.");
            else if(name.startsWith("_") && !container.hasPermission(getUser(), ACL.PERM_ADMIN))
                errors.rejectValue("name", ERROR_MSG, "Wiki names starting with underscore are reserved for administrators.");

            //check to ensure that there is not an existing wiki with the same name
            //but different entity id (works for both insert and update case)
            Wiki existingWiki = WikiManager.getWiki(container, name);
            if(null != existingWiki && !(existingWiki.getEntityId().equals(form.getEntityId())))
                errors.rejectValue("name", ERROR_MSG, "Page '" + name + "' already exists within this folder.");

            //must have a body, and if HTML, must be valid according to tidy
            if(null == form.getBody() || form.getBody().trim().length() <= 0)
                errors.rejectValue("body", ERROR_MSG, "The body text may not be blank.");
            else if(null == form.getRendererType() || WikiRendererType.valueOf(form.getRendererType()) == WikiRendererType.HTML)
            {
                String body = form.getBody();
                ArrayList<String> tidyErrors = new ArrayList<String>();
                User user = getViewContext().getUser();

                boolean allowMaliciousContent = UserManager.mayWriteScript(user);
                PageFlowUtil.validateHtml(body, tidyErrors, allowMaliciousContent);

                for(String err : tidyErrors)
                    errors.rejectValue("body", ERROR_MSG, err);
            }
        }

        protected ApiResponse insertWiki(SaveWikiForm form, BindException errors) throws Exception
        {
            Container c = getViewContext().getContainer();
            User user = getViewContext().getUser();
            if(!c.hasPermission(user, ACL.PERM_INSERT))
                throw new UnauthorizedException("You do not have permissions to create a new wiki page in this folder!");

            String wikiname = form.getName();

            Wiki wiki = new Wiki(c, wikiname);
            wiki.setParent(form.getParentId());

            WikiVersion wikiversion = new WikiVersion(wikiname);

            //if user has not submitted title, use page name as title
            String title = form.getTitle() == null ? wikiname : form.getTitle();
            wikiversion.setTitle(title);
            wikiversion.setBody(form.getBody());
            wikiversion.setRendererType(form.getRendererType());

            //insert new wiki and new version
            WikiManager.insertWiki(getUser(), c, wiki, wikiversion, null);

            String pageId = StringUtils.trimToEmpty(form.getPageId());
            int index = form.getIndex();
            if (pageId != null && index > 0)
            {
                //get web part referenced by page id and index
                Portal.WebPart webPart = Portal.getPart(pageId, index);
                webPart.setProperty("webPartContainer", c.getId());
                webPart.setProperty("name", wikiname);
                Portal.updatePart(getUser(), webPart);
            }

            //return an API response containing the current wiki and version data
            ApiSimpleResponse resp = new ApiSimpleResponse("success", true);
            resp.put("wikiProps", getWikiProps(wiki, wikiversion));
            return resp;
        }

        protected HashMap<String,Object> getWikiProps(Wiki wiki, WikiVersion wikiversion)
        {
            HashMap<String,Object> wikiProps = new HashMap<String,Object>();
            wikiProps.put("entityId", wiki.getEntityId());
            wikiProps.put("rowId", wiki.getRowId());
            wikiProps.put("name", wiki.getName());
            wikiProps.put("title", wikiversion.getTitle());
            wikiProps.put("body", wikiversion.getBody()); //CONSIDER: do we really need to return the body here?
            wikiProps.put("rendererType", wikiversion.getRendererType());
            wikiProps.put("parent", wiki.getParent());
            return wikiProps;
        }

        protected ApiResponse updateWiki(SaveWikiForm form, BindException errors) throws Exception
        {
            if(null == form.getEntityId() || form.getEntityId().length() <= 0)
                throw new IllegalArgumentException("The entityId parameter must be supplied.");

            Wiki wikiUpdate = WikiManager.getWikiByEntityId(getViewContext().getContainer(), form.getEntityId());
            if (wikiUpdate == null)
                HttpView.throwNotFound("Could not find the wiki page matching the passed id; if it was a valid page, it may have been deleted by another user.");

            BaseWikiPermissions perms = getPermissions();
            if(!perms.allowUpdate(wikiUpdate))
                HttpView.throwUnauthorized("You are not allowed to edit this wiki page.");

            WikiVersion wikiversion = wikiUpdate.latestVersion();
            if (wikiversion == null)
                HttpView.throwNotFound("There is no current version associated with this wiki page; the existing page may have been deleted.");

            //if title is null, use name
            String title = form.getTitle() == null ? form.getName() : form.getTitle();
            String currentRendererName = form.getRendererType();

            //only insert new version if something has changed
            if (StringUtils.trimToEmpty(wikiUpdate.getName()).compareTo(form.getName()) != 0 ||
                    StringUtils.trimToEmpty(wikiversion.getTitle()).compareTo(title) != 0 ||
                    StringUtils.trimToEmpty(wikiversion.getBody()).compareTo(StringUtils.trimToEmpty(form.getBody())) != 0 ||
                    wikiversion.getRendererType().compareTo(currentRendererName) != 0 ||
                    wikiUpdate.getParent() != form.getParentId())
            {
                wikiUpdate.setName(form.getName());
                wikiUpdate.setParent(form.getParentId());
                wikiversion.setTitle(title);
                wikiversion.setBody(form.getBody());
                wikiversion.setRendererType(currentRendererName);
                WikiManager.updateWiki(getViewContext().getUser(), wikiUpdate, wikiversion);
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

    @RequiresPermission(ACL.PERM_READ)
    public class AttachFilesAction extends ApiAction<AttachFilesForm>
    {
        public AttachFilesAction()
        {
            super();

            //because this will typically be called from a hidden iframe
            //we must responsd with a content-type of text/html or the
            //browser will prompt the user to save the response, as the
            //browser won't natively show application/json content-type
            setContentTypeOverride("text/html");
        }

        public ApiResponse execute(AttachFilesForm form, BindException errors) throws Exception
        {
            if(null == form.getEntityId() || form.getEntityId().length() == 0)
                throw new IllegalArgumentException("The entityId parameter is required!");

            //get the wiki using the entity id
            Wiki wiki = WikiManager.getWikiByEntityId(getViewContext().getContainer(), form.getEntityId());
            if(null == wiki)
                throw new IllegalArgumentException("Could not find the wiki with entity id '" + form.getEntityId() + "'!");

            if(!(getViewContext().getRequest() instanceof MultipartHttpServletRequest))
                throw new IllegalArgumentException("You must use the 'multipart/form-data' mimetype when posting to attachFiles.api");

            AttachmentService.Service attsvc = AttachmentService.get();

            //delete the attachments requested
            if(null != form.getToDelete() && form.getToDelete().length > 0)
            {
                for(String name : form.getToDelete())
                {
                    attsvc.deleteAttachment(wiki, name);
                }
            }

            Map<String,Object> warnings = new HashMap<String,Object>();

            Map<String, MultipartFile> fileMap = (Map<String, MultipartFile>)((MultipartHttpServletRequest)getViewContext().getRequest()).getFileMap();
            List<AttachmentFile> files = SpringAttachmentFile.createList(fileMap);

            //add any files as attachments
            if(null != files && files.size() > 0)
            {
                try
                {
                    attsvc.addAttachments(getUser(), wiki, files);
                }
                catch(AttachmentService.DuplicateFilenameException e)
                {
                    //since this is now being called ajax style with just the files, we don't
                    //really need to generate an error in this case. Just add a warning
                    warnings.put("files", e.getMessage());
                }
            }

            //uncache the wikis in the current container so that
            //changes to the attachments are reflected
            WikiCache.uncache(getViewContext().getContainer());

            //build the response
            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("success", true);
            if(warnings.size() > 0)
                resp.put("warnings", warnings);

            Wiki wikiUpdated = WikiManager.getWiki(getViewContext().getContainer(), wiki.getName());
            assert(null != wikiUpdated);

            List<Object> attachments = new ArrayList<Object>();
            if(null != wikiUpdated.getAttachments())
            {
                for(Attachment att : wikiUpdated.getAttachments())
                {
                    Map<String,Object> attProps = new HashMap<String,Object>();
                    attProps.put("name", att.getName());
                    attProps.put("iconUrl", getViewContext().getContextPath() + att.getFileIcon());
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

        public void setBody(String body)
        {
            _body = body;
        }

        public String getFromFormat()
        {
            return _fromFormat;
        }

        public void setFromFormat(String fromFormat)
        {
            _fromFormat = fromFormat;
        }

        public String getToFormat()
        {
            return _toFormat;
        }

        public void setToFormat(String toFormat)
        {
            _toFormat = toFormat;
        }
    }

    @RequiresPermission(ACL.PERM_NONE)
    public class TransformWikiAction extends ApiAction<TransformWikiForm>
    {
        public ApiResponse execute(TransformWikiForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            String newBody = form.getBody();
            Container container = getViewContext().getContainer();

            //currently, we can only transform from wiki to HTML
            if(WikiRendererType.RADEOX.name().equals(form.getFromFormat())
                    && WikiRendererType.HTML.name().equals(form.getToFormat()))
            {
                Wiki wiki = new Wiki(container, "_transform_temp");
                WikiVersion wikiver = new WikiVersion("_transform_temp");

                if(null != form.getBody())
                    wikiver.setBody(form.getBody());

                wikiver.setRendererType(form.getFromFormat());
                newBody = wikiver.getHtml(getViewContext().getContainer(), wiki);
            }

            response.put("toFormat", form.getToFormat());
            response.put("fromFormat", form.getFromFormat());
            response.put("body", newBody);

            return response;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class GetWikiTocAction extends ApiAction
    {
        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            Container container = getViewContext().getContainer();
            List<Wiki> pages = WikiManager.getWikisByParentId(container.getId(), -1);
            response.put("pages", getChildrenProps(pages));

            //include info about the current container
            Map<String,Object> containerProps = new HashMap<String,Object>();
            containerProps.put("name", container.getName());
            containerProps.put("id", container.getId());
            containerProps.put("path", container.getPath());
            response.put("container", containerProps);

            //include the user's TOC displayed preference
            Map<String,String> properties = PropertyManager.getProperties(
                    getViewContext().getUser().getUserId(), getViewContext().getContainer().getId(),
                    SetEditorPreferenceAction.CAT_EDITOR_PREFERENCE, true);

            response.put("displayToc", "true".equals(properties.get(SetTocPreferenceAction.PROP_TOC_DISPLAYED)));

            return response;
        }

        public List<Object> getChildrenProps(List<Wiki> pages)
        {
            List<Object> ret = new ArrayList<Object>();
            for(Wiki wiki : pages)
            {
                Map<String,Object> props = new HashMap<String,Object>();
                props.put("name", wiki.getName());
                props.put("pageLink", wiki.getPageLink());
                props.put("entityId", wiki.getEntityId());
                props.put("rowId", wiki.getRowId());
                props.put("depth", wiki.getDepth());
                props.put("href", wiki.getPageLink());

                WikiVersion version = wiki.latestVersion();
                if(null != version)
                    props.put("title", wiki.latestVersion().getTitle());

                List<Wiki> children = wiki.getChildren();
                if(null != children && children.size() > 0)
                    props.put("children", getChildrenProps(children));

                ret.add(props);
            }
            return ret;
        }
    }

    public static class NewPageForm
    {
        private String _redirect;
        private String _name;
        private String _pageId;
        private int _index;

        public String getRedirect()
        {
            return _redirect;
        }

        public void setRedirect(String redirect)
        {
            _redirect = redirect;
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public String getPageId()
        {
            return _pageId;
        }

        public void setPageId(String pageId)
        {
            _pageId = pageId;
        }

        public int getIndex()
        {
            return _index;
        }

        public void setIndex(int index)
        {
            _index = index;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class NewPageAction extends SimpleViewAction<NewPageForm>
    {
        public ModelAndView getView(NewPageForm form, BindException errors) throws Exception
        {
            return new JspView<NewPageForm>("/org/labkey/wiki/view/wikiChooseFormat.jsp", form);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("New Page");
        }
    }

    public static class SetEditorPreferenceForm
    {
        public boolean _useVisual;

        public boolean isUseVisual()
        {
            return _useVisual;
        }

        public void setUseVisual(boolean useVisual)
        {
            _useVisual = useVisual;
        }
    }

    @RequiresPermission(ACL.PERM_NONE)
    public class SetEditorPreferenceAction extends ApiAction<SetEditorPreferenceForm>
    {
        public static final String CAT_EDITOR_PREFERENCE = "editorPreference";
        public static final String PROP_USE_VISUAL_EDITOR = "useVisualEditor";

        public ApiResponse execute(SetEditorPreferenceForm form, BindException errors) throws Exception
        {
            //save user's editor preference
            PropertyManager.PropertyMap properties = PropertyManager.getWritableProperties(
                    getViewContext().getUser().getUserId(), getViewContext().getContainer().getId(),
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

        public void setDisplayed(boolean displayed)
        {
            _displayed = displayed;
        }
    }

    @RequiresPermission(ACL.PERM_NONE)
    public class SetTocPreferenceAction extends ApiAction<SetTocPreferenceForm>
    {
        public static final String PROP_TOC_DISPLAYED = "displayToc";

        public ApiResponse execute(SetTocPreferenceForm form, BindException errors) throws Exception
        {
            //use the same category as editor preference to save on storage
            PropertyManager.PropertyMap properties = PropertyManager.getWritableProperties(
                    getViewContext().getUser().getUserId(), getViewContext().getContainer().getId(),
                    SetEditorPreferenceAction.CAT_EDITOR_PREFERENCE, true);
            properties.put(PROP_TOC_DISPLAYED, String.valueOf(form.isDisplayed()));
            PropertyManager.saveProperties(properties);

            return new ApiSimpleResponse("success", true);
        }
    }
}

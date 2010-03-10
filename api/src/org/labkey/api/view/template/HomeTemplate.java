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
package org.labkey.api.view.template;

import org.apache.commons.collections15.ArrayStack;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.util.Pair;
import org.labkey.api.view.*;
import org.labkey.api.view.menu.MenuService;
import org.labkey.api.view.menu.MenuView;
import org.labkey.api.wiki.WikiService;
import org.labkey.api.services.ServiceRegistry;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


public class HomeTemplate extends PrintTemplate
{
    public HomeTemplate(ViewContext context, Container c, ModelAndView body)
    {
        this(context, c, body, new PageConfig(context.getActionURL().getPageFlow()), new NavTree[0]);
    }

    public HomeTemplate(ViewContext context, ModelAndView body, PageConfig page, NavTree[] navTrail)
    {
        this("/org/labkey/api/view/template/CommonTemplate.jsp", context, context.getContainer(), body, page, navTrail);
    }

    public HomeTemplate(ViewContext context, Container c, ModelAndView body, PageConfig page, NavTree[] navTrail)
    {
        this("/org/labkey/api/view/template/CommonTemplate.jsp", context, c, body, page, navTrail);
    }


    protected HomeTemplate(String template, ViewContext context, Container c, ModelAndView body, PageConfig page, NavTree[] navTrail)
    {
        super(template, page);
        init(context, c, body, page, navTrail);
    }


    protected void init(ViewContext context, Container c, ModelAndView body, PageConfig page, NavTree[] navTrail)
    {
        //show the header on the home template
        getModelBean().setShowHeader(true);

        setFrame(FrameType.NONE);
        page.setNavTrail(Arrays.asList(navTrail));

        WikiService wikiService = ServiceRegistry.get().getService(WikiService.class);
        WebPartView wikiMenu = null;
        if (null != c && null != c.getProject() && ModuleLoader.getInstance().isStartupComplete() && null != wikiService)
            wikiMenu = wikiService.getView(c.getProject(), "_navTree", false, true);

        if (null != wikiMenu)
            setView("menu", wikiMenu);
        else
        {
            MenuView navElements = MenuService.get().getMenuView(context, page);
            setView("menu", navElements);
        }


        WebPartView header = null;
        if (ModuleLoader.getInstance().isStartupComplete() && null != wikiService && null != c && null != c.getProject())
            header = wikiService.getView(c.getProject(), "_header", false, true);

        if (null != header)
            setView("header", header);
        else
            setView("header", getHeaderView(getModelBean()));

        setView("topmenu", new MenuBarView(context.getContainer()));

        LookAndFeelProperties laf = LookAndFeelProperties.getInstance(context.getContainer());
        if (laf.isAppBarUIEnabled())
            setView("appbar", getAppBarView(context, page, page.getTitle()));
        setBody(body);
    }


    private List<String> buildContainerNavLinks(ViewContext context)
    {
        List<String> links = new ArrayList<String>();
        Container container = context.getContainer();

        if (container != null)
        {
            ArrayStack<NavTree> stack = new ArrayStack<NavTree>();
            while (!container.isRoot())
            {
                // don't add the home project folder, since 'Home' is always displayed:
                boolean isHomeProject = container.getId().equals(ContainerManager.getForPath("home").getId());
                if (!isHomeProject)
                    stack.push(new NavTree(container.getName(), stack.size() > 0 ? container.getStartURL(context) : null));
                container = container.getParent();
            }
            while (stack.size() > 0)
                links.add(formatLink(stack.pop()));
        }
        return links;
    }

    //Keys for stashing away navtrails to use for nested modules
    private static final String PARENT_TRAIL_INFO = HomeTemplate.class.getName() + ".PARENT_TRAIL_INFO";
    private static class ParentTrailInfo
    {
        ActionURL url;
        List<NavTree> links;
        
        public ParentTrailInfo(ActionURL url, List<NavTree> links)
        {
            this.url = url;
            this.links = links;
        }
    }

    protected HttpView getAppBarView(ViewContext context, PageConfig page, String pageTitle)
    {
        AppBar appBar = page.getAppBar();

        if (null == appBar)
        {
            appBar = context.getContainer().getFolderType().getAppBar(context);
            page.setAppBar(appBar);
        }

        //HACK to fix up navTrail to delete navBar items
        if (null != appBar)
            page.setNavTrail(appBar.fixCrumbTrail(page.getNavTrail()));

        if (null == appBar)
            return null;
        
        return new AppBarView(appBar);
    }

//    protected HttpView getNavTrailView(ViewContext context, PageConfig page, String pageTitle)
//    {
//        return new NavTrailView(context, pageTitle, page, page.getNavTrail());
//    }

    protected HttpView getNavTrailView(ViewContext context, PageConfig page, String pageTitle)
    {
        Container container = context.getContainer();
        FolderType folderType = container.getFolderType();

        List<NavTree> navTrail = page.getNavTrail();
        List<NavTree> extraChildren = new ArrayList<NavTree>();
        ActionURL url = context.getActionURL();
        String pageFlow = url.getPageFlow();
        Module curModule = page.getModuleOwner();
        if (curModule == null)
            curModule = ModuleLoader.getInstance().getModuleForPageFlow(pageFlow);
        NavTree[] trailExtras = null == navTrail ? new NavTree[0] : navTrail.toArray(new NavTree[navTrail.size()]);

        boolean singleTabFolder = FolderType.NONE.equals(folderType) && context.getContainer().getActiveModules().size() == 1;
        //If this is an old tabbed folder just show tabs, unless there's a single "tab" which we hide
        if (FolderType.NONE.equals(folderType) && (!singleTabFolder || curModule.equals(container.getDefaultModule())))
        {
            extraChildren.addAll(Arrays.asList(trailExtras));
        }
        else   //Glue together a navtrail since we're not in default module and are not showing tabs
        {
            ActionURL ownerStartUrl;
            String startPageLabel;
            if (singleTabFolder)
            {
                startPageLabel = container.equals(ContainerManager.getHomeContainer()) ? LookAndFeelProperties.getInstance(container).getShortName() : container.getName();
                ownerStartUrl = container.getDefaultModule().getTabURL(container, context.getUser());
            }
            else
            {
                startPageLabel =  folderType.getStartPageLabel(context);
                ownerStartUrl = folderType.getStartURL(context.getContainer(), context.getUser());
            }
            boolean atStart = equalBaseUrls(url, ownerStartUrl);

            if (!atStart)
                extraChildren.add(new NavTree(startPageLabel, ownerStartUrl));

            //No extra children at the top...
            if (!atStart)
            {
                //If we are in the default module, trust any passed in trails (except use folder's dashboard link from above)
                // assume length == 1 is title only, length > 1 means root,...,title
                if (curModule.equals(folderType.getDefaultModule()))
                {
                    if (trailExtras.length == 1)
                    {
                        extraChildren.addAll(Arrays.asList(trailExtras));
                    }
                    else if (trailExtras.length > 1)
                    {
                        extraChildren.addAll(Arrays.asList(trailExtras).subList(1, trailExtras.length));
                    }

                    //Stash away the current URL & trailExtras so that if we use nested module we can
                    //know what parent trail should be. Use the page title as the last link with special
                    //handling if non-link is in the navTrail (should get rid of these)
                    //But don't ever store post urls cause they won't work...
                    if (!"POST".equalsIgnoreCase(getViewContext().getRequest().getMethod()))
                    {
                        List<NavTree> saveChildren = new ArrayList<NavTree>(extraChildren);
                        NavTree lastChild = extraChildren.get(extraChildren.size() - 1);
                        if (null == lastChild.second)
                            saveChildren.set(saveChildren.size() - 1, new NavTree(lastChild.getKey(), url));
                        context.getRequest().getSession().setAttribute(PARENT_TRAIL_INFO, new ParentTrailInfo(url, saveChildren));
                    }
                }
                else //In a "service's" module. Add its links below the dashboard.
                {
                    //If we have stashed away the parent's trail info AND it looks like it is right, use it
                    ParentTrailInfo pti = (ParentTrailInfo) context.getRequest().getSession().getAttribute(PARENT_TRAIL_INFO);
                    if (null != pti && pti.url.getExtraPath().equals(url.getExtraPath()))
                        extraChildren = new ArrayList<NavTree>(pti.links);

                    //If it specified a trail, use it. Otherwise do the default thing.
                    // assume length == 1 is title only, length > 1 means root,...,title
                    if (trailExtras.length <= 1)
                    {
                        ActionURL startUrl = curModule.getTabURL(context.getContainer(), context.getUser());
                        if (null != startUrl && !equalBaseUrls(startUrl, url))
                            extraChildren.add(new NavTree(curModule.getTabName(context), startUrl.getLocalURIString()));
                    }
                    extraChildren.addAll(Arrays.asList(trailExtras));
                }
            }
            else
            {
                context.getRequest().getSession().removeAttribute(PARENT_TRAIL_INFO);
            }

/*            if (trailExtras.length == 0)
            {
                int dashPos = pageFlow.indexOf('-');
                if (dashPos > 0)
                {
                    String childFlow = pageFlow.substring(dashPos + 1);
                    if (!"begin".equals(url.getAction()))
                        extraChildren.add(new NavTree(childFlow, url.relativeUrl("begin.view", null, url.getPageFlow(), true)));
                }
            } */
        } //  folderType != FolderType.NONE

        return new NavTrailView(context, pageTitle, page, extraChildren);
    }


    private boolean equalBaseUrls(ActionURL url1, ActionURL url2)
    {
        if (url1 == url2)
            return true;
        if(null == url1 || null == url2)
            return false;
        return url1.getExtraPath().equalsIgnoreCase(url2.getExtraPath()) && url1.getAction().equalsIgnoreCase(url2.getAction()) && url1.getPageFlow().equalsIgnoreCase(url2.getPageFlow());
    }


    private String formatLink(String display, String href)
    {
        if (null == display)
            display = href;
        if (href == null && display == null)
            return null;
        if (href == null)
            return display;
        else
        {
            StringBuilder sb = new StringBuilder();
            sb.append("<a href=\"").append(href).append("\">").append(display).append("</a>");
            return sb.toString();
        }
    }


    private String formatLink(Pair tree)
    {
        if (null == tree)
            return null;
        String display = null == tree.getKey() ? null : PageFlowUtil.filter(String.valueOf(tree.getKey()));
        String href = null == tree.getValue() ? null : PageFlowUtil.filter(String.valueOf(tree.getValue()));
        return formatLink(display, href);
    }


    protected HttpView getHeaderView(PageConfig page)
    {
        List<String> navLinks = buildContainerNavLinks(getRootContext());
        String upgradeMessage = UsageReportingLevel.getUpgradeMessage();
        Map<String, Throwable> moduleFailures = ModuleLoader.getInstance().getModuleFailures();
        return new TemplateHeaderView(navLinks, upgradeMessage, moduleFailures, page);
    }


    @Override
    public void prepareWebPart(PageConfig page)
    {
        String title = page.getTitle();
        if (null ==  title || 0 == title.length())
        {
            title = getDefaultTitle(getRootContext().getActionURL());
        }

        if (page.shouldAppendPathToTitle())
        {
            String extraPath = getRootContext().getActionURL().getExtraPath();
            if (extraPath.length() > 0)
                page.setTitle(page.getTitle() + ": " + getRootContext().getActionURL().getExtraPath());
        }

        if (null == getView("nav") && null == getView("appbar"))
            setView("nav", getNavTrailView(getRootContext(), page, title));

        if (null == getView("header"))
            setView("header", getHeaderView(page));
    }
}

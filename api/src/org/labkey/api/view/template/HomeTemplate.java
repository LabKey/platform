/*
 * Copyright (c) 2004-2013 Fred Hutchinson Cancer Research Center
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
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.MultiPortalFolderType;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.wiki.WikiService;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


public class HomeTemplate extends PrintTemplate
{
    public HomeTemplate(ViewContext context, Container c, ModelAndView body)
    {
        this(context, c, body, new PageConfig(context.getActionURL().getController()), new NavTree[0]);
    }


    public HomeTemplate(ViewContext context, Container c, ModelAndView body, PageConfig page, NavTree[] navTrail)
    {
        super("/org/labkey/api/view/template/CommonTemplate.jsp", page);

        //show the header on the home template
        page.setShowHeader(true);
        page.setNavTrail(Arrays.asList(navTrail));

        WikiService wikiService = ServiceRegistry.get().getService(WikiService.class);

        WebPartView header = null;
        if (ModuleLoader.getInstance().isStartupComplete() && null != wikiService && null != c && null != c.getProject())
        {
            header = wikiService.getView(c.getProject(), "_header", false);
            if (null != header)
                header.setFrame(FrameType.NONE); // 12336: Explicitly don't frame the _header override.
        }

        if (null != header)
            setView("header", header);
        else
            setView("header", getHeaderView(page));

        setView("topmenu", new MenuBarView(context, page));

        setBody(body);
        setView("appbar", getAppBarView(context, page));
    }


    private List<String> buildContainerNavLinks(ViewContext context)
    {
        List<String> links = new ArrayList<>();
        Container container = context.getContainer();

        if (container != null)
        {
            ArrayStack<NavTree> stack = new ArrayStack<>();
            while (!container.isRoot())
            {
                // don't add the home project folder, since 'Home' is always displayed:
                boolean isHomeProject = container.getId().equals(ContainerManager.getForPath("home").getId());
                if (!isHomeProject)
                    stack.push(new NavTree(container.getName(), stack.size() > 0 ? container.getStartURL(context.getUser()) : null));
                container = container.getParent();
            }
            while (stack.size() > 0)
                links.add(formatLink(stack.pop()));
        }
        return links;
    }

    protected HttpView getAppBarView(ViewContext context, PageConfig page)
    {
        AppBar appBar;
        if (context.getContainer().isWorkbookOrTab())
        {
            ViewContext parentContext = new ViewContext(context);
            parentContext.setContainer(context.getContainer().getParent());
            FolderType folderType =  parentContext.getContainer().getFolderType();
            if (folderType instanceof MultiPortalFolderType)
                appBar = ((MultiPortalFolderType)folderType).getAppBar(parentContext, page, context.getContainer());
            else
                appBar = folderType.getAppBar(parentContext, page);
        }
        else
        {
            appBar = context.getContainer().getFolderType().getAppBar(context, page);
        }

        //HACK to fix up navTrail to delete navBar items
        List<NavTree> navTrail = page.getNavTrail();
        if (context.getContainer().isWorkbook())
        {
            // Add the main page for the workbook to the nav trail
            navTrail = new ArrayList<>(navTrail);
            navTrail.add(0, new NavTree(context.getContainer().getTitle(), context.getContainer().getStartURL(context.getUser())));
        }
        page.setNavTrail(appBar.setNavTrail(navTrail, context));

        //allow views to have flag to hide title
        if(getBody() instanceof WebPartView && ((WebPartView) getBody()).isHidePageTitle()){
            appBar.setPageTitle(null);
        }
        return new AppBarView(appBar);
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


    private String formatLink(NavTree tree)
    {
        if (null == tree)
            return null;
        String display = null == tree.getText() ? null : PageFlowUtil.filter(String.valueOf(tree.getText()));
        String href = null == tree.getHref() ? null : PageFlowUtil.filter(String.valueOf(tree.getHref()));
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
        if (page.shouldAppendPathToTitle())
        {
            String extraPath = getRootContext().getActionURL().getExtraPath();
            if (extraPath.length() > 0)
                page.setTitle(page.getTitle() + (page.getTitle() != null && !page.getTitle().isEmpty() ? ": " : "") + extraPath);
        }

        if (null == getView("header"))
            setView("header", getHeaderView(page));
    }
}

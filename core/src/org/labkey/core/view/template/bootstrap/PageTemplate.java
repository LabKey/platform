/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.core.view.template.bootstrap;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.MultiPortalFolderType;
import org.labkey.api.security.User;
import org.labkey.api.settings.BannerProperties;
import org.labkey.api.settings.FooterProperties;
import org.labkey.api.settings.TemplateProperties;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.AppBar;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.wiki.WikiService;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public class PageTemplate extends JspView<PageConfig>
{
    private boolean isAppTemplate = false;

    protected PageTemplate(String template, PageConfig page)
    {
        super(template, page);
        page.setShowHeader(false);
        setFrame(FrameType.NONE);
    }

    public PageTemplate(ViewContext context, ModelAndView body, PageConfig page)
    {
        this("/org/labkey/core/view/template/bootstrap/pageTemplate.jsp", context, context.getContainer(), body, page);
    }

    protected PageTemplate(String template, ViewContext context, Container c, ModelAndView body, PageConfig page)
    {
        this(template, page);

        setBody(body);
        setView("bodyTemplate", getBodyTemplate(page, body));

        if (null == page.getNavTrail())
            page.setNavTrail(Collections.emptyList());

        setUserMetaTag(context, page);
        //show the header on the home template
        page.setShowHeader(true);

        WikiService wikiService = WikiService.get();

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
            setView("header", new Header(page));

        // TODO: This is being side-effected by isHidePageTitle() check. That setting should be moved to PageConfig
        setBody(body);

        page.setAppBar(generateAppBarModel(context, page));

        setView("navigation", getNavigationView(context, page));
        setView("footer", new FooterProperties(c).getView());
    }

    private AppBar generateAppBarModel(ViewContext context, PageConfig page)
    {
        AppBar appBar;
        if (!context.getContainer().isInFolderNav())
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
        if (!context.getContainer().isInFolderNav())
        {
            // Add the main page for the workbook to the nav trail
            navTrail = new ArrayList<>(navTrail);
            navTrail.add(0, new NavTree(context.getContainer().getTitle(), context.getContainer().getStartURL(context.getUser())));
        }

        List<NavTree> appNavTrail = appBar.setNavTrail(navTrail, context);
        if (page.getTemplate() != PageConfig.Template.Wizard)
            page.setNavTrail(appNavTrail);

        //allow views to have flag to hide title
        if (getBody() instanceof WebPartView && ((WebPartView) getBody()).isHidePageTitle())
            appBar.setPageTitle(null);

        return appBar;
    }

    protected void setUserMetaTag(ViewContext context, PageConfig page)
    {
        // for testing add meta tags
        User user = context.getUser();
        User authenticatedUser = user;
        User impersonatedUser = null;
        if (authenticatedUser.isImpersonated())
        {
            impersonatedUser = user;
            authenticatedUser = user.getImpersonatingUser();
        }
        page.setMetaTag("authenticatedUser", null == authenticatedUser ? "-" : StringUtils.defaultString(authenticatedUser.getEmail(),user.getDisplayName(user)));
        page.setMetaTag("impersonatedUser", null == impersonatedUser ? "-" : StringUtils.defaultString(impersonatedUser.getEmail(),user.getDisplayName(user)));
    }

    protected ModelAndView getBodyTemplate(PageConfig page, ModelAndView body)
    {
        JspView view = new JspView<>("/org/labkey/core/view/template/bootstrap/body.jsp", page);

        Container c = this.getViewContext().getContainer();
        TemplateProperties banner = new BannerProperties(c);
        if (!banner.isShowOnlyInProjectRoot() || (c.equals(c.getProject())))
            view.setView("banner", banner.getView());

        view.setBody(body);
        view.setFrame(FrameType.NONE);
        return view;
    }

    protected HttpView getNavigationView(ViewContext context, PageConfig page)
    {
        NavigationModel model = new NavigationModel(context, page);
        addClientDependencies(model.getClientDependencies());

        JspView view = new JspView<>("/org/labkey/core/view/template/bootstrap/navigation.jsp", model);
        view.setFrame(FrameType.NONE);
        return view;
    }

    @Override
    public void setView(String name, ModelAndView view)
    {
        // TODO: This doesn't feel right, however, there are certain views that should only be applied to the "bodyTemplate"
        if (WebPartFactory.LOCATION_RIGHT.equalsIgnoreCase(name))
        {
            HttpView body = ((HttpView) getView("bodyTemplate"));

            if (body != null)
            {
                HttpView right = (HttpView) body.getView(WebPartFactory.LOCATION_RIGHT);

                if (right == null)
                    body.setView(WebPartFactory.LOCATION_RIGHT, new VBox(view));
                else if (right instanceof VBox)
                    ((VBox) right).addView(view);
            }
        }
        else
        {
            super.setView(name, view);
        }
    }

    public static class NavigationModel
    {
        private final PageConfig _page;
        private final LinkedHashSet<ClientDependency> _clientDependencies = new LinkedHashSet<>();
        private final ViewContext _context;
        private final List<Portal.WebPart> _menus;

        private static final Logger LOG = LogManager.getLogger(NavigationModel.class);

        private NavigationModel(ViewContext context, PageConfig page)
        {
            _context = context;
            _page = page;
            _menus = initMenus();
        }

        private void addClientDependencies(LinkedHashSet<ClientDependency> dependencies)
        {
            _clientDependencies.addAll(dependencies);
        }

        public LinkedHashSet<ClientDependency> getClientDependencies()
        {
            return _clientDependencies;
        }

        public List<Portal.WebPart> getCustomMenus()
        {
            return _menus;
        }

        @NotNull
        public String getProjectTitle()
        {
            Container c = _context.getContainer();
            Container p = c.getProject();
            String projectTitle = "";
            if (null != p)
            {
                projectTitle = p.getTitle();
                if (projectTitle.equalsIgnoreCase("home"))
                    projectTitle = "Home";
            }

            return projectTitle;
        }

        @NotNull
        public List<NavTree> getTabs()
        {
            if (null == _page.getAppBar())
                return Collections.emptyList();

            return _page.getAppBar().getButtons();
        }

        @NotNull
        private List<Portal.WebPart> initMenus()
        {
            Container c = _context.getContainer();
            Container project = c.getProject();

            if (null != project)
            {
                Collection<Portal.WebPart> allParts = Portal.getParts(project, _context);
                MultiValuedMap<String, Portal.WebPart> locationMap = Portal.getPartsByLocation(allParts);
                List<Portal.WebPart> menuParts = (List<Portal.WebPart>) locationMap.get("menubar");

                if (null == menuParts)
                    menuParts = Collections.emptyList();

                for (Portal.WebPart part : menuParts)
                {
                    try
                    {
                        WebPartFactory factory = Portal.getPortalPart(part.getName());
                        if (null != factory)
                        {
                            WebPartView view = factory.getWebPartView(_context, part);
                            if (!view.isEmpty())
                            {
                                addClientDependencies(view.getClientDependencies());
                            }
                        }
                    }
                    catch (Exception x)
                    {
                        LOG.error("Failed to add client dependencies", x);
                    }
                }

                return menuParts;
            }

            return Collections.emptyList();
        }
    }

    public boolean isAppTemplate()
    {
        return isAppTemplate;
    }

    protected void setAppTemplate(boolean appTemplate)
    {
        isAppTemplate = appTemplate;
    }

    public ActionURL getPermaLink()
    {
        ActionURL url = getViewContext().cloneActionURL();
        return url.setExtraPath("__r" + getViewContext().getContainer().getRowId());
    }

    public static String getTemplatePrefix(PageConfig page)
    {
        PageConfig.Template t = page.getTemplate();
        final String templateCls;

        switch (t)
        {
            case Wizard:
                templateCls = "wizard";
                break;
            case Dialog:
                templateCls = "dialog";
                break;
            case Print:
                templateCls = "print";
                break;
            default:
                templateCls = "default";
        }

        return templateCls;
    }

    @Override
    protected void prepareWebPart(PageConfig page)
    {
        if (page.shouldAppendPathToTitle())
        {
            String extraPath = getRootContext().getActionURL().getExtraPath();
            if (extraPath.length() > 0)
                page.setTitle(page.getTitle() + (page.getTitle() != null && !page.getTitle().isEmpty() ? ": " : "") + extraPath);
        }
    }
}

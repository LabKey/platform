/*
 * Copyright (c) 2017 LabKey Corporation
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
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.compliance.ComplianceService;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.evaluation.EvaluationService;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.MultiPortalFolderType;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.FooterProperties;
import org.labkey.api.settings.TemplateProperties;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DismissibleWarningMessage;
import org.labkey.api.view.HtmlView;
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

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

public class PageTemplate extends JspView<PageConfig>
{
    private static final Collection<String> ADMIN_WARNINGS = getNonVolatileAdminWarnings();

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
        buildWarnings(page);

        setBody(body);
        setView("bodyTemplate", getBodyTemplate(page, body));

        if (null == page.getNavTrail())
            page.setNavTrail(Collections.emptyList());

        setUserMetaTag(context, page);
        //show the header on the home template
        page.setShowHeader(true);

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
            setView("header", new Header(page));

        // TODO: This is being side-effected by isHidePageTitle() check. That setting should be moved to PageConfig
        setBody(body);

        page.setAppBar(generateAppBarModel(context, page));

        setView("navigation", getNavigationView(context, page));
        setView("footer", getTemplateResource(new FooterProperties()));
    }

    private AppBar generateAppBarModel(ViewContext context, PageConfig page)
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

    // Check warning conditions that will never change while the server is running. No need to test these on every request.
    private static Collection<String> getNonVolatileAdminWarnings()
    {
        List<String> warnings = new LinkedList<>();

        //FIX: 9683
        //show admins warning about inadequate heap size (<= 256Mb)
        MemoryMXBean membean = ManagementFactory.getMemoryMXBean();
        long maxMem = membean.getHeapMemoryUsage().getMax();

        if (maxMem > 0 && maxMem <= 256*1024*1024)
        {
            warnings.add("The maximum amount of heap memory allocated to LabKey Server is too low (256M or less). " +
                    "LabKey recommends " +
                    new HelpTopic("configWebappMemory").getSimpleLinkHtml("setting the maximum heap to at least one gigabyte (-Xmx1024M)")
                    + ".");
        }

        // Warn if running on a deprecated database version or some other non-fatal database configuration issue
        DbScope.getLabKeyScope().getSqlDialect().addAdminWarningMessages(warnings);

        if (!ModuleLoader.getInstance().getTomcatVersion().isSupported())
        {
            String serverInfo = ModuleLoader.getServletContext().getServerInfo();
            HelpTopic topic = new HelpTopic("supported");
            warnings.add("The deployed version of Tomcat, " + serverInfo + ", is not supported." +
                    " See the " + topic.getSimpleLinkHtml("Supported Technologies page") + " for more information.");
        }

        return warnings;
    }

    private void buildWarnings(PageConfig page)
    {
        User user = getViewContext().getUser();
        Container container = getViewContext().getContainer();

        if (null != user && user.isInSiteAdminGroup())
        {
            //admin-only mode--show to admins
            if (AppProps.getInstance().isUserRequestedAdminOnlyMode())
            {
                page.addWarningMessage("This site is configured so that only administrators may sign in. To allow other users to sign in, turn off admin-only mode via the <a href=\""
                        + PageFlowUtil.urlProvider(AdminUrls.class).getCustomizeSiteURL()
                        + "\">"
                        + "site settings page</a>.");
            }

            //module failures during startup--show to admins
            Map<String, Throwable> moduleFailures = ModuleLoader.getInstance().getModuleFailures();
            if (null != moduleFailures && moduleFailures.size() > 0)
            {
                page.addWarningMessage("The following modules experienced errors during startup: "
                        + "<a href=\"" + PageFlowUtil.urlProvider(AdminUrls.class).getModuleErrorsURL(container) + "\">"
                        + PageFlowUtil.filter(moduleFailures.keySet())
                        + "</a>");
            }

            //upgrade message--show to admins
            String upgradeMessage = UsageReportingLevel.getUpgradeMessage();
            if (StringUtils.isNotEmpty(upgradeMessage))
            {
                page.addWarningMessage(upgradeMessage);
            }

            page.addWarningMessages(ADMIN_WARNINGS);
        }

        if (AppProps.getInstance().isDevMode())
        {
            int leakCount = ConnectionWrapper.getProbableLeakCount();
            if (leakCount > 0)
            {
                int count = ConnectionWrapper.getActiveConnectionCount();
                String connectionsInUse = "<a href=\"" + PageFlowUtil.urlProvider(AdminUrls.class).getMemTrackerURL() + "\">" + count + " DB connection" + (count == 1 ? "" : "s") + " in use.";
                connectionsInUse += " " + leakCount + " probable leak" + (leakCount == 1 ? "" : "s") + ".</a>";
                page.addWarningMessage(connectionsInUse);
            }
        }

        if (AppProps.getInstance().isShowRibbonMessage() && !StringUtils.isEmpty(AppProps.getInstance().getRibbonMessageHtml()))
        {
            String message = AppProps.getInstance().getRibbonMessageHtml();
            message = ModuleHtmlView.replaceTokens(message, getViewContext());
            page.addWarningMessage(message);
        }

        if (!page.isSkipPHIBanner())
        {
            String complianceWarning = ComplianceService.get().getPHIBanner(getViewContext());
            if (!StringUtils.isEmpty(complianceWarning))
                page.addDismissibleWarningMessage(complianceWarning);
        }

        DismissibleWarningMessage expirationMessage = EvaluationService.get().getExpirationMessage();
        if (expirationMessage != null && expirationMessage.showMessage(getViewContext()))
        {
            String expirationText = expirationMessage.getMessageHtml(getViewContext());
            if (!StringUtils.isEmpty(expirationText))
                page.addDismissibleWarningMessage(expirationText);
        }
    }

    protected ModelAndView getBodyTemplate(PageConfig page, ModelAndView body)
    {
        JspView view = new JspView<>("/org/labkey/core/view/template/bootstrap/body.jsp", page);
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

        private static final Logger LOG = Logger.getLogger(NavigationModel.class);

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
        return url.setExtraPath("__r" + Integer.toString(getViewContext().getContainer().getRowId()));
    }

    public static HtmlView getTemplateResource(TemplateProperties prop)
    {
        HtmlView view = null;
        if (prop.isDisplay())
        {
            Module coreModule = ModuleLoader.getInstance().getCoreModule();
            List<Module> modules = new ArrayList<>(ModuleLoader.getInstance().getModules());
            if (null != ModuleLoader.getInstance().getModule(prop.getModule()))
            {
                modules.add(ModuleLoader.getInstance().getModule(prop.getModule()));
            }
            ListIterator<Module> i = modules.listIterator(modules.size());
            while (i.hasPrevious())
            {
                view = ModuleHtmlView.get(i.previous(), prop.getFileName());
                if (null != view)
                    break;
            }
            if (null == view)
            {
                view = ModuleHtmlView.get(coreModule, prop.getFileName());
            }
            if (null != view)
            {
                view.setFrame(FrameType.NONE);
            }
        }
        return view;
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

    // For now, gives a central place to render messaging
    public static String renderSiteMessages(PageConfig page)
    {
        StringBuilder messages = new StringBuilder();
        int size = page.getWarningMessages().size();
        if (size > 0)
        {
            messages.append("<div class=\"alert alert-warning\" role=\"alert\">");
            appendMessageContent(page.getWarningMessages(), messages);
            messages.append("</div>");
        }

        int dismissibleSize = page.getDismissibleWarningMessages().size();
        if (dismissibleSize > 0)
        {
            messages.append("<div class=\"alert alert-warning alert-dismissable lk-dismissable-warn\">");
            messages.append("<a href=\"#\" class=\"close lk-dismissable-warn-close\" data-dismiss=\"alert\" aria-label=\"dismiss\" title=\"dismiss\">×</a>");

           appendMessageContent(page.getDismissibleWarningMessages(), messages);
           messages.append("</div>");
        }

        // Display a <noscript> warning message
        messages.append("<noscript>");
        messages.append("<div class=\"alert alert-warning\" role=\"alert\">JavaScript is disabled. For the full experience enable JavaScript in your browser.</div>");
        messages.append("</noscript>");

        return messages.toString();
    }

    private static void appendMessageContent(List<String> messages, StringBuilder html)
    {
        if (messages.size() > 0)
        {
            if (messages.size() == 1)
                html.append(messages.get(0));
            else
            {
                html.append("<ul>");
                for (String msg : messages)
                    html.append("<li>").append(msg).append("</li>");
                html.append("</ul>");
            }
        }
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

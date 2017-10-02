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
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.view.template.HomeTemplate;
import org.labkey.api.view.template.PageConfig;
import org.springframework.web.servlet.ModelAndView;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class BootstrapTemplate extends HomeTemplate
{
    private boolean isAppTemplate = false;

    protected BootstrapTemplate(String template, PageConfig page)
    {
        super(template, page);
    }

    public BootstrapTemplate(ViewContext context, ModelAndView body, PageConfig page)
    {
        super("/org/labkey/core/view/template/bootstrap/BootstrapTemplate.jsp", context, context.getContainer(), body, page);
        buildWarnings(context, page);
        setView("bodyTemplate", getBodyTemplate(page));
    }

    private void buildWarnings(ViewContext context, PageConfig page)
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

            //FIX: 9683
            //show admins warning about inadequate heap size (<= 256Mb)
            MemoryMXBean membean = ManagementFactory.getMemoryMXBean();
            long maxMem = membean.getHeapMemoryUsage().getMax();

            if (maxMem > 0 && maxMem <= 268435456)
            {
                page.addWarningMessage("The maximum amount of heap memory allocated to LabKey Server is too low (256M or less). " +
                        "LabKey recommends " +
                        new HelpTopic("configWebappMemory").getSimpleLinkHtml("setting the maximum heap to at least one gigabyte (-Xmx1024M)")
                        + ".");
            }

            // Warn if running on a deprecated database version or some other non-fatal database configuration issue
            List<String> sqlWarnings = new ArrayList<>();
            DbScope.getLabKeyScope().getSqlDialect().addAdminWarningMessages(sqlWarnings);
            if (sqlWarnings.size() > 0)
                page.addWarningMessages(sqlWarnings);
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
    }

    @Override
    protected HttpView getAppBarView(ViewContext context, PageConfig page)
    {
        return null;
    }

    protected HttpView getBodyTemplate(PageConfig page)
    {
        JspView view = new JspView<>("/org/labkey/core/view/template/bootstrap/bootstrap.jsp", page);
        view.setBody(getBody());
        view.setFrame(FrameType.NONE);
        return view;
    }

    @Override
    protected HttpView getHeaderView(PageConfig page)
    {
        return new BootstrapHeader(page);
    }

    @Override
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
        public PageConfig page;

        private LinkedHashSet<ClientDependency> _clientDependencies;
        private ViewContext _context;
        private List<Portal.WebPart> _menus;

        private static final Logger LOG = Logger.getLogger(NavigationModel.class);

        private NavigationModel(ViewContext context, PageConfig page)
        {
            this._context = context;
            this.page = page;

            this._clientDependencies = new LinkedHashSet<>();
            this._menus = initMenus();
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
                if (null != projectTitle && projectTitle.equalsIgnoreCase("home"))
                    projectTitle = "Home";
            }

            return projectTitle;
        }

        @NotNull
        public List<NavTree> getTabs()
        {
            if (null == page.getAppBar())
                return Collections.emptyList();

            return page.getAppBar().getButtons();
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
        String messages = "";
        int size = page.getWarningMessages().size();
        if (size > 0)
        {
            messages += "<div class=\"alert alert-warning\" role=\"alert\">";

            if (size == 1)
                messages += page.getWarningMessages().get(0) + "</div>";
            else
            {
                messages += "<ul>";
                for (String msg : page.getWarningMessages())
                    messages += "<li>" + msg + "</li>";
                messages += "</ul></div>";
            }
        }
        return messages;
    }
}

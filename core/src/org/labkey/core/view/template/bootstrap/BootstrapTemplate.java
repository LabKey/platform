package org.labkey.core.view.template.bootstrap;

import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.AppBar;
import org.labkey.api.view.template.HomeTemplate;
import org.labkey.api.view.template.PageConfig;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

public class BootstrapTemplate extends HomeTemplate
{
    public BootstrapTemplate(ViewContext context, ModelAndView body, PageConfig page)
    {
        super("/org/labkey/core/view/template/bootstrap/BootstrapTemplate.jsp", context, context.getContainer(), body, page);
    }

    @Override
    protected HttpView getAppBarView(ViewContext context, PageConfig page, AppBar model)
    {
        return null;
    }

    @Override
    protected HttpView getHeaderView(PageConfig page)
    {
        String upgradeMessage = UsageReportingLevel.getUpgradeMessage();
        Map<String, Throwable> moduleFailures = ModuleLoader.getInstance().getModuleFailures();
        return new BootstrapHeader(upgradeMessage, moduleFailures, page);
    }

    @Override
    protected HttpView getNavigationView(ViewContext context, PageConfig page, AppBar model)
    {
        return new JspView<>("/org/labkey/core/view/template/bootstrap/navigation.jsp", model);
    }
}

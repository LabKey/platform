package org.labkey.core.view.template.bootstrap.factory;

import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewService;
import org.labkey.api.view.ViewServiceImpl;
import org.labkey.api.view.template.PageConfig;
import org.labkey.core.view.template.bootstrap.BootstrapBodyTemplate;
import org.labkey.core.view.template.bootstrap.BootstrapTemplate;
import org.labkey.core.view.template.bootstrap.DialogTemplate;
import org.labkey.core.view.template.bootstrap.PrintTemplate;
import org.labkey.core.view.template.bootstrap.WizardTemplate;
import org.springframework.web.servlet.ModelAndView;

public class TemplateFactoryBootstrap implements ViewService.TemplateFactory
{
    public void registerTemplates()
    {
        for (PageConfig.Template t : PageConfig.Template.values())
            ViewServiceImpl.getInstance().registerTemplateFactory(t, this);
    }

    @Override
    public HttpView<PageConfig> createTemplate(Enum e, ViewContext context, ModelAndView body, PageConfig page)
    {
        if (!(e instanceof PageConfig.Template))
            throw new IllegalStateException("unexpected value: " + e);
        PageConfig.Template t = (PageConfig.Template)e;

        switch (t)
        {
            case None:
            {
                return null;
            }
            case Framed:
            case Print:
            {
                return new PrintTemplate(context, body, page);
            }
            case Dialog:
            {
                return new DialogTemplate(context, body, page);
            }
            case Wizard:
            {
                return new WizardTemplate(context, body, page);
            }
            case Body:
            {
                return new BootstrapBodyTemplate(context, body, page);
            }
            case App:
            {
                return new BootstrapBodyTemplate(context, body, page, true);
            }
            case Home:
            default:
            {
                return new BootstrapTemplate(context, body, page);
            }
        }
    }
}

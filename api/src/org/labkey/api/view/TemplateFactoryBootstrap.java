package org.labkey.api.view;

import org.labkey.api.view.template.BodyTemplate;
import org.labkey.api.view.template.BootstrapTemplate;
import org.labkey.api.view.template.DialogTemplate;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.view.template.PrintTemplate;
import org.labkey.api.view.template.WizardTemplate;
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
                return new PrintTemplate(body, page);
            }
            case Dialog:
            {
                return new DialogTemplate(body, page);
            }
            case Wizard:
            {
                return new WizardTemplate(body, page);
            }
            case Body:
            {
                return new BodyTemplate(body, page);
            }
            case Home:
            default:
            {
                return new BootstrapTemplate(context, context.getContainer(), body, page);
            }
        }
    }
}

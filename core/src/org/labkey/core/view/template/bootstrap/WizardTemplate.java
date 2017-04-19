package org.labkey.core.view.template.bootstrap;

import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.PageConfig;
import org.springframework.web.servlet.ModelAndView;

public class WizardTemplate extends BootstrapTemplate
{
    public WizardTemplate(ViewContext context, ModelAndView body, PageConfig page)
    {
        super(context, body, page);
    }

    @Override
    protected HttpView getBodyTemplate(PageConfig page)
    {
        HttpView view = new JspView<>("/org/labkey/core/view/template/bootstrap/wizard.jsp", page);
        view.setBody(getBody());
        return view;
    }

    @Override
    protected HttpView getNavigationView(ViewContext context, PageConfig page)
    {
        return null;
    }

    @Override
    public void prepareWebPart(PageConfig page)
    {

    }
}

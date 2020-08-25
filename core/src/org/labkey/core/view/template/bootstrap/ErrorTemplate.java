package org.labkey.core.view.template.bootstrap;

import org.labkey.api.data.Container;
import org.labkey.api.settings.FooterProperties;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.PageConfig;
import org.springframework.web.servlet.ModelAndView;


public class ErrorTemplate extends PageTemplate
{
    public ErrorTemplate(ViewContext context, ModelAndView body, PageConfig page)
    {
        this(context, context.getContainer(), body, page);
    }

    protected ErrorTemplate(ViewContext context, Container c, ModelAndView body, PageConfig page)
    {
        super("/org/labkey/core/view/template/bootstrap/pageTemplate.jsp", page);

        page.setShowHeader(true);

        setView("bodyTemplate", getBodyTemplate(page, body));

        setView("header", new Header(page));
        setBody(body);

        setView("navigation", getNavigationView(context, page));
        setView("footer", new FooterProperties(c).getView());
    }
}

package org.labkey.core.view.template.bootstrap;

import org.labkey.api.data.Container;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.PageConfig;
import org.springframework.web.servlet.ModelAndView;

import java.util.Collections;

public class BootstrapBodyTemplate extends BootstrapTemplate
{
    public BootstrapBodyTemplate(ViewContext context, ModelAndView body, PageConfig page)
    {
        this(context, context.getContainer(), body, page, false);
    }

    public BootstrapBodyTemplate(ViewContext context, ModelAndView body, PageConfig page, boolean isApp)
    {
        this(context, context.getContainer(), body, page, isApp);
    }

    protected BootstrapBodyTemplate(ViewContext context, Container c, ModelAndView body, PageConfig page, boolean isApp)
    {
        super("/org/labkey/core/view/template/bootstrap/BootstrapTemplate.jsp", page);
        this.setAppTemplate(isApp);

        if (null == page.getNavTrail())
            page.setNavTrail(Collections.emptyList());

        setUserMetaTag(context, page);

        // don't show the header on body template
        page.setShowHeader(false);

        setBody(body);
        setView("bodyTemplate", getBodyTemplate(page));
    }

    @Override
    protected HttpView getBodyTemplate(PageConfig page)
    {
        HttpView view = new JspView<>("/org/labkey/core/view/template/bootstrap/bootstrapbody.jsp", page);
        view.setBody(getBody());
        return view;
    }

    @Override
    public void prepareWebPart(PageConfig page)
    {

    }

}

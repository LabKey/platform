package org.labkey.core.view.template.bootstrap;

import org.labkey.api.data.Container;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.PageConfig;
import org.springframework.web.servlet.ModelAndView;

import java.util.Collections;

/**
 * Created by xingyang on 4/21/17.
 */
public class PrintTemplate extends BootstrapTemplate
{
    public PrintTemplate(ViewContext context, ModelAndView body, PageConfig page)
    {
        this(context, context.getContainer(), body, page);
    }

    protected PrintTemplate(ViewContext context, Container c, ModelAndView body, PageConfig page)
    {
        super("/org/labkey/core/view/template/bootstrap/BootstrapTemplate.jsp", page);

        if (null == page.getNavTrail())
            page.setNavTrail(Collections.emptyList());

        setUserMetaTag(context, page);

        page.setShowHeader(false);
        setFrame(FrameType.NONE);

        setBody(body);
        setView("bodyTemplate", getBodyTemplate(page));
    }

    @Override
    public void prepareWebPart(PageConfig page)
    {
        String title = page.getTitle();
        if (null ==  title || 0 == title.length())
        {
            title = org.labkey.api.view.template.PrintTemplate.getDefaultTitle(getRootContext().getActionURL());
            page.setTitle(title);
        }
    }

}

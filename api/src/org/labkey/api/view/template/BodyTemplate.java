package org.labkey.api.view.template;

import org.labkey.api.view.JspView;
import org.springframework.web.servlet.ModelAndView;

/**
 * User: Nick Arnold
 * Date: 12/12/12
 */
public class BodyTemplate extends JspView<PageConfig>
{
    public BodyTemplate(ModelAndView body, PageConfig page)
    {
        super("/org/labkey/api/view/template/bodyTemplate.jsp", page);

        setBody(body);
        setFrame(FrameType.NONE);
    }

    public BodyTemplate(ModelAndView body)
    {
        this(body, new PageConfig());
    }
}

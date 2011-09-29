package org.labkey.api.view.template;

import org.labkey.api.view.JspView;
import org.springframework.web.servlet.ModelAndView;

/**
 * A full screen wizard. Shows the sequence of steps on the left, specified by the NavTrail on the PageConfig.
 * The one that matches the current page's title is considered the active step.
 * User: jeckels
 * Date: Sep 24, 2011
 */
public class WizardTemplate extends JspView<PageConfig>
{
    public WizardTemplate(ModelAndView body, PageConfig page)
    {
        super("/org/labkey/api/view/template/wizardTemplate.jsp", page);

        if (page.showHeader() != PageConfig.TrueFalse.False)
            setView("header", new TemplateHeaderView(page));

        setBody(body);
        setFrame(FrameType.NONE);
    }

    public WizardTemplate(ModelAndView body)
    {
        this(body, new PageConfig());
    }
}

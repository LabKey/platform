package org.labkey.api.view;

import org.labkey.api.view.template.PageConfig;
import org.springframework.web.servlet.ModelAndView;

/**
 * Created by matthew on 12/11/15.
 */
public interface ViewService
{
    /** see PageConfig.Template for primary enum */

    interface TemplateFactory
    {
        HttpView<PageConfig> createTemplate(Enum e, ViewContext context, ModelAndView body, PageConfig page);
    }

    void registerTemplateFactory(Enum template, TemplateFactory f);

    HttpView<PageConfig> getTemplate(Enum e, ViewContext context, ModelAndView body, PageConfig page);

    /** FRAMES **/

    enum FrameType
    {
        /** with portal widgets */
        PORTAL,
        /** title w/ hr */
        TITLE,
        DIALOG,
        /** just <div class=""> */
        DIV,
        LEFT_NAVIGATION,
        /** clean */
        NONE,
        NOT_HTML    // same as NONE, just a marker
    }

    /*
    interface FrameFactory
    {
        WebPartFrame createFrame(Enum e, ViewContext context, WebPartFrame.FrameConfig config);
    }

    void registerFrameFactory(Enum template, FrameFactory f);

    HttpView<PageConfig> getFrame(Enum e, ViewContext context, WebPartFrame.FrameConfig config);
    */
}

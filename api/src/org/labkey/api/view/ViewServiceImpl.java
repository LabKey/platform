package org.labkey.api.view;

import org.labkey.api.view.template.PageConfig;
import org.springframework.web.servlet.ModelAndView;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by matthew on 12/14/15.
 *
 */
public class ViewServiceImpl implements ViewService
{
    static ViewService instance = new ViewServiceImpl();
    Map<Enum,TemplateFactory> templates = Collections.synchronizedMap(new HashMap<>());

    public static ViewService getInstance()
    {
        return instance;
    }

    @Override
    public void registerTemplateFactory(Enum template, TemplateFactory f)
    {
        templates.put(template, f);
    }

    @Override
    public HttpView<PageConfig> getTemplate(Enum e, ViewContext context, ModelAndView body, PageConfig page)
    {
        TemplateFactory f = templates.get(e);
        if (null == f)
            f = templates.get(PageConfig.Template.Home);
        return f.createTemplate(e, context, body, page);
    }

    Map<Enum,FrameFactory> frames = Collections.synchronizedMap(new HashMap<>());

    @Override
    public void registerFrameFactory(Enum frame, FrameFactory f)
    {
        frames.put(frame, f);
    }

    @Override
    public WebPartFrame getFrame(Enum e, ViewContext context, WebPartFrame.FrameConfig config)
    {
        FrameFactory f = frames.get(e);
        if (null == f)
            f = frames.get(WebPartView.FrameType.PORTAL);
        return f.createFrame(e, context, config);
    }
}

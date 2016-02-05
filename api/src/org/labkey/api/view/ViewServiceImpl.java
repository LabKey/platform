/*
 * Copyright (c) 2015-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

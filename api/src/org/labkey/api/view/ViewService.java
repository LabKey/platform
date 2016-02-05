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

/**
 * Created by matthew on 12/11/15.
 */
public interface ViewService
{
    /** TEMPLATES see PageConfig.Template for primary enum */

    interface TemplateFactory
    {
        HttpView<PageConfig> createTemplate(Enum e, ViewContext context, ModelAndView body, PageConfig page);
    }

    void registerTemplateFactory(Enum template, TemplateFactory f);

    HttpView<PageConfig> getTemplate(Enum e, ViewContext context, ModelAndView body, PageConfig page);

    /** FRAMES see WebPartView.FrameType for primary enum **/

    interface FrameFactory
    {
        WebPartFrame createFrame(Enum e, ViewContext context, WebPartFrame.FrameConfig config);
    }

    void registerFrameFactory(Enum template, FrameFactory f);

    WebPartFrame getFrame(Enum e, ViewContext context, WebPartFrame.FrameConfig config);
}

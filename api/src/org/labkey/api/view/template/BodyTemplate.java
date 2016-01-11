/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.api.view.template;

import org.labkey.api.view.JspView;
import org.springframework.web.servlet.ModelAndView;

/**
 * User: Nick Arnold
 * Date: 12/12/12
 */
public class BodyTemplate extends JspView<PageConfig>
{
    private final boolean isAppTemplate;

    public BodyTemplate(ModelAndView body, PageConfig page, boolean isAppTemplate)
    {
        super("/org/labkey/api/view/template/bodyTemplate.jsp", page);

        setBody(body);
        setFrame(FrameType.NONE);
        this.isAppTemplate = isAppTemplate;
    }
    
    public BodyTemplate(ModelAndView body, PageConfig page)
    {
        this(body, page, false);
    }

    public BodyTemplate(ModelAndView body)
    {
        this(body, new PageConfig());
    }

    public boolean isAppTemplate()
    {
        return isAppTemplate;
    }
}

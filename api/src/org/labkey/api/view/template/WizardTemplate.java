/*
 * Copyright (c) 2011 LabKey Corporation
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

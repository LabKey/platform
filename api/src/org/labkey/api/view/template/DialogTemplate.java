/*
 * Copyright (c) 2005-2016 Fred Hutchinson Cancer Research Center
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
 * User: mbellew
 * Date: Mar 2, 2004
 * Time: 1:17:42 PM
 */
public class DialogTemplate extends JspView<PageConfig>
{
    public DialogTemplate(ModelAndView body, PageConfig page)
    {
        super("/org/labkey/api/view/template/dialogTemplate.jsp", page);

        if (page.showHeader() != PageConfig.TrueFalse.False)
            setView("header", new TemplateHeaderView(page));

        setBody(body);
        setFrame(FrameType.NONE);
    }

    public DialogTemplate(ModelAndView body)
    {
        this(body, new PageConfig());
    }
}

/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.core.view.template.bootstrap.factory;

import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewService;
import org.labkey.api.view.ViewServiceImpl;
import org.labkey.api.view.template.PageConfig;
import org.labkey.core.view.template.bootstrap.PrintTemplate;
import org.labkey.core.view.template.bootstrap.AppTemplate;
import org.labkey.core.view.template.bootstrap.PageTemplate;
import org.labkey.core.view.template.bootstrap.DialogTemplate;
import org.labkey.core.view.template.bootstrap.WizardTemplate;
import org.springframework.web.servlet.ModelAndView;

public class TemplateFactory implements ViewService.TemplateFactory
{
    public void registerTemplates()
    {
        for (PageConfig.Template t : PageConfig.Template.values())
            ViewServiceImpl.getInstance().registerTemplateFactory(t, this);
    }

    @Override
    public HttpView<PageConfig> createTemplate(Enum e, ViewContext context, ModelAndView body, PageConfig page)
    {
        if (!(e instanceof PageConfig.Template))
            throw new IllegalStateException("unexpected value: " + e);
        PageConfig.Template t = (PageConfig.Template)e;

        switch (t)
        {
            case None:
            {
                return null;
            }
            case Framed:
            case Print:
            {
                return new PrintTemplate(context, body, page);
            }
            case Dialog:
            {
                return new DialogTemplate(context, body, page);
            }
            case Wizard:
            {
                return new WizardTemplate(context, body, page);
            }
            case Body:
            case App:
            {
                return new AppTemplate(context, body, page, t.equals(PageConfig.Template.App));
            }
            case Home:
            default:
            {
                return new PageTemplate(context, body, page);
            }
        }
    }
}

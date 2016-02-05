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
package org.labkey.api.view.template;

import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewService;
import org.labkey.api.view.ViewServiceImpl;
import org.springframework.web.servlet.ModelAndView;

public class TemplateFactoryClassic implements ViewService.TemplateFactory
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
                return new PrintTemplate(body, page);
            }
            case Dialog:
            {
                return new DialogTemplate(body, page);
            }
            case Wizard:
            {
                return new WizardTemplate(body, page);
            }
            case Body:
            {
                return new BodyTemplate(body, page);
            }
            case App:
            {
                return new BodyTemplate(body, page, true);
            }
            case Home:
            default:
            {
                return new HomeTemplate(context, context.getContainer(), body, page);
            }
        }
    }
}

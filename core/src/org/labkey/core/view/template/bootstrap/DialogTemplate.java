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
package org.labkey.core.view.template.bootstrap;

import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.PageConfig;
import org.springframework.web.servlet.ModelAndView;

public class DialogTemplate extends PageTemplate
{
    public DialogTemplate(ViewContext context, ModelAndView body, PageConfig page)
    {
        super(context, body, page);
    }

    @Override
    protected ModelAndView getBodyTemplate(PageConfig page, ModelAndView body)
    {
        JspView view = new JspView<>("/org/labkey/core/view/template/bootstrap/dialog.jsp", page);
        view.setBody(body);
        view.setFrame(FrameType.NONE);
        return view;
    }

    @Override
    protected HttpView getNavigationView(ViewContext context, PageConfig page)
    {
        return null;
    }
}

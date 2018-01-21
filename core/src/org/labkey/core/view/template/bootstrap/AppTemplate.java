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

import org.labkey.api.data.Container;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.PageConfig;
import org.springframework.web.servlet.ModelAndView;

import java.util.Collections;

public class AppTemplate extends PageTemplate
{
    public AppTemplate(ViewContext context, ModelAndView body, PageConfig page)
    {
        this(context, context.getContainer(), body, page, false);
    }

    public AppTemplate(ViewContext context, ModelAndView body, PageConfig page, boolean isApp)
    {
        this(context, context.getContainer(), body, page, isApp);
    }

    protected AppTemplate(ViewContext context, Container c, ModelAndView body, PageConfig page, boolean isApp)
    {
        super("/org/labkey/core/view/template/bootstrap/pageTemplate.jsp", page);
        this.setAppTemplate(isApp);

        if (null == page.getNavTrail())
            page.setNavTrail(Collections.emptyList());

        setUserMetaTag(context, page);

        // don't show the header on body template
        page.setShowHeader(false);

        setBody(body);
        setView("bodyTemplate", getBodyTemplate(page, body));
    }

    @Override
    protected ModelAndView getBodyTemplate(PageConfig page, ModelAndView body)
    {
        return body;
    }
}

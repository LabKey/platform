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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.PageConfig;
import org.springframework.web.servlet.ModelAndView;

import java.util.Collections;

/**
 * Created by xingyang on 4/21/17.
 */
public class PrintTemplate extends PageTemplate
{
    public PrintTemplate(ViewContext context, ModelAndView body, PageConfig page)
    {
        super("/org/labkey/core/view/template/bootstrap/pageTemplate.jsp", page);

        if (null == page.getNavTrail())
            page.setNavTrail(Collections.emptyList());

        setUserMetaTag(context, page);

        setBody(body);
        setView("bodyTemplate", getBodyTemplate(page, body));
    }

    private String getDefaultTitle(ActionURL helper)
    {
        String title;
        LookAndFeelProperties lafp = LookAndFeelProperties.getInstance(getContextContainer());
        if (StringUtils.isNotEmpty(lafp.getShortName()))
        {
            title = lafp.getShortName();
        }
        else
        {
            title = helper.getHost();
            if (title.startsWith("www."))
                title = title.substring("www.".length());
            int dotIndex = title.indexOf('.');
            if (-1 != dotIndex)
                title = title.substring(0, dotIndex);
        }

        String extraPath = helper.getExtraPath();
        if (null != extraPath && !"".equals(extraPath))
        {
            int slashIndex = extraPath.lastIndexOf('/');
            if (-1 != slashIndex)
                extraPath = extraPath.substring(slashIndex + 1);

            title = title + ": " + extraPath;
        }

        return title;
    }

    @Override
    public void prepareWebPart(PageConfig page)
    {
        if (StringUtils.isEmpty(page.getTitle()))
        {
            page.setTitle(getDefaultTitle(getRootContext().getActionURL()));
        }
    }
}

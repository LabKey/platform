/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.springframework.web.servlet.ModelAndView;

public class PrintTemplate extends JspView<PageConfig>
{
    protected PrintTemplate(String template, PageConfig page)
    {
        super(template, page);
        page.setShowHeader(false);
        setFrame(FrameType.NONE);
    }

    public PrintTemplate(ModelAndView body, PageConfig page)
    {
        this("/org/labkey/api/view/template/HomeTemplate.jsp", page);
        setBody(body);
    }

    public PrintTemplate(ModelAndView body, String title)
    {
        this(body, new PageConfig());
        if (title != null)
            getModelBean().setTitle(title);
    }

    public PrintTemplate(ModelAndView body)
    {
        this(body, (String)null);
    }

    public static String getDefaultTitle(ActionURL helper)
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
        String title = page.getTitle();
        if (null ==  title || 0 == title.length())
        {
            title = PrintTemplate.getDefaultTitle(getRootContext().getActionURL());
            page.setTitle(title);
        }
    }
}

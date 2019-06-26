/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
package org.labkey.study.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.view.AlwaysAvailableWebPartFactory;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;

/**
 * User: Mark Igra
 * Date: Nov 17, 2008
 * Time: 9:28:08 PM
 */
public class StudyListWebPartFactory extends AlwaysAvailableWebPartFactory
{
    public static final String DISPLAY_TYPE_PROPERTY = "displayType";

    public StudyListWebPartFactory()
    {
       super("Study List", true, false, LOCATION_MENUBAR, LOCATION_BODY);
    }


    public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
    {
        WebPartView view;

        if (webPart.getLocation().equals(HttpView.BODY))
        {
            if ("grid".equals(webPart.getPropertyMap().get(DISPLAY_TYPE_PROPERTY)))
                view = new StudyListQueryView(portalCtx);
            else
                view = new JspView<>(this.getClass(), "studyListWide.jsp", null);
        }
        else
            view = new JspView<>(this.getClass(), "studyList.jsp", null);
        view.setTitle("Studies");
        return view;
    }

    @Override
    public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
    {
        return new JspView<>(this.getClass(), "customizeStudyList.jsp", webPart);
    }
}

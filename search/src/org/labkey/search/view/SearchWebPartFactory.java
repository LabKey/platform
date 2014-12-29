/*
 * Copyright (c) 2010-2014 LabKey Corporation
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
package org.labkey.search.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.BooleanFormat;
import org.labkey.api.view.AlwaysAvailableWebPartFactory;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;

import java.text.ParseException;

/**
 * User: adam
 * Date: Jan 19, 2010
 * Time: 2:03:13 PM
 */
public class SearchWebPartFactory extends AlwaysAvailableWebPartFactory
{
    public SearchWebPartFactory()
    {
        super("Search", true, false, WebPartFactory.LOCATION_BODY, WebPartFactory.LOCATION_RIGHT);
        addLegacyNames("Narrow Search");
    }

    public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
    {
        boolean includeSubfolders = includeSubfolders(webPart);

        if (WebPartFactory.LOCATION_RIGHT.equals(webPart.getLocation()))
        {
            return new SearchWebPart(includeSubfolders, 0, false, true);
        }
        else
        {
            return new SearchWebPart(includeSubfolders, 40, true, true);
        }
    }


    @Override
    public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
    {
        return new JspView<>("/org/labkey/search/view/customizeSearchWebPart.jsp", webPart);
    }


    public static boolean includeSubfolders(Portal.WebPart part)
    {
        String value = part.getPropertyMap().get("includeSubfolders");

        try
        {
            return BooleanFormat.getInstance().parseObject(value);
        }
        catch (ParseException e)
        {
            return false;
        }
    }
}



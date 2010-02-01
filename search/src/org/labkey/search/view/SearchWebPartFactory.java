/*
 * Copyright (c) 2010 LabKey Corporation
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

import org.labkey.api.util.Search;
import org.labkey.api.view.*;
import org.labkey.search.SearchController;

import java.lang.reflect.InvocationTargetException;

/**
 * User: adam
 * Date: Jan 19, 2010
 * Time: 2:03:13 PM
 */
public class SearchWebPartFactory extends AlwaysAvailableWebPartFactory
{
    public SearchWebPartFactory(String name, String location)
    {
        super(name, location, true, false);
    }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
    {
        int width = 40;
        if ("right".equals(webPart.getLocation()))
        {
            width = 0;
        }
        boolean includeSubfolders = Search.includeSubfolders(webPart);
        return new SearchWebPart("", SearchController.getSearchURL(portalCtx.getContainer()), includeSubfolders, false, width, false);
    }
}



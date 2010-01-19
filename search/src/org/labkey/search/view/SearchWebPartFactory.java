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



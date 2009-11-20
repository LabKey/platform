package org.labkey.core.search;

import org.labkey.api.search.SearchService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.webdav.Resource;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Nov 18, 2009
 * Time: 1:10:55 PM
 */
public class NoopSearchService implements SearchService
{
    public void addResource(SearchCategory category, ActionURL url, PRIORITY pri)
    {
    }

    public void addRunnable(Runnable r, PRIORITY pri)
    {
    }

    public void addResource(String identifier, PRIORITY pri)
    {
    }

    public void addResource(Resource r, PRIORITY pri)
    {
    }

    public void deleteResource(String identifier, PRIORITY pri)
    {
    }

    public void addResourceResolver(@NotNull String prefix, @NotNull ResourceResolver resolver)
    {
    }

    public String search(String queryString)
    {
        return null;
    }

    public void clearIndex()
    {
    }

    public List<SearchCategory> getSearchCategories()
    {
        return null;
    }

    public void addResource(String category, ActionURL url, PRIORITY pri)
    {
        
    }

    public void addSearchCategory(SearchCategory category)
    {

    }
}

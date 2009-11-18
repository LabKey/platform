package org.labkey.core.search;

import org.labkey.api.search.SearchService;
import org.labkey.api.view.ActionURL;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Nov 18, 2009
 * Time: 1:10:55 PM
 */
public class NoopSearchService implements SearchService
{
    public void addResource(ActionURL url, PRIORITY pri)
    {
    }

    public void addResource(Runnable r, PRIORITY pri)
    {
    }

    public void addResource(String identifier, PRIORITY pri)
    {
    }

    public void deleteResource(String identifier, PRIORITY pri)
    {
    }

    public void addResourceResolver(@NotNull String prefix, @NotNull ResourceResolver resolver)
    {
    }
}

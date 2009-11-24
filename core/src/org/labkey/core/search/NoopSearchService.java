package org.labkey.core.search;

import org.labkey.api.search.SearchService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.webdav.Resource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Collections;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Nov 18, 2009
 * Time: 1:10:55 PM
 */
public class NoopSearchService implements SearchService
{
    IndexTask _dummyTask = new IndexTask("Dummy Search Service")
    {
        public void addRunnable(@NotNull Runnable r, @NotNull PRIORITY pri)
        {
        }

        public void addResource(@NotNull SearchCategory category, ActionURL url, PRIORITY pri)
        {
        }

        public void addResource(@NotNull String identifier, PRIORITY pri)
        {
        }

        public void addResource(@NotNull Resource r, PRIORITY pri)
        {
        }

        public void setReady()
        {
        }

        protected void checkDone()
        {
        }
    };


    public IndexTask defaultTask()
    {
        return _dummyTask;
    }

    public IndexTask createTask(String description)
    {
        return _dummyTask;
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

    public void addResource(@Nullable IndexTask task, @NotNull SearchCategory category, ActionURL url, PRIORITY pri)
    {
    }

    public void addSearchCategory(SearchCategory category)
    {

    }

    public List<IndexTask> getTasks()
    {
        return Collections.emptyList();
    }

    public void addTask(IndexTask task)
    {
    }
}

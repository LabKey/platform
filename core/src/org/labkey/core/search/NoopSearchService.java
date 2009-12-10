/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.core.search;

import org.labkey.api.search.SearchService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.webdav.Resource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.io.Reader;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Nov 18, 2009
 * Time: 1:10:55 PM
 */
public class NoopSearchService implements SearchService
{
    IndexTask _dummyTask = new IndexTask()
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

        public String getDescription()
        {
            return "Dummy Search Service";
        }

        public void cancel()
        {
        }

        public boolean isCancelled()
        {
            return false;
        }

        public int getDocumentCountEstimate()
        {
            return 0;
        }

        public int getIndexedCount()
        {
            return 0;
        }

        public int getFailedCount()
        {
            return 0;
        }

        public long getStartTime()
        {
            return 0;
        }

        public long getCompleteTime()
        {
            return 0;
        }

        public void log(String message)
        {
        }

        public Reader getLog()
        {
            return null;
        }

        public void addToEstimate(int i)
        {
        }

        public boolean cancel(boolean mayInterruptIfRunning)
        {
            return false;
        }

        public boolean isDone()
        {
            return false;
        }

        public IndexTask get() throws InterruptedException, ExecutionException
        {
            return null;
        }

        public IndexTask get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
        {
            return null;
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

    public boolean isBusy()
    {
        return false;
    }
}

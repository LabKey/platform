/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.search.SearchResultTemplate;
import org.labkey.api.search.SearchScope;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.WebPartView;
import org.labkey.api.webdav.WebdavResource;

import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
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

        public void addResource(@NotNull String identifier, PRIORITY pri)
        {
        }

        public void addResource(@NotNull WebdavResource r, PRIORITY pri)
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

        public IndexTask get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
        {
            return null;
        }

        public void onSuccess(Runnable r)
        {
        }
    };


    @Override
    public List<Pair<String, String>> getDirectoryTypes()
    {
        return null;
    }

    @Override
    public void resetIndex()
    {
    }

    @Override
    public Map<String, String> getIndexFormatProperties()
    {
        return Collections.singletonMap("Format", "No-op implementation");
    }

    @Override
    public WebPartView getSearchView(boolean includeSubfolders, int textBoxWidth, boolean includeHelpLink, boolean isWebpart)
    {
        return null;
    }

    @Override
    public IndexTask defaultTask()
    {
        return _dummyTask;
    }

    @Override
    public IndexTask createTask(String description)
    {
        return _dummyTask;
    }

    @Override
    public IndexTask createTask(String description, TaskListener l)
    {
        return _dummyTask;
    }

    @Override
    public void addPathToCrawl(Path path, Date d)
    {
    }

    @Override
    public void deleteResource(String identifier)
    {
    }

    @Override
    public void deleteResourcesForPrefix(String prefix)
    {
    }

    @Override
    public void addResourceResolver(@NotNull String prefix, @NotNull ResourceResolver resolver)
    {
    }

    @Override
    public WebdavResource resolveResource(@NotNull String resourceIdentifier)
    {
        return null;
    }

    @Override
    public HttpView getCustomSearchResult(User user, @NotNull String resourceIdentifier)
    {
        return null;
    }

    @Override
    public Map<String, Object> getCustomSearchJson(User user, @NotNull String resourceIdentifier)
    {
        return null;
    }

    @Override
    public void addSearchResultTemplate(@NotNull SearchResultTemplate template)
    {
    }

    @Override
    public SearchResultTemplate getSearchResultTemplate(@Nullable String name)
    {
        return null;
    }

    @Override
    public SearchResult search(String queryString, @Nullable List<SearchCategory> categories, User user, Container current, SearchScope scope, int offset, int limit) throws IOException
    {
        return null;
    }

    @Override
    public void clear()
    {
    }

    @Override
    public void upgradeIndex()
    {
    }

    @Override
    public void clearLastIndexed()
    {
    }

    @Override
    public List<SearchCategory> getSearchCategories()
    {
        return null;
    }

    @Override
    public void addSearchCategory(SearchCategory category)
    {

    }

    @Override
    public List<IndexTask> getTasks()
    {
        return Collections.emptyList();
    }

    @Override
    public boolean isBusy()
    {
        return false;
    }

    @Override
    public void waitForIdle() throws InterruptedException
    {
    }

    @Override
    public void setLastIndexedForPath(Path path, long time, long modified)
    {
    }

    @Override
    public void deleteContainer(String id)
    {
    }

    @Override
    public String escapeTerm(String term)
    {
        return StringUtils.trimToEmpty(term);
    }

    @Override
    public void purgeQueues()
    {
    }

    @Override
    public void start()
    {
    }

    @Override
    public void startCrawler()
    {
    }

    @Override
    public void pauseCrawler()
    {
    }

    @Override
    public @Nullable Throwable getConfigurationError()
    {
        return null;
    }

    @Override
    public boolean isRunning()
    {
        return false;
    }

    @Override
    public List<SecurableResource> getSecurableResources(User user)
    {
        return Collections.emptyList();
    }

    @Override
    public void updateIndex()
    {
    }

    @Override
    public IndexTask indexContainer(@Nullable IndexTask task, Container c, Date since)
    {
        return null==task?_dummyTask:task;
    }

    @Override
    public IndexTask indexProject(@Nullable IndexTask task, Container project)
    {
        return null==task?_dummyTask:task;
    }

    @Override
    public void indexFull(boolean force)
    {
    }

    @Override
    public void addDocumentProvider(DocumentProvider provider)
    {
    }

    @Override
    public void addDocumentParser(DocumentParser parser)
    {
    }

    @Override
    public void notFound(URLHelper url)
    {
    }

    @Override
    public List<SearchCategory> getCategories(String categories)
    {
        return null;
    }

    @Override
    public void maintenance()
    {
    }

    @Override
    public boolean accept(WebdavResource r)
    {
        return true;
    }

    @Override
    public DbSchema getSchema()
    {
        return null;
    }
}

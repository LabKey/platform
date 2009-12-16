/*
 * Copyright (c) 2009-2009 LabKey Corporation
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
package org.labkey.api.search;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.webdav.Resource;
import org.labkey.api.data.*;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.Path;
import org.apache.log4j.Category;

import java.util.*;
import java.util.concurrent.Future;
import java.io.*;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Nov 12, 2009
 * Time: 12:54:01 PM
 */
public interface SearchService
{
    static Category _log = Category.getInstance(SearchService.class);
    
    enum PRIORITY
    {
        crawl,      // lowest
        background, // crawler item

        bulk,       // all wikis
        group,      // one container
        item        // one page/attachment
    }


    enum PROPERTY
    {
        title("title"),
        category("searchCategory"),
        securableResourceId(SecurableResource.class.getName()),
        participantId("org.labkey.study#StudySubject"),
        container(Container.class.getName());

        final String _propName;
        PROPERTY(String name)
        {
            _propName = name;
        }

        @Override
        public String toString()
        {
            return _propName;
        }
    }


    public interface IndexTask extends Future<IndexTask>
    {
        String getDescription();

        boolean isCancelled();

        int getDocumentCountEstimate();

        int getIndexedCount();

        int getFailedCount();

        long getStartTime();

        long getCompleteTime();

        void log(String message);

        Reader getLog();

        void addToEstimate(int i);// indicates that caller is done adding Resources to this task

        /**
         * indicates that we're done adding the initial set of resources/runnables to this task
         * the task be considered done after calling setReady() and there is no more work to do.
         */
        void setReady();

        void addRunnable(@NotNull Runnable r, @NotNull SearchService.PRIORITY pri);

        void addResource(@NotNull SearchService.SearchCategory category, ActionURL url, SearchService.PRIORITY pri);

        void addResource(@NotNull String identifier, SearchService.PRIORITY pri);

        void addResource(@NotNull Resource r, SearchService.PRIORITY pri);
    }


    public interface ResourceResolver
    {
        Resource resolve(@NotNull String resourceIdentifier);
    }


    public static class SearchCategory
    {
        private final String _name;
        private final String _description;

        public SearchCategory(@NotNull String name, @NotNull String description)
        {
            _name = name;
            _description = description;
        }

        public String getName()
        {
            return _name;
        }
        
        public String getDescription()
        {
            return _description;
        }
    }


    //
    // search
    //


    public String search(String queryString);
    public List<SearchCategory> getSearchCategories();


    //
    // index
    //

    IndexTask defaultTask();
    IndexTask createTask(String description);

    void deleteResource(String identifier, PRIORITY pri);

    List<IndexTask> getTasks();

    void addPathToCrawl(Path path);


    /** an indicator that there are a lot of things in the queue */
    boolean isBusy();


    /** default implementation saving lastIndexed */
    void setLastIndexedForPath(Path path, long time);

    //
    // configuration
    //
    
    public void addSearchCategory(SearchCategory category);
    public void addResourceResolver(@NotNull String prefix, @NotNull ResourceResolver resolver);
    public void clearIndex();

    /**
     * filter for documents modified since the provided date
     *
     * modifiedSince == null, means full search
     * otherwise incremental search which may mean either
     *      modified > lastIndexed
     * or
     *      modified > modifiedSince
     *
     * depending on whether lastIndexed is tracked

     * see Module.enumerateDocuments
     */

    public static class LastIndexedClause extends SimpleFilter.FilterClause
    {
        SQLFragment _sqlf = new SQLFragment();
        private List<String> _colNames = new ArrayList<String>();


        public LastIndexedClause(TableInfo info, java.util.Date modifiedSince, String tableAlias)
        {
            boolean incremental = modifiedSince != null;
            // no filter
            if (!incremental)
                return;

            ColumnInfo modified = info.getColumn("modified");
            ColumnInfo lastIndexed = info.getColumn("lastIndexed");
            String prefix = null == tableAlias ? " " : tableAlias + ".";

            if (null != modified && null != lastIndexed)
            {
                _sqlf.append("NOT (");
                _sqlf.append(prefix).append(modified.getSelectName()).append("<").append(prefix).append(lastIndexed.getSelectName());
                _sqlf.append(")");
                _colNames.add(modified.getName());
                _colNames.add(lastIndexed.getName());
            }
            else if (null != modified)
            {
                _sqlf.append(prefix).append(modified.getSelectName()).append("> ?");
                _sqlf.add(modifiedSince);
                _colNames.add(modified.getName());
            }
            else if (null != lastIndexed)
            {
                // lastIndexed but no modified???
            }
        }

        public SQLFragment toSQLFragment(Map<String, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            return _sqlf;
        }

        public List<String> getColumnNames()
        {
            return _colNames;
        }
    }
}


//
// TODO Index Users
// TODO Index Study descriptions
// TODO lastIndexed field
// TODO consider Files table (Attachments?) and attached properties
// TODO participantid and sampleid
// TODO Resource.shouldCrawl()
// TODO: resource.shouldIndex()
//

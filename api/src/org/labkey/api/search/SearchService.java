/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.NavTree;
import org.labkey.api.webdav.Resource;
import org.labkey.api.data.*;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.Path;
import org.apache.log4j.Category;

import java.sql.ResultSet;
import java.sql.SQLException;
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

    public static final SearchCategory navigationCategory = new SearchCategory("navigation", "internal category", false);
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final SearchCategory fileCategory = new SearchCategory("file", "Files and Attachments", false);

    enum PRIORITY
    {
        commit,
        
        idle,       // only used to detect when there is no other work to do
        crawl,      // lowest work priority
        background, // crawler item

        bulk,       // all wikis
        group,      // one container
        item,        // one page/attachment
        delete
    }


    enum PROPERTY
    {
        displayTitle("displayTitle"),
        searchTitle("searchTitle"),
        categories("searchCategories"),
        securableResourceId(SecurableResource.class.getName()),
        participantId("org.labkey.study#StudySubject"),
        container(Container.class.getName()),
        navtrail(NavTree.class.getName());  // as in NavTree.toJS()

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

        void onSuccess(Runnable r);
    }


    //
    // plug in interfaces
    //
    
    public interface ResourceResolver
    {
        Resource resolve(@NotNull String resourceIdentifier);
    }


    public static class SearchCategory
    {
        private final String _name;
        private final String _description;
        private final boolean _showInDialog;

        public SearchCategory(@NotNull String name, @NotNull String description)
        {
            this(name,description,true);
        }
        
        public SearchCategory(@NotNull String name, @NotNull String description, boolean showInDialog)
        {
            _name = name;
            _description = description;
            _showInDialog = showInDialog;
        }

        public String getName()
        {
            return _name;
        }
        
        public String getDescription()
        {
            return _description;
        }

        @Override
        public String toString()
        {
            return _name;
        }
    }


    //
    // search
    //

    
    public static class SearchResult
    {
        public int totalHits;
        public List<SearchHit> hits;
    }

    public static class SearchHit
    {
        public String docid;
        public String container;
        public String displayTitle;
        public String summary;
        public String url;
        public String navtrail;
    }

    public SearchResult search(String queryString, @Nullable SearchCategory category, User user, Container root, boolean recursive, int offset, int limit) throws IOException;

    // Search using no offset, default page size, and recursive.
    public SearchResult search(String queryString, @Nullable SearchCategory category, User user, Container root) throws IOException;

    public String escapeTerm(String term);
    
    public List<SearchCategory> getSearchCategories();

    public boolean isParticipantId(User user, String ptid);

    // CONSIDER: async version
    // public Future<Boolean> isParticipantId(User user);


    //
    // index
    //

    void purgeQueues();
    void start();
    void pause();
    boolean isRunning();

    
    IndexTask defaultTask();
    IndexTask createTask(String description);

    void deleteResource(String identifier);
    
    // helper to call when not found exception is detected
    void notFound(URLHelper url);

    List<IndexTask> getTasks();

    void addPathToCrawl(Path path, @Nullable Date nextCrawl);

    IndexTask indexContainer(@Nullable IndexTask task, Container c, Date since);
    IndexTask indexProject(@Nullable IndexTask task, Container project /*boolean incremental*/);
    void indexFull(boolean force);

    // container, ptid pairs
    void addParticipantIds(Collection<Pair<String,String>> ptids);

    // container, ptid resultset
    void addParticipantIds(ResultSet ptids) throws SQLException;


    /** an indicator that there are a lot of things in the queue */
    boolean isBusy();
    void waitForIdle() throws InterruptedException;


    /** default implementation saving lastIndexed */
    void setLastIndexedForPath(Path path, long indexed, long modified);

    void deleteContainer(String id);

    public void clear();


    //
    // configuration, plugins 
    //
    
    public void addSearchCategory(SearchCategory category);
    public SearchCategory getCategory(String category);
    public void addResourceResolver(@NotNull String prefix, @NotNull ResourceResolver resolver);

    public interface DocumentProvider
    {
        /**
         * enumerate documents for full text search
         *
         * modifiedSince == null -> full reindex
         * else incremental (either modified > modifiedSince, or modified > lastIndexed)
         */
        void enumerateDocuments(IndexTask task, @NotNull Container c, Date since);

        /**
         *if the full-text search is deleted, providers may need to clear
         * any stored lastIndexed values.
         */
        void indexDeleted() throws SQLException;
    }

    // an interface that enumerates documents in a container (not recursive)
    public void addDocumentProvider(DocumentProvider provider);

    
    //
    // helpers
    //
    

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
        private Set<String> _colNames = new HashSet<String>();

        final static java.util.Date oldDate = new java.sql.Timestamp(DateUtil.parseStringJDBC("1967-10-04"));

        public LastIndexedClause(TableInfo info, java.util.Date modifiedSince, String tableAlias)
        {
            boolean incremental = modifiedSince == null || modifiedSince.compareTo(oldDate) > 0;
            
            // no filter
            if (!incremental)
                return;

            ColumnInfo modified = info.getColumn("modified");
            ColumnInfo lastIndexed = info.getColumn("lastIndexed");
            String prefix = null == tableAlias ? " " : tableAlias + ".";

            String or = "";
            if (null != lastIndexed)
            {
                _sqlf.append(prefix).append(lastIndexed.getSelectName()).append(" IS NULL");
                _colNames.add(lastIndexed.getName());
                or = " OR ";
            }

            if (null != modified && null != lastIndexed)
            {
                _sqlf.append(or);
                _sqlf.append(prefix).append(modified.getSelectName()).append(">").append(prefix).append(lastIndexed.getSelectName());
                _colNames.add(modified.getName());
                _colNames.add(lastIndexed.getName());
                or = " OR ";
            }

            if (null != modifiedSince && null != modified)
            {
                _sqlf.append(or);
                _sqlf.append(prefix).append(modified.getSelectName()).append("> ?");
                _sqlf.add(modifiedSince);
                _colNames.add(modified.getName());
            }
        }

        public SQLFragment toSQLFragment(Map<String, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            return _sqlf;
        }

        public List<String> getColumnNames()
        {
            return new ArrayList<String>(_colNames);
        }
    }
}

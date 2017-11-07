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
package org.labkey.api.search;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.FieldKey;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.WebPartView;
import org.labkey.api.webdav.WebdavResource;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * User: matthewb
 * Date: Nov 12, 2009
 * Time: 12:54:01 PM
 */
public interface SearchService
{
    Logger _log = Logger.getLogger(SearchService.class);

    long FILE_SIZE_LIMIT = 100L*(1024*1024); // 100 MB

    SearchCategory navigationCategory = new SearchCategory("navigation", "internal category", false);
    SearchCategory fileCategory = new SearchCategory("file", "Files and Attachments", false);

    // marker value for documents with indexing errors
    Date failDate = new Timestamp(DateUtil.parseISODateTime("1899-12-30"));

    static @Nullable SearchService get()
    {
        return ServiceRegistry.get(SearchService.class);
    }

    static void setInstance(SearchService impl)
    {
        ServiceRegistry.get().registerService(SearchService.class, impl);
    }

    enum PRIORITY
    {
        commit,
        
        idle,       // only used to detect when there is no other work to do
        crawl,      // lowest work priority
        background, // crawler item

        bulk,       // all wikis
        group,      // one container
        item,       // one page/attachment
        delete
    }


    enum PROPERTY
    {
        title("title"),
        keywordsLo("keywordsLo"),
        keywordsMed("keywordsMed"),
        keywordsHi("keywordsHi"),
        identifiersLo("identifiersLo"),
        identifiersMed("identifiersMed"),
        identifiersHi("identifiersHi"),
        categories("searchCategories"),
        securableResourceId(SecurableResource.class.getName()),
        navtrail(NavTree.class.getName());  // as in NavTree.toJS()

        private final String _propName;

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

    enum SEARCH_PHASE {createQuery, buildSecurityFilter, search, applySecurityFilter, processHits}

    interface TaskListener
    {
        void success();
        void indexError(Resource r, Throwable t);
    }


    interface IndexTask extends Future<IndexTask>
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

        void addResource(@NotNull String identifier, SearchService.PRIORITY pri);

        void addResource(@NotNull WebdavResource r, SearchService.PRIORITY pri);
    }


    boolean accept(WebdavResource r);


    //
    // plug in interfaces
    //
    
    interface ResourceResolver
    {
        default WebdavResource resolve(@NotNull String resourceIdentifier) { return null; }
        default HttpView getCustomSearchResult(User user, @NotNull String resourceIdentifier) { return null; }
        default Map<String, Object> getCustomSearchJson(User user, @NotNull String resourceIdentifier) { return null; }
    }


    class SearchCategory
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

    
    class SearchResult
    {
        public long totalHits;
        public List<SearchHit> hits;
    }

    class SearchHit
    {
        public int doc;
        public String docid;
        public String container;
        public String title;
        public String summary;
        public String url;
        public String navtrail;

        public String normalizeHref(Path contextPath)
        {
            Container c = ContainerManager.getForId(container);
            return normalizeHref(contextPath, c);
        }

        public String normalizeHref(Path contextPath, Container c)
        {
            // see issue #11481
            String href = url;
            if (href.startsWith("files/"))
                href = "/" + href;

            try
            {
                if (null != c && href.startsWith("/"))
                {
                    URLHelper url = new URLHelper(href);
                    Path path = url.getParsedPath();
                    if (path.startsWith(contextPath))
                    {
                        int pos = contextPath.size() + 1;
                        if (path.size() > pos && c.getId().equals(path.get(pos)))
                        {
                            path = path.subpath(0,pos)
                                    .append(c.getParsedPath())
                                    .append(path.subpath(pos+1,path.size()));
                            url.setPath(path);
                            return url.getLocalURIString(false);
                        }
                    }
                }
            }
            catch (Exception x)
            {
                //
            }
            return href;
        }
    }

    Map<String, String> getIndexFormatProperties();

    List<Pair<String, String>> getDirectoryTypes();

    DbSchema getSchema();

    WebPartView getSearchView(boolean includeSubfolders, int textBoxWidth, boolean includeHelpLink, boolean isWebpart);

    SearchResult search(String queryString, @Nullable List<SearchCategory> categories, User user, Container current, SearchScope scope, int offset, int limit) throws IOException;

    String escapeTerm(String term);
    
    List<SearchCategory> getSearchCategories();

    //
    // index
    //

    void purgeQueues();
    void start();
    void resetIndex();
    void startCrawler();
    void pauseCrawler();
    void updateIndex();
    @Nullable Throwable getConfigurationError();
    boolean isRunning();

    List<SecurableResource> getSecurableResources(User user);    
    IndexTask defaultTask();
    IndexTask createTask(String description);
    IndexTask createTask(String description, TaskListener l);

    void deleteResource(String identifier);

    // Delete all resources whose documentIds starts with the given prefix
    void deleteResourcesForPrefix(String prefix);

    // helper to call when not found exception is detected
    void notFound(URLHelper url);

    List<IndexTask> getTasks();

    void addPathToCrawl(Path path, @Nullable Date nextCrawl);

    IndexTask indexContainer(@Nullable IndexTask task, Container c, Date since);
    IndexTask indexProject(@Nullable IndexTask task, Container project /*boolean incremental*/);
    void indexFull(boolean force);

    /** an indicator that there are a lot of things in the queue */
    boolean isBusy();
    void waitForIdle() throws InterruptedException;

    
    /** default implementation saving lastIndexed */
    void setLastIndexedForPath(Path path, long indexed, long modified);

    void deleteContainer(String id);

    void clear();                // delete index and reset lastIndexed values. must be callable before (and after) start() has been called.
    void upgradeIndex();         // upgrade to latest format. this must be called before the SearchService is started.
    void clearLastIndexed();     // just reset lastIndexed values. must be callable before (and after) start() has been called.
    void maintenance();

    //
    // configuration, plugins 
    //
    
    void addSearchCategory(SearchCategory category);
    List<SearchCategory> getCategories(String categories);
    void addResourceResolver(@NotNull String prefix, @NotNull ResourceResolver resolver);

    WebdavResource resolveResource(@NotNull String resourceIdentifier);
    HttpView getCustomSearchResult(User user, @NotNull String resourceIdentifier);
    Map<String, Object> getCustomSearchJson(User user, @NotNull String resourceIdentifier);

    void addSearchResultTemplate(@NotNull SearchResultTemplate template);
    @Nullable SearchResultTemplate getSearchResultTemplate(@Nullable String name);

    interface DocumentProvider
    {
        /**
         * enumerate documents for full text search
         *
         * modifiedSince == null -> full reindex
         * else incremental (either modified > modifiedSince, or modified > lastIndexed)
         */
        void enumerateDocuments(IndexTask task, @NotNull Container c, @Nullable Date modifiedSince);

        /**
         *if the full-text search is deleted, providers may need to clear
         * any stored lastIndexed values.
         */
        void indexDeleted() throws SQLException;
    }


    interface DocumentParser
    {
        String getMediaType();
        boolean detect(WebdavResource resource, String contentType, byte[] buf) throws IOException;
        void parse(InputStream stream, ContentHandler handler) throws IOException, SAXException;
    }
    

    // an interface that enumerates documents in a container (not recursive)
    void addDocumentProvider(DocumentProvider provider);

    void addDocumentParser(DocumentParser parser);

    
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

    class LastIndexedClause extends SimpleFilter.FilterClause
    {
        SQLFragment _sqlf = new SQLFragment();
        private Set<FieldKey> _fieldKeys = new HashSet<>();

        final static java.util.Date oldDate = new java.sql.Timestamp(DateUtil.parseISODateTime("1967-10-04"));

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
                _fieldKeys.add(lastIndexed.getFieldKey());
                or = " OR ";
            }

            if (null != modified && null != lastIndexed)
            {
                _sqlf.append(or);
                _sqlf.append(prefix).append(modified.getSelectName()).append(">").append(prefix).append(lastIndexed.getSelectName());
                _fieldKeys.add(modified.getFieldKey());
                _fieldKeys.add(lastIndexed.getFieldKey());
                or = " OR ";
            }

            if (null != modifiedSince && null != modified)
            {
                _sqlf.append(or);
                _sqlf.append(prefix).append(modified.getSelectName()).append("> ?");
                _sqlf.add(modifiedSince);
                _fieldKeys.add(modified.getFieldKey());
            }

            if (_sqlf.isEmpty())
            {
                _sqlf.append("1=1");
            }
            else
            {
                _sqlf.insert(0, "(");
                _sqlf.append(")");
            }
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            throw new UnsupportedOperationException();
        }

        public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            return _sqlf;
        }

        public List<FieldKey> getFieldKeys()
        {
            return new ArrayList<>(_fieldKeys);
        }
    }
}

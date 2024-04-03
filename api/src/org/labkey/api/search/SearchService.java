/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.mbean.SearchMXBean;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.RedirectException;
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
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * User: matthewb
 * Date: Nov 12, 2009
 * Time: 12:54:01 PM
 */
public interface SearchService extends SearchMXBean
{
    // create logger for package which can be set via logger-manage.view
    Logger _packageLogger = LogHelper.getLogger(SearchService.class.getPackage(), "Full text search module rollup");
    Logger _log = LogHelper.getLogger(SearchService.class, "Full text search service");

    long DEFAULT_FILE_SIZE_LIMIT = 100L; // 100 MB

    /**
     * Returns the max file size indexed
     * @return Maximum file size in bytes
     */
    default long getFileSizeLimit()
    {
        return DEFAULT_FILE_SIZE_LIMIT * (1024*1024);
    }

    // If "_docid" parameter is present, strip it and redirect as a convenience in the "result was found" case
    static void stripDocIdParameterAndRedirect(@NotNull ActionURL url)
    {
        if (null != url.getParameter("_docid"))
            throw new RedirectException(url.clone().deleteParameter("_docid"));
    }

    SearchCategory navigationCategory = new SearchCategory("navigation", "internal category", false);
    SearchCategory fileCategory = new SearchCategory("file", "Files and Attachments", false);

    // marker value for documents with indexing errors
    Date failDate = new Timestamp(DateUtil.parseISODateTime("1899-12-30"));

    static @Nullable SearchService get()
    {
        return ServiceRegistry.get().getService(SearchService.class);
    }

    static void setInstance(SearchService impl)
    {
        ServiceRegistry.get().registerService(SearchService.class, impl);
    }

    /**
     * Delete the index documents for any files in a container then start a new crawler task for just that container
     * @param c
     */
    void reindexContainerFiles(Container c);

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
        ontology("ontology"),
        summary("summary"),
        jsonData("jsonData"),
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

        default void addRunnable(@NotNull SearchService.PRIORITY pri, @NotNull Runnable r)
        {
            addRunnable(r, pri);
        }

        void addRunnable(@NotNull Runnable r, @NotNull SearchService.PRIORITY pri);

        void addResource(@NotNull String identifier, SearchService.PRIORITY pri);

        void addResource(@NotNull WebdavResource r, SearchService.PRIORITY pri);

        /* This adds do nothing item to the queue, this is only useful for tracking progress of the queue. see TaskListener. */
        void addNoop(SearchService.PRIORITY pri);

        default <T> void addResourceList(List<T> list, int batchSize, Function<T,WebdavResource> mapper)
        {
            ListUtils.partition(list, batchSize).forEach(sublist ->
            {
                addRunnable( () ->
                    sublist.stream()
                            .map(mapper::apply)
                            .filter(Objects::nonNull)
                            .forEach(doc -> addResource(doc, PRIORITY.item))
                    , PRIORITY.group);
            });
        }
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
        default Map<String, Map<String, Object>> getCustomSearchJsonMap(User user, @NotNull Collection<String> resourceIdentifiers)
        {
            Map<String, Map<String, Object>> results = new HashMap<>();
            for (String resourceIdentifier : resourceIdentifiers)
            {
                results.put(resourceIdentifier, getCustomSearchJson(user, resourceIdentifier));
            }
            return results;
        }
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

        protected Set<String> getPermittedContainerIds(User user, Map<String, Container> containers, @NotNull Class<? extends Permission> perm)
        {
            Set<String> containerIds = new HashSet<>();
            containers.forEach((id, container) -> {
                if (container.hasPermission(user, perm))
                    containerIds.add(id);
            });
            return containerIds.size() == containers.size() ? containers.keySet() : containerIds;
        }

        public Set<String> getPermittedContainerIds(User user, Map<String, Container> containers)
        {
            return containers.keySet();
        }
    }


    //
    // search
    //

    
    class SearchResult
    {
        public long totalHits;
        public List<SearchHit> hits;
        public long offset;
    }

    class SearchHit
    {
        public int doc;
        public String docid;
        public String category;
        public String container;
        public String title;
        public String summary;
        public String url;
        public String navtrail;
        public String identifiers; // identifiersHi
        public JSONObject jsonData;
        public float score;

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
                        int pos = path.size() - 2; // look to see if second to last path part is GUID
                        if (pos>=0 && c.getId().equals(path.get(pos)))
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

    SearchResult search(SearchOptions options) throws IOException;

    // return list of uniqueId
    List<String> searchUniqueIds(SearchOptions options) throws IOException;

    @Nullable SearchHit find(String docId) throws IOException;

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
    void refreshNow();

    @Nullable Throwable getConfigurationError();

    IndexTask defaultTask();
    IndexTask createTask(String description);
    IndexTask createTask(String description, TaskListener l);

    void deleteResource(String identifier);
    void deleteResources(Collection<String> ids);

    // Delete all resources whose documentIds starts with the given prefix
    void deleteResourcesForPrefix(String prefix);

    // helper to call when not found exception is detected
    void notFound(URLHelper url);

    List<IndexTask> getTasks();

    void addPathToCrawl(Path path, @Nullable Date nextCrawl);

    IndexTask indexContainer(@Nullable IndexTask task, Container c, Date since);
    // TODO: Remove? This is never called
    IndexTask indexProject(@Nullable IndexTask task, Container project /*boolean incremental*/);
    void indexFull(boolean force);

    void waitForIdle() throws InterruptedException;

    
    /** default implementation saving lastIndexed */
    void setLastIndexedForPath(Path path, long indexed, long modified);

    void deleteContainer(String id);

    void deleteIndex();          // close the index if it's been initialized, then delete the index directory and reset lastIndexed values
    void clearLastIndexed();     // reset lastIndexed values and initiate aggressive crawl. must be callable before (and after) start() has been called.
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
    Map<String, Map<String, Object>> getCustomSearchJsonMap(User user, @NotNull Collection<String> resourceIdentifiers);

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
        default void indexDeleted() throws SQLException
        {
        }

        /**
         * Thrown for a document that is an illegal/invalid state that should not be indexed, but can be safely ignored.
         * May be caused by the document's subject being queued for indexing, but being deleted prior to processing.
         */
        class InvalidDocumentException extends IllegalStateException
        {
            public InvalidDocumentException(String message)
            {
                super(message);
            }

            public InvalidDocumentException(Throwable e)
            {
                super(e);
            }

            public InvalidDocumentException(String message, Throwable e)
            {
                super(message, e);
            }
        }
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
        private static final java.util.Date oldDate = new java.sql.Timestamp(DateUtil.parseISODateTime("1967-10-04"));

        private final SQLFragment _sqlf = new SQLFragment();
        private final Set<FieldKey> _fieldKeys = new HashSet<>();

        // for assert
        private final boolean tableAliasProvidedInConstructor;

        /* NOTE: Some callers construct their own SQL and know the tableAlias and can provide it here
         * Other usages use TableSelector (et al), and don't know the alias until toSQLFragment() is called.
         * If the tableAlias is not provided in either the constructor or toSQLFragment() expect a syntax error!
         */
        public LastIndexedClause(TableInfo info, java.util.Date modifiedSince, String tableAlias)
        {
            this(info, modifiedSince, tableAlias, info, tableAlias);
        }

        public LastIndexedClause(TableInfo info, java.util.Date modifiedSince, String modifiedTableAlias, TableInfo lastIndexedTable, String lastIndexedTableAlias)
        {
            tableAliasProvidedInConstructor = null != modifiedTableAlias && !ExprColumn.STR_TABLE_ALIAS.equals(modifiedTableAlias);

            // Incremental if modifiedSince is set and is more recent than 1967-10-04
            boolean incremental = modifiedSince != null && modifiedSince.compareTo(oldDate) > 0;
            
            // no filter
            if (!incremental)
                return;

            ColumnInfo modified = info.getColumn("modified");
            ColumnInfo lastIndexed = lastIndexedTable.getColumn("lastIndexed");
            if (null == modifiedTableAlias)
                modifiedTableAlias = ExprColumn.STR_TABLE_ALIAS;
            if (null == lastIndexedTableAlias)
                lastIndexedTableAlias = ExprColumn.STR_TABLE_ALIAS;

            String or = "";
            if (null != lastIndexed)
            {
                _sqlf.append(lastIndexed.getValueSql(lastIndexedTableAlias)).append(" IS NULL");
                _fieldKeys.add(lastIndexed.getFieldKey());
                or = " OR ";
            }

            if (null != modified && null != lastIndexed)
            {
                _sqlf.append(or);
                _sqlf.append(modified.getValueSql(modifiedTableAlias)).append(">").append(lastIndexed.getValueSql(lastIndexedTableAlias));
                _fieldKeys.add(modified.getFieldKey());
                _fieldKeys.add(lastIndexed.getFieldKey());
                or = " OR ";
            }

            if (null != modifiedSince && null != modified)
            {
                _sqlf.append(or);
                _sqlf.append(modified.getValueSql(modifiedTableAlias)).append("> ?");
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

        public boolean isEmpty()
        {
            return _sqlf.isEmpty();
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            assert tableAliasProvidedInConstructor;
            return toSQLFragment(null, columnMap, dialect);
        }

        @Override
        public SQLFragment toSQLFragment(String tableAlias, Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            assert tableAliasProvidedInConstructor || tableAlias != null;
            if (this._sqlf.isEmpty() || null == tableAlias)
                return _sqlf;
            String sql = StringUtils.replace(_sqlf.getSQL(), ExprColumn.STR_TABLE_ALIAS, tableAlias);
            SQLFragment ret = new SQLFragment(sql);
            ret.addAll(_sqlf.getParams());
            return ret;
        }

        @Override
        public List<FieldKey> getFieldKeys()
        {
            return new ArrayList<>(_fieldKeys);
        }
    }

    class SearchOptions
    {
        public final List<SearchCategory> categories;
        public final Container container;
        public final List<String> fields;
        public final Boolean invertResults;
        public final Integer limit;
        public final Integer offset;
        public final String queryString;
        public final SearchScope scope;
        public final String sortField;
        public final User user;

        private SearchOptions(
            String queryString,
            User user,
            Container container,
            @Nullable List<SearchCategory> categories,
            @Nullable SearchScope scope,
            @Nullable String sortField,
            @Nullable Integer offset,
            @Nullable Integer limit,
            @Nullable Boolean invertResults,
            @Nullable List<String> fields
        )
        {
            this.categories = categories;
            this.container = container;
            this.fields = fields;
            this.invertResults = invertResults != null && invertResults;
            this.limit = limit == null ? 100 : limit;
            this.offset = offset == null ? 0 : offset;
            this.queryString = queryString;
            this.scope = scope == null ? SearchScope.Folder : scope;
            this.sortField = sortField;
            this.user = user;
        }

        public static class Builder
        {
            public List<SearchCategory> categories;
            public Container container;
            public List<String> fields;
            public Boolean invertResults;
            public Integer limit;
            public Integer offset;
            public String queryString;
            public SearchScope scope;
            public String sortField;
            public User user;

            public Builder() {}

            public Builder(String queryString, User user, Container container)
            {
                this.container = container;
                this.queryString = queryString;
                this.user = user;
            }

            public Builder(SearchOptions options)
            {
                this(options.queryString, options.user, options.container);
                this.categories = options.categories;
                this.fields = options.fields;
                this.invertResults = options.invertResults;
                this.limit = options.limit;
                this.offset = options.offset;
                this.scope = options.scope;
                this.sortField = options.sortField;
            }

            public SearchOptions build()
            {
                return new SearchOptions(queryString, user, container, categories, scope, sortField, offset, limit, invertResults, fields);
            }
        }
    }
}

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
package org.labkey.search.model;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CopyOnWriteHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.search.SearchResultTemplate;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.Formats;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.MultisetRateAccumulator;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.search.view.DefaultSearchResultTemplate;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

public abstract class AbstractSearchService implements SearchService, ShutdownListener
{
    private static final Logger _log = LogHelper.getLogger(AbstractSearchService.class, "Full-text search indexing events");
    // Search categories that are candidates for logging. Each category gets a logger that can be enabled by setting it
    // to DEBUG on the Loggers page. It will then log indexing data for each document having that category.
    private static final Set<String> CATEGORIES_TO_LOG = Set.of("navigation", "assay");

    static
    {
        // This adds each desired logger to the Loggers page
        CATEGORIES_TO_LOG.forEach(AbstractSearchService::getLoggerForCategory);
    }

    private static Logger getLoggerForCategory(String category)
    {
        return LogManager.getLogger(SearchCategory.class.getName() + "." + category);
    }

    // Runnables go here, and get pulled off in a single-threaded manner (assumption is that Runnables can create work very quickly)
    final PriorityBlockingQueue<Item> _runQueue = new PriorityBlockingQueue<>(1000, itemCompare);

    // Resources go here for preprocessing (this can be multi-threaded)
    final PriorityBlockingQueue<Item> _itemQueue = new PriorityBlockingQueue<>(1000, itemCompare);

    private final List<IndexTask> _tasks = new CopyOnWriteArrayList<>();
    private final _IndexTask _defaultTask = new _IndexTask("default");

    private Throwable _configurationError = null;

    enum OPERATION
    {
        add, delete, noop
    }

    static final Comparator<Item> itemCompare = Comparator.comparing(o -> o._pri);


    public AbstractSearchService()
    {
        addSearchCategory(fileCategory);
        addSearchCategory(navigationCategory);
    }


    @Override
    public IndexTask createTask(String description)
    {
        _IndexTask task = new _IndexTask(description);
        addTask(task);
        return task;
    }

    @Override
    public IndexTask createTask(String description, TaskListener l)
    {
        _IndexTask task = new _IndexTask(description, l);
        addTask(task);
        return task;
    }

    @Override
    public IndexTask defaultTask()
    {
        return _defaultTask;
    }


    @Override
    public boolean accept(WebdavResource r)
    {
        return true;
    }


    @Override
    public void addPathToCrawl(Path path, Date next)
    {
        DavCrawler.getInstance().addPathToCrawl(path, next);
    }


    class _IndexTask extends AbstractIndexTask
    {
        _IndexTask(String description)
        {
            super(description, null);
        }

        _IndexTask(String description, TaskListener l)
        {
            super(description, l);
        }


        @Override
        public void addRunnable(@NotNull Runnable r, @NotNull PRIORITY pri)
        {
            Item i = new Item(this, r, pri);
            this.addItem(i);
            queueItem(i);
        }


        @Override
        public void addResource(@NotNull String identifier, PRIORITY pri)
        {
            Item i = new Item(this, OPERATION.add, identifier, null, pri);
            this.addItem(i);
            queueItem(i);
        }


        @Override
        public void addResource(@NotNull WebdavResource r, PRIORITY pri)
        {
            if (!r.shouldIndex())
                return;
            Item i = new Item(this, OPERATION.add, r.getDocumentId(), r, pri);
            addItem(i);
            queueItem(i);
        }


        @Override
        public void addNoop(PRIORITY pri)
        {
            final Item i = new Item( this, OPERATION.noop, "noop://noop", null, pri);
            addItem(i);
            final Item r = new Item(this, () -> queueItem(i), pri);
            queueItem(r);
        }


        @Override
        public void completeItem(Item item, boolean success)
        {
            item._complete = HeartBeat.currentTimeMillis();
            super.completeItem(item, success);
        }


        @Override
        protected boolean checkDone()
        {
            if (_isReady)
            {
                assert _subtasks.isEmpty();
                return _tasks.remove(this);
            }
            return false;
        }
        

        @Override
        public void setReady()
        {
            if (this == _defaultTask)
                throw new IllegalStateException();
            super.setReady();
        }
    }

    
    // Consider: remove _op/OPERATION (not used), subclasses for resource vs. runnable (would clarify invariants and make
    // hashCode() & equals() more straightforward), formalize _id (using Runnable.toString() seems weak).
    class Item
    {
        OPERATION _op;
        String _id;
        IndexTask _task;
        WebdavResource _res;
        Runnable _run;
        PRIORITY _pri;

        int _preprocessAttempts = 0;

        long _modified = 0; // used by setLastIndexed
        long _start = 0;    // used by setLastIndexed
        long _complete = 0; // really just for debugging

        Item(IndexTask task, OPERATION op, String id, WebdavResource r, PRIORITY pri)
        {
            if (null != r)
                _start = HeartBeat.currentTimeMillis();
            _op = op;
            _id = id;
            _res = r;
            _pri = null == pri ? PRIORITY.bulk : pri;
            _task = task;
        }

        Item(IndexTask task, Runnable r, PRIORITY pri)
        {
            _run = r;
            _pri = null == pri ? PRIORITY.bulk : pri;
            _task = task;
            _id = String.valueOf(r);
        }

        OPERATION getOperation()
        {
            return _op;
        }

        WebdavResource getResource()
        {
            if (null == _res)
            {
                _start = HeartBeat.currentTimeMillis();
                _res = resolveResource(_id);
            }
            return _res;
        }

        void complete(boolean success)
        {
            if (null != _task)
            {
                ((_IndexTask)_task).completeItem(this, success);
            }

            if (!success)
            {
                WebdavResource r = getResource();
                if (null != r)
                    r.setLastIndexed(SavePaths.failDate.getTime(), _modified);
            }
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Item item = (Item) o;

            if (_id != null ? !_id.equals(item._id) : item._id != null) return false;
            if (_op != item._op) return false;
            if (_res != null ? !_res.equals(item._res) : item._res != null) return false;
            return _run != null ? _run.equals(item._run) : item._run == null;
        }

        @Override
        public int hashCode()
        {
            int result = _op != null ? _op.hashCode() : 0;
            result = 31 * result + (_id != null ? _id.hashCode() : 0);
            result = 31 * result + (_res != null ? _res.hashCode() : 0);
            result = 31 * result + (_run != null ? _run.hashCode() : 0);
            return result;
        }

        @Override
        public String toString()
        {
            return "Item{" + (null != _res ? _res.toString() : null != _run ? _run.toString() : _op.name()) + '}';
        }
    }


    final Item _commitItem = new Item(null, () -> {}, PRIORITY.commit);


    @Override
    public boolean isBusy()
    {
        if (!_runQueue.isEmpty())
            return true;
        int n = _itemQueue.size();
        return n > 100;
    }


    final Object _idleEvent = new Object();
    boolean _idleWaiting = false;


    @Override
    public void waitForIdle() throws InterruptedException
    {
        if (_runQueue.isEmpty() && _itemQueue.size() < 4)
            return;
        synchronized (_idleEvent)
        {
            _idleWaiting = true;
            _idleEvent.wait();
        }
    }


    private void checkIdle()
    {
        if (_runQueue.isEmpty() && _itemQueue.isEmpty())
        {
            synchronized (_idleEvent)
            {
                if (_idleWaiting)
                {
                    _idleEvent.notifyAll();
                    _idleWaiting = false;
                }
            }
        }
    }


    private final SavePaths _savePaths = new SavePaths();

    @Override
    public void setLastIndexedForPath(Path path, long time, long indexed)
    {
        _savePaths.updateFile(path, new Date(time), new Date(indexed));
    }

    @Override
    public final void deleteContainer(final String id)
    {
        Runnable r = () -> {
            deleteIndexedContainer(id);
            synchronized (_commitLock)
            {
                _countIndexedSinceCommit++;
            }
        };
        queueItem(new Item(defaultTask(), r, PRIORITY.background));
    }

    @Override
    public void clearLastIndexed()
    {
        _log.info("Clearing last indexed for all providers");

        for (DocumentProvider p : _documentProviders)
        {
            try
            {
                _log.info("Clearing last indexed for provider : " + p.getClass().getName());
                p.indexDeleted();
            }
            catch (Throwable t)
            {
                _log.error("Unexpected error", t);
            }
        }

        _log.info("Clearing last indexed is complete");

        // CONSIDER: have DavCrawler implement DocumentProvider and listen for indexDeleted()
        DavCrawler.getInstance().clearFailedDocuments();
        DavCrawler.getInstance().startFull(WebdavService.getPath(), true);
    }

    /**
     * Delete any existing index file documents, and start a new indexing task for container
     * @param c Container to reindex
     */
    @Override
    public void reindexContainerFiles(Container c)
    {
        //Create new runnable instead of using existing methods so they can be run within the same job.
        Runnable r = () -> {
            //Remove old items
            clearIndexedFileSystemFiles(c);

            //TODO: make DavCrawler (or a wrapper) a DocumentProvider instead
            //Recrawl files as the container name may be used in paths & Urls. Issue #39696
            DavCrawler.getInstance().startFull(WebdavService.getPath().append(c.getParsedPath()), true);
        };
        queueItem(new Item(defaultTask(), r, PRIORITY.delete));
    }

    private void queueItem(Item i)
    {
        // UNDONE: this is not 100% correct, consider passing in a scope with Item
        DbScope s = DbScope.getLabKeyScope();

        if (s.isTransactionActive())
        {
            s.addCommitTask(new ItemRunnable(i), DbScope.CommitTaskOption.POSTCOMMIT);
            return;
        }

        _log.debug("_submitQueue.put(" + i._id + ")");

        if (null != i._run)
        {
            _runQueue.put(i);
        }
        else
        {
            _itemQueue.put(i);
        }
    }


    // ItemRunnable is used to delegate equals() and hashCode() to Item; this enables coalescing of redundant
    // indexing tasks during transactions.
    private class ItemRunnable implements Runnable
    {
        private final @NotNull Item _item;

        public ItemRunnable(@NotNull Item item)
        {
            _item = item;
        }

        @Override
        public void run()
        {
            queueItem(_item);
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ItemRunnable that = (ItemRunnable) o;

            return _item.equals(that._item);

        }

        @Override
        public int hashCode()
        {
            return _item.hashCode();
        }

        @Override
        public String toString()
        {
            return "ItemRunnable of " + _item;
        }
    }


    public void addTask(IndexTask task)
    {
        _tasks.add(task);
    }


    @Override
    public List<IndexTask> getTasks()
    {
        return new LinkedList<>(_tasks);
    }


    @Override
    public void deleteResource(String id)
    {
        this.deleteDocument(id);
        synchronized (_commitLock)
        {
            _countIndexedSinceCommit++;
        }
    }

    @Override
    public void deleteResources(Collection<String> ids)
    {
        this.deleteDocuments(ids);
        synchronized (_commitLock)
        {
            _countIndexedSinceCommit += ids.size();
        }
    }


    @Override
    public void deleteResourcesForPrefix(String prefix)
    {
        this.deleteDocumentsForPrefix(prefix);
        synchronized (_commitLock)
        {
            _countIndexedSinceCommit++;
        }
    }


    // doesn't know about contextPath
    protected Path normalize(Path p)
    {
        Container c;
        if (p.size() > 1 && GUID.isGUID(p.get(1)) && null != (c = ContainerManager.getForId(p.get(1))))
        {
            return p.subpath(0,1).append(c.getParsedPath()).append(p.subpath(2, p.size()));
        }
        else if (p.size() > 2 && GUID.isGUID(p.get(2)) && null != (c = ContainerManager.getForId(p.get(2))))
        {
            return p.subpath(0,2).append(c.getParsedPath()).append(p.subpath(3, p.size()));
        }
        return p;
    }

    /** Modifies query parameters of URLHelper */
    protected Path canonicalize(URLHelper x)
    {
        x.deleteParameter("_print");
        x.deleteParameter("_docid");

        // we need to handle both path first, and controller first urls
        if (!(x instanceof ActionURL) && x.getPath().endsWith(".view"))
            x = new ActionURL(x.toString());

        return normalize(x.getParsedPath());
    }

    /** query parameters will be modified */
    public boolean _eq(URLHelper a, URLHelper b)
    {
        Path aPath = canonicalize(a);
        Path bPath = canonicalize(b);
        if (!aPath.equals(bPath))
                return false;
        return URLHelper.queryEqual(a,b);
    }

    @Override
    public void notFound(URLHelper in)
    {
        try
        {
            if (null == in)
                return;
            // UNDONE: add find to interface
            if (!(this instanceof LuceneSearchServiceImpl))
                return;
            String docid = in.getParameter("_docid");
            if (StringUtils.isEmpty(docid))
                return;
            SearchHit hit = find(docid);
            if (null == hit || null == hit.url)
                return;

            URLHelper url = in.clone();
            URLHelper expected = new URLHelper(hit.url);
            if (!_eq(url,expected))
                return;
            deleteResource(docid);

            if (docid.startsWith("dav:"))
            {
                Path path = Path.parse(docid.substring("dav:".length()));
                DavCrawler.getInstance().addPathToCrawl(path.getParent(), SavePaths.oldDate);
            }

            if (!StringUtils.isEmpty(hit.container))
            {
                Container c = ContainerManager.getForId(hit.container);
                if (null == c)
                    deleteContainer(hit.container);
            }
        }
        catch (Throwable x)
        {
            _log.error("Unexpected error", x);
        }
    }

    private final Map<String, ResourceResolver> _resolvers = new CopyOnWriteHashMap<>();

    @Override
    public void addResourceResolver(@NotNull String prefix, @NotNull ResourceResolver resolver)
    {
        _resolvers.put(prefix, resolver);
    }

    // CONSIDER Iterable<Resource>
    @Override
    @Nullable
    public WebdavResource resolveResource(@NotNull String resourceIdentifier)
    {
        int i = resourceIdentifier.indexOf(":");
        if (i == -1)
            return null;
        String prefix = resourceIdentifier.substring(0, i);
        ResourceResolver res = _resolvers.get(prefix);
        if (null == res)
            return null;
        return res.resolve(resourceIdentifier.substring(i+1));
    }

    @Override
    public HttpView<?> getCustomSearchResult(User user, @NotNull String resourceIdentifier)
    {
        int i = resourceIdentifier.indexOf(":");
        if (i == -1)
            return null;
        String prefix = resourceIdentifier.substring(0, i);
        ResourceResolver res = _resolvers.get(prefix);
        if (null == res)
            return null;
        return res.getCustomSearchResult(user, resourceIdentifier.substring(i+1));
    }

    private Pair<String, String> getResourceResolverKeyIdentifier(@NotNull String resourceIdentifier)
    {
        int i = resourceIdentifier.indexOf(":");
        if (i == -1)
            return null;
        String prefix = resourceIdentifier.substring(0, i);

        if (_resolvers.containsKey(prefix))
            return new Pair<>(prefix, resourceIdentifier.substring(i+1));

        // Special case to allow customizing docs whose docId does not starting with category.
        // For example, assay design doc id is of "containerId:assays:rowId"
        if (resourceIdentifier.matches(".+[:].+[:].+"))
        {
            String[] idParts = resourceIdentifier.split(":");
            String resolverName = idParts[1];
            if (_resolvers.containsKey(resolverName))
                return new Pair<>(resolverName, idParts[2]);
        }

        return null;
    }

    @Override
    public Map<String, Object> getCustomSearchJson(User user, @NotNull String resourceIdentifier)
    {
        Pair<String, String> resourceResolverKeyIdentifier = getResourceResolverKeyIdentifier(resourceIdentifier);
        if (resourceResolverKeyIdentifier != null)
        {
            ResourceResolver res = _resolvers.get(resourceResolverKeyIdentifier.first);
            if (res == null)
                return null;

            return res.getCustomSearchJson(user, resourceResolverKeyIdentifier.second);
        }

        return null;
    }

    @Override
    public Map<String, Map<String, Object>> getCustomSearchJsonMap(User user, @NotNull Collection<String> resourceIdentifiers)
    {
        Map<String, Map<String, Object>> jsonMap = new HashMap<>();
        Map<String, Map<String/*short identifier*/, String/*full identifier*/>> resolverIdentifiers = new HashMap<>();
        for (String resourceIdentifier : resourceIdentifiers)
        {
            Pair<String, String> resourceResolverKeyIdentifier = getResourceResolverKeyIdentifier(resourceIdentifier);
            if (resourceResolverKeyIdentifier == null)
                continue;;
            resolverIdentifiers
                    .computeIfAbsent(resourceResolverKeyIdentifier.first, (k) -> new HashMap<>())
                    .put(resourceResolverKeyIdentifier.second, resourceIdentifier);
        }

        for (String resolver : resolverIdentifiers.keySet())
        {
            ResourceResolver res = _resolvers.get(resolver);
            if (res != null)
            {
                Map<String, String> identifiersMap = resolverIdentifiers.get(resolver);
                Set<String> shortIdentifiers = identifiersMap.keySet();
                Map<String, Map<String, Object>> searchJsonMap = res.getCustomSearchJsonMap(user, shortIdentifiers);

                for (String shortIdentifier : searchJsonMap.keySet())
                    jsonMap.put(identifiersMap.get(shortIdentifier), searchJsonMap.get(shortIdentifier));
            }
        }

        return jsonMap;
    }

    private final List<SearchResultTemplate> _templates = new CopyOnWriteArrayList<>();
    private final SearchResultTemplate _defaultTemplate = new DefaultSearchResultTemplate();

    @Override
    public void addSearchResultTemplate(@NotNull SearchResultTemplate template)
    {
        _templates.add(template);
    }

    @Override
    public SearchResultTemplate getSearchResultTemplate(@Nullable String name)
    {
        if (null != name)
        {
            for (SearchResultTemplate template : _templates)
                if (StringUtils.equalsIgnoreCase(name, template.getName()))
                    return template;
        }

        // Template was null or unrecognized, so return the default
        return _defaultTemplate;
    }

    final Object _runningLock = new Object();
    boolean _threadsInitialized = false;
    volatile boolean _shuttingDown = false;
    boolean _crawlerPaused = true;
    ArrayList<Thread> _threads = new ArrayList<>(10);


    @Override
    public void start()
    {
        synchronized (_runningLock)
        {
            startThreads();

            if (SearchPropertyManager.getCrawlerRunningState())
                startCrawler();
        }
    }


    @Override
    public void startCrawler()
    {
        synchronized (_runningLock)
        {
            _crawlerPaused = false;
            DavCrawler.getInstance().addPathToCrawl(WebdavService.getPath(), null);
            _runningLock.notifyAll();
        }
    }

    /** OK we're really only pausing the crawler */
    @Override
    public void pauseCrawler()
    {
        synchronized (_runningLock)
        {
            _crawlerPaused = true;
        }
    }


    @Override
    public void updateIndex()
    {
        // Subclasses should switch out the index at this point.
    }

    @Override
    public @Nullable Throwable getConfigurationError()
    {
        return _configurationError;
    }

    void setConfigurationError(@Nullable Throwable t)
    {
        _configurationError = t;
    }

    @Override
    public boolean isRunning()
    {
        return !_crawlerPaused;
    }


    /** this is for testing, and memcheck only! */
    @Override
    public void purgeQueues()
    {
        _defaultTask._subtasks.clear();
        for (IndexTask t : getTasks())
        {
            t.cancel(true);
            ((AbstractIndexTask)t)._subtasks.clear();
        }
        _runQueue.clear();
        _itemQueue.clear();
    }


    /** return false if returning because of shutting down */
    boolean waitForRunning()
    {
        synchronized (_runningLock)
        {
            while (_crawlerPaused && !_shuttingDown)
            {
                try
                {
                    _runningLock.wait();
                }
                catch (InterruptedException ignored) {}
            }
            return !_shuttingDown;
        }
    }

    protected int getCountIndexingThreads()
    {
        int cpu = Runtime.getRuntime().availableProcessors();
        return Math.max(1,cpu/4);
    }

    protected void startThreads()
    {
        assert Thread.holdsLock(_runningLock);
        if (_shuttingDown)
            return;
        if (_threadsInitialized)
            return;

        int countIndexingThreads = Math.max(1, getCountIndexingThreads());
        for (int i = 0; i < countIndexingThreads; i++)
        {
            startThread(new Thread(indexRunnable, "SearchService:index"));
        }

        startThread(new Thread(runRunnable, "SearchService:runner"));

        _threadsInitialized = true;

        ContextListener.addShutdownListener(this);
    }
    
    private void startThread(Thread t)
    {
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY + 1);
        t.start();
        _threads.add(t);
    }

    @Override
    public String getName()
    {
        return "Search service";
    }

    @Override
    public void shutdownPre()
    {
        _shuttingDown = true;
        _crawlerPaused = true;
        _runQueue.clear();
        _itemQueue.clear();
        for (Thread t : _threads)
            t.interrupt();
    }


    @Override
    public void shutdownStarted()
    {
        try
        {
            for (Thread t : _threads)
                t.join(1000);
        }
        catch (InterruptedException ignored) {}
        shutDown();
    }


    Runnable runRunnable = () -> {
        while (!_shuttingDown)
        {
            Item i = null;
            boolean success = true;

            try
            {
// UNDONE: only pause the crawler for now, don't want to worry about the queue growing unchecked
//                    if (!waitForRunning())
//                        continue;

                i = _runQueue.poll(30, TimeUnit.SECONDS);

                if (null != i)
                {
                    while (!_shuttingDown && _itemQueue.size() > 1000)
                    {
                        try {Thread.sleep(100);}catch(InterruptedException ignored){}
                    }
                    i._run.run();
                }
            }
            catch (InterruptedException ignored) {}
            catch (Throwable x)
            {
                success = false;
                try
                {
                    ExceptionUtil.logExceptionToMothership(null, x);
                }
                catch (Throwable t)
                {
                    /* */
                }
                _log.error("Error running " + (null != i ? i._id : ""), x);
            }
            finally
            {
                if (null != i)
                {
                    try
                    {
                        i.complete(success);
                    }
                    catch (Throwable t)
                    {
                        _log.error("Unexpected error", t);
                    }
                }
            }
        }
    };
    
    Item getItemToIndex() throws InterruptedException
    {
        Item i = null;

        try
        {
            i = _itemQueue.poll();
            if (null == i || i == _commitItem)
                checkIdle();
            if (null == i)
                i = _itemQueue.poll(2, TimeUnit.SECONDS);

            return i;
        }
        catch (RuntimeSQLException x)
        {
            if (SqlDialect.isTransactionException(x))
            {
                if (null != i && ++i._preprocessAttempts <= 3)
                    _itemQueue.put(i);
                return null;
            }
            throw x;
        }
    }
    

    final Object _commitLock = new Object(){ public String toString() { return "COMMIT LOCK"; } };
    int _countIndexedSinceCommit = 0;
    long _lastIndexedTime = 0;


    public final void commit()
    {
        synchronized (_commitLock)
        {
            commitIndex();
            _countIndexedSinceCommit = 0;
        }
    }


    Runnable indexRunnable = () ->
    {
        while (!_shuttingDown)
        {
            try
            {
                _indexLoop();
            }
            catch (Throwable t)
            {
                // this should only happen if the catch/finally of the inner loop throws
                try {_log.warn("error in indexer", t);} catch (Throwable x){/* */}
            }
        }
        synchronized (_commitLock)
        {
            if (_countIndexedSinceCommit > 0)
            {
                commit();
                _countIndexedSinceCommit = 0;
            }
        }
    };

    private void commitCheck(long ms)
    {
        synchronized (_commitLock)
        {
            // TODO: Add a check that allows commit in the case where we've been continuously pounding the indexer for a long time...
            // either an oldest non-committed document check or a simple _countIndexedSinceCommit > n check.
            if (_countIndexedSinceCommit > 0 && _lastIndexedTime + 2000 < ms && _runQueue.isEmpty())
            {
                commit();
            }
        }
    }


    private void _indexLoop()
    {
        Item i = null;
        boolean success = false;
        try
        {
            i = getItemToIndex();
            long ms = HeartBeat.currentTimeMillis();

            //TODO: _commitItem is never enqueued should this case be removed?
            if (null == i || _commitItem == i)
            {
                commitCheck(ms);
                success = true;
                return;
            }

            if (i.getOperation() == OPERATION.noop)
            {
                success = true;
                return;
            }

            WebdavResource r = i.getResource();
            if (null == r || !r.exists())
            {
                _log.info("Document no longer exist, skipping: " + i._id);
                // This is a strange case.  If this resource doesn't exist anymore, it is not really an error.
                // see 34102: Search indexing is unreliable for wiki attachments
                i.complete(true);
                commitCheck(ms);
                success = true;
                return;
            }

            i._modified = r.getLastModified();

            MemTracker.getInstance().put(r);
            _log.debug("processAndIndex(" + i._id + ")");

            Throwable[] out = new Throwable[] {null};

            success = processAndIndex(i._id, i._res, out);

            if (null != out[0])
            {
                _IndexTask t = (_IndexTask)i._task;
                if (null != t && null != t._listener)
                    t._listener.indexError(r,out[0]);
            }

            if (success)
            {
                // On a fast machine, _start could be less than _modified, since _start is set via HeartBeat. However,
                // we don't ever want to set LastIndexed to a timestamp less than Modified, otherwise we'll end up
                // reindexing this doc on the next pass.
                i._res.setLastIndexed(Math.max(i._start, i._modified), i._modified);
                synchronized (_commitLock)
                {
                    String category = (String)i.getResource().getProperties().get(PROPERTY.categories.toString());
                    incrementIndexStat(ms, category);
                    _countIndexedSinceCommit++;
                    _lastIndexedTime = ms;
                    if (_countIndexedSinceCommit > 10000)
                        commit();
                    Logger categoryLogger = getLoggerForCategory(category);
                    if (categoryLogger.isDebugEnabled())
                    {
                        String containerId = i._res.getContainerId();
                        Container c = ContainerManager.getForId(containerId);
                        String containerPath = c != null ? c.getPath() : "UNKNOWN PATH: Container not found!";
                        categoryLogger.debug(category + " " + i._res.getDocumentId() + " " + containerPath + " " + containerId);
                    }
                }
            }
            else
                _log.debug("skipping " + i._id);
        }
        catch (InterruptedException ignored) {}
        catch (Throwable x)
        {
            _log.error("Error indexing " + (null != i ? i._id : ""), x);
        }
        finally
        {
            try
            {
                if (null != i)
                    i.complete(success);
            }
            finally
            {
                DbScope.closeAllConnectionsForCurrentThread();
            }
        }
    }


    private final ArrayList<SearchCategory> _searchCategories = new ArrayList<>();
    private final Object _categoriesLock = new Object();

    private List<SearchCategory> _readonlyCategories = Collections.emptyList();


    @Override
    public DbSchema getSchema()
    {
        return SearchSchema.getInstance().getSchema();
    }


    @Override
    public List<SearchCategory> getSearchCategories()
    {
        synchronized (_categoriesLock)
        {
            return _readonlyCategories;
        }
    }


    @Override
    public void addSearchCategory(SearchCategory category)
    {
        synchronized (_categoriesLock)
        {
            _searchCategories.add(category);
            SearchCategory[] arr = _searchCategories.toArray(new SearchCategory[0]);
            _readonlyCategories = List.of(arr);
        }
    }

    @Override
    public List<SearchCategory> getCategories(String categories)
    {
        if (categories == null)
            return null;
        
        List<SearchCategory> cats = _readonlyCategories;
        List<SearchCategory> usedCats = new ArrayList<>();
        String[] requestedCats = categories.split("\\+");
        for (SearchCategory cat : cats)
        {
            for (String usedCat : requestedCats)
            {
                if (usedCat.equalsIgnoreCase(cat.toString()))
                    usedCats.add(cat);
            }
        }

        if (!usedCats.isEmpty())
        {
            return usedCats;
        }
        return null;
    }


    protected abstract void commitIndex();
    protected abstract void deleteDocument(String id);
    protected abstract void deleteDocuments(Collection<String> ids);
    protected abstract void deleteDocumentsForPrefix(String prefix);
    protected abstract void deleteIndexedContainer(String id);

    /**
     * Delete the index documents for file system files within a container
     * @param container target container
     */
    protected abstract void clearIndexedFileSystemFiles(Container container);

    protected abstract void shutDown();

    public boolean processAndIndex(String id, WebdavResource r, Throwable[] handledException)
    {
        return false;
    }

    protected final List<DocumentProvider> _documentProviders = new CopyOnWriteArrayList<>();

    @Override
    public void addDocumentProvider(DocumentProvider provider)
    {
        _documentProviders.add(provider);
    }

    protected final List<DocumentParser> _documentParsers = new CopyOnWriteArrayList<>();

    @Override
    public void addDocumentParser(DocumentParser parser)
    {
        _documentParsers.add(parser);
    }

    @Override
    public IndexTask indexContainer(IndexTask in, final Container c, final Date since)
    {
        _log.debug("Indexing container \"" + c + "\", since: " + since);
        final IndexTask task = null==in ? createTask("Index folder " + c.getPath()) : in;

        Runnable r = () ->
        {
            for (DocumentProvider p : _documentProviders)
            {
                p.enumerateDocuments(task, c, since);
            }
        };
        task.addRunnable(r, PRIORITY.bulk); // breaks rule of always adding w/higher priority than parent task
        if (null == in)
            task.setReady();
        return task;
    }


    // TODO: Remove? This is never called
    // UNDONE: get last crawl time from Crawler? support incrementals
    @Override
    public IndexTask indexProject(IndexTask in, final Container c)
    {
        final IndexTask task = null==in ? createTask("Index project " + c.getName()) : in;

        Runnable r = () -> {
            MultiValuedMap<Container, Container> mmap = ContainerManager.getContainerTree(c);
            Set<Container> set = new HashSet<>();
            for (Container key : mmap.keySet())
            {
                set.add(key);
                set.addAll(mmap.get(key));
            }
            for (Container i : set)
            {
                indexContainer(task, i, null);
            }
        };
        task.addRunnable(r, PRIORITY.bulk);
        if (null == in)
            task.setReady();
        return task;
    }


    //
    // full crawl
    // use deleteIndex() and indexFull() for full forced reindex
    //

    @Override
    public void indexFull(final boolean force, String reason)
    {
        _log.info("Initiating an aggressive full-text search reindex because: " + reason);
        // crank crawler into high gear!
        DavCrawler.getInstance().startFull(WebdavService.getPath(), force);
    }


    private final LinkedList<IndexerRateAccumulator> _history = new LinkedList<>();

    private IndexerRateAccumulator _current = new IndexerRateAccumulator(System.currentTimeMillis());
    private long _currentHour = _current.getStart() / DateUtils.MILLIS_PER_HOUR;

    // call when holding _commitLock
    private void incrementIndexStat(long now, String category)
    {
        assert Thread.holdsLock(_commitLock);
        long hour = now / DateUtils.MILLIS_PER_HOUR;
        if (hour > _currentHour)
        {
            _history.addFirst(_current);
            _current = new IndexerRateAccumulator(now);
            _currentHour = hour;
            while (_history.size() > 24)
                _history.removeLast();
        }
        _current.accumulate(category);
    }

    
    private static class IndexerRateAccumulator extends MultisetRateAccumulator<String>
    {
        public IndexerRateAccumulator(long start)
        {
            super(start);
        }

        public IndexerRateAccumulator(long start, Multiset<String> multiset)
        {
            super(start, multiset);
        }
    }


    public Map<String, Object> getIndexerStats()
    {
        Map<String, Object> map = new LinkedHashMap<>();
        ArrayList<IndexerRateAccumulator> history;
        long currentStart;

        synchronized (_commitLock)
        {
            history = new ArrayList<>(_history.size() + 1);
            currentStart = _current.getStart();
            history.add(new IndexerRateAccumulator(currentStart, HashMultiset.create(_current.getCounter())));
            history.addAll(_history);
        }

        IndexerRateAccumulator historyAccumulator = new IndexerRateAccumulator(history.get(history.size() - 1).getStart());
        SimpleDateFormat f = new SimpleDateFormat("h:mm a");
        StringBuilder hourly = new StringBuilder();
        hourly.append("<table>");
        int completedHours = 0;
        long currentHour = System.currentTimeMillis();
        currentHour -= currentHour % DateUtils.MILLIS_PER_HOUR; // Not the same as _currentHour if indexing hasn't yet taken place this hour

        for (IndexerRateAccumulator r : history)
        {
            long start = r.getStart();
            start -= start % DateUtils.MILLIS_PER_HOUR;
            String fStart = f.format(start);
            String fCount = Formats.commaf0.format(r.getCount());
            hourly.append("<tr><td align=right>").append(fStart).append("&nbsp;</td>");
            hourly.append("<td align=right>").append(fCount).append(getPopup(fStart + " " + StringUtilsLabKey.pluralize(r.getCount(), "document"), r)).append("</td></tr>");
            // Accumulate all history into a single counter, skipping the current hour's accumulator since it's incomplete
            if (start < currentHour)
            {
                r.getCounter().forEach(historyAccumulator::accumulate);
                completedHours++;
            }
        }

        hourly.append("</table>");

        // If we've accumulated multiple hours (usually 24) of history, display it as a single roll-up
        if (completedHours > 1)
        {
            String fCount = StringUtilsLabKey.pluralize(historyAccumulator.getCount(), "document");
            map.put("Indexing history added/updated (total for " + completedHours + " completed hours)", fCount + getPopup(fCount, historyAccumulator));
        }
        map.put("Indexing history added/updated (each hour)", hourly.toString());
        map.put("Maximum allowed document size", getFileSizeLimit());

        return map;
    }

    private HtmlString getPopup(String title, IndexerRateAccumulator r)
    {
        if (r.getCounter().isEmpty())
            return HtmlString.EMPTY_STRING;

        StringBuilder html = new StringBuilder();
        html.append("<table>\n");

        for (Entry<String> entry : r.getSortedEntries())
            html.append("<tr><td>").append(PageFlowUtil.filter(entry.getElement())).append("</td><td align=right>").append(Formats.commaf0.format(entry.getCount())).append("</td></tr>\n");

        html.append("</table>\n");

        return PageFlowUtil.popupHelp(HtmlString.unsafe(html.toString()), title).getHtmlString();
    }

    public abstract Map<String, Double> getSearchStats();

    @Override
    public void maintenance()
    {
        DbSchema search = getSchema();

        // TODO: Maintenance task to remove documents for participants that have been deleted

        SQLFragment delete = new SQLFragment(
        """
                DELETE FROM search.CrawlResources
                WHERE parent IN (
                  SELECT parent from search.CrawlResources
                  EXCEPT\s
                  SELECT id from search.crawlcollections
                )
                """);

        new SqlExecutor(search).execute(delete);
    }


    static
    {
        SystemMaintenance.addTask(new SearchServiceMaintenanceTask());
    }


    private static class SearchServiceMaintenanceTask implements MaintenanceTask
    {
        @Override
        public String getDescription()
        {
            return "Search Service Maintenance";
        }

        @Override
        public String getName()
        {
            return "SearchService";
        }

        @Override
        public void run(Logger log)
        {
            SearchService ss = SearchService.get();

            if (null != ss)
                ss.maintenance();
        }
    }


    @Override
    public long getFileSizeLimit()
    {
        return SearchPropertyManager.getFileSizeLimitMB() * (1024*1024);
    }
}

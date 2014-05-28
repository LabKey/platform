/*
 * Copyright (c) 2009-2014 LabKey Corporation
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

import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import org.apache.commons.collections15.MultiMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.annotations.JavaRuntimeVersion;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.search.SearchResultTemplate;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.Formats;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.MemTrackerListener;
import org.labkey.api.util.MultisetRateAccumulator;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.search.view.DefaultSearchResultTemplate;

import javax.servlet.ServletContextEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 * User: matthewb
 * Date: Nov 12, 2009
 * Time: 12:58:21 PM
 */
public abstract class AbstractSearchService implements SearchService, ShutdownListener
{
    private static final Logger _log = Logger.getLogger(AbstractSearchService.class);
    protected static final long FILE_SIZE_LIMIT = 100L*(1024*1024); // 100 MB

    // Runnables go here, and get pulled off in a single threaded manner (assumption is that Runnables can create work very quickly)
    final PriorityBlockingQueue<Item> _runQueue = new PriorityBlockingQueue<>(1000, itemCompare);

    // Resources go here for preprocessing (this can be multi-threaded)
    final PriorityBlockingQueue<Item> _itemQueue = new PriorityBlockingQueue<>(1000, itemCompare);

    // And a single threaded queue for actually writing to the index (can this be multi-threaded?)
    BlockingQueue<Item> _indexQueue = null;

    private final List<IndexTask> _tasks = new CopyOnWriteArrayList<>();
    private final _IndexTask _defaultTask = new _IndexTask("default");

    private Throwable _configurationError = null;

    enum OPERATION
    {
        add, delete
    }

    static final Comparator<Item> itemCompare = new Comparator<Item>()
    {
        public int compare(Item o1, Item o2)
        {
            return o1._pri.compareTo(o2._pri);
        }
    };


    @JavaRuntimeVersion
    public AbstractSearchService()
    {
        addSearchCategory(fileCategory);
        addSearchCategory(navigationCategory);

        // Hack to work around Java 7 PriorityBlockingQueue bug, http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=7161229
        // This has been fixed in Java 7u40. For now, continue implementing memtracker hack for JVM < 1.7.0_40.
        if (SystemUtils.IS_JAVA_1_7)
        {
            String version = SystemUtils.JAVA_VERSION;

            if (version.startsWith("1.7.0_"))
            {
                int minorVersion = Integer.valueOf(version.substring(6));

                if (minorVersion < 40)
                {
                    MemTracker.getInstance().register(new MemTrackerListener()
                    {
                        @Override
                        public void beforeReport(Set<Object> set)
                        {
                            // Add a no-op marker item to the queue to purge previously removed item that queue might be holding.
                            _itemQueue.put(_commitItem);
                            _runQueue.put(_commitItem);
                        }
                    });
                }
            }
        }
    }
    

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

    public IndexTask defaultTask()
    {
        return _defaultTask;
    }


    public boolean accept(WebdavResource r)
    {
        return true;
    }
    

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


        public void addRunnable(@NotNull Runnable r, @NotNull PRIORITY pri)
        {
            Item i = new Item(this, r, pri);
            this.addItem(i);
            queueItem(i);
        }
        

        public void addResource(@NotNull String identifier, PRIORITY pri)
        {
            Item i = new Item(this, OPERATION.add, identifier, null, pri);
            this.addItem(i);
            queueItem(i);
        }


        public void addResource(@NotNull WebdavResource r, PRIORITY pri)
        {
            Item i = new Item(this, OPERATION.add, r.getDocumentId(), r, pri);
            addItem(i);
            queueItem(i);
        }


        @Override
        public void completeItem(Object item, boolean success)
        {
            if (item instanceof Item)
                ((Item)item)._complete = HeartBeat.currentTimeMillis();
            super.completeItem(item, success);
        }

        
        protected boolean checkDone()
        {
            if (_isReady)
            {
                assert _subtasks.size() == 0;
                if (_tasks.remove(this))
                    return true;
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
    // hashCode() & equals() more straightfoward), formalize _id (using Runnable.toString() seems weak).
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

        Map<?, ?> _preprocessMap = null;
        
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
            if (_run != null ? !_run.equals(item._run) : item._run != null) return false;

            return true;
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
            return "Item{" + (null != _res ? _res.toString() : _run.toString()) + '}';
        }
    }


    final Item _commitItem = new Item(null, new Runnable(){
        @Override
        public void run()
        {
        }
    }, PRIORITY.commit);


    public boolean isBusy()
    {
        if (_runQueue.size() > 0)
            return true;
        int n = _itemQueue.size();
        if (null != _indexQueue)
            n += _indexQueue.size();
        return n > 100;
    }


    final Object _idleEvent = new Object();
    boolean _idleWaiting = false;


    public void waitForIdle() throws InterruptedException
    {
        if (_runQueue.size() == 0 && _itemQueue.size() < 4)
            return;
        synchronized (_idleEvent)
        {
            _idleWaiting = true;
            _idleEvent.wait();
        }
    }


    private void checkIdle()
    {
        if (_runQueue.size() == 0 && _itemQueue.size() == 0)
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

    

    SavePaths _savePaths = new SavePaths();

    public void setLastIndexedForPath(Path path, long time, long indexed)
    {
        _savePaths.updateFile(path, new Date(time), new Date(indexed));
    }


    public final void deleteContainer(final String id)
    {
        Runnable r = new Runnable(){
            public void run()
            {
                deleteIndexedContainer(id);
                synchronized (_commitLock)
                {
                    _countIndexedSinceCommit++;
                }
            }
        };
        queueItem(new Item(defaultTask(), r, PRIORITY.background));
    }


    public final void clear()
    {
        clearIndex();
        clearLastIndexed();
    }

    
    public final void clearLastIndexed()
    {
        for (DocumentProvider p : _documentProviders)
        {
            try
            {
                p.indexDeleted();
            }
            catch (Throwable t)
            {
                _log.error("Unexpected error", t);
            }
        }
        // CONSIDER: have DavCrawler implement DocumentProvider and listen for indexDeleted()
        DavCrawler.getInstance().clearFailedDocuments();

        DavCrawler.getInstance().startFull(WebdavService.getPath(), true);
    }

    
    private void queueItem(Item i)
    {
        // UNDONE: this is not 100% correct, consider passing in a scope with Item
        DbScope s = DbScope.getLabkeyScope();

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

            if (!_item.equals(that._item)) return false;

            return true;
        }

        @Override
        public int hashCode()
        {
            return _item.hashCode();
        }

        @Override
        public String toString()
        {
            return "ItemRunnable of " + _item.toString();
        }
    }


    public void addTask(IndexTask task)
    {
        _tasks.add(task);
    }


    public List<IndexTask> getTasks()
    {
        return new LinkedList<>(_tasks);
    }


    public void deleteResource(String id)
    {
        this.deleteDocument(id);
        synchronized (_commitLock)
        {
            _countIndexedSinceCommit++;
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


    // params will be modified
    public boolean _eq(URLHelper a, URLHelper b)
    {
        a.deleteParameter("_docid");
        a.deleteParameter("_print");
        b.deleteParameter("_docid");
        b.deleteParameter("_print");

        Path aPath = a instanceof ActionURL ? ((ActionURL)a).getFullParsedPath() : a.getParsedPath();
        Path bPath = b instanceof ActionURL ? ((ActionURL)b).getFullParsedPath() : b.getParsedPath();
        if (!aPath.equals(bPath))
        {
            // handle container ids
            aPath = normalize(aPath);
            bPath = normalize(bPath);
            if (!aPath.equals(bPath))
                return false;
        }
        return URLHelper.queryEqual(a,b);
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
            SearchHit hit = ((LuceneSearchServiceImpl)this).find(docid);
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


    Map<String, ResourceResolver> _resolvers = new ConcurrentHashMap<>();

    public void addResourceResolver(@NotNull String prefix, @NotNull ResourceResolver resolver)
    {
        _resolvers.put(prefix, resolver);
    }


    // CONSIDER Iterable<Resource>
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
    public void pauseCrawler()
    {
        synchronized (_runningLock)
        {
            _crawlerPaused = true;
        }
    }


    @Override
    public void updatePrimaryIndex()
    {
        // Subclasses should switch out the index at this point.
    }

    @Override
    public boolean hasExternalIndexPermission(User user)
    {
        return false;
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

    public boolean isRunning()
    {
        return !_crawlerPaused;
    }


    /** this is for testing, and memcheck only! */
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
        if (null != _indexQueue)
            _indexQueue.clear();
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
                catch (InterruptedException x)
                {
                }
            }
            return !_shuttingDown;
        }
    }

    
    protected int getCountPreprocessorThreads()
    {
        return isPreprocessThreadSafe() ? 1 : 0;
    }

    
    protected int getCountIndexingThreads()
    {
        int cpu = Runtime.getRuntime().availableProcessors();
        return Math.max(1,cpu/4);
    }


    protected boolean isPreprocessThreadSafe()
    {
        return true;
    }


    protected void startThreads()
    {
        assert Thread.holdsLock(_runningLock);
        if (_shuttingDown)
            return;
        if (_threadsInitialized)
            return;

        ThreadGroup group = new ThreadGroup("SearchService");
        group.setDaemon(true);
        group.setMaxPriority(Thread.MIN_PRIORITY + 1);
        
        int countIndexingThreads = Math.max(1,getCountIndexingThreads());
        for (int i=0 ; i<countIndexingThreads ; i++)
        {
            Thread t = new Thread(group, indexRunnable, "SearchService:index");
            t.start();
            _threads.add(t);
        }

        int countPreprocessingThreads = getCountPreprocessorThreads();
        for (int i=0 ; i<getCountPreprocessorThreads() ; i++)
        {
            Thread t = new Thread(group, preprocessRunnable, "SearchService:preprocess " + i);
            t.start();
            _threads.add(t);
        }

        {
            Thread t = new Thread(group, runRunnable, "SearchService:runner");
            t.start();
            _threads.add(t);
        }

        if (0 < countPreprocessingThreads)
            _indexQueue = new ArrayBlockingQueue<>(Math.min(100,10*countIndexingThreads));

        _threadsInitialized = true;

        ContextListener.addShutdownListener(this);
    }


    public void shutdownPre(ServletContextEvent servletContextEvent)
    {
        _shuttingDown = true;
        _crawlerPaused = true;
        _runQueue.clear();
        _itemQueue.clear();
        if (null != _indexQueue)
            _indexQueue.clear();
        for (Thread t : _threads)
            t.interrupt();
    }


    public void shutdownStarted(ServletContextEvent servletContextEvent)
    {
        try
        {
            for (Thread t : _threads)
                t.join(1000);
        }
        catch (InterruptedException e) {}
        shutDown();
    }


    Runnable runRunnable = new Runnable()
    {
        public void run()
        {
            while (!_shuttingDown)
            {
                Item i = null;
                boolean success = false;

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
                            try {Thread.sleep(100);}catch(InterruptedException x){}
                        }
                        i._run.run();
                    }
                }
                catch (InterruptedException x)
                {
                }
                catch (Throwable x)
                {
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
        }
    };


    boolean _lock(Lock s)
    {
        while (null != s)
        {
            try
            {
                s.lockInterruptibly();
                return true;
            }
            catch (InterruptedException x)
            {
                if (_shuttingDown)
                    return false;
            }
        }
        return true;
    }
    

    void _unlock(Lock s)
    {
        if (null != s)
            s.unlock();
    }
    


    ReentrantLock lockPreprocess = isPreprocessThreadSafe() ? null : new ReentrantLock();

    private boolean preprocess(Item i)
    {
        if (_commitItem == i)
            return true;

        WebdavResource r = i.getResource();
        if (null == r || !r.exists())
            return false;
        
        i._modified = r.getLastModified();

        if (!_lock(lockPreprocess))
            return false;

        try
        {
            Throwable[] out = new Throwable[] {null};
            _log.debug("preprocess(" + r.getDocumentId() + ")");

            /** TESTING */
            assert null==(out[0]="test_preprocess_exception".equals(r.getName())?new IllegalArgumentException("testing"):null) || true;

            if (null == out[0])
                i._preprocessMap = preprocess(i._id, i._res, out);

            if (null != out[0])
            {
                _IndexTask t = (_IndexTask)i._task;
                if (null != t && null != t._listener)
                    t._listener.indexError(r,out[0]);
            }
            if (null == i._preprocessMap)
            {
                _log.debug("skipping " + i._id);
                return false;
            }
            return true;
        }
        finally
        {
            _unlock(lockPreprocess);
        }
    }


    Runnable preprocessRunnable = new Runnable()
    {
        public void run()
        {
            while (!_shuttingDown)
            {
                Item i = null;
                boolean success = false;
                try
                {
                    i = _itemQueue.take();
                        if (!preprocess(i))
                            continue;
                    _log.debug("_indexQueue.put(" + i._id + ")");
                    _indexQueue.put(i);
                    success = true;
                }
                catch (RuntimeSQLException x)
                {
                    if (SqlDialect.isTransactionException(x))
                    {
                        if (null != i && ++i._preprocessAttempts <= 3)
                        {
                            _itemQueue.put(i);
                            continue;
                        }
                    }
                    _log.error("Error processing " + (null != i ? i._id : ""), x);
                }
                catch (InterruptedException x)
                {
                    // check _shuttingDown
                }
                catch (Throwable x)
                {
                    _log.error("Error processing " + (null != i ? i._id : ""), x);
                }
                finally
                {
                    if (!success && null != i)
                    {
                        i.complete(success);
                    }
                }
            }
        }
    };


    Item getPreprocessedItem() throws InterruptedException
    {
        Item i = null;

        try
        {
            // if there's an indexQueue, other threads may be preprocessing
            // first look for a preprocessed item on the _indexQueue
            if (null != _indexQueue)
            {
                i = _indexQueue.poll();
                if (null != i)
                    return i;
                // help out on preprocessing?
                i = _itemQueue.poll();
                if (null != i)
                {
                    if (preprocess(i))
                        return i;
                    else
                        i.complete(false);
                }
                checkIdle();
                return _indexQueue.poll(2, TimeUnit.SECONDS);
            }

            // otherwise just wait on the preprocess queue
            i = _itemQueue.poll();
            if (null == i || i == _commitItem)
                checkIdle();
            if (null == i)
                i = _itemQueue.poll(2, TimeUnit.SECONDS);

            if (null != i)
            {
                if (preprocess(i))
                    return i;
                else
                    i.complete(false);
            }
            return null;
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


    Runnable indexRunnable = new Runnable()
    {
        public void run()
        {
            while (!_shuttingDown)
            {
//                if (!waitForRunning())
//                    continue;
                Item i = null;
                boolean success = false;
                try
                {
                    i = getPreprocessedItem();
                    long ms = HeartBeat.currentTimeMillis();

                    if (null == i || _commitItem == i)
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
                        continue;
                    }

                    WebdavResource r = i.getResource();
                    if (null == r || !r.exists())
                        continue;
                    MemTracker.getInstance().put(r);
                    _log.debug("index(" + i._id + ")");

                    if (index(i._id, i._res, i._preprocessMap))
                    {
                        i._res.setLastIndexed(i._start, i._modified);
                        success = true;
                        synchronized (_commitLock)
                        {
                            String category = (String)i.getResource().getProperties().get(PROPERTY.categories.toString());
                            incrementIndexStat(ms, category);
                            _countIndexedSinceCommit++;
                            _lastIndexedTime = ms;
                            if (_countIndexedSinceCommit > 10000)
                                commit();
                        }
                    }
                }
                catch (InterruptedException x)
                {
                }
                catch (Throwable x)
                {
                    _log.error("Error indexing " + (null != i ? i._id : ""), x);
                }
                finally
                {
                    if (null != i)
                        i.complete(success);
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
        }
    };


    private final ArrayList<SearchCategory> _searchCategories = new ArrayList<>();
    private final Object _categoriesLock = new Object();

    private List<SearchCategory> _readonlyCategories = Collections.emptyList();


    public DbSchema getSchema()
    {
        return DbSchema.get("search");
    }


    public List<SearchCategory> getSearchCategories()
    {
        synchronized (_categoriesLock)
        {
            return _readonlyCategories;
        }
    }


    public void addSearchCategory(SearchCategory category)
    {
        synchronized (_categoriesLock)
        {
            _searchCategories.add(category);
            SearchCategory[] arr = _searchCategories.toArray(new SearchCategory[_searchCategories.size()]);
            _readonlyCategories = Collections.unmodifiableList(Arrays.asList(arr));
        }
    }

    public List<SearchCategory> getCategories(String categories)
    {
        if (categories == null)
            return null;
        
        List<SearchCategory> cats = _readonlyCategories;
        List<SearchCategory> usedCats = new ArrayList<>();
        List<String> requestedCats = Arrays.asList(categories.split(" "));
        for (SearchCategory cat : cats)
        {
            for (String usedCat : requestedCats)
            {
                if (usedCat.equalsIgnoreCase(cat.toString()))
                    usedCats.add(cat);
            }
        }

        if (usedCats.size() > 0)
        {
            return usedCats;
        }
        return null;
    }


    // Returns true if indexing was successful
    protected abstract boolean index(String id, WebdavResource r, Map preprocessProps);
    protected abstract void commitIndex();
    protected abstract void deleteDocument(String id);
    protected abstract void deleteDocumentsForPrefix(String prefix);
    protected abstract void deleteIndexedContainer(String id);
    protected abstract void shutDown();
    protected abstract void clearIndex();


    static Map emptyMap = Collections.emptyMap();
    
    Map<?,?> preprocess(String id, WebdavResource r, Throwable[] handledException)
    {
        return emptyMap;
    }


    protected final List<DocumentProvider> _documentProviders = new CopyOnWriteArrayList<>();
    
    public void addDocumentProvider(DocumentProvider provider)
    {
        _documentProviders.add(provider);
    }


    protected final List<DocumentParser> _documentParsers = new CopyOnWriteArrayList<>();
    
    public void addDocumentParser(DocumentParser parser)
    {
        _documentParsers.add(parser);
    }

    public IndexTask indexContainer(IndexTask in, final Container c, final Date since)
    {
        final IndexTask task = null==in ? createTask("Index folder " + c.getPath()) : in;

        Runnable r = new Runnable()
        {
            public void run()
            {
                for (DocumentProvider p : _documentProviders)
                {
                    p.enumerateDocuments(task, c, since);
                }
            }
        };
        task.addRunnable(r, PRIORITY.bulk); // breaks rule of always adding w/higher priority than parent task
        if (null == in)
            task.setReady();
        return task;
    }


    // UNDONE: get last crawl time from Crawler? support incrementsl
    public IndexTask indexProject(IndexTask in, final Container c)
    {
        final IndexTask task = null==in ? createTask("Index project " + c.getName()) : in;

        Runnable r = new Runnable()
        {
            public void run()
            {
                MultiMap<Container,Container> mmap = ContainerManager.getContainerTree(c);
                Set<Container> set = new HashSet<>();
                for (Container key : mmap.keySet())
                {
                    set.add(key);
                    for (Container v : mmap.get(key))
                        set.add(v);
                }
                for (Container i : set)
                {
                    indexContainer(task, i, null);
                }
            }
        };
        task.addRunnable(r, PRIORITY.bulk);
        if (null == in)
            task.setReady();
        return task;
    }


    //
    // full crawl
    // use clear() and indexFull() for full forced reindex
    //
    
    public void indexFull(final boolean force)
    {
//        final IndexTask task = createTask("Full reindex");
//        Runnable r = new Runnable()
//        {
//            public void run()
//            {
//                DocumentProvider[] documentProviders = _documentProviders.get();
//
//                if (force)
//                {
//                    for (DocumentProvider p : documentProviders)
//                    {
//                        try
//                        {
//                            p.indexDeleted();
//                        }
//                        catch (SQLException x)
//                        {
//                            _log.error("Unexpected exception", x);
//                        }
//                    }
//                }
//
//                for (DocumentProvider p : documentProviders)
//                {
//                    p.enumerateDocuments(task, null, null);
//                }
//            }
//        };

        // crank crawler into high gear!
        DavCrawler.getInstance().startFull(WebdavService.getPath(), force);
    }


    private final LinkedList<IndexerRateAccumulator> _history = new LinkedList<>();

    private IndexerRateAccumulator _current = new IndexerRateAccumulator(System.currentTimeMillis());
    private long _currentHour = _current.getStart() / (60*60*1000L);

    // call when holding _commitLock
    private void incrementIndexStat(long now, String type)
    {
        assert Thread.holdsLock(_commitLock);
        long hour = now / (60*60*1000L);
        if (hour > _currentHour)
        {
            _history.addFirst(_current);
            _current = new IndexerRateAccumulator(now);
            _currentHour = hour;
            while (_history.size() > 24)
                _history.removeLast();
        }
        _current.accumulate(type);
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
        HashMap<String, Object> map = new HashMap<>();

        ArrayList<IndexerRateAccumulator> history;

        synchronized (_commitLock)
        {
            history = new ArrayList<>(_history.size() + 1);
            history.add(new IndexerRateAccumulator(_current.getStart(), _current.getCounter()));
            history.addAll(_history);
        }

        SimpleDateFormat f = new SimpleDateFormat("h:mm a");
        StringBuilder sb = new StringBuilder();
        sb.append("<table>");

        for (IndexerRateAccumulator r : history)
        {
            long start = r.getStart();
            start -= start % (60*60*1000);
            String fStart = f.format(start);
            String fCount = Formats.commaf0.format(r.getCount());
            sb.append("<tr><td align=right>").append(fStart).append("&nbsp;</td>");
            sb.append("<td align=right>").append(fCount).append(getPopup(fStart + " " + fCount + " documents", r)).append("</td></tr>");
        }

        sb.append("</table>");
        map.put("Indexing history added/updated", sb.toString());
        map.put("Maximum allowed document size", FILE_SIZE_LIMIT);

        return map;
    }
    

    private String getPopup(String title, IndexerRateAccumulator r)
    {
        if (r.getCounter().isEmpty())
            return "";

        StringBuilder html = new StringBuilder();
        html.append("<table>\n");

        for (Entry<String> entry : r.getSortedEntries())
            html.append("<tr><td>").append(PageFlowUtil.filter(entry.getElement())).append("</td><td align=right>").append(Formats.commaf0.format(entry.getCount())).append("</td></tr>\n");

        html.append("</table>\n");

        return PageFlowUtil.helpPopup(title, html.toString(), true);
    }


    public abstract Map<String, Double> getSearchStats();

    public void maintenance()
    {
        DbSchema search = getSchema();

        // TODO: Maintenance task to remove documents for participants that have been deleted

        new SqlExecutor(search).execute("DELETE FROM search.CrawlResources WHERE parent NOT IN (SELECT id FROM search.CrawlCollections)");
//            if (search.getSqlDialect().isPostgreSQL())
//            {
//                Table.execute(search, "CLUSTER search.CrawlResources");
//            }
    }


    static
    {
        SystemMaintenance.addTask(new SearchServiceMaintenanceTask());
    }


    private static class SearchServiceMaintenanceTask implements SystemMaintenance.MaintenanceTask
    {
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
        public boolean canDisable()
        {
            return true;
        }

        @Override
        public boolean hideFromAdminPage() { return false; }

        public void run()
        {
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);

            if (null != ss)
                ss.maintenance();
        }
    }


    @Override
    public List<SecurableResource> getSecurableResources(User user)
    {
        return Collections.emptyList();
    }
}

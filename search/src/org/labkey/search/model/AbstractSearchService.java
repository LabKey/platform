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
package org.labkey.search.model;

import org.apache.log4j.Category;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.search.SearchService;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.view.ActionURL;
import org.labkey.api.webdav.Resource;
import org.labkey.api.webdav.ActionResource;

import javax.servlet.ServletContextEvent;
import java.util.*;
import java.util.concurrent.*;


/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Nov 12, 2009
 * Time: 12:58:21 PM
 */
public abstract class AbstractSearchService implements SearchService, ShutdownListener
{
    final static Category _log = Category.getInstance(AbstractSearchService.class);

    // Runnables go here, and get pulled off in a single threaded manner (assumption is that Runnables can create work very quickly)
    PriorityBlockingQueue<Item> _runQueue = new PriorityBlockingQueue<Item>(1000, itemCompare);

    // Resources go here for preprocessing (this can be multi-threaded)
    PriorityBlockingQueue<Item> _itemQueue = new PriorityBlockingQueue<Item>(1000, itemCompare);

    // And a single threaded queue for actually writing to the index (can this be multi-threaded?)
    BlockingQueue<Item> _indexQueue = new ArrayBlockingQueue<Item>(Math.min(100,Runtime.getRuntime().availableProcessors()));

    final List<IndexTask> _tasks = Collections.synchronizedList(new ArrayList<IndexTask>());

    final _IndexTask _defaultTask = new _IndexTask("default");


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


    public IndexTask createTask(String description)
    {
        _IndexTask task = new _IndexTask(description);
        addTask(task);
        return task;
    }
    

    public IndexTask defaultTask()
    {
        return _defaultTask;
    }
    

    class _IndexTask extends AbstractIndexTask
    {
        _IndexTask(String description)
        {
            super(description);
        }


        public void addRunnable(@NotNull Runnable r, @NotNull PRIORITY pri)
        {
            Item i = new Item(this, r, pri);
            this.addItem(i);
            queueItem(i);
        }
        

        public void addResource(@NotNull String identifier, PRIORITY pri)
        {
            addResource(identifier, null, pri);
        }


        public void addResource(@NotNull SearchCategory category, ActionURL url, PRIORITY pri)
        {
            addResource(new ActionResource(category, url), pri);
        }


        public void addResource(String identifier, Resource r, PRIORITY pri)
        {
            Item i = new Item(this, OPERATION.add, identifier, r, pri);
            this.addItem(i);
            queueItem(i);
        }


        public void addResource(@NotNull Resource r, PRIORITY pri)
        {
            Item i = new Item(this, OPERATION.add, r.getName(), r, pri);
            addItem(i);
            queueItem(i);
        }


        @Override
        public void completeItem(Object item, boolean success)
        {
            super.completeItem(item, success);
        }

        
        protected boolean checkDone()
        {
            if (_isReady && _subtasks.size() == 0)
            {
                if (_tasks.remove(this))
                {
                    return true;
                }
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

    
    class Item
    {
        OPERATION _op;
        String _id;
        IndexTask _task;
        Resource _res;
        Runnable _run;
        PRIORITY _pri;
        long _start = 0;
        Map<?,?> _preprocessMap = null;
        
        Item(IndexTask task, OPERATION op, String id, Resource r, PRIORITY pri)
        {
            if (null != r)
                _start = System.currentTimeMillis();
            _op = op; _id = id; _res = r;
            _pri = null == pri ? PRIORITY.bulk : pri;
            _task = task;
        }

        Item(IndexTask task, Runnable r, PRIORITY pri)
        {
            _run = r;
            _pri = null == pri ? PRIORITY.bulk : pri;
            _task = task;
        }

        Resource getResource()
        {
            if (null == _res)
            {
                _start = System.currentTimeMillis();
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
        }
    }


    public void start()
    {
        startThreads();
//        _crawler.startContinuous(this, new Path(WebdavService.getServletPath()));
//        _crawler.startFull(this.defaultTask(), new Path(WebdavService.getServletPath()), null);
    }


    public boolean isBusy()
    {
        int n = _itemQueue.size() + _indexQueue.size() + 10 * _runQueue.size();
        return n > 1000;
    }
    

    private void queueItem(Item i)
    {
        assert MemTracker.put(i);

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


    public void addTask(IndexTask task)
    {
        _tasks.add(task);
    }


    public List<IndexTask> getTasks()
    {
        IndexTask[] arr = _tasks.toArray(new IndexTask[_tasks.size()]);
        return Arrays.asList(arr);
    }


    public void deleteResource(String identifier, PRIORITY pri)
    {
        Item i = new Item(null, OPERATION.delete, identifier, null, pri);
        // don't need to preprocess so try to put in the indexQueue
        // if it's full then put in the itemQueue
        if (_indexQueue.offer(i))
            return;
        _itemQueue.put(i);
    }


    Map<String, ResourceResolver> _resolvers = Collections.synchronizedMap(new HashMap<String,ResourceResolver>());

    public void addResourceResolver(@NotNull String prefix, @NotNull ResourceResolver resolver)
    {
        _resolvers.put(prefix, resolver);
    }


    // CONSIDER Iterable<Resource>
    @Nullable
    public Resource resolveResource(@NotNull String resourceIdentifier)
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


    protected int getCountPreprocessorThreads()
    {
        return 0;
    }

    protected int getCountIndexingThreads()
    {
        return 1;
    }


    void startThreads()
    {
        if (_shuttingDown)
            return;

        for (int i=0 ; i<Math.max(1, getCountIndexingThreads()) ; i++)
        {
            Thread t = new Thread(indexRunnable, "SearchService:index");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY+1);
            t.start();
            _threads.add(t);
        }

        for (int i=0 ; i<getCountPreprocessorThreads() ; i++)
        {
            Thread t = new Thread(preprocessRunnable, "SearchService:preprocess " + i);
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY+1);
            t.start();
            _threads.add(t);
        }

        {
            Thread t = new Thread(runRunnable, "SearchService:runner");
            t.setDaemon(true);
            t.start();
            _threads.add(t);
        }

        ContextListener.addShutdownListener(this);
    }


    boolean _shuttingDown = false;
    ArrayList<Thread> _threads = new ArrayList<Thread>(10);


    public void shutdownStarted(ServletContextEvent servletContextEvent)
    {
        _shuttingDown = true;

        for (Thread t : _threads)
            t.interrupt();

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
                    i = _runQueue.take();
                    while (!_shuttingDown && _itemQueue.size() > 1000)
                    {
                        try {Thread.sleep(100);}catch(InterruptedException x){}
                    }
                    i._run.run();
                }
                catch (InterruptedException x)
                {
                }
                catch (Exception x)
                {
                    _log.error("Error running " + (null != i ? i._id : ""), x);
                }
                finally
                {
                    if (null != i)
                    {
                        i.complete(success);
                    }
                }
            }
        }
    };


    private boolean preprocess(Item i)
    {
        Resource r = i.getResource();
        if (null == r || !r.exists())
            return false;
        assert MemTracker.put(r);
        _log.debug("preprocess(" + r.getDocumentId() + ")");
        i._preprocessMap = preprocess(i._id, i._res);
        if (null == i._preprocessMap)
        {
            _log.debug("skipping " + i._id);
            return false;
        }
        return true;
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
                catch (InterruptedException x)
                {
                }
                catch (Exception x)
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


    final Object _commitLock = new Object(){ public String toString() { return "COMMIT LOCK"; } };
    int _countIndexedSinceCommit = 0;
    
    Runnable indexRunnable = new Runnable()
    {
        public void run()
        {
            while (!_shuttingDown)
            {
                Item i = null;
                boolean success = false;
                try
                {
                    if (null == (i = _indexQueue.poll()))
                    {
                        // help out on preprocessing?
                        i = _itemQueue.poll();
                        if (null != i)
                        {
                            if (!preprocess(i))
                                continue;
                        }
                        else
                            i = _indexQueue.poll(1, TimeUnit.SECONDS);
                    }

                    if (null == i)
                    {
                        synchronized (_commitLock)
                        {
                            if (_countIndexedSinceCommit > 0)
                            {
                                commit();
                                _countIndexedSinceCommit = 0;
                            }
                        }
                        continue;
                    }

                    Resource r = i.getResource();
                    if (null == r || !r.exists())
                        continue;
                    assert MemTracker.put(r);
                    _log.debug("index(" + i._id + ")");
                    index(i._id, i._res, i._preprocessMap);
                    i._res.setLastIndexed(i._start);
                    success = true;
                    synchronized (_commitLock)
                    {
                        _countIndexedSinceCommit++;
                        if (_countIndexedSinceCommit > 10000)
                        {
                            commit();
                            _countIndexedSinceCommit = 0;
                        }
                    }
                }
                catch (InterruptedException x)
                {
                }
                catch (Exception x)
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


    final Object _categoriesLock = new Object();
    List<SearchCategory> _readonlyCategories = Collections.emptyList();
    ArrayList<SearchCategory> _searchCategories = new ArrayList<SearchCategory>();
            
    
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


    protected abstract void index(String id, Resource r, Map preprocessProps);
    protected abstract void commit();
    protected abstract void shutDown();

    static Map emptyMap = Collections.emptyMap();
    
    Map<?,?> preprocess(String id, Resource r)
    {
        return emptyMap;
    }
}

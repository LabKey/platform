package org.labkey.core.search;

import org.apache.log4j.Category;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.search.SearchService;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.view.ActionURL;
import org.labkey.api.webdav.Resource;

import javax.servlet.ServletContextEvent;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Nov 12, 2009
 * Time: 12:58:21 PM
 */
public abstract class AbstractSearchService implements SearchService, ShutdownListener
{
    final static Category _log = Category.getInstance(SearchService.class);

    // CONSIDER: don't allow duplicates in queue
    PriorityBlockingQueue<Item> _submitQueue = new PriorityBlockingQueue<Item>(1000, new Comparator<Item>()
    {
        public int compare(Item o1, Item o2)
        {
            return o1._pri.compareTo(o2._pri);
        }
    });

    BlockingQueue<Item> _indexQueue = new ArrayBlockingQueue<Item>(Math.min(4,Runtime.getRuntime().availableProcessors()));

    enum OPERATION
    {
        add, delete
    }


    class Item
    {
        OPERATION _op;
        String _id;
        Resource _res;
        Runnable _run;
        PRIORITY _pri;
        long _start = 0;
        Map<?,?> _preprocessMap = null;
        
        Item(OPERATION op, String id, Resource r, PRIORITY pri)
        {
            if (null != r)
                _start = System.currentTimeMillis();
            _op = op; _id = id; _res = r;
            _pri = null == pri ? PRIORITY.bulk : pri;
        }

        Item(Runnable r, PRIORITY pri)
        {
            _run = r;
            _pri = null == pri ? PRIORITY.bulk : pri;
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
    }


    public AbstractSearchService()
    {
        startThread();
    }


    private void queueItem(Item i)
    {
        assert MemTracker.put(i);
        _log.debug("_sumbitQueue.put(" + i._id + ")");
        _submitQueue.put(i);
    }

    
    public void addResource(Runnable r, PRIORITY pri)
    {
        queueItem(new Item(r,pri));
    }


    public void addResource(String identifier, PRIORITY pri)
    {
        addResource(identifier, null, pri);
    }


    public void addResource(ActionURL url, PRIORITY pri)
    {
        addResource("action:" + url.getLocalURIString(), null);
    }


    public void addResource(String identifier, Resource r, PRIORITY pri)
    {
        queueItem(new Item(OPERATION.add, identifier, r, pri));
    }


    public void deleteResource(String identifier, PRIORITY pri)
    {
        Item i = new Item(OPERATION.delete, identifier, null, pri);
        _submitQueue.put(i);
    }


    Map<String,ResourceResolver> _resolvers = Collections.synchronizedMap(new HashMap<String,ResourceResolver>());

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


    void startThread()
    {
        if (_shuttingDown)
            return;
        _indexingThread = new Thread(indexRunnable, "SearchService:index");
        _indexingThread.setDaemon(true);
        _indexingThread.start();

        for (int i=0 ; i<1 ; i++)
        {
            Thread t = new Thread(preprocessRunnable, "SearchService:preprocess " + i);
            t.setDaemon(true);
            t.start();
            _preprocessingThreads.add(t);
        }
        ContextListener.addShutdownListener(this);
    }


    boolean _shuttingDown = false;
    Thread _indexingThread = null;
    ArrayList<Thread> _preprocessingThreads = new ArrayList<Thread>(10);


    public void shutdownStarted(ServletContextEvent servletContextEvent)
    {
        _shuttingDown = true;

        for (Thread t : _preprocessingThreads)
            t.interrupt();
        if (null != _indexingThread)
            _indexingThread.interrupt();

        try
        {
            if (null != _indexingThread)
                _indexingThread.join(2000);
            for (Thread t : _preprocessingThreads)
                t.join(100);
        }
        catch (InterruptedException e) {}
        shutDown();
    }


    // Runnables can probably queue items faster than we can process them, so we only want to process
    // one runnable at a time.  Do you have a better idea?
    AtomicReference<Runnable> _running = new AtomicReference<Runnable>();

    Runnable preprocessRunnable = new Runnable()
    {
        public void run()
        {
            while (!_shuttingDown)
            {
                Item i = null;
                try
                {
                    i = _submitQueue.take();
                    if (null != i._run)
                    {
                        if (_running.compareAndSet(null, i._run))
                        {
                            try
                            {
                                i._run.run();
                            }
                            finally
                            {
                                _running.compareAndSet(i._run, null);
                            }
                        }
                        else
                        {
                            Thread.sleep(10);
                            queueItem(i);   // toss it back
                        }
                    }
                    else
                    {
                        Resource r = i.getResource();
                        if (null == r || !r.exists())
                            continue;
                        assert MemTracker.put(r);
                        i._preprocessMap = preprocess(i._id, i._res);
                        if (null == i._preprocessMap)
                        {
                            _log.debug("skipping " + i._id);
                            continue;
                        }
                        _log.debug("_indexQueue.put(" + i._id + ")");
                        _indexQueue.put(i);
                    }
                }
                catch (InterruptedException x)
                {
                }
                catch (Exception x)
                {
                    Category.getInstance(SearchService.class).error("Error processing " + (null != i ? i._id : ""), x);
                }
            }
        }
    };
    

    Runnable indexRunnable = new Runnable()
    {
        public void run()
        {
            int countIndexedSinceCommit = 0;
            
            while (!_shuttingDown)
            {
                Item i = null;
                try
                {
                    i = _indexQueue.poll(1, TimeUnit.SECONDS);
                    if (null == i)
                    {
                        if (countIndexedSinceCommit > 0)
                        {
                            commit();
                            countIndexedSinceCommit = 0;
                        }
                        continue;
                    }
                    
                    Resource r = i.getResource();
                    if (null == r || !r.exists())
                        continue;
                    assert MemTracker.put(r);
                    _log.debug("index(" + i._id + ")");
                    index(i._id, i._res, i._preprocessMap);
                    countIndexedSinceCommit++;
                    i._res.setLastIndexed(i._start);
                }
                catch (InterruptedException x)
                {
                }
                catch (Exception x)
                {
                    Category.getInstance(SearchService.class).error("Error indexing " + (null != i ? i._id : ""), x);
                }
            }
            if (countIndexedSinceCommit > 0)
                commit();
        }
    };

    protected abstract void index(String id, Resource r, Map preprocessProps);
    protected abstract void commit();
    protected abstract void shutDown();

    static Map emptyMap = Collections.emptyMap();
    
    Map<?,?> preprocess(String id, Resource r)
    {
        return emptyMap;
    }
}

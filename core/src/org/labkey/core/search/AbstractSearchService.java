package org.labkey.core.search;

import org.labkey.api.search.SearchService;
import org.labkey.api.webdav.WebdavResolver;
import org.labkey.api.view.ActionURL;
import org.labkey.api.security.User;
import org.labkey.api.util.MemTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.apache.log4j.Category;

import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Nov 12, 2009
 * Time: 12:58:21 PM
 */
public abstract class AbstractSearchService implements SearchService
{
    // CONSIDER: don't allow duplicates in queue
    PriorityBlockingQueue<Item> _queue = new PriorityBlockingQueue<Item>(1000, new Comparator<Item>()
    {
        public int compare(Item o1, Item o2)
        {
            return o1._pri.compareTo(o2._pri);
        }
    });
    String _solrEndpoint = "http://localhost:8080/";


    enum OPERATION
    {
        add, delete
    }


    class Item
    {
        OPERATION _op;
        String _id;
        WebdavResolver.Resource _res;
        Runnable _run;
        PRIORITY _pri;
        
        Item(OPERATION op, String id, WebdavResolver.Resource r, PRIORITY pri)
        {
            _op = op; _id = id; _res = r;
            _pri = null == pri ? PRIORITY.bulk : pri;
        }

        Item(Runnable r, PRIORITY pri)
        {
            _run = r;
            _pri = null == pri ? PRIORITY.bulk : pri;
        }
    }


    public AbstractSearchService()
    {
        startThread();
    }


    private void queueItem(Item i)
    {
        assert MemTracker.put(i);
        _queue.put(i);
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


    public void addResource(String identifier, WebdavResolver.Resource r, PRIORITY pri)
    {
        queueItem(new Item(OPERATION.add, identifier, r, pri));
    }


    public void deleteResource(String identifier, PRIORITY pri)
    {
        Item i = new Item(OPERATION.delete, identifier, null, pri);
        _queue.put(i);
    }


    Map<String,ResourceResolver> _resolvers = Collections.synchronizedMap(new HashMap<String,ResourceResolver>());

    public void addResourceResolver(@NotNull String prefix, @NotNull ResourceResolver resolver)
    {
        _resolvers.put(prefix, resolver);
    }


    // CONSIDER Iterable<Resource>
    @Nullable
    public Object resolveResource(@NotNull String resourceIdentifier)
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
        indexer.start();
        // UNDONE: showdown listener
    }


    Thread indexer = new Thread()
    {
        public void run()
        {
            while (true)
            {
                Item i;
                try
                {
                    i = _queue.take();
                }
                catch (InterruptedException x)
                {
                    continue;
                }
                if (null != i._run)
                {
                    i._run.run();
                }
                else
                {
                    if (null == i._res)
                    {
                        Object o = resolveResource(i._id);
                        assert MemTracker.put(o);
                        if (null == o)
                            continue;
                        i._res = (WebdavResolver.Resource)o;
                    }
                    try
                    {
                        index(i._id, i._res);
                    }
                    catch (Exception x)
                    {
                        Category.getInstance(SearchService.class).error(x);
                    }
                }
            }
        }
    };

    protected abstract void index(String id, WebdavResolver.Resource r);
    protected abstract void commit();
}

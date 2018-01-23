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
package org.labkey.search.model;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.resource.Resource;
import org.labkey.api.search.SearchService;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.RateLimiter;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.util.URLHelper;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.api.webdav.WebdavResolver;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * User: matthewb
 * Date: Nov 18, 2009
 * Time: 11:09:03 AM
 *
 * The Crawler has several components
 *
 * 1) DirectoryCrawler
 *  The directory crawler looks for new directories, and file updates.
 *  By default every known directory will be scanned for new folders every 12hrs
 *
 * 2) FileUpdater
 *  When a new directory or file is found it is queued up for indexing, this is where throttling 
 *  will occur (when implemented)
 *
 * The SearchService also has its own thread pool we use when we find files to index, but the
 * background crawling is pretty different and needs its own scheduling behavior.
 */
public class DavCrawler implements ShutdownListener
{
//    SearchService.SearchCategory folderCategory = new SearchService.SearchCategory("Folder", "Folder");

    long _defaultWait = TimeUnit.SECONDS.toMillis(60);
    long _defaultBusyWait = TimeUnit.SECONDS.toMillis(1);

    // UNDONE: configurable
    // NOTE: we want to use these to control how fast we SUBMIT jobs to the indexer,
    // we don't want to hold up the actual indexer threads if possible

     // 10 directories/second
    final RateLimiter _listingRateLimiter = new RateLimiter("directory listing", 10, TimeUnit.SECONDS);

    // 1 Mbyte/sec, this seems to be enough to use a LOT of tika cpu time
    final RateLimiter _fileIORateLimiter = new RateLimiter("file io", 1000000, TimeUnit.SECONDS);

    // CONSIDER: file count limiter
    final RateLimiter _filesIndexRateLimiter = new RateLimiter("file index", 100, TimeUnit.SECONDS);


    public static class ResourceInfo
    {
        ResourceInfo(Date indexed, Date modified)
        {
            this.lastIndexed = indexed;
            this.modified = modified;
        }
        
        Date lastIndexed;
        Date modified;
        //long length;
    }

    static private Cache<Path,ResourceInfo> errors = CacheManager.getCache(1000,TimeUnit.DAYS.toMillis(7),"crawler indexing errors");


    // to make testing easier, break out the interface for persisting crawl state
    // This is an awkward factoring.  Break out the "FileQueue" function instead
    public interface SavePaths
    {
        java.util.Date failDate = SearchService.failDate;
        java.util.Date nullDate = new java.sql.Timestamp(DateUtil.parseISODateTime("1899-12-31"));
        java.util.Date oldDate =  new java.sql.Timestamp(DateUtil.parseISODateTime("1967-10-04"));

        // collections

        /** update path (optionally create) */
        boolean updatePath(Path path, java.util.Date lastIndexed, java.util.Date nextCrawl, boolean create);

        /** insert path if it does not exist */
        boolean insertPath(Path path, java.util.Date nextCrawl);
        void updatePrefix(Path path, Date next, boolean forceIndex);
        void deletePath(Path path);

        /** <lastCrawl, nextCrawl> */
        Map<Path, Pair<Date,Date>> getPaths(int limit);
        Date getNextCrawl();

        // files
        Map<String,ResourceInfo> getFiles(Path path);
        boolean updateFile(@NotNull Path path, @NotNull Date lastIndexed, @Nullable Date modified);

        void clearFailedDocuments();
    }
    

    final static Logger _log = Logger.getLogger(DavCrawler.class);

    
    DavCrawler()
    {
        ContextListener.addShutdownListener(this);
        _crawlerThread.setDaemon(true);
    }


    static DavCrawler _instance = new DavCrawler();
    volatile boolean _shuttingDown = false;

    
    public static DavCrawler getInstance()
    {
        return _instance;
    }


    public void start()
    {
        if (!_shuttingDown && !_crawlerThread.isAlive())
            _crawlerThread.start();
    }


    @Override
    public String getName()
    {
        return "DAV crawler";
    }

    public void shutdownPre()
    {
        _shuttingDown = true;
        if (null != _crawlerThread)
            _crawlerThread.interrupt();
    }


    public void shutdownStarted()
    {
        if (null != _crawlerThread)
        try
        {
            _crawlerThread.join(1000);
        }
        catch (InterruptedException x)
        {
        }
    }

    
    /**
     * Aggressively scan the file system for new directories and new/updated files to index
     * 
     * @param path
     * @param force if (force==true) then don't check lastindexed and modified dates
     */


    public void startFull(Path path, boolean force)
    {
        _log.debug("START FULL: " + path);

        if (null == path)
            path = WebdavService.get().getResolver().getRootPath();

        // note use oldDate++ so that the crawler can schedule tasks ahead of these bulk updated collections
        _paths.updatePrefix(path, new Date(SavePaths.oldDate.getTime() + 24*60*60*1000), force);

        addPathToCrawl(path, SavePaths.oldDate);
    }


    /**
     * start a background process to watch directories
     * optionally add a path at the same time
     */
    public void addPathToCrawl(@Nullable Path start, Date nextCrawl)
    {
        if (null != start)
        {
            _log.debug("START CONTINUOUS " + start.toString());
            _paths.updatePath(start, null, nextCrawl, true);
        }

        pingCrawler();
    }


    private final LinkedList<Pair<String, Date>> _recent = new LinkedList<>();

    
    class IndexDirectoryJob implements Runnable, SearchService.TaskListener
    {
        SearchService.IndexTask _task;
        Path _path;
        WebdavResource _directory;
        boolean _full;
        Date _lastCrawl=null;
        Date _nextCrawl=null;
        Date _indexTime = null;
        
        IndexDirectoryJob(Path path, Date last, Date next)
        {
            _path = path;
            _lastCrawl = last;
            _full = next.getTime() <= SavePaths.oldDate.getTime();
            _task = getSearchService().createTask("Index " + _path.toString(), this);
        }

        /** TaskListener **/

        @Override
        public void success()
        {
            _paths.updatePath(_path, _indexTime, _nextCrawl, true);
            addRecent(_directory);
        }


        @Override
        public void indexError(Resource r, Throwable t)
        {
            ResourceInfo info = new ResourceInfo(new Date(HeartBeat.currentTimeMillis()), new Date(r.getLastModified()));
            errors.put(r.getPath(), info);
        }

        /** TaskListener **/


        public void run()
        {
            boolean isCrawlerThread = Thread.currentThread() == _crawlerThread;
            
            _listingRateLimiter.add(1, isCrawlerThread);

            _log.debug("IndexDirectoryJob.run(" + _path + ")");

            _directory = getResolver().lookup(_path);

            // CONSIDER: delete previously indexed resources in child containers as well
            if (null == _directory || !_directory.isCollection() || !_directory.shouldIndex() || skipContainer(_directory))
            {
                if (_path.startsWith(getResolver().getRootPath()))
                    _paths.deletePath(_path);
                return;
            }

            _indexTime = new Date(System.currentTimeMillis());
            long changeInterval = (_directory instanceof WebdavResolver.WebFolder) ? CacheManager.DAY / 2 : CacheManager.DAY;
            long nextCrawl = _indexTime.getTime() + (long)(changeInterval * (0.5 + 0.5 * Math.random()));
            _nextCrawl = new Date(nextCrawl);

            // if this is a web folder, call enumerate documents
            if (_directory instanceof WebdavResolver.WebFolder)
            {
                Container c = ContainerManager.getForId(_directory.getContainerId());
                if (null == c)
                    return;
                getSearchService().indexContainer(_task, c,  _full ? null : _lastCrawl);
            }

            // get current index status for files
            // CONSIDER: store lastModifiedTime in crawlResources
            // CONSIDER: store documentId in crawlResources
            Map<String,ResourceInfo> map = _paths.getFiles(_path);

            for (WebdavResource child : _directory.list())
            {
                if (_shuttingDown)
                    return;
                if (!child.exists()) // happens when pipeline is defined but directory doesn't exist
                    continue;

                if (child.isFile())
                {
                    ResourceInfo info =  map.remove(child.getName());
                    Date lastIndexed   = (null==info || null==info.lastIndexed) ? SavePaths.nullDate : info.lastIndexed;
                    Date savedModified = (null==info || null==info.modified) ? SavePaths.nullDate : info.modified;
                    long lastModified = child.getLastModified();

                    if (lastModified == savedModified.getTime() && (lastModified <= lastIndexed.getTime() || lastIndexed.getTime() == SavePaths.failDate.getTime()))
                        continue;

                    // if we've failed at indexing this, don't try again: see Issue 16776
                    ResourceInfo errorInfo = errors.get(child.getPath());
                    if (null != errorInfo && errorInfo.modified.getTime() == lastModified)
                        continue;

                    if (!child.shouldIndex())
                        continue;

                    if (skipFile(child))
                    {
                        // just index the name and that's all
                        final WebdavResource wrap = child;
                        URLHelper url;
                        try
                        {
                            url = new URLHelper(child.getExecuteHref(null));
                        }
                        catch (URISyntaxException uri)
                        {
                            ExceptionUtil.logExceptionToMothership(null, uri);
                            continue;
                        }
                        Map<String, Object> props = new HashMap<>();
                        props.put(SearchService.PROPERTY.categories.toString(), SearchService.fileCategory.toString());
                        props.put(SearchService.PROPERTY.title.toString(), wrap.getPath().getName());
                        props.put(SearchService.PROPERTY.keywordsMed.toString(), FileUtil.getSearchKeywords(wrap.getPath().getName()));
                        child = new SimpleDocumentResource(wrap.getPath(), wrap.getDocumentId(), wrap.getContainerId(), "text/plain", (String)null, url, props) {
                            @Override
                            public void setLastIndexed(long ms, long modified)
                            {
                                wrap.setLastIndexed(ms, modified);
                            }
                        };
                    }

                    File f = child.getFile();
                    if (null != f)
                    {
                        if (!f.isFile())
                            continue;
                        _fileIORateLimiter.add(f.length(), isCrawlerThread);
                    }

                    _task.addResource(child, SearchService.PRIORITY.background);
                    addRecent(child);
                }
                else if (!child.shouldIndex())
                {
                    continue;
                }
                else if (!skipContainer(child))
                {
                    long childCrawl = SavePaths.oldDate.getTime();
                    if (!(child instanceof WebdavResolver.WebFolder))
                        childCrawl += child.getPath().size()*1000; // bias toward breadth first
                    if (_full)
                    {
                        _paths.updatePath(child.getPath(), null, new Date(childCrawl), true);
                        pingCrawler();
                    }
                    else
                    {
                        _paths.insertPath(child.getPath(), new Date(childCrawl));
                    }
                }
            }

            // as for the missing
            SearchService ss = getSearchService();
            for (String missing : map.keySet())
            {
                Path missingPath = _path.append(missing);
                String docId =  "dav:" + missingPath.toString();
                ss.deleteResource(docId);
            }

            _task.setReady();
        }
    }


    void addRecent(WebdavResource r)
    {
        synchronized (_recent)
        {
            Date d = new Date(System.currentTimeMillis());
            while (_recent.size() > 40)
                _recent.removeFirst();
            while (_recent.size() > 0 && _recent.getFirst().second.getTime() < d.getTime()-10*60000)
                _recent.removeFirst();
            String text = r.isCollection() ? r.getName() + "/" : r.getName();
            _recent.add(new Pair(text,d));
        }
    }


    final Object _crawlerEvent = new Object();

    void pingCrawler()
    {
        synchronized (_crawlerEvent)
        {
            _crawlerEvent.notifyAll();
        }
    }


    void _wait(Object event, long wait)
    {
        if (wait == 0 || _shuttingDown)
            return;
        try
        {
            synchronized (event)
            {
                event.wait(wait);
            }
        }
        catch (InterruptedException x)
        {
        }
    }


//    final Runnable pingJob = new Runnable()
//    {
//        public void run()
//        {
//            synchronized (_crawlerEvent)
//            {
//                _crawlerEvent.notifyAll();
//            }
//        }
//    };


    void waitForIndexerIdle() throws InterruptedException
    {
        SearchService ss = getSearchService();
        ((AbstractSearchService)ss).waitForRunning();

        // wait for indexer to have nothing else to do
        if (!_shuttingDown && ss.isBusy())
            ss.waitForIdle();
    }
    

    Thread _crawlerThread = new Thread("DavCrawler")
    {
        @Override
        public void run()
        {
            while (!_shuttingDown && null == getSearchService())
            {
                try { Thread.sleep(1000); } catch (InterruptedException x) {}
            }

            while (!_shuttingDown)
            {
                try
                {
                    waitForIndexerIdle();

                    IndexDirectoryJob j = findSomeWork();
                    if (null != j)
                    {
                        j.run();
                    }
                    else
                    {
                        _wait(_crawlerEvent, _defaultWait);                  
                    }
                }
                catch (InterruptedException x)
                {
                    continue;
                }
                catch (Throwable t)
                {
                    _log.error("Unexpected error", t);
                }
            }
        }
    };


    LinkedList<IndexDirectoryJob> crawlQueue = new LinkedList<>();

    IndexDirectoryJob findSomeWork()
    {
        if (_shuttingDown)
            return null;
        if (crawlQueue.isEmpty())
        {
            _log.debug("findSomeWork()");

            Map<Path,Pair<Date,Date>> map = _paths.getPaths(100);

            for (Map.Entry<Path,Pair<Date,Date>> e : map.entrySet())
            {
                Path path = e.getKey();
                Date lastCrawl = e.getValue().first;
                Date nextCrawl = e.getValue().second;

                _log.debug("add to queue: " + path.toString());
                crawlQueue.add(new IndexDirectoryJob(path, lastCrawl, nextCrawl));
            }
        }
        return crawlQueue.isEmpty() ? null : crawlQueue.removeFirst();
    }
    

    static boolean skipContainer(WebdavResource r)
    {
        Path path = r.getPath();
        String name = path.getName();

        if ("@wiki".equals(name))
            return true;

        if (".svn".equals(name))
            return true;

        if (".Trash".equals(name))
            return true;

        // if symbolic link
        //  return true;
        
        if (name.startsWith("."))
            return true;

        // UNDONE: shouldn't be hard-coded
        if ("labkey_full_text_index".equals(name))
            return true;

        // google convention
        if (path.contains("no_crawl"))
            return true;

        File f = r.getFile();
        if (null != f)
        {
            // labkey convention
            if (new File(f,".nocrawl").exists())
                return true;
            // postgres
            if (new File(f,"PG_VERSION").exists())
                return true;
        }

        return false;
    }

    
    boolean skipFile(WebdavResource r)
    {
        return !getSearchService().accept(r);
    }
    

    //
    // dependencies
    //

    SavePaths _paths = new org.labkey.search.model.SavePaths();
    WebdavResolver _resolver = null;
    SearchService _ss = null;

    void setSearchService(SearchService ss)
    {
        _ss = ss;
    }
    
    SearchService getSearchService()
    {
        if (null == _ss)
            _ss = SearchService.get();
        return _ss;
    }

    void setResolver(WebdavResolver resolver)
    {
        _resolver = resolver;
    }
    
    WebdavResolver getResolver()
    {
        if (_resolver == null)
            _resolver = WebdavService.get().getResolver();
        return _resolver;
    }


    public Map<String, Object> getStats()
    {
        SearchService ss = getSearchService();
        boolean paused = !ss.isRunning();
        long now = System.currentTimeMillis();

        Map<String, Object> m = new LinkedHashMap<>();
        long uniqueCollections = new TableSelector(SearchSchema.getInstance().getCrawlCollectionsTable()).getRowCount();
        m.put("Number of unique folders/directories", uniqueCollections);

        if (!paused)
        {
            Date nextHour = new Date(now + TimeUnit.SECONDS.toMillis(60*60));
            Filter nextFilter = new SimpleFilter(FieldKey.fromParts("NextCrawl"), nextHour);
            long countNext = new TableSelector(SearchSchema.getInstance().getCrawlCollectionsTable(), nextFilter, null).getRowCount();
            double max = (60*60) * _listingRateLimiter.getTarget().getRate(TimeUnit.SECONDS);
            long scheduled = Math.min(countNext, Math.round(max));
            m.put("Directories to scan in next 1 hr", scheduled);
        }

        m.put("Directory limiter", Math.round(_listingRateLimiter.getTarget().getRate(TimeUnit.SECONDS)) + "/sec");
        m.put("File I/O limiter", (_fileIORateLimiter.getTarget().getRate(TimeUnit.SECONDS)/1000000) + " MB/sec");

        String activity = getActivityHtml();
        m.put("Recent crawler activity", activity);
        return m;
    }


    String getActivityHtml()
    {
        SearchService ss = getSearchService();
        boolean paused = !ss.isRunning();

        List<Pair<String, Date>> recent;

        synchronized(_recent)
        {
            recent = new ArrayList<>(_recent);
        }

        recent.sort(Comparator.comparing(Pair::getValue, Comparator.reverseOrder()));

        StringBuilder activity = new StringBuilder("<table cellpadding=1 cellspacing=0>"); //<tr><td><img width=80 height=1 src='" + AppProps.getInstance().getContextPath() + "/_.gif'></td><td><img width=300 height=1 src='" + AppProps.getInstance().getContextPath() + "/_.gif'></td></tr>");
        String last = "";
        long now = System.currentTimeMillis();
        long cutoff = now - (paused ? 60000 : 5*60000);
        now = now - (now % 1000);
        long newest = 0;

        for (Pair<String, Date> p : recent)
        {
            String text  = p.first;
            long time = p.second.getTime();
            if (time < cutoff) continue;
            newest = Math.max(newest,time);
            long dur = Math.max(0,now - (time-(time%1000)));
            String ago = DateUtil.formatDuration(dur) + "&nbsp;ago";
            activity.append("<tr><td align=right color=#c0c0c0>").append(ago.equals(last) ? "" : ago).append("&nbsp;</td><td>").append(PageFlowUtil.filter(text)).append("</td></tr>\n");
            last = ago;
        }
        if (paused)
        {
            activity.append("<tr><td colspan=2>PAUSED");
            if (newest+60000>now)
                activity.append (" (queue may take a while to clear)");
            activity.append("</td></tr>");
        }
        activity.append("</table>");
        return activity.toString();
    }


    public void clearFailedDocuments()
    {
        _paths.clearFailedDocuments();
    }
}
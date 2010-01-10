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
import org.apache.commons.lang.StringUtils;
import org.labkey.api.collections.Cache;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.search.SearchService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.*;
import org.labkey.api.webdav.Resource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.api.webdav.WebdavResolver;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.api.view.ActionURL;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;

import javax.servlet.ServletContextEvent;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by IntelliJ IDEA.
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
 * The SearchService also has it's own thread pool we use when we find files to index, but the
 * background crawling is pretty different and needs its own scheduling behavior.
 */
public class DavCrawler implements ShutdownListener
{
//    SearchService.SearchCategory folderCategory = new SearchService.SearchCategory("Folder", "Folder");

    long _defaultWait = TimeUnit.SECONDS.toMillis(60);
    long _defaultBusyWait = TimeUnit.SECONDS.toMillis(5);

    // to make testing easier, break out the interface for persisting crawl state
    public interface SavePaths
    {
        final static java.util.Date nullDate = new java.sql.Timestamp(DateUtil.parseStringJDBC("1967-10-04"));
        final static java.util.Date oldDate = new java.sql.Timestamp(DateUtil.parseStringJDBC("2000-01-01"));

        // collections

        /** update path (optionally create) */
        boolean updatePath(Path path, java.util.Date lastIndexed, java.util.Date nextCrawl, boolean create);

        /** insert path if it does not exist */
        boolean insertPath(Path path, java.util.Date nextCrawl);
        void updatePrefix(Path path, java.util.Date lastIndexed, java.util.Date nextCrawl, boolean forceReindexAtNextCrawl);
        void deletePath(Path path);

        /** <lastCrawl, nextCrawl> */
        public Map<Path, Pair<Date,Date>> getPaths(int limit);
        public Date getNextCrawl();

        // files
        public Map<String,Date> getFiles(Path path);
        public boolean updateFile(Path path, Date lastIndexed);
    }
    

    final static Category _log = Category.getInstance(DavCrawler.class);

    
    DavCrawler()
    {
        ContextListener.addShutdownListener(this);
        _crawlerThread.setDaemon(true);
        _crawlerThread.start();
    }


    static DavCrawler _instance = new DavCrawler();
    boolean _shuttingDown = false;

    
    public static DavCrawler getInstance()
    {
        return _instance;
    }

    
    public void shutdownStarted(ServletContextEvent servletContextEvent)
    {
        _shuttingDown = true;
        if (null != _crawlerThread)
            _crawlerThread.interrupt();
    }

    
    /**
     * Aggressively scan the file system for new directories and new/updated files to index
     * 
     * @param path
     */


    public void startFull(Path path)
    {
        _log.debug("START FULL: " + path);

        if (null == path)
            path = WebdavService.get().getResolver().getRootPath();

        _paths.updatePrefix(path, null, null, true);

        addPathToCrawl(path, null);
    }


    /**
     * start a background process to watch directories
     * optionally add a path at the same time
     */
    public void addPathToCrawl(Path start, Date nextCrawl)
    {
        _log.debug("START CONTINUOUS " + start.toString());

        if (null != start)
            _paths.insertPath(start, nextCrawl);
        pingCrawler();
    }


    class IndexDirectoryJob implements Runnable
    {
        SearchService.IndexTask _task;
        Path _path;
        boolean _full;
        Date _lastCrawl=null;
        Date _nextCrawl=null;
        Date _indexTime = null;
        
        /**
         * @param path
         */
        IndexDirectoryJob(Path path, Date last, Date next)
        {
            _path = path;
            _lastCrawl = last;
            _nextCrawl = next;
            _full = _nextCrawl.getTime() < SavePaths.oldDate.getTime();
        }


        public void submit()
        {
            _task = getSearchService().createTask("Index " + _path.toString());
            _task.addRunnable(this, SearchService.PRIORITY.crawl);
            _task.setReady();
        }


        public void run()
        {
            _log.debug("IndexDirectoryJob.run(" + _path + ")");
            final long now = System.currentTimeMillis();

            if (_path.equals(Path.rootPath))
            {
                // what do we want to do here...
                return;
            }


            Resource r = getResolver().lookup(_path);
            if (null == r || !r.isCollection())
            {
                _paths.deletePath(_path);
                return;
            }


            // if this is a web folder, call enumerate documents
            if (r instanceof WebdavResolver.WebFolder)
            {
                Container c = ContainerManager.getForId(r.getContainerId());
                if (null == c)
                    return;
                getSearchService().indexContainer(_task, c,  _full ? null : _lastCrawl);
            }

            // get current index status for files
            Map<String,Date> map = _paths.getFiles(_path);
            // CONSIDER: _paths.getChildCollections(_path);

            for (Resource child : r.list())
            {
                if (_shuttingDown)
                    return;
                if (child.isFile())
                {
                    Date lastIndexed = map.get(child.getName());
                    if (null == lastIndexed)
                        lastIndexed = SavePaths.nullDate;
                    long lastModified = child.getLastModified();
                    if (lastModified <= lastIndexed.getTime())
                        continue;
                    _task.addResource(child, SearchService.PRIORITY.background);
                }
                else if (!child.exists()) // happens when pipeline is defined but directory doesn't exist
                {
                    continue;
                }
                else if (!child.shouldIndex())
                {
                    continue;
                }
                else if (skipContainer(child))
                {
                    continue;
                }
                else
                {
                    long nextCrawl = SavePaths.nullDate.getTime();
                    if (!(r instanceof WebdavResolver.WebFolder))
                        nextCrawl += child.getPath().size()*1000; // bias toward breadth first
                    if (_full)
                    {
                        _paths.updatePath(child.getPath(), null, new Date(nextCrawl), true);
                        pingCrawler();
                    }
                    else
                    {
                        _paths.insertPath(child.getPath(), new Date(nextCrawl));
                    }
                }
            }

            // UNDONE: would be better if this were called on _task completion
            long changeInterval = (r instanceof WebdavResolver.WebFolder) ? Cache.DAY / 2 : Cache.DAY;
            long nextCrawl = now + (long)(changeInterval * (0.5 + 0.5 * Math.random()));
            _paths.updatePath(_path, new Date(now), new Date(nextCrawl), true);
        }
    }


    class Rate
    {
        double _rate;
        Rate(int count, int duration, TimeUnit unit)
        {
            _rate = (double)count / (double)unit.toMillis(duration);
        }
    }

    
    class RateAccumulator
    {
        long _start = System.currentTimeMillis();
        double _count = 0;
        void accumulate(int add)
        {
            _count += add;
        }
        double getRate()
        {
            return _count / Math.max(1, System.currentTimeMillis()-_start);
        }
    }


    class RateLimiter
    {

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
        if (wait == 0)
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


    Thread _crawlerThread = new Thread("DavCrawler")
    {
        @Override
        public void run()
        {
            long delay = 0;
            
            while (!_shuttingDown)
            {
                try
                {
                    SearchService ss = getSearchService();
                    if (null != ss && !((AbstractSearchService)ss).waitForRunning())
                        continue;
                    _wait(_crawlerEvent, delay);
                    delay = _defaultBusyWait;
                    if (null == ss || ss.isBusy())
                        continue;
                    delay = findSomeWork();
                }
                catch (Throwable t)
                {
                    _log.error("Unexpected error", t);
                }
            }
        }
    };
    

    long findSomeWork()
    {
        _log.debug("findSomeWork()");

        boolean fullCrawl = false;
        Map<Path,Pair<Date,Date>> map = _paths.getPaths(100);

        if (map.isEmpty())
        {
            return _defaultWait;
//            Date next = _paths.getNextCrawl();
//            if (null == next)
//                return 5 * 60000;
//            long delay = next.getTime() - System.currentTimeMillis();
//            return Math.max(0,Math.min(5*60000,delay));
        }

        List<Path> paths = new ArrayList<Path>(map.size());
        for (Map.Entry<Path,Pair<Date,Date>> e : map.entrySet())
        {
            Path path = e.getKey();
            Date lastCrawl = e.getValue().first;
            Date nextCrawl = e.getValue().second;
            boolean full = nextCrawl.getTime() < SavePaths.oldDate.getTime();
            fullCrawl |= full;

            _log.debug("crawl: " + path.toString());
            paths.add(path);
            new IndexDirectoryJob(path, lastCrawl, nextCrawl).submit();
        }
        long delay = (fullCrawl || map.size() > 0) ? 0 : _defaultWait;
        return delay;
    }
    

    static boolean skipContainer(Resource r)
    {
        String name = r.getName();

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
        
        return false;
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
            _ss = ServiceRegistry.get().getService(SearchService.class);
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
}
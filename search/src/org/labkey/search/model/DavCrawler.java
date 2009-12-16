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
import org.labkey.api.search.SearchService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.Path;
import org.labkey.api.webdav.Resource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.api.webdav.WebdavResolver;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
public class DavCrawler
{
    long _defaultWait = TimeUnit.SECONDS.toMillis(60);
    long _defaultBusyWait = TimeUnit.SECONDS.toMillis(1);

    // to make testing easier, break out the interface for persisting crawl state
    public interface SavePaths
    {
        final static java.util.Date nullDate = new java.sql.Timestamp(DateUtil.parseStringJDBC("1967-10-04"));
        final static java.util.Date oldDate = new java.sql.Timestamp(DateUtil.parseStringJDBC("2000-01-01"));

        // collections
        boolean updatePath(Path path, java.util.Date lastIndexed, java.util.Date nextCrawl, boolean create);
        void updatePrefix(Path path, java.util.Date lastIndexed, java.util.Date nextCrawl);
        void deletePath(Path path);
        public Map<Path, Date> getPaths(int limit);

        // files
        public Map<String,Date> getFiles(Path path);
        public boolean updateFile(Path path, Date lastIndexed);
    }
    

    final static Category _log = Category.getInstance(DavCrawler.class);

    
    DavCrawler()
    {
        _crawlerThread.setDaemon(true);
        _crawlerThread.start();
    }


    static DavCrawler _instance = new DavCrawler();

    public static DavCrawler getInstance()
    {
        return _instance;
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

        _paths.updatePrefix(path, null, null);

        startContinuous(path);
    }


    /**
     * start a background process to watch directories
     */
    public void startContinuous(Path start)
    {
        _log.debug("START FULL");

        _paths.updatePath(start, null, null, true);
        pingCrawler();
    }



    class IndexDirectoryJob implements Runnable
    {
        SearchService.IndexTask _task;
        Path _path;
        boolean _full;

        /**
         * @param path
         * @param full  if true, immediately schedule a scan for all children
         */
        IndexDirectoryJob(SearchService.IndexTask task, Path path, boolean full)
        {
            _task = task;
            _path = path;
            _full = full;
        }


        public void submit()
        {
            _task.addRunnable(this, SearchService.PRIORITY.background);
        }

        
        public void run()
        {
            _log.debug("IndexDirectoryJob.run(" + _path + ")");
            
            // index files
            Resource r = getResolver().lookup(_path);
            if (null == r || !r.isCollection())
            {
                _paths.deletePath(_path);
                return;
            }

            // get current index status
            Map<String,Date> map = _paths.getFiles(_path);

            for (Resource child : r.list())
            {
                if (child.isFile())
                {
                    Date lastIndexed = map.get(r.getName());
                    if (null == lastIndexed)
                        lastIndexed = SavePaths.nullDate;
                    long lastModified = r.getLastModified();
                    if (lastModified > lastIndexed.getTime())
                        _task.addResource(child, SearchService.PRIORITY.background);
                }
                else if (skipContainer(child))
                {
                    continue;
                }
                else if (_full)
                {
                    _paths.updatePath(child.getPath(), null, null, true);
                    pingCrawler();
                }
            }
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
            
            while (1==1)
            {
                try
                {
                    _wait(_crawlerEvent, delay);
                    delay = _defaultBusyWait;
                    SearchService ss = getSearchService();
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
        Map<Path,Date> map = _paths.getPaths(100);
        List<Path> paths = new ArrayList<Path>(map.size());

        for (Map.Entry<Path,Date> e : map.entrySet())
        {
            Path path = e.getKey();
            Date nextCrawl = e.getValue();
            boolean full = nextCrawl.getTime() < SavePaths.oldDate.getTime();
            fullCrawl |= full;

            _log.debug("reindex: " + path.toString());
            paths.add(path);
            new IndexDirectoryJob(getSearchService().defaultTask(), path, full).submit();
        }
        long delay = (fullCrawl || map.size() > 0) ? 0 : TimeUnit.SECONDS.toMillis(60);
        return delay;
    }
    

    static boolean skipContainer(Resource r)
    {
        String name = r.getName();
        
        if ("@wiki".equals(name))
            return true;

        if (".svn".equals(name))
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
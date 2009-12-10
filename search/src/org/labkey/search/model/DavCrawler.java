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
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.*;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.webdav.Resource;
import org.labkey.api.webdav.WebdavService;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
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
    final static java.sql.Timestamp nullDate = new java.sql.Timestamp(DateUtil.parseStringJDBC("1967-10-04"));
    final static java.sql.Timestamp oldDate = new java.sql.Timestamp(DateUtil.parseStringJDBC("2000-01-01"));

    final static Category _log = Category.getInstance(DavCrawler.class);


    private DavCrawler()
    {
        _crawlerThread.setDaemon(true);
        _crawlerThread.start();
    }


    static DavCrawler _instance = new DavCrawler();

    public static DavCrawler getInstance()
    {
        return _instance;
    }
    

    private static DbSchema getSearchSchema()
    {
        return DbSchema.get("search");
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

        try
        {
            // TODO: where path like ...
            Table.execute(getSearchSchema(),
                    "UPDATE search.CrawlCollections " +
                    "SET LastCrawled=NULL, NextCrawl=? " +
                    "WHERE Path like ?",
                    new Object[]{nullDate, toPathString(path) + "%"});
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        
        startContinuous(path);
    }


    /**
     * start a background process to watch directories
     */
    public void startContinuous(Path start)
    {
        _log.debug("START FULL");

        updateDirectory(start, null, null);
        pingCrawler();
    }


    SearchService getSearchService()
    {
        return ServiceRegistry.get().getService(SearchService.class);
    }


    private static String toPathString(Path path)
    {
        return path.toString("/", "/");
    }


    private boolean _update(Path path, java.util.Date last, java.util.Date next) throws SQLException
    {
        String pathStr = toPathString(path);
        if (null == last) last = nullDate;
        if (null == next) next = nullDate;
        SQLFragment upd = new SQLFragment(
                "UPDATE search.CrawlCollections SET LastCrawled=?, NextCrawl=? WHERE Path=?",
                last, next, pathStr);
        if (null != getSearchSchema().getTable("CrawlCollections").getColumn("csPath"))
        {
            upd.append(" AND csPath=CHECKSUM(?)");
            upd.add(pathStr);
        }
        int count = Table.execute(getSearchSchema(), upd);
        return count > 0;
    }


    // -1 if not exists
    private int getId(Path path) throws SQLException
    {
        // find parent
        String s = toPathString(path);
        SQLFragment find = new SQLFragment("SELECT id FROM search.CrawlCollections WHERE Path=?");
        find.add(s);
        if (null != getSearchSchema().getTable("CrawlCollections").getColumn("csPath"))
        {
            find.append(" AND csPath=CHECKSUM(?)");
            find.add(s);
        }
        ResultSet rs = null;
        try
        {
            rs = Table.executeQuery(getSearchSchema(), find);
            if (rs.next())
                return rs.getInt(1);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
        return -1;
    }

    
    // create if not exists
    private int getParentId(Path path) throws SQLException
    {
        Path parent = path.getParent();
        if (null == parent)
            return 0;

        int id = getId(path.getParent());
        if (id != -1)
            return id;

        // insert parent
        return _insertPath(parent);
    }


    private int _insertPath(Path path) throws SQLException
    {
        // Mostly I don't care about Parent
        // However, we need this for the primary key
        int parent = getParentId(path);

        CaseInsensitiveHashMap<Object> map = new CaseInsensitiveHashMap<Object>();
        map.put("Path", toPathString(path));
        map.put("Name", path.equals(Path.rootPath) ? "/" : path.getName());   // "" is treated like NULL
        map.put("Parent", parent);
        try
        {
            map = Table.insert(User.getSearchUser(), getSearchSchema().getTable("CrawlCollections"), map);
            int id = ((Integer)map.get("id")).intValue();
            return id;
        }
        catch (SQLException x)
        {
            if (SqlDialect.isConstraintException(x))
            {
                return getId(path);
            }
            else
            {
                throw x;
            }
        }
    }
    

    private void updateDirectory(Path path, java.util.Date last, java.util.Date next)
    {
        try
        {
            boolean success = _update(path,last,next);
            if (!success)
            {
                _insertPath(path);
                _update(path,last,next);
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
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
            Resource r = WebdavService.get().lookup(_path);
            if (null == r || !r.exists() || r.isFile())
            {
                try
                {
                    Table.execute(getSearchSchema(), "DELETE FROM search.CrawlCollections WHERE Path=?", new Object[]{toPathString(_path)});
                    return;
                }
                catch (SQLException x)
                {
                    throw new RuntimeSQLException(x);
                }
            }

            // get current index status
            SQLFragment s = new SQLFragment(
                    "SELECT D.ChangeInterval, D.Path, D.id, F.Name, F.Modified, F.LastIndexed\n" +
                    "FROM search.CrawlCollections D LEFT OUTER JOIN search.CrawlResources F on D.id=F.parent\n" +
                    "WHERE D.path = ?");
            s.add(toPathString(_path));

            Map<String,Map> map = new HashMap<String, Map>();
            CachedRowSetImpl rs = null;
            try
            {
                rs = (CachedRowSetImpl)Table.executeQuery(getSearchSchema(), s);
                while (rs.next())
                {
                    String name = rs.getString("Name");
                    if (rs.wasNull())
                        map.put(name, rs.getRowMap());
                }
                rs.close();
                rs = null;
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
            finally
            {
                ResultSetUtil.close(rs);
            }

            for (Resource child : r.list())
            {
                if (child.isFile())
                {
                    long lastIndex = 0;
                    Map resourceInfo = map.get(r.getName());
                    if (null != resourceInfo)
                    {
                        Long L = (Long)resourceInfo.get("lastindexed");
                        lastIndex = null==L ? 0 : L.longValue();
                    }
                    long lastModified = r.getLastModified();
                    if (lastModified > lastIndex)
                        _task.addResource(child, SearchService.PRIORITY.background);
                }
                else if (skipContainer(child))
                {
                    continue;
                }
                else if (_full)
                {
                    updateDirectory(child.getPath(), null, null);
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

    Thread _crawlerThread = new Thread("DavCrawler")
    {
        @Override
        public void run()
        {
            long defaultDelay = TimeUnit.SECONDS.toMillis(1);
            long delay = defaultDelay;
            
            while (1==1)
            {
                try
                {
                    synchronized (_crawlerEvent)
                    {
                        _crawlerEvent.wait(delay);
                        SearchService ss = getSearchService();
                        if (ss.isBusy())
                        {
                            delay = TimeUnit.SECONDS.toMillis(1);
                            continue;
                        }
                    }
                }
                catch (InterruptedException x)
                {
                }
                delay = defaultDelay;

                try
                {
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

        long delay;
        boolean fullCrawl = false;
        ResultSet rs = null;
        int count = 0;
        
        try
        {
            java.sql.Timestamp now = new Timestamp(System.currentTimeMillis());
            java.sql.Timestamp awhileago = new Timestamp(now.getTime() - 30*60000);

            SQLFragment f = new SQLFragment(
                    "SELECT Path, NextCrawl\n" +
                    "FROM search.CrawlCollections\n" +
                    "WHERE NextCrawl < ? AND (LastCrawled IS NULL OR LastCrawled < ?) " +
                    "ORDER BY NextCrawl",
                    now, awhileago);
            SQLFragment sel = getSearchSchema().getSqlDialect().limitRows(f, 100);
            rs = Table.executeQuery(getSearchSchema(), sel);
            List<String> paths = new ArrayList<String>(100);

            while (rs.next())
            {
                count++;
                String path = rs.getString(1);
                java.sql.Timestamp nextCrawl = rs.getTimestamp(2);
                paths.add(path);
                boolean full = nextCrawl.getTime() < oldDate.getTime();
                fullCrawl |= full;
                _log.debug("reindex: " + path);
                new IndexDirectoryJob(getSearchService().defaultTask(), Path.parse(path), full).submit();
            }
            
            if (count == 0)
            {
                _log.debug("no work to do");

            }
            else
            {
                // UPDATE LastCrawled so we won't try to crawl for a while
                SQLFragment upd = new SQLFragment(
                        "UPDATE search.CrawlCollections SET LastCrawled=? WHERE Path IN ",
                        now);
                String comma = "(";
                for (String path : paths)
                {
                    upd.append(comma).append('?');
                    upd.add(path);
                    comma = ",";
                }
                upd.append(")");
                Table.execute(getSearchSchema(), upd);
            }
        }
        catch (SQLException x)
        {
            _log.error("Unexpected exception", x);
            throw new RuntimeSQLException(x);
        }
        finally
        {
            ResultSetUtil.close(rs);
            delay = (fullCrawl || count > 0) ? 0 : TimeUnit.SECONDS.toMillis(60);
        }
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


    public void updateLastIndexed(Path path, long time)
    {
        String s = toPathString(path);
        try
        {
            int count = Table.execute(getSearchSchema(),
                    "UPDATE search.CrawlResources SET LastIndexed=? WHERE Path=?",
                    new Object[] {time, s});
            if (count == 0)
            {

                SearchService.IndexTask task = getSearchService().defaultTask();
                new IndexDirectoryJob(task, path.getParent(), false).submit();
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }
}
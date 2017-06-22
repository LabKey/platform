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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.search.SearchService;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.springframework.dao.DuplicateKeyException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * User: matthewb
 * Date: Dec 12, 2009
 * Time: 9:38:46 AM
 */
public class SavePaths implements DavCrawler.SavePaths
{
    final private long _startupTime = System.currentTimeMillis();
    

    SQLFragment pathFilter(TableInfo ti, String path)
    {
        SQLFragment f = new SQLFragment(" Path=? ", path);
        if (null != ti.getColumn("csPath"))
        {
            f.append(" AND csPath=CHECKSUM(?)");
            f.add(path);
        }
        return f;
    }

    SQLFragment pathFilter(int parentId, String name)
    {
        return new SQLFragment(" Parent=? AND Name=?", parentId, name);
    }

    //
    // FOLDER/RESOURCES
    //

    private boolean _update(Path path, @Nullable java.util.Date last, @Nullable java.util.Date next) throws SQLException
    {
        String pathStr = toPathString(path);
        if (null == last) last = nullDate;
        if (null == next) next = oldDate;

        DbSchema search = getSearchSchema();
        SqlDialect d = search.getSqlDialect();

        SQLFragment upd = new SQLFragment(
                String.format("UPDATE search.CrawlCollections %s SET LastCrawled=?, NextCrawl=? ",
                        d.isSqlServer() ? "WITH (UPDLOCK)" : ""),
                last, next);
        upd.append(" WHERE " );
        SQLFragment f = pathFilter(getSearchSchema().getTable("CrawlCollections"), pathStr);
        upd.append(f);

        int count = new SqlExecutor(getSearchSchema()).execute(upd);
        return count > 0;
    }


    private static String toPathString(Path path)
    {
        return path.toString("/", "/");
    }


//    Cache<Path,Integer> idcache = CacheManager.getBlockingCache(1000, TimeUnit.MINUTES.toMillis(5), "SavePaths: path to id cache", new CacheLoader<Path,Integer>()
//    {
//        @Override
//        public Integer load(Path path, @Nullable Object argument)
//        {
//            String pathStr = toPathString(path);
//            SQLFragment find = new SQLFragment("SELECT id FROM search.CrawlCollections WHERE ");
//            find.append(pathFilter(getSearchSchema().getTable("CrawlCollections"), pathStr));
//            Integer id = new SqlSelector(getSearchSchema(), find).getObject(Integer.class);
//            return null != id ? id : -1;
//        }
//    });


    // NOTE: not using a blocking cache since one request can cause multiple entries to be loaded
    Cache<Path,Integer> idcache = CacheManager.getCache(10_000, TimeUnit.MINUTES.toMillis(5), "SavePaths: path to id cache");


    // -1 if not exists
    private synchronized int getId(Path path) throws SQLException
    {
        Integer id = idcache.get(path);
        if (null != id)
            return id;
        int parentId = _getParentId(path);
        String name = path.size() == 0 ? "/" : path.getName();

        SQLFragment find = new SQLFragment("SELECT id FROM search.CrawlCollections WHERE ");
        find.append(pathFilter(parentId, name));
        id = new SqlSelector(getSearchSchema(), find).getObject(Integer.class);
        return null != id ? id : -1;
    }


    // create if not exists
    private synchronized int _getParentId(Path path) throws SQLException
    {
        Path parent = path.getParent();
        if (null == parent)
            return 0;

        int id = getId(path.getParent());
        if (id != -1)
            return id;

        // insert parent
        return _ensure(parent);
    }






    private int _ensure(Path path) throws SQLException
    {
        // Mostly I don't care about Parent
        // However, we need this for the primary key
        int valueParent = _getParentId(path);

        String valuePath = toPathString(path);
        String valueName = path.equals(Path.rootPath) ? "/" : path.getName();   // "" is treated like NULL
        Date valueNextCrawl = new Date();
        Date valueLastCrawled = nullDate;

        DbSchema db = getSearchSchema();
        TableInfo coll = getSearchSchema().getTable("CrawlCollections");
        ColumnInfo id = coll.getColumn("id");

        try
        {
            SQLFragment insert = new SQLFragment(
                    "INSERT INTO search.crawlcollections (parent, name, path, lastcrawled, nextcrawl)\n" +
                    "SELECT ? as parent, ? as name, ? as path, ? as lastcrawled, ? as nextcrawl\n" +
                    "WHERE NOT EXISTS (SELECT * FROM search.crawlcollections WHERE parent=? and name=?)");
            // values
            insert.add(valueParent);
            insert.add(valueName);
            insert.add(valuePath);
            insert.add(valueNextCrawl);
            insert.add(valueLastCrawled);
            // where
            insert.add(valueParent);
            insert.add(valueName);
            db.getSqlDialect().addReselect(insert, id, null);

            Integer ident = new SqlSelector(getSearchSchema(),insert).getObject(Integer.class);
            if (null == ident)
                return getId(path);
            return ident;
        }
        catch (DuplicateKeyException | RuntimeSQLException x)
        {
            if (x instanceof DuplicateKeyException || ((RuntimeSQLException) x).isConstraintException())
            {
                return getId(path);
            }
            else
            {
                throw x;
            }
        }
    }


    public boolean insertPath(Path path, Date nextCrawl)
    {
        try
        {
            // Mostly I don't care about Parent
            // However, we need this for the primary key
            int parent = _getParentId(path);
            if (nextCrawl == null)
                nextCrawl = new Date(System.currentTimeMillis()+5*60000);

            String pathStr = toPathString(path);
            SQLFragment f = new SQLFragment(
                    "INSERT INTO search.crawlcollections (Path,Name,Parent,NextCrawl,LastCrawled) " +
                    "SELECT ?,?,?,?,? " +
                    "WHERE NOT EXISTS (SELECT Path FROM search.crawlcollections WHERE ");
            f.add(pathStr);
            f.add(path.equals(Path.rootPath) ? "/" : path.getName());   // "" is treated like NULL
            f.add(parent);
            f.add(nextCrawl);
            f.add(nullDate);
            f.append(pathFilter(getSearchSchema().getTable("CrawlCollections"), pathStr));
            f.append(")");
            int count = new SqlExecutor(getSearchSchema()).execute(f);
            return count==1;
        }
        catch (SQLException x)
        {
            if (RuntimeSQLException.isConstraintException(x))
            {
                return false;
            }
            else
            {
                throw new RuntimeSQLException(x);
            }
        }
    }


    public boolean updatePath(Path path, java.util.Date last, java.util.Date next, boolean create)
    {
        try
        {
            boolean success = _update(path,last,next);
            if (!success && create)
            {
                _ensure(path);
                success = _update(path,last,next);
            }
            return success;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }
    

    public void updatePrefix(Path path, Date next, boolean forceIndex)
    {
        if (next == null)
            next = oldDate;

        SqlExecutor executor = new SqlExecutor(getSearchSchema());

        if (forceIndex)
        {
            executor.execute(
                    "UPDATE search.CrawlResources SET LastIndexed=NULL " +
                    "WHERE Parent IN (SELECT id FROM search.CrawlCollections " +
                    "  WHERE Path LIKE ?)",
                    toPathString(path) + "%");
        }

        executor.execute(
                "UPDATE search.CrawlCollections " +
                "SET LastCrawled=NULL, NextCrawl=? " +
                "WHERE Path LIKE ?",
                // UNDONE LIKE ESCAPE
                next, toPathString(path) + "%");
    }

    
    public void clearFailedDocuments()
    {
        assert failDate.getTime() < oldDate.getTime();
        new SqlExecutor(getSearchSchema()).execute(
                "UPDATE search.CrawlResources SET LastIndexed=NULL WHERE LastIndexed<?",
                oldDate);
    }


    public void deletePath(Path path)
    {
        // UNDONE LIKE ESCAPE
        new SqlExecutor(getSearchSchema()).execute("DELETE FROM search.CrawlResources WHERE Parent IN (SELECT id FROM search.CrawlCollections WHERE Path LIKE ?)", toPathString(path) + "%");
        new SqlExecutor(getSearchSchema()).execute("DELETE FROM search.CrawlCollections WHERE Path LIKE ?", toPathString(path) + "%");
        idcache.clear();
    }


    public Date getNextCrawl()
    {
        SQLFragment f = new SQLFragment("SELECT MIN(NextCrawl) FROM search.CrawlCollections WHERE LastCrawled IS NULL OR LastCrawled < ?");
        f.add(System.currentTimeMillis() - 30*60000);
        return new SqlSelector(getSearchSchema(), f).getObject(Timestamp.class);
    }


    public Map<Path, Pair<Date,Date>> getPaths(int limit)
    {
        Date now = new Date(System.currentTimeMillis());
        Date awhileago = new Date(Math.max(_startupTime, now.getTime() - 30*60000));

        SqlDialect dialect = getSearchSchema().getSqlDialect();
        SQLFragment f = new SQLFragment(
                "SELECT Parent, Name, Path, LastCrawled, NextCrawl\n" +
                "FROM search.CrawlCollections\n");
        f.append("WHERE NextCrawl < ? AND (LastCrawled IS NULL OR LastCrawled < ?) " +
                "ORDER BY NextCrawl");
        f.add(now);
        f.add(awhileago);
        SQLFragment sel = dialect.limitRows(f, limit);

        try
        {
            Map<Path,Pair<Date,Date>> map = new LinkedHashMap<>();
            ArrayList<String> paths = new ArrayList<>(limit);
            String or = " WHERE ";
            SQLFragment updWHERE = new SQLFragment();

            try (ResultSet rs = new SqlSelector(getSearchSchema(), sel).getResultSet())
            {
                while (rs.next())
                {
                    int parent = rs.getInt(1);
                    String name = rs.getString(2);
                    String path = rs.getString(3);

                    java.sql.Timestamp lastCrawl = rs.getTimestamp(4);
                    java.sql.Timestamp nextCrawl = rs.getTimestamp(5);
                    map.put(Path.parse(path), new Pair<Date, Date>(lastCrawl, nextCrawl));
                    paths.add(path);

                    updWHERE.append(or).append(" (Parent=? AND Name=?)");
                    updWHERE.add(parent);
                    updWHERE.add(name);
                    or = " OR ";
                }
            }

            // UPDATE LastCrawled so we won't try to crawl for a while
            if (!paths.isEmpty())
            {
                SQLFragment upd = new SQLFragment(
                        "UPDATE search.CrawlCollections " + (getSearchSchema().getSqlDialect().isSqlServer() ? " WITH (UPDLOCK)" : "") + "\n" +
                        "SET LastCrawled=?", now);
                upd.append(updWHERE);
                new SqlExecutor(getSearchSchema()).execute(upd);
            }
            return map;
        }
        catch (SQLException x)
        {
            if (!SqlDialect.isTransactionException(x))
                throw new RuntimeSQLException(x);
            return Collections.emptyMap();
        }
    }


    //
    // FILES/RESOURCES
    //

    public Map<String, DavCrawler.ResourceInfo> getFiles(Path path)
    {
        SQLFragment s = new SQLFragment(
                "SELECT D.ChangeInterval, D.Path, D.id, F.Name, F.Modified, F.LastIndexed\n" +
                "FROM search.CrawlCollections D LEFT OUTER JOIN search.CrawlResources F on D.id=F.parent\n" +
                "WHERE D.path = ?");
        s.add(toPathString(path));

        final Map<String,DavCrawler.ResourceInfo> map = new HashMap<>();

        new SqlSelector(getSearchSchema(), s).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                String name = rs.getString("Name");
                if (null == name)
                    return;
                Date modified = rs.getTimestamp("Modified");
                Date lastIndex = rs.getTimestamp("LastIndexed");
                map.put(name, new DavCrawler.ResourceInfo(lastIndex, modified));
            }
        });

        return map;
    }

    String datetime = null;

    public boolean updateFile(@NotNull Path path, @NotNull Date lastIndexed, Date modified)
    {
        try
        {
            if (null == datetime)
                datetime = getSearchSchema().getSqlDialect().getDefaultDateTimeDataType();
            if (modified.getTime() == Long.MIN_VALUE)
                modified = null;
            int id = _getParentId(path);
            SQLFragment upd = new SQLFragment(
                    "UPDATE search.CrawlResources SET LastIndexed=?, Modified=CAST(? AS " + datetime + ") WHERE Parent=? AND Name=?",
                    lastIndexed, modified, id, path.getName());
            int count = new SqlExecutor(getSearchSchema()).execute(upd);
            if (count > 0)
                return true;
            SQLFragment ins = new SQLFragment(
                    "INSERT INTO search.CrawlResources(Parent,Name,LastIndexed, Modified) VALUES (?,?,?,CAST(? AS " + datetime + "))",
                    id, path.getName(), lastIndexed, modified);
            count = new SqlExecutor(getSearchSchema()).execute(ins);
            return count > 0;
        }
        catch (SQLException x)
        {

            if (RuntimeSQLException.isConstraintException(x))
            {
                return false;
            }
            else
            {
                throw new RuntimeSQLException(x);
            }
        }
    }


    private static DbSchema getSearchSchema()
    {
        SearchService ss = SearchService.get();

        if (null != ss)
            return ss.getSchema();
        else
            return null;
    }
}

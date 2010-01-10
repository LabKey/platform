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

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.ResultSetUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Dec 12, 2009
 * Time: 9:38:46 AM
 */
public class SavePaths implements DavCrawler.SavePaths
{
    static SQLFragment _emptyFragment = new SQLFragment();
    static java.util.Date _futureDate = new Date(DateUtil.parseDateTime("3000-01-01"));
    
    SQLFragment csPath(TableInfo ti, String path)
    {
        if (null == ti.getColumn("csPath"))
            return _emptyFragment;
        return new SQLFragment(" AND csPath=CHECKSUM(?)", path);
    }



    //
    // FOLDER/RESOURCES
    //

    private boolean _update(Path path, java.util.Date last, java.util.Date next) throws SQLException
    {
        String pathStr = toPathString(path);
        if (null == last) last = nullDate;
        if (null == next) next = nullDate;
        SQLFragment upd = new SQLFragment(
                "UPDATE search.CrawlCollections SET LastCrawled=?, NextCrawl=? WHERE Path=?",
                last, next, pathStr);
        SQLFragment cs = csPath(getSearchSchema().getTable("CrawlCollections"), pathStr);
        upd.append(cs);
        
        int count = Table.execute(getSearchSchema(), upd);
        return count > 0;
    }


    private static String toPathString(Path path)
    {
        return path.toString("/", "/");
    }


    // -1 if not exists
    private int getId(Path path) throws SQLException
    {
        // find parent
        String pathStr = toPathString(path);
        SQLFragment find = new SQLFragment("SELECT id FROM search.CrawlCollections WHERE Path=?", pathStr);
        SQLFragment cs = csPath(getSearchSchema().getTable("CrawlCollections"), pathStr);
        find.append(cs);
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
    private int _getParentId(Path path) throws SQLException
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
        int parent = _getParentId(path);

        CaseInsensitiveHashMap<Object> map = new CaseInsensitiveHashMap<Object>();
        map.put("Path", toPathString(path));
        map.put("Name", path.equals(Path.rootPath) ? "/" : path.getName());   // "" is treated like NULL
        map.put("Parent", parent);
        map.put("NextCrawl", _futureDate);
        map.put("LastCrawled", _futureDate);
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


    public boolean insertPath(Path path, Date nextCrawl)
    {
        try
        {
        // Mostly I don't care about Parent
            // However, we need this for the primary key
            int parent = _getParentId(path);
            if (nextCrawl == null)
                nextCrawl = new Date(System.currentTimeMillis()+5*60000);

            CaseInsensitiveHashMap<Object> map = new CaseInsensitiveHashMap<Object>();
            map.put("Path", toPathString(path));
            map.put("Name", path.equals(Path.rootPath) ? "/" : path.getName());   // "" is treated like NULL
            map.put("Parent", parent);
            map.put("NextCrawl", nextCrawl);
            map.put("LastCrawled", nullDate);
            map = Table.insert(User.getSearchUser(), getSearchSchema().getTable("CrawlCollections"), map);
            return true;
        }
        catch (SQLException x)
        {
            if (SqlDialect.isConstraintException(x))
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
    

    public void updatePrefix(Path path, java.util.Date last, java.util.Date next, boolean forceIndex)
    {
        try
        {
            if (forceIndex)
            {
                Table.execute(getSearchSchema(),
                        "UPDATE search.CrawlResources SET LastIndexed=?" +
                        "WHERE Parent IN (SELECT id FROM search.CrawlCollections " +
                        "  WHERE Path LIKE ?)",
                        new Object[]{nullDate, toPathString(path) + "%"});
            }
            Table.execute(getSearchSchema(),
                    "UPDATE search.CrawlCollections " +
                    "SET LastCrawled=NULL, NextCrawl=? " +
                    "WHERE Path LIKE ?",
                    // UNDONE LIKE ESCAPE
                    new Object[]{nullDate, toPathString(path) + "%"});
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public void deletePath(Path path)
    {
        try
        {
            // UNDONE LIKE ESCAPE
            Table.execute(getSearchSchema(), "DELETE FROM search.CrawlResources WHERE Parent IN (SELECT id FROM search.CrawlCollections WHERE Path LIKE ?)", new Object[]{toPathString(path) + "%"});
            Table.execute(getSearchSchema(), "DELETE FROM search.CrawlCollections WHERE Path LIKE ?", new Object[]{toPathString(path) + "%"});
        }
        catch (SQLException x)
        {
            throw new RuntimeException(x);
        }
    }


    public Date getNextCrawl()
    {
        try
        {
            SQLFragment f = new SQLFragment("SELECT MIN(NextCrawl) FROM search.CrawlCollections WHERE LastCrawled IS NULL OR LastCrawled < ?");
            f.add(System.currentTimeMillis() - 30*60000);
            Timestamp t = Table.executeSingleton(getSearchSchema(), f.getSQL(), f.getParamsArray(), java.sql.Timestamp.class);
            return t;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public Map<Path, Pair<Date,Date>> getPaths(int limit)
    {
        java.sql.Timestamp now = new Timestamp(System.currentTimeMillis());
        java.sql.Timestamp awhileago = new Timestamp(now.getTime() - 30*60000);

        SQLFragment f = new SQLFragment(
                "SELECT Path, LastCrawled, NextCrawl\n" +
                "FROM search.CrawlCollections\n" +
                "WHERE NextCrawl < ? AND (LastCrawled IS NULL OR LastCrawled < ?) " +
                "ORDER BY NextCrawl",
                now, awhileago);
        SQLFragment sel = getSearchSchema().getSqlDialect().limitRows(f, limit);

        ResultSet rs = null;
        try
        {
            Map<Path,Pair<Date,Date>> map = new LinkedHashMap<Path,Pair<Date,Date>>();
            ArrayList<String> paths = new ArrayList<String>(limit);

            rs = Table.executeQuery(getSearchSchema(), sel);

            while (rs.next())
            {
                String path = rs.getString(1);
                java.sql.Timestamp lastCrawl = rs.getTimestamp(2);
                java.sql.Timestamp nextCrawl = rs.getTimestamp(3);
                map.put(Path.parse(path),new Pair(lastCrawl,nextCrawl));
                paths.add(path);
            }
            rs.close();
            rs = null;

            // UPDATE LastCrawled so we won't try to crawl for a while
            // TODO csPath
            if (!paths.isEmpty())
            {
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
            return map;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }


    //
    // FILES/RESOURCES
    //

    public Map<String,Date> getFiles(Path path)
    {
        SQLFragment s = new SQLFragment(
                "SELECT D.ChangeInterval, D.Path, D.id, F.Name, F.Modified, F.LastIndexed\n" +
                "FROM search.CrawlCollections D LEFT OUTER JOIN search.CrawlResources F on D.id=F.parent\n" +
                "WHERE D.path = ?");
        s.add(toPathString(path));

        Map<String,Date> map = new HashMap<String, Date>();
        CachedRowSetImpl rs = null;
        try
        {
            rs = (CachedRowSetImpl)Table.executeQuery(getSearchSchema(), s);
            while (rs.next())
            {
                String name = rs.getString("Name");
                if (null == name)
                    continue;
                Date lastIndex = rs.getTimestamp("LastIndexed");
                map.put(name, lastIndex);
            }
            rs.close();
            rs = null;
            return map;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }


    public boolean updateFile(Path path, Date lastIndexed)
    {
        try
        {
            int id = _getParentId(path);
            SQLFragment upd = new SQLFragment(
                    "UPDATE search.CrawlResources SET LastIndexed=? WHERE Parent=? AND Name=?",
                    lastIndexed, id, path.getName());
            int count = Table.execute(getSearchSchema(), upd);
            if (count > 0)
                return true;
            SQLFragment ins = new SQLFragment(
                    "INSERT INTO search.CrawlResources(Parent,Name,LastIndexed) VALUES (?,?,?)",
                    id, path.getName(), lastIndexed);
            count = Table.execute(getSearchSchema(), ins);
            return count > 0;
        }
        catch (SQLException x)
        {

            if (SqlDialect.isConstraintException(x))
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
        return DbSchema.get("search");
    }
}

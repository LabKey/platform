package org.labkey.search.model;

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.util.Path;
import org.labkey.api.util.ResultSetUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Dec 12, 2009
 * Time: 9:38:46 AM
 */
public class SavePaths implements DavCrawler.SavePaths
{
    static SQLFragment _emptyFragment = new SQLFragment();
    
    SQLFragment csPath(TableInfo ti, String path)
    {
        if (null == ti.getColumn("csPath"))
            return _emptyFragment;
        return new SQLFragment(" AND csPath=CHECKSUM(?)", path);
    }


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
        return _insert(parent);
    }


    private int _insert(Path path) throws SQLException
    {
        // Mostly I don't care about Parent
        // However, we need this for the primary key
        int parent = _getParentId(path);

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


    public boolean updatePath(Path path, java.util.Date last, java.util.Date next, boolean create)
    {
        try
        {
            boolean success = _update(path,last,next);
            if (!success && create)
            {
                _insert(path);
                success = _update(path,last,next);
            }
            return success;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    
    public void updatePrefix(Path path, java.util.Date last, java.util.Date next)
    {
        try
        {
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
    }


    public void deletePath(Path path)
    {
        try
        {
            Table.execute(getSearchSchema(), "DELETE FROM search.CrawlCollections WHERE Path=?", new Object[]{toPathString(path)});
        }
        catch (SQLException x)
        {
            throw new RuntimeException(x);
        }
    }



    public Map<Path, Date> getPaths(int limit)
    {
        java.sql.Timestamp now = new Timestamp(System.currentTimeMillis());
        java.sql.Timestamp awhileago = new Timestamp(now.getTime() - 30*60000);

        SQLFragment f = new SQLFragment(
                "SELECT Path, NextCrawl\n" +
                "FROM search.CrawlCollections\n" +
                "WHERE NextCrawl < ? AND (LastCrawled IS NULL OR LastCrawled < ?) " +
                "ORDER BY NextCrawl",
                now, awhileago);
        SQLFragment sel = getSearchSchema().getSqlDialect().limitRows(f, limit);

        ResultSet rs = null;
        try
        {
            Map<Path,Date> map = new HashMap<Path,Date>();
            ArrayList<String> paths = new ArrayList<String>(limit);
            
            rs = Table.executeQuery(getSearchSchema(), sel);

            while (rs.next())
            {
                String path = rs.getString(1);
                java.sql.Timestamp nextCrawl = rs.getTimestamp(2);
                map.put(Path.parse(path),nextCrawl);
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
                if (rs.wasNull())
                    continue;
                Date lastIndex = rs.getDate("LastIndexed");
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
        return true;
    }


    private static DbSchema getSearchSchema()
    {
        return DbSchema.get("search");
    }
}

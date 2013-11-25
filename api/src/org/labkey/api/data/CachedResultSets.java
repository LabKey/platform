package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.collections.RowMap;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: 11/22/13
 * Time: 11:12 PM
 */

/*
    Factory methods that create CachedResultSets, plus a couple helpers
 */
public class CachedResultSets
{
    public static CachedResultSet create(ResultSet rs, boolean cacheMetaData, int maxRows) throws SQLException
    {
        return create(rs, cacheMetaData, maxRows, null);
    }


    public static CachedResultSet create(ResultSet rs, boolean cacheMetaData, int maxRows, @Nullable StackTraceElement[] stackTrace) throws SQLException
    {
        ArrayList<RowMap<Object>> list = new ArrayList<>();

        if (maxRows == Table.ALL_ROWS)
            maxRows = Integer.MAX_VALUE;

        ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);

        // Note: we check in this order to avoid consuming the "extra" row used to detect complete vs. not
        while (list.size() < maxRows && rs.next())
            list.add(factory.getRowMap(rs));

        // If we have another row, then we're not complete
        boolean isComplete = !rs.next();

        return new CachedResultSet(rs.getMetaData(), cacheMetaData, list, isComplete, stackTrace);
    }


    public static CachedResultSet create(ResultSetMetaData md, boolean cacheMetaData, List<Map<String, Object>> maps, boolean isComplete)
    {
        return new CachedResultSet(md, cacheMetaData, convertToRowMaps(md, maps), isComplete, null);
    }


    public static CachedResultSet create(List<Map<String, Object>> maps)
    {
        return create(maps, maps.get(0).keySet());
    }


    public static CachedResultSet create(List<Map<String, Object>> maps, Collection<String> columnNames)
    {
        ResultSetMetaData md = createMetaData(columnNames);

        CachedResultSet crs = new CachedResultSet(md, false, convertToRowMaps(md, maps), true, null);

        try
        {
            // Avoid error message from CachedResultSet.finalize() about unclosed CachedResultSet.
            crs.close();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        return crs;
    }


    private static ResultSetMetaData createMetaData(Collection<String> columnNames)
    {
        ResultSetMetaDataImpl md = new ResultSetMetaDataImpl(columnNames.size());
        for (String columnName : columnNames)
        {
            ResultSetMetaDataImpl.ColumnMetaData col = new ResultSetMetaDataImpl.ColumnMetaData();
            col.columnName = columnName;
            md.addColumn(col);
        }

        return md;
    }


    private static ArrayList<RowMap<Object>> convertToRowMaps(ResultSetMetaData md, List<Map<String, Object>> maps)
    {
        ArrayList<RowMap<Object>> list = new ArrayList<>();

        ResultSetRowMapFactory factory;
        try
        {
            factory = ResultSetRowMapFactory.create(md);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        for (Map<String, Object> map : maps)
        {
            list.add(factory.getRowMap(map));
        }

        return list;
    }
}

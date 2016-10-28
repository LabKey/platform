/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
 * Factory methods that create CachedResultSets, plus a couple helpers
 * User: adam
 * Date: 11/22/13
 */
public class CachedResultSets
{
    public static CachedResultSet create(ResultSet rs, boolean cacheMetaData, int maxRows) throws SQLException
    {
        return create(rs, cacheMetaData, maxRows, null, QueryLogging.emptyQueryLogging());      // TODO: Should be only for MetaData??
    }


    public static CachedResultSet create(ResultSet rsIn, boolean cacheMetaData, int maxRows, @Nullable StackTraceElement[] stackTrace, QueryLogging queryLogging) throws SQLException
    {
        try (ResultSet rs = new LoggingResultSetWrapper(rsIn, queryLogging))         // TODO: avoid is we're passed a read-only and empty one??
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

        // Avoid error message from CachedResultSet.finalize() about unclosed CachedResultSet.
        crs.close();

        return crs;
    }


    private static ResultSetMetaData createMetaData(Collection<String> columnNames)
    {
        ResultSetMetaDataImpl md = new ResultSetMetaDataImpl(columnNames.size());
        for (String columnName : columnNames)
        {
            ResultSetMetaDataImpl.ColumnMetaData col = new ResultSetMetaDataImpl.ColumnMetaData();
            col.columnName = columnName;
            col.columnLabel = columnName;
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

/*
 * Copyright (c) 2013-2018 LabKey Corporation
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
 * Builder for CachedResultSets, given a {@code ResultSet} or a {@code List<Map<String, Object>>}
 */
public abstract class CachedResultSetBuilder<C extends CachedResultSetBuilder<C>>
{
    protected boolean _requireClose = true;
    protected StackTraceElement[] _stackTrace = null;

    public static FromResultSet create(ResultSet rs)
    {
        return new FromResultSet(rs);
    }

    public static FromListOfMaps create(List<Map<String, Object>> maps)
    {
        return create(maps, maps.get(0).keySet());
    }

    /**
     * Create CachedResultSetBuilder from a list of maps and collection of column names. For the most flexibility, the
     * maps may need to be case-insensitive. How do you tell? If the maps have data and the keys match the columnNames,
     * but the ResultSet rowMap values are all null.
     * @param maps List of row data, possibly case-insensitive maps
     * @param columnNames Collection of column names
     *
     * TODO: A case-insensitive option for the builder, but there may be performance impact for very large result sets
     * if the implementation were simply to wrap each incoming map with CaseInsensitiveHashMap. For now, onus is on the
     * caller to provide case insensitive maps when necessary.
     */
    public static FromListOfMaps create(List<Map<String, Object>> maps, Collection<String> columnNames)
    {
        return new FromListOfMaps(maps, columnNames);
    }

    public abstract C getThis();

    public C setRequireClose(boolean requireClose)
    {
        _requireClose = requireClose;
        return getThis();
    }

    public C setStackTrace(StackTraceElement[] stackTrace)
    {
        _stackTrace = stackTrace;
        return getThis();
    }

    public final static class FromResultSet extends CachedResultSetBuilder<FromResultSet>
    {
        private final ResultSet _rs;

        private int _maxRows = Table.ALL_ROWS;
        private QueryLogging _queryLogging = QueryLogging.emptyQueryLogging();

        private FromResultSet(ResultSet rs)
        {
            _rs = rs;
        }

        @Override
        public FromResultSet getThis()
        {
            return this;
        }

        public FromResultSet setMaxRows(int maxRows)
        {
            _maxRows = maxRows;
            return this;
        }

        public FromResultSet setQueryLogging(QueryLogging queryLogging)
        {
            _queryLogging = queryLogging;
            return this;
        }

        public CachedResultSet build() throws SQLException
        {
            try (ResultSet rs = new LoggingResultSetWrapper(_rs, _queryLogging))         // TODO: avoid if we're passed a read-only and empty one??
            {
                // Snowflake auto-closes metadata after reading the last row, so cache that metadata first
                ResultSetMetaData md = new CachedResultSetMetaData(rs.getMetaData());

                ArrayList<RowMap<Object>> list = new ArrayList<>();

                if (_maxRows == Table.ALL_ROWS)
                    _maxRows = Integer.MAX_VALUE;

                ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);

                // Note: we check in this order to avoid consuming the "extra" row used to detect complete vs. not
                while (list.size() < _maxRows && rs.next())
                    list.add(factory.getRowMap(rs));

                // If we have another row, then we're not complete
                boolean isComplete = !rs.next();

                return new CachedResultSet(md, list, isComplete, _requireClose, _stackTrace);
            }
        }
    }

    public final static class FromListOfMaps extends CachedResultSetBuilder<FromListOfMaps>
    {
        private final List<Map<String, Object>> _maps;
        private final Collection<String> _columnNames;

        private ResultSetMetaData _md = null;
        private boolean _isComplete = true;

        private FromListOfMaps(List<Map<String, Object>> maps, Collection<String> columnNames)
        {
            _maps = maps;
            _columnNames = columnNames;
        }

        @Override
        public FromListOfMaps getThis()
        {
            return this;
        }

        public FromListOfMaps setMetaData(ResultSetMetaData md)
        {
            _md = md;
            return this;
        }

        public FromListOfMaps setComplete(boolean complete)
        {
            _isComplete = complete;
            return this;
        }

        public CachedResultSet build()
        {
            if (_md == null)
                _md = createMetaData(_columnNames);

            return new CachedResultSet(_md, convertToRowMaps(_md, _maps), _isComplete, _requireClose, _stackTrace);
        }
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

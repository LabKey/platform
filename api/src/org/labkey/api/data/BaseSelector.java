/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.data.Table.TableResultSet;
import org.labkey.api.data.dialect.SqlDialect;

import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseSelector<FACTORY extends SqlFactory, SELECTOR extends BaseSelector<FACTORY, SELECTOR>> extends JdbcCommand implements Selector
{
    protected int _maxRows = Table.ALL_ROWS;
    protected long _offset = Table.NO_OFFSET;
    protected @Nullable Map<String, Object> _namedParameters = null;

    // SQL factory used for the duration of a single query. This helps Selector reuse, since query-specific optimizations
    // won't mutate the Selector's externally set state.
    abstract protected FACTORY getSqlFactory();

    // SQL factory used for ResultSet / Results (has different maxRows handling to support isComplete())
    abstract protected FACTORY getResultSetSqlFactory();

    protected BaseSelector(DbScope scope)
    {
        super(scope, null);
    }

    // SELECTOR and getThis() make it easier to chain setMaxRows() and setOffset() while returning the correct selector type from subclasses
    abstract protected SELECTOR getThis();

    public SELECTOR setMaxRows(int maxRows)
    {
        assert Table.validMaxRows(maxRows) : maxRows + " is an illegal value for maxRows; should be positive, Table.ALL_ROWS or Table.NO_ROWS";

        _maxRows = maxRows;
        return getThis();
    }

    public SELECTOR setOffset(long offset)
    {
        assert Table.validOffset(offset) : offset + " is an illegal value for offset; should be positive or Table.NO_OFFSET";

        _offset = offset;
        return getThis();
    }

    public SELECTOR setNamedParameters(@Nullable Map<String, Object> namedParameters)
    {
        _namedParameters = namedParameters;
        return getThis();
    }

    // Standard internal handleResultSet method used by everything except ResultSets
    private <K> K handleResultSet(SqlFactory sqlFactory, ResultSetHandler<K> handler)
    {
        return handleResultSet(sqlFactory, handler, true, false, false);
    }

    private <K> K handleResultSet(SqlFactory sqlFactory, ResultSetHandler<K> handler, boolean closeOnSuccess, boolean scrollable, boolean tweakJdbcParameters)
    {
        DbScope scope = getScope();
        SQLFragment sql = sqlFactory.getSql();
        boolean queryFailed = false;

        Connection conn = null;
        ResultSet rs = null;

        try
        {
            conn = getConnection();

            if (tweakJdbcParameters && Table.isSelect(sql.getSQL()) && !scope.isTransactionActive())
            {
                // Only fiddle with the Connection settings if we're not inside of a transaction so we won't mess
                // up any state the caller is relying on. Also, only do this when we're fairly certain that it's
                // a read-only statement (starting with SELECT)
                scope.getSqlDialect().configureToDisableJdbcCaching(conn);
            }

            rs = Table._executeQuery(conn, sql.getSQL(), sql.getParamsArray(), scrollable, getAsyncRequest(), sqlFactory.getStatementMaxRows());
            sqlFactory.processResultSet(rs);

            return handler.handle(rs, conn, scope);
        }
        catch(SQLException e)
        {
            // TODO: Substitute SQL parameter placeholders with values?
            Table.logException(sql.getSQL(), sql.getParamsArray(), conn, e, getLogLevel());
            queryFailed = true;
            throw getExceptionFramework().translate(getScope(), "Message", sql.getSQL(), e);  // TODO: Change message
        }
        finally
        {
            if (closeOnSuccess || queryFailed)
                close(rs, conn);
        }
    }

    protected Table.TableResultSet getResultSet(SqlFactory sqlFactory, boolean scrollable, boolean cache) throws SQLException
    {
        if (cache)
        {
            return handleResultSet(sqlFactory, new ResultSetHandler<TableResultSet>()
            {
                @Override
                public TableResultSet handle(ResultSet rs, Connection conn, DbScope scope) throws SQLException
                {
                    return Table.cacheResultSet(scope.getSqlDialect(), rs, _maxRows, getLoggingStacktrace());
                }
            }, true, scrollable, true);
        }
        else
        {
            return handleResultSet(sqlFactory, new ResultSetHandler<TableResultSet>()
            {
                @Override
                public TableResultSet handle(ResultSet rs, Connection conn, DbScope scope) throws SQLException
                {
                    return new ResultSetImpl(conn, scope, rs, _maxRows);
                }
            }, false, scrollable, true);
        }
    }

    @Override
    public Table.TableResultSet getResultSet() throws SQLException
    {
        return getResultSet(false, true);
    }

    @Override
    public Table.TableResultSet getResultSet(boolean scrollable, boolean cache) throws SQLException
    {
        return getResultSet(getResultSetSqlFactory(), scrollable, cache);
    }

    private <K> ArrayList<K> getArrayList(final Class<K> clazz, FACTORY factory)
    {
        final ArrayList<K> list;
        final Table.Getter getter = Table.Getter.forClass(clazz);

        // This is a simple object (Number, String, Date, etc.)
        if (null != getter)
        {
            list = new ArrayList<K>();
            forEach(new ForEachBlock<ResultSet>() {
                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    //noinspection unchecked
                    list.add((K)getter.getObject(rs));
                }
            }, factory);
        }
        else
        {
            list = handleResultSet(factory, new ResultSetHandler<ArrayList<K>>()
            {
                @Override
                public ArrayList<K> handle(ResultSet rs, Connection conn, DbScope scope) throws SQLException
                {
                    if (Map.class == clazz)
                    {
                        CachedResultSet copy = (CachedResultSet) Table.cacheResultSet(getScope().getSqlDialect(), rs, Table.ALL_ROWS, null);
                        //noinspection unchecked
                        K[] arrayListMaps = (K[]) (copy._arrayListMaps == null ? new ArrayListMap[0] : copy._arrayListMaps);
                        copy.close();

                        // TODO: Not very efficient...
                        ArrayList<K> list = new ArrayList<K>(arrayListMaps.length);
                        Collections.addAll(list, arrayListMaps);
                        return list;
                    }
                    else
                    {
                        ObjectFactory<K> factory = getObjectFactory(clazz);

                        return factory.handleArrayList(rs);
                    }
                }
            });
        }

        return list;
    }

    private <K> ObjectFactory<K> getObjectFactory(Class<K> clazz)
    {
        ObjectFactory<K> factory = ObjectFactory.Registry.getFactory(clazz);

        if (null == factory)
            throw new IllegalArgumentException("Cound not find object factory for " + clazz.getSimpleName() + ".");

        return factory;
    }

    @Override
    public long getRowCount()
    {
        return getRowCount(getSqlFactory());
    }

    protected long getRowCount(FACTORY factory)
    {
        SqlFactory rowCountFactory = new RowCountSqlFactory(factory);

        return handleResultSet(rowCountFactory, new ResultSetHandler<Long>()
        {
            @Override
            public Long handle(ResultSet rs, Connection conn, DbScope scope) throws SQLException
            {
                rs.next();
                return rs.getLong(1);
            }
        });
    }

    @Override
    public boolean exists()
    {
        return exists(getSqlFactory());
    }

    protected boolean exists(FACTORY factory)
    {
        SqlFactory existsFactory = new ExistsSqlFactory(factory);

        return handleResultSet(existsFactory, new ResultSetHandler<Boolean>()
        {
            @Override
            public Boolean handle(ResultSet rs, Connection conn, DbScope scope) throws SQLException
            {
                rs.next();
                return rs.getBoolean(1);
            }
        });
    }

    @Override
    public <K> K[] getArray(Class<K> clazz)
    {
        ArrayList<K> list = getArrayList(clazz, getSqlFactory());
        return list.toArray((K[]) Array.newInstance(clazz, list.size()));
    }

    @Override
    public <K> Collection<K> getCollection(Class<K> clazz)
    {
        return getArrayList(clazz, getSqlFactory());
    }

    @Override
    public <K> K getObject(Class<K> clazz)
    {
        return getObject(clazz, getSqlFactory());
    }

    protected <K> K getObject(Class<K> clazz, FACTORY factory)
    {
        List<K> list = getArrayList(clazz, factory);

        if (list.size() == 1)
            return list.get(0);
        else if (list.isEmpty())
            return null;
        else
            throw new IllegalStateException("Query returned " + list.size() + " " + clazz.getSimpleName() + " objects; expected 1 or 0.");
    }

    @Override
    public void forEach(final ForEachBlock<ResultSet> block)
    {
        forEach(block, getSqlFactory());
    }

    protected void forEach(final ForEachBlock<ResultSet> block, FACTORY factory)
    {
        handleResultSet(factory, (new ResultSetHandler<Object>() {
            @Override
            public Object handle(ResultSet rs, Connection conn, DbScope scope) throws SQLException
            {
                while (rs.next())
                    block.exec(rs);

                return null;
            }
        }));
    }

    @Override
    public void forEachMap(final ForEachBlock<Map<String, Object>> block)
    {
        forEachMap(block, getSqlFactory());
    }

    protected void forEachMap(final ForEachBlock<Map<String, Object>> block, FACTORY factory)
    {
        handleResultSet(factory, new ResultSetHandler<Object>()
        {
            @Override
            public Object handle(ResultSet rs, Connection conn, DbScope scope) throws SQLException
            {
                ResultSetIterator iter = new ResultSetIterator(rs);

                while (iter.hasNext())
                    block.exec(iter.next());

                return null;
            }
        });
    }

    @Override
    public <K> void forEach(final ForEachBlock<K> block, Class<K> clazz)
    {
        final Table.Getter getter = Table.Getter.forClass(clazz);

        // This is a simple object (Number, String, Date, etc.)
        if (null != getter)
        {
            forEach(new ForEachBlock<ResultSet>() {
                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    //noinspection unchecked
                    block.exec((K)getter.getObject(rs));
                }
            });
        }
        else
        {
            final ObjectFactory<K> factory = getObjectFactory(clazz);

            ForEachBlock<Map<String, Object>> mapBlock = new ForEachBlock<Map<String, Object>>() {
                @Override
                public void exec(Map<String, Object> map) throws SQLException
                {
                    block.exec(factory.fromMap(map));
                }
            };

            forEachMap(mapBlock);
        }
    }

    @Override
    public Map<Object, Object> fillValueMap(final Map<Object, Object> map)
    {
        forEach(new ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                map.put(rs.getObject(1), rs.getObject(2));
            }
        });

        return map;
    }

    @Override
    public Map<Object, Object> getValueMap()
    {
        return fillValueMap(new HashMap<Object, Object>());
    }

    public interface ResultSetHandler<K>
    {
        K handle(ResultSet rs, Connection conn, DbScope scope) throws SQLException;
    }


    // Wraps the underlying factory's SQL with a SELECT COUNT(*) query
    private static class RowCountSqlFactory extends BaseSqlFactory
    {
        private final SqlFactory _factory;

        private RowCountSqlFactory(SqlFactory factory)
        {
            _factory = factory;
        }

        @Override
        public SQLFragment getSql()
        {
            SQLFragment sql = new SQLFragment("SELECT COUNT(*) FROM\n(\n");
            sql.append(_factory.getSql());
            sql.append("\n) x");

            return sql;
        }
    }


    // Wraps the underlying factory's SQL with an EXISTS query that returns true or false
    private class ExistsSqlFactory extends BaseSqlFactory
    {
        private final SqlFactory _factory;

        private ExistsSqlFactory(SqlFactory factory)
        {
            _factory = factory;
        }

        @Override
        public SQLFragment getSql()
        {
            SqlDialect dialect = getScope().getSqlDialect();

            // This EXISTS syntax works on PostgreSQL and SQL Server
            SQLFragment sql = new SQLFragment("SELECT CASE WHEN EXISTS\n(\n");
            sql.append(_factory.getSql());
            sql.append("\n)\n");
            sql.append("THEN ");
            sql.append(dialect.getBooleanTRUE());
            sql.append(" ELSE ");
            sql.append(dialect.getBooleanFALSE());
            sql.append(" END");

            return sql;
        }
    }
}

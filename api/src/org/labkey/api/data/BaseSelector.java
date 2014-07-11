/*
 * Copyright (c) 2012-2014 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.RowMap;

import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: 12/11/12
 * Time: 5:29 AM
 */

/**
 * A partial, base implementation of Selector; this class manipulates result sets but doesn't create them. Subclasses
 * execute SQL to generate result sets (TableSelector and SqlSelector), call JDBC meta data methods to generate result
 * sets (JdbcMetaDataSelector), or simply receive result sets generated elsewhere (ResultSetSelector).
 */
public abstract class BaseSelector<SELECTOR extends BaseSelector> extends JdbcCommand<SELECTOR> implements Selector
{
    protected BaseSelector(@NotNull DbScope scope, @Nullable Connection conn)
    {
        super(scope, conn);
    }

    // Used by standard enumerating methods (forEach(), getArrayList()) and their callers (getArray(), getCollection(), getObject())
    abstract protected ResultSetFactory getStandardResultSetFactory();

    // No implementation of getResultSet(), getRowCount(), or exists() here since implementations will differ widely.

    @Override
    public @NotNull <E> E[] getArray(Class<E> clazz)
    {
        ArrayList<E> list = getArrayList(clazz);
        //noinspection unchecked
        return list.toArray((E[]) Array.newInstance(clazz, list.size()));
    }

    // Convenience method that avoids "unchecked assignment" warnings
    @Override
    public @NotNull Map<String, Object>[] getMapArray()
    {
        //noinspection unchecked
        return getArray(Map.class);
    }

    @Override
    public @NotNull <E> Collection<E> getCollection(Class<E> clazz)
    {
        return getArrayList(clazz);
    }

    // Convenience method that avoids "unchecked assignment" warnings
    @Override
    public @NotNull Collection<Map<String, Object>> getMapCollection()
    {
        //noinspection unchecked
        return Arrays.asList(getMapArray());
    }

    @Override
    public @NotNull <E> ArrayList<E> getArrayList(Class<E> clazz)
    {
        return getArrayList(clazz, getStandardResultSetFactory());
    }

    protected @NotNull <E> ArrayList<E> getArrayList(final Class<E> clazz, final ResultSetFactory factory)
    {
        return handleResultSet(factory, new ArrayListResultSetHandler<>(clazz, factory));
    }


    private class ArrayListResultSetHandler<E> implements ResultSetHandler<ArrayList<E>>
    {
        private final Class<E> _clazz;
        private final ResultSetFactory _factory;

        public ArrayListResultSetHandler(Class<E> clazz, ResultSetFactory factory)
        {
            _clazz = clazz;
            _factory = factory;
        }

        @Override
        public ArrayList<E> handle(ResultSet rs, Connection conn) throws SQLException
        {
            final ArrayList<E> list;
            final Table.Getter getter = Table.Getter.forClass(_clazz);

           // If we have a Getter, then use it (simple object case: Number, String, Date, etc.)
            if (null != getter)
            {
                list = new ArrayList<>();
                forEach(new ForEachBlock<ResultSet>() {
                    @Override
                    public void exec(ResultSet rs) throws SQLException
                    {
                        //noinspection unchecked
                        list.add((E)getter.getObject(rs));
                    }
                }, _factory);
            }
            // If not, we're generating maps or beans
            else
            {
                if (Map.class == _clazz)
                {
                    ResultSetIterator iter = new ResultSetIterator(rs);
                    list = new ArrayList<>();

                    while (iter.hasNext())
                        //noinspection unchecked
                        list.add((E)iter.next());
                }
                else
                {
                    list = getObjectFactory(_clazz).handleArrayList(rs);
                }
            }

            return list;
        }
    }


    @Override
    public <T> T getObject(Class<T> clazz)
    {
        return getObject(clazz, getStandardResultSetFactory());
    }

    protected <T> T getObject(final Class<T> clazz, ResultSetFactory factory)
    {
        List<T> list = handleResultSet(factory, new ArrayListResultSetHandler<T>(clazz, factory) {
            @Override
            public ArrayList<T> handle(ResultSet rs, Connection conn) throws SQLException
            {
                ArrayList<T> list = super.handle(rs, conn);

                if (list.size() > 1)
                    throw new SQLException("Query returned " + list.size() + " " + clazz.getSimpleName() + " objects; expected 1 or 0.");

                return list;
            }
        });

        if (list.size() == 1)
            return list.get(0);
        else if (list.isEmpty())
            return null;
        else
            throw new IllegalStateException("Result set handler should have returned either 0 or 1 rows!");
    }

    @Override
    public Map<String, Object> getMap()
    {
        //noinspection unchecked
        return getObject(Map.class);
    }

    @Override
    public void forEach(final ForEachBlock<ResultSet> block)
    {
        forEach(block, getStandardResultSetFactory());
    }

    protected void forEach(final ForEachBlock<ResultSet> block, ResultSetFactory factory)
    {
        handleResultSet(factory, (new ResultSetHandler<Object>()
        {
            @Override
            public Object handle(ResultSet rs, Connection conn) throws SQLException
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
        handleResultSet(getStandardResultSetFactory(), new ResultSetHandler<Object>()
        {
            @Override
            public Object handle(ResultSet rs, Connection conn) throws SQLException
            {
                ResultSetIterator iter = new ResultSetIterator(rs);

                while (iter.hasNext())
                    block.exec(iter.next());

                return null;
            }
        });
    }

    public interface ResultSetHandler<T>
    {
        T handle(ResultSet rs, Connection conn) throws SQLException;
    }

    public interface StatementHandler<T>
    {
        T handle(PreparedStatement stmt, Connection conn) throws SQLException;
    }

    protected <T> T handleResultSet(ResultSetFactory factory, ResultSetHandler<T> handler)
    {
        boolean success = false;
        Connection conn = null;
        ResultSet rs = null;

        try
        {
            conn = getConnection();
            rs = factory.getResultSet(conn);

            T ret = handler.handle(rs, conn);
            success = true;

            return ret;
        }
        catch(RuntimeSQLException e)
        {
            factory.handleSqlException(e.getSQLException(), conn);
            throw new IllegalStateException(factory.getClass().getSimpleName() + ".handleSqlException() should have thrown an exception");
        }
        catch(SQLException e)
        {
            factory.handleSqlException(e, conn);
            throw new IllegalStateException(factory.getClass().getSimpleName() + ".handleSqlException() should have thrown an exception");
        }
        finally
        {
            if (factory.shouldClose() || !success)
                close(rs, conn);

            afterComplete(rs);
        }
    }

    @Override
    public <T> void forEach(final ForEachBlock<T> block, Class<T> clazz)
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
                    block.exec((T)getter.getObject(rs));
                }
            });
        }
        else
        {
            final ObjectFactory<T> factory = getObjectFactory(clazz);

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

    protected <T> ObjectFactory<T> getObjectFactory(Class<T> clazz)
    {
        ObjectFactory<T> factory = ObjectFactory.Registry.getFactory(clazz);

        if (null == factory)
            throw new IllegalArgumentException("Could not find object factory for " + clazz.getSimpleName() + ".");

        return factory;
    }

    @Override
    public @NotNull <K, V> Map<K, V> fillValueMap(@NotNull final Map<K, V> fillMap)
    {
        // Using a ResultSetIterator ensures that standard type conversion happens (vs. ResultSet enumeration and rs.getObject())
        handleResultSet(getStandardResultSetFactory(), new ResultSetHandler<Object>()
        {
            @Override
            public Object handle(ResultSet rs, Connection conn) throws SQLException
            {
                if (rs.getMetaData().getColumnCount() < 2)
                    throw new IllegalStateException("Must select at least two columns to use fillValueMap() or getValueMap()");

                ResultSetIterator iter = new ResultSetIterator(rs);

                while (iter.hasNext())
                {
                    RowMap rowMap = (RowMap)iter.next();
                    //noinspection unchecked
                    fillMap.put((K)rowMap.get(1), (V)rowMap.get(2));
                }

                return null;
            }
        });

        return fillMap;
    }

    @Override
    public @NotNull <K, V> Map<K, V> getValueMap()
    {
        return fillValueMap(new HashMap<K, V>());
    }
}

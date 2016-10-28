/*
 * Copyright (c) 2012-2016 LabKey Corporation
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

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.RowMap;
import org.springframework.jdbc.BadSqlGrammarException;

import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * A partial, base implementation of {@link org.labkey.api.data.Selector}. This class manipulates result sets but doesn't
 * generate them. Subclasses include ExecutingSelector (which executes SQL to generate a result set) and ResultSetSelector,
 * which takes an externally generated ResultSet (e.g., from JDBC metadata calls) and allows Selector operations on it.
 * User: adam
 * Date: 12/11/12
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

    public QueryLogging getQueryLogging()
    {
        return QueryLogging.emptyQueryLogging();      // empty queryLogging
    }

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
        return handleResultSet(factory, new ArrayListResultSetHandler<>(clazz));
    }

    // Simple object case: Number, String, Date, etc.
    protected @NotNull <E> ArrayList<E> createPrimitiveArrayList(ResultSet rs, @NotNull Table.Getter getter) throws SQLException
    {
        ArrayList<E> list = new ArrayList<>();

        while (rs.next())
            //noinspection unchecked
            list.add((E)getter.getObject(rs));

        return list;
    }

    private class ArrayListResultSetHandler<E> implements ResultSetHandler<ArrayList<E>>
    {
        private final Class<E> _clazz;

        public ArrayListResultSetHandler(Class<E> clazz)
        {
            _clazz = clazz;
        }

        @Override
        public ArrayList<E> handle(ResultSet rs, Connection conn) throws SQLException
        {
            final ArrayList<E> list;
            final Table.Getter getter = Table.Getter.forClass(_clazz);

            // If we have a Getter, then use it (simple object case: Number, String, Date, etc.)
            if (null != getter)
            {
                list = createPrimitiveArrayList(rs, getter);
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
        List<T> list = handleResultSet(factory, new ArrayListResultSetHandler<T>(clazz) {
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
        handleResultSet(factory, ((rs, conn) -> {
            try
            {
                while (rs.next())
                    block.exec(rs);
            }
            catch (StopIteratingException sie)
            {
            }

            return null;
        }));
    }

    @Override
    public void forEachMap(final ForEachBlock<Map<String, Object>> block)
    {
        forEachMap(block, getStandardResultSetFactory());
    }

    private void forEachMap(final ForEachBlock<Map<String, Object>> block, ResultSetFactory factory)
    {
        handleResultSet(factory, (rs, conn) -> {
            ResultSetIterator iter = new ResultSetIterator(rs);

            try
            {
                while (iter.hasNext())
                    block.exec(iter.next());
            }
            catch (StopIteratingException sie)
            {
            }

            return null;
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
        catch(BadSqlGrammarException e)
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
        forEach(block, clazz, getStandardResultSetFactory());
    }

    private <T> void forEach(final ForEachBlock<T> block, Class<T> clazz, ResultSetFactory resultSetFactory)
    {
        final Table.Getter getter = Table.Getter.forClass(clazz);

        // This is a simple object (Number, String, Date, etc.)
        if (null != getter)
        {
            forEach(rs -> {
                //noinspection unchecked
                block.exec((T)getter.getObject(rs));
            }, resultSetFactory);
        }
        else
        {
            final ObjectFactory<T> factory = getObjectFactory(clazz);

            ForEachBlock<Map<String, Object>> mapBlock = map -> block.exec(factory.fromMap(map));

            forEachMap(mapBlock, resultSetFactory);
        }
    }

    @Override
    public void forEachMapBatch(ForEachBatchBlock<Map<String, Object>> batchBlock, int batchSize)
    {
        ResultSetFactory factory = getStandardResultSetFactory();

        // Try-with-resources ensures that the final batch gets processed (on close())
        try (BatchForEachBlock<Map<String, Object>> bfeb = new BatchForEachBlock<>(batchBlock, batchSize))
        {
            forEachMap(bfeb, factory);
        }
        catch (SQLException e)
        {
            factory.handleSqlException(e, null);
        }
    }

    @Override
    public <T> void forEachBatch(ForEachBatchBlock<T> batchBlock, Class<T> clazz, int batchSize)
    {
        ResultSetFactory factory = getStandardResultSetFactory();

        // Try-with-resources ensures that the final batch gets processed (on close())
        try (BatchForEachBlock<T> bfeb = new BatchForEachBlock<>(batchBlock, batchSize))
        {
            forEach(bfeb, clazz, factory);
        }
        catch (SQLException e)
        {
            factory.handleSqlException(e, null);
        }
    }

    private static class BatchForEachBlock<T> implements ForEachBlock<T>, AutoCloseable
    {
        private final ForEachBatchBlock<T> _batchBlock;
        private final int _batchSize;
        private final List<T> _batch;

        private BatchForEachBlock(ForEachBatchBlock<T> batchBlock, int batchSize)
        {
            _batchBlock = batchBlock;
            _batchSize = batchSize;
            _batch = new ArrayList<>(_batchSize);
        }

        @Override
        public void exec(T object) throws SQLException
        {
            if (_batchBlock.accept(object))
            {
                _batch.add(object);

                if (_batch.size() == _batchSize)
                {
                    _batchBlock.exec(new LinkedList<>(_batch));  // Make a defensive copy... _batchBlock might process _batch asynchronously
                    _batch.clear();
                }
            }
        }

        @Override
        public void close() throws SQLException
        {
            if (!_batch.isEmpty())
                _batchBlock.exec(_batch);
        }
    }

    protected <T> ObjectFactory<T> getObjectFactory(Class<T> clazz)
    {
        ObjectFactory<T> factory = ObjectFactory.Registry.getFactory(clazz);

        if (null == factory)
            throw new IllegalArgumentException("Could not find object factory for " + clazz.getSimpleName() + ".");

        return factory;
    }

    // Unfortunately, Map and MultiValuedMap don't share an interface for the put method so we have to pass in a method reference instead.
    private <K, V> void fillValues(BiFunction<K, V, ?> fn)
    {
        // Using a ResultSetIterator ensures that standard type conversion happens (vs. ResultSet enumeration and rs.getObject())
        handleResultSet(getStandardResultSetFactory(), (rs, conn) -> {
            if (rs.getMetaData().getColumnCount() < 2)
                throw new IllegalStateException("Must select at least two columns to use fillValueMap() or getValueMap()");

            ResultSetIterator iter = new ResultSetIterator(rs);

            while (iter.hasNext())
            {
                RowMap rowMap = (RowMap)iter.next();
                //noinspection unchecked
                fn.apply((K)rowMap.get(1), (V)rowMap.get(2));
            }

            return null;
        });
    }

    @Override
    public @NotNull <K, V> Map<K, V> fillValueMap(@NotNull final Map<K, V> fillMap)
    {
        fillValues(fillMap::put);
        return fillMap;
    }

    @Override
    public @NotNull <K, V> Map<K, V> getValueMap()
    {
        return fillValueMap(new HashMap<>());
    }

    @Override
    public @NotNull <K, V> MultiValuedMap<K, V> fillMultiValuedMap(@NotNull final MultiValuedMap<K, V> multiMap)
    {
        fillValues(multiMap::put);
        return multiMap;
    }

    @Override
    public @NotNull <K, V> MultiValuedMap<K, V> getMultiValuedMap()
    {
        return fillMultiValuedMap(new ArrayListValuedHashMap<>());
    }
}

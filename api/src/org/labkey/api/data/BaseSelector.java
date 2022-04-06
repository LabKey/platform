/*
 * Copyright (c) 2011-2019 LabKey Corporation
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
import org.labkey.api.data.Table.Getter;

import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A partial, base implementation of {@link org.labkey.api.data.Selector}. This class manipulates result sets but doesn't
 * generate them. Subclasses include ExecutingSelector (which executes SQL to generate a result set) and ResultSetSelector,
 * which takes an externally generated ResultSet (e.g., from JDBC metadata calls) and allows Selector operations on it.
 * User: adam
 * Date: 12/11/12
 */

public abstract class BaseSelector<SELECTOR extends BaseSelector<?>> extends JdbcCommand<SELECTOR> implements Selector
{
    BaseSelector(@NotNull DbScope scope, @Nullable Connection conn)
    {
        super(scope, conn);
    }

    // Used by standard enumerating methods (forEach(), getArrayList()) and their callers (getArray(), getCollection(), getObject())
    abstract protected ResultSetFactory getStandardResultSetFactory();

    abstract protected ResultSetFactory getStandardResultSetFactory(boolean closeResultSet);

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
        return Arrays.asList(getMapArray());
    }

    @Override
    public @NotNull <E> ArrayList<E> getArrayList(Class<E> clazz)
    {
        return getArrayList(clazz, getStandardResultSetFactory());
    }

    protected @NotNull <E> ArrayList<E> getArrayList(final Class<E> clazz, final ResultSetFactory factory)
    {
        return factory.handleResultSet(new ArrayListResultSetHandler<>(clazz));
    }

    // Simple object case: Number, String, Date, etc.
    protected @NotNull <E> ArrayList<E> createPrimitiveArrayList(ResultSet rs, @NotNull Getter getter) throws SQLException
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

        ArrayListResultSetHandler(Class<E> clazz)
        {
            _clazz = clazz;
        }

        @Override
        public ArrayList<E> handle(ResultSet rs, Connection conn) throws SQLException
        {
            final ArrayList<E> list;
            final Getter getter = Getter.forClass(_clazz);

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

    @Override
    public <T> Stream<T> stream(Class<T> clazz)
    {
        return getCollection(clazz).stream();  // TODO: Use uncachedStream below?
    }

    /**
     * Returns an uncached Object Stream that <b>must be closed</b>
     */
    @Override
    public <T> Stream<T> uncachedStream(Class<T> clazz)
    {
        return stream(rs -> {
            final Getter getter = Getter.forClass(clazz);

            // This is a simple object (Number, String, Date, etc.)
            if (null != getter)
            {
                Iterator<ResultSet> iter = new SimpleResultSetIterator(rs);

                return new Iterator<T>()
                {
                    @Override
                    public boolean hasNext()
                    {
                        return iter.hasNext();
                    }

                    @Override
                    public T next()
                    {
                        try
                        {
                            //noinspection unchecked
                            return (T)getter.getObject(iter.next());
                        }
                        catch (SQLException e)
                        {
                            throw new RuntimeSQLException(e);
                        }
                    }
                };
            }
            else
            {
                Iterator<Map<String, Object>> iter = new ResultSetIterator(rs);
                ObjectFactory<T> factory = getObjectFactory(clazz);

                return new Iterator<T>()
                {
                    @Override
                    public boolean hasNext()
                    {
                        return iter.hasNext();
                    }

                    @Override
                    public T next()
                    {
                        return factory.fromMap(iter.next());
                    }
                };
            }
        }, false);
    }

    @Override
    public Stream<Map<String, Object>> mapStream()
    {
        return mapStream(true);
    }

    /**
     * Returns an uncached Map Stream that <b>must be closed</b>
     */
    @Override
    public Stream<Map<String, Object>> uncachedMapStream()
    {
        return mapStream(false);
    }

    private Stream<Map<String, Object>> mapStream(boolean cached)
    {
        return stream(ResultSetIterator::new, cached);
    }

    @Override
    public Stream<ResultSet> resultSetStream()
    {
        return resultSetStream(true);
    }

    /**
     * Returns an uncached ResultSet Stream that <b>must be closed</b>
     */
    @Override
    public Stream<ResultSet> uncachedResultSetStream()
    {
        return resultSetStream(false);
    }

    private Stream<ResultSet> resultSetStream(boolean cached)
    {
        return stream(SimpleResultSetIterator::new, cached);
    }


    /**
     * A very simple Iterator that returns the ResultSet on each call to {@code next()}. (Compare with {@link ResultSetIterator},
     * which returns a {@code Map<String, Object>} on each call to {@code next()}).
     */
    private class SimpleResultSetIterator implements Iterator<ResultSet>
    {
        private final ResultSetIteratorHelper _iter;

        private SimpleResultSetIterator(ResultSet rs)
        {
            _iter = new ResultSetIteratorHelper(rs);
        }

        @Override
        public boolean hasNext()
        {
            try
            {
                return _iter.hasNext();
            }
            catch (SQLException e)
            {
                throw getExceptionFramework().translate(getScope(), "Determining if we're at the end of a result set", e);
            }
        }

        @Override
        public ResultSet next()
        {
            try
            {
                return _iter.next();
            }
            catch (SQLException e)
            {
                throw getExceptionFramework().translate(getScope(), "Iterating the result set", e);
            }
        }
    }


    private <T> Stream<T> stream(Function<ResultSet, Iterator<T>> function, boolean cached)
    {
        return getStandardResultSetFactory(cached).handleResultSet((incoming, conn) -> {
            // For convenience, we don't require closing Streams over cached result sets, so set the CachedResultSet to not validate.
            ResultSet rs = wrapResultSet(incoming, conn, cached, !cached);
            Iterable<T> iterable = () -> function.apply(rs);
            return StreamSupport.stream(iterable.spliterator(), false)
                .onClose(() -> {
                    try
                    {
                        rs.close();
                    }
                    catch (SQLException e)
                    {
                        throw getExceptionFramework().translate(getScope(), "Attempting to close() ResultSet and Connection", e);
                    }
                });
        });
    }

    protected abstract TableResultSet wrapResultSet(ResultSet rs, Connection conn, boolean cache, boolean requireClose) throws SQLException;

    protected <T> T getObject(final Class<T> clazz, ResultSetFactory factory)
    {
        List<T> list = factory.handleResultSet(new ArrayListResultSetHandler<T>(clazz) {
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
        forEach(getStandardResultSetFactory(), block);
    }

    protected void forEach(ResultSetFactory factory, final ForEachBlock<ResultSet> block)
    {
        factory.handleResultSet(((rs, conn) -> {
            try
            {
                while (rs.next())
                    block.exec(rs);
            }
            catch (StopIteratingException ignored)
            {
            }

            return null;
        }));
    }

    @Override
    public void forEachMap(final ForEachBlock<Map<String, Object>> block)
    {
        forEachMap(getStandardResultSetFactory(), block);
    }

    private void forEachMap(ResultSetFactory factory, final ForEachBlock<Map<String, Object>> block)
    {
        factory.handleResultSet((rs, conn) -> {
            ResultSetIterator iter = new ResultSetIterator(rs);

            try
            {
                while (iter.hasNext())
                    block.exec(iter.next());
            }
            catch (StopIteratingException ignored)
            {
            }

            return null;
        });
    }

    public interface ResultSetHandler<T>
    {
        T handle(ResultSet rs, Connection conn) throws SQLException;
    }

    @Override
    public <T> void forEach(Class<T> clazz, final ForEachBlock<T> block)
    {
        forEach(clazz, getStandardResultSetFactory(), block);
    }

    public <T> void forEach2(Class<T> clazz, final ForEachBlock<T> block)
    {
        forEach2(() -> uncachedStream(clazz), getStandardResultSetFactory(), block);
    }

    // Prototype general purpose forEach() built on an uncached stream (not used)
    private <T> void forEach2(Supplier<Stream<T>> streamFactory, ResultSetFactory resultSetFactory, ForEachBlock<T> block)
    {
        try (Stream<T> stream = streamFactory.get())
        {
            stream.forEach(t-> {
                try
                {
                    block.exec(t);
                }
                catch (SQLException | StopIteratingException e)
                {
                    throw new RuntimeException(e);
                }
            });
        }
        catch (RuntimeException e)
        {
            try
            {
                throw e.getCause();
            }
            catch (SQLException se)
            {
                resultSetFactory.handleSqlException(se, null);
            }
            catch (StopIteratingException ignore)
            {
                // Mission accomplished... iterating is stopped
            }
            catch (Throwable throwable)
            {
                // Shouldn't happen... just throw the exception
                throw e;
            }
        }
    }

    private <T> void forEach(Class<T> clazz, ResultSetFactory resultSetFactory, final ForEachBlock<T> block)
    {
        final Getter getter = Getter.forClass(clazz);

        // This is a simple object (Number, String, Date, etc.)
        if (null != getter)
        {
            forEach(resultSetFactory, rs -> {
                //noinspection unchecked
                block.exec((T)getter.getObject(rs));
            });
        }
        else
        {
            final ObjectFactory<T> factory = getObjectFactory(clazz);

            ForEachBlock<Map<String, Object>> mapBlock = map -> block.exec(factory.fromMap(map));

            forEachMap(resultSetFactory, mapBlock);
        }
    }

    @Override
    public void forEachMapBatch(int batchSize, ForEachBatchBlock<Map<String, Object>> batchBlock)
    {
        ResultSetFactory factory = getStandardResultSetFactory();

        // Try-with-resources ensures that the final batch gets processed (on close())
        try (BatchForEachBlock<Map<String, Object>> bfeb = new BatchForEachBlock<>(batchSize, batchBlock))
        {
            forEachMap(factory, bfeb);
        }
        catch (SQLException e)
        {
            factory.handleSqlException(e, null);
        }
    }

    @Override
    public <T> void forEachBatch(Class<T> clazz, int batchSize, ForEachBatchBlock<T> batchBlock)
    {
        ResultSetFactory factory = getStandardResultSetFactory();

        // Try-with-resources ensures that the final batch gets processed (on close())
        try (BatchForEachBlock<T> bfeb = new BatchForEachBlock<>(batchSize, batchBlock))
        {
            forEach(clazz, factory, bfeb);
        }
        catch (SQLException e)
        {
            factory.handleSqlException(e, null);
        }
    }

    private static class BatchForEachBlock<T> implements ForEachBlock<T>, AutoCloseable
    {
        private final int _batchSize;
        private final List<T> _batch;
        private final ForEachBatchBlock<T> _batchBlock;

        private BatchForEachBlock(int batchSize, ForEachBatchBlock<T> batchBlock)
        {
            _batchSize = batchSize;
            _batch = new ArrayList<>(_batchSize);
            _batchBlock = batchBlock;
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

    private <T> ObjectFactory<T> getObjectFactory(Class<T> clazz)
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
        getStandardResultSetFactory().handleResultSet((rs, conn) -> {
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

    @Override
    public @NotNull <K> Set<K> fillSet(@NotNull final Set<K> fillSet)
    {
        getStandardResultSetFactory().handleResultSet((rs, conn) -> {
            ResultSetIterator iter = new ResultSetIterator(rs);
            while (iter.hasNext())
            {
                RowMap rowMap = (RowMap)iter.next();
                //noinspection unchecked
                fillSet.add((K)rowMap.get(1));
            }
            return null;
        });
        return fillSet;
    }
}

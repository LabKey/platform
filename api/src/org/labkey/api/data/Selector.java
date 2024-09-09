/*
 * Copyright (c) 2011-2018 LabKey Corporation
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
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Base-level interface for getting results from the database.
 */
public interface Selector
{
    /**
     * If no transaction is active and the SQL statement is a SELECT, this method assumes it is safe to tweak
     * connection parameters (such as disabling auto-commit, and never committing) to optimize memory and other
     * resource usage. If you are, for example, invoking a stored procedure that will have side effects via a SELECT
     * statement, you must explicitly start your own transaction and commit it.
     */
    TableResultSet getResultSet();

    long getRowCount();

    /** @return whether there is at least one row that matches the selection criteria */
    boolean exists();

    /**
     * @return an array of simple classes, java beans, or java records of the given {@code Class}
     */
    @NotNull <T> T[] getArray(Class<T> clazz);

    /** Convenience method that avoids "unchecked assignment" warnings */
    @NotNull Map<String, Object>[] getMapArray();

    /**
     * @return a {@code Collection} of simple classes, java beans, or java records of the given {@code Class}
     */
    @NotNull <E> Collection<E> getCollection(Class<E> clazz);

    /** Convenience method that avoids "unchecked assignment" warnings */
    @NotNull Collection<Map<String, Object>> getMapCollection();

    /**
     * @return an {@code ArrayList} of simple classes, java beans, or java records of the given {@code Class}
     */
    @NotNull <E> ArrayList<E> getArrayList(Class<E> clazz);


    /**
     * @return a single simple class, java bean, or java record of the given {@code Class}
     * @throws IllegalStateException if the {@code Selector} received more than one row
     */
    <T> T getObject(Class<T> clazz);

    /**
     * Returns a sequential Stream of objects or records representing rows from the database. Converts each result row
     * into an object the specified {@code Class}. The Stream is backed by a cached data structure (ResultSet and
     * Connection are closed before returning the stream), so no need to close or fully exhaust this stream. Cached
     * streams are more convenient to use than uncached streams and should perform well in low-volume situations.
     */
    <T> Stream<T> stream(Class<T> clazz);

    /**
     * Returns an uncached sequential Stream of objects or records representing rows from the database. Converts each
     * result row into an object of the specified {@code Class}. The Stream is backed by a live ResultSet with an open
     * Connection, so <b>it must be closed</b>, typically using try-with-resources. This is less convenient than a
     * cached Stream, but important when large results are possible. An example showing proper closing:
     *
     * <pre>{@code
     *
     * // Uncached streams must be closed to release backing resources
     * try (Stream<User> stream = selector.uncachedStream(User.class))
     * {
     *     List<String> emails = stream
     *         .map(User::getEmail)
     *         .collect(Collectors.toList());
     * }}</pre>
     */
    <T> Stream<T> uncachedStream(Class<T> clazz);

    /**
     * Returns a sequential Stream of maps representing rows from the database. Converts each result row into a {@code
     * Map<String, Object>}. The Stream is backed by a cached data structure (ResultSet and Connection are closed before
     * returning the stream), so no need to close or fully exhaust this stream. Cached streams are more convenient to use
     * than uncached streams and should perform well in low-volume situations.
     */
    Stream<Map<String, Object>> mapStream();

    /**
     * Returns an uncached sequential Stream of maps representing rows from the database. Converts each result row into a
     * {@code Map<String, Object>}. The Stream is backed by a live ResultSet with an open Connection, so it <b>must be
     * closed</b>, typically using try-with-resources. This is less convenient than a cached Stream, but useful when
     * large results are possible.
     */
    Stream<Map<String, Object>> uncachedMapStream();

    /**
     * Returns a sequential Stream that iterates a ResultSet. The Stream is backed by a cached data structure (ResultSet
     * and Connection are closed before returning the stream), so no need to close or fully exhaust this stream. Cached
     * streams are more convenient to use than uncached streams and should perform well in low-volume situations.
     */
    Stream<ResultSet> resultSetStream();

    /**
     * Returns an uncached sequential Stream that iterates a ResultSet. The Stream is backed by a live ResultSet with an
     * open Connection, so <b>it must be closed</b>, typically using try-with-resources. This is less convenient than a
     * cached Stream, but useful when large results are possible.
     */
    Stream<ResultSet> uncachedResultSetStream();

    /** Convenience method that avoids "unchecked assignment" warnings */
    Map<String, Object> getMap();

    void forEach(ForEachBlock<ResultSet> block);

    /**
     * Streams maps from the database. Converts each result row into a {@code Map<String, Object>} and invokes
     * {@code block.exec()} on it.
     **/
    void forEachMap(ForEachBlock<Map<String, Object>> block);

    /**
     *  Streams maps from the database in batches. Convert rows to maps and pass them to batchBlock.exec() in batches no
     *  larger than batchSize. This is convenient for cases where streaming is desired, but processing in batches is more
     *  efficient than one-by-one. All batches are of size batchSize, except the last batch which is typically smaller.
     */
    void forEachMapBatch(int batchSize, ForEachBatchBlock<Map<String, Object>> batchBlock);

    /**
     * Streams objects or records from the database.
     * Converts each result row into an object specified by clazz and invoke block.exec() on it.
     * @return the number of rows processed
     */
    <T> int forEach(Class<T> clazz, ForEachBlock<T> block);

    /**
     *  Streams objects or records from the database in batches. Converts rows to objects and passes them to
     *  {@code batchBlock.exec()} in batches no larger than batchSize. This is convenient for cases where streaming is
     *  desired, but processing in batches is more efficient than one-by-one. All batches are of size batchSize, except
     *  the last batch which is typically smaller.
     *  @return the number of rows processed
     */
    <T> int forEachBatch(Class<T> clazz, int batchSize, ForEachBatchBlock<T> batchBlock);

    /**
     * Returns a new value map. If the query selects a single column, return an identity map. If the query selects
     * multiple columns, the first column is the key, the second column is the value. Subsequent columns are ignored.
     */
    @NotNull <K, V> Map<K, V> getValueMap();

    /**
     * Populates an existing map. If the query selects a single column, populates as an identity map. If the query
     * selects multiple columns, the first column is the key, the second column is the value. Subsequent columns are
     * ignored.
     */
    @NotNull <K, V> Map<K, V> fillValueMap(@NotNull Map<K, V> map);

    /**
     * Returns a new MultiValuedMap. If the query selects a single column, returns an identity map. If the query selects
     * multiple columns, the first column is the key, the second column is the value, of which there may be more than
     * one for each key. Subsequent columns are ignored.
     */
    @NotNull <K, V> MultiValuedMap<K, V> getMultiValuedMap();

    /**
     * Populates an existing MultiValuedMap. If the query selects a single column, return an identity map. If the query
     * selects multiple columns, the first column is the key, the second column is the value, of which there may be more
     * than one for each key. Subsequent columns are ignored.
     */
    @NotNull <K, V> MultiValuedMap<K, V> fillMultiValuedMap(@NotNull final MultiValuedMap<K, V> multiMap);

    @SuppressWarnings("UnusedReturnValue")
    @NotNull <K> Set<K> fillSet(@NotNull final Set<K> fillSet);

    /** Callback interface for dealing with objects or records streamed from the database one-by-one */
    interface ForEachBlock<T>
    {
        /** Invoked once for each row returned by the selector */
        void exec(T object) throws SQLException, StopIteratingException;

        default void stopIterating() throws StopIteratingException
        {
            throw new StopIteratingException();
        }
    }

    /** Callback interface for dealing with objects or records streamed from the database in batches */
    interface ForEachBatchBlock<T>
    {
        /**
         * Invoked once for each row returned by the selector. Return true to add element to the current batch, false to skip it.
         */
        default boolean accept(T element)
        {
            return true;
        }

        /** Invoked once for each batch of rows returned by the selector */
        void exec(List<T> batch) throws SQLException;
    }
}

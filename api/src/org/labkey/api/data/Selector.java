/*
 * Copyright (c) 2011-2016 LabKey Corporation
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

/**
 * Base-level interface for getting results from the database.
 * User: adam
 * Date: Sep 3, 2011
 */
public interface Selector
{
    /**
     * If no transaction is active and the SQL statement is a SELECT, this method assumes it is safe to tweak
     * connection parameters (such as disabling auto-commit, and never committing) to optimize memory and other
     * resource usage.
     *
     * If you are, for example, invoking a stored procedure that will have side effects via a SELECT statement,
     * you must explicitly start your own transaction and commit it.
     */
    TableResultSet getResultSet();

    long getRowCount();

    /** @return whether there is at least one row that matches the selection criteria */
    boolean exists();

    @NotNull <T> T[] getArray(Class<T> clazz);

    /** Convenience method that avoids "unchecked assignment" warnings */
    @NotNull Map<String, Object>[] getMapArray();

    @NotNull <E> Collection<E> getCollection(Class<E> clazz);

    /** Convenience method that avoids "unchecked assignment" warnings */
    @NotNull Collection<Map<String, Object>> getMapCollection();

    @NotNull <E> ArrayList<E> getArrayList(Class<E> clazz);

    <T> T getObject(Class<T> clazz);

    /** Convenience method that avoids "unchecked assignment" warnings */
    Map<String, Object> getMap();

    void forEach(ForEachBlock<ResultSet> block);

    /** Stream maps from the database. Convert each result row into a Map<String, Object> and invoke block.exec() on it. */
    void forEachMap(ForEachBlock<Map<String, Object>> block);

    /**
     *  Stream maps from the database in batches. Convert rows to maps and pass them to batchBlock.exec() in batches no
     *  larger than batchSize. This is convenient for cases where streaming is desired, but processing in batches is more
     *  efficient than one-by-one. All batches are of size batchSize, except the last batch which is typically smaller.
     */
    void forEachMapBatch(ForEachBatchBlock<Map<String, Object>> batchBlock, int batchSize);

    /** Stream objects from the database. Convert each result row into an object specified by clazz and invoke block.exec() on it. */
    <T> void forEach(ForEachBlock<T> block, Class<T> clazz);

    /**
     *  Stream objects from the database in batches. Convert rows to objects and pass them to batchBlock.exec() in batches
     *  no larger than batchSize. This is convenient for cases where streaming is desired, but processing in batches is more
     *  efficient than one-by-one. All batches are of size batchSize, except the last batch which is typically smaller.
     */
    <T> void forEachBatch(ForEachBatchBlock<T> batchBlock, Class<T> clazz, int batchSize);

    /** Return a new map from a two-column query; the first column is the key, the second column is the value. */
    @NotNull <K, V> Map<K, V> getValueMap();

    /** Populate an existing map from a two-column query; the first column is the key, the second column is the value. */
    @NotNull <K, V> Map<K, V> fillValueMap(@NotNull Map<K, V> map);

    /** Return a new MultiValuedMap from a two-column query; the first column is the key, the second column is the value of which there may be more than one for the key. */
    @NotNull <K, V> MultiValuedMap<K, V> getMultiValuedMap();

    /** Populate an existing MultiValuedMap from a two-column query; the first column is the key, the second column is the value of which there may be more than one for the key. */
    @NotNull <K, V> MultiValuedMap<K, V> fillMultiValuedMap(@NotNull final MultiValuedMap<K, V> multiMap);

    /** Callback interface for dealing with objects streamed from the database one-by-one */
    interface ForEachBlock<T>
    {
        /** Invoked once for each row returned by the selector */
        void exec(T object) throws SQLException, StopIteratingException;

        default void stopIterating() throws StopIteratingException
        {
            throw new StopIteratingException();
        }
    }

    /** Callback interface for dealing with objects streamed from the database in batches */
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

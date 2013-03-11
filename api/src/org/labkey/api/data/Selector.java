/*
 * Copyright (c) 2011-2013 LabKey Corporation
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/*
* User: adam
* Date: Sep 3, 2011
* Time: 9:13:05 AM
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
    Table.TableResultSet getResultSet() throws SQLException;

    long getRowCount();

    boolean exists();

    <K> K[] getArray(Class<K> clazz);

    // Convenience method that avoids "unchecked assignment" warnings
    Map<String, Object>[] getMapArray();

    <K> Collection<K> getCollection(Class<K> clazz);

    <K> ArrayList<K> getArrayList(Class<K> clazz);

    <K> K getObject(Class<K> clazz);

    void forEach(ForEachBlock<ResultSet> block);

    void forEachMap(ForEachBlock<Map<String, Object>> block);

    <K> void forEach(ForEachBlock<K> block, Class<K> clazz);

    // Return a new map from a two-column query; the first column is the key, the second column is the value.
    <K, V> Map<K, V> getValueMap();

    // Populate an existing map from a two-column query; the first column is the key, the second column is the value.
    <K, V> Map<K, V> fillValueMap(Map<K, V> map);

    interface ForEachBlock<K>
    {
        void exec(K object) throws SQLException;
    }
}

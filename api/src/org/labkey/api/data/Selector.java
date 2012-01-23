/*
 * Copyright (c) 2011 LabKey Corporation
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
import java.util.Collection;
import java.util.Map;

/*
* User: adam
* Date: Sep 3, 2011
* Time: 9:13:05 AM
*/
public interface Selector
{
//    ResultSet getResultSet() throws SQLException;      // TODO: Don't throw SQLException?

    long getRowCount();

    <K> K[] getArray(Class<K> clazz);

    <K> Collection<K> getCollection(Class<K> clazz);

    <K> K getObject(Class<K> clazz);

    void forEach(ForEachBlock<ResultSet> block);

    void forEachMap(ForEachBlock<Map<String, Object>> block);

    <K> void forEach(ForEachBlock<K> block, Class<K> clazz);

    // Used to populate a map with a two-column query; the first column is the key, the second column is the value.
    // This variant returns a new map.
    Map<Object, Object> getValueMap();

    // Used to populate a map with a two-column query; the first column is the key, the second column is the value.
    // This variant populates and returns the map that is passed in.
    Map<Object, Object> fillValueMap(Map<Object, Object> map);

    interface ForEachBlock<K>
    {
        void exec(K object) throws SQLException;
    }
}

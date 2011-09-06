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

import org.springframework.dao.DataAccessException;

import java.sql.Connection;
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
    public enum ExceptionFramework
    {
        Spring
            {
                @Override
                DataAccessException translate(DbScope scope, String message, String sql, SQLException e)
                {
                    return scope.translateToSpringException(message, sql, e);
                }
            },
        JDBC
            {
                @Override
                RuntimeSQLException translate(DbScope scope, String message, String SQL, SQLException e)
                {
                    return new RuntimeSQLException(e);
                }
            };

        abstract RuntimeException translate(DbScope scope, String message, String SQL, SQLException e);
    }

    ResultSet getResultSet() throws SQLException;      // TODO: Don't throw SQLException?

    <K> K[] getArray(Class<K> clazz);

    <K> Collection<K> getCollection(Class<K> clazz);

    <K> K getObject(Class<K> clazz);

    void forEach(Table.ForEachBlock<ResultSet> block);

    void forEachMap(Table.ForEachBlock<Map<String, Object>> block);

    <K> void forEach(Table.ForEachBlock<K> block, Class<K> clazz);

    // Used to populate a map with a two-column query; the first column is the key, the second column is the value.
    // This variant returns a new map.
    Map<Object, Object> getValueMap();

    // Used to populate a map with a two-column query; the first column is the key, the second column is the value.
    // This variant populates and returns the map that is passed in.
    Map<Object, Object> fillValueMap(Map<Object, Object> map);
}

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

import org.labkey.api.data.Selector.ExceptionFramework;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;

/*
* User: adam
* Date: Sep 3, 2011
* Time: 9:19:04 AM
*/

// Our new Selector API throws RuntimeExceptions, but the Table layer methods (and its callers) expect
// checked SQLExceptions. This class helps migrate to the new API by wrapping a Selector and translating
// its RuntimeSQLExceptions to checked SQLExceptions.
public class LegacySelector
{
    protected final Selector _selector;

    public LegacySelector(BaseSelector selector)
    {
        _selector = selector;
        selector.setExceptionFramework(ExceptionFramework.JDBC);
    }

    // All the results-gathering methods are below

    /* TODO: Fix up caching and connection closing, then expose this
    public ResultSet getResultSet() throws SQLException
    {
        return _selector.getResultSet();
    }
    */
    public <K> void forEach(Selector.ForEachBlock<K> block, Class<K> clazz) throws SQLException
    {
        try
        {
            _selector.forEach(block, clazz);
        }
        catch (RuntimeSQLException e)
        {
            throw e.getSQLException();
        }
    }

    public void forEach(Selector.ForEachBlock<ResultSet> block) throws SQLException
    {
        try
        {
            _selector.forEach(block);
        }
        catch (RuntimeSQLException e)
        {
            throw e.getSQLException();
        }
    }

    public void forEachMap(Selector.ForEachBlock<Map<String, Object>> block) throws SQLException
    {
        try
        {
            _selector.forEachMap(block);
        }
        catch (RuntimeSQLException e)
        {
            throw e.getSQLException();
        }
    }

    public <K> K[] getArray(Class<K> clazz) throws SQLException
    {
        try
        {
            return _selector.getArray(clazz);
        }
        catch (RuntimeSQLException e)
        {
            throw e.getSQLException();
        }
    }

    public <K> Collection<K> getCollection(Class<K> clazz) throws SQLException
    {
        try
        {
            return _selector.getCollection(clazz);
        }
        catch (RuntimeSQLException e)
        {
            throw e.getSQLException();
        }
    }

    public <K> K getObject(Class<K> clazz) throws SQLException
    {
        try
        {
            return _selector.getObject(clazz);
        }
        catch (RuntimeSQLException e)
        {
            throw e.getSQLException();
        }
    }

    public Map<Object, Object> getValueMap() throws SQLException
    {
        try
        {
            return _selector.getValueMap();
        }
        catch (RuntimeSQLException e)
        {
            throw e.getSQLException();
        }
    }

    public Map<Object, Object> fillValueMap(Map<Object, Object> map) throws SQLException
    {
        try
        {
            return _selector.fillValueMap(map);
        }
        catch (RuntimeSQLException e)
        {
            throw e.getSQLException();
        }
    }
}

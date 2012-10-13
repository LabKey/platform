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
// its RuntimeSQLExceptions into checked SQLExceptions.

// This declaration borders on insanity...
abstract class LegacySelector<SELECTOR extends BaseSelector<? extends SqlFactory, ?>, LEGACYSELECTOR extends LegacySelector<SELECTOR, ?>>
{
    protected final SELECTOR _selector;

    public LegacySelector(SELECTOR selector)
    {
        _selector = selector;
        selector.setExceptionFramework(ExceptionFramework.JDBC);
    }

    // LEGACYSELECTOR and getThis() make it easier to chain setMaxRows() and setOffset() while returning the correct subclass type
    protected abstract LEGACYSELECTOR getThis();

    public LEGACYSELECTOR setMaxRows(int maxRows)
    {
        _selector.setMaxRows(maxRows);
        return getThis();
    }

    public LEGACYSELECTOR setOffset(long offset)
    {
        _selector.setOffset(offset);
        return getThis();
    }

    public LEGACYSELECTOR setNamedParamters(Map<String, Object> parameters)
    {
        _selector.setNamedParameters(parameters);
        return getThis();
    }

    // All the results-gathering methods are below

    public Table.TableResultSet getResultSet() throws SQLException
    {
        return _selector.getResultSet();
    }

    public Table.TableResultSet getResultSet(boolean scrollable, boolean cache) throws SQLException
    {
        return _selector.getResultSet(scrollable, cache);
    }

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

    public long getRowCount() throws SQLException
    {
        try
        {
            return _selector.getRowCount();
        }
        catch (RuntimeSQLException e)
        {
            throw e.getSQLException();
        }
    }
}

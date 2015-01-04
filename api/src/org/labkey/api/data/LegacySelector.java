/*
 * Copyright (c) 2011-2014 LabKey Corporation
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

import java.sql.SQLException;
import java.util.Map;

/**
 * Our new Selector API throws RuntimeExceptions, but the Table layer methods (and its callers) expect
 * checked SQLExceptions. This class helps migrate to the new API by wrapping a Selector and translating
 * its RuntimeSQLExceptions into checked SQLExceptions.

 * User: adam
 * Date: Sep 3, 2011
*/

abstract class LegacySelector<SELECTOR extends SqlExecutingSelector<? extends SqlFactory, ?>, LEGACYSELECTOR extends LegacySelector<SELECTOR, ?>>
{
    protected final SELECTOR _selector;

    protected LegacySelector(SELECTOR selector)
    {
        _selector = selector;
        selector.setExceptionFramework(ExceptionFramework.JDBC);
    }

    // LEGACYSELECTOR and getThis() make it easier to chain setMaxRows() and setOffset() while returning the correct subclass type
    protected abstract LEGACYSELECTOR getThis();

    LEGACYSELECTOR setMaxRows(int maxRows)
    {
        _selector.setMaxRows(maxRows);
        return getThis();
    }

    LEGACYSELECTOR setOffset(long offset)
    {
        _selector.setOffset(offset);
        return getThis();
    }

    LEGACYSELECTOR setNamedParameters(Map<String, Object> parameters)
    {
        _selector.setNamedParameters(parameters);
        return getThis();
    }

    // All the results-gathering methods are below

    TableResultSet getResultSet()
    {
        return _selector.getResultSet();
    }

    TableResultSet getResultSet(boolean cache)
    {
        return _selector.getResultSet(cache, false);
    }

    TableResultSet getResultSet(boolean cache, boolean scrollable)
    {
        return _selector.getResultSet(cache, scrollable);
    }

    <E> E[] getArray(Class<E> clazz) throws SQLException
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

    Map<String, Object>[] getMapArray() throws SQLException
    {
        try
        {
            return _selector.getMapArray();
        }
        catch (RuntimeSQLException e)
        {
            throw e.getSQLException();
        }
    }

    <T> T getObject(Class<T> clazz) throws SQLException
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

    Map<Object, Object> getValueMap() throws SQLException
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

    Map<Object, Object> fillValueMap(Map<Object, Object> map) throws SQLException
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

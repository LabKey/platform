/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.collections.ResultSetRowMapFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;

/**
 * Wraps a JDBC {@link ResultSet} to expose it as an {@link Iterator} of maps.
 */
public class ResultSetIterator implements Iterator<Map<String, Object>>
{
    private final ResultSet _rs;
    private final ResultSetRowMapFactory _factory;

    private boolean _didNext = false;
    private boolean _hasNext = false;

    public ResultSetIterator(ResultSet rs)
    {
        _rs = rs;

        try
        {
            // Note: If _rs is a CachedResultSet then this method returns a simple, pass-through factory
            _factory = ResultSetRowMapFactory.create(_rs);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public boolean hasNext()
    {
        try
        {
            // This used to be simply !_rs.isLast(), but that fails in some edge cases (e.g., no rows)
            if (!_didNext)
            {
                _hasNext = _rs.next();
                _didNext = true;
            }

            return _hasNext;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public Map<String, Object> next()
    {
        try
        {
            if (!_didNext)
            {
                _rs.next();
            }
            _didNext = false;

            return _factory.getRowMap(_rs);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void remove()
    {
        throw new UnsupportedOperationException("Can't remove row when iterating");
    }
} 

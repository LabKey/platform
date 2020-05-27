/*
 * Copyright (c) 2004-2018 Fred Hutchinson Cancer Research Center
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
    private final ResultSetIteratorHelper _iter;
    private final ResultSetRowMapFactory _factory;

    public ResultSetIterator(ResultSet rs)
    {
        _iter = new ResultSetIteratorHelper(rs);

        try
        {
            // Note: If _rs is a CachedResultSet then this method returns a simple, pass-through factory
            _factory = ResultSetRowMapFactory.create(rs);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
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
            throw new RuntimeSQLException(e);
        }
    }

    @Override
    public Map<String, Object> next()
    {
        try
        {
            return _factory.getRowMap(_iter.next());
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }
}

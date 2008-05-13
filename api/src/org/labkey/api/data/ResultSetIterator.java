/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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
import java.util.Iterator;
import java.util.Map;

import org.apache.log4j.Logger;
import org.labkey.api.util.ResultSetUtil;

public class ResultSetIterator implements Iterator<Map>
{
    private ResultSet _rs;
    private Map<String,Object> _map = null;
    static Logger _log = Logger.getLogger(ResultSetIterator.class);

    public static ResultSetIterator get(ResultSet rs)
    {
        return new ResultSetIterator(rs);
    }

    public ResultSetIterator(ResultSet rs)
    {
        _rs = rs;
    }

    public boolean hasNext()
    {
        try
        {
            return !_rs.isLast();
        }
        catch (SQLException e)
        {
            _log.error("isLast", e);
            return true;
        }
    }

    public Map next()
    {
        try
        {
            _rs.next();
            if (_rs instanceof CachedRowSetImpl)
                return _map = ((CachedRowSetImpl)_rs).getRowMap();
            else
                return _map = ResultSetUtil.mapRow(_rs, _map);
        }
        catch (SQLException e)
        {
            _log.error("ResultSetIterator.next()", e);
            return null;
        }
    }

    public void remove()
    {
        throw new UnsupportedOperationException("Can't remove row when iterating");
    }
} 

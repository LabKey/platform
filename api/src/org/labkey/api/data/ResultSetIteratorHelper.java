/*
 * Copyright (c) 2018 LabKey Corporation
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

/**
 * Wraps a JDBC {@link ResultSet} to provide a simple {@link Iterator}-like object. Does not implement Iterator, because
 * these methods throw {@link SQLException}.
 */
public class ResultSetIteratorHelper
{
    private final ResultSet _rs;

    private boolean _didNext = false;
    private boolean _hasNext = false;

    public ResultSetIteratorHelper(ResultSet rs)
    {
        _rs = rs;
    }

    public boolean hasNext() throws SQLException
    {
        // This used to be simply !_rs.isLast(), but that fails in some edge cases (e.g., no rows)
        if (!_didNext)
        {
            _hasNext = _rs.next();
            _didNext = true;
        }

        return _hasNext;
    }

    public ResultSet next() throws SQLException
    {
        if (!_didNext)
        {
            _rs.next();
        }
        _didNext = false;

        return _rs;
    }
}

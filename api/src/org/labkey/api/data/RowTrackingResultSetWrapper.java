/*
 * Copyright (c) 2015 LabKey Corporation
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

/**
 * User: adam
 * Date: 2/21/2015
 * Time: 1:17 PM
 */

// ResultSet wrapper that tracks the current row based on navigation operations. This is needed for JDBC drivers that don't
// correctly implement getRow(), most notably, the SAS JDBC driver.
//
// Implementation follows the same basic pattern as LoggingResultSet.
public class RowTrackingResultSetWrapper extends ResultSetWrapper
{
    private int _currentRow = 0;

    public RowTrackingResultSetWrapper(ResultSet rs)
    {
        super(rs);
    }

    @Override
    public int getRow() throws SQLException
    {
        return _currentRow;
    }

    @Override
    public boolean next() throws SQLException
    {
        boolean isNext = resultset.next();
        if (isNext)
            _currentRow++;
        return isNext;
    }

    @Override
    public boolean first() throws SQLException
    {
        boolean isValid = resultset.first();
        if (isValid)
            _currentRow = 1;
        return isValid;
    }

    @Override
    public boolean last() throws SQLException
    {
        // We can't set _currentRow correctly, unless we enumerate every row, so just throw for now
        throw new SQLException("Not supported");
//        boolean isValid = resultset.last();
//        if (isValid)
//            _currentRow = ??;
//        return isValid;
    }

    @Override
    public boolean absolute(int i) throws SQLException
    {
        boolean isValid = resultset.absolute(i);
        if (isValid)
            _currentRow = i;
        return isValid;
    }

    @Override
    public boolean relative(int i) throws SQLException
    {
        boolean isValid = resultset.relative(i);
        if (isValid)
            _currentRow += i;
        return isValid;
    }

    @Override
    public boolean previous() throws SQLException
    {
        boolean isValid = resultset.previous();
        if (isValid)
            _currentRow--;
        return isValid;
    }
}

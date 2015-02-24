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

import org.junit.Assert;
import org.junit.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * ResultSet wrapper that tracks the current row based on navigation operations. This is needed for JDBC drivers that don't
 * correctly implement getRow(), most notably, the SAS JDBC driver.
 *
 * User: adam
 * Date: 2/21/2015
 * Time: 1:17 PM
 */

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

    // These are the absolute positioning methods that we support

    @Override
    public boolean first() throws SQLException
    {
        _currentRow = 1;
        return resultset.first();
    }

    @Override
    public boolean absolute(int i) throws SQLException
    {
        _currentRow = i;
        return resultset.absolute(i);
    }

    @Override
    public void beforeFirst() throws SQLException
    {
        _currentRow = 0;
        resultset.beforeFirst();
    }

    @Override
    public void afterLast() throws SQLException
    {
        _currentRow = 0;
        resultset.afterLast();
    }

    // These are the relative positioning methods that we support

    @Override
    public boolean next() throws SQLException
    {
        _currentRow++;
        return resultset.next();
    }

    @Override
    public boolean previous() throws SQLException
    {
        _currentRow--;
        return resultset.previous();
    }

    @Override
    public boolean relative(int i) throws SQLException
    {
        _currentRow += i;
        return resultset.relative(i);
    }

    // We don't support this method

    @Override
    public boolean last() throws SQLException
    {
        // We can't set _currentRow correctly, unless we enumerate every row, so just throw for now
        throw new SQLException("Not supported");
    }


    // Test basic operations. This doesn't test edge conditions like previous() when before first, next when after last, absolute(-3), etc.
    public static class TestCase extends Assert
    {
        @Test
        public void test() throws SQLException
        {
            TableSelector selector = new TableSelector(CoreSchema.getInstance().getTableInfoModules());

            try (ResultSet rs = selector.getResultSet(); ResultSet wrapper = new RowTrackingResultSetWrapper(rs))
            {
                assertEquals(rs.getRow(), wrapper.getRow());
                wrapper.next();
                assertEquals(rs.getRow(), wrapper.getRow());
                wrapper.next();
                assertEquals(rs.getRow(), wrapper.getRow());
                wrapper.previous();
                assertEquals(rs.getRow(), wrapper.getRow());
                wrapper.absolute(7);
                assertEquals(rs.getRow(), wrapper.getRow());
                wrapper.absolute(5);
                assertEquals(rs.getRow(), wrapper.getRow());
                wrapper.relative(2);
                assertEquals(rs.getRow(), wrapper.getRow());
                wrapper.relative(-2);
                assertEquals(rs.getRow(), wrapper.getRow());
                wrapper.first();
                assertEquals(rs.getRow(), wrapper.getRow());
                wrapper.beforeFirst();
                assertEquals(rs.getRow(), wrapper.getRow());
                wrapper.afterLast();
                assertEquals(rs.getRow(), wrapper.getRow());
            }
        }
    }
}

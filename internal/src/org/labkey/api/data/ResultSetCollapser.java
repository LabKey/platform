/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

/**
 * User: jeckels
 * Date: Dec 20, 2006
 */
public class ResultSetCollapser extends ResultSetImpl
{
    private String _columnName;
    private TableResultSet _tableRS;

    // XXX: needs offset?
    public ResultSetCollapser(TableResultSet rs, String columnName, int maxRows)
    {
        super(rs, QueryLogging.emptyQueryLogging());
        try
        {
            if (maxRows > 0)
            {
                rs.last();
                setComplete(rs.getRow() <= maxRows);
                rs.beforeFirst();
            }
            else
            {
                setComplete(true);
            }
            _columnName = columnName;
            _tableRS = rs;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public boolean isComplete()
    {
        return _tableRS.isComplete();
    }

    @Override
    public int getSize()
    {
        return -1;
    }

    public String getTruncationMessage(int maxRows)
    {
        return _tableRS.getTruncationMessage(maxRows);
    }

    public boolean next() throws SQLException
    {
        Object lastValue;

        if (getRow() > 0)
        {
            lastValue = getObject(_columnName);
        }
        else
        {
            lastValue = new Object();
        }

        while(super.next())
        {
            if (!lastValue.equals(getObject(_columnName)))
            {
                return true;
            }
        }
        return false;
    }
}

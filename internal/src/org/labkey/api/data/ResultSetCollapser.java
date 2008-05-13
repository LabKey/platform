/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
public class ResultSetCollapser extends Table.ResultSetImpl
{
    private Object _lastValue = new Object();
    private String _columnName;
    private Table.TableResultSet _tableRS;

    // XXX: needs offset?
    public ResultSetCollapser(Table.TableResultSet rs, String columnName, int maxRows) throws SQLException
    {
        super(rs);
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

    public boolean isComplete()
    {
        return _tableRS.isComplete();
    }

    public String getTruncationMessage(int maxRows)
    {
        return _tableRS.getTruncationMessage(maxRows);
    }

    public boolean next() throws SQLException
    {
        while(super.next())
        {
            if (!_lastValue.equals(getObject(_columnName)))
            {
                _lastValue= getInt(_columnName);
                return true;
            }
        }
        return false;
    }
}

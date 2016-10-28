/*
 * Copyright (c) 2004-2016 Fred Hutchinson Cancer Research Center
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
import java.util.Objects;

/**
 * User: arauch
 * Date: Nov 15, 2004
 * Time: 11:27:24 AM
 *
 * A simple result set subclass that supports grouping by any column.  Allows for an efficient implementation of
 * fully expanded nested data regions, using a single query for all the detail rows.
 *
 * Pass a ResultSet (sorted by the grouping column) and the column name to the constructor, then use getNextResultSet()
 * to iterate through the groups.  Each call to getNextResultSet() returns a NestedResultSet that iterates through the rows
 * until the grouping column changes.
 *
 * Note: only a minimal set of methods has been overridden... just enough to fool DataRegions & ExcelGrids.
 * Note: tested only with Protein (a String column) grouping, doesn't currently support null as a valid value
 */
public class GroupedResultSet extends ResultSetImpl
{
    private int _columnIndex = 0;
    private int _rowOffset = 0;
    private int _lastRow = 0;
    private int _groupCount = 0;

    private boolean _ignoreNext;

    public GroupedResultSet(ResultSet rs, String columnName, int maxRows, int maxGroups)
    {
        this(rs, columnName);
        try
        {
            setMaxRows(maxRows);

            if (maxRows > 0)
            {
                rs.last();
                if (rs.getRow() > maxRows || (rs instanceof TableResultSet && !((TableResultSet)rs).isComplete()))
                {
                    setComplete(false);
                    Object value = getObject(_columnIndex);
                    while (rs.previous() && value.equals(getObject(_columnIndex)))
                    {
                        // Don't need to do anything here
                    }
                    _lastRow = rs.getRow();
                    value = getObject(_columnIndex);
                    int groupCount = 1;
                    while (rs.previous())
                    {
                        if (!value.equals(getObject(_columnIndex)))
                        {
                            value = getObject(_columnIndex);
                            groupCount++;
                        }
                    }
                    _groupCount = groupCount;
                }
                rs.beforeFirst();
            }

            Object value = null;

            if (maxRows > 0 && maxGroups > 0)
            {
                int groupingCount = 0;
                while (rs.next())
                {
                    if (!getObject(_columnIndex).equals(value))
                    {
                        value = getObject(_columnIndex);
                        groupingCount++;
                        if (groupingCount > maxGroups)
                        {
                            _groupCount = maxGroups;
                            _lastRow = getRow() - 1;
                            setComplete(false);
                            break;
                        }
                    }
                }
                rs.beforeFirst();
            }
            setComplete(_lastRow == 0);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public GroupedResultSet(ResultSet rs, String columnName, int maxGroups) throws SQLException
    {
        this(rs, columnName);

        // Check if we're a complete result set. We can tell if there
        // are no more groups than our max.
        Object value = null;
        while (rs.next())
        {
            // Look for the group number changing
            if (!Objects.equals(getObject(_columnIndex), value))
            {
                _groupCount++;
                value = getObject(_columnIndex);
            }
            if (_groupCount == maxGroups)
            {
                // We reached our max number of groups. Remember
                // this spot so we can treat it as the end of the
                // result set
                _lastRow = rs.getRow();
                if (rs.next())
                {
                    setComplete(false);
                }
                break;
            }
        }

        rs.beforeFirst();
    }

    public GroupedResultSet(ResultSet rs, String columnName)
    {
        super(rs, QueryLogging.emptyQueryLogging());

        try
        {
            _columnIndex = rs.findColumn(columnName);  // Cache the index
        }
        catch (SQLException e)
        {
           throw new RuntimeSQLException(e);
        }
    }

    @Override
    public int getSize()
    {
        return _groupCount;
    }

    public String getTruncationMessage(int maxRows)
    {
        if (_lastRow != 0)
        {
            return "Displaying only the first " + _groupCount + " results.";
        }
        return super.getTruncationMessage(maxRows);
    }

    private boolean innerNext() throws SQLException
    {
        return super.next();
    }

    public boolean next() throws SQLException
    {
        if (_ignoreNext)
        {
            _ignoreNext = false;
            return true;
        }
        Object previousValue;
        if (getRow() > 0)
        {
            previousValue = getObject(_columnIndex);
        }
        else
        {
            previousValue = new Object();
        }
        Object currentValue;
        do
        {
            if (!super.next())
            {
                return false;
            }
            currentValue = getObject(_columnIndex);
        }
        while (Objects.equals(currentValue, previousValue));

        if (_lastRow != 0)
        {
            return getRow() <= _lastRow;
        }
        return true;
    }

    public ResultSet getNextResultSet() throws SQLException
    {
        return new NestedResultSet(this);
    }

    public class NestedResultSet extends ResultSetImpl
    {
        Object _currentValue;


        public NestedResultSet(ResultSet rs) throws SQLException
        {
            super(rs, QueryLogging.emptyQueryLogging());
            _currentValue = null;
            _rowOffset = super.getRow();  // Reset the row offset so getRow() returns index within the sub-resultset
        }

        public void close() throws SQLException
        {
            // Rely on the outer result set for closing
            _wasClosed = true;
        }

        // Treat a change of value in the grouping column as the "end" of the ResultSet
        public boolean next() throws SQLException
        {
            if (_ignoreNext)
            {
                _ignoreNext = false;
                return true;
            }
            boolean success = innerNext();

            if (!success)
                return success;

            if (null == _currentValue)
                _currentValue = getObject(_columnIndex);
            else
                success = Objects.equals(getObject(_columnIndex), _currentValue);

            if (!success)
            {
                previous();  // Back it up
            }

            return success;
        }

        public void beforeFirst() throws SQLException
        {
            if (0 == _rowOffset)
                super.beforeFirst();
            else
                super.absolute(_rowOffset);
        }

        public boolean absolute(int i) throws SQLException
        {
            if (i < 0)
            {
                throw new UnsupportedOperationException("NestedResultSets do not yet support negative offsets for absolute()");
            }
            return super.absolute(i + _rowOffset);
        } 

        public int getRow() throws SQLException
        {
            return super.getRow() - _rowOffset;
        }
    }
}

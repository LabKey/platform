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

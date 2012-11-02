package org.labkey.api.data;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * User: kevink
 * Date: 10/20/12
 *
 * Creates a simple ResultSet out of a List of Maps.
 */
public class MapListResultSet extends CachedResultSet
{
    public MapListResultSet(List<Map<String, Object>> maps)
    {
        this(maps, maps.get(0).keySet());
    }

    public MapListResultSet(List<Map<String, Object>> maps, Collection<String> columnNames)
    {
        super(createMetaData(columnNames), false, maps, true);
        try
        {
            // Avoid error message from CachedResultSet.finalize() about unclosed CachedResultSet.
            close();
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }
    }

    private static ResultSetMetaData createMetaData(Collection<String> columnNames)
    {
        ResultSetMetaDataImpl md = new ResultSetMetaDataImpl(columnNames.size());
        for (String columnName : columnNames)
        {
            ResultSetMetaDataImpl.ColumnMetaData col = new ResultSetMetaDataImpl.ColumnMetaData();
            col.columnName = columnName;
            md.addColumn(col);
        }

        return md;
    }
}

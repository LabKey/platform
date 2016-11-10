package org.labkey.api.data;

import org.labkey.remoteapi.query.jdbc.LabKeyResultSet;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 * Impedance match a List of Maps into rows in a ResultSet. Implements TableResultSet
 * so it can be passed into a Results and usable in a DataRegion.
 * Not a complete implementation, but might be useful in more than
 * just the case I needed it.
 *
 * Uses two classes from the LabKey java client API, LabKeyResultSet
 * and LabKeyResultSet.Column.
 *
 * User: tgaluhn
 * Date: 11/9/2016
 */
public class MapListResultSet extends LabKeyResultSet implements TableResultSet
{

    /**
     *  Main constructor, allowing flexible combination of data types for columns
     *
     * @param rows List of maps. Maps should have one entry per ResultSet column
     * @param cols The list of LabKeyResultSet.Columns
     */
    public MapListResultSet(List<Map<String, Object>> rows, List<Column> cols)
    {
        super(rows, cols, null);
    }

    /**
     *  Convenience constructor for creating Column list from string names.
     *  Assumes all columns correspond to Strings; if a different data type is needed,
     *  use the above constructor instead.
     * @param rows List of maps
     * @param cols Var args set of column names. Will be converted into a List<Column>,
     *             with all Columns set to String.class data type.
     */
    public MapListResultSet(List<Map<String, Object>> rows, String... cols)
    {
        this(rows, makeStringColumns(Arrays.asList(cols)));
    }

    private static List<Column> makeStringColumns(List<String> columnNames)
    {
        return columnNames.stream().map(name -> new Column(name, String.class)).collect(Collectors.toList());
    }

    @Override
    public boolean isComplete()
    {
        return true;
    }

    @Override
    public Map<String, Object> getRowMap() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Map<String, Object>> iterator()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getTruncationMessage(int maxRows)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getSize()
    {
        return _rows.size();
    }
}

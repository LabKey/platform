package org.labkey.api.etl;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.query.BatchValidationException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: May 23, 2011
 * Time: 5:01:51 PM
 */
public class MapDataIterator extends AbstractDataIterator implements DataIterator
{
    List<ColumnInfo> _cols = new ArrayList<ColumnInfo>();
    final List<Map<String,Object>> _rows;
    int _currentRow = -1;
    

    public MapDataIterator(List<ColumnInfo> cols, List<Map<String,Object>> rows)
    {
        super(null);
        cols.add(new ColumnInfo("_rowNumber", JdbcType.INTEGER));
        cols.addAll(cols);
        _rows = initRows(rows);
    }
    
    public MapDataIterator(Set<String> colNames, List<Map<String,Object>> rows)
    {
        super(null);
        _cols.add(new ColumnInfo("_rowNumber", JdbcType.INTEGER));
        for (String name : colNames)
            _cols.add(new ColumnInfo(name));
        _rows = initRows(rows);
    }

    private List<Map<String,Object>> initRows(List<Map<String,Object>> rows)
    {
        boolean debug = false;
        assert true == (debug = true);

        if (debug)
        {
            ArrayList<Map<String,Object>> copy = new ArrayList<Map<String,Object>>(rows.size());
            for (Map<String,Object> row : rows)
                copy.add(Collections.unmodifiableMap(row));
            return copy;
        }
        else
        {
            return rows;
        }
    }

    @Override
    public int getColumnCount()
    {
        return _cols.size()-1;
    }

    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        return _cols.get(i);
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        return ++_currentRow < _rows.size();
    }

    @Override
    public Object get(int i)
    {
        if (i == 0)
            return _currentRow+1;
        return _rows.get(_currentRow).get(_cols.get(i).getName());
    }

    @Override
    public void close() throws IOException
    {
    }
}

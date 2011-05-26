package org.labkey.api.etl;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.query.ValidationException;

import java.io.IOException;
import java.util.List;

/**
* Created by IntelliJ IDEA.
* User: matthewb
* Date: 2011-05-20
* Time: 2:16 PM
*/
class StringTestIterator implements DataIterator
{
    final List<String> columns;
    final List<String[]> data;
    int row = -1;

    void reset()
    {
        row = -1;
    }

    StringTestIterator(List<String> columns, List<String[]> data)
    {
        this.columns = columns;
        this.data = data;
    }

    @Override
    public int getColumnCount()
    {
        return columns.size();
    }

    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        if (i==0)
            return new ColumnInfo("_rownumber", JdbcType.INTEGER);
        return new ColumnInfo(columns.get(i-1),JdbcType.VARCHAR);
    }

    @Override
    public boolean next() throws ValidationException
    {
        return ++row < data.size();
    }

    @Override
    public Object get(int i)
    {
        if (0==i)
            return row+1;
        return data.get(row)[i-1];
    }

    @Override
    public void close() throws IOException
    {
    }
}

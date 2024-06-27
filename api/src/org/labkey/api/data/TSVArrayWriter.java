package org.labkey.api.data;

import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.util.FileUtil;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

// This class supports generating files with duplicate column names. Consider using TSVMapWriter if
// multiple identical column names is not an implementation concern.
public class TSVArrayWriter extends TSVWriter
{
    private final List<String> _columns;
    private final List<List<String>> _rows;
    private final String _fileName;

    public TSVArrayWriter(String fileName, ColumnDescriptor[] columns, List<Object[]> rows)
    {
        _fileName = fileName;
        _columns = Arrays.stream(columns)
                .map(ColumnDescriptor::getColumnName)
                .collect(Collectors.toList());
        _rows = rows.stream()
                .map(array -> Arrays.stream(array)
                        .map(obj -> (obj == null) ? "" : (obj instanceof String) ? (String) obj : String.valueOf(obj))
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());
    }

    @Override
    protected void writeColumnHeaders()
    {
        writeLine(_columns);
    }

    @Override
    protected int writeBody()
    {
        int rowCount = 0;

        for (List<String> row : _rows)
        {
            writeLine(row);
        }

        return rowCount;
    }

    @Override
    protected String getFilename()
    {
        return FileUtil.makeLegalName(_fileName);
    }
}

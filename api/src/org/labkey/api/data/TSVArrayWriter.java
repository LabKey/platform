package org.labkey.api.data;

import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.util.FileUtil;

import java.util.Arrays;
import java.util.List;

// This class supports generating files with duplicate column names. Consider using TSVMapWriter if
// multiple identical column names is not an implementation concern.
public class TSVArrayWriter extends TSVWriter
{
    private final List<String> _columns;
    private final List<List<String>> _rows;
    private final String _fileName;

    public TSVArrayWriter(String fileName, List<ColumnDescriptor> columns, List<Object[]> rows)
    {
        _fileName = fileName;
        _columns = columns.stream()
                .map(ColumnDescriptor::getColumnName)
                .toList();
        _rows = rows.stream()
                .map(array -> Arrays.stream(array)
                        .map(obj -> (obj == null) ? "" : String.valueOf(obj))
                        .toList())
                .toList();
    }

    @Override
    protected void writeColumnHeaders()
    {
        writeLine(_columns);
    }

    @Override
    protected int writeBody()
    {
        for (List<String> row : _rows)
        {
            writeLine(row);
        }

        return _rows.size();
    }

    @Override
    protected String getFilename()
    {
        return FileUtil.makeLegalName(_fileName + "." + getFilenameExtension());
    }
}

/*
 * Copyright (c) 2024-2026 LabKey Corporation
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

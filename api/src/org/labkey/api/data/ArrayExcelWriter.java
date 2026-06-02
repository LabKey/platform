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

import org.apache.poi.ss.usermodel.Sheet;
import org.labkey.api.reader.ColumnDescriptor;

import java.util.ArrayList;
import java.util.List;

// This class supports generating Excel files with duplicate column names. Consider using MapArrayExcelWriter if
// multiple identical column names is not an implementation concern.
public class ArrayExcelWriter extends ExcelWriter
{
    private final List<Object[]> data;
    private int currentRow = 0;

    /**
     * @param data The data rows, in which index position of a value corresponds to the desired respective column index
     * @param cols The columns, in which ordering determines the left-to-right column ordering in the generated Excel
     */
    public ArrayExcelWriter(List<Object[]> data, List<ColumnDescriptor> cols)
    {
        super(ExcelDocumentType.xlsx);
        this.data = data;
        List<DisplayColumn> displayColumns = new ArrayList<>();

        for (int i = 0; i < cols.size(); i++)
        {
            ColumnDescriptor col = cols.get(i);
            displayColumns.add(new ArrayDisplayColumn(col.name, col.clazz, i));
        }

        setDisplayColumns(displayColumns);
    }

    @Override
    public void renderGrid(RenderContext ctx, Sheet sheet, List<ExcelColumn> visibleColumns) throws MaxRowsExceededException
    {
        for (currentRow = 0; currentRow < data.size(); currentRow++)
        {
            renderGridRow(sheet, ctx, visibleColumns);
        }
    }

    public class ArrayDisplayColumn extends AbstractExcelDisplayColumn
    {
        private final int _position;

        public ArrayDisplayColumn(String name, Class<?> valueClass, int position)
        {
            super(name, name, valueClass);
            _position = position;
        }

        @Override
        public Object getValue(RenderContext ctx)
        {
            return data.get(currentRow)[_position];
        }
    }
}

/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

package org.labkey.api.study;

import org.apache.poi.ss.usermodel.Sheet;
import org.labkey.api.data.AbstractExcelDisplayColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.ExcelColumn;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.RenderContext;
import org.labkey.api.reader.ColumnDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MapArrayExcelWriter extends ExcelWriter
{
    private final List<Map<String,Object>> maps;
    private int currentRow = 0;

    public MapArrayExcelWriter(List<Map<String, Object>> maps, ColumnDescriptor[] cols)
    {
        super(ExcelDocumentType.xls);
        this.maps = maps;
        List<DisplayColumn> xlcols = new ArrayList<>();
        for (ColumnDescriptor col : cols)
            xlcols.add(new MapArrayDisplayColumn(col.name, col.clazz));

        setDisplayColumns(xlcols);
    }

    @Override
    public void renderGrid(RenderContext ctx, Sheet sheet, List<ExcelColumn> visibleColumns) throws MaxRowsExceededException
    {
        for (currentRow = 0; currentRow < maps.size(); currentRow++)
        {
            renderGridRow(sheet, ctx, visibleColumns);
        }
    }

    public class MapArrayDisplayColumn extends AbstractExcelDisplayColumn
    {
        public MapArrayDisplayColumn(String name, Class<?> valueClass)
        {
            super(name, name, valueClass);
        }

        @Override
        public Object getValue(RenderContext ctx)
        {
            //Ignore the context.
            return maps.get(currentRow).get(getName());
        }
    }
}

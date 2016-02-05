/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

package org.labkey.study.designer;

import org.apache.poi.ss.usermodel.Sheet;
import org.labkey.api.data.*;
import org.labkey.api.view.HttpView;
import org.labkey.api.reader.ColumnDescriptor;

import java.util.*;
import java.sql.SQLException;
import java.io.Writer;
import java.io.IOException;

/**
 * User: Mark Igra
 * Date: May 4, 2007
 * Time: 3:08:34 PM
 */
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
    public void renderGrid(RenderContext ctx, Sheet sheet, List<ExcelColumn> visibleColumns) throws SQLException, MaxRowsExceededException
    {
        for (currentRow = 0; currentRow < maps.size(); currentRow++)
        {
            renderGridRow(sheet, ctx, visibleColumns);
        }
    }

    public class MapArrayDisplayColumn extends DisplayColumn
    {
        Class valueClass;

        public MapArrayDisplayColumn(String name, Class valueClass)
        {
            this(name, name, valueClass);
        }

        public MapArrayDisplayColumn(String name, String caption, Class valueClass)
        {
            setName(name);
            setCaption(caption);
            this.valueClass = valueClass;

        }

        @Override
        public Object getValue(RenderContext ctx)
        {
            //Ignore the context.
            return maps.get(currentRow).get(getName());
        }

        @Override
        public Class getValueClass()
        {
            return valueClass; 
        }


        //NOTE: Methods beyond here are unimplemented, just abstract in base class!
        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }

        @Override
        public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }

        @Override
        public void renderTitle(RenderContext ctx, Writer out) throws IOException
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }

        @Override
        public boolean isSortable()
        {
            return false;
        }

        @Override
        public boolean isFilterable()
        {
            return false;
        }

        @Override
        public boolean isEditable()
        {
            return false;
        }

        @Override
        public void renderFilterOnClick(RenderContext ctx, Writer out) throws IOException
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }

        @Override
        public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }

        @Override
        public void setURL(String url)
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }

        @Override
        public String getURL()
        {
            return null;
        }

        @Override
        public String renderURL(RenderContext ctx)
        {
            return null;
        }

        @Override
        public boolean isQueryColumn()
        {
            return false;
        }

        @Override
        public ColumnInfo getColumnInfo()
        {
            return null;
        }

        @Override
        public void render(RenderContext ctx, Writer out) throws IOException
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }
    }
}

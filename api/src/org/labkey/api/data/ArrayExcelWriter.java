package org.labkey.api.data;

import org.apache.poi.ss.usermodel.Sheet;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.view.ActionURL;

import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class ArrayExcelWriter extends ExcelWriter
{
    private final List<List<Object>> data;
    private int currentRow = 0;

    public ArrayExcelWriter(List<List<Object>> data, ColumnDescriptor[] cols)
    {
        super(ExcelDocumentType.xls);
        this.data = data;
        List<DisplayColumn> xlcols = new ArrayList<>();

        for (int i = 0; i < cols.length; i++)
        {
            ColumnDescriptor col = cols[i];
            xlcols.add(new MapArrayDisplayColumn(col.name, col.clazz, i));
        }

        setDisplayColumns(xlcols);
    }

    @Override
    public void renderGrid(RenderContext ctx, Sheet sheet, List<ExcelColumn> visibleColumns) throws MaxRowsExceededException
    {
        for (currentRow = 0; currentRow < data.size(); currentRow++)
        {
            renderGridRow(sheet, ctx, visibleColumns);
        }
    }

    public class MapArrayDisplayColumn extends DisplayColumn
    {
        Class valueClass;
        int position;

        public MapArrayDisplayColumn(String name, Class valueClass, int position)
        {
            this(name, name, valueClass, position);
        }

        public MapArrayDisplayColumn(String name, String caption, Class valueClass, int position)
        {
            setName(name);
            setCaption(caption);
            this.valueClass = valueClass;
            this.position = position;
        }

        @Override
        public Object getValue(RenderContext ctx)
        {
            return data.get(currentRow).get(position);
        }

        @Override
        public Class getValueClass()
        {
            return valueClass; 
        }


        //NOTE: Methods beyond here are unimplemented, just abstract in base class
        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out)
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }

        @Override
        public void renderDetailsCellContents(RenderContext ctx, Writer out)
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }

        @Override
        public void renderTitle(RenderContext ctx, Writer out)
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
        public void renderFilterOnClick(RenderContext ctx, Writer out)
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }

        @Override
        public void renderInputHtml(RenderContext ctx, Writer out, Object value)
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }

        @Override
        public void setURL(ActionURL url)
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
        public void render(RenderContext ctx, Writer out)
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }
    }
}

package org.labkey.study.designer;

import org.labkey.api.data.*;
import org.labkey.api.view.HttpView;
import org.labkey.common.tools.TabLoader;
import jxl.write.WritableSheet;
import jxl.write.WriteException;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.io.Writer;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: May 4, 2007
 * Time: 3:08:34 PM
 */
public class MapArrayExcelWriter extends ExcelWriter
{
    Map<String,Object>[] maps;
    int currentRow = 0;

    public MapArrayExcelWriter(Map<String,Object>[] maps)
    {
        this.maps = maps;
        assert maps.length > 0;
        List<DisplayColumn> cols = new ArrayList<DisplayColumn>();
        for (String key : maps[0].keySet())
            cols.add(new MapArrayDisplayColumn(key, null != maps[0].get(key) ? maps[0].get(key).getClass() : Object.class));

        setColumns(cols);
    }

    public MapArrayExcelWriter(Map<String,Object>[] maps, TabLoader.ColumnDescriptor[] cols)
    {
        this.maps = maps;
        List<DisplayColumn> xlcols = new ArrayList<DisplayColumn>();
        for (TabLoader.ColumnDescriptor col : cols)
            xlcols.add(new MapArrayDisplayColumn(col.name, col.clazz));

        setColumns(xlcols);
    }

    @Override
    public void renderGrid(WritableSheet sheet, List<ExcelColumn> visibleColumns) throws SQLException, WriteException, MaxRowsExceededException
    {
        RenderContext ctx = new RenderContext(HttpView.currentContext());

        for (currentRow = 0; currentRow < maps.length; currentRow++)
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

        public Object getValue(RenderContext ctx)
        {
            //Ignore the context.
            return maps[currentRow].get(getName());
        }

        public Class getValueClass()
        {
            return valueClass; 
        }


        //NOTE: Methods beyond here are unimplemented, just abstract in base class!
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }

        public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }

        public void renderTitle(RenderContext ctx, Writer out) throws IOException
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }

        public boolean isSortable()
        {
            return false;
        }

        public boolean isFilterable()
        {
            return false;
        }

        public boolean isEditable()
        {
            return false;
        }

        public void renderSortHref(RenderContext ctx, Writer out) throws IOException
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }

        public void renderFilterOnClick(RenderContext ctx, Writer out) throws IOException
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }

        public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }

        public void setURL(String url)
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }

        public String getURL()
        {
            return null;
        }

        public String getURL(RenderContext ctx)
        {
            return null;
        }

        public boolean isQueryColumn()
        {
            return false;
        }

        public void addQueryColumns(Set<ColumnInfo> columns)
        {
        }

        public ColumnInfo getColumnInfo()
        {
            return null;
        }

        public void render(RenderContext ctx, Writer out) throws IOException
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }
    }
}

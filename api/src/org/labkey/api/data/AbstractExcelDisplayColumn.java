package org.labkey.api.data;

import org.labkey.api.util.HtmlString;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.HtmlWriter;

public abstract class AbstractExcelDisplayColumn extends DisplayColumn
{
    private final Class<?> _valueClass;

    public AbstractExcelDisplayColumn(String name, String caption, Class<?> valueClass)
    {
        setName(name);
        setCaption(caption);
        _valueClass = valueClass;
    }

    @Override
    public Class<?> getValueClass()
    {
        return _valueClass;
    }

    //NOTE: Methods below are unimplemented, just abstract in base class

    @Override
    public void renderGridCellContents(RenderContext ctx, HtmlWriter out)
    {
        throw new UnsupportedOperationException("This is for excel only.");
    }

    @Override
    public void renderDetailsCellContents(RenderContext ctx, HtmlWriter out)
    {
        throw new UnsupportedOperationException("This is for excel only.");
    }

    @Override
    public void renderInputHtml(RenderContext ctx, HtmlWriter out, Object value)
    {
        throw new UnsupportedOperationException("This is for excel only.");
    }

    @Override
    public HtmlString getTitle(RenderContext ctx)
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
    public String getFilterOnClick(RenderContext ctx)
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
    public void render(RenderContext ctx, HtmlWriter out)
    {
        throw new UnsupportedOperationException("This is for excel only.");
    }
}

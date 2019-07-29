package org.labkey.experiment.api;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.RenderContext;

import java.io.IOException;
import java.io.Writer;

import static org.labkey.api.util.PageFlowUtil.filter;

public class LineageDisplayColumn extends DisplayColumn
{
    final ColumnInfo lsid;
    final String displayField;
    final boolean parents;
    final Integer depth;
    final String expType;
    final String cpasType;


    LineageDisplayColumn(ColumnInfo lsid, String displayField, boolean parents, Integer depth, String expType, String cpasType)
    {
        this.lsid = lsid;
        this.displayField = displayField;
        this.parents = parents;
        this.depth = depth;
        this.expType = expType;
        this.cpasType = cpasType;
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        out.write(filter(getValue(ctx)));
    }

    @Override
    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        out.write(filter(getValue(ctx)));
    }

    @Override
    public void renderTitle(RenderContext ctx, Writer out) throws IOException
    {

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
    }

    @Override
    public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
    {
    }

    @Override
    public boolean isQueryColumn()
    {
        return false;
    }

    @Override
    public ColumnInfo getColumnInfo()
    {
        // TODO return display column info
        return lsid;
    }

    @Override
    public Object getValue(RenderContext ctx)
    {
        return this.getClass().getName() + ": " + this.cpasType + "," + this.displayField;
    }

    @Override
    public Class getValueClass()
    {
        return String.class;
    }

    @Override
    public void render(RenderContext ctx, Writer out) throws IOException
    {
        out.write(filter(getValue(ctx)));
    }
}

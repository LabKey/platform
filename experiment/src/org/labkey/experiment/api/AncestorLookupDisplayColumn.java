package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.util.HtmlString;

public class AncestorLookupDisplayColumn extends DataColumn
{
    private final ColumnInfo _lookupCol;

    public AncestorLookupDisplayColumn(ColumnInfo foreignKey, ColumnInfo currentCol)
    {
        super(currentCol);
        _lookupCol = foreignKey;
    }

    @Override
    public @NotNull HtmlString getFormattedHtml(RenderContext ctx)
    {
        Long lookupKey = getLookupRowId(ctx);
        if (lookupKey < 0)
            return HtmlString.of("<" + (-lookupKey) + " values>");
        return super.getFormattedHtml(ctx);
    }

    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        Long lookupKey = getLookupRowId(ctx);
        if (lookupKey < 0)
            return  (-lookupKey) + " values";
        return super.getDisplayValue(ctx);
    }

    @Override
    public Object getValue(RenderContext ctx)
    {
        Long lookupKey = getLookupRowId(ctx);
        if (lookupKey < 0)
            return lookupKey;
        return super.getValue(ctx);
    }

    private Long getLookupRowId(RenderContext ctx)
    {
        return (Long) _lookupCol.getValue(ctx);
    }
}

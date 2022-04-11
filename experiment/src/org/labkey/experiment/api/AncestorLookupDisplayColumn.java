package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.util.HtmlString;

/**
 * Display column for fields that go through an Ancestor (Lineage) lookup via the ClosureQuery.
 * When an entity has more than one ancestor of a particular type the ClosureQuery value is
 * the negative of the number of ancestors of that type.  We take advantage of this to display
 * a more informative message about why users don't see actual data in the column.
 *
 * This currently supports only the first level of lookups. If the data being
 * displayed is from a lookup that comes from another ancestor lookup, we don't
 * have the foreign key for the original ancestor lookup to be able to find out
 * if that original ancestor type had more than one value.
 */
public class AncestorLookupDisplayColumn extends DataColumn
{
    private final ColumnInfo _lookupCol;

    public AncestorLookupDisplayColumn(ColumnInfo currentCol)
    {
        this(null, currentCol);
    }

    public AncestorLookupDisplayColumn(@Nullable ColumnInfo lookupCol, ColumnInfo currentCol)
    {
        super(currentCol);
        _lookupCol = lookupCol == null ? currentCol : lookupCol;
    }

    @Override
    public @NotNull HtmlString getFormattedHtml(RenderContext ctx)
    {
        Integer lookupKey = getLookupId(ctx);
        if (lookupKey != null && lookupKey < 0)
            return HtmlString.unsafe("<span style=\"color: gray;\"><"+ (-lookupKey) + " values></span>");
        return super.getFormattedHtml(ctx);
    }

    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        Integer lookupKey = getLookupId(ctx);
        if (lookupKey != null && lookupKey < 0)
            return  (-lookupKey) + " values";
        return super.getDisplayValue(ctx);
    }

    @Override
    public Object getValue(RenderContext ctx)
    {
        Integer lookupKey = getLookupId(ctx);
        if (lookupKey != null && lookupKey < 0)
            return lookupKey;
        return super.getValue(ctx);
    }

    @Override
    public String renderURL(RenderContext ctx)
    {
        Integer lookupKey = getLookupId(ctx);
        if (lookupKey != null && lookupKey < 0)
            return null;
        return super.renderURL(ctx);
    }

    private Integer getLookupId(RenderContext ctx)
    {
        return (Integer) _lookupCol.getValue(ctx);
    }
}

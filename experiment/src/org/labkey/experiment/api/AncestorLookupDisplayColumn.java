package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.util.HtmlString;

/**
 * Display column for showing the ancestor names via the ClosureQuery.  When an entity has
 * more than one ancestor of a particular type the ClosureQuery value is the negative of the
 * number of ancestors of that type.  We take advantage of this to display a more informative
 * message about why users don't see actual data in the column.
 */
public class AncestorLookupDisplayColumn extends DataColumn
{
    public AncestorLookupDisplayColumn(ColumnInfo col)
    {
        super(col);
    }

    @Override
    public @NotNull HtmlString getFormattedHtml(RenderContext ctx)
    {
        if (getBoundColumn() != null)
        {
            Long boundColValue = (Long) getBoundColumn().getValue(ctx);
            if (boundColValue != null && boundColValue < 0)
            {
                return HtmlString.of("<" + (-boundColValue) + " values>");
            }
        }
        return super.getFormattedHtml(ctx);
    }

    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        if (getBoundColumn() != null)
        {
            Long boundColValue = (Long) getBoundColumn().getValue(ctx);
            if (boundColValue != null && boundColValue < 0)
            {
                return (-boundColValue) + " values";
            }
        }
        return super.getDisplayValue(ctx);
    }

    // Don't return a URL when we have more than one ancestor so we don't link into nothingness
    @Override
    public String renderURL(RenderContext ctx)
    {
        if (getBoundColumn() != null)
        {
            Long boundColValue = (Long) getBoundColumn().getValue(ctx);
            if (boundColValue != null && boundColValue < 0)
                return null;
        }
        return super.renderURL(ctx);
    }
}

package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.HtmlString;

import java.util.Set;

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
    private final FieldKey _lookupFK;
    private final DisplayColumn _dc;

    public AncestorLookupDisplayColumn(ColumnInfo currentCol)
    {
        this(null, currentCol, null);
    }

    public AncestorLookupDisplayColumn(@Nullable ColumnInfo lookupCol, ColumnInfo currentCol, @Nullable DisplayColumnFactory displayColumnFactory)
    {
        super(currentCol);
        _lookupCol = lookupCol == null ? currentCol : lookupCol;
        _lookupFK = lookupCol == null ? null : lookupCol.getFieldKey();
        _dc = displayColumnFactory != null ? displayColumnFactory.createRenderer(currentCol) : null;
    }

    @Override
    public void setFormatString(String formatString)
    {
        super.setFormatString(formatString);
        if (_dc != null)
            _dc.setFormatString(formatString);
    }

    @Override
    public @NotNull HtmlString getFormattedHtml(RenderContext ctx)
    {
        Integer lookupKey = getLookupId(ctx);
        if (lookupKey != null && lookupKey < 0)
            return HtmlString.unsafe("<span style=\"color: gray;\"><"+ (-lookupKey) + " values></span>");

        if (_dc != null)
            return _dc.getFormattedHtml(ctx);

        return super.getFormattedHtml(ctx);
    }

    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        Integer lookupKey = getLookupId(ctx);
        if (lookupKey != null && lookupKey < 0)
            return  (-lookupKey) + " values";

        if (_dc != null)
            return _dc.getDisplayValue(ctx);

        return super.getDisplayValue(ctx);
    }

    @Override
    @Nullable
    public String getFormattedText(RenderContext ctx)
    {
        Integer lookupKey = getLookupId(ctx);
        if (lookupKey != null && lookupKey < 0)
            return  (-lookupKey) + " values";

        if (_dc != null)
            return _dc.getFormattedText(ctx);

        return super.getFormattedText(ctx);
    }

    @Override
    public Object getValue(RenderContext ctx)
    {
        Integer lookupKey = getLookupId(ctx);
        if (lookupKey != null && lookupKey < 0)
            return lookupKey;

        Object value = null;
        if (_dc != null)
            value = _dc.getValue(ctx);

        if (value == null)
            value = super.getValue(ctx);

        return value;
    }

    @Override
    public String renderURL(RenderContext ctx)
    {
        Integer lookupKey = getLookupId(ctx);
        if (lookupKey != null && lookupKey < 0)
            return null;

        if (_dc != null)
            return _dc.renderURL(ctx);

        return super.renderURL(ctx);
    }

    @Override
    public void addQueryFieldKeys(Set<FieldKey> keys)
    {
        super.addQueryFieldKeys(keys);
        if (_lookupFK != null)
            keys.add(_lookupFK);
    }

    private Integer getLookupId(RenderContext ctx)
    {
        return (Integer) _lookupCol.getValue(ctx);
    }
}

package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.PageFlowUtil;

/**
 * User: jeckels
 * Date: 10/9/2015
 */
public class WhitespacePreservingDisplayColumnFactory implements DisplayColumnFactory
{
    @Override
    public DisplayColumn createRenderer(ColumnInfo col)
    {
        return new WhitespacePreservingDisplayColumn(col);
    }

    public static class WhitespacePreservingDisplayColumn extends DataColumn
    {
        public WhitespacePreservingDisplayColumn(ColumnInfo col)
        {
            super(col);
        }

        @NotNull
        @Override
        public String getFormattedValue(RenderContext ctx)
        {
            Object value = ctx.get(getDisplayColumn().getFieldKey());
            if (value == null)
            {
                // If we couldn't find it by FieldKey, check by alias as well
                value = getDisplayColumn().getValue(ctx);
            }

            if (value == null)
            {
                return "&nbsp;";
            }
            return PageFlowUtil.filter(value.toString(), true, false);
        }
    }
}

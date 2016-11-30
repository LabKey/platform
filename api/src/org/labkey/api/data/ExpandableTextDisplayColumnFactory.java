package org.labkey.api.data;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.template.ClientDependency;

import java.util.Set;

public class ExpandableTextDisplayColumnFactory implements DisplayColumnFactory
{
    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new ExpandableTextDataColumn(colInfo);
    }

    static class ExpandableTextDataColumn extends DataColumn
    {
        ExpandableTextDataColumn(ColumnInfo col)
        {
            super(col, false);
        }

        @NotNull
        @Override
        public String getFormattedValue(RenderContext ctx)
        {
            Object value = getValueFromCtx(ctx);
            if (null == value)
                return "";

            return getFormattedOutputText(value.toString(), 10, 500);
        }

        protected Object getValueFromCtx(RenderContext ctx)
        {
            Object value = ctx.get(getBoundColumn().getFieldKey());
            if (value == null)
            {
                // If we couldn't find it by FieldKey, check by alias as well
                value = getValue(ctx);
            }
            return value;
        }

        protected String getFormattedOutputText(String value, @Nullable Integer maxLineCount, @Nullable Integer maxCharCount)
        {
            // Too bad there's no way to configure EOL characters for the Jackson pretty printer.
            // It seems to use system defaults.
            String[] outputLines = value.split("\r\n|\r|\n");

            String outputTxt = "<div class='expandable-text-container'>" + StringUtils.join(outputLines, "<br/>") + "</div>";

            boolean exceedsMaxLineCount = maxLineCount != null && outputLines.length > maxLineCount;
            boolean exceedsMaxCharCount = maxCharCount != null && value.length() > maxCharCount;
            if (exceedsMaxLineCount || exceedsMaxCharCount)
            {
                outputTxt = "<div class='expandable-text-collapsed'>" + outputTxt
                        + "<div class='expandable-text-overflow'></div>"
                        + "<div class='expandable-text-showmore'><div class='labkey-wp-text-buttons'><a href='javascript:;' onclick=\"LABKEY.ExpandableTextDisplayColumn.showMore(this);\">Show More&#9660;</a></div></div>"
                        + "<div class='expandable-text-showless'><div class='labkey-wp-text-buttons'><a href='javascript:;' onclick=\"LABKEY.ExpandableTextDisplayColumn.showLess(this);\">Show Less&#9650;</a></div></div>"
                        + "</div>";
            }

            return outputTxt;
        }

        @NotNull
        @Override
        public Set<ClientDependency> getClientDependencies()
        {
            return PageFlowUtil.set(
                    ClientDependency.fromPath("core/ExpandableTextDisplayColumn.js"),
                    ClientDependency.fromPath("core/ExpandableTextDisplayColumn.css")
            );
        }
    }
}

/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.template.ClientDependency;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            String filteredValue = PageFlowUtil.filter(value, true);
            Pattern p = Pattern.compile("<br>");
            Matcher m = p.matcher(filteredValue);
            int count = 0;
            while (m.find())
                count++;
            String outputTxt = "<div class='expandable-text-container'>" + filteredValue + "</div>";

            boolean exceedsMaxLineCount = maxLineCount != null && count > maxLineCount;
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

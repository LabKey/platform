/*
 * Copyright (c) 2015 LabKey Corporation
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
import org.labkey.api.util.PageFlowUtil;

/**
 * Renders the value in HTML preserving whitespace, including spaces and newlines.
 *
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

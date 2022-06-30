/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
package org.labkey.api.audit.data;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A display column used to render encoded audit datamaps (primarily used to show record changes), in a user readeable format
 */
public class DataMapColumn extends DataColumn
{
    public DataMapColumn(ColumnInfo col)
    {
        super(col);
    }

    @Override @NotNull
    public HtmlString getFormattedHtml(RenderContext ctx)
    {
        HtmlStringBuilder b = HtmlStringBuilder.of();
        HtmlString separator = HtmlString.EMPTY_STRING;
        for (String s : formatColumn(getValue(ctx)))
        {
            b.append(separator);
            separator = HtmlString.BR;
            b.append(s);
        }
        return b.getHtmlString();
    }

    @Override
    public String getTsvFormattedValue(RenderContext ctx)
    {
        return StringUtils.join(formatColumn(getValue(ctx)), "\n");
    }

    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        return StringUtils.join(formatColumn(getValue(ctx)), "\n");
    }

    @Override
    public boolean isFilterable()
    {
        return false;
    }
    @Override
    public boolean isSortable()
    {
        return false;
    }

    @NotNull
    private List<String> formatColumn(@Nullable Object contents)
    {
        if (contents instanceof String)
        {
            List<String> result = new ArrayList<>();

            for (Map.Entry<String, String> entry : AbstractAuditTypeProvider.decodeFromDataMap((String) contents).entrySet())
            {
                result.add(entry.getKey() + ": " + entry.getValue());
            }
            return result;
        }
        return Collections.emptyList();
    }

    public static class Factory implements DisplayColumnFactory
    {
        @Override
        public DisplayColumn createRenderer(ColumnInfo colInfo)
        {
            return new DataMapColumn(colInfo);
        }
    }
}

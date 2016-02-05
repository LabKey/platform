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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;

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
    public String getFormattedValue(RenderContext ctx)
    {
        return formatColumn(getValue(ctx), "<br>");
    }

    @Override
    public String getTsvFormattedValue(RenderContext ctx)
    {
        return formatColumn(getValue(ctx), "\n");
    }

    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        return formatColumn(getValue(ctx), "\n");
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
    private String formatColumn(@Nullable Object contents, String lineBreak)
    {
        if (contents instanceof String)
        {
            String delim = "";
            StringBuilder sb = new StringBuilder();

            for (Map.Entry<String, String> entry : AbstractAuditTypeProvider.decodeFromDataMap((String) contents).entrySet())
            {
                sb.append(delim);
                sb.append(entry.getKey()).append(": ").append(entry.getValue());

                delim = lineBreak;
            }
            return sb.toString();
        }
        return "";
    }
}

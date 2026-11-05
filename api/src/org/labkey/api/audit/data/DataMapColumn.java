/*
 * Copyright (c) 2012-2026 LabKey Corporation
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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A display column used to render encoded audit datamaps (primarily used to show record changes), in a user readeable format
 */
public class DataMapColumn extends DataColumn
{
    private static final String DECODED_CACHE_KEY = DataMapColumn.class.getName() + ".decoded";
    private static final int MAX_CACHED_DECODES = 8;

    public DataMapColumn(ColumnInfo col)
    {
        super(col);
    }

    @Override @NotNull
    public HtmlString getFormattedHtml(RenderContext ctx)
    {
        HtmlStringBuilder b = HtmlStringBuilder.of();
        HtmlString separator = HtmlString.EMPTY_STRING;
        for (String s : formatColumn(ctx))
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
        return StringUtils.join(formatColumn(ctx), "\n");
    }

    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        return StringUtils.join(formatColumn(ctx), "\n");
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
    private List<String> formatColumn(RenderContext ctx)
    {
        return decode(ctx, getValue(ctx)).entrySet().stream()
            .map(entry -> entry.getKey() + ": " + entry.getValue())
            .toList();
    }

    /**
     * Old and new record maps are each decoded by their own column and again by the diff column, so share the
     * read-only result across columns of the current row.
     */
    @NotNull
    static Map<String, String> decode(RenderContext ctx, @Nullable Object contents)
    {
        if (!(contents instanceof String s))
            return Collections.emptyMap();

        @SuppressWarnings("unchecked")
        Map<String, Map<String, String>> cache = (Map<String, Map<String, String>>) ctx.computeIfAbsent(DECODED_CACHE_KEY, k -> new LinkedHashMap<String, Map<String, String>>(16, 0.75f, true)
        {
            // Holds a couple of rows' worth; the render context lives for the whole export
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Map<String, String>> eldest)
            {
                return size() > MAX_CACHED_DECODES;
            }
        });
        return cache.computeIfAbsent(s, k -> Collections.unmodifiableMap(AbstractAuditTypeProvider.decodeFromDataMap(k)));
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

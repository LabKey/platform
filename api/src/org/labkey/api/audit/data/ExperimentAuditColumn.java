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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.HtmlWriter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public abstract class ExperimentAuditColumn<ObjectType extends ExpObject> extends DataColumn
{
    // The same object typically repeats across rows, usually on adjacent ones; cache only its name and URL, and cap the memo, so a large export can't pin one ExpObject per distinct row
    private static final int MAX_CACHED_VALUES = 1000;

    protected ColumnInfo _containerId;
    protected ColumnInfo _defaultName;
    private final Map<CacheKey, Optional<ExpLinkDisplay>> _expValues = new LinkedHashMap<>(16, 0.75f, true)
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CacheKey, Optional<ExpLinkDisplay>> eldest)
        {
            return size() > MAX_CACHED_VALUES;
        }
    };

    public static final String KEY_SEPARATOR = "~~KEYSEP~~";

    protected record ExpLink<T extends ExpObject>(T object, @Nullable ActionURL url) {}

    private record ExpLinkDisplay(String name, @Nullable ActionURL url) {}

    private record CacheKey(Object boundValue, @Nullable String containerId) {}

    public ExperimentAuditColumn(ColumnInfo col, ColumnInfo containerId, ColumnInfo defaultName)
    {
        super(col);
        _containerId = containerId;
        _defaultName = defaultName;
        setTextAlign("left");
    }

    @Override
    public String getName()
    {
        return getColumnInfo().getLabel();
    }

    @Nullable
    protected Container getContainer(RenderContext ctx)
    {
        String cId = (String)ctx.get("ContainerId");
        if (cId == null)
            cId = (String) ctx.get("Container");
        return cId == null ? null : ContainerManager.getForId(cId);
    }

    @Nullable
    protected abstract ExpLink<ObjectType> getExpValue(RenderContext ctx);

    @Nullable
    private ExpLinkDisplay getCachedExpValue(RenderContext ctx)
    {
        Container c = getContainer(ctx);
        CacheKey key = new CacheKey(getBoundColumn().getValue(ctx), c == null ? null : c.getId());
        return _expValues.computeIfAbsent(key, _ -> {
            ExpLink<ObjectType> link = getExpValue(ctx);
            return Optional.ofNullable(link == null ? null : new ExpLinkDisplay(link.object().getName(), link.url()));
        }).orElse(null);
    }

    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        ExpLinkDisplay value = getCachedExpValue(ctx);
        if (value != null)
        {
            return value.name();
        }

        if (_defaultName != null)
        {
            return extractFromKey3(ctx);
        }
        return null;
    }

    @Override
    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        super.addQueryColumns(columns);
        if (_containerId != null)
            columns.add(_containerId);
        if (_defaultName != null)
            columns.add(_defaultName);
    }

    @Override
    public boolean isFilterable()
    {
        return false;
    }

    @Nullable
    protected abstract String extractFromKey3(RenderContext ctx);

    @Override
    public void renderGridCellContents(RenderContext ctx, HtmlWriter out)
    {
        ExpLinkDisplay value = getCachedExpValue(ctx);
        if (value != null && value.url() != null)
        {
            out.write(LinkBuilder.simpleLink(value.name(), value.url()));
            return;
        }

        if (_defaultName != null)
        {
            String extracted = extractFromKey3(ctx);
            if (extracted != null)
                out.write(extracted);
            else
                out.write(HtmlString.NBSP);
        }
        else
        {
            out.write(HtmlString.NBSP);
        }
    }
}

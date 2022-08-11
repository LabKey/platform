/*
 * Copyright (c) 2011-2018 LabKey Corporation
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
package org.labkey.api.wiki;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.logging.LogHelper;

import java.util.Map;
import java.util.Set;

/**
 * Renders the contents of a database column in one of the supported {@link WikiRendererType} formats, as specified
 * by another column (referenced via the renderTypeColumnName constructor argument
 * User: markigra
 * Date: 10/25/11
 */
public class WikiRendererDisplayColumn extends DataColumn
{
    @NotNull
    private final String _renderTypeColumnName;
    private WikiRendererType _defaultRenderer = WikiRendererType.TEXT_WITH_LINKS;
    private static final Logger _log = LogHelper.getLogger(WikiRendererDisplayColumn.class, "Renders wiki content for grid and detail views");

    public WikiRendererDisplayColumn(ColumnInfo contentColumn, @NotNull String renderTypeColumnName, WikiRendererType defaultRenderer)
    {
        super(contentColumn);
        _renderTypeColumnName = renderTypeColumnName;
        if (null != defaultRenderer)
            _defaultRenderer = defaultRenderer;
    }

    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        return getFormattedHtml(ctx);
    }

    @Override @NotNull
    public HtmlString getFormattedHtml(RenderContext ctx)
    {
        // Remember the rendered result so we don't recreate it multiple times for a single cell in a grid
        FieldKey renderedFK = FieldKey.fromString(getBoundColumn().getFieldKey(), "Rendered");
        HtmlString rendered = (HtmlString) ctx.getRow().get(renderedFK.encode());
        if (rendered == null)
        {
            String content = (String) getValue(ctx);
            if (null == content)
                return HtmlString.NBSP;

            WikiRenderingService wikiService = WikiRenderingService.get();
            WikiRendererType rendererType = _defaultRenderer;
            Object rendererTypeName = ctx.get(getRenderTypeFieldKey());
            if (null != rendererTypeName)
            {
                try
                {
                    rendererType = WikiRendererType.valueOf(rendererTypeName.toString());
                }
                catch (IllegalArgumentException err)
                {
                    _log.error("Bad wiki renderer type: " + rendererTypeName, err);
                }
            }

            rendered = wikiService.getFormattedHtml(rendererType, content);

            Map<String, Object> newRow = new CaseInsensitiveHashMap<>(ctx.getRow());
            newRow.put(renderedFK.encode(), rendered);
            ctx.setRow(newRow);
        }
        return rendered;
    }

    private FieldKey getRenderTypeFieldKey()
    {
        return new FieldKey(getColumnInfo().getFieldKey().getParent(), _renderTypeColumnName);
    }


    @Override
    public void addQueryFieldKeys(Set<FieldKey> fieldKeys)
    {
        super.addQueryFieldKeys(fieldKeys);
        fieldKeys.add(getRenderTypeFieldKey());
    }
}

/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.services.ServiceRegistry;

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
    private String _renderTypeColumnName;
    private WikiRendererType _defaultRenderer = WikiRendererType.TEXT_WITH_LINKS;
    private static final Logger _log = Logger.getLogger(WikiRendererDisplayColumn.class);

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
        return getFormattedValue(ctx);
    }

    @Override @NotNull
    public String getFormattedValue(RenderContext ctx)
    {
        WikiService wikiService = ServiceRegistry.get().getService(WikiService.class);
        String content = (String) getValue(ctx);
        if (null == content)
            return "&nbsp";


        WikiRendererType rendererType = _defaultRenderer;
        Object rendererTypeName = ctx.get(getRenderTypeFieldKey());
        if (null != rendererTypeName)
        {
            try
            {
                rendererType = WikiRendererType.valueOf(rendererTypeName.toString());
            }
            catch(IllegalArgumentException err)
            {
                _log.error("Bad wiki renderer type: " + rendererTypeName, err);
            }
        }

        return wikiService.getFormattedHtml(rendererType, content);
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

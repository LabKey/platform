/*
 * Copyright (c) 2011 LabKey Corporation
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
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.services.ServiceRegistry;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: markigra
 * Date: 10/25/11
 * Time: 7:57 PM
 */
public class WikiRendererDisplayColumn extends DataColumn
{
    private ColumnInfo _renderTypeColumn;
    private WikiRendererType _defaultRenderer = WikiRendererType.TEXT_WITH_LINKS;
    private static final Logger _log = Logger.getLogger(WikiRendererDisplayColumn.class);

    public WikiRendererDisplayColumn(ColumnInfo contentColumn, ColumnInfo renderTypeColumn, WikiRendererType defaultRenderer)
    {
        super(contentColumn);
        _renderTypeColumn = renderTypeColumn;
        if (null != defaultRenderer)
            _defaultRenderer = defaultRenderer;
    }

    @Override
    public String getFormattedValue(RenderContext ctx)
    {
        WikiService wikiService = ServiceRegistry.get().getService(WikiService.class);
        String content = (String) getValue(ctx);
        if (null == content)
            return "&nbsp";


        WikiRendererType rendererType = null;
        if (null != _renderTypeColumn)
        {
            String rendererTypeName = (String) ctx.get(_renderTypeColumn.getFieldKey());
            if (null != rendererTypeName)
                try
                {
                    rendererType = WikiRendererType.valueOf(rendererTypeName);
                }
                catch(IllegalArgumentException err)
                {
                    _log.error("Bad wiki renderer type: " + rendererTypeName, err);
                }

        }
        if (null == rendererType)
            rendererType = _defaultRenderer;

        return wikiService.getFormattedHtml(rendererType, content);
    }


    @Override
    public void addQueryFieldKeys(Set<FieldKey> fieldKeys)
    {
        super.addQueryFieldKeys(fieldKeys);
        if (null != _renderTypeColumn)
            fieldKeys.add(_renderTypeColumn.getFieldKey());
    }
}

/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryView;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.URLHelper;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DetailsColumn extends UrlColumn
{
    TableInfo tinfo;

    public DetailsColumn(StringExpression url, TableInfo table)
    {
        super(url, "details");
        tinfo = table;

        setGridHeaderClass("");
        addDisplayClass("labkey-details");
    }

    @Override
    public void addQueryFieldKeys(Set<FieldKey> keys)
    {
        if (tinfo != null)
        {
            keys.addAll(tinfo.getDetailsURLKeys());
        }
    }

    @Override
    public boolean isVisible(RenderContext ctx)
    {
        if (!super.isVisible(ctx) || null == getURLExpression())
        {
            return false;
        }
        Map<FieldKey, ColumnInfo> fieldMap = ctx.getFieldMap();
        if (fieldMap == null)
            return false;
        return tinfo == null || getURLExpression().canRender(fieldMap.keySet());
    }
}


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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

/**
 * User: klum
 * Date: Mar 15, 2012
 */
public abstract class ExperimentAuditColumn<ObjectType extends ExpObject> extends DataColumn
{
    protected ColumnInfo _containerId;
    protected ColumnInfo _defaultName;

    public static final String KEY_SEPARATOR = "~~KEYSEP~~";

    public ExperimentAuditColumn(ColumnInfo col, ColumnInfo containerId, ColumnInfo defaultName)
    {
        super(col);
        _containerId = containerId;
        _defaultName = defaultName;
        setTextAlign("left");
    }

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
    protected abstract Pair<ObjectType, ActionURL> getExpValue(RenderContext ctx);

    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        Pair<ObjectType, ActionURL> value = getExpValue(ctx);
        if (value != null)
        {
            return value.first.getName();
        }

        if (_defaultName != null)
        {
            return extractFromKey3(ctx);
        }
        return null;
    }

    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        super.addQueryColumns(columns);
        if (_containerId != null)
            columns.add(_containerId);
        if (_defaultName != null)
            columns.add(_defaultName);
    }

    public boolean isFilterable()
    {
        return false;
    }

    @Nullable
    protected abstract String extractFromKey3(RenderContext ctx);

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Pair<ObjectType, ActionURL> value = getExpValue(ctx);
        if (value != null && value.second != null)
        {
            out.write("<a href=\"" + value.second.getLocalURIString() + "\">" + PageFlowUtil.filter(value.first.getName()) + "</a>");
            return;
        }

        if (_defaultName != null)
        {
            String extracted = extractFromKey3(ctx);
            out.write(extracted != null ? PageFlowUtil.filter(extracted) : "&nbsp;");
        }
        else
            out.write("&nbsp;");
    }

}

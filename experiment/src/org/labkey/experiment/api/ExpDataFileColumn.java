/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
package org.labkey.experiment.api;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;

import java.io.IOException;
import java.io.Writer;

/**
 * User: bbimber
 * Date: 7/3/12
 * Time: 8:52 AM
 */
abstract class ExpDataFileColumn extends DataColumn
{
    private static final String DATA_OBJECT_KEY = DataLinkColumn.class + "-DataObject";

    public ExpDataFileColumn(ColumnInfo col)
    {
        super(col);
    }

    protected ExpData getData(RenderContext ctx)
    {
        Integer rowIdObject = ctx.get(getColumnInfo().getFieldKey(), Integer.class);
        ExpData data = null;
        if (rowIdObject != null)
        {
            int rowId = rowIdObject.intValue();
            // Check if another column has already grabbed the value
            data = (ExpData)ctx.get(DATA_OBJECT_KEY);
            if (data == null || data.getRowId() != rowId)
            {
                data = ExperimentService.get().getExpData(rowId);
                // Cache it for other columns to use
                ctx.put(DATA_OBJECT_KEY, data);
            }
        }
        return data;
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        ExpData data = getData(ctx);

        if (data != null)
        {
            renderData(out, data);
        }
    }

    /** Write the value into the HTML. Responsible for HTML-encoding as necessary. */
    protected abstract void renderData(Writer out, ExpData data) throws IOException;

    @Override
    public Object getJsonValue(RenderContext ctx)
    {
        ExpData data = getData(ctx);
        if (data == null)
            return null;
        else
            return getJsonValue(data);
    }

    protected abstract Object getJsonValue(ExpData data);

    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        return getJsonValue(ctx);
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

}

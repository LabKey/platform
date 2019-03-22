/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.view;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.Filter;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Sort;
import org.springframework.validation.Errors;

import java.io.IOException;
import java.io.Writer;

/**
 * Renders a grid (table with separate columns for each field, and rows for each separate element
 * Modern code should not generally create a GridView directly - it should go through a {@link org.labkey.api.query.QueryView}
 * instead, which will in turn create a GridView.
 */
public class GridView extends DataView
{
    public GridView(DataRegion dataRegion, Errors errors)
    {
        super(dataRegion, errors);
    }

    protected boolean isColumnIncluded(ColumnInfo col)
    {
        return !col.isHidden();
    }

    public GridView(DataRegion dataRegion, RenderContext ctx)
    {
        super(dataRegion, ctx);
    }

    public void setFilter(Filter filter)
    {
        getRenderContext().setBaseFilter(filter);
    }

    public void setSort(Sort sort)
    {
        getRenderContext().setBaseSort(sort);
    }

    protected void _renderDataRegion(RenderContext ctx, Writer out) throws IOException
    {
        ctx.setMode(DataRegion.MODE_GRID);
        //Force through bottleneck
        getDataRegion().render(ctx, out);
    }
}

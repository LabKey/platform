/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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

import org.apache.log4j.Logger;
import org.labkey.api.data.*;
import org.labkey.api.util.PageFlowUtil;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;

public class GridView extends DataView
{
    private static Logger _log = Logger.getLogger(GridView.class);

    public GridView(DataRegion dataRegion)
    {
        super(dataRegion, (BindException)null);
        setupSelectionKey(dataRegion);
    }

    private void setupSelectionKey(DataRegion dataRegion)
    {
        if (dataRegion != null &&
            dataRegion.getSelectionKey() == null &&
            dataRegion.getTable() != null &&
            dataRegion.getTable().getSchema() != null)
        {
            dataRegion.setSelectionKey(DataRegionSelection.getSelectionKey(dataRegion.getTable().getSchema().getName(), dataRegion.getTable().getName(), null, dataRegion.getName()));
        }
    }

    public GridView(DataRegion dataRegion, RenderContext ctx)
    {
        super(dataRegion, ctx);
        setupSelectionKey(dataRegion);
    }

    // TODO: This should take a Filter, not a SimpleFilter
    public void setFilter(SimpleFilter filter)
    {
        getRenderContext().setBaseFilter(filter);
    }

    public void setSort(Sort sort)
    {
        getRenderContext().setBaseSort(sort);
    }

    protected void _renderDataRegion(RenderContext ctx, Writer out) throws IOException, SQLException
    {
        String err = PageFlowUtil.getStrutsError(ctx.getRequest(), "dataregion_" + getDataRegion().getName());
        if (err != null && err.length() > 0)
            out.write(err.replaceAll(System.getProperty("line.separator"), "<br>"));

        getDataRegion().renderTable(ctx, out);
    }
} 

/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.labkey.api.data.DataColumn;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.settings.AppProps;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.io.Writer;
import java.io.IOException;

/**
 * User: jeckels
* Date: Nov 7, 2008
*/
abstract class DataLinkColumn extends DataColumn
{
    private static final String DATA_OBJECT_KEY = DataLinkColumn.class + "-DataObject";

    public DataLinkColumn(ColumnInfo col)
    {
        super(col);
        setTextAlign("left");
    }

    protected abstract ActionURL getURL(ExpData data);

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        ExpData data = getData(ctx);

        if (data != null)
        {
            renderData(out, data);
        }
    }

    protected void renderData(Writer out, ExpData data) throws IOException
    {
        ActionURL url = getURL(data);
        if (url != null)
        {
            out.write("<a href=\"");
            out.write(url.toString());
            out.write("\">");
        }
        out.write(PageFlowUtil.filter(data.getName()));
        if (url != null)
        {
            out.write("</a>");
        }

        renderThumbnailPopup(out, data, url);
    }

    protected void renderThumbnailPopup(Writer out, ExpData data, ActionURL url)
            throws IOException
    {
        if (data.isInlineImage() && data.isFileOnDisk())
        {
            out.write("&nbsp;");
            String icon = "<img src=\"" + AppProps.getInstance().getContextPath() + "/_icons/image.png\" />";
            out.write(PageFlowUtil.helpPopup(data.getFile().getName(), renderThumbnailImg(data, url), true, icon, 310, url == null ? null : "window.location = '" + url.toString() + "'"));
        }
    }

    protected String renderThumbnailImg(ExpData data, ActionURL url)
    {
        StringBuilder html = new StringBuilder();
        if (data.isInlineImage() && data.isFileOnDisk())
        {
            ActionURL thumbnailURL = ExperimentController.ExperimentUrlsImpl.get().getShowFileURL(data, true);
            thumbnailURL.addParameter("maxDimension", 300);
            if (url != null)
            {
                html.append("<a href=\"");
                html.append(url);
                html.append("\">");
            }

            html.append("<img src=\"");
            html.append(thumbnailURL);
            html.append("\" />");

            if (url != null)
            {
                html.append("</a>");
            }
        }
        return html.toString();
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
    public Object getJsonValue(RenderContext ctx)
    {
        ExpData data = getData(ctx);
        return data == null ? null : getURL(data);
    }}
/*
 * Copyright (c) 2008-2026 LabKey Corporation
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
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.DOM.Renderable;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.HtmlWriter;
import org.labkey.experiment.controllers.exp.ExperimentController;

import static org.labkey.api.util.DOM.Attribute.src;
import static org.labkey.api.util.DOM.IMG;
import static org.labkey.api.util.DOM.at;

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
    public void renderGridCellContents(RenderContext ctx, HtmlWriter out)
    {
        ExpData data = getData(ctx, getColumnInfo());

        if (data != null)
        {
            renderData(out, data);
        }
    }

    protected void renderData(HtmlWriter out, ExpData data)
    {
        ActionURL url = getURL(data);
        if (url != null)
        {
            out.write(LinkBuilder.simpleLink(data.getName(), url));
        }
        else
        {
            out.write(data.getName());
        }

        renderThumbnailPopup(out, data, url);
    }

    protected void renderThumbnailPopup(HtmlWriter out, ExpData data, ActionURL url)
    {
        if (data.isInlineImage() && data.isFileOnDisk())
        {
            out.write(HtmlString.NBSP);
            String icon = "<img src=\"" + AppProps.getInstance().getContextPath() + "/_icons/image.png\" />";
            PageFlowUtil.popupHelp(renderThumbnailImg(data, url), data.getFile().getName()).link(HtmlString.unsafe(icon)).width(310).script(url == null ? null : "window.location = '" + url + "'").appendTo(out);
        }
    }

    protected Renderable renderThumbnailImg(ExpData data, ActionURL url)
    {
        Renderable ret;
        if (data.isInlineImage() && data.isFileOnDisk())
        {
            ActionURL thumbnailURL = ExperimentController.ExperimentUrlsImpl.get().getShowFileURL(data, true);
            thumbnailURL.addParameter("maxDimension", 300);
            Renderable img = IMG(at(src, thumbnailURL));

            ret = url != null ? LinkBuilder.simpleLink(img, url) : img;
        }
        else
        {
            ret = HtmlString.EMPTY_STRING;
        }
        return ret;
    }

    static ExpData getData(RenderContext ctx, ColumnInfo col)
    {
        Integer rowIdObject = ctx.get(col.getFieldKey(), Integer.class);
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
        ExpData data = getData(ctx, getColumnInfo());
        return data == null ? null : getURL(data);
    }
}
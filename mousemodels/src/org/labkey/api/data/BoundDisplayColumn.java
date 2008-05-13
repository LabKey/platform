/*
 * Copyright (c) 2004-2007 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.SQLException;
import java.util.Set;


public class BoundDisplayColumn extends DisplayColumn
{
    private DisplayColumn _col;
    private RenderContext _ctx;
    private DataRegion _rgn;

    public BoundDisplayColumn(DisplayColumn col, RenderContext ctx, DataRegion rgn)
    {
        _col = col;
        _ctx = ctx;
        _rgn = rgn;
    }

    public ColumnInfo getColumnInfo()
    {
        return _col.getColumnInfo();
    }

    public String getURL(RenderContext ctx)
    {
        return _col.getURL(ctx);
    }

    public String getURL()
    {
        return _col.getURL();
    }

    public boolean isEditable()
    {
        return _col.isEditable();
    }

    public boolean isFilterable()
    {
        return _col.isFilterable();
    }

    public boolean isQueryColumn()
    {
        return _col.isQueryColumn();
    }

    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        _col.addQueryColumns(columns);
    }

    public boolean isSortable()
    {
        return _col.isSortable();
    }

    public Object getValue(RenderContext ctx)
    {
        return _col.getValue(ctx);
    }

    public Class getValueClass()
    {
        return _col.getValueClass();
    }

    public String getFormatString()
    {
        return _col.getFormatString();
    }

    public void setFormatString(String formatString)
    {
        _col.setFormatString(formatString);
    }

    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        _col.renderDetailsCellContents(ctx, out);
    }

    public void renderFilterOnClick(RenderContext ctx, Writer out) throws IOException
    {
        _col.renderFilterOnClick(ctx, out);
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        _col.renderGridCellContents(ctx, out);
    }

    public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
    {
        _col.renderInputHtml(ctx, out, value);
    }

    public void renderSortHref(RenderContext ctx, Writer out) throws IOException
    {
        _col.renderSortHref(ctx, out);
    }

    public void renderTitle(RenderContext ctx, Writer out) throws IOException
    {
        _col.renderTitle(ctx, out);
    }

    public void setURL(String url)
    {
        _col.setURL(url);
    }

    public void render(RenderContext ctx, Writer out) throws IOException
    {
        _col.render(ctx, out);
    }

    public String getDetailsCell()
    {
        return super.getDetailsData(_ctx);
    }

    public String getGridDataCell()
    {
        return super.getGridDataCell(_ctx);
    }

    public String getInputCell()
    {
        return super.getInputCell(_ctx);
    }

    public String getInputHtml()
    {
        return super.getInputHtml(_ctx);
    }

    public String getOutput()
    {
        return super.getOutput(_ctx);
    }

    protected boolean shouldRender()
    {
        return super.shouldRender(_ctx);
    }

    public String toString()
    {
        StringWriter writer = new StringWriter();
        try
        {
            _col.render(_ctx, writer);
            return writer.toString();
        }
        catch (IOException x)
        {
            return "IOException rendering column " + _col.getName() + ": " + x.getMessage();
        }
    }

    public String getGridCellContents() throws IOException
    {
        StringWriter out = new StringWriter();
        _col.renderGridCellContents(_ctx, out);
        return out.toString();
    }

    public String getDetails() throws IOException
    {
        StringWriter out = new StringWriter();
        _col.renderDetailsCellContents(_ctx, out);
        return out.toString();
    }

    public String getTitle() throws IOException
    {
        StringWriter out = new StringWriter();
        _col.renderTitle(_ctx, out);
        return out.toString();
    }

    public String getError() throws IOException
    {
        String err = PageFlowUtil.getStrutsError(_ctx.getRequest(), _col.getName());
        if (null != err)
        {
            StringWriter out = new StringWriter();
            out.write(err);
            return out.toString();
        }
        else
            return null;
    }


    public String getFormField() throws IOException, SQLException
    {
        StringWriter out = new StringWriter();
        if (_ctx.getMode() == DataRegion.MODE_DETAILS)
        {
            out.write("  <tr>\n    ");
            _col.renderDetailsCaptionCell(_ctx, out);
            _col.renderDetailsData(_ctx, out, 1);
            out.write("  </tr>\n");
        }
        else
            _rgn.renderFormField(_ctx, out, _col);
        return out.toString();
    }
}

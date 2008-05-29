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

package org.labkey.api.data;

import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

public class SimpleDisplayColumn extends DisplayColumn
{
    private StringExpressionFactory.StringExpression _displayHTML = null;
    private StringExpressionFactory.StringExpression _url = null;

    public SimpleDisplayColumn()
    {
        super();
    }

    public SimpleDisplayColumn(String displayHTML)
    {
        super();
        setDisplayHtml(displayHTML);
    }

    public void setDisplayHtml(String displayHTML)
    {
        _displayHTML = StringExpressionFactory.create(displayHTML);
    }

    public String getDisplayHTML()
    {
        return _displayHTML == null ? null : _displayHTML.getSource();
    }

    public String getDisplayHTML(RenderContext ctx)
    {
        return _displayHTML == null ? null : _displayHTML.eval(ctx);
    }

    public void setURL(String url)
    {
        _url = StringExpressionFactory.create(url, true);
    }

    protected void setURL(StringExpressionFactory.StringExpression url)
    {
        _url = url;
    }

    public String getURL()
    {
        return _url == null ? null : _url.getSource();
    }

    public String getURL(RenderContext ctx)
    {
        return _url == null ? null : _url.eval(ctx);
    }

    protected StringExpressionFactory.StringExpression getURLExpression()
    {
        return _url;
    }

    public ColumnInfo getColumnInfo()
    {
        return null;
    }

    public boolean isFilterable()
    {
        return false;
    }

    public boolean isQueryColumn()
    {
        return false;
    }

    public void addQueryColumns(Set<ColumnInfo> set)
    {
    }

    public boolean isSortable()
    {
        return false;
    }

    public Class getValueClass()
    {
        return String.class;
    }

    public Object getValue(RenderContext ctx)
    {
        return getDisplayHTML(ctx);
    }

    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Object value = getValue(ctx);
        if (value != null)
            out.write(value.toString());
    }

    public void renderFilterOnClick(RenderContext ctx, Writer out) throws IOException
    {
        throw new UnsupportedOperationException("Non Bound columns not filterable");
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        String url = getURL(ctx);
        if (null != url)
        {
            out.write("<a href='");
            out.write(PageFlowUtil.filter(url));

            String linkTarget = getLinkTarget();

            if (null != linkTarget)
            {
                out.write("' target='");
                out.write(linkTarget);
            }

            out.write("'>");
        }
        Object value = getDisplayValue(ctx);
        if (value == null)
            out.write("");
        else if (null == _format)
            out.write(getDisplayValue(ctx).toString());
        else
            out.write(_format.format(getDisplayValue(ctx)));

        if (null != url)
            out.write("</a>");
    }

    public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
    {
        throw new UnsupportedOperationException("Non Bound columns not editable");
    }

    public void renderSortHandler(RenderContext ctx, Writer out, Sort.SortDirection sort) throws IOException
    {
        throw new UnsupportedOperationException("Non Bound columns not sortable");
    }

    public void renderTitle(RenderContext ctx, Writer out) throws IOException
    {
        if (null != _caption)
            _caption.render(out, ctx);
        else
            out.write("&nbsp;");
    }

    public boolean isEditable()
    {
        return false;
    }

    public void render(RenderContext ctx, Writer out) throws IOException
    {
        renderTitle(ctx, out);
        if (null != _caption)
            out.write(" ");
        renderDetailsCellContents(ctx, out);
    }
} 

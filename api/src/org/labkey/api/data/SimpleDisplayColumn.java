/*
 * Copyright (c) 2004-2026 Fred Hutchinson Cancer Research Center
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.DOM.Renderable;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.writer.HtmlWriter;

import java.util.HashSet;
import java.util.Set;

/**
 * {@link DisplayColumn} implementation that typically doesn't render the value from a column in the query
 * being executed. Examples include columns that show links with fixed text (details or update links, for example).
 */
public class SimpleDisplayColumn extends DisplayColumn
{
    private StringExpression _displayHTML = null;

    public SimpleDisplayColumn()
    {
        super();
    }

    public SimpleDisplayColumn(String displayHTML)
    {
        setDisplayHtml(displayHTML);
    }

    public void setDisplayHtml(String displayHTML)
    {
        _displayHTML = StringExpressionFactory.create(displayHTML);
    }

    public HtmlString getDisplayHTML(RenderContext ctx)
    {
        return _displayHTML == null ? null : HtmlString.unsafe(_displayHTML.eval(ctx));
    }

    @Override
    public ColumnInfo getColumnInfo()
    {
        return null;
    }

    @Override
    public boolean isFilterable()
    {
        return false;
    }

    @Override
    public boolean isQueryColumn()
    {
        return false;
    }

    @Override
    public void addQueryFieldKeys(Set<FieldKey> keys)
    {
        super.addQueryFieldKeys(keys);
        Set<ColumnInfo> cols = new HashSet<>();
        addQueryColumns(cols);
        for (ColumnInfo c : cols)
            keys.add(c.getFieldKey());
    }

    @Override
    public boolean isSortable()
    {
        return false;
    }

    @Override
    public Class<?> getValueClass()
    {
        return String.class;
    }

    @Override
    public Object getValue(RenderContext ctx)
    {
        return getDisplayHTML(ctx);
    }

    @Override
    public void renderDetailsCellContents(RenderContext ctx, HtmlWriter out)
    {
        Object value = getValue(ctx);
        if (value != null)
            out.write(value);
    }

    @Override
    public String getFilterOnClick(RenderContext ctx)
    {
        throw new UnsupportedOperationException("Non Bound columns not filterable");
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, HtmlWriter out)
    {
        Object value = getDisplayValue(ctx);
        final String text;
        final Renderable renderable;

        if (value instanceof Renderable r)
        {
            renderable = r;
            text = null;
        }
        else
        {
            renderable = null;
            if (value == null)
                text = "";
            else if (null == _format)
                text = value.toString();
            else
                text = _format.format(value);
        }

        String url = renderURL(ctx);
        if (null != url)
        {
            String linkTarget = getLinkTarget();
            LinkBuilder lb = (renderable != null ? new LinkBuilder(renderable) : new LinkBuilder(text))
                .href(url)
                .target(linkTarget)
                .addClass(getLinkCls());

            if (linkTarget != null)
                lb.rel("noopener noreferrer");

            out.write(lb);
        }
        else
        {
            out.write(renderable != null ? renderable : text);
        }
    }

    @Override
    public void renderInputHtml(RenderContext ctx, HtmlWriter out, Object value)
    {
        throw new UnsupportedOperationException("Non Bound columns not editable for " + this);
    }

    @Override
    public @NotNull HtmlString getTitle(RenderContext ctx)
    {
        return null != _caption ? HtmlString.of(_caption) : HtmlString.NBSP;
    }

    @Override
    public boolean isEditable()
    {
        return false;
    }

    @Override
    public void render(RenderContext ctx, HtmlWriter out)
    {
        out.write(getTitle(ctx));
        if (null != _caption)
            out.write(" ");
        renderDetailsCellContents(ctx, out);
    }
} 

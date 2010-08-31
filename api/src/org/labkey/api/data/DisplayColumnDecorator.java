/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

/**
 * User: kevink
 * Date: Nov 20, 2008 4:25:54 PM
 */
public class DisplayColumnDecorator extends DisplayColumn
{
    private final DisplayColumn _column;

    public DisplayColumnDecorator(DisplayColumn column)
    {
        _column = column;
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        _column.renderGridCellContents(ctx, out);
    }

    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        _column.renderDetailsCellContents(ctx, out);
    }

    public void renderTitle(RenderContext ctx, Writer out) throws IOException
    {
        _column.renderTitle(ctx, out);
    }

    public boolean isSortable()
    {
        return _column.isSortable();
    }

    public boolean isFilterable()
    {
        return _column.isFilterable();
    }

    public boolean isEditable()
    {
        return _column.isEditable();
    }

    public void renderSortHandler(RenderContext ctx, Writer out, Sort.SortDirection sort) throws IOException
    {
        _column.renderSortHandler(ctx, out, sort);
    }

    public void renderFilterOnClick(RenderContext ctx, Writer out) throws IOException
    {
        _column.renderFilterOnClick(ctx, out);
    }

    public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
    {
        _column.renderInputHtml(ctx, out, value);
    }

    public void setURL(String url)
    {
        _column.setURL(url);
    }

    public String getURL()
    {
        return _column.getURL();
    }

    public boolean isQueryColumn()
    {
        return _column.isQueryColumn();
    }

    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        _column.addQueryColumns(columns);
    }

    public ColumnInfo getColumnInfo()
    {
        return _column.getColumnInfo();
    }

    public Object getValue(RenderContext ctx)
    {
        return _column.getValue(ctx);
    }

    public Class getValueClass()
    {
        return _column.getValueClass();
    }

    public void render(RenderContext ctx, Writer out) throws IOException
    {
        _column.render(ctx, out);
    }
}

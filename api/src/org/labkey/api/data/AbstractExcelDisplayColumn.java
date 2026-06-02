/*
 * Copyright (c) 2025-2026 LabKey Corporation
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

import org.labkey.api.util.HtmlString;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.HtmlWriter;

public abstract class AbstractExcelDisplayColumn extends DisplayColumn
{
    private final Class<?> _valueClass;

    public AbstractExcelDisplayColumn(String name, String caption, Class<?> valueClass)
    {
        setName(name);
        setCaption(caption);
        _valueClass = valueClass;
    }

    @Override
    public Class<?> getValueClass()
    {
        return _valueClass;
    }

    //NOTE: Methods below are unimplemented, just abstract in base class

    @Override
    public void renderGridCellContents(RenderContext ctx, HtmlWriter out)
    {
        throw new UnsupportedOperationException("This is for excel only.");
    }

    @Override
    public void renderDetailsCellContents(RenderContext ctx, HtmlWriter out)
    {
        throw new UnsupportedOperationException("This is for excel only.");
    }

    @Override
    public void renderInputHtml(RenderContext ctx, HtmlWriter out, Object value)
    {
        throw new UnsupportedOperationException("This is for excel only.");
    }

    @Override
    public HtmlString getTitle(RenderContext ctx)
    {
        throw new UnsupportedOperationException("This is for excel only.");
    }

    @Override
    public boolean isSortable()
    {
        return false;
    }

    @Override
    public boolean isFilterable()
    {
        return false;
    }

    @Override
    public boolean isEditable()
    {
        return false;
    }

    @Override
    public String getFilterOnClick(RenderContext ctx)
    {
        throw new UnsupportedOperationException("This is for excel only.");
    }

    @Override
    public void setURL(ActionURL url)
    {
        throw new UnsupportedOperationException("This is for excel only.");
    }

    @Override
    public void setURL(String url)
    {
        throw new UnsupportedOperationException("This is for excel only.");
    }

    @Override
    public String getURL()
    {
        return null;
    }

    @Override
    public String renderURL(RenderContext ctx)
    {
        return null;
    }

    @Override
    public boolean isQueryColumn()
    {
        return false;
    }

    @Override
    public ColumnInfo getColumnInfo()
    {
        return null;
    }

    @Override
    public void render(RenderContext ctx, HtmlWriter out)
    {
        throw new UnsupportedOperationException("This is for excel only.");
    }
}

/*
 * Copyright (c) 2005-2008 LabKey Corporation
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

import org.apache.commons.beanutils.ConvertUtils;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

/**
 * User: migra
 * Date: Dec 5, 2005
 * Time: 4:14:54 PM
 */
public class SimpleInputColumn<T> extends DisplayColumn
{
    protected String name;
    protected T value;
    protected Class<T> valueClass;

    protected SimpleInputColumn(String name, T value, Class<T> tClass)
    {
        this.name = name;
        this.value = value;
        this.valueClass = tClass;
    }

    public static <C> SimpleInputColumn<C> create(String name, C value, Class<C> tClass)
    {
        return new SimpleInputColumn<C>(name, value, tClass);
    }

    public static SimpleInputColumn<String> create(String name, String value)
    {
        return new SimpleInputColumn<String>(name, value, String.class);
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        out.write(value == null ? "" : PageFlowUtil.filter(ConvertUtils.convert(value)));
    }

    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        out.write(value == null ? "" : PageFlowUtil.filter(ConvertUtils.convert(value)));
    }

    public void renderTitle(RenderContext ctx, Writer out) throws IOException
    {
        if (null != _caption)
        {
            out.write(PageFlowUtil.filter(_caption.eval(ctx)));
        }
    }

    public boolean isSortable()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isFilterable()
    {
        return false;
    }

    public boolean isEditable()
    {
        return true;
    }

    public void renderSortHandler(RenderContext ctx, Writer out, Sort.SortDirection sort) throws IOException
    {
        throw new UnsupportedOperationException("Can't sort");
    }

    public void renderFilterOnClick(RenderContext ctx, Writer out) throws IOException
    {
        throw new UnsupportedOperationException("Can't Filter");
    }

    public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
    {
        out.write("<input name=\"");
        out.write(name);
        out.write("\" size=\"40\" value=\"");
        if (null != value)
            out.write(PageFlowUtil.filter(ConvertUtils.convert(value)));
        out.write("\">");
    }

    public void setURL(String url)
    {

    }

    public String getURL()
    {
        return null;
    }

    public String getURL(RenderContext ctx)
    {
        return null;
    }

    public boolean isQueryColumn()
    {
        return false;
    }

    public void addQueryColumns(Set<ColumnInfo> columns)
    {
    }

    public ColumnInfo getColumnInfo()
    {
        return null;
    }

    public T getValue(RenderContext ctx)
    {
        String strVal = ctx.getRequest().getParameter(name);
        if (null != strVal)
        {
            T val;
            try
            {
                val = (T) ConvertUtils.convert(strVal, valueClass);
            }
            catch(Exception x)
            {
                //Silent null return. Only should happen in the case
                //of bad input, which we handle separately
                return null;
            }

            return val;
        }
        else
            return value;

    }

    public String getValueString(RenderContext ctx)
    {
        if (null != value)
            return ConvertUtils.convert(value);

        return ctx.getRequest().getParameter(name);
    }

    public boolean isRequestValid(RenderContext ctx)
    {
        String val = ctx.getRequest().getParameter(name);
        if (null != val)
        {
            try
            {
                ConvertUtils.convert(val, valueClass);
            }
            catch (Exception x)
            {
                return false;
            }
        }
        return true;
    }


    public Class<T> getValueClass()
    {
        return valueClass;
    }

    public void render(RenderContext ctx, Writer out) throws IOException
    {
        renderTitle(ctx, out);
        out.write(" ");
        renderInputHtml(ctx, out, getValueString(ctx));
    }
}

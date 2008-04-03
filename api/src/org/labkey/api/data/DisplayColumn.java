/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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
import org.apache.commons.lang.time.FastDateFormat;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.Format;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public abstract class DisplayColumn extends RenderColumn
{
    protected String _textAlign = null;
    protected boolean _nowrap = false;
    protected String _width = "60";
    protected String _linkTarget = null;
    protected String _excelFormatString = null;
    protected Format _format = null;
    protected Format _tsvFormat = null;
    protected String _gridHeaderClass = "header";
    protected String _gridCellClass = "normal";
    protected String _detailsCaptionClass = "ms-searchform";
    protected String _detailsDataClass = "normal";
    protected String _inputCellClass = "normal";
    private String _inputPrefix = "";
    private String _description = null;
    protected boolean _htmlFiltered = true;
    private String _backgroundColor;

    public abstract void renderGridCellContents(RenderContext ctx, Writer out) throws IOException;

    public abstract void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException;

    public abstract void renderTitle(RenderContext ctx, Writer out) throws IOException;

    public abstract boolean isSortable();

    public abstract boolean isFilterable();

    public abstract boolean isEditable();

    public abstract void renderSortHref(RenderContext ctx, Writer out) throws IOException;

    public abstract void renderFilterOnClick(RenderContext ctx, Writer out) throws IOException;

    public abstract void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException;

    public abstract void setURL(String url);

    public abstract String getURL();

    public abstract String getURL(RenderContext ctx);

    public abstract boolean isQueryColumn();

    public abstract void addQueryColumns(Set<ColumnInfo> columns);

    public abstract ColumnInfo getColumnInfo();

    public abstract Object getValue(RenderContext ctx);

    public abstract Class getValueClass();

    public String getName()
    {
        if (null != getColumnInfo())
            return getColumnInfo().getPropertyName();
        else
            return super.getName();
    }

    protected String getInputPrefix()
    {
        return _inputPrefix;
    }

    protected void setInputPrefix(String inputPrefix)
    {
        _inputPrefix = inputPrefix;
    }

    /** If width is null, no width will be requested in the HTML table */
    public void setWidth(String width)
    {
        _width = width;
    }

    public String getWidth()
    {
        return _width;
    }

    public void setNoWrap(boolean nowrap)
    {
        _nowrap = nowrap;
    }

    public void setFormatString(String formatString)
    {
        super.setFormatString(formatString);
        _format = createFormat(formatString);
    }

    public void setTsvFormatString(String formatString)
    {
        super.setTsvFormatString(formatString);
        _tsvFormat = createFormat(formatString);
    }

    private Format createFormat(String formatString)
    {
        if (null != formatString)
        {
            Class valueClass = getDisplayValueClass();

            if (Boolean.class.isAssignableFrom(valueClass) || boolean.class.isAssignableFrom(valueClass))
                return new BooleanFormat(formatString);
            if (valueClass.isPrimitive() || Number.class.isAssignableFrom(valueClass))
                return new DecimalFormat(formatString);
            else if (Date.class.isAssignableFrom(valueClass))
                return FastDateFormat.getInstance(formatString);
        }

        return null;
    }

    public Format getFormat()
    {
        return _format;
    }

    public Format getTsvFormat()
    {
        return _tsvFormat;
    }

    public String getFormattedValue(RenderContext ctx)
    {
        Format format = getFormat();
        return formatValue(ctx, format);
    }

    private String formatValue(RenderContext ctx, Format format)
    {
        Object value = getDisplayValue(ctx);

        if (null == value)
            return "";

        if (null != format)
            return format.format(value);
        else if (value instanceof String)
            return (String)value;
        return ConvertUtils.convert(value);
    }

    public String getTsvFormattedValue(RenderContext ctx)
    {
        Format format = getTsvFormat();
        if (format == null)
        {
            format = getFormat();
        }
        return formatValue(ctx, format);
    }

    public String getJsonFormattedValue(RenderContext ctx)
    {
        Object value = getDisplayValue(ctx);
        if (value == null)
            return "null";
        else if (value instanceof Date)
            return "new Date(" + String.valueOf(((Date) value).getTime()) + ")";
        else if (value instanceof String)
            return PageFlowUtil.jsString((String) value);
        else
            return ConvertUtils.convert(value);
    }

    /**
     * Returns the JSON type name for the column's display value,
     * which might be different than its value (e.g., lookup column)
     * @return JSON type name
     */
    public String getDisplayJsonTypeName()
    {
        return getJsonTypeName(getDisplayValueClass());
    }

    /**
     * Returns the JSON type name for the column's value
     * @return JSON type name
     */
    public String getJsonTypeName()
    {
        return getJsonTypeName(getValueClass());
    }

    protected String getJsonTypeName(Class valueClass)
    {
        if(String.class.isAssignableFrom(valueClass))
            return "string";
        else if(Boolean.class.isAssignableFrom(valueClass) || boolean.class.isAssignableFrom(valueClass))
            return "boolean";
        else if(Integer.class.isAssignableFrom(valueClass) || int.class.isAssignableFrom(valueClass))
            return "int";
        else if(Double.class.isAssignableFrom(valueClass) || double.class.isAssignableFrom(valueClass)
                || Float.class.isAssignableFrom(valueClass) || float.class.isAssignableFrom(valueClass))
            return "float";
        else if(Date.class.isAssignableFrom(valueClass))
            return "date";
        else
            return "string";

    }

    public Object getDisplayValue(RenderContext ctx)
    {
        return getValue(ctx);
    }

    public Class getDisplayValueClass()
    {
        return getValueClass();
    }

    public void setTextAlign(String textAlign)
    {
        _textAlign = textAlign;
    }

    public String getTextAlign()
    {
        return _textAlign;
    }

    public void renderColTag(Writer out) throws IOException
    {
        out.write("<col ");
        if (_width != null)
        {
            out.write("width=\"");
            out.write(_width);
            out.write("\"");
        }
        out.write(" style=\"text-align:");
        out.write(_textAlign == null ? "left" : _textAlign);
        out.write("\"/>");
    }

    public String getGridHeaderCell(RenderContext ctx)
    {
        StringWriter writer = new StringWriter();
        try
        {
            renderGridHeaderCell(ctx, writer);
        }
        catch (Exception e)
        {
            writer.write(e.getMessage());
        }
        return writer.toString();
    }

    public String getDefaultHeaderStyle()
    {
        return (_nowrap ? "white-space:nowrap;" : "") + "text-align:" + (getTextAlign() != null ? getTextAlign() : "left");
    }

    public void renderGridHeaderCell(RenderContext ctx, Writer out) throws IOException, SQLException
    {
        renderGridHeaderCell(ctx, out, "");
    }

    public void renderGridHeaderCell(RenderContext ctx, Writer out, String styleAttributes) throws IOException, SQLException
    {
        out.write("\n<th class='");
        out.write(_gridHeaderClass);
        out.write("' ");
        if (styleAttributes != null)
        {
            styleAttributes = styleAttributes + "; " + getDefaultHeaderStyle();
        }
        else
        {
            styleAttributes = getDefaultHeaderStyle();
        }
        out.write(" style='");
        out.write(styleAttributes);
        out.write("'");

        if (_backgroundColor != null)
        {
            out.write(" bgColor=\"" + _backgroundColor + "\"");
        }
        if (null != getDescription())
        {
            out.write(" title=\"");
            out.write(PageFlowUtil.filter(getDescription()));
            out.write("\"");
        }
        out.write(">");


        if (isSortable() && ctx.getCurrentRegion().isSortable())
        {
            out.write("<a ");
            if (null == getDescription())
                out.write("title=\"Sort\"");
            out.write("href='");
            renderSortHref(ctx, out);
            out.write("'>");
            renderTitle(ctx, out);
            out.write("</a>");
        }
        else
            renderTitle(ctx, out);

        if (isFilterable() && ctx.getCurrentRegion().getShowFilters())
        {
            renderFilterGif(ctx, out);
        }
        out.write("</th>");
    }

    private void renderFilterGif(RenderContext ctx, Writer out) throws IOException
    {
        DataRegion rgn = ctx.getCurrentRegion();
        assert null != rgn;
        Set<String> filteredColSet = (Set<String>) ctx.get(rgn.getName() + ".filteredCols");
        if (null == filteredColSet)
        {
            TableInfo tinfo = rgn.getTable();
            assert null != tinfo;
            ActionURL url = ctx.getSortFilterURLHelper();
            SimpleFilter filter = new SimpleFilter(url, rgn.getName());

            filteredColSet = new HashSet<String>();
            for (String s : filter.getWhereParamNames())
            {
                filteredColSet.add(s.toLowerCase());
            }
            ctx.put(rgn.getName() + ".filteredCols", filteredColSet);
        }

        String img = ctx.getRequest().getContextPath() + "/_images/filter.gif";
        if (null != this.getColumnInfo() &&
                (filteredColSet.contains(this.getColumnInfo().getName().toLowerCase())) ||
                    (this.getColumnInfo().getDisplayField() != null &&
                    filteredColSet.contains(this.getColumnInfo().getDisplayField().getName().toLowerCase())))
            img = ctx.getRequest().getContextPath() + "/_images/filter_on.gif";

        out.write("<img src=\"");
        out.write(img);
        out.write("\" align=\"bottom\" title=\"Filter\"  style=\"cursor:hand;\"");
        if (null != this.getColumnInfo())
            out.write(" id=\""+rgn.getName()+":"+this.getColumnInfo().getName()+":filter\"");
        out.write(" onclick=\"");
        renderFilterOnClick(ctx, out);
        out.write("\" >");

    }

    public String getGridDataCell(RenderContext ctx)
    {
        StringWriter writer = new StringWriter();
        try
        {
            renderGridDataCell(ctx, writer);
        }
        catch (Exception e)
        {
            writer.write(e.getMessage());
        }
        return writer.toString();
    }

    public void renderGridDataCell(RenderContext ctx, Writer out) throws IOException, SQLException
    {
        renderGridDataCell(ctx, out, null);
    }

    public void renderGridDataCell(RenderContext ctx, Writer out, String style) throws IOException, SQLException
    {
        if ("right".equals(_textAlign))
            out.write("<td align=right");
        else
            out.write("<td");
        if (style != null)
        {
            out.write(" style='");
            out.write(style);
            out.write("'");
        }
        if (_backgroundColor != null)
        {
            out.write(" bgColor=\"" + _backgroundColor + "\"");
        }
        if (_nowrap)
            out.write(" nowrap>");
        else
            out.write(">");
        renderGridCellContents(ctx, out);
        out.write("</td>");
    }


    public String getCaption()
    {
        return getCaption(null);
    }

    public String getCaption(RenderContext ctx)
    {
        StringWriter writer = new StringWriter();
        try
        {
            renderTitle(ctx, writer);
        }
        catch (Exception e)
        {
            writer.write(e.getMessage());
        }
        return writer.toString();
    }


    public String getDetailsCaptionCell(RenderContext ctx)
    {
        StringWriter writer = new StringWriter();
        try
        {
            renderDetailsCaptionCell(ctx, writer);
        }
        catch (Exception e)
        {
            writer.write(e.getMessage());
        }
        return writer.toString();
    }


    public void renderDetailsCaptionCell(RenderContext ctx, Writer out) throws IOException
    {
        if (null == _caption)
            return;

        out.write("<td class='ms-searchform'>");
        renderTitle(ctx, out);
        out.write("</td>");
    }

    public String getDetailsData(RenderContext ctx)
    {
        StringWriter writer = new StringWriter();
        try
        {
            renderDetailsData(ctx, writer, 1);
        }
        catch (Exception e)
        {
            writer.write(e.getMessage());
        }
        return writer.toString();
    }

    public void renderDetailsData(RenderContext ctx, Writer out, int span) throws IOException, SQLException
    {
        if (null == _caption)
            out.write("<td colspan=" + (span + 1) + " class='ms-vb'>");
        else
            out.write("<td colspan=" + span + " class='ms-vb'>");
        renderDetailsCellContents(ctx, out);
        out.write("</td>");
    }

    public String getInputCell(RenderContext ctx)
    {
        StringWriter writer = new StringWriter();
        try
        {
            renderInputCell(ctx, writer, 1);
        }
        catch (Exception e)
        {
            writer.write(e.getMessage());
        }
        return writer.toString();
    }

    protected Object getInputValue(RenderContext ctx)
    {
        ColumnInfo col = getColumnInfo();
        Object val = null;
        TableViewForm viewForm = ctx.getForm();

        if (col != null)
        {
            String formFieldName = ctx.getForm().getFormFieldName(col);
            if (null != viewForm && viewForm.getStrings().containsKey(formFieldName))
                val = viewForm.get(formFieldName);
            else if (ctx.getRow() != null)
                val = col.getValue(ctx);
        }

        return val;
    }

    public String getFormFieldName(RenderContext ctx)
    {
        return ctx.getForm().getFormFieldName(getColumnInfo());
    }

    public void renderInputCell(RenderContext ctx, Writer out, int span) throws IOException
    {
        out.write("<td colspan=" + span + " class='ms-vb'>");
        renderInputHtml(ctx, out, getInputValue(ctx));
        out.write("</td>");
    }

    public String getSortHref(RenderContext ctx)
    {
        StringWriter writer = new StringWriter();
        try
        {
            renderSortHref(ctx, writer);
        }
        catch (Exception e)
        {
            writer.write(e.getMessage());
        }
        return writer.toString();
    }

    public String getFilterOnClick(RenderContext ctx)
    {
        StringWriter writer = new StringWriter();
        try
        {
            renderFilterOnClick(ctx, writer);
        }
        catch (Exception e)
        {
            writer.write(e.getMessage());
        }
        return writer.toString();
    }

    public String getInputHtml(RenderContext ctx)
    {
        Object value = getInputValue(ctx);
        StringWriter writer = new StringWriter();
        try
        {
            renderInputHtml(ctx, writer, value);
        }
        catch (Exception e)
        {
            writer.write(e.getMessage());
        }
        return writer.toString();
    }

    public boolean isHtmlFiltered()
    {
        return _htmlFiltered;
    }

    public void setHtmlFiltered(boolean htmlFiltered)
    {
        _htmlFiltered = htmlFiltered;
    }

    public void setLinkTarget(String linkTarget)
    {
        _linkTarget = linkTarget;
    }

    public String getLinkTarget()
    {
        return _linkTarget;
    }

    public String getExcelFormatString()
    {
        return _excelFormatString;
    }

    public void setExcelFormatString(String excelFormatString)
    {
        _excelFormatString = excelFormatString;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String _description)
    {
        this._description = _description;
    }

    public void setBackgroundColor(String backgroundColor)
    {
        _backgroundColor = backgroundColor;
    }

    public String getBackgroundColor()
    {
        return _backgroundColor;
    }
}

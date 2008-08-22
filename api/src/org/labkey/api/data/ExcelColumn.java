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

import jxl.Cell;
import jxl.CellType;
import jxl.DateCell;
import jxl.NumberCell;
import jxl.write.*;
import jxl.write.Number;
import org.apache.commons.lang.time.FastDateFormat;
import org.apache.log4j.Logger;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.DateUtil;
import org.labkey.common.util.Pair;

import java.io.IOException;
import java.io.Writer;
import java.lang.Boolean;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.Format;
import java.util.Date;
import java.util.Map;

public class ExcelColumn extends RenderColumn
{
    private static Logger _log = Logger.getLogger(ExcelColumn.class);

    private static final int TYPE_UNKNOWN = 0;
    private static final int TYPE_INT = 1;
    private static final int TYPE_DOUBLE = 2;
    private static final int TYPE_STRING = 3;
    private static final int TYPE_DATE = 4;
    private static final int TYPE_BOOLEAN = 5;

    // CONSIDER: Add support for left/right/center alignment (from DisplayColumn)
    private int _simpleType = TYPE_UNKNOWN;
    private WritableCellFormat _format = null;
    private int _width = 10;
    private boolean _autoSize = false;
    private int _autoSizeWidth = 0;
    private String _name = null;
    private String _caption = null;

    public static class ExcelFormatDescriptor extends Pair<Class, String>
    {
        private ExcelFormatDescriptor(Class aClass, String format)
        {
            super(aClass, format);
        }
    }

    private DisplayColumn _dc;
    private final Map<ExcelFormatDescriptor, WritableCellFormat> _formatters;

    ExcelColumn(DisplayColumn dc, Map<ExcelFormatDescriptor, WritableCellFormat> formatters)
    {
        super();
        _dc = dc;
        _formatters = formatters;
        setSimpleType(dc.getDisplayValueClass());
        if (dc.getExcelFormatString() != null)
        {
            setFormatString(dc.getExcelFormatString());
        }
        else
        {
            setFormatString(dc.getFormatString());
        }
        setName(dc.getName());
        try
        {
            setCaption(dc.getCaptionExpr());
            setVisibleExpr(dc.getVisibleExpr());
        } catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }


    public void setName(String name)
    {
        _name = name;
    }


    public String getCaption()
    {
        return _caption;
    }


    public void setCaption(String caption)
    {
        _caption = caption;
    }


    private void setSimpleType(Class valueClass)
    {
        if (Integer.class.isAssignableFrom(valueClass) || Integer.TYPE.isAssignableFrom(valueClass) ||
                Long.class.isAssignableFrom(valueClass) || Long.TYPE.isAssignableFrom(valueClass))
            _simpleType = TYPE_INT;
        else if (Float.class.isAssignableFrom(valueClass) || Float.TYPE.isAssignableFrom(valueClass) ||
                Double.class.isAssignableFrom(valueClass) || Double.TYPE.isAssignableFrom(valueClass))
            _simpleType = TYPE_DOUBLE;
        else if (String.class.isAssignableFrom(valueClass))
            _simpleType = TYPE_STRING;
        else if (Date.class.isAssignableFrom(valueClass))
            _simpleType = TYPE_DATE;
        else if (Boolean.class.isAssignableFrom(valueClass) || Boolean.TYPE.isAssignableFrom(valueClass))
            _simpleType = TYPE_BOOLEAN;
        else
        {
            _log.error("init: Unknown Class " + valueClass + " " + getName());
            _simpleType = TYPE_UNKNOWN;
        }
    }


    public String getFormatString()
    {
        String formatString = super.getFormatString();

        if (null != formatString)
            return formatString;

        switch (_simpleType)
        {
            case(TYPE_DATE):
                return DateUtil.getStandardDateFormatString();
            case(TYPE_INT):
                return "0";
            case(TYPE_DOUBLE):
                return "0.0000";
        }

        return null;
    }


    public void setFormatString(String formatString)
    {
        super.setFormatString(formatString);

        switch (_simpleType)
        {
            case(TYPE_INT):
            case(TYPE_DOUBLE):
            {
                ExcelFormatDescriptor formatDescriptor = new ExcelFormatDescriptor(NumberFormat.class, getFormatString());
                _format = _formatters.get(formatDescriptor);
                if (_format == null)
                {
                    _format = new WritableCellFormat(new NumberFormat(getFormatString()));
                    _formatters.put(formatDescriptor, _format);
                }
                break;
            }
            case(TYPE_DATE):
            {
                ExcelFormatDescriptor formatDescriptor = new ExcelFormatDescriptor(DateFormat.class, getFormatString());
                _format = _formatters.get(formatDescriptor);
                if (_format == null)
                {
                    _format = new WritableCellFormat(new DateFormat(getFormatString()));
                    _formatters.put(formatDescriptor, _format);
                }
                break;
            }
        }
    }


    public void setAutoSize(boolean autoSize)
    {
        _autoSize = autoSize;
    }


    public boolean getAutoSize()
    {
        return _autoSize;
    }


    public String getName()
    {
        return _name;
    }


    protected void writeCell(WritableSheet sheet, int column, int row, RenderContext ctx) throws SQLException, WriteException
    {
        WritableCell cell = null;
        Object o = _dc.getDisplayValue(ctx);

        // For null values, leave the cell blank
        if (null == o)
            return;

        switch (_simpleType)
        {
            case(TYPE_DATE):
                // Careful here... need to make sure we adjust dates for GMT.  This constructor automatically does the conversion, but there seem to be
                // bugs in other jxl 2.5.7 constructors: DateTime(c, r, d) forces the date to time-only, DateTime(c, r, d, gmt) doesn't adjust for gmt
                cell = new DateTime(column, row, (Date) o, _format);
                break;
            case(TYPE_INT):
            case(TYPE_DOUBLE):
                cell = new Number(column, row, ((java.lang.Number) o).doubleValue(), _format);
                break;
            case(TYPE_STRING):
            default:
                URL url = createURL(_dc.getURL(ctx));
                if (url != null)
                    sheet.addHyperlink(new WritableHyperlink(column, row, column, row, url, o.toString()));
                else
                    cell = new Label(column, row, o.toString());
                break;
        }

        if (cell != null)
            sheet.addCell(cell);

    }

    private URL createURL(String urlString)
    {
        if (urlString == null)
            return null;

        URL url = null;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException ex) { }

        if (url == null)
        {
            try
            {
                url = new URL(AppProps.getInstance().getBaseServerUrl() + urlString);
            }
            catch (MalformedURLException e) { }
        }

        return url;
    }


    protected void renderCaption(WritableSheet sheet, int row, int column, WritableCellFormat cellFormat) throws WriteException
    {
        sheet.addCell(new Label(column, row, getCaption(), cellFormat));
    }


    // Note: width of the column will be adjusted once per call to ExcelWriter.render(), which potentially means
    // multiple times per sheet.  This shouldn't be a problem, though.
    protected void adjustWidth(WritableSheet sheet, int column, int startRow, int endRow)
    {
        if (_autoSize)
        {
            calculateAutoSize(sheet, column, startRow, endRow);
            sheet.setColumnView(column, _autoSizeWidth + 1);
        }
        else
        {
            sheet.setColumnView(column, _width);
        }
    }

    //
    // Calculates the "autosize" column width, the width that approximates the width of the contents of cells in
    // this column.  Several caveats here:
    // 
    // 1. This assumes all data is in Arial 10-point normal font
    // 2. It only counts the number of characters; it doesn't know the exact font display width on the client PC.
    // 3. It's not very efficient; for each cell, it reads the contents, converts it to the appropriate
    //    Java object, and then applies the appropriate Format to determine the displayed width.
    // 4. Be extra careful with long String columns; there's no absolute maximum, so you could end up with
    //    very wide columns.
    //
    // The results are actually fairly good and performance seems reasonable.  But setting display widths
    // in the schema XML file may be preferable.
    //
    private void calculateAutoSize(WritableSheet sheet, int column, int startRow, int endRow)
    {
        Format format = null;

        // In some cases (e.g., exporting multiple MS2 runs), this method is called multiple times for a given sheet.
        // Maintaining _autoSizeWidth as a member variable between calls ensures that the width
        if (0 == _autoSizeWidth)
            _autoSizeWidth = _caption != null ? _caption.length() : 10;  // Start with caption width as minimum

        switch (_simpleType)
        {
            case(TYPE_DATE):
                format = FastDateFormat.getInstance(getFormatString());
                break;
            case(TYPE_INT):
            case(TYPE_DOUBLE):
                format = new DecimalFormat(getFormatString());
                break;
        }

        // Assumes column has same cell type from startRow to endRow, and that cell type matches the Excel column type (which it should, since we just wrote it)
        for (int row = startRow; row <= endRow; row++)
        {
            Cell cell = sheet.getCell(column, row);

            String formatted;

            if (CellType.DATE == cell.getType() && null != format)
                formatted = format.format(((DateCell) cell).getDate());
            else if (CellType.NUMBER == cell.getType() && null != format)
                formatted = format.format(((NumberCell) cell).getValue());
            else
                formatted = cell.getContents();

            if (formatted.length() > _autoSizeWidth)
                _autoSizeWidth = formatted.length();
        }
    }

    // CONSIDER: Change RenderColumn to NOT extend DisplayElement
    public void render(RenderContext ctx, Writer out) throws IOException
    {
        throw new UnsupportedOperationException();
    }
}

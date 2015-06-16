/*
 * Copyright (c) 2004-2015 Fred Hutchinson Cancer Research Center
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

import jxl.format.Colour;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.Pair;

import java.io.IOException;
import java.io.Writer;
import java.text.DecimalFormat;
import java.text.Format;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Representation of a column to be rendered into an Excel file. Wraps a
 * {@link org.labkey.api.data.DisplayColumn}
 */

public class ExcelColumn extends RenderColumn
{
    private static Logger _log = Logger.getLogger(ExcelColumn.class);

    private static final int TYPE_UNKNOWN = 0;
    private static final int TYPE_INT = 1;
    private static final int TYPE_DOUBLE = 2;
    private static final int TYPE_STRING = 3;
    private static final int TYPE_MULTILINE_STRING = 4;
    private static final int TYPE_DATE = 5;
    private static final int TYPE_BOOLEAN = 6;

    // CONSIDER: Add support for left/right/center alignment (from DisplayColumn)
    private int _simpleType = TYPE_UNKNOWN;
    private CellStyle _style = null;
    private boolean _autoSize = false;
    private int _autoSizeWidth = 0;
    private String _name = null;
    private String _caption = null;
    private final Map<ConditionalFormat, CellStyle> _formats = new HashMap<>();
    private final Workbook _workbook;

    public static class ExcelFormatDescriptor extends Pair<Class, String>
    {
        private ExcelFormatDescriptor(Class aClass, String format)
        {
            super(aClass, format);
        }
    }

    private DisplayColumn _dc;
    private final Map<ExcelFormatDescriptor, CellStyle> _formatters;

    ExcelColumn(DisplayColumn dc, Map<ExcelFormatDescriptor, CellStyle> formatters, Workbook workbook)
    {
        super();
        _dc = dc;
        _dc.setHtmlFiltered(false);
        _formatters = formatters;
        _workbook = workbook;
        setSimpleType(dc);
        if (dc.getExcelFormatString() != null)
        {
            setFormatString(dc.getExcelFormatString());
        }
        else
        {
            setFormatString(dc.getFormatString());
        }
        setName(dc.getName());
        setCaption(dc.getCaptionExpr());
    }

    public DisplayColumn getDisplayColumn()
    {
        return _dc;
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


    private void setSimpleType(DisplayColumn dc)
    {
        Class valueClass = dc.getDisplayValueClass();
        if (Integer.class.isAssignableFrom(valueClass) || Integer.TYPE.isAssignableFrom(valueClass) ||
                Long.class.isAssignableFrom(valueClass) || Long.TYPE.isAssignableFrom(valueClass))
            _simpleType = TYPE_INT;
        else if (Float.class.isAssignableFrom(valueClass) || Float.TYPE.isAssignableFrom(valueClass) ||
                Double.class.isAssignableFrom(valueClass) || Double.TYPE.isAssignableFrom(valueClass))
            _simpleType = TYPE_DOUBLE;
        else if (String.class.isAssignableFrom(valueClass))
        {
            _simpleType = TYPE_STRING;
            if (_dc.getColumnInfo() != null && _dc.getColumnInfo().getInputRows() > 1)
            {
                _simpleType = TYPE_MULTILINE_STRING;
            }
        }
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
                ExcelFormatDescriptor formatDescriptor = new ExcelFormatDescriptor(Number.class, getFormatString());
                _style = _formatters.get(formatDescriptor);
                if (_style == null)
                {
                    _style = _workbook.createCellStyle();
                    String excelFormatString = getFormatString();
                    // Excel has a different idea of how to represent scientific notation, so be sure that we
                    // transform the Java format if needed.
                    // https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=17735
                    excelFormatString = excelFormatString.replaceAll("[eE][^\\+]", "E+0");
                    short formatIndex = _workbook.createDataFormat().getFormat(excelFormatString);
                    _style.setDataFormat(formatIndex);
                    _formatters.put(formatDescriptor, _style);
                }
                break;
            }
            case(TYPE_DATE):
            {
                ExcelFormatDescriptor formatDescriptor = new ExcelFormatDescriptor(Date.class, getFormatString());
                _style = _formatters.get(formatDescriptor);
                if (_style == null)
                {
                    _style = _workbook.createCellStyle();
                    short formatIndex = _workbook.createDataFormat().getFormat(getFormatString());
                    _style.setDataFormat(formatIndex);
                    _formatters.put(formatDescriptor, _style);
                }
                break;
            }
            case(TYPE_MULTILINE_STRING):
            {
                ExcelFormatDescriptor formatDescriptor = new ExcelFormatDescriptor(String.class, getFormatString());
                _style = _formatters.get(formatDescriptor);
                if (_style == null)
                {
                    _style = _workbook.createCellStyle();
                    _style.setWrapText(true);
                    _formatters.put(formatDescriptor, _style);
                }
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


    protected void writeCell(Sheet sheet, int column, int row, RenderContext ctx)
    {
        Object o = _dc.getExcelCompatibleValue(ctx);
        ColumnInfo columnInfo = _dc.getColumnInfo();
        Row rowObject = getRow(sheet, row);

        if (null == o)
        {
            // For null values, don't create the cell unless there's a conditional format that applies
            CellStyle cellFormat = getExcelFormat(o, columnInfo);
            if (cellFormat != null)
            {
                // Set the format but there's no value to set
                Cell cell = rowObject.getCell(column, Row.CREATE_NULL_AS_BLANK);
                cell.setCellStyle(cellFormat);
            }
            return;
        }

        Cell cell = rowObject.getCell(column, Row.CREATE_NULL_AS_BLANK); 

        try
        {
            switch (_simpleType)
            {
                case(TYPE_DATE):
                    // Issue 19329: Work around JTDS bug 679 - http://sourceforge.net/p/jtds/bugs/679/
                    // DATE columns are returned as VARCHAR on SQLServer
                    if (o instanceof String)
                    {
                        o = new Date(DateUtil.parseDateTime(ContainerManager.getRoot(), (String)o));
                    }

                    // Careful here... need to make sure we adjust dates for GMT.  This constructor automatically does the conversion, but there seem to be
                    // bugs in other jxl 2.5.7 constructors: DateTime(c, r, d) forces the date to time-only, DateTime(c, r, d, gmt) doesn't adjust for gmt
                    cell.setCellValue((Date) o);
                    cell.setCellStyle(_style);
                    break;
                case(TYPE_INT):
                case(TYPE_DOUBLE):
                    if (o instanceof Number)
                    {
                        cell.setCellValue(((java.lang.Number) o).doubleValue());
                        cell.setCellStyle(_style);
                    }
                    break;
                case(TYPE_STRING):
                default:
                    // 9729 : CRs are doubled in list data exported to Excel, normalize newlines as '\n'
                    String s = o.toString().replaceAll("\r\n", "\n");

                    // Check if the string is too long
                    if (s.length() > 32767)
                    {
                        s = s.substring(0, 32762) + "...";
                    }
                    
                    cell.setCellValue(s);
                    if (_style != null)
                        cell.setCellStyle(_style);
                    break;
            }

            if (cell != null)
            {
                if (columnInfo != null)
                {
                    CellStyle cellFormat = getExcelFormat(o, columnInfo);
                    if (cellFormat != null)
                    {
                        cell.setCellStyle(cellFormat);
                    }
                }
            }
        }
        catch(ClassCastException cce)
        {
            _log.error("Can't cast \'" + o.toString() + "\', class \'" + o.getClass().getName() + "\', to class corresponding to simple type \'" + _simpleType + "\'");
            _log.error("DisplayColumn.getCaption(): " + _dc.getCaption());
            _log.error("DisplayColumn.getClass().getName(): " + _dc.getClass().getName());
            _log.error("DisplayColumn.getDisplayValueClass(): " + _dc.getDisplayValueClass());
            _log.error("DisplayColumn.getValueClass(): " + _dc.getValueClass());
            _log.error("DisplayColumn.getColumnInfo().getSqlTypeInt(): " + columnInfo.getSqlTypeInt());
            _log.error("DisplayColumn.getColumnInfo().getSqlTypeName(): " + columnInfo.getSqlTypeName());

            throw cce;
        }
    }

    private CellStyle getExcelFormat(Object o, ColumnInfo columnInfo)
    {
        if (columnInfo == null)
        {
            // Not all DisplayColumns have a ColumnInfo
            return null;
        }
        
        for (ConditionalFormat format : columnInfo.getConditionalFormats())
        {
            if (format.meetsCriteria(o))
            {
                CellStyle excelFormat = _formats.get(format);
                if (excelFormat == null)
                {
                    Font font = _workbook.createFont();
                    if (format.isItalic())
                    {
                        font.setItalic(true);
                    }
                    if (format.isStrikethrough())
                    {
                        font.setStrikeout(true);
                    }
                    if (format.isBold())
                    {
                        font.setBoldweight(Font.BOLDWEIGHT_BOLD);
                    }
                    java.awt.Color textColor = format.getParsedTextColor();
                    if (textColor != null)
                    {
                        font.setColor(findBestColour(textColor));
                    }
                    excelFormat = _workbook.createCellStyle();
                    if (_simpleType == TYPE_INT || _simpleType == TYPE_DOUBLE || _simpleType == TYPE_DATE)
                    {
                        short formatIndex = _workbook.createDataFormat().getFormat(getFormatString());
                        excelFormat.setDataFormat(formatIndex);
                    }
                    if (_simpleType == TYPE_MULTILINE_STRING)
                    {
                        excelFormat.setWrapText(true);
                    }
                    excelFormat.setFont(font);
                    java.awt.Color backgroundColor = format.getParsedBackgroundColor();
                    if (backgroundColor != null)
                    {
                        excelFormat.setFillForegroundColor(findBestColour(backgroundColor));
                        excelFormat.setFillPattern(CellStyle.SOLID_FOREGROUND);
                    }
                    _formats.put(format, excelFormat);
                }
                return excelFormat;
            }
        }
        return null;
    }

    /** Since our Excel library has an enum of allowable colors, find the one that's closest to the one the user selected */
    private short findBestColour(java.awt.Color color)
    {
        int bestMatch = 0;
        int bestScore = Integer.MAX_VALUE;
        // The POI library doesn't give us any information about what each color in its enum actually looks like in
        // terms of RGB, so use the JXL library. The colors are indexed and standardized, so it's safe to use the
        // value across libraries
        for (Colour colour : Colour.getAllColours())
        {
            // Evaluate based on simple per-color difference in intensity
            int score = Math.abs(colour.getDefaultRGB().getRed() - color.getRed());
            score += Math.abs(colour.getDefaultRGB().getGreen() - color.getGreen());
            score += Math.abs(colour.getDefaultRGB().getBlue() - color.getBlue());

            if (score < bestScore)
            {
                bestScore = score;
                bestMatch = colour.getValue();
            }
        }
        return (short)bestMatch;
    }

    protected Row getRow(Sheet sheet, int rowNumber)
    {
        Row row = sheet.getRow(rowNumber);
        if (row == null)
        {
            row = sheet.createRow(rowNumber);
        }
        return row;
    }

    protected void renderCaption(Sheet sheet, int rowNumber, int column, CellStyle cellFormat, ColumnHeaderType headerType)
    {
        Cell cell = getRow(sheet, rowNumber).createCell(column);
        cell.setCellValue(headerType.getText(this.getDisplayColumn()));
        cell.setCellStyle(cellFormat);
    }

    // Note: width of the column will be adjusted once per call to ExcelWriter.render(), which potentially means
    // multiple times per sheet.  This shouldn't be a problem, though.
    protected void adjustWidth(Sheet sheet, int column, int startRow, int endRow)
    {
        if (_autoSize)
        {
            calculateAutoSize(sheet, column, startRow, endRow);
            // Maximum allowed is 255 characters. Width is in 1/256 of a character, so multiply by 256
            sheet.setColumnWidth(column, Math.min(_autoSizeWidth + 1, 255) * 256);
        }
        else
        {
            sheet.setColumnWidth(column, 10 * 256);
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
    private void calculateAutoSize(Sheet sheet, int column, int startRow, int endRow)
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
            Cell cell = getRow(sheet, row).getCell(column);
            if (cell != null)
            {
                String formatted = null;

                // Need to be careful here, checking _simpleType again and verifying legal values. See #18561 for an example
                // of a problem that occurred because we assumed all date values could be formatted by FastDateFormat.
                switch (cell.getCellType())
                {
                    case(Cell.CELL_TYPE_NUMERIC):
                        switch (_simpleType)
                        {
                            case(TYPE_DATE):
                                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell))
                                    formatted = format.format(cell.getDateCellValue());
                                break;
                            case(TYPE_INT):
                            case(TYPE_DOUBLE):
                                formatted = format.format(cell.getNumericCellValue());
                                break;
                        }
                        break;
                    case(Cell.CELL_TYPE_ERROR):
                        formatted = FormulaError.forInt(cell.getErrorCellValue()).getString();
                        break;
                    default:
                        formatted = cell.getStringCellValue();
                        break;
                }

                if (null != formatted && formatted.length() > _autoSizeWidth)
                    _autoSizeWidth = formatted.length();
            }
        }
    }

    // CONSIDER: Change RenderColumn to NOT extend DisplayElement
    public void render(RenderContext ctx, Writer out) throws IOException
    {
        throw new UnsupportedOperationException();
    }
}

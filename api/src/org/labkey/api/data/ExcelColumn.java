/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.log4j.Logger;
import org.apache.poi.hssf.usermodel.HSSFClientAnchor;
import org.apache.poi.hssf.usermodel.HSSFPicture;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.ClientAnchor.AnchorType;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Picture;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFAnchor;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.Pair;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
    private static final int TYPE_FILE = 7;


    /**
     * 256 is one full character, one character has 7 pixels.
     */
    private static final double PIXELS_TO_CHARACTERS = 256 / 7;

    private static final double MAX_IMAGE_RATIO = 0.75;
    private static final double MAX_IMAGE_HEIGHT = 400.0;
    private static final double MAX_IMAGE_WIDTH = 300.0;

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
        _dc.setRequiresHtmlFiltering(false);
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
        if (dc.getColumnInfo() != null)
        {
            // Use bound column's name so import/export will round-trip
            setName(dc.getColumnInfo().getName());
        }
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
        else if (File.class.isAssignableFrom(valueClass))
            _simpleType = TYPE_FILE;
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
                Cell cell = rowObject.getCell(column, MissingCellPolicy.CREATE_NULL_AS_BLANK);
                cell.setCellStyle(cellFormat);
            }
            return;
        }

        Cell cell = rowObject.getCell(column, MissingCellPolicy.CREATE_NULL_AS_BLANK);

        try
        {
            switch (_simpleType)
            {
                case(TYPE_DATE):
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

                case TYPE_FILE:
                    String filePath = o.toString().toLowerCase().replaceAll("\r\n", "\n");
                    cell.setCellValue(filePath);
                    if (_style != null)
                        cell.setCellStyle(_style);

                    Drawing drawing = (Drawing)ctx.get(ExcelWriter.SHEET_DRAWING);
                    if (drawing != null && _dc instanceof AbstractFileDisplayColumn)
                    {
                        String path = (String)o;
                        int imageType = -1;
                        if (path.endsWith(".png"))
                            imageType = Workbook.PICTURE_TYPE_PNG;
                        else if (path.endsWith(".jpeg") || path.endsWith(".jpg"))
                            imageType = Workbook.PICTURE_TYPE_JPEG;

                        if (imageType != -1)
                        {
                            BufferedImage img = null;
                            byte[] data = null;
                            try(InputStream is = ((AbstractFileDisplayColumn)_dc).getFileContents(ctx, o))
                            {
                                if (is != null)
                                {
                                    data = IOUtils.toByteArray(is);
                                    img = ImageIO.read(new ByteArrayInputStream(data));
                                }
                            }
                            catch (IOException e)
                            {
                                _log.error("Error reading image file data", e);
                                //throw new RuntimeException(e);
                                data = null; //will change to throw exception after fixing file lookups
                            }

                            if (data != null && data.length > 0)
                            {
                                int height = img.getHeight();
                                int width = img.getWidth();
//
                                double ratio = (double) width/height;
                                if (ratio >= MAX_IMAGE_RATIO)
                                {
                                    // resize to max width
                                    if (width > MAX_IMAGE_WIDTH)
                                    {
                                        height = (int) (height / (width / MAX_IMAGE_WIDTH));
                                        width = (int) MAX_IMAGE_WIDTH;
                                    }
                                }
                                else
                                {
                                    // resize to max height
                                    if (height > MAX_IMAGE_HEIGHT)
                                    {
                                        width = (int) (width / (height / MAX_IMAGE_HEIGHT));
                                        height = (int) MAX_IMAGE_HEIGHT;
                                    }
                                }
                                setImageSize(ctx, row, column, Pair.of(width, height));

                                Workbook wb = cell.getSheet().getWorkbook();
                                CreationHelper helper = wb.getCreationHelper();
                                int pictureIdx = wb.addPicture(data, imageType);

                                double rowRatio = height / /*maxRowHeight*/40.0;
                                double colRatio = width / /*maxColWidth*/120.0;
                                ClientAnchor anchor = createAnchor(row, column, rowRatio, colRatio, helper);
                                Picture pict = drawing.createPicture(anchor, pictureIdx);
                                setImagePicture(ctx, row, column, pict);
                            }
                        }
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
            _log.error("DisplayColumn.getColumnInfo().getJdbcType(): " + columnInfo.getJdbcType());
            _log.error("DisplayColumn.getColumnInfo().getSqlTypeName(): " + columnInfo.getSqlTypeName());

            throw cce;
        }
    }

    private void setImageSize(RenderContext ctx, int row, int column, Pair<Integer, Integer> size)
    {
        HashMap<Pair<Integer, Integer>, Pair<Integer, Integer>> imageSize = (HashMap<Pair<Integer, Integer>, Pair<Integer, Integer>>)ctx.get(ExcelWriter.SHEET_IMAGE_SIZES);
        if (imageSize != null)
            imageSize.put(Pair.of(row, column), size);
    }

    private void setImagePicture(RenderContext ctx, int row, int column, Picture pict)
    {
        HashMap<Pair<Integer, Integer>, Picture> pictures = (HashMap<Pair<Integer, Integer>, Picture>)ctx.get(ExcelWriter.SHEET_IMAGE_PICTURES);
        if (pictures != null)
            pictures.put(Pair.of(row, column), pict);
    }

    private Pair<Integer, Integer> getImageSize(RenderContext ctx, int row, int column)
    {
        HashMap<Pair<Integer, Integer>, Pair<Integer, Integer>> imageSize = (HashMap<Pair<Integer, Integer>, Pair<Integer, Integer>>)ctx.get(ExcelWriter.SHEET_IMAGE_SIZES);
        if (imageSize == null)
            return null;
        return imageSize.get(Pair.of(row, column));
    }

    private Picture getImagePicture(RenderContext ctx, int row, int column)
    {
        HashMap<Pair<Integer, Integer>, Picture> pictures = (HashMap<Pair<Integer, Integer>, Picture>)ctx.get(ExcelWriter.SHEET_IMAGE_PICTURES);
        if (pictures == null)
            return null;
        return pictures.get(Pair.of(row, column));
    }

    /**
     * Creates an anchor within a single cell.
     */
    private ClientAnchor createAnchor(int row, int column, double rowRatio, double colRatio, CreationHelper helper)
    {
        ClientAnchor anchor = helper.createClientAnchor();
        anchor.setAnchorType(AnchorType.MOVE_AND_RESIZE);
        anchor.setCol1(column);
        anchor.setRow1(row);
        anchor.setCol2(column);
        anchor.setRow2(row);
        // need to make the image smaller than the cell so it is sortable
        // create a margin of 1 on each side
        if (anchor instanceof XSSFClientAnchor) {
            // format is XSSF
            // full cell size is 1023 width and 255 height
            // each multiplied by XSSFShape.EMU_PER_PIXEL
            anchor.setDx1(XSSFShape.EMU_PER_PIXEL);
            anchor.setDy1(XSSFShape.EMU_PER_PIXEL);
            anchor.setDx2((int)(1022 * XSSFShape.EMU_PER_PIXEL * colRatio) - XSSFShape.EMU_PER_PIXEL);
            anchor.setDy2((int)(254 * XSSFShape.EMU_PER_PIXEL * rowRatio) - XSSFShape.EMU_PER_PIXEL);
        } else {
            // format is HSSF
            // full cell size is 1023 width and 255 height
            anchor.setDx1(colRatio > 1 ? (int) colRatio : 1);
            anchor.setDy1(rowRatio > 1 ? (int) rowRatio : 1);
            anchor.setDx2((int)(1021 * colRatio));
            anchor.setDy2((int)(253 * rowRatio));
        }
        return anchor;
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
                        font.setBold(true);
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
                        excelFormat.setFillPattern(FillPatternType.SOLID_FOREGROUND);
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
    protected void adjustWidth(RenderContext ctx, Sheet sheet, int column, int startRow, int endRow)
    {
        if (_autoSize)
        {
            boolean hasImage = false;
            for (int row = endRow; row >= startRow; row--)
            {
                Pair<Integer, Integer> imgSizes = getImageSize(ctx, row, column);
                if (imgSizes != null)
                {
                    hasImage = true;
                    break;
                }
            }
            if (!hasImage)
                sheet.autoSizeColumn(column);
            else
                adjustImageColumnWidth(ctx, sheet, column, startRow, endRow);

        }
        else
        {
            sheet.setColumnWidth(column, 10 * 256);
        }
    }

    private void adjustImageColumnWidth(RenderContext ctx, Sheet sheet, int column, int startRow, int endRow)
    {
        calculateAutoSize(ctx, sheet, column, startRow, endRow);

        int newWidth = Math.min(_autoSizeWidth + 1, 255) * 256;
        sheet.setColumnWidth(column, newWidth);

        for (int row = endRow; row >= startRow; row--)
        {
            Row sheetRow = sheet.getRow(row);
            float rowHeight = sheetRow.getHeightInPoints();
            Picture pict = getImagePicture(ctx, row, column);
            Pair<Integer, Integer> sizes = getImageSize(ctx, row, column);
            if (pict == null)
                continue;

            int originalWidth = sizes.first;
            int originalHeight = sizes.second;
            int adjustedWidth;
            int adjustedHeight;

            if (originalWidth <= MAX_IMAGE_WIDTH && originalHeight <= MAX_IMAGE_HEIGHT)
            {
                adjustedWidth = originalWidth;
                adjustedHeight = originalHeight;
            }
            else if (rowHeight / originalHeight > (float) newWidth / (originalWidth * PIXELS_TO_CHARACTERS))
            {
                adjustedWidth = (int) (originalWidth * (float) newWidth / (originalWidth * PIXELS_TO_CHARACTERS));
                adjustedHeight = (int) (originalHeight * (float) newWidth / (originalWidth * PIXELS_TO_CHARACTERS));
            }
            else
            {
                adjustedWidth = (int) (originalWidth * rowHeight / originalHeight);
                adjustedHeight = (int) (originalHeight * rowHeight / originalHeight);
            }

            if (pict instanceof XSSFPicture)
            {
                XSSFPicture picture = (XSSFPicture) pict;
                XSSFAnchor anchor = picture.getAnchor();
                anchor.setDx2(adjustedWidth * XSSFShape.EMU_PER_POINT);
                anchor.setDy2(adjustedHeight * XSSFShape.EMU_PER_POINT);
            }
            else if (pict instanceof  HSSFPicture)
            {
                HSSFPicture picture = (HSSFPicture) pict;
                HSSFClientAnchor anchor = (HSSFClientAnchor) picture.getAnchor();

                double xRatio = 1023.0 * PIXELS_TO_CHARACTERS / sheet.getColumnWidth(anchor.getCol2());
                double yRatio = 255.0 / sheet.getRow(anchor.getRow2()).getHeightInPoints();
                int dY2 = (int) (adjustedHeight * yRatio);
                int dX2 = (int) (adjustedWidth * xRatio);
                anchor.setDx2(dX2);
                anchor.setDy2(dY2);
            }
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
    private void calculateAutoSize(RenderContext ctx, Sheet sheet, int column, int startRow, int endRow)
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
                int length = -1;

                // Need to be careful here, checking _simpleType again and verifying legal values. See #18561 for an example
                // of a problem that occurred because we assumed all date values could be formatted by FastDateFormat.
                switch (cell.getCellType())
                {
                    case Cell.CELL_TYPE_NUMERIC:
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

                    case Cell.CELL_TYPE_ERROR:
                        formatted = FormulaError.forInt(cell.getErrorCellValue()).getString();
                        break;

                    case Cell.CELL_TYPE_BLANK:
                        if (_simpleType == TYPE_FILE) {
                            Pair<Integer, Integer> size = getImageSize(ctx, row, column);
                            if (size != null)
                            {
                                // added 2 margin pixels per side and 1 for the gridline
                                int width = size.first;
                                length = (width + 5) / 7;//PIXELS_TO_CHARACTERS);
                            }
                        }
                        break;

                    default:
                        Pair<Integer, Integer> size = getImageSize(ctx, row, column);
                        if (size != null)
                        {
                            // added 2 margin pixels per side and 1 for the gridline
                            int width = size.first;
                            length = (width + 5) / 7;//PIXELS_TO_CHARACTERS);
                        }
                        else
                            formatted = cell.getStringCellValue();
                        break;
                }

                if (formatted != null)
                    length = formatted.length();

                if (length > _autoSizeWidth)
                    _autoSizeWidth = length;
//                if (null != formatted && formatted.length() > _autoSizeWidth)
//                    _autoSizeWidth = formatted.length();
            }
        }
    }

    // CONSIDER: Change RenderColumn to NOT extend DisplayElement
    public void render(RenderContext ctx, Writer out) throws IOException
    {
        throw new UnsupportedOperationException();
    }
}

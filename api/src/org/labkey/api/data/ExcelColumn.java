/*
 * Copyright (c) 2004-2018 Fred Hutchinson Cancer Research Center
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
import org.apache.logging.log4j.Logger;
import org.apache.poi.hssf.usermodel.HSSFClientAnchor;
import org.apache.poi.hssf.usermodel.HSSFPicture;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.ClientAnchor.AnchorType;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Picture;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFAnchor;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.sql.Time;
import java.text.DecimalFormat;
import java.text.Format;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Representation of a column to be rendered into an Excel file. Wraps a
 * {@link org.labkey.api.data.DisplayColumn}
 */

public class ExcelColumn extends RenderColumn
{
    private static final Logger _log = LogHelper.getLogger(ExcelColumn.class, "Excel column rendering");


    /**
     * 256 is one full character, one character has 7 pixels.
     */
    private static final double PIXELS_TO_CHARACTERS = 256 / 7;

    private static final double MAX_IMAGE_RATIO = 0.75;
    private static final double MAX_IMAGE_HEIGHT = 400.0;
    private static final double MAX_IMAGE_WIDTH = 300.0;

    // CONSIDER: Add support for left/right/center alignment (from DisplayColumn)
    private int _simpleType = ExcelCellUtils.TYPE_UNKNOWN;
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

        setName(dc.getName());
        if (dc.getColumnInfo() != null)
        {
            // Use bound column's name so import/export will round-trip
            setName(dc.getColumnInfo().getName());
        }
        setCaption(dc.getCaptionExpr());
        setSimpleType(dc); //call after the call to setName()

        if (dc.getExcelFormatString() != null)
        {
            setFormatString(dc.getExcelFormatString());
        }
        else
        {
            setFormatString(dc.getFormatString());
        }
    }

    public DisplayColumn getDisplayColumn()
    {
        return _dc;
    }

    @Override
    public void setName(String name)
    {
        _name = name;
    }


    @Override
    public String getCaption()
    {
        return _caption;
    }


    @Override
    public void setCaption(String caption)
    {
        _caption = caption;
    }


    private void setSimpleType(DisplayColumn dc)
    {
        _simpleType = ExcelCellUtils.getSimpleType(dc);

        if (_simpleType == ExcelCellUtils.TYPE_UNKNOWN)
        {
            Class valueClass = dc.getDisplayValueClass();
            _log.error("init: Unknown Class " + valueClass + " " + getName());
        }
    }


    @Override
    public String getFormatString()
    {
        String formatString = super.getFormatString();
        return ExcelCellUtils.getFormatString(_simpleType, formatString);
    }


    @Override
    public void setFormatString(String formatString)
    {
        super.setFormatString(formatString);

        ExcelFormatDescriptor formatDescriptor = null;

        switch (_simpleType)
        {
            case(ExcelCellUtils.TYPE_INT):
            case(ExcelCellUtils.TYPE_DOUBLE):
            {
                formatDescriptor = new ExcelFormatDescriptor(Number.class, getFormatString());
                break;
            }
            case(ExcelCellUtils.TYPE_DATE):
            {
                formatDescriptor = new ExcelFormatDescriptor(Date.class, getFormatString());
                break;
            }
            case(ExcelCellUtils.TYPE_TIME):
            {
                formatDescriptor = new ExcelFormatDescriptor(Time.class, getFormatString());
                break;
            }
            case(ExcelCellUtils.TYPE_MULTILINE_STRING):
            {
                formatDescriptor = new ExcelFormatDescriptor(String.class, getFormatString());
                break;
            }
        }

        if (formatDescriptor != null)
        {
            _style = _formatters.get(formatDescriptor);

            if (_style == null)
            {
                _style = ExcelCellUtils.createCellStyle(_workbook, _simpleType, getFormatString());
                _formatters.put(formatDescriptor, _style);
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


    @Override
    public String getName()
    {
        return _name;
    }


    public void writeCell(Sheet sheet, int column, int row, RenderContext ctx)
    {
        Object o = _dc.getExcelCompatibleValue(ctx);
        ColumnInfo columnInfo = _dc.getColumnInfo();
        Row rowObject = getRow(sheet, row);

        Cell cell = null;
        if (null == o)
        {
            // For null values, don't create the cell unless there's a conditional format that applies
            CellStyle cellFormat = getExcelFormat(o, columnInfo);
            if (cellFormat != null)
            {
                // Set the format but there's no value to set
                cell = rowObject.getCell(column, MissingCellPolicy.CREATE_NULL_AS_BLANK);
                cell.setCellStyle(cellFormat);
            }
            return;
        }

        cell = rowObject.getCell(column, MissingCellPolicy.CREATE_NULL_AS_BLANK);

        try
        {
            if (_simpleType == ExcelCellUtils.TYPE_FILE)
            {
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
            }
            else if (_simpleType == ExcelCellUtils.TYPE_BOOLEAN)
            {
                String s = _dc.getTsvFormattedValue(ctx);
                cell.setCellValue(s);
                if (_style != null)
                    cell.setCellStyle(_style);
            }
            else
            {
                ExcelCellUtils.writeCell(cell, _style, _simpleType, getFormatString(), columnInfo, o);
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
            _log.error("Can't cast '" + o.toString() + "', class '" + o.getClass().getName() + "', to class corresponding to simple type '" + _simpleType + "'");
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
            anchor.setDx1(Units.EMU_PER_PIXEL);
            anchor.setDy1(Units.EMU_PER_PIXEL);
            anchor.setDx2((int)(1022 * Units.EMU_PER_PIXEL * colRatio) - Units.EMU_PER_PIXEL);
            anchor.setDy2((int)(254 * Units.EMU_PER_PIXEL * rowRatio) - Units.EMU_PER_PIXEL);
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
            if (format.meetsCriteria(columnInfo, o))
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
                    if (_simpleType == ExcelCellUtils.TYPE_INT || _simpleType == ExcelCellUtils.TYPE_DOUBLE || _simpleType == ExcelCellUtils.TYPE_DATE || _simpleType == ExcelCellUtils.TYPE_TIME)
                    {
                        short formatIndex = _workbook.createDataFormat().getFormat(getFormatString());
                        excelFormat.setDataFormat(formatIndex);
                    }
                    if (_simpleType == ExcelCellUtils.TYPE_MULTILINE_STRING)
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
        return getRow(sheet, rowNumber, false);
    }

    protected Row getRow(Sheet sheet, int rowNumber, boolean skipCreate)
    {
        Row row = sheet.getRow(rowNumber);
        if (row == null && !skipCreate)
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
    public void adjustWidth(RenderContext ctx, Sheet sheet, int column, int startRow, int endRow)
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
            if (sheetRow == null)
                continue;

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
                anchor.setDx2(adjustedWidth * Units.EMU_PER_POINT);
                anchor.setDy2(adjustedHeight * Units.EMU_PER_POINT);
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
            case(ExcelCellUtils.TYPE_DATE):
                format = FastDateFormat.getInstance(getFormatString());
                break;
            case(ExcelCellUtils.TYPE_INT):
            case(ExcelCellUtils.TYPE_DOUBLE):
                format = new DecimalFormat(getFormatString());
                break;
        }

        // Assumes column has same cell type from startRow to endRow, and that cell type matches the Excel column type (which it should, since we just wrote it)
        for (int row = startRow; row <= endRow; row++)
        {
            Row sheetRow = getRow(sheet, row, true);
            if (sheetRow == null)
                continue;

            Cell cell = sheetRow.getCell(column);
            if (cell != null)
            {
                String formatted = null;
                int length = -1;

                // Need to be careful here, checking _simpleType again and verifying legal values. See #18561 for an example
                // of a problem that occurred because we assumed all date values could be formatted by FastDateFormat.
                switch (cell.getCellType())
                {
                    case NUMERIC:
                        switch (_simpleType)
                        {
                            case(ExcelCellUtils.TYPE_DATE):
                                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell))
                                    formatted = format.format(cell.getDateCellValue());
                                break;
                            case(ExcelCellUtils.TYPE_INT):
                            case(ExcelCellUtils.TYPE_DOUBLE):
                                formatted = format.format(cell.getNumericCellValue());
                                break;
                        }
                        break;

                    case ERROR:
                        formatted = FormulaError.forInt(cell.getErrorCellValue()).getString();
                        break;

                    case BLANK:
                        if (_simpleType == ExcelCellUtils.TYPE_FILE) {
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
    @Override
    public void render(RenderContext ctx, Writer out)
    {
        throw new UnsupportedOperationException();
    }

    public static class TestCase extends Assert
    {
        public static final String PROJECT_NAME = "ExcelColumnIntegrationTest";
        private static final String LISTNAME = "List1";

        @BeforeClass
        public static void initialSetUp() throws Exception
        {
            Assume.assumeTrue("This test requires the list module.", ListService.get() != null);

            doInitialSetUp(PROJECT_NAME);
        }

        @AfterClass
        public static void cleanup()
        {
            doCleanup(PROJECT_NAME);
        }

        @Test
        public void testExportFormatting() throws Exception
        {
            UserSchema us = QueryService.get().getUserSchema(getUser(), getProject(), "lists");

            //insert data:
            List<Map<String, Object>> toInsert = new ArrayList<>();
            Arrays.stream(DATA).forEach(r -> {
                Map<String, Object> row = new CaseInsensitiveHashMap<>();
                for (int i=0;i<r.length;i++)
                {
                    row.put(FIELDS[i][0].toString(), r[i]);
                }

                toInsert.add(row);
            });

            TableInfo ti = us.getTable(LISTNAME);
            BatchValidationException bve = new BatchValidationException();
            ti.getUpdateService().insertRows(getUser(), getProject(), toInsert, bve, null, null);
            if (bve.hasErrors())
            {
                throw bve;
            }

            //download as excel:
            QueryForm qf = new QueryForm();
            qf.setSchemaName("lists");
            qf.setQueryName(LISTNAME);
            qf.setViewContext(ViewContext.getMockViewContext(TestContext.get().getUser(), getProject(), getProject().getStartURL(TestContext.get().getUser()), false));
            List<FieldKey> fields = Arrays.stream(FIELDS).map(x -> {
                return FieldKey.fromString(x[0].toString());
            }).collect(Collectors.toList());
            qf.getQuerySettings().setFieldKeys(fields);

            BindException errors = new NullSafeBindException(new Object(), "command");
            QueryView view = us.createView(qf, errors);
            ExcelWriter excel = view.getExcelWriter(ExcelWriter.ExcelDocumentType.xlsx);
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream())
            {
                excel.renderWorkbook(baos);
                Sheet wb = WorkbookFactory.create(new ByteArrayInputStream(baos.toByteArray())).getSheetAt(0);
                DataFormatter formatter = new DataFormatter();
                for (int rowIdx = 0; rowIdx < DATA.length; rowIdx++)
                {
                    Object[] expectedData = DATA[rowIdx];
                    Row excelRow = wb.getRow(rowIdx + 1);
                    for (int fieldIdx = 0; fieldIdx < FIELDS.length; fieldIdx++)
                    {
                        Object[] fieldDef = FIELDS[fieldIdx];
                        ColumnInfo ci = ti.getColumn((String) fieldDef[0]);

                        String expected = parseAndFormatExpected(expectedData[fieldIdx], ci);
                        Cell cell = excelRow.getCell(fieldIdx);
                        String actual = formatter.formatCellValue(cell);
                        assertEquals("Incorrect Excel Value", expected, actual);

                        Object expectedObj = ConvertHelper.convert(expected, ci.getJavaClass());
                        Object actualObj = ConvertHelper.convert(actual, ci.getJavaClass());
                        assertEquals("Incorrect Parsed Excel Value", expectedObj, actualObj);
                    }
                }
            }
        }

        private String parseAndFormatExpected(Object value, ColumnInfo ci)
        {
            Format fmt = ci.getDisplayColumnFactory().createRenderer(ci).getFormat();
            if (fmt != null)
            {
                value = ConvertHelper.convert(value, ci.getJavaClass());
                return fmt.format(value);
            }

            return value.toString();
        }

        private static final Object[][] FIELDS = new Object[][]{
                {"PKField", JdbcType.VARCHAR},
                {"DateField", JdbcType.DATE, "yyyy-MM-dd"},
                {"DateTimeField", JdbcType.TIMESTAMP, "yyyy-MM-dd hh:mm"},
                {"TextField", JdbcType.VARCHAR},
                {"MultiLineTextField", JdbcType.VARCHAR},
                {"IntField", JdbcType.INTEGER},
                {"DoubleField", JdbcType.DOUBLE, "##.##"},
                {"DoubleField2", JdbcType.DOUBLE, "##.000"}
        };

        private static final Object[][] DATA = new Object[][]{
                {"1", "2019-01-01", "2019-02-01 10:20", "Text1", "Line1\nLine2", 1, 1.0, 2.0},
                {"2", "1/1/2004", "2000/04/04 11:00", "Text12", "Line1\nLine3", 200, 4.5, 3.1}
        };

        protected static void doInitialSetUp(String projectName) throws Exception
        {
            //pre-clean
            doCleanup(projectName);

            Container project = ContainerManager.getForPath(projectName);
            if (project == null)
            {
                project = ContainerManager.createContainer(ContainerManager.getRoot(), projectName, TestContext.get().getUser());
            }

            //create list:
            ListDefinition ld1 = ListService.get().createList(project, LISTNAME, ListDefinition.KeyType.Varchar);
            Arrays.stream(FIELDS).forEach(x -> {
                DomainProperty dp = ld1.getDomain().addProperty(new PropertyStorageSpec((String)x[0], (JdbcType)x[1]));
                dp.setLabel((String)x[0]);  //to simplify parsing excel headers
                if (x.length > 2)
                {
                    dp.setFormat(x[2].toString());
                }
            });

            ld1.setKeyName("PKField");
            ld1.save(TestContext.get().getUser());
        }

        protected static void doCleanup(String projectName)
        {
            Container project = ContainerManager.getForPath(projectName);
            if (project != null)
            {
                ContainerManager.delete(project, getUser());
            }
        }

        protected static User getUser()
        {
            return TestContext.get().getUser();
        }

        protected Container getProject()
        {
            return ContainerManager.getForPath(PROJECT_NAME);
        }
    }
}

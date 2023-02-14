/*
 * Copyright (c) 2011-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.reader;

import org.apache.commons.beanutils.ConversionException;
import org.apache.logging.log4j.Logger;
import org.apache.poi.UnsupportedFileFormatException;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.format.CellGeneralFormatter;
import org.apache.poi.ss.formula.FormulaParseException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.model.SharedStringsTable;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.reader.jxl.JxlWorkbook;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.logging.LogHelper;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTSheet;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper that abstracts the different versions of Excel files and returns the implementation that can
 * parse or create them.
 * User: klum
 * Date: May 2, 2011
 */
public class ExcelFactory
{
    private static final Logger LOG = LogHelper.getLogger(ExcelFactory.class, "Excel file parsing (.xls, .xlsx)");

    public static final String SUB_TYPE_XSSF = "vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String SUB_TYPE_BIFF5 = "x-tika-msoffice";
    public static final String SUB_TYPE_BIFF8 = "vnd.ms-excel";


    public static class WorkbookMetadata implements Closeable
    {
        private final List<String> _names;
        private final boolean _isSpreadSheetML;
        private final Boolean _isStart1904;
        private final Workbook _workbook;
        private final String[] _sharedStrings;

        WorkbookMetadata(Workbook workbook, boolean isXML, @Nullable List<String> names, @Nullable Boolean isStart1904, @Nullable SharedStringsTable sharedStrings)
        {
            this._workbook = workbook;
            this._isSpreadSheetML = isXML;
            this._isStart1904 = _isSpreadSheetML ? isStart1904 : null;

            if (null == names)
            {
                names = new ArrayList<>();
                for (int i=0 ; i < workbook.getNumberOfSheets() ; i++)
                    names.add(workbook.getSheetName(i));
            }
            this._names = names;

            if (null == sharedStrings)
                this._sharedStrings = null;
            else
            {
                var items = sharedStrings.getSharedStringItems();
                String[] strings = new String[items.size()];
                for (int i = 0; i < items.size(); i++)
                    strings[i] = items.get(i).getString();
                this._sharedStrings = strings;
            }
        }

        public boolean isSpreadSheetML()
        {
            return _isSpreadSheetML;
        }
        public List<String> getSheetNames()
        {
            return _names;
        }
        // returns null if we don't know (.XLS).
        // Consider moving code from ExcelLoader.computeIsStartDate1904() to here
        @Nullable
        public Boolean isStart1904()
        {
            return _isStart1904;
        }
        /* I can't seem to avoid loading the shared strings so might as well return them */
        public String[] getSharedStrings()
        {
            return _sharedStrings;
        }
        /* returns non-null value if workbook is the same as returned by create(File) */
        public Workbook getWorkbook()
        {
            return _workbook;
        }

        @Override
        public void close() throws IOException
        {
            if (getWorkbook() != null)
            {
                getWorkbook().close();
            }
        }
    }


    // throw FileNotFoundException instead of InvalidOperationException or InvalidFormatException
    static OPCPackage openOPCPackage(@NotNull File file) throws IOException, InvalidFormatException
    {
        if (!file.isFile())
            throw new FileNotFoundException("file not found");
        return OPCPackage.open(file, PackageAccess.READ);
    }


    public static WorkbookMetadata getMetadata(@NotNull File file) throws IOException, InvalidFormatException
    {
        try (OPCPackage opc = openOPCPackage(file))
        {
            return getMetadata(opc);
        }
        // might be OLE2 formet
        catch (InvalidFormatException|UnsupportedFileFormatException e)
        {
            Workbook wb = create(file);
            return getMetadata(wb, null);
        }
        catch (IllegalArgumentException e)
        {
            throw new InvalidFormatException("Unable to open file as an Excel document. " + (e.getMessage() == null ? "" : e.getMessage()));
        }
    }


    @NotNull
    public static WorkbookMetadata getMetadata(@NotNull OPCPackage opc) throws IOException, InvalidFormatException
    {
        final ArrayList<String> sheetNames = new ArrayList<>();
        Workbook wb = new XSSFWorkbook(opc)
        {
            @Override
            public void parseSheet(Map<String, XSSFSheet> shIdMap, CTSheet ctSheet)
            {
                sheetNames.add(ctSheet.getName());
            }
        };
        return getMetadata(wb, sheetNames);
    }


    public static WorkbookMetadata getMetadata(Workbook wb) throws IOException, InvalidFormatException
    {
        ArrayList<String> names = new ArrayList<>();
        for (int i=0 ; i<wb.getNumberOfSheets() ; i++)
            names.add(wb.getSheetName(i));
        return getMetadata(wb, names);
    }


    public static WorkbookMetadata getMetadata(Workbook wb, List<String> names) throws IOException, InvalidFormatException
    {
        if (wb instanceof XSSFWorkbook)
            return new WorkbookMetadata(null, true, names, ((XSSFWorkbook)wb).isDate1904(), ((XSSFWorkbook)wb).getSharedStringSource());
        else
            return new WorkbookMetadata(wb, false, names, null, null);
    }


    /**
     * Sniffs the file type and returns the appropriate parser
     * @throws InvalidFormatException if the file isn't a recognized Excel format
     */
    @NotNull
    public static Workbook create(@NotNull File file) throws IOException, InvalidFormatException
    {
        try (OPCPackage opc = openOPCPackage(file))
        {
            return new XSSFWorkbook(opc)
            {
                // This overload is here to make it easy to see which code paths are inadvertently loading spreadsheet data the expensive way!

                @Override
                public void parseSheet(Map<String, XSSFSheet> shIdMap, CTSheet ctSheet)
                {
                    super.parseSheet(shIdMap, ctSheet);
                }
            };
        }
        catch (UnsupportedFileFormatException not_xssf)
        {
            try
            {
                return WorkbookFactory.create(file);
            }
            catch (UnsupportedFileFormatException|IOException not_ole2_either)
            {
                if (not_ole2_either instanceof IOException && !not_ole2_either.getMessage().contains("unsupported file type"))
                    throw not_ole2_either;

                try (FileInputStream fis = new FileInputStream(file))
                {
                    return new JxlWorkbook(fis);
                }
            }
        }
        catch (IllegalArgumentException | POIXMLException e)
        {
            // Issue 45464 - improve error message for .xlsx variant that's unsupported by POI
            if (e.getMessage() != null && e.getMessage().contains("57699"))
            {
                throw new ExcelFormatException("Unable to open file as an Excel document. \"Strict Open XML Spreadsheet\" versions of .xlsx files are not supported.", e);
            }
            else
            {
                throw new ExcelFormatException("Unable to open file as an Excel document. " + (e.getMessage() == null ? "" : e.getMessage()), e);
            }
        }
    }

    /**
     * Contructs an in-memory Excel file from a JSON representation, as described in the LABKEY.Utils.convertToExcel JavaScript API
     */
    public static Workbook createFromArray(JSONArray sheetsArray, ExcelWriter.ExcelDocumentType docType)
    {
        Workbook workbook = docType.createWorkbook();
        CreationHelper factory = workbook.getCreationHelper();

        Map<String, CellStyle> customStyles = new HashMap<>();

        for (int sheetIndex = 0; sheetIndex < sheetsArray.length(); sheetIndex++)
        {
            JSONObject sheetObject = sheetsArray.getJSONObject(sheetIndex);
            String sheetName = sheetObject.has("name") ? sheetObject.getString("name") : "Sheet" + sheetIndex;
            sheetName = ExcelWriter.cleanSheetName(sheetName);
            Sheet sheet = workbook.createSheet(sheetName);

            DataFormat dataFormat = workbook.createDataFormat();
            CellStyle defaultStyle = workbook.createCellStyle();
            CellStyle defaultDateStyle = workbook.createCellStyle();
            defaultDateStyle.setDataFormat(dataFormat.getFormat(org.labkey.api.util.DateUtil.getStandardDateFormatString()));

            CellStyle errorStyle = workbook.createCellStyle();
            errorStyle.setFillBackgroundColor(IndexedColors.RED.getIndex());

            JSONArray rowsArray = sheetObject.optJSONArray("data");
            if (rowsArray == null)
            {
                rowsArray = new JSONArray();
            }
            for (int rowIndex = 0; rowIndex < rowsArray.length(); rowIndex++)
            {
                JSONArray rowArray = rowsArray.getJSONArray(rowIndex);

                Row row = sheet.createRow(rowIndex);

                for (int colIndex = 0; colIndex < rowArray.length(); colIndex++)
                {
                    // NOTE: if the value is null, it is stored as JSONObject.NULL. check for this:
                    Object value = rowArray.isNull(colIndex) ? null : rowArray.get(colIndex);
                    JSONObject metadataObject = null;
                    CellStyle cellStyle = defaultStyle;
                    if (value instanceof JSONObject)
                    {
                        metadataObject = (JSONObject)value;
                        value = metadataObject.get("value");
                    }

                    boolean forceString = metadataObject != null && metadataObject.has("forceString") && Boolean.TRUE.equals(metadataObject.get("forceString"));

                    String cellComment = null;
                    if ( metadataObject != null && metadataObject.has("cellComment")){
                        cellComment = metadataObject.getString("cellComment");
                    }

                    Cell cell = row.createCell(colIndex);
                    if (value instanceof Number)
                    {
                        cell.setCellValue(((Number)value).doubleValue());
                        if (metadataObject != null && metadataObject.has("formatString"))
                        {
                            String formatString = metadataObject.getString("formatString");
                            cellStyle = getCustomCellStyle(workbook, customStyles, dataFormat, formatString);
                        }
                    }
                    else if (value instanceof Boolean)
                    {
                        cell.setCellValue((Boolean) value);
                    }
                    else if (value instanceof String && !forceString)
                    {
                        try
                        {
                            // JSON has no date literal syntax so try to parse all Strings as dates
                            Date d = new Date(org.labkey.api.util.DateUtil.parseDateTime((String) value));
                            try
                            {
                                if (metadataObject != null && metadataObject.has("formatString"))
                                {
                                    cellStyle = getCustomCellStyle(workbook, customStyles, dataFormat, metadataObject.getString("formatString"));
                                }
                                else
                                {
                                    cellStyle = defaultDateStyle;
                                }
                                boolean timeOnly = metadataObject != null && metadataObject.has("timeOnly") && Boolean.TRUE.equals(metadataObject.get("timeOnly"));
                                cell.setCellValue(d);
                            }
                            catch (IllegalArgumentException e)
                            {
                                // Invalid date format
                                cellStyle = errorStyle;
                                cell.setCellValue(e.getMessage());
                            }
                        }
                        catch (ConversionException e)
                        {
                            // Not a date
                            cell.setCellValue((String)value);
                        }
                    }
                    else if (value != null)
                    {
                        cell.setCellValue(value.toString());
                    }
                    if (cell != null)
                    {
                        cell.setCellStyle(cellStyle);
                    }
                    if (cell != null && cellComment != null)
                    {
                        //Determine the size of the comment box based on the length of the comment
                        int commentBoxWidth = 3;
                        int commentBoxHeight = 4;

                        if (cellComment.length() > 30 )
                        {
                            commentBoxHeight= 5;
                        }
                        if (cellComment.length() > 45 && cellComment.length() < 60)
                        {
                            commentBoxWidth= 4;
                            commentBoxHeight= 4;
                        }
                        if (cellComment.length() >= 60)
                        {
                            commentBoxHeight = 5;
                        }
                        if (cellComment.length() > 70)
                        {
                            commentBoxHeight = 6;
                            commentBoxWidth = 4;
                        }
                        ClientAnchor anchor = factory.createClientAnchor();
                        Drawing drawing = sheet.createDrawingPatriarch();

                        anchor.setCol1(cell.getColumnIndex()+1);
                        anchor.setCol2(cell.getColumnIndex()+commentBoxWidth);
                        anchor.setRow1(row.getRowNum()+1);
                        anchor.setRow2(row.getRowNum()+commentBoxHeight);

                        Comment comment = drawing.createCellComment(anchor);
                        RichTextString str = factory.createRichTextString(cellComment);
                        comment.setString(str);
                        comment.setAuthor("LabKey Server");

                        cell.setCellComment(comment);
                    }
                }
            }
        }

        return workbook;
    }

    private static CellStyle getCustomCellStyle(Workbook workbook, Map<String, CellStyle> customStyles, DataFormat dataFormat, String formatString)
    {
        CellStyle cellStyle;
        cellStyle = customStyles.get(formatString);
        if (cellStyle == null)
        {
            cellStyle = workbook.createCellStyle();
            cellStyle.setDataFormat(dataFormat.getFormat(formatString));
            customStyles.put(formatString, cellStyle);
        }
        return cellStyle;
    }

    /**
     * @param colIndex zero-based column index
     * @param rowIndex zero-based row index
     * @param sheetName name of the sheet (optional)
     */
    public static String getCellLocationDescription(int colIndex, int rowIndex, @Nullable String sheetName)
    {
        String cellLocation = getCellColumnDescription(colIndex) + (rowIndex + 1);
        if (sheetName != null)
        {
            return cellLocation + " in sheet '" + sheetName + "'";
        }
        return cellLocation;
    }

    /**
     * @param colIndex zero-based column index
     * http://stackoverflow.com/questions/22708/how-do-i-find-the-excel-column-name-that-corresponds-to-a-given-integer
     */
    private static String getCellColumnDescription(int colIndex)
    {
        // Convert to one-based index
        colIndex++;
        
        StringBuilder name = new StringBuilder();
        while (colIndex > 0)
        {
            colIndex--;
            name.insert(0, (char) ('A' + colIndex % 26));
            colIndex /= 26;
        }
        return name.toString();
    }

    /**
     * Helper to safely convert cell values to a string equivalent
     */
    public static String getCellStringValue(Cell cell)
    {
        if (cell != null)
        {
            CellGeneralFormatter formatter = new CellGeneralFormatter();

            if ("General".equals(cell.getCellStyle().getDataFormatString()))
            {
                switch (cell.getCellType())
                {
                    case BOOLEAN:
                        return formatter.format(cell.getBooleanCellValue());
                    case NUMERIC:
                        return formatter.format(cell.getNumericCellValue());
                    case FORMULA:
                    {
                        if (cell.getCachedFormulaResultType() == CellType.STRING)
                        {
                            return cell.getStringCellValue();
                        }
                        Workbook wb = cell.getSheet().getWorkbook();
                        FormulaEvaluator evaluator = createFormulaEvaluator(wb);
                        if (evaluator != null)
                        {
                            try
                            {
                                return evaluator.evaluate(cell).formatAsString();
                            }
                            catch (FormulaParseException e)
                            {
                                return e.getMessage() == null ? e.toString() : e.getMessage();
                            }
                        }
                        return "";
                    }
                    case ERROR:
                        return ((Byte)cell.getErrorCellValue()).toString();
                }
                return cell.getStringCellValue();
            }
            else if (isCellNumeric(cell) && DateUtil.isCellDateFormatted(cell) && cell.getDateCellValue() != null)
                return formatter.format(cell.getDateCellValue());
            else if (cell.getCellType() == CellType.FORMULA && cell.getCachedFormulaResultType() == CellType.STRING)
                return cell.getStringCellValue();
            else
            {
                // This seems to be the best way to get the value that's shown in Excel
                // http://stackoverflow.com/questions/1072561/how-can-i-read-numeric-strings-in-excel-cells-as-string-not-numbers-with-apach
                try
                {
                    Workbook wb = cell.getSheet().getWorkbook();
                    FormulaEvaluator evaluator = createFormulaEvaluator(wb);
                    return new DataFormatter().formatCellValue(cell, evaluator);
                }
                catch (Exception e)
                {
                    // Issue 41879 -- best effort, don't make a big fuss over bad formula
                    LOG.warn("Exception parsing Excel formula: " + e.getMessage());
                }
            }
        }
        return "";
    }

    public static boolean isCellNumeric(Cell cell)
    {
        if (cell != null)
        {
            CellType type = cell.getCellType();
            if (type == CellType.FORMULA)
            {
                type = cell.getCachedFormulaResultType();
            }
            return type == CellType.NUMERIC;
        }
        return false;
    }

    public static FormulaEvaluator createFormulaEvaluator(Workbook workbook)
    {
        return workbook != null ? workbook.getCreationHelper().createFormulaEvaluator() : null;
    }

    /**
     * Returns a specified cell given a col/row format
     */
    @Nullable
    public static Cell getCell(Sheet sheet, int colIdx, int rowIdx)
    {
        Row row = sheet.getRow(rowIdx);

        return row != null ? row.getCell(colIdx) : null;
    }

    /** Supports .xls (BIFF8 only), and .xlsx */
    public static JSONArray convertExcelToJSON(InputStream in, boolean extended) throws IOException, InvalidFormatException
    {
        try (Workbook workbook = WorkbookFactory.create(in))
        {
            return convertExcelToJSON(workbook, extended, -1);
        }
    }

    /** Supports both new and old style .xls (BIFF5 and BIFF8), and .xlsx because we can reopen the stream if needed */
    public static JSONArray convertExcelToJSON(File excelFile, boolean extended) throws IOException, InvalidFormatException
    {
        return convertExcelToJSON(excelFile, extended, -1);
    }

    public static JSONArray convertExcelToJSON(File excelFile, boolean extended, int maxRows) throws IOException, InvalidFormatException
    {
        try (Workbook workbook = ExcelFactory.create(excelFile))
        {
            return convertExcelToJSON(workbook, extended, maxRows);
        }
    }

    /** Supports .xls (BIFF8 only) and .xlsx */
    public static JSONArray convertExcelToJSON(Workbook workbook, boolean extended)
    {
        return convertExcelToJSON(workbook, extended, -1);
    }

    public static JSONArray convertExcelToJSON(Workbook workbook, boolean extended, int maxRows)
    {
        JSONArray result = new JSONArray();

        DataFormatter formatter = new DataFormatter();

        for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++)
        {
            JSONArray rowsArray = new JSONArray();
            Sheet sheet = workbook.getSheetAt(sheetIndex);
            for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++)
            {
                if (maxRows > -1 && maxRows <= rowIndex)
                    break;

                Row row = sheet.getRow(rowIndex);
                JSONArray rowArray = new JSONArray();
                if (row != null)
                {
                    for (int cellIndex = 0; cellIndex < row.getLastCellNum(); cellIndex++)
                    {
                        Object value;
                        JSONObject metadataMap = new JSONObject();
                        Cell cell = row.getCell(cellIndex, MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        String formatString = cell.getCellStyle().getDataFormatString();
                        String formattedValue;
                        try
                        {
                            if (cell.getCellComment() != null && cell.getCellComment().getString() != null)
                            {
                                metadataMap.put("comment", cell.getCellComment().getString().getString());
                            }
                        }
                        // Workaround for issue 15437
                        catch (NullPointerException|UnsupportedOperationException ignored) {}

                        if ("General".equalsIgnoreCase(formatString))
                        {
                            formatString = null;
                        }

                        CellType effectiveCellType = cell.getCellType();
                        if (effectiveCellType == CellType.FORMULA)
                        {
                            effectiveCellType = cell.getCachedFormulaResultType();
                            metadataMap.put("formula", cell.getCellFormula());
                        }

                        switch (effectiveCellType)
                        {
                            case NUMERIC:
                                if (DateUtil.isCellDateFormatted(cell))
                                {
                                    value = cell.getDateCellValue();

                                    boolean timeOnly = false;
                                    Format format = formatter.createFormat(cell);
                                    if (format instanceof SimpleDateFormat)
                                    {
                                        formatString = ((SimpleDateFormat)format).toPattern();
                                        timeOnly = !formatString.contains("G") && !formatString.contains("y") &&
                                                !formatString.contains("M") && !formatString.contains("w") &&
                                                !formatString.contains("W") && !formatString.contains("D") &&
                                                !formatString.contains("d") && !formatString.contains("F") &&
                                                !formatString.contains("E");

                                        formattedValue = format.format(value);
                                    }
                                    else
                                    {
                                        formattedValue = formatter.formatCellValue(cell);
                                    }
                                    metadataMap.put("timeOnly", timeOnly);
                                }
                                else
                                {
                                    value = cell.getNumericCellValue();
                                    if (formatString != null)
                                    {
                                        // Excel escapes characters like $ in its number formats
                                        formatString = formatString.replace("\"", "");
                                        boolean excelScientific = "0.00E+00".equals(formatString);
                                        if (excelScientific)
                                        {
                                            // DecimalFormat doesn't understand Excel's scientific notation format syntax
                                            formatString = "0.00E00";
                                        }
                                        try
                                        {
                                            formattedValue = new DecimalFormat(formatString).format(value);
                                        }
                                        catch (IllegalArgumentException e)
                                        {
                                            formattedValue = Double.toString(cell.getNumericCellValue());
                                        }

                                        if (excelScientific && !formattedValue.contains("E-"))
                                        {
                                            // Need to insert a plus between the E and exponent to match Excel's formatting
                                            formattedValue = formattedValue.replace("E", "E+");
                                        }
                                    }
                                    else
                                    {
                                        formattedValue = formatter.formatCellValue(cell);
                                    }
                                }
                                break;

                            case BOOLEAN:
                                value = cell.getBooleanCellValue();
                                formattedValue = value.toString();
                                break;

                            case ERROR:
                                FormulaError error = FormulaError.forInt(cell.getErrorCellValue());
                                metadataMap.put("error", true);
                                if (error != null)
                                {
                                    value = error.getString();
                                }
                                else
                                {
                                    value = "Error! (code " + cell.getErrorCellValue() + ")";
                                }
                                formattedValue = value.toString();
                                break;

                            default:
                                value = cell.getStringCellValue();
                                if ("".equals(value))
                                {
                                    value = null;
                                }
                                formattedValue = cell.getStringCellValue();
                        }

                        if (extended)
                        {
                            metadataMap.put("value", value);
                            if (formatString != null && !"".equals(formatString))
                            {
                                metadataMap.put("formatString", formatString);
                            }
                            metadataMap.put("formattedValue", formattedValue);
                            rowArray.put(metadataMap);
                        }
                        else
                        {
                            rowArray.put(value);
                        }
                    }
                }
                rowsArray.put(rowArray);
            }
            JSONObject sheetJSON = new JSONObject();
            sheetJSON.put("name", workbook.getSheetName(sheetIndex));
            sheetJSON.put("data", rowsArray);
            result.put(sheetJSON);
        }
        return result;
    }

    public static String getCellContentsAt(Sheet sheet, int colIdx, int rowIdx)
    {
        return getCellStringValue(getCell(sheet, colIdx, rowIdx));
    }

    public static class ExcelFactoryTestCase extends Assert
    {
        private static final double DELTA = 1E-8;

        @Test
        public void testCreateFromArray() throws IOException
        {
            /* Initialize stream */
            ByteArrayOutputStream os = new ByteArrayOutputStream();

            String source = "{" +
                    "fileName: 'output.xls'," +
                    "sheets  : [" +
                        "{" +
                            "name : 'FirstSheet'," +
                            "data : [" +
                                "['Row1Col1', 'Row1Col2']," +
                                "['Row2Col1', 'Row2Col2']" +
                            "]" +
                        "},{" +
                            "name : 'SecondSheet'," +
                            "data : [" +
                                "['Col1Header', 'Col2Header']," +
                                "[{value: 1000.5, formatString: '0,000.00'}, {value: '5 Mar 2009 05:14:17', formatString: 'yyyy MMM dd'}]," +
                                "[{value: 2000.6, formatString: '0,000.00'}, {value: '6 Mar 2009 07:17:10', formatString: 'yyyy MMM dd'}]" +
                            "]" +
                        "}" +
                    "]" +
            "}";

            /* Initialize JSON - see LABKEY.Utils.convertToExcel */
            JSONObject root      = new JSONObject(source);
            JSONArray sheetArray = root.getJSONArray("sheets");

            try (Workbook wb = ExcelFactory.createFromArray(sheetArray, ExcelWriter.ExcelDocumentType.xls))
            {
                wb.write(os);

                Sheet sheet = wb.getSheet("FirstSheet");
                assertNotNull(sheet);
                Cell cell = sheet.getRow(0).getCell(0);
                assertEquals("Row1Col1", cell.getStringCellValue());
                cell = sheet.getRow(1).getCell(1);
                assertEquals("Row2Col2", cell.getStringCellValue());

                // Validate equaility with '5 Mar 2009 05:14:17'
                sheet = wb.getSheet("SecondSheet");
                cell = sheet.getRow(1).getCell(1);
                Calendar cal = new GregorianCalendar();
                cal.setTime(cell.getDateCellValue());
                assertEquals(cal.get(Calendar.DATE), 5);
                assertEquals(cal.get(Calendar.MONTH), Calendar.MARCH);
                assertEquals(cal.get(Calendar.YEAR), 2009);
                assertEquals(cal.get(Calendar.HOUR), 5);
                assertEquals(cal.get(Calendar.MINUTE), 14);
                assertEquals(cal.get(Calendar.SECOND), 17);

                // Now make sure that it round-trips back to JSON correctly
                JSONArray array = convertExcelToJSON(wb, true);
                assertEquals(2, array.length());

                JSONObject sheet1JSON = array.getJSONObject(0);
                assertEquals("FirstSheet", sheet1JSON.getString("name"));
                JSONArray sheet1Values = sheet1JSON.getJSONArray("data");
                assertEquals("Wrong number of rows", 2, sheet1Values.length());
                assertEquals("Wrong number of columns", 2, sheet1Values.getJSONArray(0).length());
                assertEquals("Wrong number of columns", 2, sheet1Values.getJSONArray(1).length());
                assertEquals("Row1Col1", sheet1Values.getJSONArray(0).getJSONObject(0).getString("value"));
                assertEquals("Row1Col2", sheet1Values.getJSONArray(0).getJSONObject(1).getString("value"));
                assertEquals("Row2Col1", sheet1Values.getJSONArray(1).getJSONObject(0).getString("value"));
                assertEquals("Row2Col2", sheet1Values.getJSONArray(1).getJSONObject(1).getString("value"));

                JSONObject sheet2JSON = array.getJSONObject(1);
                assertEquals("SecondSheet", sheet2JSON.getString("name"));
                JSONArray sheet2Values = sheet2JSON.getJSONArray("data");
                assertEquals("Wrong number of rows", 3, sheet2Values.length());
                assertEquals("Wrong number of columns in row 0", 2, sheet2Values.getJSONArray(0).length());
                assertEquals("Wrong number of columns in row 1", 2, sheet2Values.getJSONArray(1).length());
                assertEquals("Wrong number of columns in row 2", 2, sheet2Values.getJSONArray(2).length());
                assertEquals("Col1Header", sheet2Values.getJSONArray(0).getJSONObject(0).getString("value"));
                assertEquals("Col2Header", sheet2Values.getJSONArray(0).getJSONObject(1).getString("value"));

                assertEquals(1000.5, sheet2Values.getJSONArray(1).getJSONObject(0).getDouble("value"), DELTA);
                assertEquals("1,000.50", sheet2Values.getJSONArray(1).getJSONObject(0).getString("formattedValue"));
                assertEquals("0,000.00", sheet2Values.getJSONArray(1).getJSONObject(0).getString("formatString"));

                assertEquals(2000.6, sheet2Values.getJSONArray(2).getJSONObject(0).getDouble("value"), DELTA);
                assertEquals("2,000.60", sheet2Values.getJSONArray(2).getJSONObject(0).getString("formattedValue"));
                assertEquals("0,000.00", sheet2Values.getJSONArray(2).getJSONObject(0).getString("formatString"));

//            assertEquals("Thu Mar 05 05:14:17 PST 2009", sheet2Values.getJSONArray(1).getJSONObject(1).getString("value"));
                assertEquals("2009 Mar 05", sheet2Values.getJSONArray(1).getJSONObject(1).getString("formattedValue"));
                assertEquals("yyyy MMM dd", sheet2Values.getJSONArray(1).getJSONObject(1).getString("formatString"));

//            assertEquals("Fri Mar 06 07:17:10 PST 2009", sheet2Values.getJSONArray(2).getJSONObject(1).getString("value"));
                assertEquals("2009 Mar 06", sheet2Values.getJSONArray(2).getJSONObject(1).getString("formattedValue"));
                assertEquals("yyyy MMM dd", sheet2Values.getJSONArray(2).getJSONObject(1).getString("formatString"));
            }
        }

        @Test
        public void testParseXLS() throws Exception
        {
            validateSimpleExcel("SimpleExcelFile.xls");
        }

        @Test
        public void testParseXLSX() throws Exception
        {
            validateSimpleExcel("SimpleExcelFile.xlsx");
        }

        @Test
        //Issue 15478: IllegalStateException from org.labkey.api.reader.ExcelFactory.getCellStringValue
        public void testExcelFileImportShouldSucceed()  throws Exception
        {
            JSONArray jsonArray = startImportFile("Formulas.xlsx");
            if(jsonArray == null && !AppProps.getInstance().isDevMode())
                return; // test requires dev mode
            try
            {
                assertEquals("#VALUE!", jsonArray.getJSONObject(0).getJSONArray("data").getJSONArray(1).getJSONObject(0).getString("value"));
            }
            catch (NullPointerException | JSONException e)
            {
                throw new RuntimeException("Bad import response: \n" + jsonArray, e);
            }
        }

        @Test
        public void testExcelFileImportShouldFail() throws Exception
        {
            File dataloading = JunitUtil.getSampleData(null, "dataLoading/excel");

            if (null == dataloading)
                return;

            attemptImportExpectError(new File(dataloading, "doesntexist.xls"), FileNotFoundException.class);
            attemptImportExpectError(new File(dataloading, ""), FileNotFoundException.class);
            attemptImportExpectError(new File(dataloading, "notreallyexcel.xls"), InvalidFormatException.class);
        }

        private void attemptImportExpectError(File excelFile, Class exceptionClass)
        {
            try
            {
                JSONArray jsonArray = ExcelFactory.convertExcelToJSON(excelFile, true);
                if(jsonArray == null && !AppProps.getInstance().isDevMode())
                    return; // test requires dev mode
                fail("Should have failed before this point");
            }
            catch(Exception e)
            {
                assertEquals("Unexpected exception type", exceptionClass, e.getClass());
            }
        }

        @Test
        public void testColumnDescription()
        {
            assertEquals("A", getCellColumnDescription(0));
            assertEquals("Z", getCellColumnDescription(25));
            assertEquals("AA", getCellColumnDescription(26));
            assertEquals("AB", getCellColumnDescription(27));
            assertEquals("ZZ", getCellColumnDescription(701));
            assertEquals("AAA", getCellColumnDescription(702));
            assertEquals("ABC", getCellColumnDescription(730));
            assertEquals("ABC", getCellColumnDescription(730));
            assertEquals("IRS", getCellColumnDescription(6570));
            assertEquals("SIR", getCellColumnDescription(13095));
        }

        private JSONArray startImportFile(String filename) throws Exception
        {
            File excelFile = JunitUtil.getSampleData(null, "dataLoading/excel/" + filename);

            return null != excelFile ? ExcelFactory.convertExcelToJSON(excelFile, true) : null;
        }

        private void validateSimpleExcel(String filename) throws Exception
        {
            JSONArray jsonArray = startImportFile(filename);
            if(jsonArray == null)
                return;
            assertEquals("Wrong number of sheets", 3, jsonArray.length());
            JSONObject sheet1 = jsonArray.getJSONObject(0);
            assertEquals("Sheet name", "SheetA", sheet1.getString("name"));
            JSONArray sheet1Rows = sheet1.getJSONArray("data");
            assertEquals("Number of rows", 4, sheet1Rows.length());
            assertEquals("Number of columns - row 0", 2, sheet1Rows.getJSONArray(0).length());
            assertEquals("Number of columns - row 1", 2, sheet1Rows.getJSONArray(1).length());
            assertEquals("Number of columns - row 2", 2, sheet1Rows.getJSONArray(2).length());
            assertEquals("Number of columns - row 3", 2, sheet1Rows.getJSONArray(3).length());

            assertEquals("StringColumn", sheet1Rows.getJSONArray(0).getJSONObject(0).getString("value"));
            assertEquals("Hello", sheet1Rows.getJSONArray(1).getJSONObject(0).getString("value"));
            assertEquals("world", sheet1Rows.getJSONArray(2).getJSONObject(0).getString("value"));
            assertNull(sheet1Rows.getJSONArray(3).getJSONObject(0).optString("value", null));

            assertEquals("DateColumn", sheet1Rows.getJSONArray(0).getJSONObject(1).getString("value"));
            assertEquals("May 17, 2009", sheet1Rows.getJSONArray(1).getJSONObject(1).getString("formattedValue"));
            assertEquals("MMMM d, yyyy", sheet1Rows.getJSONArray(1).getJSONObject(1).getString("formatString"));
            assertEquals("12/21/08 7:31 PM", sheet1Rows.getJSONArray(2).getJSONObject(1).getString("formattedValue"));
            assertEquals("M/d/yy h:mm a", sheet1Rows.getJSONArray(2).getJSONObject(1).getString("formatString"));
            assertEquals("8:45 AM", sheet1Rows.getJSONArray(3).getJSONObject(1).getString("formattedValue"));
            assertEquals("h:mm a", sheet1Rows.getJSONArray(3).getJSONObject(1).getString("formatString"));

            JSONObject sheet2 = jsonArray.getJSONObject(1);
            assertEquals("Sheet name", "Other Sheet", sheet2.getString("name"));
            JSONArray sheet2Rows = sheet2.getJSONArray("data");
            assertEquals("Number of rows", 8, sheet2Rows.length());
            assertEquals("Number of columns - row 0", sheet2Rows.getJSONArray(0).length(), 1);

            assertEquals("NumberColumn", sheet2Rows.getJSONArray(0).getJSONObject(0).getString("value"));
            assertEquals(55.44, sheet2Rows.getJSONArray(1).getJSONObject(0).getDouble("value"), DELTA);
            assertEquals("$55.44", sheet2Rows.getJSONArray(1).getJSONObject(0).getString("formattedValue"));
            assertEquals("$#,##0.00", sheet2Rows.getJSONArray(1).getJSONObject(0).getString("formatString"));
            assertEquals(100.34, sheet2Rows.getJSONArray(2).getJSONObject(0).getDouble("value"), DELTA);
            assertEquals("100.34", sheet2Rows.getJSONArray(2).getJSONObject(0).getString("formattedValue"));
            assertEquals(-1.0, sheet2Rows.getJSONArray(3).getJSONObject(0).getDouble("value"), DELTA);
            assertEquals("-1", sheet2Rows.getJSONArray(3).getJSONObject(0).getString("formattedValue"));
            assertEquals("61.00", sheet2Rows.getJSONArray(4).getJSONObject(0).getString("formattedValue"));
            assertEquals("56+5", sheet2Rows.getJSONArray(4).getJSONObject(0).getString("formula"));
            assertEquals("0.00", sheet2Rows.getJSONArray(4).getJSONObject(0).getString("formatString"));
            assertEquals("jeckels:\nA comment about the value 61\n", sheet2Rows.getJSONArray(4).getJSONObject(0).getString("comment"));
            assertEquals("#DIV/0!", sheet2Rows.getJSONArray(5).getJSONObject(0).getString("value"));
            assertTrue(sheet2Rows.getJSONArray(5).getJSONObject(0).getBoolean("error"));
            assertEquals(4.325E-4, sheet2Rows.getJSONArray(6).getJSONObject(0).getDouble("value"), DELTA);
            assertEquals("4.32E-04", sheet2Rows.getJSONArray(6).getJSONObject(0).getString("formattedValue"));
            assertEquals(2550.00064, sheet2Rows.getJSONArray(7).getJSONObject(0).getDouble("value"), DELTA);
            assertEquals("2.55E+03", sheet2Rows.getJSONArray(7).getJSONObject(0).getString("formattedValue"));
        }
    }
}

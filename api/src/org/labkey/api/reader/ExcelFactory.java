/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
import org.apache.poi.hssf.OldExcelFormatException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.format.CellGeneralFormatter;
import org.apache.poi.ss.formula.FormulaParseException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
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
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.JunitUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper that abstracts the different versions of Excel files and returns the implementation that can
 * parse or create them.
 * User: klum
 * Date: May 2, 2011
 */
public class ExcelFactory
{
    public static final String SUB_TYPE_XSSF = "vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String SUB_TYPE_BIFF5 = "x-tika-msoffice";
    public static final String SUB_TYPE_BIFF8 = "vnd.ms-excel";

    /**
     * Sniffs the file type and returns the appropriate parser
     * @throws InvalidFormatException if the file isn't a recognized Excel format
     */
    @NotNull
    public static Workbook create(@NotNull File file) throws IOException, InvalidFormatException
    {
        FileInputStream fIn = new FileInputStream(file);
        try
        {
            return WorkbookFactory.create(fIn);
        }
        catch (OldExcelFormatException e)
        {
            try { fIn.close(); } catch (IOException ignored) {}
            fIn = new FileInputStream(file);
            return new JxlWorkbook(fIn);
        }
        catch (IllegalArgumentException e)
        {
            throw new InvalidFormatException("Unable to open file as an Excel document. " + (e.getMessage() == null ? "" : e.getMessage()));
        }
        finally
        {
            try { fIn.close(); } catch (IOException ignored) {}
        }
    }

    /** Creates a temp file from the InputStream, then attempts to parse using the new and old formats. */
    public static Workbook create(InputStream is) throws IOException, InvalidFormatException
    {

        File temp = File.createTempFile("excel", "tmp");
        try
        {
            FileUtil.copyData(is, temp);
            return create(temp);
        }
        finally
        {
            temp.delete();
        }
/*
        DefaultDetector detector = new DefaultDetector();
        MediaType type = detector.detect(TikaInputStream.get(dataFile), new Metadata());

        if (SUB_TYPE_BIFF5.equals(type.getSubtype()))
            return new JxlWorkbook(dataFile);
        else
            return WorkbookFactory.create(new FileInputStream(dataFile));
*/
    }

    /**
     * Contructs an in-memory Excel file from a JSON representation, as described in the LABKEY.Utils.convertToExcel JavaScript API
     */
    public static Workbook createFromArray(JSONArray sheetsArray, ExcelWriter.ExcelDocumentType docType) throws IOException
    {
        Workbook workbook = docType.createWorkbook();

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
                    Object value = rowArray.get(colIndex);
                    JSONObject metadataObject = null;
                    CellStyle cellStyle = defaultStyle;
                    if (value instanceof JSONObject)
                    {
                        metadataObject = (JSONObject)value;
                        value = metadataObject.get("value");
                    }

                    boolean forceString = metadataObject != null && metadataObject.has("forceString") && Boolean.TRUE.equals(metadataObject.get("forceString"));

                    Cell cell = row.createCell(colIndex);
                    if (value instanceof java.lang.Number)
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
                    case Cell.CELL_TYPE_BOOLEAN:
                        return formatter.format(cell.getBooleanCellValue());
                    case Cell.CELL_TYPE_NUMERIC:
                        return formatter.format(cell.getNumericCellValue());
                    case Cell.CELL_TYPE_FORMULA:
                    {
                        if (cell.getCachedFormulaResultType() == Cell.CELL_TYPE_STRING)
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
                    case Cell.CELL_TYPE_ERROR:
                        return ((Byte)cell.getErrorCellValue()).toString();
                }
                return cell.getStringCellValue();
            }
            else if (isCellNumeric(cell) && DateUtil.isCellDateFormatted(cell) && cell.getDateCellValue() != null)
                return formatter.format(cell.getDateCellValue());
            else if (cell.getCellType() == Cell.CELL_TYPE_FORMULA && cell.getCachedFormulaResultType() == Cell.CELL_TYPE_STRING)
                return cell.getStringCellValue();
            else
            {
                // This seems to be the best way to get the value that's shown in Excel
                // http://stackoverflow.com/questions/1072561/how-can-i-read-numeric-strings-in-excel-cells-as-string-not-numbers-with-apach
                Workbook wb = cell.getSheet().getWorkbook();
                FormulaEvaluator evaluator = createFormulaEvaluator(wb);
                return new DataFormatter().formatCellValue(cell, evaluator);
            }
        }
        return "";
    }

    public static boolean isCellNumeric(Cell cell)
    {
        if (cell != null)
        {
            int type = cell.getCellType();
            if (type == Cell.CELL_TYPE_FORMULA)
            {
                type = cell.getCachedFormulaResultType();
            }
            return type == Cell.CELL_TYPE_NUMERIC;
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
        return convertExcelToJSON(WorkbookFactory.create(in), extended, -1);
    }

    /** Supports both new and old style .xls (BIFF5 and BIFF8), and .xlsx because we can reopen the stream if needed */
    public static JSONArray convertExcelToJSON(File excelFile, boolean extended) throws IOException, InvalidFormatException
    {
        return convertExcelToJSON(ExcelFactory.create(excelFile), extended, -1);
    }

    public static JSONArray convertExcelToJSON(File excelFile, boolean extended, int maxRows) throws IOException, InvalidFormatException
    {
        return convertExcelToJSON(ExcelFactory.create(excelFile), extended, maxRows);
    }

    /** Supports .xls (BIFF8 only) and .xlsx */
    public static JSONArray convertExcelToJSON(Workbook workbook, boolean extended) throws IOException
    {
        return convertExcelToJSON(workbook, extended, -1);
    }

    public static JSONArray convertExcelToJSON(Workbook workbook, boolean extended, int maxRows) throws IOException
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
                        catch (NullPointerException ignored) {}

                        if ("General".equalsIgnoreCase(formatString))
                        {
                            formatString = null;
                        }

                        int effectiveCellType = cell.getCellType();
                        if (effectiveCellType == Cell.CELL_TYPE_FORMULA)
                        {
                            effectiveCellType = cell.getCachedFormulaResultType();
                            metadataMap.put("formula", cell.getCellFormula());
                        }

                        switch (effectiveCellType)
                        {
                            case Cell.CELL_TYPE_NUMERIC:
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

                            case Cell.CELL_TYPE_BOOLEAN:
                                value = cell.getBooleanCellValue();
                                formattedValue = value == null ? null : value.toString();
                                break;

                            case Cell.CELL_TYPE_ERROR:
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
        public void testCreateFromArray() throws IOException, InvalidFormatException
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

            Workbook wb = ExcelFactory.createFromArray(sheetArray, ExcelWriter.ExcelDocumentType.xls);
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
            assertEquals(null, sheet1Rows.getJSONArray(3).getJSONObject(0).getString("value"));

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

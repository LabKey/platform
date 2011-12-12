/*
 * Copyright (c) 2011 LabKey Corporation
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

import jxl.WorkbookSettings;
import jxl.write.DateFormat;
import jxl.write.DateTime;
import jxl.write.Label;
import jxl.write.NumberFormat;
import jxl.write.WritableCell;
import jxl.write.WritableCellFormat;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
import org.apache.poi.hssf.OldExcelFormatException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.format.CellFormat;
import org.apache.poi.ss.format.CellGeneralFormatter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.reader.jxl.JxlWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * User: klum
 * Date: May 2, 2011
 * Time: 6:24:37 PM
 */
public class ExcelFactory
{
    public static final String SUB_TYPE_XSSF = "vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String SUB_TYPE_BIFF5 = "x-tika-msoffice";
    public static final String SUB_TYPE_BIFF8 = "vnd.ms-excel";

    public static Workbook create(File dataFile) throws IOException, InvalidFormatException
    {
        try
        {
            return WorkbookFactory.create(new FileInputStream(dataFile));
        }
        catch (OldExcelFormatException e)
        {
            return new JxlWorkbook(dataFile);
        }
        catch (IllegalArgumentException e)
        {
            throw new InvalidFormatException("Unable to open file as an Excel document. " + e.getMessage() == null ? "" : e.getMessage());
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

    // TODO: Convert this to return a workbook that can be either jxl OR poi
    public static WritableWorkbook createFromArray(OutputStream os, JSONArray sheetsArray) throws IOException, WriteException
    {
        WorkbookSettings settings = new WorkbookSettings();
        settings.setArrayGrowSize(300000);
        SimpleDateFormat dateFormat = new SimpleDateFormat(JSONObject.JAVASCRIPT_DATE_FORMAT);
        WritableWorkbook workbook = jxl.Workbook.createWorkbook(os, settings);

        for (int sheetIndex = 0; sheetIndex < sheetsArray.length(); sheetIndex++)
        {
            JSONObject sheetObject = sheetsArray.getJSONObject(sheetIndex);
            String sheetName = sheetObject.has("name") ? sheetObject.getString("name") : "Sheet" + sheetIndex;
            sheetName = ExcelWriter.cleanSheetName(sheetName);
            WritableSheet sheet = workbook.createSheet(sheetName, sheetIndex);

            WritableCellFormat defaultFormat = new WritableCellFormat();
            WritableCellFormat defaultDateFormat = new WritableCellFormat(new DateFormat(org.labkey.api.util.DateUtil.getStandardDateFormatString()));
            WritableCellFormat errorFormat = new WritableCellFormat();
            errorFormat.setBackground(jxl.format.Colour.RED);

            JSONArray rowsArray = sheetObject.getJSONArray("data");
            for (int rowIndex = 0; rowIndex < rowsArray.length(); rowIndex++)
            {
                JSONArray rowArray = rowsArray.getJSONArray(rowIndex);
                for (int colIndex = 0; colIndex < rowArray.length(); colIndex++)
                {
                    Object value = rowArray.get(colIndex);
                    WritableCell cell = null;
                    JSONObject metadataObject = null;
                    WritableCellFormat cellFormat = defaultFormat;
                    if (value instanceof JSONObject)
                    {
                        metadataObject = (JSONObject)value;
                        value = metadataObject.get("value");
                    }
                    if (value instanceof java.lang.Number)
                    {
                        cell = new jxl.write.Number(colIndex, rowIndex, ((java.lang.Number) value).doubleValue());
                        if (metadataObject != null && metadataObject.has("formatString"))
                        {
                            cellFormat = new WritableCellFormat(new NumberFormat(metadataObject.getString("formatString")));
                        }
                    }
                    else if (value instanceof Boolean)
                    {
                        cell = new jxl.write.Boolean(colIndex, rowIndex, ((Boolean) value).booleanValue());
                    }
                    else if (value instanceof String)
                    {
                        try
                        {
                            // JSON has no date literal syntax so try to parse all Strings as dates
                            Date d = dateFormat.parse((String)value);
                            try
                            {
                                if (metadataObject != null && metadataObject.has("formatString"))
                                {
                                    cellFormat = new WritableCellFormat(new DateFormat(metadataObject.getString("formatString")));
                                }
                                else
                                {
                                    cellFormat = defaultDateFormat;
                                }
                                boolean timeOnly = metadataObject != null && metadataObject.has("timeOnly") && Boolean.TRUE.equals(metadataObject.get("timeOnly"));
                                cell = new DateTime(colIndex, rowIndex, d, cellFormat, timeOnly);
                            }
                            catch (IllegalArgumentException e)
                            {
                                // Invalid date format
                                cellFormat = errorFormat;
                                cell = new Label(colIndex, rowIndex, e.getMessage());
                            }
                        }
                        catch (ParseException e)
                        {
                            // Not a date
                            cell = new Label(colIndex, rowIndex, (String)value);
                        }
                    }
                    else if (value != null)
                    {
                        cell = new Label(colIndex, rowIndex, value.toString());
                    }
                    if (cell != null)
                    {
                        cell.setCellFormat(cellFormat);
                        sheet.addCell(cell);
                    }
                }
            }
        }

        return workbook;
    }

    /**
     * Helper to safely convert cell values to a string equivalent
     *
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
                        Workbook wb = cell.getSheet().getWorkbook();
                        FormulaEvaluator evaluator = createFormulaEvaluator(wb);
                        if (evaluator != null)
                        {
                            String val = evaluator.evaluate(cell).formatAsString();
                            return val;
                        }
                        return "";
                    }
                }
                return cell.getStringCellValue();
            }
            else if (isCellNumeric(cell) && DateUtil.isCellDateFormatted(cell) && cell.getDateCellValue() != null)
                return formatter.format(cell.getDateCellValue());
            else
                return CellFormat.getInstance(cell.getCellStyle().getDataFormatString()).apply(cell).text;
        }
        return "";
    }

    public static boolean isCellNumeric(Cell cell)
    {
        if (cell != null)
        {
            int type = cell.getCellType();

            return type == Cell.CELL_TYPE_BLANK || type == Cell.CELL_TYPE_NUMERIC || type == Cell.CELL_TYPE_FORMULA;
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

    public static String getCellContentsAt(Sheet sheet, int colIdx, int rowIdx)
    {
        return getCellStringValue(getCell(sheet, colIdx, rowIdx));
    }

    public static class ExcelFactoryTestCase extends Assert
    {
        @Test
        public void testCreateFromArray()
        {
            /* Initialize stream */
            OutputStream os = new ByteArrayOutputStream();

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

            WritableWorkbook wb = null;
            try
            {
                wb = ExcelFactory.createFromArray(os, sheetArray);
                wb.write();
                wb.close();
            }
            catch (Exception e)
            {

            }

            WritableSheet sheet = wb.getSheet("FirstSheet");
            assertNotNull(sheet);
            jxl.Cell cell = sheet.getCell(0,0);
            assertEquals("Row1Col1", cell.getContents());
            cell = sheet.getCell(1,1);
            assertEquals("Row2Col2", cell.getContents());

            sheet = wb.getSheet("SecondSheet");
            cell = sheet.getCell(1,1);
            assertEquals(jxl.write.DateTime.class, cell.getClass());
        }
    }
}

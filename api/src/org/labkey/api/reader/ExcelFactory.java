/*
 * Copyright (c) 2010 LabKey Corporation
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

import org.apache.poi.hssf.OldExcelFormatException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.reader.jxl.JxlWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: May 2, 2011
 * Time: 6:24:37 PM
 */
public class ExcelFactory
{
    private static final String SUB_TYPE_XSSF = "vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String SUB_TYPE_BIFF5 = "x-tika-msoffice";
    private static final String SUB_TYPE_BIFF8 = "vnd.ms-excel";

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
     * Helper to safely convert cell values to a string equivalent
     *
     */
    public static String getCellStringValue(Cell cell)
    {
        if (cell != null)
        {
            switch (cell.getCellType())
            {
                case Cell.CELL_TYPE_BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case Cell.CELL_TYPE_NUMERIC:
                    return String.valueOf(cell.getNumericCellValue());
            }
            return cell.getStringCellValue();
        }
        return "";
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
}

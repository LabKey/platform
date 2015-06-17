/*
 * Copyright (c) 2008-2015 LabKey Corporation
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

package org.labkey.api.study.assay.plate;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.DataLoaderFactory;
import org.labkey.api.reader.ExcelFactory;
import org.labkey.api.study.PlateTemplate;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * User: Karl Lum
 * Date: Jan 23, 2008
 */
public class ExcelPlateReader extends AbstractPlateReader implements PlateReader
{
    public static final String TYPE = "xls";
    
    public String getType()
    {
        return TYPE;
    }

    public double[][] loadFile(PlateTemplate template, File dataFile) throws ExperimentException
    {
        try
        {
            DataLoaderFactory factory = DataLoader.get().findFactory(dataFile, null);
            DataLoader loader = factory.createLoader(dataFile, false);

            return PlateUtils.parseGrid(dataFile, loader.load(), template.getRows(), template.getColumns(), this);
        }
        catch (IOException ioe)
        {
            throw new ExperimentException(ioe);
        }
    }

    @Override
    public Map<String, double[][]> loadMultiGridFile(PlateTemplate template, File dataFile) throws ExperimentException
    {
        try
        {
            DataLoaderFactory factory = DataLoader.get().findFactory(dataFile, null);
            DataLoader loader = factory.createLoader(dataFile, false);

            return PlateUtils.parseAllGrids(dataFile, loader.load(), template.getRows(), template.getColumns(), this);
        }
        catch (IOException ioe)
        {
            throw new ExperimentException(ioe);
        }
    }

    protected boolean isValidStartRow(Sheet sheet, int row)
    {
        Row sheetRow = sheet.getRow(row);
        if (sheetRow != null)
        {
            for (Cell cell : sheetRow)
            {
                if (cell.getCellType() == Cell.CELL_TYPE_STRING && StringUtils.equalsIgnoreCase(cell.getStringCellValue(), "A"))
                {
                    int col = cell.getColumnIndex();
                    char start = 'B';
                    for (int i=1; i < 8; i++)
                    {
                        String val = String.valueOf(start++);
                        Row r = sheet.getRow(row+i);
                        if (r != null)
                        {
                            Cell c = r.getCell(col);
                            if (c == null || c.getCellType() != Cell.CELL_TYPE_STRING || !StringUtils.equalsIgnoreCase(c.getStringCellValue(), val))
                                return false;
                        }
                        else
                            return false;
                    }
                    return true;
                }
            }
        }
        return false;
    }
}

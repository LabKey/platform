/*
 * Copyright (c) 2012-2026 LabKey Corporation
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

package org.labkey.api.assay.plate;

import org.apache.commons.lang3.Strings;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.DataLoaderFactory;
import org.labkey.vfs.FileLike;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * User: Karl Lum
 * Date: Jan 23, 2008
 */
public class ExcelPlateReader extends AbstractPlateReader implements PlateReader
{
    public static final String TYPE = "xls";

    double emptyWellValue = 0.0d;
    
    @Override
    public String getType()
    {
        return TYPE;
    }

    @Override
    public double[][] loadFile(Plate template, FileLike dataFile) throws ExperimentException
    {
        DataLoaderFactory factory = DataLoader.get().findFactory(dataFile, null);
        try (InputStream in = dataFile.openInputStream();
             DataLoader loader = factory.createLoader(in, false))
        {
            return PlateUtils.parseGrid(dataFile, loader.load(), template.getRows(), template.getColumns(), this);
        }
        catch (IOException ioe)
        {
            throw new ExperimentException(ioe);
        }
    }

    @Override
    public List<PlateUtils.GridInfo> loadMultiGridFile(Plate template, FileLike dataFile) throws ExperimentException
    {
        DataLoaderFactory factory = DataLoader.get().findFactory(dataFile, null);
        try (InputStream in = dataFile.openInputStream();
                DataLoader loader = factory.createLoader(in, false))
        {
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
                if (cell.getCellType() == CellType.STRING && Strings.CI.equals(cell.getStringCellValue(), "A"))
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
                            if (c == null || cell.getCellType() == CellType.STRING || !Strings.CI.equals(c.getStringCellValue(), val))
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

    @Override
    public double getEmptyWellValue()
    {
        return emptyWellValue;
    }

    public void setEmptyWellValue(double emptyWellValue)
    {
        this.emptyWellValue = emptyWellValue;
    }
}

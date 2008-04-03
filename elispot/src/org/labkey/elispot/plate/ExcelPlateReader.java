package org.labkey.elispot.plate;

import org.labkey.api.study.PlateTemplate;
import org.labkey.api.exp.ExperimentException;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;

import jxl.WorkbookSettings;
import jxl.Workbook;
import jxl.Sheet;
import jxl.Cell;
import jxl.read.biff.BiffException;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Jan 23, 2008
 */
public class ExcelPlateReader implements ElispotPlateReaderService.I
{
    public static final String TYPE = "xls";
    
    public String getType()
    {
        return TYPE;
    }

    public double[][] loadFile(PlateTemplate template, File dataFile) throws ExperimentException
    {
        String fileName = dataFile.getName().toLowerCase();
        if (!fileName.endsWith(".xls"))
            throw new ExperimentException("Unable to load data file: Invalid Format");

        WorkbookSettings settings = new WorkbookSettings();
        settings.setGCDisabled(true);
        Workbook workbook = null;
        try
        {
            workbook = Workbook.getWorkbook(dataFile, settings);
        }
        catch (IOException e)
        {
            throw new ExperimentException(dataFile.getName() + " does not appear to be a valid data file: " + e.getMessage(), e);
        }
        catch (BiffException e)
        {
            throw new ExperimentException(dataFile.getName() + " does not appear to be a valid data file: " + e.getMessage(), e);
        }
        double[][] cellValues = new double[template.getRows()][template.getColumns()];

        Sheet plateSheet = workbook.getSheet(0);

        int startRow = -1;
        int startCol = -1;

        for (int row = 0; row < plateSheet.getRows(); row++)
        {
            startCol = getStartColumn(plateSheet.getRow(row));
            if (startCol != -1)
            {
                startRow = getStartRow(plateSheet, row);
                break;
            }
        }

        if (startRow == -1 || startCol == -1)
        {
            throw new ExperimentException(dataFile.getName() + " does not appear to be a valid data file: unable to locate spot counts");
        }

        if (template.getRows() + startRow > plateSheet.getRows() || template.getColumns() + startCol > plateSheet.getColumns())
        {
            throw new ExperimentException(dataFile.getName() + " does not appear to be a valid data file: expected " +
                    (template.getRows() + startRow) + " rows and " + (template.getColumns() + startCol) + " columns, but found "+
                    plateSheet.getRows() + " rows and " + plateSheet.getColumns() + " columns.");
        }

        for (int row = 0; row < template.getRows(); row++)
        {
            for (int col = 0; col < template.getColumns(); col++)
            {
                Cell cell = plateSheet.getCell(col + startCol, row + startRow);
                String cellContents = cell.getContents();
                try
                {
                    cellValues[row][col] = Double.parseDouble(cellContents);
                }
                catch (NumberFormatException e)
                {
                    throw new ExperimentException(dataFile.getName() + " does not appear to be a valid data file: could not parse '" +
                            cellContents + "' as a number.", e);
                }
            }
        }
        return cellValues;
    }

    private static int getStartColumn(Cell[] row)
    {
        int col = 0;
        while (col < row.length)
        {
            if (StringUtils.equals(row[col].getContents(), "1"))
            {
                for (int i=1; i < 12; i++)
                {
                    if (!StringUtils.equals(row[col+i].getContents(), String.valueOf(1 + i)))
                        return -1;
                }
                return col;
            }
            col++;
        }
        return -1;
    }

    private static int getStartRow(Sheet sheet, int row)
    {
        while (row < sheet.getRows())
        {
            for (Cell cell : sheet.getRow(row))
            {
                if (StringUtils.equalsIgnoreCase(cell.getContents(), "A"))
                {
                    int col = cell.getColumn();
                    char start = 'B';
                    for (int i=1; i < 8; i++)
                    {
                        String val = String.valueOf(start++);
                        if (!StringUtils.equalsIgnoreCase(sheet.getRow(row+i)[col].getContents(), val))
                            return -1;
                    }
                    return row;
                }
            }
            row++;
        }
        return -1;
    }
}

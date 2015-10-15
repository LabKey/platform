/*
 * Copyright (c) 2015 LabKey Corporation
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
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.RowMap;
import org.labkey.api.query.ValidationException;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by klum on 3/11/2015.
 */
public class PlateUtils
{
    public static final Logger LOG = Logger.getLogger(PlateUtils.class);
    private static final int START_ROW = 6; //0 based, row 7 in the workshet
    private static final int START_COL = 0;

    public static final String DEFAULT_GRID_NAME = "dataGrid";

    /**
     * Search for a grid of numbers that has the expected number of rows and columns.
     * TODO: Find multiple plates in "RC121306.xls" while ignoring duplicate plate found in "20131218_0004.txt"
     */
    public static Map<String, double[][]> parseAllGrids(File dataFile, List<Map<String, Object>> rows, int expectedRows, int expectedCols, PlateReader reader)
    {
        return _parseGrids(dataFile, rows, expectedRows, expectedCols, true, reader);
    }

    /**
     * Search for a grid of numbers that has the expected number of rows and columns.
     * TODO: Find multiple plates in "RC121306.xls" while ignoring duplicate plate found in "20131218_0004.txt"
     */
    @Nullable
    public static double[][] parseGrid(File dataFile, List<Map<String, Object>> rows, int expectedRows, int expectedCols, @Nullable PlateReader reader)
    {
        Map<String, double[][]> gridMap = _parseGrids(dataFile, rows, expectedRows, expectedCols, false, reader);
        if (!gridMap.isEmpty())
        {
            assert gridMap.size() == 1 : "_parseGrids returned more than 1 grid matrix for the data file";
            return gridMap.values().iterator().next();
        }
        return null;
    }

    /**
     * Search for a grid of numbers that has the expected number of rows and columns.
     * TODO: Find multiple plates in "RC121306.xls" while ignoring duplicate plate found in "20131218_0004.txt"
     */
    private static Map<String, double[][]> _parseGrids(File dataFile, List<Map<String, Object>> rows, int expectedRows,
                                                       int expectedCols, boolean parseAllGrids, @Nullable PlateReader reader)
    {
        Map<String, double[][]> gridMap = new HashMap<>();
        double[][] matrix;

        // try to find the top-left cell of the plate
        for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++)
        {
            Map<String, Object> row = rows.get(rowIdx);
            RowMap<Object> rowMap = (RowMap<Object>)row;
            for (int colIdx = 0; colIdx < rowMap.size(); colIdx++)
            {
                Object value = rowMap.get(colIdx);

                // For Luc5, EnVision, and "16AUG11 KK CD3-1-1.8." plate formats:
                // look for labeled matrix with a header row (numbered 1, 2, 3...) and header column (lettered A, B, C...)
                Location loc = isPlateMatrix(rows, rowIdx, colIdx, expectedRows, expectedCols, reader);
                if (loc != null)
                {
                    matrix = parseGridAt(rows, loc.getRow(), loc.getCol(), expectedRows, expectedCols, reader);

                    if (matrix != null)
                    {
                        LOG.debug(String.format("found labeled grid style plate data at (%d,%d) in %s", rowIdx+1, colIdx+1, dataFile.getName()));
                        gridMap.put(getGridAnnotation(rows, loc.getRow() - 1), matrix);
                        if (!parseAllGrids)
                            return gridMap;
                        rowIdx += expectedRows;
                    }
                }
                else if (value instanceof String)
                {
                    if (colIdx == 0 && ((String)value).startsWith("Plate:"))
                    {
                        // CONSIDER: Detecting SpectraMax format seems fragile.  Create SpectraMax specific PlateReader parser that can be chosen in the assay design ala Elispot.
                        // For SpectraMax format: look for a matrix grid at rowIdx+2, colIdx+2
                        loc = new Location(rowIdx+2, colIdx+2);
                        matrix = parseGridAt(rows, loc.getRow(), loc.getCol(), expectedRows, expectedCols, reader);
                        if (matrix != null)
                        {
                            LOG.debug(String.format("found SpectraMax grid style plate data at (%d,%d) in %s", rowIdx+1, colIdx+1, dataFile.getName()));
                            gridMap.put(getGridAnnotation(rows, loc.getRow() - 1), matrix);
                            if (!parseAllGrids)
                                return gridMap;
                            rowIdx += expectedRows;
                        }
                    }
                    // NOTE: Commented out since finding an abitrary grid hits too many false positives containing null cells.
//                    else if (NumberUtilsLabKey.isNumber((String) value))
//                    {
//                        // if the cell is a number, attempt to find a grid of numbers at the location
//                        matrix = parseGridAt(rows, rowIdx, colIdx, expectedRows, expectedCols);
//                        if (matrix != null)
//                        {
//                            LOG.debug(String.format("found grid style plate data at (%d,%d) in %s", rowIdx+1, colIdx+1, dataFile.getName()));
//                            return matrix;
//                        }
//                    }
                }
            }
        }

        // attempt to parse a grid at the "well known" location (pun intended)
        if (gridMap.isEmpty())
        {
            matrix = parseGridAt(rows, START_ROW, START_COL, expectedRows, expectedCols, reader);
            if (matrix != null)
            {
                gridMap.put(DEFAULT_GRID_NAME, matrix);
            }
            else
            {
                // attempt to parse as grid at 0,0
                matrix = parseGridAt(rows, 0, 0, expectedRows, expectedCols, reader);
                if (matrix != null)
                    gridMap.put(DEFAULT_GRID_NAME, matrix);
            }
        }
        return gridMap;
    }

    /**
     * Look for a grid of numbers (or blank) staring from rowIdx, colIdx and matches the expected size.
     * The startRow may be a header row (numbered 1..12)
     */
    public static double[][] parseGridAt(List<Map<String, Object>> rows, int startRow, int startCol, int expectedRows, int expectedCols, @Nullable PlateReader reader)
    {
        // Ensure there are enough available rows from startRow
        if (startRow + expectedRows > rows.size())
            return null;

        ArrayList<double[]> values = new ArrayList<>(expectedRows);

        for (int i = 0; i < expectedRows; i++)
        {
            Map<String, Object> row = rows.get(startRow + i);
            RowMap<Object> rowMap = (RowMap<Object>)row;

            double[] cells = parseRowAt(rowMap, startCol, expectedCols, reader);
            if (cells == null)
                return null;

            values.add(cells);
        }

        // Check if this is a header row (numbered 1 through 12)
        // If it is, shift down one row and attempt to parse that.
        // If there is no header row or adding an additional row of numbers fails, accept the data as is.
        if (isSequentialNumbers(values.get(0)))
        {
            if (startRow + expectedRows + 1 <= rows.size())
            {
                Map<String, Object> row = rows.get(startRow + expectedRows + 1);
                RowMap<Object> rowMap = (RowMap<Object>)row;

                double[] cells = parseRowAt(rowMap, startCol, expectedCols, reader);
                if (cells != null)
                {
                    // CONSIDER: Only accept this new row if it has non-null values?
                    values = new ArrayList<>(values.subList(1, values.size()));
                    values.add(cells);
                }
            }
        }

        return values.toArray(new double[values.size()][]);
    }

    /**
     * Look for a matrix that has numbers 1..12 header row and A..H header column
     *
     * <pre>
     *    1  2  3  ... 12
     * A  .  .  .      .
     * B  .  .  .      .
     * C  .  .  .      .
     * ...
     * H  .  .  .      .
     * </pre>
     *
     * @return the Location of the upper left hand corner of the start of the data matrix
     */
    @Nullable
    public static Location isPlateMatrix(List<Map<String, Object>> rows, int startRow, int startCol, int expectedRows, int expectedCols, @Nullable PlateReader reader)
    {
        Map<String, Object> row = rows.get(startRow);
        RowMap<Object> rowMap = (RowMap<Object>)row;

        // make sure that there are plate_width + 1 cells to the right of startCol:
        if (startCol + expectedCols + 1 > rowMap.size())
            return null;

        // make sure that there are plate_height + 1 cells below startRow
        if (startRow + expectedRows + 1 > rows.size())
            return null;

        // check for 1-12 in the row:
        double[] headerRow = parseRowAt(rowMap, startCol+1, expectedCols, reader);
        if (headerRow == null)
            return null;

        if (!isSequentialNumbers(headerRow))
            return null;

        // check for A-H in the startRow+1
        char start = 'A';
        int rowOffset = 1;
        for (int rowIndex = startRow + 1; rowIndex < startRow + expectedRows + 1; rowIndex++)
        {
            Map<String, Object> currentRow = rows.get(rowIndex);
            RowMap<Object> currentRowMap = (RowMap<Object>)currentRow;
            Object cell = currentRowMap.get(startCol);
            if (cell != null)
            {
                String indexString = String.valueOf(start++);
                if (!StringUtils.equals(String.valueOf(cell), indexString))
                    return null;
            }
            else
            {
                // some formats have additional space between the row header and the start of
                // the row data
                rowOffset++;
            }
        }

        return new Location(startRow+rowOffset, startCol+1);
    }

    public static double[] parseRowAt(RowMap<Object> rowMap, int startCol, int expectedCols, @Nullable PlateReader reader)
    {
        // Ensure there are enough available columns from startCol
        if (startCol + expectedCols > rowMap.size())
            return null;

        double[] cells = new double[expectedCols];

        for (int j = 0; j < expectedCols; j++)
        {
            // Get the value at the location and convert a double if possible.
            // If the value is not null or a number, stop parsing.
            Object value = rowMap.get(startCol + j);
            if (value == null)
                cells[j] = 0.0d;
            else if (value instanceof String)
            {
                try
                {
                    if (reader != null)
                        cells[j] = reader.convertWellValue((String)value);
                    else
                        cells[j] = Double.parseDouble((String) value);
                }
                catch (ValidationException | NumberFormatException e)
                {
                    // failed
                    return null;
                }
            }
            else if (value instanceof Number)
            {
                cells[j] = ((Number)value).doubleValue();
            }
            else
                return null;
        }

        return cells;
    }

    // Check if all values in first row are numbered sequentially starting at 1
    public static boolean isSequentialNumbers(double[] row)
    {
        for (int i = 0; i < row.length; i++)
        {
            if (row[i] != i+1)
                return false;
        }

        return true;
    }

    /**
     * Tries to look for an identifying annotation to associate with the grid matrix, useful for data files that
     * contain data for multiple measurements (Fluorospot). Fairly simplistic, just searches the row above the row header
     * for any string values.
     *
     * @return the annotation found, else DEFAULT_GRID_NAME
     */
    private static String getGridAnnotation(List<Map<String, Object>> rows, int dataRow)
    {
        if (dataRow > 1)
        {
            Map<String, Object> row = rows.get(dataRow-1);
            if (row instanceof RowMap)
            {
                RowMap<Object> rowMap = (RowMap<Object>)row;

                for (Object value : rowMap.values())
                {
                    if (value != null && value instanceof String)
                    {
                        return (String)value;
                    }
                }
            }
        }
        return DEFAULT_GRID_NAME;
    }

    public static class Location
    {
        private int _row;
        private int _col;

        public Location(int row, int col)
        {
            _row = row;
            _col = col;
        }

        public int getRow()
        {
            return _row;
        }

        public int getCol()
        {
            return _col;
        }
    }
}

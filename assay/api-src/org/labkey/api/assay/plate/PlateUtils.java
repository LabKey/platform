/*
 * Copyright (c) 2019 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.labkey.api.collections.RowMap;
import org.labkey.api.collections.RowMapFactory;
import org.labkey.api.query.ValidationException;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Created by klum on 3/11/2015.
 */
public class PlateUtils
{
    public static final Logger LOG = LogManager.getLogger(PlateUtils.class);
    private static final int START_ROW = 6; //0 based, row 7 in the workshet
    private static final int START_COL = 0;

    /**
     * Represents a rectangular matrix of data and optional text annotations associated with the data
     */
    public static class GridInfo
    {
        private List<String> _annotations;
        private double[][] _data;

        public GridInfo(double[][] data, List<String> annotations)
        {
            _data = data;
            _annotations = annotations;
        }

        public List<String> getAnnotations()
        {
            return _annotations;
        }

        public double[][] getData()
        {
            return _data;
        }
    }

    /**
     * Search for a grid of numbers that has the expected number of rows and columns.
     * TODO: Find multiple plates in "RC121306.xls" while ignoring duplicate plate found in "20131218_0004.txt"
     */
    public static List<GridInfo> parseAllGrids(File dataFile, List<Map<String, Object>> rows, int expectedRows, int expectedCols, PlateReader reader)
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
        List<GridInfo> grids = _parseGrids(dataFile, rows, expectedRows, expectedCols, false, reader);
        if (!grids.isEmpty())
        {
            assert grids.size() == 1 : "_parseGrids returned more than 1 grid matrix for the data file";
            return grids.get(0).getData();
        }
        return null;
    }

    /**
     * Search for a grid of numbers that has the expected number of rows and columns.
     * TODO: Find multiple plates in "RC121306.xls" while ignoring duplicate plate found in "20131218_0004.txt"
     */
    private static List<GridInfo> _parseGrids(
            File dataFile,
            List<Map<String, Object>> rows,
            int expectedRows,
            int expectedCols,
            boolean parseAllGrids,
            @Nullable PlateReader reader)
    {
        List<GridInfo> gridList = new ArrayList<>();
        double[][] matrix;
        int prevGridIdx = 0;

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
                        gridList.add(new GridInfo(matrix, parseGridAnnotations(rows, prevGridIdx, loc.getRow()-1)));
                        if (!parseAllGrids)
                            return gridList;
                        rowIdx += expectedRows;
                    }
                    prevGridIdx = loc.getRow() + expectedRows;
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
                            gridList.add(new GridInfo(matrix, parseGridAnnotations(rows, prevGridIdx, loc.getRow()-1)));
                            if (!parseAllGrids)
                                return gridList;
                            rowIdx += expectedRows;
                        }
                        prevGridIdx = loc.getRow() + expectedRows;
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
        if (gridList.isEmpty())
        {
            matrix = parseGridAt(rows, START_ROW, START_COL, expectedRows, expectedCols, reader);
            if (matrix != null)
            {
                gridList.add(new GridInfo(matrix, Collections.emptyList()));
            }
            else
            {
                // attempt to parse as grid at 0,0
                matrix = parseGridAt(rows, 0, 0, expectedRows, expectedCols, reader);
                if (matrix != null)
                    gridList.add(new GridInfo(matrix, Collections.emptyList()));
            }
        }
        return gridList;
    }

    /**
     * Tries to look for an identifying annotation to associate with the grid matrix, useful for data files that
     * contain data for multiple measurements (Fluorospot) or multiple plates.
     * Searches the rows above the row header for a string value plus the first cell of the dataRow.
     *
     * @return the annotations found, else an empty list
     */
    private static List<String> parseGridAnnotations(List<Map<String, Object>> rows, int startRow, int endRow)
    {
        List<String> annotations = new ArrayList<>();
        for (int i = startRow; i < endRow; i++)
        {
            Map<String, Object> row = rows.get(i);
            if (row instanceof RowMap<Object> rowMap)
            {
                List<Object> values = rowMap.values().stream().filter(o -> o instanceof String str && !str.isBlank()).toList();
                if (values.size() == 1 && values.get(0) instanceof String strValue)
                    annotations.add(strValue.trim());
            }
        }

        // also check cell 0 of the header row
        Map<String, Object> row = rows.get(endRow);
        if (row instanceof RowMap<Object> rowMap)
        {
            Object value = !rowMap.isEmpty() ? rowMap.get(0) : null;
            if (value instanceof String strValue && !strValue.isEmpty())
                annotations.add(strValue.trim());
        }

        return annotations;
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
     * Return a Location object from a location string : A1, A2, ... H11, H12
     */
    public static Location parseLocation(String description)
    {
        Position pos = new PositionImpl(null, description);
        return new Location(pos.getRow(), pos.getColumn()-1);
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

    public static final class TestCase
    {
        @Test
        public void testGetGridAnnotation()
        {
            RowMapFactory<Object> factory = new RowMapFactory<>(List.of("column0", "column1"));
            assertEquals("Zero grid annotations expected", 0, parseGridAnnotations(List.of(
                    factory.getRowMap(Map.of())), 0, 0).size());
            assertEquals("Zero grid annotations expected", 0, parseGridAnnotations(List.of(
                    factory.getRowMap(Map.of("column0", "", "column1", "1"))), 0, 0).size());

            assertEquals("Zero grid annotations expected", 0, parseGridAnnotations(List.of(
                    factory.getRowMap(Map.of("column0", "H", "column1", "1")),
                    factory.getRowMap(Map.of("column0", "", "column1", "1"))), 0, 1).size());

            List<String> annotations = parseGridAnnotations(List.of(
                    factory.getRowMap(Map.of("column0", "", "column1", "")),
                    factory.getRowMap(Map.of("column0", "Plate1", "column1", "1"))), 0, 1);
            assertEquals("One grid annotation expected", 1, annotations.size());
            assertEquals("Annotation was not found", "Plate1", annotations.get(0));

            annotations = parseGridAnnotations(List.of(
                    factory.getRowMap(Map.of("column0", "Plate1", "column1", "")),
                    factory.getRowMap(Map.of("column0", "", "column1", "1"))), 0, 1);
            assertEquals("One grid annotation expected", 1, annotations.size());
            assertEquals("Annotation was not found", "Plate1", annotations.get(0));

            annotations = parseGridAnnotations(List.of(
                    factory.getRowMap(Map.of("column0", "", "column1", "Plate1")),
                    factory.getRowMap(Map.of("column0", "", "column1", "1"))), 0, 1);
            assertEquals("One grid annotation expected", 1, annotations.size());
            assertEquals("Annotation was not found", "Plate1", annotations.get(0));

            annotations = parseGridAnnotations(List.of(
                    factory.getRowMap(Map.of("column0", "", "column1", "Measure")),
                    factory.getRowMap(Map.of("column0", "", "column1", "Plate1")),
                    factory.getRowMap(Map.of("column0", "", "column1", "1"))), 0, 2);
            assertEquals("Two grid annotation expected", 2, annotations.size());
            assertEquals("Annotation was not found", "Measure", annotations.get(0));
            assertEquals("Annotation was not found", "Plate1", annotations.get(1));

            annotations = parseGridAnnotations(List.of(
                    factory.getRowMap(Map.of("column0", "Measure", "column1", "")),
                    factory.getRowMap(Map.of("column0", "", "column1", "Plate1")),
                    factory.getRowMap(Map.of("column0", "", "column1", "1"))), 0, 2);
            assertEquals("Two grid annotation expected", 2, annotations.size());
            assertEquals("Annotation was not found", "Measure", annotations.get(0));
            assertEquals("Annotation was not found", "Plate1", annotations.get(1));
        }
    }
}

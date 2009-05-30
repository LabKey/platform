/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
package org.labkey.api.reader;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.labkey.api.data.MvUtil;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.CloseableIterator;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Data loader for Excel files -- can infer columns and return rows of data
 *
 * User: jgarms
 * Date: Oct 22, 2008
 */
public class ExcelLoader extends DataLoader<Map<String, Object>>
{
    private final Workbook workbook;

    private String sheetName;

    public ExcelLoader(File file) throws IOException
    {
        this(file, false);
    }

    public ExcelLoader(File file, boolean hasColumnHeaders) throws IOException
    {
        setHasColumnHeaders(hasColumnHeaders);
        setSource(file);

        try
        {
            WorkbookSettings ws = new WorkbookSettings();
            ws.setGCDisabled(true);
            workbook = Workbook.getWorkbook(file, ws);
        }
        catch (BiffException e)
        {
            throw new IOException(e.getMessage());
        }
    }

    public List<String> getSheetNames()
    {
        return Arrays.asList(workbook.getSheetNames());
    }

    public void setSheetName(String sheetName)
    {
        this.sheetName = sheetName;
    }

    private Sheet getSheet() throws IOException
    {
        try
        {
            if (sheetName != null)
                return workbook.getSheet(sheetName);
            else
                return workbook.getSheet(0);
        }
        catch (ArrayIndexOutOfBoundsException e)
        {
            throw new IOException("Invalid Excel file");
        }
    }

    public String[][] getFirstNLines(int n) throws IOException
    {
        Sheet sheet = getSheet();

        List<String[]> cells = new ArrayList<String[]>();
        int numCols = sheet.getColumns();
        int numRows = Math.min(sheet.getRows(), n);
        for (int row = 0; row < numRows; row++)
        {
            String[] rowData = new String[numCols];

            // Excel can report back more rows than exist. If we find no data at all,
            // we should not add a row.
            boolean foundData = false;
            for (int column = 0; column < numCols; column++)
            {
                Cell cell = sheet.getCell(column, row);
                String data = cell.getContents();
                if (data != null && !"".equals(data))
                    foundData = true;
                rowData[column] = cell.getContents();
            }
            if (foundData)
                cells.add(rowData);
        }
        return cells.toArray(new String[cells.size()][]);
    }

    public CloseableIterator<Map<String, Object>> iterator()
    {
        return new ExcelIterator();
    }

    public void finalize() throws Throwable
    {
        workbook.close();
        super.finalize();
    }

    public void close()
    {
        workbook.close();
    }

    private class ExcelIterator implements CloseableIterator<Map<String,Object>>
    {
        private final Sheet sheet;
        private final int numRows;
        private final int numCols;

        private int rowIndex;

        private Map<String, Object> nextRow;

        public ExcelIterator()
        {
            // find a converter for each column type
            for (ColumnDescriptor column : _columns)
                column.converter = ConvertUtils.lookup(column.clazz);

            try
            {
                sheet = getSheet();
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }

            numRows = sheet.getRows();
            numCols = sheet.getColumns();

            rowIndex = _skipLines == -1 ? 1 : _skipLines;

            nextRow = getNextRow();
        }

        public boolean hasNext()
        {
            return nextRow != null;
        }

        public Map<String, Object> next()
        {
            if (nextRow == null || rowIndex > numRows)
                throw new IllegalStateException("Attempt to call next() on a finished iterator");
            Map<String, Object> row = nextRow;
            nextRow = getNextRow();
            return row;
        }

        public Map<String, Object> getNextRow()
        {
            if (rowIndex >= numRows)
                return null;

            // If this row is blank, keep going until we either find a row or hit the end
            boolean foundData = false;
            Map<String, Object> row = new HashMap<String, Object>();

            for (int columnIndex = 0; columnIndex < _columns.length; columnIndex++)
            {
                ColumnDescriptor column = _columns[columnIndex];
                Object contents;

                if (columnIndex < numCols) // We can get asked for more data than we contain, as extra columns can exist
                {
                    Cell cell = sheet.getCell(columnIndex, rowIndex);
                    if (column.clazz.equals(String.class))
                    {
                        contents = cell.getContents();
                    }
                    else
                    {
                        contents = PropertyType.getFromExcelCell(cell);
                    }
                }
                else
                {
                    contents = "";
                }

                Object value = column.missingValues;

                try
                {
                    if (column.isMvEnabled())
                    {
                        MvFieldWrapper mvWrapper = (MvFieldWrapper)row.get(column.name);
                        if (mvWrapper != null)
                        {
                            // We have an indicator column and it placed a qc wrapper in here for us
                            mvWrapper.setValue(column.converter.convert(column.clazz, contents));
                            value = mvWrapper;
                        }
                        else
                        {
                            // Do we have an MV indicator column?
                            int mvIndicatorIndex = getMvIndicatorColumnIndex(column);
                            if (mvIndicatorIndex != -1)
                            {
                                // There is such a column, so this value had better be good.
                                mvWrapper = new MvFieldWrapper();
                                mvWrapper.setValue(column.converter.convert(column.clazz, contents));
                                value = mvWrapper;
                            }
                            else
                            {
                                // No such column. Is this a valid MV indicator or a valid value?
                                if (MvUtil.isValidMvIndicator(contents.toString(), column.getMvContainer()))
                                {
                                    // set the MV indicator
                                    mvWrapper = new MvFieldWrapper();
                                    mvWrapper.setMvIndicator("".equals(contents) ? null : contents.toString());
                                    value = mvWrapper;
                                }
                                else
                                {
                                    // set the actual value
                                    mvWrapper = new MvFieldWrapper();
                                    mvWrapper.setValue(column.converter.convert(column.clazz, contents));
                                    value = mvWrapper;
                                }
                            }
                        }
                    }
                    else if (column.isMvIndicator())
                    {
                        int qcColumnIndex = getMvColumnIndex(column);
                        if (qcColumnIndex != -1)
                        {
                            String qcColumName = _columns[qcColumnIndex].name;
                            // Is there a qc column that matches?
                            MvFieldWrapper mvWrapper = (MvFieldWrapper)row.get(qcColumName);
                            if (mvWrapper == null)
                            {
                                mvWrapper = new MvFieldWrapper();
                                mvWrapper.setMvIndicator("".equals(contents) ? null : contents.toString());
                                value = mvWrapper;
                                row.put(qcColumName, value); // store it for the qc column's use
                            }
                            else
                            {
                                mvWrapper.setMvIndicator("".equals(contents) ? null : contents.toString());
                                value = mvWrapper;
                            }
                        }
                        else
                        {
                            MvFieldWrapper mvWrapper = new MvFieldWrapper();
                            mvWrapper.setMvIndicator("".equals(contents) ? null : contents.toString());
                            value = mvWrapper;
                        }
                    }
                    else if (!"".equals(contents))
                    {
                        value = column.converter.convert(column.clazz, contents);
                    }
                }
                catch (ConversionException ce)
                {
                    value = column.errorValues;
                }
                
                if (value != null)
                    foundData = true;

                row.put(column.name, value);
            }

            rowIndex++;

            if (foundData)
                return row;
            else
                return getNextRow();  // keep going until a valid row or the end of the file
        }

        public void remove()
        {
            throw new UnsupportedOperationException("Please don't do that.");
        }

        public void close() throws IOException
        {
        }
    }

    public static class ExcelLoaderTestCase extends TestCase
    {
        public ExcelLoaderTestCase(String name)
        {
            super(name);
        }

        public static Test suite()
        {
            return new TestSuite(ExcelLoaderTestCase.class);
        }

        public void testColumnTypes() throws Exception
        {
            AppProps props = AppProps.getInstance();
            if (!props.isDevMode()) // We can only run the excel tests if we're in dev mode and have access to our samples
                return;

            String projectRootPath =  props.getProjectRoot();
            File projectRoot = new File(projectRootPath);

            File excelSamplesRoot = new File(projectRoot, "sampledata/dataLoading/excel");

            if (!excelSamplesRoot.exists() || !excelSamplesRoot.canRead())
                throw new IOException("Could not read excel samples in: " + excelSamplesRoot);

            File metadataSample = new File(excelSamplesRoot, "ExcelLoaderTest.xls");

            ExcelLoader loader = new ExcelLoader(metadataSample, true);
            checkColumnMetadata(loader);
            checkData(loader);
            loader.close();
        }

        private static void checkColumnMetadata(ExcelLoader loader) throws IOException
        {
            ColumnDescriptor[] columns = loader.getColumns();

            assertTrue(columns.length == 18);

            assertEquals(columns[0].clazz, Date.class);
            assertEquals(columns[1].clazz, Integer.class);
            assertEquals(columns[2].clazz, Double.class);

            assertEquals(columns[4].clazz, Boolean.class);

            assertEquals(columns[17].clazz, String.class);
        }

        private static void checkData(ExcelLoader loader) throws IOException
        {
            List<Map<String, Object>> data = loader.load();

            assertTrue(data.size() == 7);

            for (Map map : data)
            {
                assertTrue(map.size() == 18);
            }

            Map firstRow = data.get(0);
            assertTrue(firstRow.get("scan").equals(96));
            assertTrue(firstRow.get("accurateMZ").equals(false));
            assertTrue(firstRow.get("description").equals("description"));
        }
    }
}

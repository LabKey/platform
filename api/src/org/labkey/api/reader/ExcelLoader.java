/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

import org.apache.commons.collections15.iterators.ArrayIterator;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.iterator.CloseableIterator;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Data loader for Excel files -- can infer columns and return rows of data
 *
 * User: jgarms
 * Date: Oct 22, 2008
 */
public class ExcelLoader extends DataLoader
{
    public static FileType FILE_TYPE = new FileType(Arrays.asList("xlsx", "xls"), "xlsx");
    static {
        FILE_TYPE.setExtensionsMutuallyExclusive(false);
    }

    public static class Factory extends AbstractDataLoaderFactory
    {
        @NotNull @Override
        public DataLoader createLoader(File file, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
        {
            return new ExcelLoader(file, hasColumnHeaders, mvIndicatorContainer);
        }

        @NotNull @Override
        public DataLoader createLoader(InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
        {
            return new ExcelLoader(is, hasColumnHeaders, mvIndicatorContainer);
        }

        @NotNull @Override
        public FileType getFileType() { return FILE_TYPE; }
    }

    private final Workbook workbook;

    private String sheetName;

    private boolean deleteFileOnClose = false;

    public ExcelLoader(File file) throws IOException
    {
        this(file, false);
    }

    public ExcelLoader(File file, boolean hasColumnHeaders) throws IOException
    {
        this(file, hasColumnHeaders, null);
    }

    public ExcelLoader(InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
    {
        super(mvIndicatorContainer);
        setHasColumnHeaders(hasColumnHeaders);

        try
        {
            workbook = ExcelFactory.create(is);
        }
        catch (InvalidFormatException e)
        {
            throw new IOException(e.getMessage());
        }
    }

    public ExcelLoader(File file, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
    {
        super(mvIndicatorContainer);
        setHasColumnHeaders(hasColumnHeaders);
        setSource(file);

        try
        {
            workbook = ExcelFactory.create(file);
        }
        catch (InvalidFormatException e)
        {
            throw new IOException(e.getMessage());
        }
    }

    public void setDeleteFileOnClose(boolean del)
    {
        deleteFileOnClose = del;
    }
    
    public List<String> getSheetNames()
    {
        List<String> names = new ArrayList<String>();

        for (int i=0; i < workbook.getNumberOfSheets(); i++)
            names.add(workbook.getSheetName(i));
        return names;
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
                return workbook.getSheetAt(0);
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
        for (Row currentRow : sheet)
        {
            List<String> rowData = new ArrayList<String>();

            // Excel can report back more rows than exist. If we find no data at all,
            // we should not add a row.
            boolean foundData = false;
            if (currentRow.getPhysicalNumberOfCells() != 0)
            {
                for (int column = 0; column < currentRow.getLastCellNum(); column++)
                {
                    Cell cell = currentRow.getCell(column);
                    if (cell != null)
                    {
                        String data = String.valueOf(PropertyType.getFromExcelCell(cell));

                        if (data != null && !"".equals(data))
                            foundData = true;

                        rowData.add(data != null ? data : "");
                    }
                    else
                        rowData.add("");
                }
                if (foundData)
                    cells.add(rowData.toArray(new String[rowData.size()]));
            }
            if (--n == 0)
                break;
        }
        return cells.toArray(new String[cells.size()][]);
    }

    public CloseableIterator<Map<String, Object>> iterator()
    {
        try
        {
            return new ExcelIterator();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

/*
    public void finalize() throws Throwable
    {
        workbook.close();
        super.finalize();
    }
*/

    public void close()
    {
        if (deleteFileOnClose && null != _file)
        {
            _file.delete();
        }
    }


    private class ExcelIterator extends DataLoaderIterator
    {
        private final Sheet sheet;
        private final int numRows;

        public ExcelIterator() throws IOException
        {
            super(_skipLines == -1 ? 1 : _skipLines, true);

            sheet = getSheet();
            numRows = sheet.getLastRowNum() + 1;
        }

        @Override
        protected Object[] readFields() throws IOException
        {
            if (lineNum() >= numRows)
                return null;

            ColumnDescriptor[] allColumns = getColumns();
            Iterator<ColumnDescriptor> columnIter = new ArrayIterator<ColumnDescriptor>(allColumns);
            Object[] fields = new Object[_activeColumns.length];

            Row row = sheet.getRow(lineNum());
            if (row != null)
            {
                int numCols = row.getLastCellNum();
                for (int columnIndex = 0, fieldIndex = 0; columnIndex < allColumns.length; columnIndex++)
                {
                    boolean loadThisColumn = ((columnIter.hasNext() && columnIter.next().load));

                    if (loadThisColumn)
                    {
                        ColumnDescriptor column = _activeColumns[fieldIndex];
                        Object contents;

                        if (columnIndex < numCols) // We can get asked for more data than we contain, as extra columns can exist
                        {
                            Cell cell = row.getCell(columnIndex);
                            if (cell == null)
                            {
                                contents = "";
                            }
                            else if (column.clazz.equals(String.class))
                            {
                                contents = ExcelFactory.getCellStringValue(cell);
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
                        fields[fieldIndex++] = contents;
                    }
                }
            }
            return fields;
        }

        public void close() throws IOException
        {
            super.close();       // TODO: Shouldn't this close the workbook?
        }
    }

    public static class ExcelLoaderTestCase extends Assert
    {
        @Test
        public void testColumnTypes() throws Exception
        {
            AppProps.Interface props = AppProps.getInstance();
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

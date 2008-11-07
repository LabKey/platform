/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.common.tools;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.collections.Transformer;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.settings.AppProps;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Data loader for Excel files -- can infer columns and return rows of data
 *
 * User: jgarms
 * Date: Oct 22, 2008
 */
public class ExcelLoader extends DataLoader
{
    private final Workbook workbook;

    private String sheetName;

    public ExcelLoader(File file) throws IOException
    {
        this(file, false);
    }

    public ExcelLoader(File file, boolean hasColumnHeaders) throws IOException
    {
        setSource(file);
        try
        {
            workbook = Workbook.getWorkbook(file);
        }
        catch (BiffException e)
        {
            throw new IOException(e.getMessage());
        }
        _skipLines = hasColumnHeaders ? 1 : 0;
    }

    public List<String> getSheetNames()
    {
        return Arrays.asList(workbook.getSheetNames());
    }

    public void setSheetName(String sheetName)
    {
        this.sheetName = sheetName;
    }

    private Sheet getSheet()
    {
        if (sheetName != null)
            return workbook.getSheet(sheetName);
        else
            return workbook.getSheet(0);
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
            for (int column = 0; column < numCols; column++)
            {
                Cell cell = sheet.getCell(column, row);
                rowData[column] = cell.getContents();
            }
            cells.add(rowData);
        }
        return cells.toArray(new String[cells.size()][]);
    }

    protected Iterator<?> iterator() throws IOException
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

    private static class SimpleTransformer implements Transformer
    {
        private final ObjectFactory<?> factory;

        public SimpleTransformer(Class<?> clazz)
        {
            this.factory = ObjectFactory.Registry.getFactory(clazz);
        }

        public Object transform(Object o)
        {
            //noinspection unchecked
            return factory.fromMap((Map)o);   
        }
    }

    private class ExcelIterator implements Iterator
    {
        private boolean returnMaps;
        private int rowIndex;
        private Sheet sheet;
        private final int numRows;
        private final int numCols;

        public ExcelIterator()
        {
            // find a converter for each column type
            for (ColumnDescriptor column : _columns)
                column.converter = ConvertUtils.lookup(column.clazz);

            returnMaps = _returnElementClass == null || _returnElementClass.equals(java.util.Map.class);

            if (_transformer == null && !returnMaps)
            {
                _transformer = new SimpleTransformer(_returnElementClass);
            }

            sheet = getSheet();
            numRows = sheet.getRows();
            numCols = sheet.getColumns();

            rowIndex = _skipLines == -1 ? 1 : _skipLines;
        }

        public boolean hasNext()
        {
            return rowIndex < numRows;
        }

        public Object next()
        {
            if (rowIndex >= numRows)
                throw new IllegalStateException("Attempt to call next() on a finished iterator");

            Map<String,Object> row = new HashMap<String,Object>();
            for (int columnIndex = 0; columnIndex < _columns.length; columnIndex++)
            {
                ColumnDescriptor column = _columns[columnIndex];
                String contents;
                if (columnIndex < numCols) // We can get asked for more data than we contain, as extra columns can exist
                {
                    Cell cell = sheet.getCell(columnIndex, rowIndex);
                    contents = cell.getContents();
                }
                else
                {
                    contents = "";
                }
                Object value = column.missingValues;
                if (!"".equals(contents))
                {
                    try
                    {
                        value = column.converter.convert(column.clazz, contents);
                    }
                    catch (ConversionException ce)
                    {
                        // Couldn't convert. Leave it as a missing value.
                    }
                }

                row.put(column.name, value);
            }
            rowIndex++;

            if (_transformer != null)
                return _transformer.transform(row);

            return row;
        }

        public void remove()
        {
            throw new UnsupportedOperationException("Please don't do that.");
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
            checkObject(loader);
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
            Map[] data = (Map[])loader.load();

            assertTrue(data.length == 7);

            for (Map map : data)
            {
                assertTrue(map.size() == 18);
            }

            Map firstRow = data[0];
            assertTrue(firstRow.get("scan").equals(96));
            assertTrue(firstRow.get("accurateMZ").equals(false));
            assertTrue(firstRow.get("description").equals("description"));
        }

        private static void checkObject(ExcelLoader loader) throws IOException
        {
            loader.setReturnElementClass(TestRow.class);
            TestRow[] rows = (TestRow[])loader.load();

            assertTrue(rows.length == 7);

            TestRow firstRow = rows[0];
            assertEquals(firstRow.getScan(), 96);
            assertFalse(firstRow.isAccurateMZ());
            assertEquals(firstRow.getDescription(), "description");
        }
    }
}

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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
        setSource(file);
        try
        {
            workbook = Workbook.getWorkbook(file);
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

    public Object[] load() throws IOException
    {
        throw new UnsupportedOperationException();
    }

    protected String[][] getFirstNLines(int n) throws IOException
    {
        Sheet sheet;
        if (sheetName != null)
            sheet = workbook.getSheet(sheetName);
        else
            sheet = workbook.getSheet(0);

        List<String[]> cells = new ArrayList<String[]>();
        int numCols = sheet.getColumns();
        for (int row = 1; row <= n; row++) // rows are 1-indexed
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
            
        }
    }
}

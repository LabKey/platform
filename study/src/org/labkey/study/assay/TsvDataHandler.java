/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.study.assay;

import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import org.apache.commons.beanutils.ConversionException;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.study.assay.AbstractAssayTsvDataHandler;
import org.labkey.common.tools.ColumnDescriptor;
import org.labkey.common.tools.TabLoader;

import java.io.*;
import java.util.*;

/**
 * User: brittp
 * Date: Jul 11, 2007
 * Time: 11:17:56 AM
 */
public class TsvDataHandler extends AbstractAssayTsvDataHandler
{
    public static final DataType DATA_TYPE = new DataType("AssayRunTSVData");

    public Priority getPriority(ExpData data)
    {
        Lsid lsid = new Lsid(data.getLSID());
        if (DATA_TYPE.matches(lsid))
        {
            return Priority.HIGH;
        }
        return null;
    }

    protected boolean allowEmptyData()
    {
        return false;
    }

    protected Map<String, Object>[] loadFileData(PropertyDescriptor[] columns, File inputfile) throws IOException, ExperimentException
    {
        if (inputfile.getName().toLowerCase().endsWith(".xls"))
            return loadXls(columns, inputfile);
        else
            return loadTsv(columns, inputfile);
    }

    private Map<String, Object>[] loadXls(PropertyDescriptor[] columns, File inputfile) throws IOException, ExperimentException
    {
        FileInputStream fIn = null;
        try
        {
            fIn = new FileInputStream(inputfile);
            WorkbookSettings settings = new WorkbookSettings();
            settings.setGCDisabled(true);
            Workbook workbook = Workbook.getWorkbook(fIn, settings);
            Sheet sheet = workbook.getSheet(0);
            if (sheet.getRows() == 0 || sheet.getColumns() == 0)
                throw new ExperimentException("The first sheet of the  Excel workbook contains no data.  " +
                        "Data is read only from the first sheet.");
            Cell[] headerCells = sheet.getRow(0);
            List<String> headers = new ArrayList<String>(headerCells.length);
            // get list of header strings, normalizing empty headers to null:
            for (Cell headerCell : headerCells)
            {
                String header = headerCell.getContents();
                if (header != null && header.trim().length() == 0)
                    header = null;
                headers.add(header);
            }

            // remove "ghost" headers from the end of our list.  ghost headers occur when a header cell contains
            // formatting but no text content
            while (headers.size() > 0 && headers.get(headers.size() - 1) == null)
                headers.remove(headers.size() - 1);

            if (headers.size() == 0)
                throw new ExperimentException("Excel workbook contained no data on sheet 1.");

            Map<String, Object>[] rowDatas = new HashMap[sheet.getRows() - 1];

            Map<String, PropertyType> colTypes = new HashMap<String, PropertyType>();
            for (PropertyDescriptor pd : columns)
                colTypes.put(pd.getName().toLowerCase(), pd.getPropertyType());

            for (int rowIndex = 1; rowIndex < sheet.getRows(); rowIndex++)
            {
                Map<String, Object> rowData = new HashMap<String, Object>();
                rowDatas[rowIndex - 1] = rowData;

                Cell[] row = sheet.getRow(rowIndex);
                for (int i = 0; i < row.length; i++)
                {
                    String stringValue = row[i].getContents();
                    String header = i < headers.size() ? headers.get(i) : null;
                    if (header == null)
                    {
                        if (stringValue != null && stringValue.length() > 0)
                        {
                            throw new ExperimentException("The format of " + inputfile.getName() + " is not as expected. " +
                                    "No header was found for the data in column " + convertColumnToLetter(i) + ", row " + (rowIndex + 1) + ".");
                        }
                        else
                        {
                            // sometimes there are formatted cells to the right of the last data column.  This formatting
                            // introduces ghost rows into our data grid that should be ignored.  So, we continue if the header
                            // is empty/missing and there's no actual content in the data cell:
                            continue;
                        }
                    }
                    PropertyType type = colTypes.get(header.toLowerCase());
                    if (type != null)
                    {
                        try
                        {
                            rowData.put(header, type.getExcelValue(row[i]));
                        }
                         catch (ConversionException e)
                        {
                            throw new ExperimentException("There are errors in the uploaded data: " + header + " must be of type " + ColumnInfo.getFriendlyTypeName(type.getJavaType()) + " but the value \"" + row[i].getContents() + "\" in row " + (rowIndex + 1) + " could not be converted.");
                        }
                    }
                    else
                        rowData.put(header, stringValue != null && stringValue.length() > 0 ? stringValue : null);
                }
            }

            // If formatting has been applied to cells below the data, we'll sometimes have empty rows within our
            // rawData map that should be trimmed. See https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=4797
            int rowCount = rowDatas.length;
            boolean hasData = false;
            while (rowCount > 0 && !hasData)
            {
                Collection<Object> rowValues = rowDatas[rowCount - 1].values();
                for (Object value : rowValues)
                {
                    if (value != null)
                    {
                        hasData = true;
                        break;
                    }
                }
                if (!hasData)
                    rowCount--;
            }
            if (rowCount < rowDatas.length)
            {
                Map<String, Object>[] truncated = new Map[rowCount];
                System.arraycopy(rowDatas, 0, truncated, 0, rowCount);
                rowDatas = truncated;
            }

            return rowDatas;
        }
        catch (BiffException e)
        {
            throw new ExperimentException(e);
        }
        finally
        {
            if (fIn != null)
                try { fIn.close(); } catch (IOException e) { /* fall through */ }
        }
    }

    /**
     *
     * @param column zero-based column number
     * @return excel column name (e.g. A, B, AA, AB, BA, etc)
     */
    private static String convertColumnToLetter(int column)
    {
        if (column > 701)
        {
            // If greater than ZZ, punt. Return a string of the column name
            return Integer.toString(column);
        }
        if (column < 26)
        {

            int result = column + 'A';
            char c = (char)result;
            return Character.toString(c);
        }
        else
        {
            int remainder = column/26 - 1;
            return convertColumnToLetter(remainder) + convertColumnToLetter(column % 26);
        }
    }

    private Map<String, Object>[] loadTsv(PropertyDescriptor[] columns, File inputFile) throws IOException, ExperimentException
    {
        Map<String, Class> expectedColumns = new HashMap<String, Class>(columns.length);
        for (PropertyDescriptor col : columns)
        {
            if (col.getLabel() != null)
                expectedColumns.put(col.getLabel().toLowerCase(), col.getPropertyType().getJavaType());
        }
        for (PropertyDescriptor col : columns)
            expectedColumns.put(col.getName().toLowerCase(), col.getPropertyType().getJavaType());
        Reader fileReader = new FileReader(inputFile);
        try
        {
            TabLoader loader = new TabLoader(fileReader, true);
            for (ColumnDescriptor column : loader.getColumns())
            {
                Class expectedColumnClass = expectedColumns.get(column.name.toLowerCase());
                if (expectedColumnClass != null)
                    column.clazz = expectedColumnClass;
                else
                    column.load = false;
                column.errorValues = ERROR_VALUE;
            }
            return (Map<String, Object>[]) loader.load();
        }
        finally
        {
            try { fileReader.close(); } catch (IOException e) {}
        }
    }
}

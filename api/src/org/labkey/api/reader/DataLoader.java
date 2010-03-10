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

import org.apache.commons.beanutils.ConvertUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.util.CloseableIterator;
import org.labkey.api.util.Filter;
import org.labkey.api.util.FilterIterator;

import javax.servlet.ServletException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

/**
 * Abstract class for loading columnar data from different sources: TSVs, Excel files, etc.
 *
 * User: jgarms
 * Date: Oct 22, 2008
 * Time: 11:26:37 AM
 */
public abstract class DataLoader<T> implements Iterable<T>
{
    /**
     * Defines order of column type preferences. 
     * We'll try each one in turn, falling back
     * to the more general as necessary
     **/
    private final static Class[] CONVERT_CLASSES = new Class[]
    {
        Date.class,
        Integer.class,
        Double.class,
        Boolean.class,
        String.class
    };

    protected File _file = new File("Resource");

    protected ColumnDescriptor[] _columns;
    private boolean _initialized = false;
    protected int _scanAheadLineCount = 100; // number of lines to scan trying to infer data types
    // CONSIDER: explicit flags for hasHeaders, inferHeaders, skipLines etc.
    protected int _skipLines = -1;      // -1 means infer headers

    public static DataLoader<Map<String, Object>> getDataLoaderForFile(File file) throws ServletException, IOException
    {
        String filename = file.getName();

        if (filename.endsWith("xls"))
        {
            return new ExcelLoader(file, true);
        }
        else if (filename.endsWith("txt") || filename.endsWith("tsv"))
        {
            return new TabLoader(file, true);
        }
        else if (filename.endsWith("csv"))
        {
            TabLoader loader = new TabLoader(file, true);
            loader.parseAsCSV();
            return loader;
        }

        throw new ServletException("Unknown file type. File must have a suffix of .xls, .txt, .tsv or .csv.");
    }

    public final ColumnDescriptor[] getColumns() throws IOException
    {
        ensureInitialized();

        return _columns;
    }

    protected void ensureInitialized() throws IOException
    {
        if (!_initialized)
        {
            initialize();
            _initialized = true;
        }
    }

    protected void initialize() throws IOException
    {
        initializeColumns();
    }

    public void setColumns(ColumnDescriptor[] columns)
    {
        _columns = columns;
    }

    public void ensureColumn(ColumnDescriptor column) throws IOException
    {
        ColumnDescriptor[] existingColumns = getColumns();
        for (ColumnDescriptor existing : existingColumns)
        {
            if (existing.name.equalsIgnoreCase(column.name))
                return;
        }
        ColumnDescriptor[] newColumns = new ColumnDescriptor[existingColumns.length + 1];
        System.arraycopy(existingColumns, 0, newColumns, 0, existingColumns.length);
        newColumns[newColumns.length - 1] = column;
        setColumns(newColumns);
    }

    protected void initializeColumns() throws IOException
    {
        //Take our best guess since some columns won't map
        if (null == _columns)
            inferColumnInfo();
    }

    protected void setHasColumnHeaders(boolean hasColumnHeaders)
    {
        _skipLines = hasColumnHeaders ? 1 : 0;
    }

    protected void setSource(File inputFile) throws IOException
    {
        _file = inputFile;
        if (!_file.exists())
            throw new FileNotFoundException(_file.getPath());
        if (!_file.canRead())
            throw new IOException("Can't read file: " + _file.getPath());
    }

    /**
     * Return the data for the first n lines. Note that
     * subclasses are allowed to return fewer than n lines
     * if there are fewer rows than that in the data.
     **/
    public abstract String[][] getFirstNLines(int n) throws IOException;

    /**
     * Look at first <code>scanAheadLineCount</code> lines of the file and infer col names, data types.
     * Most useful if maps are being returned, otherwise use inferColumnInfo(reader, clazz) to
     * use properties of a bean instead.
     *
     * @throws java.io.IOException
     */
    @SuppressWarnings({"ConstantConditions"})
    private void inferColumnInfo() throws IOException
    {
        int numLines = _scanAheadLineCount + Math.max(_skipLines, 0);
        String[][] lineFields = getFirstNLines(numLines);
        numLines = lineFields.length;

        if (numLines == 0)
        {
            _columns = new ColumnDescriptor[0];
            return;
        }

        int nCols = 0;
        for (String[] lineField : lineFields)
        {
            nCols = Math.max(nCols, lineField.length);
        }

        ColumnDescriptor[] colDescs = new ColumnDescriptor[nCols];
        for (int i = 0; i < nCols; i++)
            colDescs[i] = new ColumnDescriptor();

        //Try to infer types
        int inferStartLine = _skipLines == -1 ? 1 : _skipLines;
        for (int f = 0; f < nCols; f++)
        {
            int classIndex = -1;
            for (int line = inferStartLine; line < numLines; line++)
            {
                if (f >= lineFields[line].length)
                    continue;
                String field = lineFields[line][f];
                if ("".equals(field))
                    continue;

                for (int c = Math.max(classIndex, 0); c < CONVERT_CLASSES.length; c++)
                {
                    //noinspection EmptyCatchBlock
                    try
                    {
                        Object o = ConvertUtils.convert(field, CONVERT_CLASSES[c]);
                        //We found a type that works. If it is more general than
                        //what we had before, we must use it.
                        if (o != null && c > classIndex)
                            classIndex = c;
                        break;
                    }
                    catch (Exception x)
                    {
                    }
                }
            }
            colDescs[f].clazz = classIndex == -1 ? String.class : CONVERT_CLASSES[classIndex];
        }

        //If first line is compatible type for all fields, then there is no header row
        if (_skipLines == -1)
        {
            boolean firstLineCompat = true;
            String[] fields = lineFields[0];
            for (int f = 0; f < nCols; f++)
            {
                if ("".equals(fields[f]))
                    continue;

                try
                {
                    Object o = ConvertUtils.convert(fields[f], colDescs[f].clazz);
                    if (null == o)
                    {
                        firstLineCompat = false;
                        break;
                    }
                }
                catch (Exception x)
                {
                    firstLineCompat = false;
                    break;
                }
            }
            if (firstLineCompat)
                _skipLines = 0;
            else
                _skipLines = 1;
        }

        if (_skipLines > 0)
        {
            String[] headers = lineFields[_skipLines - 1];
            for (int f = 0; f < nCols; f++)
                colDescs[f].name = (f >= headers.length || "".equals(headers[f])) ? getDefaultColumnName(f) : headers[f].trim();
        }
        else
        {
            for (int f = 0; f < colDescs.length; f++)
            {
                ColumnDescriptor colDesc = colDescs[f];
                colDesc.name = getDefaultColumnName(f);
            }
        }

        Set<String> columnNames = new HashSet<String>();
        for (ColumnDescriptor colDesc : colDescs)
        {
            if (!columnNames.add(colDesc.name))
            {
                throw new IOException("All columns must have unique names, but the column name '" + colDesc.name + "' appeared more than once.");
            }
        }

        _columns = colDescs;
    }

    protected String getDefaultColumnName(int col)
    {
        return "column" + col;
    }

    // Given a mv indicator column, find its matching value column
    protected int getMvColumnIndex(ColumnDescriptor mvIndicatorColumn)
    {
        // Sometimes names are URIs, sometimes they're names. If they're URIs, the columns
        // share a name. If not, they have different names
        @Nullable String nonMvIndicatorName = null;
        if (mvIndicatorColumn.name.toLowerCase().endsWith(MvColumn.MV_INDICATOR_SUFFIX.toLowerCase()))
        {
            nonMvIndicatorName = mvIndicatorColumn.name.substring(0, mvIndicatorColumn.name.length() - MvColumn.MV_INDICATOR_SUFFIX.length());
        }
        for(int i = 0; i<_columns.length; i++)
        {
            ColumnDescriptor col = _columns[i];
            if (col.isMvEnabled() && (col.name.equals(mvIndicatorColumn.name) || col.name.equals(nonMvIndicatorName)))
                return i;
        }
        return -1;
    }

    protected int getMvIndicatorColumnIndex(ColumnDescriptor mvColumn)
    {
        // Sometimes names are URIs, sometimes they're names. If they're URIs, the columns
        // share a name. If not, they have different names
        String namePlusIndicator = mvColumn.name + MvColumn.MV_INDICATOR_SUFFIX;

        for(int i = 0; i<_columns.length; i++)
        {
            ColumnDescriptor col = _columns[i];
            if (col.isMvIndicator() && (col.name.equals(mvColumn.name) || col.name.equals(namePlusIndicator)))
                return i;
        }
        return -1;
    }

    /**
     * Set the number of lines to look ahead in the file when infering the data types of the columns.
     */
    public void setScanAheadLineCount(int count)
    {
        _scanAheadLineCount = count;
    }

    /**
     * Returns an iterator over the data
     */
    public abstract CloseableIterator<T> iterator();

    public static class CloseableFilterIterator<T> extends FilterIterator<T> implements CloseableIterator<T>
    {
        protected CloseableIterator<T> _iter;

        public CloseableFilterIterator(CloseableIterator<T> iter, Filter<T> filter)
        {
            super(iter, filter);
            _iter = iter;
        }

        public void close() throws IOException
        {
            _iter.close();
        }
    }


    /**
     * Returns a list of T records, one for each non-header row of the file.
     */
    public List<T> load() throws IOException
    {
        getColumns();

        List<T> rowList = new LinkedList<T>();
        CloseableIterator<T> it = iterator();

        try
        {
            while (it.hasNext())
                rowList.add(it.next());
        }
        finally
        {
            it.close();
        }

        return rowList;
    }

    public abstract void close();
}

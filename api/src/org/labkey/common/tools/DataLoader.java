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

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.PropertyUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.beans.PropertyDescriptor;

/**
 * Interface for loading columnar data from different sources: TSVs, Excel files, etc.
 *
 * User: jgarms
 * Date: Oct 22, 2008
 * Time: 11:26:37 AM
 */
public abstract class DataLoader
{
    /**
     * Defines order of column type preferences. 
     * We'll try each one in turn, falling back
     * to the more general as necessary
     **/
    protected final static Class[] CONVERT_CLASSES = new Class[]
    {
        Boolean.class,
        Date.class,
        Integer.class,
        Double.class,
        String.class
    };

    protected File _file = new File("Resource");

    protected ColumnDescriptor[] _columns;
    private boolean columnsInitialized = false;
    protected int _scanAheadLineCount = 20; // number of lines to scan trying to infer data types
    // CONSIDER: explicit flags for hasHeaders, inferHeaders, skipLines etc.
    protected int _skipLines = -1;      // -1 means infer headers
    protected Class _returnElementClass = java.util.Map.class;

    public final ColumnDescriptor[] getColumns() throws IOException
    {
        if (!columnsInitialized)
        {
            initializeColumns();
        }
        columnsInitialized = true;
        return _columns;
    }

    private void initializeColumns() throws IOException
    {
        //Take our best guess since some columns won't map
        if (null == _columns)
            inferColumnInfo();

        if (null != _returnElementClass)
            initColumnInfos(_returnElementClass);
    }

    public abstract Object[] load() throws IOException;

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
    protected abstract String[][] getFirstNLines(int n) throws IOException;

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
                colDescs[f].name = (f >= headers.length || "".equals(headers[f])) ? "column" + f : headers[f];
        }
        else
        {
            for (int f = 0; f < colDescs.length; f++)
            {
                ColumnDescriptor colDesc = colDescs[f];
                colDesc.name = "column" + f;
            }
        }

        _columns = colDescs;
    }

    /**
     * Set the number of lines to look ahead in the file when infering the data types of the columns.
     */
    public void setScanAheadLineCount(int count)
    {
        _scanAheadLineCount = count;
    }

    public int getSkipLines()
    {
        return _skipLines;
    }

    /**
     * @param skipLines -1 means infer headers, 0 means no headers, and 1 means there is one header line
     */
    public void setSkipLines(int skipLines)
    {
        this._skipLines = skipLines;
    }

    private void initColumnInfos(Class clazz)
    {
        PropertyDescriptor origDescriptors[] = PropertyUtils.getPropertyDescriptors(clazz);
        HashMap<String, PropertyDescriptor> mappedPropNames = new HashMap<String, PropertyDescriptor>();
        for (PropertyDescriptor origDescriptor : origDescriptors)
        {
            if (origDescriptor.getName().equals("class"))
                continue;

            mappedPropNames.put(origDescriptor.getName().toLowerCase(), origDescriptor);
        }

        boolean isMapClass = java.util.Map.class.isAssignableFrom(clazz);
        for (ColumnDescriptor column : _columns)
        {
            PropertyDescriptor prop = mappedPropNames.get(column.name.toLowerCase());
            if (null != prop)
            {
                column.name = prop.getName();
                column.clazz = prop.getPropertyType();
                column.isProperty = true;
                column.setter = prop.getWriteMethod();
                if (column.clazz.isPrimitive())
                {
                    if (Float.TYPE.equals(column.clazz))
                        column.missingValues = 0.0F;
                    else if (Double.TYPE.equals(column.clazz))
                        column.missingValues = 0.0;
                    else if (Boolean.TYPE.equals(column.clazz))
                        column.missingValues = Boolean.FALSE;
                    else
                        column.missingValues = 0; //Will get converted.
                }
            }
            else if (isMapClass)
            {
                column.isProperty = false;
            }
            else
            {
                column.load = false;
            }
        }
    }

    public Class getReturnElementClass()
    {
        return _returnElementClass;
    }

    public void setReturnElementClass(Class returnElementClass)
    {
        this._returnElementClass = returnElementClass;
    }
}

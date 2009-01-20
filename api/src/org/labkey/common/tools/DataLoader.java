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
package org.labkey.common.tools;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.collections.Transformer;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.QcColumn;

import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;

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
    private boolean columnsInitialized = false;
    protected int _scanAheadLineCount = 20; // number of lines to scan trying to infer data types
    // CONSIDER: explicit flags for hasHeaders, inferHeaders, skipLines etc.
    protected int _skipLines = -1;      // -1 means infer headers
    protected Class _returnElementClass = java.util.Map.class;
    protected Transformer _transformer = null;

    public final ColumnDescriptor[] getColumns() throws IOException
    {
        if (!columnsInitialized)
        {
            initializeColumns();
        }
        columnsInitialized = true;
        return _columns;
    }

    public void setColumns(ColumnDescriptor[] columns)
    {
        this._columns = columns;
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

    private void initializeColumns() throws IOException
    {
        //Take our best guess since some columns won't map
        if (null == _columns)
            inferColumnInfo();

        if (null != _returnElementClass)
            initColumnInfos(_returnElementClass);
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
     * Returns an iterator over the data in this file
     */
    protected abstract Iterator<?> iterator() throws IOException;

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
                colDescs[f].name = (f >= headers.length || "".equals(headers[f])) ? getDefaultColumnName(f) : headers[f];
        }
        else
        {
            for (int f = 0; f < colDescs.length; f++)
            {
                ColumnDescriptor colDesc = colDescs[f];
                colDesc.name = getDefaultColumnName(f);
            }
        }

        _columns = colDescs;
    }

    protected String getDefaultColumnName(int col)
    {
        return "column" + col;
    }

    // Given a qc indicator column, find its matching qc column
    protected int getQcColumnIndex(ColumnDescriptor qcIndicatorColumn)
    {
        // Sometimes names are URIs, sometimes they're names. If they're URIs, the columns
        // share a name. If not, they have different names
        @Nullable String nonQcIndicatorName = null;
        if (qcIndicatorColumn.name.toLowerCase().endsWith(QcColumn.QC_INDICATOR_SUFFIX.toLowerCase()))
        {
            nonQcIndicatorName = qcIndicatorColumn.name.substring(0, qcIndicatorColumn.name.length() - QcColumn.QC_INDICATOR_SUFFIX.length());
        }
        for(int i = 0; i<_columns.length; i++)
        {
            ColumnDescriptor col = _columns[i];
            if (col.qcEnabled && (col.name.equals(qcIndicatorColumn.name) || col.name.equals(nonQcIndicatorName)))
                return i;
        }
        return -1;
    }

    protected int getQcIndicatorColumnIndex(ColumnDescriptor qcColumn)
    {
        // Sometimes names are URIs, sometimes they're names. If they're URIs, the columns
        // share a name. If not, they have different names
        String namePlusIndicator = qcColumn.name + QcColumn.QC_INDICATOR_SUFFIX;

        for(int i = 0; i<_columns.length; i++)
        {
            ColumnDescriptor col = _columns[i];
            if (col.qcIndicator && (col.name.equals(qcColumn.name) || col.name.equals(namePlusIndicator)))
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

    public Transformer getTransformer()
    {
        return _transformer;
    }

    public void setTransformer(Transformer transformer)
    {
        this._transformer = transformer;
    }

    /**
     * Returns an array of objects one for each non-header row of the file.
     * By default the objects are maps, but may be java beans.
     */
    public Object[] load() throws IOException
    {
        getColumns();

        List<Object> rowList = new ArrayList<Object>();
        Iterator it = iterator();
        while (it.hasNext())
            rowList.add(it.next());

        Object[] oarr = rowList.toArray((Object[]) Array.newInstance(_returnElementClass, rowList.size()));
        return oarr;
    }

    public void close() {}


    /** Test class for JUnit tests **/
    public static class TestRow
    {
        private Date date;
        private int scan;
        private double time;
        private double mz;
        private boolean accurateMZ;
        private double mass;
        private double intensity;
        private int chargeStates;
        private double kl;
        private double background;
        private double median;
        private int peaks;
        private int scanFirst;
        private int scanLast;
        private int scanCount;
        private String description;

        public boolean isAccurateMZ()
        {
            return accurateMZ;
        }

        public void setAccurateMZ(boolean accurateMZ)
        {
            this.accurateMZ = accurateMZ;
        }

        public double getBackground()
        {
            return background;
        }

        public void setBackground(double background)
        {
            this.background = background;
        }

        public int getChargeStates()
        {
            return chargeStates;
        }

        public void setChargeStates(int chargeStates)
        {
            this.chargeStates = chargeStates;
        }

        public Date getDate()
        {
            return date;
        }

        public void setDate(Date date)
        {
            this.date = date;
        }

        public String getDescription()
        {
            return description;
        }

        public void setDescription(String description)
        {
            this.description = description;
        }

        public double getIntensity()
        {
            return intensity;
        }

        public void setIntensity(double intensity)
        {
            this.intensity = intensity;
        }

        public double getKl()
        {
            return kl;
        }

        public void setKl(double kl)
        {
            this.kl = kl;
        }

        public double getMass()
        {
            return mass;
        }

        public void setMass(double mass)
        {
            this.mass = mass;
        }

        public double getMedian()
        {
            return median;
        }

        public void setMedian(double median)
        {
            this.median = median;
        }

        public double getMz()
        {
            return mz;
        }

        public void setMz(double mz)
        {
            this.mz = mz;
        }

        public int getPeaks()
        {
            return peaks;
        }

        public void setPeaks(int peaks)
        {
            this.peaks = peaks;
        }

        public int getScan()
        {
            return scan;
        }

        public void setScan(int scan)
        {
            this.scan = scan;
        }

        public int getScanCount()
        {
            return scanCount;
        }

        public void setScanCount(int scanCount)
        {
            this.scanCount = scanCount;
        }

        public int getScanFirst()
        {
            return scanFirst;
        }

        public void setScanFirst(int scanFirst)
        {
            this.scanFirst = scanFirst;
        }

        public int getScanLast()
        {
            return scanLast;
        }

        public void setScanLast(int scanLast)
        {
            this.scanLast = scanLast;
        }

        public double getTime()
        {
            return time;
        }

        public void setTime(double time)
        {
            this.time = time;
        }
    }
}

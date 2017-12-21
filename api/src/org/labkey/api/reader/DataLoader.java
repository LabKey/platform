/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.dataiterator.ScrollableDataIterator;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.RowMapFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ImportAliasable;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MvUtil;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.MapDataIterator;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.iterator.CloseableIterator;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.ExceptionUtil;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * User: jgarms
 * Date: Oct 22, 2008
 * Time: 11:26:37 AM
 */

// Abstract class for loading columnar data from file sources: TSVs, Excel files, etc.
public abstract class DataLoader implements Iterable<Map<String, Object>>, Loader, DataIteratorBuilder, Closeable
{
    public static DataLoaderService get()
    {
        return DataLoaderService.get();
    }
    
    // if a conversion error occurs, the original field value is returned
    public static final Object ERROR_VALUE_USE_ORIGINAL = new Object();
    private static final Logger _log = Logger.getLogger(DataLoader.class);

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

    @NotNull
    protected Map<String, ColumnInfo> _columnInfoMap = Collections.emptyMap();
    protected ColumnDescriptor[] _columns;
    private boolean _initialized = false;
    protected int _scanAheadLineCount = 1000; // number of lines to scan trying to infer data types
    // CONSIDER: explicit flags for hasHeaders, inferHeaders, skipLines etc.
    protected int _skipLines = -1;      // -1 means infer headers
    private boolean _inferTypes = true;
    private boolean _includeBlankLines = false;
    protected boolean _throwOnErrors = false;
    protected final Container _mvIndicatorContainer;
    // true if the results can be scrolled by the DataIterator created in .getDataIterator()
    protected Boolean _scrollable = null;
    protected boolean _preserveEmptyString = false;

    protected DataLoader()
    {
        _mvIndicatorContainer = null;
    }

    protected DataLoader(Container mvIndicatorContainer)
    {
        _mvIndicatorContainer = mvIndicatorContainer;
    }

    public void setInferTypes(boolean infer)
    {
        _inferTypes = infer;
    }

    public boolean getInferTypes()
    {
        return _inferTypes;
    }

    public boolean isThrowOnErrors()
    {
        return _throwOnErrors;
    }

    public void setThrowOnErrors(boolean throwOnErrors)
    {
        _throwOnErrors = throwOnErrors;
    }

    public boolean isIncludeBlankLines()
    {
        return _includeBlankLines;
    }

    /** When false (the default), lines that have no values will be skipped. When true, a row of null values is returned instead. */
    public void setIncludeBlankLines(boolean includeBlankLines)
    {
        _includeBlankLines = includeBlankLines;
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

    // if provided, the header row will be inspected and compared to these ColumnInfos.  if imported columns match a known
    // ColumnInfo, the datatype of this column will be preferentially used.  this can help avoid issues such as a varchar column
    // where incoming data looks numeric.
    public void setKnownColumns(List<ColumnInfo> cols)
    {
        if (cols == null || cols.size() == 0)
            throw new IllegalArgumentException("List of columns cannot be null or empty");

        boolean useMv = _mvIndicatorContainer != null && !MvUtil.getIndicatorsAndLabels(_mvIndicatorContainer).isEmpty();
        _columnInfoMap = ImportAliasable.Helper.createImportMap(cols, useMv);
    }

    protected void initializeColumns() throws IOException
    {
        //Take our best guess since some columns won't map
        if (null == _columns)
            inferColumnInfo();
    }

    public void setHasColumnHeaders(boolean hasColumnHeaders)
    {
        _skipLines = hasColumnHeaders ? 1 : 0;
    }

    protected void setSource(File inputFile) throws IOException
    {
        verifyFile(inputFile);
        _file = inputFile;
    }

    protected static void verifyFile(File inputFile) throws IOException
    {
        if (!inputFile.exists())
            throw new FileNotFoundException(inputFile.getPath());
        if (!inputFile.canRead())
            throw new IOException("Can't read file: " + inputFile.getPath());
    }

    /** @return if the input to this DataLoader is entirely in-memory can be reset. */
    protected boolean isScrollable()
    {
        if (_scrollable == null)
        {
            _log.warn("DataLoader scrollability not explicitly set.  Assuming DataLoader is scrollable, but the default may change in the future.");
            _scrollable = true;
        }
        return _scrollable;
    }

    /** Set scrollable to true if the input to this DataLoader is entirely in-memory can be reset. */
    protected void setScrollable(boolean scrollable)
    {
        _scrollable = scrollable;
    }

    /**
     * By default, we treat empty strings as NULL values. Set true to keep them as empty strings instead.
     *
     */
    public void setPreserveEmptyString(boolean preserveEmptyString)
    {
        _preserveEmptyString = preserveEmptyString;
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

        Set<String> missingValueIndicators = _mvIndicatorContainer != null ? MvUtil.getMvIndicators(_mvIndicatorContainer) : Collections.emptySet();

        int nCols = 0;
        for (String[] lineField : lineFields)
        {
            nCols = Math.max(nCols, lineField.length);
        }

        ColumnDescriptor[] colDescs = new ColumnDescriptor[nCols];
        for (int i = 0; i < nCols; i++)
            colDescs[i] = new ColumnDescriptor();

        //Try to infer types
        if (getInferTypes())
        {
            int inferStartLine = _skipLines == -1 ? 1 : _skipLines;
            for (int f = 0; f < nCols; f++)
            {
                List<Class> classesToTest = new ArrayList<>(Arrays.asList(CONVERT_CLASSES));

                int classIndex = -1;
                //NOTE: this means we have a header row
                if (_skipLines == 1)
                {
                    if (f < lineFields[0].length)
                    {
                        String name = lineFields[0][f];
                        if (_columnInfoMap.containsKey(name))
                        {
                            //preferentially use this class if it matches
                            classesToTest.add(0, _columnInfoMap.get(name).getJavaClass());
                        }
                    }
                }

                for (int line = inferStartLine; line < numLines; line++)
                {
                    if (f >= lineFields[line].length)
                        continue;
                    String field = lineFields[line][f];
                    if (missingValueIndicators.contains(field))
                    {
                        colDescs[f].setMvEnabled(_mvIndicatorContainer);
                        continue;
                    }

                    if ("".equals(field))
                        continue;

                    for (int c = Math.max(classIndex, 0); c < classesToTest.size(); c++)
                    {
                        //noinspection EmptyCatchBlock
                        try
                        {
                            Object o = ConvertUtils.convert(field, classesToTest.get(c));
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
                colDescs[f].clazz = classIndex == -1 ? String.class : classesToTest.get(classIndex);
            }
        }

        //If first line is compatible type for all fields, then there is no header row
        if (_skipLines == -1)
        {
            boolean firstLineCompat = true;
            String[] fields = lineFields[0];
            for (int f = 0; f < nCols; f++)
            {
                //Issue 14295: **ArrayIndexOutOfBoundsException in org.labkey.api.reader.DataLoader.inferColumnInfo()
                //if you have an irregularly shaped TSV a given row can have fewer than nCols elements
                if (f >= fields.length || "".equals(fields[f]))
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
                colDescs[f].name = (f >= headers.length || StringUtils.isBlank(headers[f])) ? getDefaultColumnName(f) : headers[f].trim();
        }
        else
        {
            for (int f = 0; f < colDescs.length; f++)
            {
                ColumnDescriptor colDesc = colDescs[f];
                colDesc.name = getDefaultColumnName(f);
            }
        }

        Set<String> columnNames = new HashSet<>();
        for (ColumnDescriptor colDesc : colDescs)
        {
            if (!columnNames.add(colDesc.name) && isThrowOnErrors())
            {
                // TODO: This should be refactored to not throw this here, but rather, have the callers check themselves. It
                // is not in the interest of inferring columns that we validate duplicate columns.
                IOException e = new IOException("All columns must have unique names, but the column name '" + colDesc.name + "' appeared more than once.");

                // 12908: IOException in DataLoader.inferColumnInfo() is not the correct exception type -- disabled logging for now
                ExceptionUtil.decorateException(e, ExceptionUtil.ExceptionInfo.SkipMothershipLogging, "true", true);
                throw e;
            }
        }

        _columns = colDescs;
    }

    protected String getDefaultColumnName(int col)
    {
        return "column" + col;
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
    public abstract CloseableIterator<Map<String, Object>> iterator();


    /**
     * Returns a list of T records, one for each non-header row of the file.
     */
    // Caution: Using this instead of iterating directly has lead to many scalability problems in the past.
    // TODO: Migrate usages to iterator()
    public List<Map<String, Object>> load()
    {
        return IteratorUtils.toList(iterator());
    }

    public abstract void close();


    public static final Converter noopConverter = (type, value) -> value;
    public static final Converter StringConverter = ConvertUtils.lookup(String.class);

    protected abstract class DataLoaderIterator implements CloseableIterator<Map<String, Object>>
    {
        protected final ColumnDescriptor[] _activeColumns;
        private final RowMapFactory<Object> _factory;

        private Object[] _fields = null;
        private Map<String, Object> _values = null;
        private int _lineNum = 0;
        private boolean _closed = false;


        protected DataLoaderIterator(int lineNum) throws IOException
        {
            _lineNum = lineNum;

            // Figure out the active columns (load = true).  This is the list of columns we care about throughout the iteration.
            ColumnDescriptor[] allColumns = getColumns();
            ArrayList<ColumnDescriptor> active = new ArrayList<>(allColumns.length);

            for (ColumnDescriptor column : allColumns)
                if (column.load)
                    active.add(column);

            _activeColumns = active.toArray(new ColumnDescriptor[active.size()]);
            ArrayListMap.FindMap<String> colMap = new ArrayListMap.FindMap<>(new CaseInsensitiveHashMap<>());

            for (int i = 0; i < _activeColumns.length; i++)
            {
                if (!_activeColumns[i].isMvIndicator())
                    colMap.put(_activeColumns[i].name, i);
            }

            _factory = new RowMapFactory<>(colMap);

            // find a converter for each column type
            for (ColumnDescriptor column : _activeColumns)
                if (column.converter == null)
                    column.converter = ConvertUtils.lookup(column.clazz);
        }

        public int lineNum()
        {
            return _lineNum;
        }

        protected abstract Object[] readFields() throws IOException;

        @Override
        public Map<String, Object> next()
        {
            if (_values == null)
                throw new IllegalStateException("Attempt to call next() on a finished iterator");
            Map<String, Object> next = _values;
            _values = null;
            return next;
        }

        @Override
        public boolean hasNext()
        {
            if (_fields != null)
                return true;    // throw illegalstate?

            try
            {
                while (true)
                {
                    _fields = readFields();
                    if (_fields == null)
                    {
                        close();
                        return false;
                    }
                    _lineNum++;

                    _values = convertValues();
                    if (_values == Collections.EMPTY_MAP && !isIncludeBlankLines())
                        continue;

                    return _values != null;
                }
            }
            catch (IOException e)
            {
                _log.error("unexpected io error", e);
                throw new RuntimeException(e);
            }
        }

        protected int getMvIndicatorColumnIndex(ColumnDescriptor mvColumn)
        {
            // Sometimes names are URIs, sometimes they're names. If they're URIs, the columns
            // share a name. If not, they have different names
            String namePlusIndicator = mvColumn.name + MvColumn.MV_INDICATOR_SUFFIX;

            for (int i = 0; i < _activeColumns.length; i++)
            {
                ColumnDescriptor col = _activeColumns[i];
                if (col.isMvIndicator() && (col.name.equals(mvColumn.name) || col.name.equals(namePlusIndicator)))
                    return i;
            }

            return -1;
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

            for (int i = 0; i < _activeColumns.length; i++)
            {
                ColumnDescriptor col = _activeColumns[i];
                if (col.isMvEnabled() && (col.name.equals(mvIndicatorColumn.name) || col.name.equals(nonMvIndicatorName)))
                    return i;
            }
            return -1;
        }

        protected final Map<String, Object> convertValues()
        {
            if (_fields == null)
                return null;    // consider: throw IllegalState

            try
            {
                Object[] fields = _fields;
                _fields = null;
                Object[] values = new Object[_activeColumns.length];

                boolean foundData = false;
                for (int i = 0; i < _activeColumns.length; i++)
                {
                    ColumnDescriptor column = _activeColumns[i];
                    if (_preserveEmptyString && null == column.missingValues)
                    {
                        column.missingValues = "";
                    }
                    Object fld;
                    if (i >= fields.length)
                    {
                        fld = _preserveEmptyString ? null : "";
                    }
                    else
                    {
                        fld = fields[i];
                        if (fld instanceof String && StringUtils.containsOnly(((String) fld), ' '))
                            fld = "";
                        else if (fld == null)
                            fld = _preserveEmptyString ? null : "";
                    }
                    try
                    {
                        if (column.isMvEnabled())
                        {
                            if (values[i] != null)
                            {
                                // An MV indicator column must have generated this. Set the value
                                MvFieldWrapper mvWrapper = (MvFieldWrapper)values[i];
                                mvWrapper.setValue(("".equals(fld)) ?
                                    column.missingValues :
                                    column.converter.convert(column.clazz, fld));
                            }
                            else
                            {
                                // Do we have an MV indicator column?
                                int mvIndicatorIndex = getMvIndicatorColumnIndex(column);
                                if (mvIndicatorIndex != -1)
                                {
                                    // There is such a column, so this value had better be good.
                                    MvFieldWrapper mvWrapper = new MvFieldWrapper(MvUtil.getMvIndicators(column.getMvContainer()));
                                    mvWrapper.setValue( ("".equals(fld)) ?
                                        column.missingValues :
                                        column.converter.convert(column.clazz, fld));
                                    values[i] = mvWrapper;
                                    values[mvIndicatorIndex] = mvWrapper;
                                }
                                else
                                {
                                    // No such column. Is this a valid MV indicator or a valid value?
                                    if (MvUtil.isValidMvIndicator(fld.toString(), column.getMvContainer()))
                                    {
                                        MvFieldWrapper mvWrapper = new MvFieldWrapper(MvUtil.getMvIndicators(column.getMvContainer()));
                                        mvWrapper.setMvIndicator("".equals(fld) ? null : fld.toString());
                                        values[i] = mvWrapper;
                                    }
                                    else
                                    {
                                        MvFieldWrapper mvWrapper = new MvFieldWrapper(MvUtil.getMvIndicators(column.getMvContainer()));
                                        mvWrapper.setValue( ("".equals(fld)) ?
                                            column.missingValues :
                                            column.converter.convert(column.clazz, fld));
                                        values[i] = mvWrapper;
                                    }
                                }
                            }
                        }
                        else if (column.isMvIndicator())
                        {
                            int mvColumnIndex = getMvColumnIndex(column);
                            if (mvColumnIndex != -1)
                            {
                                // There's an mv column that matches
                                if (values[mvColumnIndex] == null)
                                {
                                    MvFieldWrapper mvWrapper = new MvFieldWrapper(MvUtil.getMvIndicators(column.getMvContainer()));
                                    mvWrapper.setMvIndicator("".equals(fld) ? null : fld.toString());
                                    values[mvColumnIndex] = mvWrapper;
                                    values[i] = mvWrapper;
                                }
                                else
                                {
                                    MvFieldWrapper mvWrapper = (MvFieldWrapper)values[mvColumnIndex];
                                    mvWrapper.setMvIndicator("".equals(fld) ? null : fld.toString());
                                }
                                if (_throwOnErrors && !MvUtil.isValidMvIndicator(fld.toString(), column.getMvContainer()))
                                    throw new ConversionException(fld + " is not a valid MV indicator");
                            }
                            else
                            {
                                // No matching mv column, just put in a wrapper
                                if (!MvUtil.isValidMvIndicator(fld.toString(), column.getMvContainer()))
                                {
                                    throw new ConversionException(fld + " is not a valid MV indicator");
                                }
                                MvFieldWrapper mvWrapper = new MvFieldWrapper(MvUtil.getMvIndicators(column.getMvContainer()));
                                mvWrapper.setMvIndicator("".equals(fld) ? null : fld.toString());
                                values[i] = mvWrapper;
                            }
                        }
                        else
                        {
                            values[i] = ("".equals(fld)) ?
                                    column.missingValues :
                                    column.converter.convert(column.clazz, fld);
                        }
                    }
                    catch (Exception x)
                    {
                        if (_throwOnErrors)
                        {
                            StringBuilder sb = new StringBuilder("Could not convert the ");
                            if (fields[i] == null)
                            {
                                sb.append("empty value");
                            }
                            else
                            {
                                sb.append("value '");
                                sb.append(fields[i]);
                                sb.append("'");
                            }
                            sb.append(" from line #");
                            sb.append(_lineNum);
                            sb.append(" in column #");
                            sb.append(i + 1);
                            sb.append(" (");
                            if (column.name.contains("#"))
                            {
                                sb.append(column.name.substring(column.name.indexOf("#") + 1));
                            }
                            else
                            {
                                sb.append(column.name);
                            }
                            sb.append(") to ");
                            sb.append(column.clazz.getSimpleName());

                            throw new ConversionException(sb.toString(), x);
                        }
                        else if (ERROR_VALUE_USE_ORIGINAL.equals(column.errorValues))
                            values[i] = fld;
                        else
                            values[i] = column.errorValues;
                    }

                    if (values[i] != null)
                        foundData = true;
                }

                if (foundData || isIncludeBlankLines())
                {
                    // This extra copy was added to AbstractTabLoader in r12810 to let DatasetDefinition.importDatasetData()
                    // modify the underlying maps. TODO: Refactor dataset import and return immutable maps. 
                    ArrayList<Object> list = new ArrayList<>(_activeColumns.length);
                    list.addAll(Arrays.asList(values));
                    return _factory.getRowMap(list);
                }
                else
                {
                    // Return EMPTY_MAP to signal that we haven't reached the end yet
                    return Collections.emptyMap();
                }
            }
            catch (Exception e)
            {
                if (_throwOnErrors)
                {
                    if (e instanceof ConversionException)
                        throw ((ConversionException) e);
                    else
                        throw new RuntimeException(e);
                }

                if (null != _file)
                    _log.error("failed loading file " + _file.getName() + " at line: " + _lineNum + " " + e, e);
            }

            // Return null to signals there are no more rows
            return null;
        }


        @Override
        public void remove()
        {
            throw new UnsupportedOperationException("'remove()' is not defined for TabLoaderIterator");
        }


        @Override
        public void close() throws IOException
        {
            _closed = true;
        }


        @Override
        protected void finalize() throws Throwable
        {
            super.finalize();
            // assert _closed;  TODO: Uncomment to force all callers to close iterator.
        }
    }


    /**
     * It might be nice to go one level lower in the parser
     * (pre conversion, missing value) but this is a quick way to
     * get all the DataLoaders to play with the newer DataIterator code
     * @return null if there was an IOException trying to parse the input
     */
    @Override
    @Nullable
    public DataIterator getDataIterator(final DataIteratorContext context)
    {
        setInferTypes(false);
        try
        {
            return LoggingDataIterator.wrap(createDataIterator(context));
        }
        catch (IOException x)
        {
            context.getErrors().addRowError(new ValidationException(x.getMessage()));
            return null;
        }
    }

    /** Actually create an instance of DataIterator to use, which might be subclass-specific */
    protected DataIterator createDataIterator(DataIteratorContext context) throws IOException
    {
        return new _DataIterator(context, getColumns(), isScrollable());
    }

    protected class _DataIterator implements ScrollableDataIterator, MapDataIterator
    {
        final DataIteratorContext _context;
        private final boolean _scrollable;
        final BatchValidationException _errors;
        CloseableIterator<Map<String, Object>> _it = null;
        ArrayListMap<String,Object> _row = null;
        int _rowNumber = 0;
        ColumnDescriptor[] _columns;

        protected _DataIterator(DataIteratorContext context, ColumnDescriptor[] columns, boolean scrollable)
        {
            _context = context;
            _scrollable = scrollable;
            _errors = context.getErrors();
            _columns = columns;
            beforeFirst();
        }

        @Override
        public String getDebugName()
        {
            return DataLoader.this.getClass().getSimpleName();
        }

        @Override
        public boolean isScrollable()
        {
            return _scrollable;
        }

        @Override
        public void beforeFirst()
        {
            if (null != _it)
            {
                if (!isScrollable())
                {
                    throw new UnsupportedOperationException("Unable to reset on a non-scrollable iterator");
                }
                IOUtils.closeQuietly(_it);
            }
            _it = iterator();
            _rowNumber = 0;
        }

        @Override
        public int getColumnCount()
        {
            return _columns.length;
        }

        @Override
        public ColumnInfo getColumnInfo(int i)
        {
            if (i == 0)
                return new ColumnInfo("_rowNumber", JdbcType.INTEGER);
            ColumnDescriptor d = _columns[i-1];
            JdbcType type = JdbcType.valueOf(d.clazz);
            if (null == type)
                type = JdbcType.VARCHAR;
            ColumnInfo ret = new ColumnInfo(d.name, type);
            if (null != d.propertyURI)
                ret.setPropertyURI(d.propertyURI);
            return ret;
        }

        private ArrayListMap.FindMap<String> _findMap = null;

        @Override
        public boolean next() throws BatchValidationException
        {
            _row = null;
            boolean hasNext = _it.hasNext();
            if (hasNext)
            {
                Map<String, Object> nextRow = _it.next();
                if (nextRow instanceof ArrayListMap)
                {
                    _row = (ArrayListMap)nextRow;
                }
                else
                {
                    if (null == _findMap)
                    {
                        _findMap = new ArrayListMap.FindMap<>(new CaseInsensitiveHashMap<>());
                        for (ColumnDescriptor cd : _columns)
                            _findMap.put(cd.getColumnName(),_findMap.size());
                    }
                    _row = new ArrayListMap<>(_findMap);
                    _row.putAll(nextRow);
                }
                _rowNumber++;
            }
            return hasNext;
        }

        @Override
        public boolean supportsGetMap()
        {
            return true;
        }

        @Override
        public Map<String, Object> getMap()
        {
            _row.setReadOnly(true);
            return _row;
        }

        @Override
        public Object get(int i)
        {
            if (i == 0)
                return _rowNumber;
            return _row.get(i-1);
        }

        @Override
        public Supplier<Object> getSupplier(int i)
        {
            if (i==0)
                return () -> _rowNumber;
            else
                return () -> _row.get(i-1);
        }

        @Override
        public boolean isConstant(int i)
        {
            return false;
        }

        @Override
        public Object getConstantValue(int i)
        {
            return null;
        }

        @Override
        public void close() throws IOException
        {
            if (null != _it)
                IOUtils.closeQuietly(_it);
            DataLoader.this.close();
        }

        @Override
        public void debugLogInfo(StringBuilder sb)
        {
            sb.append(this.getClass().getName()).append("\n");
            Arrays.stream(_columns)
                .forEach(c ->
                {
                    sb.append("    ").append(c.name).append(" ").append(c.clazz.getSimpleName()).append("\n");
                });
        }
    }
}

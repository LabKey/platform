/*
 * Copyright (c) 2004-2009 Fred Hutchinson Cancer Research Center
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

// UNDONE: should probably be in package org.labkey.common.util

import junit.framework.Test;
import junit.framework.TestFailure;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.collections.Transformer;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.QcUtil;
import org.labkey.api.exp.QcFieldWrapper;

import java.io.*;
import java.lang.reflect.Array;
import java.util.*;
import java.util.regex.Pattern;


/**
 * TabLoader will load tab-delimited text into an array of objects.
 * Client can specify a bean class to load the objects into. If the class is java.util.Map
 * an Array
 * <p/>
 * NOTE: If a loader is been used to load an array of maps you should NOT change the column descriptors.
 * A single set of column descriptors is used to key all the maps. (ISSUE: Should probably use
 * ArrayListMap or clone the column desciptors instead).
 * <p/>
 * UNDONE: Would like to overflow bean properties into a map if the bean also implements map.
 * <p/>
 * UNDONE: Should probably integrate in some way with ObjectFactory
 * <p/>
 * User: migra
 * Date: Jun 28, 2004
 * Time: 2:25:19 PM
 */
public class TabLoader extends DataLoader
{
    static Logger _log = Logger.getLogger(TabLoader.class);

    // source data
    private String _stringData = null;
    private Reader _reader;

    /* this is a little hokey - it makes some later code work without mods */
    private Map<Object, Integer> _colMap = new HashMap<Object, Integer>();
    private Map<String, String> _comments = new HashMap<String, String>();
    private boolean _lowerCaseHeaders;

    protected char _chDelimiter = '\t';
    protected String _strDelimiter = null;
    protected boolean _parseQuotes = false;
    protected boolean _throwOnErrors = false;


    public TabLoader(File inputFile) throws IOException
    {
        setSource(inputFile);
    }

    public TabLoader(File inputFile, boolean hasColumnHeaders) throws IOException
    {
        setSource(inputFile);
        _skipLines = hasColumnHeaders ? 1 : 0;
    }


    public TabLoader(Reader reader, boolean hasColumnHeaders, Class returnClass)
    {
        if (returnClass != null)
            _returnElementClass = returnClass;

        setSource(reader);
        _skipLines = hasColumnHeaders ? 1 : 0;
    }


    public TabLoader(Reader reader, boolean hasColumnHeaders)
    {
        this(reader, hasColumnHeaders, null);
    }


    // infer whether there are columnHeaders
    public TabLoader(Reader reader)
    {
        this(reader, false, null);
    }


    public TabLoader(String src, boolean hasColumnHeaders)
    {
        if (src == null)
            throw new IllegalArgumentException("src cannot be null");
        _skipLines = hasColumnHeaders ? 1 : 0;
        setSource(src);
    }


    public TabLoader(String src)
    {
        setSource(src);
    }


    public TabLoader(File inputFile, int skipLines)
            throws IOException
    {
        setSource(inputFile);
        this._skipLines = skipLines;
    }


    public TabLoader(File inputFile, Class returnObjectClass)
            throws IOException
    {
        setSource(inputFile);
        _returnElementClass = returnObjectClass;
    }


    public TabLoader(File inputFile, int skipLines, Class returnObjectClass, ColumnDescriptor[] columns)
            throws IOException
    {
        _returnElementClass = returnObjectClass;
        setSource(inputFile);
        _skipLines = skipLines;
        _returnElementClass = returnObjectClass;
        _columns = columns;
    }


    public TabLoader(Reader reader, int skipLines, Class returnObjectClass, ColumnDescriptor[] columns)
    {
        _returnElementClass = returnObjectClass;
        setSource(reader);
        _skipLines = skipLines;
        _returnElementClass = returnObjectClass;
        _columns = columns;
    }


    protected void setSource(Reader reader)
    {
        if (reader.markSupported())
            _reader = reader;
        else
            _reader = new BufferedReader(reader);

        try
        {
            // shouldn't throw as we checked markSupported
            _reader.mark(1024 * 1024);
        }
        catch (IOException x)
        {
            throw new RuntimeException(x);
        }
    }


    protected void setSource(String src)
    {
        _stringData = src;
    }


    protected BufferedReader getReader() throws IOException
    {
        if (null != _reader)
        {
            // We don't close handed in readers
            _reader.reset();
            return new BufferedReader(_reader)
            {
                public void close()
                {
                }
            };
        }

        if (null != _stringData)
            return new BufferedReader(new StringReader(_stringData));

        return new BufferedReader(new FileReader(_file));
    }


    public void setLowerCaseHeaders(boolean lowerCaseHeaders)
    {
        _lowerCaseHeaders = lowerCaseHeaders;
    }


    public Map getComments()
    {
        //noinspection unchecked
        return Collections.unmodifiableMap(_comments);
    }


    public Object[] loadColsAsArrays() throws IOException
    {
        initColNameMap();
        ColumnDescriptor[] columns = getColumns();
        Object[] valueLists = new Object[columns.length];

        for (int i = 0; i < valueLists.length; i++)
        {
            if (!columns[i].load)
                continue;

            Class clazz = columns[i].clazz;
            if (clazz.isPrimitive())
            {
                if (clazz.equals(Double.TYPE))
                    valueLists[i] = new DoubleArray();
                else if (clazz.equals(Float.TYPE))
                    valueLists[i] = new FloatArray();
                else if (clazz.equals(Integer.TYPE))
                    valueLists[i] = new IntegerArray();
            }
            else
            {
                valueLists[i] = new ArrayList();
            }
        }

        BufferedReader reader = null;
        try
        {
            reader = getReader();
            int line = 0;

            String s;
            for (int skip = 0; skip < _skipLines; skip++)
            {
                //noinspection UnusedAssignment
                s = reader.readLine();
                line++;
            }

            while ((s = reader.readLine()) != null)
            {
                line++;
                if ("".equals(s.trim()))
                    continue;

                String[] fields = parseLine(s);
                for (int i = 0; i < fields.length && i < columns.length; i++)
                {
                    if (!columns[i].load)
                        continue;

                    String value = fields[i];

                    Class clazz = columns[i].clazz;
                    if (clazz.isPrimitive())
                    {
                        if (clazz.equals(Double.TYPE))
                            ((DoubleArray) valueLists[i]).add(Double.parseDouble(value));
                        else if (clazz.equals(Float.TYPE))
                            ((FloatArray) valueLists[i]).add(Float.parseFloat(value));
                        else if (clazz.equals(Integer.TYPE))
                            ((IntegerArray) valueLists[i]).add(Integer.parseInt(value));
                    }
                    else
                    {
                        try
                        {
                            if ("".equals(value))
                                ((List<Object>) valueLists[i]).add(columns[i].missingValues);
                            else
                                ((List<Object>) valueLists[i]).add(ConvertUtils.convert(value, columns[i].clazz));
                        }
                        catch (Exception x)
                        {
                            if (_throwOnErrors)
                                throw new ConversionException("Conversion error: line " + line + " column " + (i+ 1), x);

                            ((List<Object>) valueLists[i]).add(columns[i].errorValues);
                        }
                    }

                }
            }
        }
        finally
        {
            if (null != reader)
                reader.close();
        }

        Object[] returnArrays = new Object[columns.length];
        for (int i = 0; i < columns.length; i++)
        {
            if (!columns[i].load)
                continue;

            Class clazz = columns[i].clazz;
            if (clazz.isPrimitive())
            {
                if (clazz.equals(Double.TYPE))
                    returnArrays[i] = ((DoubleArray) valueLists[i]).toArray(null);
                else if (clazz.equals(Float.TYPE))
                    returnArrays[i] = ((FloatArray) valueLists[i]).toArray(null);
                else if (clazz.equals(Integer.TYPE))
                    returnArrays[i] = ((IntegerArray) valueLists[i]).toArray(null);
            }
            else
            {
                Object[] values = (Object[]) Array.newInstance(columns[i].clazz, ((List) valueLists[i]).size());
                returnArrays[i] = ((List<Object>) valueLists[i]).toArray(values);
            }
        }

        return returnArrays;
    }


    /**
     * called for non-quoted strings
     * you could argue that TAB delimited string shouldn't have white space stripped, but
     * we always strip.
     */
    protected static String parseValue(String value)
    {
        value = StringUtils.trimToEmpty(value);
        if ("\\N".equals(value))
            return "";
        return value;
    }

    private ArrayList<String> listParse = new ArrayList<String>(30);


    /**
     * Note we don't handled values with embedded newlines
     *
     * @param s
     */
    Pattern replaceDoubleQuotes = Pattern.compile("\"\"");
    
    protected String[] parseLine(String s)
    {
        if (!_parseQuotes)
        {
            if (_strDelimiter == null)
                _strDelimiter = new String(new char[]{_chDelimiter});
            String[] fields = s.split(_strDelimiter);
            for (int i = 0; i < fields.length; i++)
                fields[i] = parseValue(fields[i]);
            return fields;
        }

        s = s.trim();

        String field;
        int length = s.length();
        int start = 0;
        listParse.clear();
        while (start < length)
        {
            int end;
            char ch = s.charAt(start);
            if (ch == _chDelimiter)
            {
                end = start;
                field = "";
            }
            else if (ch == '"')
            {
                end = start;
                boolean hasQuotes = false;
                while (true)
                {
                    end = s.indexOf('"', end + 1);
                    if (end == -1)
                        throw new IllegalArgumentException("CSV can't parse line: " + s);
                    if (end == s.length() - 1 || s.charAt(end + 1) != '"')
                        break;
                    hasQuotes = true;
                    end++; // skip double ""
                }
                field = s.substring(start + 1, end);
                if (hasQuotes && -1 != field.indexOf("\"\""))
                    field = replaceDoubleQuotes.matcher(field).replaceAll("\"");
                // eat final " and any trailing white space
                end++;
                while (end < length && s.charAt(end) != _chDelimiter && Character.isWhitespace(s.charAt(end)))
                    end++;
            }
            else
            {
                end = s.indexOf(_chDelimiter, start);
                if (end == -1)
                    end = s.length();
                field = s.substring(start, end);
                field = parseValue(field);
            }
            listParse.add(field);

            // there should be a comma or an EOL here
            if (end < length && s.charAt(end) != _chDelimiter)
                throw new IllegalArgumentException("CSV can't parse line: " + s);
            end++;
            while (end < length && s.charAt(end) != _chDelimiter && Character.isWhitespace(s.charAt(end)))
                end++;
            start = end;
        }
        return listParse.toArray(new String[listParse.size()]);
    }


    public TabLoaderIterator
    iterator() throws IOException
    {
        TabLoaderIterator retVal = new TabLoader.TabLoaderIterator();
        return retVal;
    }


    private void initColNameMap() throws IOException
    {
        ColumnDescriptor[] columns = getColumns();
        for (int i = 0; i < columns.length; i++)
        {
            String colName = _lowerCaseHeaders ? columns[i].name.toLowerCase() : columns[i].name;
            _colMap.put(colName, i);
        }
    }

    /**
     * Load a tab delimited file into an array of arrays.
     * Every column in the file is loaded.
     * Number of columns in the file must be >= number of classes
     * Blank lines are skipped. Missing intrinsic values are converted to 0
     *
     * @param r
     * @param types
     * @param skipRows
     * @return
     * @throws IOException
     */
    public static Object[] loadColumnArrays(Reader r, Class[] types, int skipRows) throws IOException
    {
        ColumnDescriptor[] colDescs = new ColumnDescriptor[types.length];
        for (int i = 0; i < colDescs.length; i++)
        {
            ColumnDescriptor desc = new ColumnDescriptor();
            desc.clazz = types[i];
            desc.load = true;
            colDescs[i] = desc;
        }
        TabLoader loader = new TabLoader(r, skipRows, null, colDescs);
        Object[] arrays = loader.loadColsAsArrays();
        return arrays;
    }

    public static Object[] loadObjects(File file, Class returnClass) throws Exception
    {
        TabLoader loader = new TabLoader(file, returnClass);
        Object[] objects = loader.load();
        return objects;
    }

    public static Map[] loadMaps(File file) throws Exception
    {
        TabLoader loader = new TabLoader(file);
        return (Map[]) loader.load();
    }


    public void parseAsCSV()
    {
        _chDelimiter = ',';
        _parseQuotes = true;
    }


    public void setParseQuotes(boolean parseQuotes)
    {
        _parseQuotes = parseQuotes;
    }

    public boolean isThrowOnErrors()
    {
        return _throwOnErrors;
    }

    public void setThrowOnErrors(boolean throwOnErrors)
    {
        _throwOnErrors = throwOnErrors;
    }


    protected class _RowMap implements Map<Object, Object>
    {
        protected Object[] _values;

        _RowMap(Object[] values)
        {
            this._values = values;
        }

        public Object[] getArray()
        {
            return _values;
        }

        public int size()
        {
            return _values.length;
        }

        public boolean isEmpty()
        {
            return false;
        }

        public boolean containsKey(Object o)
        {
            if (o instanceof String && _lowerCaseHeaders)
                o = ((String) o).toLowerCase();
            Integer index = _colMap.get(o);
            return null != index && index < _values.length;
        }

        public boolean containsValue(Object o)
        {
            return false;
        }

        public Object get(Object o)
        {
            if (o instanceof String && _lowerCaseHeaders)
                o = ((String) o).toLowerCase();
            Integer col = _colMap.get(o);
            if (null == col)
                return null;
            int icol = col;
            if (icol < 0 || icol >= _values.length)
                return null;

            return _values[icol];
        }

        public Object put(Object o, Object o1)
        {
            if (o instanceof String && _lowerCaseHeaders)
                o = ((String) o).toLowerCase();
            Integer col = _colMap.get(o);
            if (null == col)
                throw new IllegalArgumentException("Can't find col: " + o);

            //This generally won't happen
            if (null == _values || _values.length <= col)
            {
                Object[] newValues = new Object[col + 1];
                if (null != _values)
                    System.arraycopy(_values, 0, newValues, 0, _values.length);

                _values = newValues;
            }

            Object oldValue = _values[col];
            _values[col] = o1;
            return oldValue;
        }

        public Object remove(Object o)
        {
            throw new UnsupportedOperationException();
        }

        public void putAll(Map map)
        {
            for (Object o : map.keySet())
                put(o, map.get(o));
        }

        public void clear()
        {
            _values = new Object[_columns.length];
        }

        public Set<Object> keySet()
        {
            return _colMap.keySet();
        }

        public Collection<Object> values()
        {
            return Collections.unmodifiableCollection(Arrays.asList(_values));
        }

        public Set<Map.Entry<Object, Object>> entrySet()
        {
            Set<Map.Entry<Object, Object>> s = new HashSet<Map.Entry<Object, Object>>();
            for (int i = 0; i < _columns.length; i++)
                s.add(new RowMapEntry(i));
            return s;
        }

        private class RowMapEntry implements Entry
        {
            int col;

            RowMapEntry(int col)
            {
                this.col = col;
            }

            public Object getKey()
            {
                return _columns[col].name;
            }

            public Object getValue()
            {
                return _values[col];
            }

            public Object setValue(Object o)
            {
                Object oldVal = _values[col];
                _values[col] = o;
                return oldVal;
            }
        }
    }


    public class TabLoaderIterator implements Iterator<Object>
    {
        public void close()
        {
            try
            {
                if (null != reader)
                    reader.close();
                reader = null;
            }
            catch (IOException x)
            {
                _log.error("Unexpected exception", x);
            }
        }

        BufferedReader reader = null;
        String line = null;
        int lineNo = 0;
        boolean returnMaps = true;

        protected TabLoaderIterator() throws IOException
        {
            initColNameMap();

            returnMaps = _returnElementClass == null || _returnElementClass.equals(java.util.Map.class);

            // UNDONE: _transformer is in parent class (ick)
            if (_transformer == null && !returnMaps)
                _transformer = new TabTransformer();

            // find a converter for each column type
            for (ColumnDescriptor column : _columns)
                column.converter = ConvertUtils.lookup(column.clazz);

            reader = TabLoader.this.getReader();
            String s;
            for (int skip = 0; skip < _skipLines;)
            {
                s = reader.readLine();
                if (null == s)
                    break;
                lineNo++;
                if (s.length() == 0 || s.charAt(0) == '#')
                {
                    int eq = s.indexOf('=');
                    if (eq != -1)
                    {
                        String key = s.substring(1, eq).trim();
                        String value = s.substring(eq + 1).trim();
                        if (key.length() > 0 || value.length() > 0)
                            _comments.put(key, value);
                    }
                    continue;
                }
                skip++;
            }
        }


        public boolean hasNext()
        {
            if (line != null)
                return true;    // throw illegalstate?

            try
            {
                do
                {
                    line = reader.readLine();
                    if (line == null)
                    {
                        close();
                        return false;
                    }
                    lineNo++;
                }
                while (null == StringUtils.trimToNull(line) || line.charAt(0) == '#');
            }
            catch (Exception e)
            {
                _log.error("unexpected io error", e);
                throw new RuntimeException(e);
            }

            return true;
        }


        public Object next()
        {
            if (line == null)
                return null;    // consider: throw IllegalState

            try
            {
                String s = line;
                line = null;

                String[] fields = parseLine(s);

                Object[] values = new Object[_columns.length];
                for (int i = 0; i < _columns.length; i++)
                {
                    ColumnDescriptor column = _columns[i];
                    if (!column.load)
                        continue;
                    String fld;
                    if (i >= fields.length)
                    {
                        fld = "";
                    }
                    else
                    {
                        fld = fields[i];
                    }
                    try
                    {
                        if (column.qcEnabled)
                        {
                            if (values[i] != null)
                            {
                                // A QC indicator column must have generated this. Set the value
                                QcFieldWrapper qcWrapper = (QcFieldWrapper)values[i];
                                qcWrapper.setValue(("".equals(fld)) ?
                                    column.missingValues :
                                    column.converter.convert(column.clazz, fld));
                            }
                            else
                            {
                                // Do we have a QC indicator column?
                                int qcIndicatorIndex = getQcIndicatorColumnIndex(column);
                                if (qcIndicatorIndex != -1)
                                {
                                    // There is such a column, so this value had better be good.
                                    QcFieldWrapper qcWrapper = new QcFieldWrapper();
                                    qcWrapper.setValue( ("".equals(fld)) ?
                                        column.missingValues :
                                        column.converter.convert(column.clazz, fld));
                                    values[i] = qcWrapper;
                                    values[qcIndicatorIndex] = qcWrapper;
                                }
                                else
                                {
                                    // No such column. Is this a valid qc indicator or a valid value?
                                    if (QcUtil.isValidQcValue(fld, column.qcContainer))
                                    {
                                        QcFieldWrapper qcWrapper = new QcFieldWrapper();
                                        qcWrapper.setValue("".equals(fld) ? null : fld);
                                        values[i] = qcWrapper;
                                    }
                                    else
                                    {
                                        QcFieldWrapper qcWrapper = new QcFieldWrapper();
                                        qcWrapper.setValue( ("".equals(fld)) ?
                                            column.missingValues :
                                            column.converter.convert(column.clazz, fld));
                                        values[i] = qcWrapper;
                                    }
                                }
                            }
                        }
                        else if (column.qcIndicator)
                        {
                            int qcColumnIndex = getQcColumnIndex(column);
                            if (qcColumnIndex != -1)
                            {
                                // There's a qc column that matches
                                if (values[qcColumnIndex] == null)
                                {
                                    QcFieldWrapper qcWrapper = new QcFieldWrapper();
                                    qcWrapper.setQcValue("".equals(fld) ? null : fld);
                                    values[qcColumnIndex] = qcWrapper;
                                    values[i] = qcWrapper;
                                }
                                else
                                {
                                    if (QcUtil.isValidQcValue(fld, column.qcContainer))
                                    {
                                        QcFieldWrapper qcWrapper = (QcFieldWrapper)values[qcColumnIndex];
                                        qcWrapper.setQcValue("".equals(fld) ? null : fld);
                                    }
                                    else
                                    {
                                        throw new ConversionException(fld + " is not a valid QC value");
                                    }
                                }
                            }
                            else
                            {
                                // No matching qc column, just put in a wrapper
                                if (!QcUtil.isValidQcValue(fld, column.qcContainer))
                                {
                                    throw new ConversionException(fld + " is not a valid QC value");
                                }
                                QcFieldWrapper qcWrapper = new QcFieldWrapper();
                                qcWrapper.setQcValue("".equals(fld) ? null : fld);
                                values[i] = qcWrapper;
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
                                throw new ConversionException("Conversion error: line " + lineNo + " column " + (i+ 1) + " (" + column.name + ")", x);

                        values[i] = column.errorValues;
                    }
                }

                Map m = new _RowMap(values);
                if (null == _transformer)
                    return m;
                else
                    return _transformer.transform(m);
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
                
                _log.error("failed loading file " + _file.getName() + " at line: " + lineNo + " " + e, e);
            }
            return null;
        }


        public void remove()
        {
            throw new UnsupportedOperationException("'remove()' is not defined for TabLoaderIterator");
        }
    }


    /**
     * NOTE: we don't use ObjectFactory, because that's not available in the tools build currently.  Hoewever, you
     * can easily wrap an ObjectFactory with the Transformer interface
     */
    class TabTransformer implements Transformer
    {
        public Object transform(Object o)
        {
            try
            {
                _RowMap m = (_RowMap) o;
                Object bean = _returnElementClass.newInstance();

                for (int i = 0; i < _columns.length; i++)
                {

                    ColumnDescriptor column = _columns[i];
                    if (!column.load) continue;
                    // CONSIDER: explicit option to not skip blank/null values

                    Object value = m._values[i];
                    if (null == value)
                        continue;

                    if (column.isProperty)
                    {
                        try
                        {
                            if (null != column.setter)
                            {
                                column.setter.invoke(bean, value);
                            }
                            else
                            {
                                BeanUtils.setProperty(bean, column.name, value);
                            }
                        }
                        catch (Exception x)
                        {
                            if (null != _columns[i].errorValues)
                            {
                                BeanUtils.setProperty(bean, _columns[i].name, _columns[i].errorValues);
                            }
                        }
                    }
                    else
                    {
                        //dhmay correcting this check and making it do something, 7/17/06.
                        //This was only half-implemented, before.
                        if (java.util.Map.class.isAssignableFrom(bean.getClass()))
                        {
                            //cast is ok here because we're explicitly checking
                            ((Map) bean).put(column.name, value);
                        }

                    }
                }
                return bean;
            }
            catch (Exception x)
            {
                throw new RuntimeException(x);
            }
        }
    }

    public String[][] getFirstNLines(int n) throws IOException
    {
        BufferedReader reader = getReader();
        try
        {
            String[] lines = new String[n];
            int i;
            for (i = 0; i < lines.length;)
            {
                String line = reader.readLine();
                if (null == line)
                    break;
                if (line.length() == 0 || line.charAt(0) == '#')
                    continue;
                lines[i++] = line;
            }
            int nLines = i;
            if (nLines == 0)
            {
                return new String[0][];
            }

            String[][] lineFields = new String[nLines][];
            for (i = 0; i < nLines; i++)
            {
                lineFields[i] = parseLine(lines[i]);
            }
            return lineFields;
        }
        finally
        {
            try {reader.close();} catch (IOException ioe) {}
        }
    }

    public static class TabLoaderTestCase extends junit.framework.TestCase
    {
        String csvData =
                "# algorithm=org.fhcrc.cpas.viewer.feature.FeatureStrategyPeakClusters\n" +
                        "# date=Mon May 22 13:25:28 PDT 2006\n" +
                        "# java.vendor=Sun Microsystems Inc.\n" +
                        "# java.version=1.5.0_06\n" +
                        "# revision=rev1.1\n" +
                        "# user.name=Matthew\n" +
                        "date,scan,time,mz,accurateMZ,mass,intensity,charge,chargeStates,kl,background,median,peaks,scanFirst,scanLast,scanCount,totalIntensity,description\n" +
                        "1/2/2006,96,1543.3401,858.3246,FALSE,1714.6346,2029.6295,2,1,0.19630894,26.471083,12.982442,4,92,100,9,20248.762,description\n" +
/*empty int*/   "2/Jan/2006,,1560.348,858.37555,FALSE,1714.7366,1168.3536,2,1,0.033085547,63.493385,8.771278,5,101,119,19,17977.979,\"desc\"\"ion\"\n" +
/*empty date*/  ",25,1460.2411,745.39404,FALSE,744.3868,1114.4303,1,1,0.020280406,15.826528,12.413276,4,17,41,25,13456.231,\"des,crip,tion\"\n" +
                        "2-Jan-06,89,1535.602,970.9579,FALSE,1939.9012,823.70984,2,1,0.0228055,10.497823,2.5962036,5,81,103,23,9500.36,\n" +
                        "2 January 2006,164,1624.442,783.8968,FALSE,1565.779,771.20935,2,1,0.024676466,11.3547325,3.3645654,5,156,187,32,12656.351,\n" +
                        "\"January 2, 2006\",224,1695.389,725.39404,FALSE,2173.1604,6.278867,3,1,0.2767084,1.6497655,1.2496755,3,221,229,9,55.546417\n" +
                        "1/2/06,249,1724.5541,773.42175,FALSE,1544.829,5.9057474,2,1,0.5105971,0.67020833,1.4744527,2,246,250,5,29.369175\n" +
                        "# bar\n" +
                        "\n" +
                        "#";
        String tsvData =
                "# algorithm=org.fhcrc.cpas.viewer.feature.FeatureStrategyPeakClusters\n" +
                        "# date=Mon May 22 13:25:28 PDT 2006\n" +
                        "# java.vendor=Sun Microsystems Inc.\n" +
                        "# java.version=1.5.0_06\n" +
                        "# revision=rev1.1\n" +
                        "# user.name=Matthew\n" +
                        "date\tscan\ttime\tmz\taccurateMZ\tmass\tintensity\tcharge\tchargeStates\tkl\tbackground\tmedian\tpeaks\tscanFirst\tscanLast\tscanCount\ttotalIntensity\tdescription\n" +
                        "1/2/2006\t96\t1543.3401\t858.3246\tFALSE\t1714.6346\t2029.6295\t2\t1\t0.19630894\t26.471083\t12.982442\t4\t92\t100\t9\t20248.762\tdescription\n" +
/*empty int*/   "2/Jan/2006\t\t1560.348\t858.37555\tFALSE\t1714.7366\t1168.3536\t2\t1\t0.033085547\t63.493385\t8.771278\t5\t101\t119\t19\t17977.979\tdesc\"ion\n" +
/*empty date*/  "\t25\t1460.2411\t745.39404\tFALSE\t744.3868\t1114.4303\t1\t1\t0.020280406\t15.826528\t12.413276\t4\t17\t41\t25\t13456.231\tdes,crip,tion\n" +
                        "2-Jan-06\t89\t1535.602\t970.9579\tFALSE\t1939.9012\t823.70984\t2\t1\t0.0228055\t10.497823\t2.5962036\t5\t81\t103\t23\t9500.36\t\n" +
                        "2 January 2006\t164\t1624.442\t783.8968\tFALSE\t1565.779\t771.20935\t2\t1\t0.024676466\t11.3547325\t3.3645654\t5\t156\t187\t32\t12656.351\t\n" +
                        "January 2, 2006\t224\t1695.389\t725.39404\tFALSE\t2173.1604\t6.278867\t3\t1\t0.2767084\t1.6497655\t1.2496755\t3\t221\t229\t9\t55.546417\t\n" +
                        "1/2/06\t249\t1724.5541\t773.42175\tFALSE\t1544.829\t5.9057474\t2\t1\t0.5105971\t0.67020833\t1.4744527\t2\t246\t250\t5\t29.369175\t\n" +
                        "# foo\n" +
                        "\n" +
                        "#";


        private File _createTempFile(String data, String ext) throws IOException
        {
            File f = File.createTempFile("junit", ext);
            f.deleteOnExit();
            Writer w = new FileWriter(f);
            w.write(data);
            w.close();
            return f;
        }


        public TabLoaderTestCase()
        {
            this("TabLoader Test");
        }


        public TabLoaderTestCase(String name)
        {
            super(name);
        }


        public void testTSV() throws IOException
        {
        }

        public void testTSVFile() throws IOException
        {
            File tsv = _createTempFile(tsvData, ".tsv");

            TabLoader l = new TabLoader(tsv);
            Map[] maps = (Map[]) l.load();
            assertEquals(l.getColumns().length, 18);
            assertEquals(l.getColumns()[0].clazz, Date.class);
            assertEquals(l.getColumns()[1].clazz, Integer.class);
            assertEquals(l.getColumns()[2].clazz, Double.class);
            assertEquals(l.getColumns()[4].clazz, Boolean.class);
            assertEquals(l.getColumns()[17].clazz, String.class);
            assertEquals(maps.length, 7);

            Map firstRow = maps[0];
            assertTrue(firstRow.get("scan").equals(96));
            assertTrue(firstRow.get("accurateMZ").equals(false));
            assertTrue(firstRow.get("description").equals("description"));
            tsv.delete();
        }


        public void testTSVReader() throws IOException
        {
            File csv = _createTempFile(tsvData, ".tsv");
            Reader r = new FileReader(csv);
            TabLoader l = new TabLoader(r, true);
            Map[] maps = (Map[]) l.load();
            assertEquals(l.getColumns().length, 18);
            assertEquals(maps.length, 7);
            r.close();
            csv.delete();
        }


        public void testCSVFile() throws IOException
        {
            File csv = _createTempFile(csvData, ".csv");

            TabLoader l = new TabLoader(csv);
            l.parseAsCSV();
            Map[] maps = (Map[]) l.load();
            assertEquals(l.getColumns().length, 18);
            assertEquals(l.getColumns()[0].clazz, Date.class);
            assertEquals(l.getColumns()[1].clazz, Integer.class);
            assertEquals(l.getColumns()[2].clazz, Double.class);
            assertEquals(l.getColumns()[4].clazz, Boolean.class);
            assertEquals(l.getColumns()[17].clazz, String.class);
            assertEquals(maps.length, 7);

            Map firstRow = maps[0];
            assertTrue(firstRow.get("scan").equals(96));
            assertTrue(firstRow.get("accurateMZ").equals(false));
            assertTrue(firstRow.get("description").equals("description"));

            csv.delete();
        }

        public void testCSVReader() throws IOException
        {
            File csv = _createTempFile(csvData, ".csv");
            Reader r = new FileReader(csv);
            TabLoader l = new TabLoader(r, true);
            l.parseAsCSV();
            Map[] maps = (Map[]) l.load();
            assertEquals(l.getColumns().length, 18);
            assertEquals(maps.length, 7);
            r.close();
            csv.delete();
        }


        public void compareTSVtoCSV() throws IOException
        {
            TabLoader lCSV = new TabLoader(csvData, true);
            lCSV.parseAsCSV();
            Map[] mapsCSV = (Map[]) lCSV.load();

            TabLoader lTSV = new TabLoader(tsvData, true);
            Map[] mapsTSV = (Map[]) lTSV.load();

            assertEquals(lCSV.getColumns().length, lTSV.getColumns().length);
            assertEquals(mapsCSV.length, mapsTSV.length);
            for (int i = 0; i < mapsCSV.length; i++)
                assertEquals(mapsCSV[i], mapsTSV[i]);
        }


        public void testObject() throws Exception
        {
            File tsv = _createTempFile(tsvData, ".tsv");
            TabLoader loader = new TabLoader(tsv, TestRow.class);
            TestRow[] rows = (TestRow[])loader.load();

            assertTrue(rows.length == 7);

            TestRow firstRow = rows[0];
            assertEquals(firstRow.getScan(), 96);
            assertFalse(firstRow.isAccurateMZ());
            assertEquals(firstRow.getDescription(), "description");
        }


        public void testTransform()
        {
            // UNDONE
        }


        public static Test suite()
        {
            return new TestSuite(TabLoaderTestCase.class);
        }
    }


    public static void main(String[] args) throws Exception
    {
        try
        {
            Class c = Class.forName("org.fhcrc.cpas.data.ConvertHelper");
            c.getMethod("registerHelpers").invoke(null);

            Test test = TabLoaderTestCase.suite();
            TestResult result = new TestResult();
            test.run(result);
            System.out.println(result.wasSuccessful() ? "success" : "fail");

            Enumeration failures = result.failures();
            Throwable first = null;
            while (failures.hasMoreElements())
            {
                TestFailure failure = (TestFailure) failures.nextElement();
                System.err.println(failure.toString());
                if (first == null)
                    first = failure.thrownException();
            }
            Enumeration errors = result.errors();
            while (errors.hasMoreElements())
            {
                TestFailure error = (TestFailure) errors.nextElement();
                System.err.println(error.toString());
                if (first == null)
                    first = error.thrownException();
            }
            if (first != null)
            {
                System.err.println("first exception");
                first.printStackTrace(System.err);
            }
        }
        catch (Throwable t)
        {
            t.printStackTrace(System.err);
        }
    }

}

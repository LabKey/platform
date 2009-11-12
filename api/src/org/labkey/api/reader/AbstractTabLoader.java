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
package org.labkey.api.reader;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.io.input.CharSequenceReader;
import org.apache.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.RowMapFactory;
import org.labkey.api.data.MvUtil;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.util.CloseableIterator;
import org.labkey.api.util.Filter;

import java.io.*;
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
public abstract class AbstractTabLoader<T> extends DataLoader<T>
{
    private static final Logger _log = Logger.getLogger(TabLoader.class);

    // source data
    private CharSequence _stringData = null;
    private Reader _reader;
    private int _commentLines = 0;

    private Map<String, String> _comments = new HashMap<String, String>();

    protected char _chDelimiter = '\t';
    protected String _strDelimiter = null;
    protected boolean _parseQuotes = false;
    protected boolean _throwOnErrors = false;
    private Filter<Map<String, Object>> _mapFilter;

    protected AbstractTabLoader(File inputFile, Boolean hasColumnHeaders) throws IOException
    {
        setSource(inputFile);
        init(hasColumnHeaders);
    }

    protected AbstractTabLoader(CharSequence src, Boolean hasColumnHeaders) throws IOException
    {
        if (src == null)
            throw new IllegalArgumentException("src cannot be null");

        _stringData = src;
        init(hasColumnHeaders);
    }

    protected AbstractTabLoader(Reader reader, Boolean hasColumnHeaders) throws IOException
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

        init(hasColumnHeaders);
    }


    private void init(Boolean hasColumnHeaders) throws IOException
    {
        if (null != hasColumnHeaders)
            setHasColumnHeaders(hasColumnHeaders.booleanValue());
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
            return new BufferedReader(new CharSequenceReader(_stringData));

        return new BufferedReader(new FileReader(_file));
    }

    public Map getComments() throws IOException
    {
        ensureInitialized();

        //noinspection unchecked
        return Collections.unmodifiableMap(_comments);
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
     * Note we don't handle values with embedded newlines
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


    public void setMapFilter(Filter<Map<String, Object>> mapFilter)
    {
        _mapFilter = mapFilter;
    }

    protected CloseableIterator<Map<String, Object>> mapIterator()
    {
        TabLoaderIterator iter;
        try
        {
            ensureInitialized();
            iter = new TabLoaderIterator();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        if (null == _mapFilter)
            return iter;
        else
            return new CloseableFilterIterator<Map<String, Object>>(iter, _mapFilter);
    }


    public void parseAsCSV()
    {
        setDelimiterCharacter(',');
        setParseQuotes(true);
    }

    public void setDelimiterCharacter(char delimiter)
    {
        _chDelimiter = delimiter;
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

    @Override
    public void close()
    {
        if (_reader != null)
        {
            try
            {
                _reader.close();
            }
            catch (IOException e)
            {
                // Ignore
            }
        }
    }

    @Override
    protected void initialize() throws IOException
    {
        readComments();
        super.initialize();
    }

    private void readComments() throws IOException
    {
        BufferedReader reader = getReader();

        while(true)
        {
            String s = reader.readLine();

            if (null == s)
                break;

            if (s.length() == 0 || s.charAt(0) == '#')
            {
                _commentLines++;

                int eq = s.indexOf('=');
                if (eq != -1)
                {
                    String key = s.substring(1, eq).trim();
                    String value = s.substring(eq + 1).trim();
                    if (key.length() > 0 || value.length() > 0)
                        _comments.put(key, value);
                }
            }
            else
            {
                break;
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


    public class TabLoaderIterator implements CloseableIterator<Map<String, Object>>
    {
        private final RowMapFactory<Object> factory;
        private final BufferedReader reader;

        private String line = null;
        private int lineNo = 0;

        protected TabLoaderIterator() throws IOException
        {
            reader = getReader();

            assert _skipLines != -1;

            for (int i = 0; i < (_commentLines + _skipLines); i++)
                reader.readLine();

            lineNo = _commentLines + _skipLines;

            Map<String, Integer> colMap = new CaseInsensitiveHashMap<Integer>();
            ColumnDescriptor[] columns = getColumns();

            for (int i = 0; i < columns.length; i++)
            {
                if (columns[i].load)
                    colMap.put(columns[i].name, i);
            }

            factory = new RowMapFactory<Object>(colMap);

            // find a converter for each column type
            for (ColumnDescriptor column : _columns)
                if (column.converter == null)
                    column.converter = ConvertUtils.lookup(column.clazz);
        }


        public void close()
        {
            try
            {
                if (null != reader)
                    reader.close();
            }
            catch (IOException x)
            {
                _log.error("Unexpected exception", x);
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


        public Map<String, Object> next()
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
                                    MvFieldWrapper mvWrapper = new MvFieldWrapper();
                                    mvWrapper.setValue( ("".equals(fld)) ?
                                        column.missingValues :
                                        column.converter.convert(column.clazz, fld));
                                    values[i] = mvWrapper;
                                    values[mvIndicatorIndex] = mvWrapper;
                                }
                                else
                                {
                                    // No such column. Is this a valid MV indicator or a valid value?
                                    if (MvUtil.isValidMvIndicator(fld, column.getMvContainer()))
                                    {
                                        MvFieldWrapper mvWrapper = new MvFieldWrapper();
                                        mvWrapper.setMvIndicator("".equals(fld) ? null : fld);
                                        values[i] = mvWrapper;
                                    }
                                    else
                                    {
                                        MvFieldWrapper mvWrapper = new MvFieldWrapper();
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
                            int qcColumnIndex = getMvColumnIndex(column);
                            if (qcColumnIndex != -1)
                            {
                                // There's an mv column that matches
                                if (values[qcColumnIndex] == null)
                                {
                                    MvFieldWrapper mvWrapper = new MvFieldWrapper();
                                    mvWrapper.setMvIndicator("".equals(fld) ? null : fld);
                                    values[qcColumnIndex] = mvWrapper;
                                    values[i] = mvWrapper;
                                }
                                else
                                {
                                    MvFieldWrapper mvWrapper = (MvFieldWrapper)values[qcColumnIndex];
                                    mvWrapper.setMvIndicator("".equals(fld) ? null : fld);
                                }
                                if (_throwOnErrors && !MvUtil.isValidMvIndicator(fld, column.getMvContainer()))
                                    throw new ConversionException(fld + " is not a valid MV indicator");
                            }
                            else
                            {
                                // No matching mv column, just put in a wrapper
                                if (!MvUtil.isValidMvIndicator(fld, column.getMvContainer()))
                                {
                                    throw new ConversionException(fld + " is not a valid MV indicator");
                                }
                                MvFieldWrapper mvWrapper = new MvFieldWrapper();
                                mvWrapper.setMvIndicator("".equals(fld) ? null : fld);
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
                            sb.append(lineNo);
                            sb.append(" in column #");
                            sb.append(i + 1);
                            sb.append(" (");
                            if (column.name.indexOf("#") != -1)
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

                        values[i] = column.errorValues;
                    }
                }

                return factory.getRowMap(values);
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
                    _log.error("failed loading file " + _file.getName() + " at line: " + lineNo + " " + e, e);
            }
            return null;
        }


        public void remove()
        {
            throw new UnsupportedOperationException("'remove()' is not defined for TabLoaderIterator");
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
            List<Map<String, Object>> maps = l.load();
            assertEquals(l.getColumns().length, 18);
            assertEquals(l.getColumns()[0].clazz, Date.class);
            assertEquals(l.getColumns()[1].clazz, Integer.class);
            assertEquals(l.getColumns()[2].clazz, Double.class);
            assertEquals(l.getColumns()[4].clazz, Boolean.class);
            assertEquals(l.getColumns()[17].clazz, String.class);
            assertEquals(maps.size(), 7);

            Map<String, Object> firstRow = maps.get(0);
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
            List<Map<String, Object>> maps = l.load();
            assertEquals(l.getColumns().length, 18);
            assertEquals(maps.size(), 7);
            r.close();
            csv.delete();
        }


        public void testCSVFile() throws IOException
        {
            File csv = _createTempFile(csvData, ".csv");

            TabLoader l = new TabLoader(csv);
            l.parseAsCSV();
            List<Map<String, Object>> maps = l.load();
            assertEquals(l.getColumns().length, 18);
            assertEquals(l.getColumns()[0].clazz, Date.class);
            assertEquals(l.getColumns()[1].clazz, Integer.class);
            assertEquals(l.getColumns()[2].clazz, Double.class);
            assertEquals(l.getColumns()[4].clazz, Boolean.class);
            assertEquals(l.getColumns()[17].clazz, String.class);
            assertEquals(maps.size(), 7);

            Map firstRow = maps.get(0);
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
            List<Map<String, Object>> maps = l.load();
            assertEquals(l.getColumns().length, 18);
            assertEquals(maps.size(), 7);
            r.close();
            csv.delete();
        }


        public void compareTSVtoCSV() throws IOException
        {
            TabLoader lCSV = new TabLoader(csvData, true);
            lCSV.parseAsCSV();
            List<Map<String, Object>> mapsCSV = lCSV.load();

            TabLoader lTSV = new TabLoader(tsvData, true);
            List<Map<String, Object>> mapsTSV = lTSV.load();

            assertEquals(lCSV.getColumns().length, lTSV.getColumns().length);
            assertEquals(mapsCSV.size(), mapsTSV.size());
            for (int i = 0; i < mapsCSV.size(); i++)
                assertEquals(mapsCSV.get(i), mapsTSV.get(i));
        }


        public void testObject() throws Exception
        {
            File tsv = _createTempFile(tsvData, ".tsv");
            BeanTabLoader<TestRow> loader = new BeanTabLoader<TestRow>(TestRow.class, tsv);

            List<TestRow> rows = loader.load();

            assertTrue(rows.size() == 7);

            TestRow firstRow = rows.get(0);
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
}

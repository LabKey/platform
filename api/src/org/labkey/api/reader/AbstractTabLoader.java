/*
 * Copyright (c) 2004-2010 Fred Hutchinson Cancer Research Center
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
import org.apache.commons.io.input.CharSequenceReader;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
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
    protected char _chQuote = '"';
    protected String _strQuote = null;
    protected String _strQuoteQuote = null;
    protected boolean _parseQuotes = true;
    protected boolean _unescapeBackslashes = true;
    private Filter<Map<String, Object>> _mapFilter;

    protected AbstractTabLoader(File inputFile, Boolean hasColumnHeaders) throws IOException
    {
        this(inputFile, hasColumnHeaders, null);
    }

    protected AbstractTabLoader(File inputFile, Boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
    {
        super(mvIndicatorContainer);
        setSource(inputFile);
        init(hasColumnHeaders);
    }

    protected AbstractTabLoader(CharSequence src, Boolean hasColumnHeaders) throws IOException
    {
        // This AbstractTabLoader constructor doesn't support MV Indicators:
        super(null);
        if (src == null)
            throw new IllegalArgumentException("src cannot be null");

        _stringData = src;
        init(hasColumnHeaders);
    }

    protected AbstractTabLoader(Reader reader, Boolean hasColumnHeaders) throws IOException
    {
        // This AbstractTabLoader constructor doesn't support MV Indicators:
        super(null);
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
    protected String parseValue(String value)
    {
        value = StringUtils.trimToEmpty(value);
        if ("\\N".equals(value))
            return "";
        if (_unescapeBackslashes)
            return StringEscapeUtils.unescapeJava(value);
        return value;
    }

    private ArrayList<String> listParse = new ArrayList<String>(30);


    private String readLine(BufferedReader r, boolean skipComments)
    {
        try
        {
            String line = null;
            do
            {
                line = r.readLine();
                if (line == null)
                    return null;
            }
            while (skipComments && (null == StringUtils.trimToNull(line) || line.charAt(0) == '#'));
            return line;
        }
        catch (Exception e)
        {
            _log.error("unexpected io error", e);
            throw new RuntimeException(e);
        }
    }

    Pattern _replaceDoubleQuotes = null;

    private String[] readFields(BufferedReader r)
    {
        if (!_parseQuotes)
        {
            if (_strDelimiter == null)
                _strDelimiter = new String(new char[]{_chDelimiter});
            String line = readLine(r, true);
            if (line == null)
                return null;
            String[] fields = line.split(_strDelimiter);
            for (int i = 0; i < fields.length; i++)
                fields[i] = parseValue(fields[i]);
            return fields;
        }

        String line = readLine(r, true);
        if (line == null)
            return null;
        StringBuffer buf = new StringBuffer(line.length());
        buf.append(line);

        String field;
        int start = 0;
        listParse.clear();

        while (start < buf.length())
        {
            int end;
            char ch = buf.charAt(start);
            if (ch == _chDelimiter)
            {
                end = start;
                field = "";
            }
            else if (ch == _chQuote)
            {
                if (_strQuote == null)
                {
                    _strQuote = new String(new char[] { _chQuote });
                    _strQuoteQuote = new String(new char[] { _chQuote, _chQuote });
                    _replaceDoubleQuotes = Pattern.compile("\\" + _chQuote + "\\" + _chQuote);
                }

                end = start;
                boolean hasQuotes = false;
                while (true)
                {
                    end = buf.indexOf(_strQuote, end + 1);
                    if (end == -1)
                    {
                        // XXX: limit number of lines we read
                        String nextLine = readLine(r, false);
                        if (nextLine == null)
                            throw new IllegalArgumentException("CSV can't parse line: " + buf);
                        end = buf.length();
                        buf.append('\n');
                        buf.append(nextLine);
                        continue;
                    }
                    if (end == buf.length() - 1 || buf.charAt(end + 1) != _chQuote)
                        break;
                    hasQuotes = true;
                    end++; // skip double ""
                }
                field = buf.substring(start + 1, end);
                if (hasQuotes && -1 != field.indexOf(_strQuoteQuote))
                    field = _replaceDoubleQuotes.matcher(field).replaceAll("\"");

                // eat final "
                end++;

                //FIX: 9727
                //if not at end of line and next char is not a tab, append any chars to field up to the next tab/eol
                //note that this is a surgical quick-fix due to the proximity of release.
                //the better fix would be to parse the file character-by-character and support
                //double quotes anywhere within the field to escape delimiters
                if (end < buf.length() && buf.charAt(end) != _chDelimiter)
                {
                    start = end;
                    end = buf.indexOf(_strDelimiter, end);
                    if (-1 == end)
                        end = buf.length();
                    field = field + buf.substring(start, end);
                }
            }
            else
            {
                if (_strDelimiter == null)
                    _strDelimiter = new String(new char[]{_chDelimiter});
                end = buf.indexOf(_strDelimiter, start);
                if (end == -1)
                    end = buf.length();
                field = buf.substring(start, end);
                field = parseValue(field);
            }

            listParse.add(field);

            // there should be a comma or an EOL here
            if (end < buf.length() && buf.charAt(end) != _chDelimiter)
                throw new IllegalArgumentException("CSV can't parse line: " + buf);
            end++;
            while (end < buf.length() && buf.charAt(end) != _chDelimiter && Character.isWhitespace(buf.charAt(end)))
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

    public void setUnescapeBackslashes(boolean unescapeBackslashes)
    {
        _unescapeBackslashes = unescapeBackslashes;
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
            List<String[]> lineFields = new ArrayList<String[]>(n);
            int i;

            for (i = 0; i < n; i++)
            {
                String[] fields = readFields(reader);
                if (null == fields)
                    break;
                lineFields.add(fields);
            }

            if (i == 0)
                return new String[0][];

            return lineFields.toArray(new String[i][]);
        }
        finally
        {
            try {reader.close();} catch (IOException ioe) {}
        }
    }


    public class TabLoaderIterator extends DataLoaderIterator
    {
        private final BufferedReader reader;

        protected TabLoaderIterator() throws IOException
        {
            super(_commentLines + _skipLines, false);
            assert _skipLines != -1;

            reader = getReader();
            for (int i = 0; i < lineNum(); i++)
                reader.readLine();
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

        @Override
        protected String[] readFields()
        {
            return AbstractTabLoader.this.readFields(reader);
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

        public void testUnescape() throws Exception
        {
            final String data =
                    "A\tMulti-Line\tB\n" +
                    "a\tthis\\nis\\tmulti-line\tb\n" +
                    "\tthis\\nis\\tmulti-line\tb\n";

            TabLoader loader = new TabLoader(data, true);
            loader.setUnescapeBackslashes(true);
            List<Map<String, Object>> rows = loader.load();
            assertEquals(2, rows.size());

            Map<String, Object> row = rows.get(0);
            assertEquals("a", row.get("A"));
            assertEquals("this\nis\tmulti-line", row.get("Multi-Line"));
            assertEquals("b", row.get("B"));

            row = rows.get(1);
            assertEquals(null, row.get("A"));
            assertEquals("this\nis\tmulti-line", row.get("Multi-Line"));
            assertEquals("b", row.get("B"));

            // now test no-unescaping
            loader = new TabLoader(data, true);
            loader.setUnescapeBackslashes(false);
            rows = loader.load();
            assertEquals(2, rows.size());

            row = rows.get(0);
            assertEquals("a", row.get("A"));
            assertEquals("this\\nis\\tmulti-line", row.get("Multi-Line"));
            assertEquals("b", row.get("B"));

            row = rows.get(1);
            assertEquals(null, row.get("A"));
            assertEquals("this\\nis\\tmulti-line", row.get("Multi-Line"));
            assertEquals("b", row.get("B"));

        }

        public void testParseQuotes() throws Exception
        {
            final String data =
                    "Name\tMulti-Line\tAge\n" +
                    "Bob\t\"apple\norange\tgrape\"\t3\n" +
                    "Bob\t\"one\n\"\"two\"\"\tthree\"\n" +
                    "\tred\\nblue\\tgreen\t4\n" +
                    "Fred\t\"quoted stuff\" unquoted\t1";
            TabLoader loader = new TabLoader(data, true);
            loader.setParseQuotes(true);
            loader.setUnescapeBackslashes(true);

            List<Map<String, Object>> rows = loader.load();
            assertEquals(4, rows.size());

            Map<String, Object> row = rows.get(0);
            assertEquals("Bob", row.get("Name"));
            assertEquals("apple\norange\tgrape", row.get("Multi-Line"));
            assertEquals(3, row.get("Age"));

            row = rows.get(1);
            assertEquals("Bob", row.get("Name"));
            assertEquals("one\n\"two\"\tthree", row.get("Multi-Line"));
            assertEquals(null, row.get("Age"));

            row = rows.get(2);
            assertEquals(null, row.get("Name"));
            assertEquals("red\nblue\tgreen", row.get("Multi-Line"));
            assertEquals(4, row.get("Age"));

            // now test no-unescaping
            loader = new TabLoader(data, true);
            loader.setParseQuotes(true);
            loader.setUnescapeBackslashes(false);

            rows = loader.load();
            assertEquals(4, rows.size());

            row = rows.get(0);
            assertEquals("Bob", row.get("Name"));
            assertEquals("apple\norange\tgrape", row.get("Multi-Line"));
            assertEquals(3, row.get("Age"));

            row = rows.get(1);
            assertEquals("Bob", row.get("Name"));
            assertEquals("one\n\"two\"\tthree", row.get("Multi-Line"));
            assertEquals(null, row.get("Age"));

            row = rows.get(2);
            assertEquals(null, row.get("Name"));
            assertEquals("red\\nblue\\tgreen", row.get("Multi-Line"));
            assertEquals(4, row.get("Age"));

            row = rows.get(3);
            assertEquals("Fred", row.get("Name"));
            assertEquals("quoted stuff unquoted", row.get("Multi-Line"));
            assertEquals(1, row.get("Age"));
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

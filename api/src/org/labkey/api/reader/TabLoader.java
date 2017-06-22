/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CharSequenceReader;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.iterator.BeanIterator;
import org.labkey.api.iterator.CloseableFilteredIterator;
import org.labkey.api.iterator.CloseableIterator;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Filter;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.writer.PrintWriters;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringBufferInputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;


/**
 * Parses rows of tab-delimited text, returning a CloseableIterator of Map<String, Object>.  The iterator must be closed
 * (typically in a finally block) to close the underlying input source.  The iterator can be wrapped with a BeanIterator
 * (to provide beans) and/or a CloseableFilteredIterator (to filter the iterator).
 * <p/>
 * NOTE: Column descriptors should not be changed in the midst of iterating; a single set of column descriptors is used
 * to key all the maps.
 * <p/>
 * User: migra
 * Date: Jun 28, 2004
 * Time: 2:25:19 PM
 */
public class TabLoader extends DataLoader
{
    public static final FileType TSV_FILE_TYPE = new TabFileType(Arrays.asList(".tsv", ".txt"), ".tsv", "text/tab-separated-values");
    public static final FileType CSV_FILE_TYPE = new TabFileType(Collections.singletonList(".csv"), ".csv", "text/comma-separated-values");

    private static final Logger _log = Logger.getLogger(TabLoader.class);

    public static class TsvFactory extends AbstractDataLoaderFactory
    {
        @NotNull @Override
        public DataLoader createLoader(File file, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
        {
            return new TabLoader(file, hasColumnHeaders, mvIndicatorContainer);
        }

        /** A DataLoader created with this constructor does NOT close the reader */
        @NotNull @Override
        public DataLoader createLoader(InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
        {
            return new TabLoader(new InputStreamReader(is, StandardCharsets.UTF_8), hasColumnHeaders, mvIndicatorContainer);
        }

        @NotNull @Override
        public FileType getFileType() { return TSV_FILE_TYPE; }
    }

    public static class CsvFactory extends AbstractDataLoaderFactory
    {
        @NotNull @Override
        public DataLoader createLoader(File file, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
        {
            TabLoader loader = new TabLoader(file, hasColumnHeaders, mvIndicatorContainer);
            loader.parseAsCSV();
            return loader;
        }

        @NotNull @Override
        // A DataLoader created with this constructor does NOT close the reader
        public DataLoader createLoader(InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
        {
            TabLoader loader = new TabLoader(new InputStreamReader(is, StandardCharsets.UTF_8), hasColumnHeaders, mvIndicatorContainer);
            loader.parseAsCSV();
            return loader;
        }

        @NotNull @Override
        public FileType getFileType() { return CSV_FILE_TYPE; }
    }

    public static class CsvFactoryNoConversions extends CsvFactory
    {
        @NotNull @Override
        public DataLoader createLoader(File file, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
        {

            DataLoader loader = super.createLoader(file, hasColumnHeaders, mvIndicatorContainer);
            loader.setInferTypes(false);
            return loader;
        }

        @NotNull @Override
        // A DataLoader created with this constructor does NOT close the reader
        public DataLoader createLoader(InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
        {
            DataLoader loader = super.createLoader(is, hasColumnHeaders, mvIndicatorContainer);
            loader.setInferTypes(false);
            return loader;
        }

        @NotNull @Override
        public FileType getFileType() { return CSV_FILE_TYPE; }
    }

    public static class MysqlFactory extends AbstractDataLoaderFactory
    {
        String fieldTerminator="~@~";
        String lineTerminator="~@@~";

        @NotNull @Override
        public DataLoader createLoader(File file, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
        {
            TabLoader loader = new TabLoader(file, hasColumnHeaders, mvIndicatorContainer);
            loader.setDelimiters(fieldTerminator, lineTerminator);
            loader.setParseQuotes(false);
            return loader;
        }

        @NotNull @Override
        public DataLoader createLoader(InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
        {
            TabLoader loader = new TabLoader(Readers.getBOMDetectingReader(is), hasColumnHeaders, mvIndicatorContainer);
            loader.setDelimiters(fieldTerminator,lineTerminator);
            loader.setParseQuotes(false);
            return loader;
        }

        @NotNull @Override
        public FileType getFileType() { return CSV_FILE_TYPE; }
    }


    protected static char COMMENT_CHAR = '#';

    // source data
    private final ReaderFactory _readerFactory;
    private BufferedReader _reader = null;

    private int _commentLines = 0;
    private Map<String, String> _comments = new HashMap<>();
    private char _chDelimiter = '\t';
    private String _strDelimiter = new String(new char[]{_chDelimiter});
    private String _lineDelimiter = null;

    private String _strQuote = null;
    private String _strQuoteQuote = null;
    private boolean _parseQuotes = true;
    private boolean _unescapeBackslashes = true;
    private Filter<Map<String, Object>> _mapFilter;

    // Infer whether there are headers
    public TabLoader(File inputFile) throws IOException
    {
        this(inputFile, null);
    }

    public TabLoader(File inputFile, Boolean hasColumnHeaders) throws IOException
    {
        this(inputFile, hasColumnHeaders, null);
    }

    public TabLoader(final File inputFile, Boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
    {
        this(() -> {
            verifyFile(inputFile);
            // Detect Charset encoding using BOM
            return Readers.getBOMDetectingReader(inputFile);
        }, hasColumnHeaders, mvIndicatorContainer);

        setScrollable(true);
    }

    // Infer whether there are headers
    public TabLoader(CharSequence src) throws IOException
    {
        this(src, null);
    }

    // This constructor doesn't support MV Indicators:
    public TabLoader(final CharSequence src, Boolean hasColumnHeaders) throws IOException
    {
        this(() -> new BufferedReader(new CharSequenceReader(src)), hasColumnHeaders, null);

        if (src == null)
            throw new IllegalArgumentException("src cannot be null");

        setScrollable(true);
    }

    /** A TabLoader created with this constructor does NOT close the reader */
    public TabLoader(Reader reader, Boolean hasColumnHeaders) throws IOException
    {
        this(reader, hasColumnHeaders, null);
    }
    
    /** A TabLoader created with this constructor does NOT close the reader */
    public TabLoader(Reader reader, Boolean hasColumnHeaders, @Nullable Container mvIndicatorContainer) throws IOException
    {
        this(reader, hasColumnHeaders, mvIndicatorContainer, false);
    }

    /** A TabLoader created with this constructor closes the reader only if closeOnComplete is true */
    public TabLoader(final Reader reader, Boolean hasColumnHeaders, @Nullable Container mvIndicatorContainer, final boolean closeOnComplete) throws IOException
    {
        this(new ReaderFactory()
        {
            private boolean _closed = false;

            @Override
            public BufferedReader getReader()
            {
                if (_closed)
                    throw new IllegalStateException("Reader is closed");

                // Customize close() behavior to track closing and handle closeOnComplete
                return new BufferedReader(reader)
                {
                    @Override
                    public void close() throws IOException
                    {
                        _closed = true;

                        if (closeOnComplete)
                            super.close();
                    }
                };
            }
        }, hasColumnHeaders, mvIndicatorContainer);

        setScrollable(false);
    }


    private TabLoader(ReaderFactory factory, Boolean hasColumnHeaders, @Nullable Container mvIndicatorContainer) throws IOException
    {
        super(mvIndicatorContainer);

        _readerFactory = factory;

        if (null != hasColumnHeaders)
            setHasColumnHeaders(hasColumnHeaders);
    }


    protected BufferedReader getReader() throws IOException
    {
        if (null == _reader)
        {
            _reader = _readerFactory.getReader();
            // Issue 23437 - use a reasonably high limit for buffering
            _reader.mark(10 * 1024 * 1024);
        }

        return _reader;
    }

    public Map<String, String> getComments() throws IOException
    {
        ensureInitialized();

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
            return _preserveEmptyString ? null : "";
        if (_unescapeBackslashes)
        {
            try
            {
                return StringEscapeUtils.unescapeJava(value);
            }
            catch (IllegalArgumentException e)
            {
                // Issue 16691: OctalUnescaper or UnicodeUnescaper translators will throw NumberFormatException for illegal sequences such as '\' followed by octal '9' or unicode 'zzzz'.
                // StringEscapeUtils can also throw IllegalArgumentException
                String msg = "Error reading data. Can't unescape value '" + value + "'. ";
                if (e instanceof NumberFormatException)
                    msg += "Number format error ";
                msg += e.getMessage();
                if (isThrowOnErrors())
                    throw new IllegalArgumentException(msg, e);
                else
                    _log.warn(msg);
            }
        }
        return value;
    }

    private ArrayList<String> listParse = new ArrayList<>(30);



    private CharSequence readLine(BufferedReader r, boolean skipComments, boolean skipBlankLines)
    {
        String line = readOneTextLine(r, skipComments, skipBlankLines);
        if (null == line || null == _lineDelimiter)
            return line;
        if (line.endsWith(_lineDelimiter))
            return line.substring(0,line.length()-_lineDelimiter.length());
        StringBuilder sb = new StringBuilder(line);
        while (null != (line = readOneTextLine(r, false, false)))
        {
            sb.append("\n");
            if (line.endsWith(_lineDelimiter))
            {
                sb.append(line.substring(0,line.length()-_lineDelimiter.length()));
                return sb;
            }
            sb.append(line);
        }
        return sb;
    }


    private String readOneTextLine(BufferedReader r, boolean skipComments, boolean skipBlankLines)
    {
        try
        {
            String line;
            do
            {
                line = r.readLine();
                if (line == null)
                    return null;
            }
            while ((skipComments && line.length() > 0 && line.charAt(0) == COMMENT_CHAR) || (skipBlankLines && null == StringUtils.trimToNull(line)));
            return line;
        }
        catch (Exception e)
        {
            _log.error("unexpected io error", e);
            throw new RuntimeException(e);
        }
    }

    Pattern _replaceDoubleQuotes = null;

    private String[] readFields(BufferedReader r, @Nullable ColumnDescriptor[] columns) throws IOException
    {
        if (!_parseQuotes)
        {
            CharSequence line = readLine(r, true, !isIncludeBlankLines());
            if (line == null)
                return null;
            String[] fields = StringUtils.splitByWholeSeparator(line.toString(), _strDelimiter);
            for (int i = 0; i < fields.length; i++)
                fields[i] = parseValue(fields[i]);
            return fields;
        }

        CharSequence line = readLine(r, true, !isIncludeBlankLines());
        if (line == null)
            return null;
        StringBuilder buf = line instanceof StringBuilder ? (StringBuilder)line : new StringBuilder(line);

        String field = null;
        int start = 0, colIndex = 0;
        listParse.clear();

        while (start < buf.length())
        {
            boolean loadThisColumn = null==columns || colIndex >= columns.length || columns[colIndex].load;
            int end;
            char ch = buf.charAt(start);
            char chQuote = '"';

            colIndex++;

            if (ch == _chDelimiter)
            {
                end = start;
                field = _preserveEmptyString ? null : "";
            }
            else if (ch == chQuote)
            {
                if (_strQuote == null)
                {
                    _strQuote = new String(new char[] {chQuote});
                    _strQuoteQuote = new String(new char[] {chQuote, chQuote});
                    _replaceDoubleQuotes = Pattern.compile("\\" + chQuote + "\\" + chQuote);
                }

                end = start;
                boolean hasQuotes = false;

                while (true)
                {
                    end = buf.indexOf(_strQuote, end + 1);

                    if (end == -1)
                    {
                        // XXX: limit number of lines we read
                        CharSequence nextLine = readLine(r, false, false);
                        end = buf.length();
                        if (nextLine == null)
                        {
                            // We've reached the end of the input, so there's nothing else to append
                            break;
                        }

                        buf.append('\n');
                        buf.append(nextLine);
                        continue;
                    }

                    if (end == buf.length() - 1 || buf.charAt(end + 1) != chQuote)
                        break;
                    hasQuotes = true;
                    end++; // skip double ""
                }

                field = buf.substring(start + 1, end);
                if (hasQuotes && field.contains(_strQuoteQuote))
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
                end = buf.indexOf(_strDelimiter, start);
                if (end == -1)
                    end = buf.length();

                // Grab and parse the field only if we're going to load it
                if (loadThisColumn)
                {
                    field = buf.substring(start, end);
                    field = parseValue(field);
                }
            }

            // Add the field value only if we're inferring columns or column.load == true
            if (loadThisColumn)
                listParse.add(field);

            // there should be a delimiter or an EOL here
            if (end < buf.length() && buf.charAt(end) != _chDelimiter)
                throw new IllegalArgumentException("Can't parse line: " + buf);

            end += _strDelimiter.length();

            while (end < buf.length() && buf.charAt(end) != _chDelimiter && Character.isWhitespace(buf.charAt(end)))
                end++;

            start = end;
        }

        return listParse.toArray(new String[listParse.size()]);
    }

    @Deprecated // Just use a CloseableFilteredIterator.  TODO: Remove
    public void setMapFilter(Filter<Map<String, Object>> mapFilter)
    {
        _mapFilter = mapFilter;
    }

    public CloseableIterator<Map<String, Object>> iterator()
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
            return new CloseableFilteredIterator<>(iter, _mapFilter);
    }


    public void parseAsCSV()
    {
        setDelimiterCharacter(',');
        setParseQuotes(true);
    }

    public void setDelimiterCharacter(char delimiter)
    {
        _chDelimiter = delimiter;
        _strDelimiter = new String(new char[]{_chDelimiter});
    }

    public void setDelimiters(@NotNull String field, @Nullable String line)
    {
        if (StringUtils.isEmpty(field))
            throw new IllegalArgumentException();
        _chDelimiter = field.charAt(0);
        _strDelimiter = field;
        _lineDelimiter = StringUtils.isEmpty(line) ? null : line;
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
        IOUtils.closeQuietly(_reader);
        _reader = null;
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

        try
        {
            while (true)
            {
                String s = reader.readLine();

                if (null == s)
                    break;

                if (s.length() == 0 || s.charAt(0) == COMMENT_CHAR)
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
        finally
        {
            reader.reset();
        }
    }

    public String[][] getFirstNLines(int n) throws IOException
    {
        BufferedReader reader = getReader();

        try
        {
            List<String[]> lineFields = new ArrayList<>(n);
            int i;

            for (i = 0; i < n; i++)
            {
                String[] fields = readFields(reader, null);
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
            reader.reset();
        }
    }


    public class TabLoaderIterator extends DataLoaderIterator
    {
        private final BufferedReader reader;

        protected TabLoaderIterator() throws IOException
        {
            super(_commentLines + _skipLines);
            assert _skipLines != -1;

            reader = getReader();
            for (int i = 0; i < lineNum(); i++)
                reader.readLine();

            // make sure _columns is initialized
            ColumnDescriptor[] cols = getColumns();

            // all input starts as String, we don't need to use a String converter
            // unless a column has configured a custom converter (e.g ViabilityTsvDataHandler)
            for (ColumnDescriptor col : cols)
            {
                if (col.converter == StringConverter && col.clazz == String.class)
                    col.converter = noopConverter;
            }
        }

        public void close() throws IOException
        {
            try
            {
                TabLoader.this.close();
            }
            finally
            {
                super.close();
            }
        }

        @Override
        protected String[] readFields() throws IOException
        {
            return TabLoader.this.readFields(reader, _columns);
        }
    }

    public static class TabLoaderTestCase extends Assert
    {
        String malformedCsvData =
                "\"Header1\",\"Header2\", \"Header3\"\n" +
                        "\"test1a\", \"testb";

        String csvData =
                "# algorithm=org.fhcrc.cpas.viewer.feature.FeatureStrategyPeakClusters\n" +
                        "# date=Mon May 22 13:25:28 PDT 2006\n" +
                        "# java.vendor=Sun Microsystems Inc.\n" +
                        "# java.version=1.5.0_06\n" +
                        "# revision=rev1.1\n" +
                        "# user.name=Matthew\n" +
                        "date,scan,time,mz,accurateMZ,mass,intensity,charge,chargeStates,kl,background,median,peaks,scanFirst,scanLast,scanCount,totalIntensity,description\n" +
                        "1/2/2006,96,1543.3401,858.3246,FALSE,1714.6346,2029.6295,2,1,0.19630894,26.471083,12.982442,4,92,100,9,20248.762,description\n" +
/*empty int*/   "2/Jan/2006,100,1560.348,858.37555,FALSE,1714.7366,1168.3536,2,1,0.033085547,63.493385,8.771278,5,101,119,19,17977.979,\"desc\"\"ion\"\n" +
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
/*empty int*/   "2/Jan/2006\t100\t1560.348\t858.37555\tFALSE\t1714.7366\t1168.3536\t2\t1\t0.033085547\t63.493385\t8.771278\t5\t101\t119\t19\t17977.979\tdesc\"ion\n" +
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

            try (Writer w = PrintWriters.getPrintWriter(f))
            {
                w.write(data);
            }

            return f;
        }


        @Test
        public void testMalformedCsv() throws IOException
        {
            File csv = _createTempFile(malformedCsvData, ".csv");

            TabLoader l = new TabLoader(csv);
            l.parseAsCSV();
            List<Map<String, Object>> maps = l.load();
            assertEquals(l.getColumns().length, 3);
            assertEquals(String.class, l.getColumns()[0].clazz);
            assertEquals(String.class, l.getColumns()[1].clazz);
            assertEquals(String.class, l.getColumns()[2].clazz);
            assertEquals(2, maps.size());

            assertEquals("Header1", maps.get(0).get("column0"));
            assertEquals("Header2", maps.get(0).get("column1"));
            assertEquals("Header3", maps.get(0).get("column2"));

            assertEquals("test1a", maps.get(1).get("column0"));
            assertEquals("testb", maps.get(1).get("column1"));
            assertEquals(null, maps.get(1).get("column2"));
        }

        @Test
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


        @Test
        public void testTSVReader() throws IOException
        {
            File csv = _createTempFile(tsvData, ".tsv");
            Reader r = Readers.getReader(csv);
            TabLoader l = new TabLoader(r, true);
            List<Map<String, Object>> maps = l.load();
            assertEquals(l.getColumns().length, 18);
            assertEquals(maps.size(), 7);
            r.close();
            csv.delete();
        }


        @Test
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

        @Test
        public void testCSVReader() throws IOException
        {
            File csv = _createTempFile(csvData, ".csv");
            Reader r = Readers.getReader(csv);
            TabLoader l = new TabLoader(r, true);
            l.parseAsCSV();
            List<Map<String, Object>> maps = l.load();
            assertEquals(l.getColumns().length, 18);
            assertEquals(maps.size(), 7);
            r.close();
            csv.delete();
        }


        @Test
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


        @Test
        public void testObject() throws Exception
        {
            TabLoader tl = new TabLoader(tsvData);
            CloseableIterator<TestRow> iter = new BeanIterator<>(tl.iterator(), TestRow.class);

            assertTrue(iter.hasNext());
            TestRow firstRow = iter.next();

            assertEquals(firstRow.getScan(), 96);
            assertFalse(firstRow.isAccurateMZ());
            assertEquals(firstRow.getDescription(), "description");

            int count = 1;

            while (iter.hasNext())
            {
                iter.next();
                count++;
            }

            iter.close();
            assertTrue(count == 7);
        }

        @Test
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

        @Test
        public void testEmptyRow() throws Exception
        {
            final String data =
                    "A\tB\n" +
                    "first\tline\n" +
                    "\n" +
                    "# comment\n" +
                    "second\tline\n";

            for (int i = 0; i < 1; i++)
            {
                boolean parseQuotes = i == 0;

                // default behavior is to skip blank lines
                {
                    TabLoader loader = new TabLoader(data, true);
                    loader.setInferTypes(false);
                    loader.setParseQuotes(parseQuotes);
                    List<Map<String, Object>> rows = loader.load();
                    assertEquals(2, rows.size());

                    Map<String, Object> row = rows.get(0);
                    assertEquals("first", row.get("A"));

                    row = rows.get(1);
                    assertEquals("second", row.get("A"));
                }

                // include blank lines as row of all null values
                {
                    TabLoader loader = new TabLoader(data, true);
                    loader.setInferTypes(false);
                    loader.setParseQuotes(parseQuotes);
                    loader.setIncludeBlankLines(true);
                    List<Map<String, Object>> rows = loader.load();
                    assertEquals(3, rows.size());

                    Map<String, Object> row = rows.get(0);
                    assertEquals("first", row.get("A"));

                    row = rows.get(1);
                    assertEquals(null, row.get("A"));
                    assertEquals(null, row.get("B"));

                    row = rows.get(2);
                    assertEquals("second", row.get("A"));
                }

            }
        }

        @Test
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


        @Test
        public void testMySql() throws IOException
        {
            String mysqlData =
                    "3072~@~\\N~@~biotinylated antihuIgG antibody~@~ESR8865~@~2149~@@~\n" +
                    "3073~@~Multiline\n" +
                            "description~@~anti-huIgM antibody~@~ESR8866~@~2149~@@~\n" +
                    "3074~@~short~description~@~anti-huIgA antibody~@~ESR8867~@~2149~@@~\n" +
                    "3075~@~description with\n" +
                            "\n" +
                            "\n" +
                            "\n" +
                            "blank lines\n" +
                            "\n~@~avidin-D-HRP conjugate~@~ESR8868~@~2149~@@~\n";
            TabLoader loader = (TabLoader)new TabLoader.MysqlFactory().createLoader(new StringBufferInputStream(mysqlData), false, null);
            loader.setColumns(new ColumnDescriptor[] {new ColumnDescriptor("analyte_id"), new ColumnDescriptor("description"), new ColumnDescriptor("name"),new ColumnDescriptor("reagent_ascession"),new ColumnDescriptor("workspace_id")});
            loader.setDelimiters("~@~","~@@~");
            List<Map<String, Object>> rows = loader.load();
            loader.close();

            assertEquals(4, rows.size());

            Map<String, Object> row = rows.get(0);
            assertEquals("biotinylated antihuIgG antibody", row.get("name"));
            assertNull(row.get("description"));

            row = rows.get(1);
            assertEquals("anti-huIgM antibody", row.get("name"));
            assertEquals("Multiline\ndescription",row.get("description"));

            row = rows.get(2);
            assertEquals("anti-huIgA antibody", row.get("name"));
            assertEquals("short~description",row.get("description"));

            row = rows.get(3);
            assertEquals("avidin-D-HRP conjugate", row.get("name"));
            assertEquals("description with\n\n\n\nblank lines",row.get("description"));
        }

        @Test
        public void testTransform()
        {
            // UNDONE
        }
    }

    public static class HeaderMatchTest extends Assert
    {
        @Test
        public void testHeader()
        {
            TabFileType f = (TabFileType)TabLoader.TSV_FILE_TYPE;

            // Issue 22171: TabFileType can't match with non-ASCII characters in header row
            assertTrue(f.isHeader("Volume (\u00b5l)"));
        }

        // Test sniffing using only the file header and not the file extension
        private boolean isType(FileType ft, File f) throws IOException
        {
            byte[] header = FileUtil.readHeader(f, 8 * 1024);
            return ft.isType((String)null, null, header);
        }

        @Test
        public void testSniff() throws IOException
        {
            // File has comment headers
            assertTrue(isType(TabLoader.TSV_FILE_TYPE, JunitUtil.getSampleData(null, "ms1/bvt/inspect/Find Features/msi-sample.peptides.tsv")));

            assertFalse(isType(TabLoader.TSV_FILE_TYPE, JunitUtil.getSampleData(null, "Nab/384well_highthroughput.csv")));
            assertTrue(isType(TabLoader.CSV_FILE_TYPE, JunitUtil.getSampleData(null, "Nab/384well_highthroughput.csv")));

            // TODO: Support files without headers
            //assertTrue(isType(TabLoader.CSV_FILE_TYPE, JunitUtil.getSampleData(null, "viability/small.VIA.csv")));

            // binary files
            assertFalse(isType(TabLoader.TSV_FILE_TYPE, JunitUtil.getSampleData(null, "flow/8color/L02-060120-QUV-JS/91745.fcs")));
            assertFalse(isType(TabLoader.TSV_FILE_TYPE, JunitUtil.getSampleData(null, "FolderExport/Sample.folder.zip")));
        }
    }
}

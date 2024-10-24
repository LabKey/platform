/*
 * Copyright (c) 2004-2018 Fred Hutchinson Cancer Research Center
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.dataiterator.HashDataIterator;
import org.labkey.api.iterator.BeanIterator;
import org.labkey.api.iterator.CloseableIterator;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.writer.PrintWriters;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringBufferInputStream;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * Parses rows of tab-delimited text, returning a CloseableIterator of Map<String, Object>. The iterator must be closed
 * (typically via try-with-resources or a finally block) to close the underlying input source. The iterator can be wrapped
 * with a BeanIterator (to provide beans) and/or a CloseableFilteredIterator (to filter the iterator).
 * <p/>
 * NOTE: Column descriptors should not be changed in the midst of iterating; a single set of column descriptors is used
 * to key all the maps.
 * <p/>
 */
public class TabLoader extends DataLoader
{
    public static final FileType TSV_FILE_TYPE = new TabFileType(Arrays.asList(".tsv", ".txt"), ".tsv", "text/tab-separated-values");
    public static final FileType CSV_FILE_TYPE = new TabFileType(Collections.singletonList(".csv"), ".csv", "text/comma-separated-values");

    private boolean _includeComments = false;

    private static final Logger _log = LogManager.getLogger(TabLoader.class);

    public static class TsvFactory extends AbstractDataLoaderFactory
    {
        @NotNull @Override
        public DataLoader createLoader(File file, boolean hasColumnHeaders, Container mvIndicatorContainer)
        {
            return new TabLoader(file, hasColumnHeaders, mvIndicatorContainer);
        }

        /** A DataLoader created with this constructor does NOT close the reader */
        @NotNull @Override
        public DataLoader createLoader(InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer)
        {
            return new TabLoader(new InputStreamReader(is, StandardCharsets.UTF_8), hasColumnHeaders, mvIndicatorContainer);
        }

        @NotNull @Override
        public FileType getFileType() { return TSV_FILE_TYPE; }
    }

    public static class CsvFactory extends AbstractDataLoaderFactory
    {
        @NotNull @Override
        public TabLoader createLoader(File file, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
        {
            TabLoader loader = new TabLoader(file, hasColumnHeaders, mvIndicatorContainer);
            loader.parseAsCSV();
            return loader;
        }

        @NotNull @Override
        // A TabLoader created with this constructor does NOT close the reader
        public TabLoader createLoader(InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
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
        public TabLoader createLoader(File file, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
        {
            TabLoader loader = super.createLoader(file, hasColumnHeaders, mvIndicatorContainer);
            return configParsing(loader);
        }

        private TabLoader configParsing(TabLoader loader)
        {
            loader.setInferTypes(false);
            // Issue 43661 - Excessive logging when indexing a .log file containing backslash followed by "u" that confuses TabLoader
            loader.setUnescapeBackslashes(false);
            return loader;
        }

        @NotNull @Override
        // A TabLoader created with this constructor does NOT close the reader
        public TabLoader createLoader(InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
        {
            TabLoader loader = super.createLoader(is, hasColumnHeaders, mvIndicatorContainer);
            return configParsing(loader);
        }

        @NotNull @Override
        public FileType getFileType() { return CSV_FILE_TYPE; }
    }

    public static class MysqlFactory extends AbstractDataLoaderFactory
    {
        String fieldTerminator="~@~";
        String lineTerminator="~@@~";

        @NotNull @Override
        public DataLoader createLoader(File file, boolean hasColumnHeaders, Container mvIndicatorContainer)
        {
            TabLoader loader = new TabLoader(file, hasColumnHeaders, mvIndicatorContainer);
            loader.setUnescapeBackslashes(false);
            loader.setDelimiters(fieldTerminator, lineTerminator);
            loader.setParseQuotes(false);
            return loader;
        }

        @NotNull @Override
        public DataLoader createLoader(InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
        {
            TabLoader loader = new TabLoader(Readers.getBOMDetectingReader(is), hasColumnHeaders, mvIndicatorContainer);
            loader.setUnescapeBackslashes(false);
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
    private final Map<String, String> _comments = new HashMap<>();

    private TabBufferedReader _reader = null;
    private int _commentLines = 0;
    private char _chDelimiter = '\t';
    private String _strDelimiter = String.valueOf(_chDelimiter);
    private String _lineDelimiter = null;

    private String _strQuote = null;
    private String _strQuoteQuote = null;
    private boolean _parseQuotes = true;
    private boolean _parseEnclosedQuotes = false; // only treat quote as quote if it comes in pairs, otherwise treat it as a regular character
    private boolean _unescapeBackslashes = true;

    // Infer whether there are headers
    public TabLoader(File inputFile)
    {
        this(inputFile, null);
    }

    public TabLoader(File inputFile, Boolean hasColumnHeaders)
    {
        this(inputFile, hasColumnHeaders, null);
    }

    public TabLoader(final File inputFile, Boolean hasColumnHeaders, Container mvIndicatorContainer)
    {
        this(() -> {
            verifyFile(inputFile);
            // Detect Charset encoding using BOM
            return new TabBufferedReader(Readers.getBOMDetectingUnbufferedReader(inputFile), inputFile.length());
        }, hasColumnHeaders, mvIndicatorContainer);

        setScrollable(true);
    }

    // Infer whether there are headers
    public TabLoader(CharSequence src)
    {
        this(src, null);
    }

    // This constructor doesn't support MV Indicators:
    public TabLoader(final CharSequence src, Boolean hasColumnHeaders)
    {
        this(() -> new TabBufferedReader(new CharSequenceReader(src)), hasColumnHeaders, null);

        if (src == null)
            throw new IllegalArgumentException("src cannot be null");

        setScrollable(true);
    }

    /** A TabLoader created with this constructor does NOT close the reader */
    public TabLoader(Reader reader, Boolean hasColumnHeaders)
    {
        this(reader, hasColumnHeaders, null);
    }
    
    /** A TabLoader created with this constructor does NOT close the reader */
    public TabLoader(Reader reader, Boolean hasColumnHeaders, @Nullable Container mvIndicatorContainer)
    {
        this(reader, hasColumnHeaders, mvIndicatorContainer, false);
    }

    /** A TabLoader created with this constructor closes the reader only if closeOnComplete is true */
    public TabLoader(final Reader reader, Boolean hasColumnHeaders, @Nullable Container mvIndicatorContainer, final boolean closeOnComplete)
    {
        this(new ReaderFactory()
        {
            private boolean _closed = false;

            @Override
            public TabBufferedReader getReader()
            {
                if (_closed)
                    throw new IllegalStateException("Reader is closed");

                // Customize close() behavior to track closing and handle closeOnComplete
                return new TabBufferedReader(reader)
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


    private TabLoader(ReaderFactory factory, Boolean hasColumnHeaders, @Nullable Container mvIndicatorContainer)
    {
        super(mvIndicatorContainer);

        _readerFactory = factory;

        if (null != hasColumnHeaders)
            setHasColumnHeaders(hasColumnHeaders);
    }


    protected TabBufferedReader getReader() throws IOException
    {
        if (null == _reader)
            _reader = _readerFactory.getReader();

        return _reader;
    }

    public Map<String, String> getComments() throws IOException
    {
        ensureInitialized(Collections.emptyMap());

        return Collections.unmodifiableMap(_comments);
    }

    public boolean isIncludeComments()
    {
        return _includeComments;
    }

    public void setIncludeComments(boolean includeComments)
    {
        _includeComments = includeComments;
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
        if (_unescapeBackslashes && value.indexOf('\\') >= 0)   // unescapeJava() is really slow
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

    private final ArrayList<String> listParse = new ArrayList<>(30);

    private CharSequence readLine(TabBufferedReader r, boolean skipComments, boolean skipBlankLines)
    {
        String line = readOneTextLine(r, skipComments, skipBlankLines);
        if (null == line || null == _lineDelimiter)
            return line;
        if (line.endsWith(_lineDelimiter))
            return line.substring(0, line.length() - _lineDelimiter.length());
        StringBuilder sb = new StringBuilder(line);
        while (null != (line = readOneTextLine(r, false, false)))
        {
            sb.append("\n");
            if (line.endsWith(_lineDelimiter))
            {
                sb.append(line, 0, line.length()-_lineDelimiter.length());
                return sb;
            }
            sb.append(line);
        }
        return sb;
    }

    private String readOneTextLine(TabBufferedReader r, boolean skipComments, boolean skipBlankLines)
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

    private String[] readFields(TabBufferedReader r, @Nullable ColumnDescriptor[] columns)
    {
        CharSequence line = readLine(r, !isIncludeComments(), !isIncludeBlankLines());

        if (line == null)
            return null;

        if (!_parseQuotes)
        {
            String[] fields = StringUtils.splitByWholeSeparator(line.toString(), _strDelimiter);
            for (int i = 0; i < fields.length; i++)
                fields[i] = parseValue(fields[i]);
            return fields;
        }

        StringBuilder buf = line instanceof StringBuilder ? (StringBuilder)line : new StringBuilder(line);

        String field = null;
        int start = 0, colIndex = 0;
        listParse.clear();

        while (start < buf.length())
        {
            boolean loadThisColumn = null==columns || colIndex >= columns.length || columns[colIndex].load;
            int end = 0;
            char ch = buf.charAt(start);
            char chQuote = '"';

            colIndex++;

            boolean isDelimiterOrQuote = false;
            if (ch == _chDelimiter)
            {
                end = start;
                field = _preserveEmptyString ? null : "";
                isDelimiterOrQuote = true;
            }
            else if (ch == chQuote)
            {
                isDelimiterOrQuote = true;
                if (_strQuote == null)
                {
                    _strQuote = String.valueOf(chQuote);
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
                            if (_parseEnclosedQuotes)
                                isDelimiterOrQuote = false;
                            break;
                        }

                        buf.append('\n');
                        buf.append(nextLine);
                        continue;
                    }

                    if (end == buf.length() - 1 || buf.charAt(end + 1) != chQuote)
                    {
                        // Issue 51056: pooling sample parents with single quote doesn't work
                        // " a, " b should be parsed as [" a, " b], not [a,  b]
                        if (_parseEnclosedQuotes && end != buf.length() - 1 && buf.charAt(end + 1) != _chDelimiter)
                            isDelimiterOrQuote = false;
                        break;
                    }
                    hasQuotes = true;
                    end++; // skip double ""
                }

                if (isDelimiterOrQuote)
                {
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
            }

            if (!isDelimiterOrQuote)
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

        return listParse.toArray(new String[0]);
    }

    @Override
    public @NotNull CloseableIterator<Map<String, Object>> _iterator(boolean includeRowHash)
    {
        TabLoaderIterator iter;
        try
        {
            ensureInitialized(Collections.emptyMap());
            iter = new TabLoaderIterator(includeRowHash);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        return iter;
    }


    public void parseAsCSV()
    {
        setDelimiterCharacter(',');
        setParseQuotes(true);
    }

    public void setDelimiterCharacter(char delimiter)
    {
        _chDelimiter = delimiter;
        _strDelimiter = String.valueOf(_chDelimiter);
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

    public void setParseEnclosedQuotes(boolean parseEnclosedQuotes)
    {
        _parseEnclosedQuotes = parseEnclosedQuotes;
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
    protected void initialize(@NotNull Map<String, String> renamedColumns) throws IOException
    {
        readComments();
        super.initialize(renamedColumns);
    }

    private void readComments() throws IOException
    {
        TabBufferedReader reader = getReader();
        reader.setReadAhead();

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
            reader.resetReadAhead();
        }
    }

    @Override
    public String[][] getFirstNLines(int n) throws IOException
    {
        TabBufferedReader reader = getReader();
        reader.setReadAhead();

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
            reader.resetReadAhead();
        }
    }


    public class TabLoaderIterator extends AbstractDataLoaderIterator
    {
        private final TabBufferedReader reader;

        protected TabLoaderIterator(boolean includeRowHash) throws IOException
        {
            super(_commentLines + _skipLines, includeRowHash);
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

        @Override
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
        protected String[] readFields()
        {
            return TabLoader.this.readFields(reader, _columns);
        }
    }

    public static class TabLoaderTestCase extends Assert
    {
        String malformedCsvData = """
                "Header1","Header2", "Header3"
                "test1a", "testb, "a "b""";

        String malformedTsvData = """
                "Header1"\t"Header2"\t "Header3"
                "test1a"\t "testb\t"a "b""";

        String csvData = """
                # algorithm=org.fhcrc.cpas.viewer.feature.FeatureStrategyPeakClusters
                # date=Mon May 22 13:25:28 PDT 2006
                # java.vendor=Sun Microsystems Inc.
                # java.version=1.5.0_06
                # revision=rev1.1
                # user.name=Matthew
                date,scan,time,mz,accurateMZ,mass,intensity,charge,chargeStates,kl,background,median,peaks,scanFirst,scanLast,scanCount,totalIntensity,description
                1/2/2006,96,1543.3401,858.3246,FALSE,1714.6346,2029.6295,2,1,0.19630894,26.471083,12.982442,4,92,100,9,20248.762,description
                2/Jan/2006,100,1560.348,858.37555,FALSE,1714.7366,1168.3536,2,1,0.033085547,63.493385,8.771278,5,101,119,19,17977.979,"desc""ion"
                ,25,1460.2411,745.39404,FALSE,744.3868,1114.4303,1,1,0.020280406,15.826528,12.413276,4,17,41,25,13456.231,"des,crip,tion"
                2-Jan-06,89,1535.602,970.9579,FALSE,1939.9012,823.70984,2,1,0.0228055,10.497823,2.5962036,5,81,103,23,9500.36,
                2 January 2006,164,1624.442,783.8968,FALSE,1565.779,771.20935,2,1,0.024676466,11.3547325,3.3645654,5,156,187,32,12656.351,
                "January 2, 2006",224,1695.389,725.39404,FALSE,2173.1604,6.278867,3,1,0.2767084,1.6497655,1.2496755,3,221,229,9,55.546417
                1/2/06,249,1724.5541,773.42175,FALSE,1544.829,5.9057474,2,1,0.5105971,0.67020833,1.4744527,2,246,250,5,29.369175
                # bar
    
                #""";

        String tsvData = """
                # algorithm=org.fhcrc.cpas.viewer.feature.FeatureStrategyPeakClusters
                # date=Mon May 22 13:25:28 PDT 2006
                # java.vendor=Sun Microsystems Inc.
                # java.version=1.5.0_06
                # revision=rev1.1
                # user.name=Matthew
                date\tscan\ttime\tmz\taccurateMZ\tmass\tintensity\tcharge\tchargeStates\tkl\tbackground\tmedian\tpeaks\tscanFirst\tscanLast\tscanCount\ttotalIntensity\tdescription
                1/2/2006\t96\t1543.3401\t858.3246\tFALSE\t1714.6346\t2029.6295\t2\t1\t0.19630894\t26.471083\t12.982442\t4\t92\t100\t9\t20248.762\tdescription
                2/Jan/2006\t100\t1560.348\t858.37555\tFALSE\t1714.7366\t1168.3536\t2\t1\t0.033085547\t63.493385\t8.771278\t5\t101\t119\t19\t17977.979\tdesc"ion
                \t25\t1460.2411\t745.39404\tFALSE\t744.3868\t1114.4303\t1\t1\t0.020280406\t15.826528\t12.413276\t4\t17\t41\t25\t13456.231\tdes,crip,tion
                2-Jan-06\t89\t1535.602\t970.9579\tFALSE\t1939.9012\t823.70984\t2\t1\t0.0228055\t10.497823\t2.5962036\t5\t81\t103\t23\t9500.36\t
                2 January 2006\t164\t1624.442\t783.8968\tFALSE\t1565.779\t771.20935\t2\t1\t0.024676466\t11.3547325\t3.3645654\t5\t156\t187\t32\t12656.351\t
                January 2, 2006\t224\t1695.389\t725.39404\tFALSE\t2173.1604\t6.278867\t3\t1\t0.2767084\t1.6497655\t1.2496755\t3\t221\t229\t9\t55.546417\t
                1/2/06\t249\t1724.5541\t773.42175\tFALSE\t1544.829\t5.9057474\t2\t1\t0.5105971\t0.67020833\t1.4744527\t2\t246\t250\t5\t29.369175\t
                # foo

                #""";

        /* same data in a different order */
        String tsvDataReordered = """
                date\tscan\ttime\tmz\taccurateMZ\tmass\tintensity\tcharge\tchargeStates\tkl\tbackground\tmedian\tpeaks\tscanFirst\tscanLast\tscanCount\ttotalIntensity\tdescription
                1/2/06\t249\t1724.5541\t773.42175\tFALSE\t1544.829\t5.9057474\t2\t1\t0.5105971\t0.67020833\t1.4744527\t2\t246\t250\t5\t29.369175\t
                \t25\t1460.2411\t745.39404\tFALSE\t744.3868\t1114.4303\t1\t1\t0.020280406\t15.826528\t12.413276\t4\t17\t41\t25\t13456.231\tdes,crip,tion
                2-Jan-06\t89\t1535.602\t970.9579\tFALSE\t1939.9012\t823.70984\t2\t1\t0.0228055\t10.497823\t2.5962036\t5\t81\t103\t23\t9500.36\t
                January 2, 2006\t224\t1695.389\t725.39404\tFALSE\t2173.1604\t6.278867\t3\t1\t0.2767084\t1.6497655\t1.2496755\t3\t221\t229\t9\t55.546417\t
                2/Jan/2006\t100\t1560.348\t858.37555\tFALSE\t1714.7366\t1168.3536\t2\t1\t0.033085547\t63.493385\t8.771278\t5\t101\t119\t19\t17977.979\tdesc"ion
                1/2/2006\t96\t1543.3401\t858.3246\tFALSE\t1714.6346\t2029.6295\t2\t1\t0.19630894\t26.471083\t12.982442\t4\t92\t100\t9\t20248.762\tdescription
                2 January 2006\t164\t1624.442\t783.8968\tFALSE\t1565.779\t771.20935\t2\t1\t0.024676466\t11.3547325\t3.3645654\t5\t156\t187\t32\t12656.351\t
                """;

        private File _createTempFile(String data, String ext) throws IOException
        {
            File f = FileUtil.createTempFile("junit", ext);
            f.deleteOnExit();

            try (Writer w = PrintWriters.getPrintWriter(f))
            {
                w.write(data);
            }

            return f;
        }

        private void verifyMalformedData(File csv, boolean requireEnclosedQuotes, String expectedResult11, @Nullable String expectedTestBResult12) throws IOException
        {
            try (TabLoader l = new TabLoader(csv))
            {
                if (requireEnclosedQuotes)
                    l.setParseEnclosedQuotes(true);

                if (csv.getName().endsWith("csv"))
                    l.parseAsCSV();
                
                List<Map<String, Object>> maps = l.load();
                assertEquals(3, l.getColumns().length);
                assertEquals(String.class, l.getColumns()[0].clazz);
                assertEquals(String.class, l.getColumns()[1].clazz);
                assertEquals(String.class, l.getColumns()[2].clazz);
                assertEquals(2, maps.size());

                assertEquals("Header1", maps.get(0).get("column0"));
                assertEquals("Header2", maps.get(0).get("column1"));
                assertEquals("Header3", maps.get(0).get("column2"));

                assertEquals("test1a", maps.get(1).get("column0"));
                assertEquals(expectedResult11, maps.get(1).get("column1"));

                if (requireEnclosedQuotes)
                    assertEquals(expectedTestBResult12, maps.get(1).get("column2"));
                else
                    assertNull(maps.get(1).get("column2"));
            }
        }

        @Test
        public void testMalformedCsv() throws IOException
        {
            File csv = _createTempFile(malformedCsvData, ".csv");

            verifyMalformedData(csv, false, "testb, a \"b", null);
            verifyMalformedData(csv, true, "\"testb", "\"a \"b");

            assertTrue(csv.delete());
        }

        @Test
        public void testMalformedTsv() throws IOException
        {
            File tsv = _createTempFile(malformedTsvData, ".tsv");

            verifyMalformedData(tsv, false, "testb\ta \"b", null);
            verifyMalformedData(tsv, true, "\"testb", "\"a \"b");

            assertTrue(tsv.delete());
        }

        @Test
        public void testTSV() throws IOException
        {
            testTextFile(tsvData, ".tsv", t->{});
            testReader(tsvData, ".tsv", t->{});
        }

        @Test
        public void testCSV() throws IOException
        {
            testTextFile(csvData, ".csv", TabLoader::parseAsCSV);
            testReader(csvData, ".csv", TabLoader::parseAsCSV);
        }

        private void testTextFile(String data, String extension, Consumer<TabLoader> consumer) throws IOException
        {
            File file = _createTempFile(data, extension);

            try (TabLoader l = new TabLoader(file))
            {
                consumer.accept(l);
                List<Map<String, Object>> maps = l.load();
                assertEquals(18, l.getColumns().length);
                assertEquals(Date.class, l.getColumns()[0].clazz);
                assertEquals(Integer.class, l.getColumns()[1].clazz);
                assertEquals(Double.class, l.getColumns()[2].clazz);
                assertEquals(Boolean.class, l.getColumns()[4].clazz);
                assertEquals(String.class, l.getColumns()[17].clazz);
                assertEquals(7, maps.size());

                Map<String, Object> firstRow = maps.get(0);
                assertEquals(96, firstRow.get("scan"));
                assertEquals(false, firstRow.get("accurateMZ"));
                assertEquals("description", firstRow.get("description"));
            }

            assertTrue(file.delete());
        }

        public void testReader(String data, String extension, Consumer<TabLoader> consumer) throws IOException
        {
            File file = _createTempFile(data, extension);

            try (Reader r = Readers.getReader(file); TabLoader l = new TabLoader(r, true))
            {
                consumer.accept(l);
                List<Map<String, Object>> maps = l.load();
                assertEquals(18, l.getColumns().length);
                assertEquals(7, maps.size());
            }
            assertTrue(file.delete());
        }

        // Test that TabLoader can handle empty and nearly empty content, Issue 41897
        @Test
        public void testEmptyTabLoader() throws IOException
        {
            testEmptyTabLoader("", 0);
            testEmptyTabLoader("X", 1);
            testEmptyTabLoader("X\n", 1);
        }

        private void testEmptyTabLoader(String content, int expectedColumnCount) throws IOException
        {
            try (TabLoader l = new TabLoader(content, true))
            {
                testEmptyTabLoader(l, expectedColumnCount);
            }
            try (Reader r = new StringReader(content); TabLoader l = new TabLoader(r, true))
            {
                testEmptyTabLoader(l, expectedColumnCount);
            }
            File tsv = _createTempFile(content, ".tsv");
            try (TabLoader l = new TabLoader(tsv, true))
            {
                testEmptyTabLoader(l, expectedColumnCount);
            }
            try (Reader r = Readers.getReader(tsv); TabLoader l = new TabLoader(r, true))
            {
                testEmptyTabLoader(l, expectedColumnCount);
            }
            try (Reader r = Readers.getUnbufferedReader(tsv); TabLoader l = new TabLoader(r, true))
            {
                testEmptyTabLoader(l, expectedColumnCount);
            }
            assertTrue(tsv.delete());
        }

        private void testEmptyTabLoader(TabLoader l, int expectedColumnCount) throws IOException
        {
            List<Map<String, Object>> maps = l.load();
            assertEquals(expectedColumnCount, l.getColumns().length);
            assertEquals(0, maps.size());
        }

        @Test
        // We had a window (after incorporating org.labkey.api.reader.BufferedReader but before fixing Issue 41897) where
        // TabLoader would fail to read the last line at infer time. Test that we don't regress.
        public void testSmallFile() throws IOException
        {
            File tsv = _createTempFile("Heading1\tHeading2\n1\t1.2", ".tsv");

            try (TabLoader l = new TabLoader(tsv, true))
            {
                List<Map<String, Object>> maps = l.load();
                assertEquals(1, maps.size());
                assertEquals(Integer.class, l.getColumns()[0].clazz);
                assertEquals(Double.class, l.getColumns()[1].clazz);
            }
            assertTrue(tsv.delete());
        }

        @Test
        public void compareTSVtoCSV() throws IOException
        {
            try (TabLoader lCSV = new TabLoader(csvData, true))
            {
                lCSV.parseAsCSV();
                List<Map<String, Object>> mapsCSV = lCSV.load();

                try (TabLoader lTSV = new TabLoader(tsvData, true))
                {
                    List<Map<String, Object>> mapsTSV = lTSV.load();

                    assertEquals(lCSV.getColumns().length, lTSV.getColumns().length);
                    assertEquals(mapsCSV.size(), mapsTSV.size());
                    for (int i = 0; i < mapsCSV.size(); i++)
                        assertEquals(mapsCSV.get(i), mapsTSV.get(i));
                }
            }
        }

        @Test
        public void testObject() throws Exception
        {
            try (TabLoader tl = new TabLoader(tsvData); CloseableIterator<TestRow> iter = new BeanIterator<>(tl.iterator(), TestRow.class))
            {
                assertTrue(iter.hasNext());
                TestRow firstRow = iter.next();

                assertEquals(96, firstRow.getScan());
                assertFalse(firstRow.isAccurateMZ());
                assertEquals("description", firstRow.getDescription());

                int count = 1;

                while (iter.hasNext())
                {
                    iter.next();
                    count++;
                }

                assertEquals(7, count);
            }
        }

        @Test
        public void testUnescape()
        {
            final String data = """
                A\tMulti-Line\tB
                a\tthis\\nis\\tmulti-line\tb
                \tthis\\nis\\tmulti-line\tb
                """;

            try (TabLoader loader = new TabLoader(data, true))
            {
                loader.setUnescapeBackslashes(true);
                List<Map<String, Object>> rows = loader.load();
                assertEquals(2, rows.size());

                Map<String, Object> row = rows.get(0);
                assertEquals("a", row.get("A"));
                assertEquals("this\nis\tmulti-line", row.get("Multi-Line"));
                assertEquals("b", row.get("B"));

                row = rows.get(1);
                assertNull(row.get("A"));
                assertEquals("this\nis\tmulti-line", row.get("Multi-Line"));
                assertEquals("b", row.get("B"));
            }

            // now test no-unescaping
            try (TabLoader loader = new TabLoader(data, true))
            {
                loader.setUnescapeBackslashes(false);
                List<Map<String, Object>> rows = loader.load();
                assertEquals(2, rows.size());

                Map<String, Object> row = rows.get(0);
                assertEquals("a", row.get("A"));
                assertEquals("this\\nis\\tmulti-line", row.get("Multi-Line"));
                assertEquals("b", row.get("B"));

                row = rows.get(1);
                assertNull(row.get("A"));
                assertEquals("this\\nis\\tmulti-line", row.get("Multi-Line"));
                assertEquals("b", row.get("B"));
            }
        }

        @Test
        public void testEmptyRow()
        {
            final String data = """
                A\tB
                first\tline

                # comment
                second\tline
                """;

            for (int i = 0; i < 1; i++)
            {
                boolean parseQuotes = i == 0;

                // default behavior is to skip blank lines
                try (TabLoader loader = new TabLoader(data, true))
                {
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
                try (TabLoader loader = new TabLoader(data, true))
                {
                    loader.setInferTypes(false);
                    loader.setParseQuotes(parseQuotes);
                    loader.setIncludeBlankLines(true);
                    List<Map<String, Object>> rows = loader.load();
                    assertEquals(3, rows.size());

                    Map<String, Object> row = rows.get(0);
                    assertEquals("first", row.get("A"));

                    row = rows.get(1);
                    assertNull(row.get("A"));
                    assertNull(row.get("B"));

                    row = rows.get(2);
                    assertEquals("second", row.get("A"));
                }
            }
        }

        @Test
        public void testParseQuotes()
        {
            final String data = """
                Name\tMulti-Line\tAge
                Bob\t"apple
                orange\tgrape"\t3
                Bob\t"one
                ""two""\tthree"
                \tred\\nblue\\tgreen\t4
                Fred\t"quoted stuff" unquoted\t1""";

            try (TabLoader loader = new TabLoader(data, true))
            {
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
                assertNull(row.get("Age"));

                row = rows.get(2);
                assertNull(row.get("Name"));
                assertEquals("red\nblue\tgreen", row.get("Multi-Line"));
                assertEquals(4, row.get("Age"));
            }

            // now test no-unescaping
            try (TabLoader loader = new TabLoader(data, true))
            {
                loader.setParseQuotes(true);
                loader.setUnescapeBackslashes(false);

                List<Map<String, Object>> rows = loader.load();
                assertEquals(4, rows.size());

                Map<String, Object> row = rows.get(0);
                assertEquals("Bob", row.get("Name"));
                assertEquals("apple\norange\tgrape", row.get("Multi-Line"));
                assertEquals(3, row.get("Age"));

                row = rows.get(1);
                assertEquals("Bob", row.get("Name"));
                assertEquals("one\n\"two\"\tthree", row.get("Multi-Line"));
                assertNull(row.get("Age"));

                row = rows.get(2);
                assertNull(row.get("Name"));
                assertEquals("red\\nblue\\tgreen", row.get("Multi-Line"));
                assertEquals(4, row.get("Age"));

                row = rows.get(3);
                assertEquals("Fred", row.get("Name"));
                assertEquals("quoted stuff unquoted", row.get("Multi-Line"));
                assertEquals(1, row.get("Age"));

                List<Map<String, Object>> rows2 = loader.stream()
                    .collect(Collectors.toList());

                assertEquals(rows, rows2);
            }
        }

        @Test
        public void testMySql() throws IOException
        {
            String mysqlData = """
                3072~@~\\N~@~biotinylated antihuIgG antibody~@~ESR8865~@~2149~@@~
                3073~@~Multiline
                description~@~anti-huIgM antibody~@~ESR8866~@~2149~@@~
                3074~@~short~description~@~anti-huIgA antibody~@~ESR8867~@~2149~@@~
                3075~@~description with



                blank lines

                ~@~avidin-D-HRP conjugate~@~ESR8868~@~2149~@@~
                """;

            final List<Map<String, Object>> rows;

            try (TabLoader loader = (TabLoader)new TabLoader.MysqlFactory().createLoader(new StringBufferInputStream(mysqlData), false, null))
            {
                loader.setColumns(new ColumnDescriptor[]{new ColumnDescriptor("analyte_id"), new ColumnDescriptor("description"), new ColumnDescriptor("name"), new ColumnDescriptor("reagent_ascession"), new ColumnDescriptor("workspace_id")});
                loader.setDelimiters("~@~", "~@@~");
                rows = loader.load();
            }

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

        @Test
        public void testHash()
        {
            /* NOTE hashes are hard-coded so we know if implementation has changed. Uncomment this block to print out hashes to update code *
            TabLoader tl = new TabLoader(tsvData);
            DataLoaderIterator it = (DataLoaderIterator)tl.iterator(true);
            while (it.hasNext())
                System.err.println("\"" + it.next().get(HashDataIterator.HASH_COLUMN_NAME) + "\",");
            */

            String[] expectedHashes = new String[] {
                    "zC1fuRsYCgYT3sjZd1xzsg==",
                    "Lmpv0AW+Zf1YFawCcE3/Vg==",
                    "itsVDD4jsZKoBNpQJM94CA==",
                    "OTj8Q9T5Y8XPuApt+rCi6g==",
                    "qwAF7kX9pLOV0uuspUcVdg==",
                    "RrFphIaMdtv9DBBwkPgKkA==",
                    "RsoWq6d2hJPBfyHDpWnpVQ=="
            };
            TabLoader tl = new TabLoader(tsvData);
            DataLoaderIterator it = (DataLoaderIterator)tl.iterator(true);
            for (int i=0 ; it.hasNext() ; i++)
                assertEquals(expectedHashes[i], it.next().get(HashDataIterator.HASH_COLUMN_NAME));

            HashSet<String> set1 = new HashSet<>();
            DataLoaderIterator it1 = (DataLoaderIterator)new TabLoader(tsvData).iterator(true);
            while (it1.hasNext())
                set1.add((String)it1.next().get(HashDataIterator.HASH_COLUMN_NAME));
            HashSet<String> set2 = new HashSet<>();
            DataLoaderIterator it2 = (DataLoaderIterator)new TabLoader(tsvDataReordered).iterator(true);
            while (it2.hasNext())
                set2.add((String)it2.next().get(HashDataIterator.HASH_COLUMN_NAME));
            assert(set1.equals(set2));
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

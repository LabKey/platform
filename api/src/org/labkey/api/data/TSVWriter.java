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

package org.labkey.api.data;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.log4j.Logger;
import org.labkey.api.util.ExceptionUtil;

import javax.servlet.ServletOutputStream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.regex.Pattern;

public abstract class TSVWriter
{
    private static Logger _log = Logger.getLogger(TSVWriter.class);

    private String _filenamePrefix = "tsv";
    private boolean _exportAsWebPage = false;

    // Stashing the OutputStream and the PrintWriter allows multiple writes (e.g., Export Runs to TSV)
    private ServletOutputStream _outputStream = null;
    protected PrintWriter _pw = null;
    protected char _chDelimiter = '\t';
    protected char _chQuote = '"';
    protected String _rowSeparator = "\n";


    public TSVWriter()
    {
    }


    public String getFilenamePrefix()
    {
        return _filenamePrefix;
    }

    public boolean isExportAsWebPage()
    {
        return _exportAsWebPage;
    }

    public void setExportAsWebPage(boolean exportAsWebPage)
    {
        _exportAsWebPage = exportAsWebPage;
    }

    private static final Pattern badChars = Pattern.compile("[\\\\:/\\[\\]\\?\\*\\|]");

    public void setFilenamePrefix(String filenamePrefix)
    {
        _filenamePrefix = badChars.matcher(filenamePrefix).replaceAll("_");

        if (_filenamePrefix.length() > 30)
            _filenamePrefix = _filenamePrefix.substring(0, 30);
    }

    public void setDelimiterCharacter(char delimiter)
    {
        _chDelimiter = delimiter;
    }

    public void setQuoteCharacter(char quote)
    {
        _chQuote = quote;
    }

    public void setRowSeparator(String rowSeparator)
    {
        _rowSeparator = rowSeparator;
    }

    protected Pattern _escapedChars;
    protected Pattern _leadingWhitespace = Pattern.compile("^\\p{Space}.*");
    protected Pattern _trailingWhitespace = Pattern.compile(".*\\p{Space}$");

    /**
     * Quote the value if necessary.  The quoting rules are:
     * <ul>
     *   <li>Values containing leading or trailing whitespace, a newline, a carrage return, or the field separator will be quoted.
     *   <li>Values containing the quoting character will also be quoted with the quote character replaced by two quote characters.
     * </ul>
     * <p>
     * The quoting character is also known as a "text qualifier" in Excel and is usually
     * the double quote character but may be the single quote character.
     * <p>
     * Note: Excel will always quote a field if it includes comma even if it isn't the delimeter, but
     * this algorithm doesn't to avoid unnecessary quoting.
     * 
     * @param value The raw value.
     * @return The quoted value.
     */
    protected String quoteValue(String value)
    {
        if (value == null)
            return "";
        
        String escaped = value;

        if (_escapedChars == null)
        {
            // NOTE: Excel always includes comma in the list of characters that will be quoted,
            // but we will only quote comma if it is the delimeter character.
            _escapedChars = Pattern.compile("[\r\n\\" + _chDelimiter + "\\" + _chQuote + "]");
        }

        if (_escapedChars.matcher(value).find() ||
                _leadingWhitespace.matcher(value).matches() ||
                _trailingWhitespace.matcher(value).matches())
        {
            StringBuffer sb = new StringBuffer(value.length() + 10);
            sb.append(_chQuote);
            int i = -1, lastMatch = 0;
            while (-1 != (i = value.indexOf(_chQuote, lastMatch)))
            {
                sb.append(value.substring(lastMatch, i));
                sb.append(_chQuote).append(_chQuote);
                lastMatch = i+1;
            }
            if (lastMatch < value.length())
                sb.append(value.substring(lastMatch));
            sb.append(_chQuote);
            escaped = sb.toString();
        }

        return escaped;
    }

    // Create a TSV file and stream it to the browser.
    public void write(HttpServletResponse response) throws ServletException
    {
        try
        {
            prepare(response);
            write();
        }
        finally
        {
            close();
        }
    }

    // Prepare the TSVWriter to write TSV file to the file system -- can be used for testing
    public void prepare(File file) throws ServletException
    {
        try
        {
            _pw = new PrintWriter(new BufferedWriter(new FileWriter(file)));
        }
        catch(IOException e)
        {
            _log.error("prepare", e);
            throw new ServletException(e);
        }
    }


    public void prepare(StringBuilder builder)
    {
        _pw = new PrintWriter(new StringBuilderWriter(builder));
    }

    // Prepare the TSVWriter to stream TSV file to the browser
    public void prepare(HttpServletResponse response) throws ServletException
    {
        // Flush any extraneous output (e.g., <CR><LF> from JSPs)
        response.reset();

        // Specify attachment and foil caching
        if (_exportAsWebPage)
           response.setHeader("Content-disposition", "inline; filename=\"" + getFilename() + "\"");
        else
        {
           // Set the content-type so the browser knows which application to launch
           response.setContentType(getContentType());
           response.setHeader("Content-disposition", "attachment; filename=\"" + getFilename() + "\"");
        }

        try
        {
            // Get the outputstream of the servlet (BTW, always get the outputstream AFTER you've
            // set the content-disposition and content-type)
            _outputStream = response.getOutputStream();
            _pw = new PrintWriter(_outputStream);
        }
        catch(IOException e)
        {
            _log.error("preparePrintWriter", e);
            throw new ServletException(e);
        }
    }

    /**
     * Override to return a different content type
     * @return The content type
     */
    protected String getContentType()
    {
        return "text/tab-separated-values";
    }

    /**
     * Override to return a different filename
     * @return The filename
     */
    protected String getFilename()
    {
        return getFilenamePrefix() + "_" + Math.round(Math.random() * 100000) + ".tsv";
    }


    public void setPrintWriter(PrintWriter pw)
    {
        _pw = pw;
    }


    public PrintWriter getPrintWriter()
    {
        return _pw;
    }


    // Write a newly created TSV file to the PrintWriter.
    public void write(PrintWriter pw) throws ServletException
    {
        _pw = pw;
        write();
        close();
    }

    // Write a newly created TSV file to the file system.
    public void write(File file) throws ServletException
    {
        prepare(file);
        write();
        close();
    }

    // Write a newly created TSV file to a string builder.
    public void write(StringBuilder builder) throws ServletException
    {
        prepare(builder);
        write();
        close();
    }

    private class StringBuilderWriter extends Writer
    {
        private StringBuilder _builder;
        private boolean _closed = false;
        public StringBuilderWriter(StringBuilder builder)
        {
            _builder = builder;
        }
        public void write(char cbuf[], int off, int len) throws IOException
        {
            if (_closed)
                throw new IOException("Cannot write to closed writer.");
            _builder.append(cbuf, off, len);
        }

        public void close() throws IOException
        {
            _closed = true;
        }

        public void flush() throws IOException
        {
            // no-op
        }
    }

    public void close() throws ServletException
    {
        _pw.close();

        if (_pw.checkError())
            _log.error("PrintWriter error");

        if (null == _outputStream)
            return;

        try
        {
            // Flush the outputstream
            _outputStream.flush();
            // Finally, close the outputstream
            _outputStream.close();
        }
        catch(IOException e)
        {
            if (!ExceptionUtil.isClientAbortException(e))
            {
                _log.error("close", e);
                throw new ServletException(e);
            }
        }
    }


    protected abstract void write();

    
    public static class TestCase extends junit.framework.TestCase
    {
        public TestCase() { super(); }
        public TestCase(String name) { super(name); }
        public static Test suite() { return new TestSuite(TestCase.class); }

        private static class FakeTSVWriter extends TSVWriter
        {
            protected void write()
            {
                // no-op
            }
        }

        public void testBasic()
        {
            FakeTSVWriter w = new FakeTSVWriter();
            assertEquals("", w.quoteValue(""));
            assertEquals("a", w.quoteValue("a"));
            assertEquals("", w.quoteValue(null));
        }

        public void testWhitespace()
        {
            FakeTSVWriter w = new FakeTSVWriter();
            assertEquals("a b", w.quoteValue("a b"));
            assertEquals("\"  ab\"", w.quoteValue("  ab"));
            assertEquals("\"ab  \"", w.quoteValue("ab  "));
            assertEquals("\"a\nb\"", w.quoteValue("a\nb"));
            assertEquals("\"a\n\rb\"", w.quoteValue("a\n\rb"));
        }

        public void testDelimiterChar()
        {
            FakeTSVWriter w = new FakeTSVWriter();
            assertEquals("\"one\t two\t three\"", w.quoteValue("one\t two\t three"));
            //assertEquals("\"one, two, three\"", w.quoteValue("one, two, three")); // commas should always be quoted
            assertEquals("one; two; three", w.quoteValue("one; two; three"));

            w = new FakeTSVWriter();
            w.setDelimiterCharacter(';');
            assertEquals("one\ttwo\tthree", w.quoteValue("one\ttwo\tthree"));
            //assertEquals("\"one, two, three\"", w.quoteValue("one, two, three")); // commas should always be quoted
            assertEquals("\"one; two; three\"", w.quoteValue("one; two; three"));

            w = new FakeTSVWriter();
            w.setDelimiterCharacter('\\');
            assertEquals("one\ttwo\tthree", w.quoteValue("one\ttwo\tthree"));
            assertEquals("\"one\\ two\\ three\"", w.quoteValue("one\\ two\\ three"));

            w = new FakeTSVWriter();
            w.setDelimiterCharacter('(');
            assertEquals("\"one( two( three\"", w.quoteValue("one( two( three"));
        }

        public void testQuoteChar()
        {
            FakeTSVWriter w = new FakeTSVWriter();
            assertEquals("hello", w.quoteValue("hello"));
            assertEquals("\"\"\"\"", w.quoteValue("\""));
            assertEquals("\"bob says, \"\"hello\"\"\"", w.quoteValue("bob says, \"hello\""));
            assertEquals("\"bob says; \"\"hello\"\"\"", w.quoteValue("bob says; \"hello\""));

            w = new FakeTSVWriter();
            w.setQuoteCharacter(':');
            assertEquals(":bob says:: \"hello\":", w.quoteValue("bob says: \"hello\""));
            //assertEquals(":bob says, \"hello\":", w.quoteValue("bob says, \"hello\"")); // commas should always be quoted
            assertEquals("bob says; \"hello\"", w.quoteValue("bob says; \"hello\""));
        }
    }
}

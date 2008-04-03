package org.labkey.api.module;

import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * User: jeckels
 * Date: Jun 15, 2006
 */
public class SafeFlushResponseWrapper extends HttpServletResponseWrapper
{
    private OutputStreamWrapper _outputStreamWrapper;

    public SafeFlushResponseWrapper(HttpServletResponse response)
    {
        super(response);
    }

    public ServletOutputStream getOutputStream()
    throws IOException
    {
        if (_outputStreamWrapper == null)
        {
            _outputStreamWrapper = new OutputStreamWrapper(getResponse().getOutputStream());
        }
        return _outputStreamWrapper;
    }

    public static class OutputStreamWrapper extends ServletOutputStream
    {
        private final OutputStream _out;
        private boolean _canFlush = true;

        public OutputStreamWrapper(OutputStream out)
        {
            _out = out;
        }

        public void write(byte b[]) throws IOException
        {
            _out.write(b);
        }

        public void write(byte b[], int off, int len) throws IOException
        {
            _out.write(b, off, len);
        }

        public void flush() throws IOException
        {
            if (_canFlush)
            {
                _out.flush();
            }
        }

        public void write(int b) throws IOException
        {
            _out.write(b);
        }

        public void close() throws IOException
        {
            _canFlush = false;
            _out.close();
        }
    }
}

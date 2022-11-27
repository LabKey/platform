/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.module;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
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

    @Override
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

        @Override
        public void write(byte b[]) throws IOException
        {
            _out.write(b);
        }

        @Override
        public void write(byte b[], int off, int len) throws IOException
        {
            _out.write(b, off, len);
        }

        @Override
        public void flush() throws IOException
        {
            if (_canFlush)
            {
                _out.flush();
            }
        }

        @Override
        public void write(int b) throws IOException
        {
            _out.write(b);
        }

        @Override
        public void close() throws IOException
        {
            _canFlush = false;
            _out.close();
        }

        @Override
        public boolean isReady()
        {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener)
        {
            throw new UnsupportedOperationException();
        }
    }
}

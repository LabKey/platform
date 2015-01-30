/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
package org.labkey.api.util;

import org.apache.commons.io.IOUtils;

import java.io.*;

/**
 * User: Matthew
 * Date: Apr 29, 2009
 * Time: 9:29:16 PM
 *
 * Useful when we need to communicate the length of the stream (that is not a FileInputStream e.g.)
 */
public interface FileStream
{
    public long getSize() throws IOException;
    public InputStream openInputStream() throws IOException;
    public void closeInputStream() throws IOException;


    public static FileStream EMPTY = new FileStream()
    {
        public long getSize() throws IOException
        {
            return 0;
        }

        public InputStream openInputStream() throws IOException
        {
            return new ByteArrayInputStream(new byte[0]);
        }

        public void closeInputStream() throws IOException
        {
        }
    };


    public static class ByteArrayFileStream implements FileStream
    {
        ByteArrayInputStream in;

        public ByteArrayFileStream(ByteArrayInputStream b)
        {
            in = b;
            MemTracker.getInstance().put(in);
        }

        public ByteArrayFileStream(byte[] buf)
        {
            in = new ByteArrayInputStream(buf);
            MemTracker.getInstance().put(in);
        }

        public ByteArrayFileStream(ByteArrayOutputStream out)
        {
            in = new ByteArrayInputStream(out.toByteArray());
            MemTracker.getInstance().put(in);
        }

        public long getSize()
        {
            return in.available();
        }

        public InputStream openInputStream() throws IOException
        {
            return in;
        }

        public void closeInputStream() throws IOException
        {
            IOUtils.closeQuietly(in);
            MemTracker.getInstance().remove(in);
            in = null;
        }

        @Override
        protected void finalize() throws Throwable
        {
            assert in == null;
        }
    }


    public static class StringFileStream extends ByteArrayFileStream
    {
        public StringFileStream(String s) throws UnsupportedEncodingException
        {
            super(toBytes(s));
        }

        static byte[] toBytes(String s)
        {
            return s.getBytes(StringUtilsLabKey.DEFAULT_CHARSET);
        }
    }


    public static class FileFileStream implements FileStream
    {
        FileInputStream in;

        public FileFileStream(File f) throws IOException
        {
            in = new FileInputStream(f);
            MemTracker.getInstance().put(in);
        }

        public FileFileStream(FileInputStream fin) throws IOException
        {
            in = fin;
            MemTracker.getInstance().put(in);
        }

        public long getSize() throws IOException
        {
            return in.getChannel().size();
        }

        public InputStream openInputStream() throws IOException
        {
            return in;
        }

        public void closeInputStream() throws IOException
        {
            IOUtils.closeQuietly(in);
            MemTracker.getInstance().remove(in);
            in = null;
        }

        @Override
        protected void finalize() throws Throwable
        {
            assert in == null;
        }
    }
}
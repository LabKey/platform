/*
 * Copyright (c) 2009-2015 LabKey Corporation
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
    long getSize() throws IOException;
    InputStream openInputStream() throws IOException;
    void closeInputStream() throws IOException;


    FileStream EMPTY = new FileStream()
    {
        @Override
        public long getSize()
        {
            return 0;
        }

        @Override
        public InputStream openInputStream()
        {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void closeInputStream()
        {
        }
    };


    class ByteArrayFileStream implements FileStream
    {
        private ByteArrayInputStream in;

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
            this(new ByteArrayInputStream(out.toByteArray()));
            MemTracker.getInstance().put(in);
        }

        @Override
        public long getSize()
        {
            return in.available();
        }

        @Override
        public InputStream openInputStream()
        {
            return in;
        }

        @Override
        public void closeInputStream()
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


    class StringFileStream extends ByteArrayFileStream
    {
        public StringFileStream(String s)
        {
            super(toBytes(s));
        }

        static byte[] toBytes(String s)
        {
            return s.getBytes(StringUtilsLabKey.DEFAULT_CHARSET);
        }
    }


    class FileFileStream implements FileStream
    {
        private FileInputStream in;

        public FileFileStream(File f) throws IOException
        {
            this(new FileInputStream(f));
        }

        public FileFileStream(FileInputStream fin)
        {
            in = fin;
            MemTracker.getInstance().put(in);
        }

        @Override
        public long getSize() throws IOException
        {
            return in.getChannel().size();
        }

        @Override
        public InputStream openInputStream()
        {
            return in;
        }

        @Override
        public void closeInputStream()
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
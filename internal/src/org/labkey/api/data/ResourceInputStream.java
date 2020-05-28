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

package org.labkey.api.data;

import java.io.IOException;
import java.io.InputStream;

/**
 * User: brittp
 * Date: Aug 20, 2006
 * Time: 10:50:39 AM
 */
public class ResourceInputStream extends InputStream
{
    public static interface ResourceCloser
    {
        public void close();
    }

    private ResourceCloser _closer;
    private InputStream _wrapped;

    public ResourceInputStream(InputStream stream, ResourceCloser closer)
    {
        _wrapped = stream;
        _closer = closer;
    }

    @Override
    public int read() throws IOException
    {
        return _wrapped.read();
    }

    @Override
    public int available() throws IOException
    {
        return _wrapped.available();
    }

    @Override
    public void close() throws IOException
    {
        _closer.close();
        _wrapped.close();
    }

    @Override
    public synchronized void mark(int readlimit)
    {
        _wrapped.mark(readlimit);
    }

    @Override
    public boolean markSupported()
    {
        return _wrapped.markSupported();
    }

    @Override
    public int read(byte b[]) throws IOException
    {
        return _wrapped.read(b);
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException
    {
        return _wrapped.read(b, off, len);
    }

    @Override
    public synchronized void reset() throws IOException
    {
        _wrapped.reset();
    }

    @Override
    public long skip(long n) throws IOException
    {
        return _wrapped.skip(n);
    }
}

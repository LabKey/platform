/*
 * Copyright (c) 2012 LabKey Corporation
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

import java.io.IOException;
import java.io.InputStream;

/**
 * User: adam
 * Date: 7/2/12
 * Time: 5:10 PM
 */
public class InputStreamWrapper extends InputStream
{
    private final InputStream _is;

    public InputStreamWrapper(InputStream is)
    {
        _is = is;
    }

    @Override
    public int read() throws IOException
    {
        return _is.read();
    }

    @Override
    public int read(byte[] b) throws IOException
    {
        return _is.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException
    {
        return _is.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException
    {
        return _is.skip(n);
    }

    @Override
    public int available() throws IOException
    {
        return _is.available();
    }

    @Override
    public void close() throws IOException
    {
        _is.close();
    }

    @Override
    public void mark(int readlimit)
    {
        _is.mark(readlimit);
    }

    @Override
    public void reset() throws IOException
    {
        _is.reset();
    }

    @Override
    public boolean markSupported()
    {
        return _is.markSupported();
    }
}

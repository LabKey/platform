/*
 * Copyright (c) 2022-2026 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

/**
 * Limits the underlying InputStream to return at most a given number of bytes.
 * Taken from https://stackoverflow.com/questions/15445504/copy-inputstream-abort-operation-if-size-exceeds-limit
 */
public class LimitedSizeInputStream extends InputStream
{
    private final InputStream original;
    private final long maxSize;
    private long total;

    public LimitedSizeInputStream(InputStream original, long maxSize)
    {
        this.original = original;
        this.maxSize = maxSize;
    }

    @Override
    public int read() throws IOException
    {
        int i = original.read();
        if (i >= 0) incrementCounter(1);
        return i;
    }

    @Override
    public int read(byte @NotNull [] b) throws IOException
    {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte @NotNull [] b, int off, int len) throws IOException
    {
        int i = original.read(b, off, len);
        if (i >= 0) incrementCounter(i);
        return i;
    }

    private void incrementCounter(int size) throws IOException
    {
        if (total + size > maxSize) throw new LimitReachedException("InputStream exceeded maximum size of " + maxSize + " bytes.", total);
        total += size;
    }

    /**
     * Thrown to indicate we hit the cap
     */
    public static class LimitReachedException extends IOException
    {
        private final long _bytesRead;

        public LimitReachedException(String message, long bytesRead)
        {
            super(message);
            _bytesRead = bytesRead;
        }

        public long getBytesRead()
        {
            return _bytesRead;
        }
    }

    @Override
    public void close() throws IOException
    {
        if (original != null)
            original.close();

        super.close();
    }
}

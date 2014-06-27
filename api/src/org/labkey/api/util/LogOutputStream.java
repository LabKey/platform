/*
 * Copyright (c) 2014 LabKey Corporation
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

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * User: adam
 * Date: 6/24/2014
 * Time: 5:30 PM
 */

// An OutputStream that sends its output to a log4j Logger, flushing at each line break.
public class LogOutputStream extends ByteArrayOutputStream
{
    private static final int INITIAL_BUFFER_SIZE = 1000;

    private final Logger _logger;
    private final Level _level;

    // TODO: Pass in line separator and support multiple characters. PrintWriter.println() uses system line separator (and
    // there's no way to override this behavior), which currently results in double-spacing when used on Windows.
    public LogOutputStream(Logger logger, Level level)
    {
        super(INITIAL_BUFFER_SIZE);
        _logger = logger;
        _level = level;
    }

    @Override
    public void write(int b)
    {
        if ('\n' == b || '\r' == b)
            flush();
        else
            super.write(b);
    }

    @Override
    public void write(@NotNull byte[] bytes) throws IOException
    {
        for (byte b : bytes)
            write(b);
    }

    @Override
    public synchronized void write(byte[] bytes, int off, int len)
    {
        for (int i = off; i < len; i++)
            write(bytes[i]);
    }

    @Override
    public void flush()
    {
        byte[] buffer = toByteArray();
        String line = new String(buffer);
        _logger.log(_level, line);
        count = 0;  // Clear the buffer
    }
}

/*
 * Copyright (c) 2020-2026 LabKey Corporation
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * This loggerWriter will log the contents in a file.
 * */
public class FileLoggerWriter implements LoggerWriter, AutoCloseable
{
    private final String LINE_SEP = System.lineSeparator();
    private static final Logger LOG = LogManager.getLogger(FileLoggerWriter.class);

    private final File _file;
    private BufferedWriter _writer;

    public FileLoggerWriter(File file)
    {
        _file = file;
        // Make sure that we try to mount the drive (if needed) before using the file
        NetworkDrive.exists(_file);
        try
        {
            _writer = new BufferedWriter(new FileWriter(_file, true));
        }
        catch (IOException e)
        {
            LOG.error("Failed opening the file - {}", _file.getName(), e);
        }

    }

    @Override
    public void close()
    {
        try
        {
            _writer.close();
        }
        catch (IOException e)
        {
            LOG.error("Unable to close the file - {}", _file.getName(), e);
        }
    }

    private boolean isFilePresent()
    {
        return _file != null;
    }

    @Override
    public void write(String message, @Nullable Throwable t)
    {
        if (isFilePresent())
        {
            try
            {
                _writer.write(message);
                _writer.write(LINE_SEP);

                if (null != t)
                {
                    StackTraceElement[] stackTraceElements = t.getStackTrace();
                    if (stackTraceElements != null)
                    {
                        for (StackTraceElement stackTraceElement : stackTraceElements)
                        {
                            _writer.write(stackTraceElement.toString());
                            _writer.write(LINE_SEP);
                        }
                    }
                }
            }
            catch (IOException e)
            {
                LOG.error("Failed appending to file - {}", _file.getName(), e);
            }
        }
        else
        {
            LOG.error("File not provided to write logs.");
        }

    }

    @Override
    public void debug(String message)
    {
        write(message, null);
    }

    @Override
    public void debug(String message, Throwable t)
    {
        write(message, t);
    }

    @Override
    public void error(String message)
    {
        write(message, null);
    }

    @Override
    public void error(String message, Throwable t)
    {
        write(message, t);
    }

    @Override
    public void info(String message)
    {
        write(message, null);
    }

    @Override
    public void info(String message, Throwable t)
    {
        write(message, t);
    }

    @Override
    public void fatal(String message)
    {
        write(message, null);
    }

    @Override
    public void fatal(String message, Throwable t)
    {
        write(message, t);
    }

    @Override
    public void trace(String message)
    {
        write(message, null);
    }

    @Override
    public void trace(String message, Throwable t)
    {
        write(message, t);
    }
}

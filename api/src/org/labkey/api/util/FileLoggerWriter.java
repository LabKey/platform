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
public class FileLoggerWriter implements LoggerWriter
{
    private final String LINE_SEP = System.getProperty("line.separator");
    private static Logger LOG = LogManager.getLogger(FileLoggerWriter.class);

    private File _file;

    public void writeToFile(File file)
    {
        // Make sure that we try to mount the drive (if needed) before using the file
        NetworkDrive.exists(file);
        _file = file;
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
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(_file, true)))
            {
                writer.write(message);
                writer.write(LINE_SEP);

                if (null != t)
                {
                    StackTraceElement[] stackTraceElements = t.getStackTrace();
                    if (stackTraceElements != null)
                    {
                        for (StackTraceElement stackTraceElement : stackTraceElements)
                        {
                            writer.write(stackTraceElement.toString());
                            writer.write(LINE_SEP);
                        }
                    }
                }
            }
            catch (IOException e)
            {
                LOG.error("Failed appending to file.", e);
            }
        }

        LOG.error("File not provided to write logs.");

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

package org.labkey.api.util;

import org.jetbrains.annotations.Nullable;

/**
 * This is the helper class to provide a custom logger writer which can be
 * used to route log messages to a different location than the logger. ex - to a file.
 *
 * User : ankurj
 * Date : Jul 23, 2020
 * */

public interface LoggerWriter
{
    void write(String message, @Nullable Throwable t);

    void debug(String message);

    void debug(String message, Throwable t);

    void error(String message);

    void error(String message, Throwable t);

    void info(String message);

    void info(String message, Throwable t);

    void fatal(String message);

    void fatal(String message, Throwable t);

    void trace(String message);

    void trace(String message, Throwable t);
}

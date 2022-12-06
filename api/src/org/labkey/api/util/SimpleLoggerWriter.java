package org.labkey.api.util;

import org.apache.logging.log4j.Logger;

/**
 * Simple implementation of LoggerWriter that uses the regular logger.
 */
public class SimpleLoggerWriter implements LoggerWriter
{
    private final Logger _log;

    public SimpleLoggerWriter(Logger log)
    {
        _log = log;
    }

    @Override
    public void write(String message, Throwable t)
    {

    }

    @Override
    public void debug(String message)
    {
        _log.debug(message);
    }

    @Override
    public void debug(String message, Throwable t)
    {
        _log.debug(message, t);
    }

    @Override
    public void error(String message)
    {
        _log.error(message);
    }

    @Override
    public void error(String message, Throwable t)
    {
        _log.error(message, t);
    }

    @Override
    public void info(String message)
    {
        _log.info(message);
    }

    @Override
    public void info(String message, Throwable t)
    {
        _log.info(message, t);
    }

    @Override
    public void fatal(String message)
    {
        _log.fatal(message);
    }

    @Override
    public void fatal(String message, Throwable t)
    {
        _log.fatal(message, t);
    }

    @Override
    public void trace(String message)
    {
        _log.trace(message);
    }

    @Override
    public void trace(String message, Throwable t)
    {
        _log.trace(message, t);
    }
}

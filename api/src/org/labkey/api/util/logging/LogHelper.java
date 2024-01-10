package org.labkey.api.util.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keep a short note about what a given class will log to help admins enable/disable logging of interest via the
 * Loggers page in the Admin Console.
 */
public class LogHelper
{
    public static final String LOG_HOME_PROPERTY_NAME = "labkey.log.home";

    private static final Map<String, String> LOGGER_NOTES = new ConcurrentHashMap<>();

    private static Logger registerNote(Logger logger, String note)
    {
        // Always use the Logger's name when saving or retrieving notes
        LOGGER_NOTES.put(logger.getName(), note);
        return logger;
    }

    public static Logger getLogger(Class<?> c, String note)
    {
        //noinspection SSBasedInspection
        return registerNote(LogManager.getLogger(c), note);
    }

    public static Logger getLogger(Package p, String note)
    {
        //noinspection SSBasedInspection
        return registerNote(LogManager.getLogger(p.getName()), note);
    }

    public static String getNote(String loggerName)
    {
        return LOGGER_NOTES.get(loggerName);
    }

    public static String getLabKeyLogDir()
    {
        return System.getProperty(LOG_HOME_PROPERTY_NAME);
    }
}

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

    public static Logger getLogger(Class<?> c, String note)
    {
        LOGGER_NOTES.put(c.getName(), note);
        //noinspection SSBasedInspection
        return LogManager.getLogger(c);
    }

    public static String getNote(String className)
    {
        return LOGGER_NOTES.get(className);
    }

    public static String getLabKeyLogDir()
    {
        return System.getProperty(LOG_HOME_PROPERTY_NAME);
    }
}

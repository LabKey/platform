/*
 * Copyright (c) 2021-2026 LabKey Corporation
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

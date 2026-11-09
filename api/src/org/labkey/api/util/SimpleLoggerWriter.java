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

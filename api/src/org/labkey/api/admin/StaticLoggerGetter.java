package org.labkey.api.admin;

import org.apache.log4j.Logger;

/**
 * Implementation used when an import/export context is used within an action or otherwise outside of a pipeline job.
 * In these cases, we can hold on the Logger directly because we don't need to be serialized.
 * User: jeckels
 * Date: 10/31/12
 */
public class StaticLoggerGetter implements LoggerGetter
{
    private final Logger _logger;

    public StaticLoggerGetter(Logger logger)
    {
        _logger = logger;
    }

    @Override
    public Logger getLogger()
    {
        return _logger;
    }
}

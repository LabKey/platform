package org.labkey.api.mbean;

import org.apache.log4j.spi.LoggingEvent;

import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: 2012-02-28
 * Time: 4:06 PM
 */
public interface ErrorsMXBean
{
    Date getTime();
    String getMessage();
    String getLevel();
    Error[] getErrors();
    void clear();

    public interface Error
    {
        Date getTime();
        String getMessage();
        String getThreadName();
        String getLevel();
        String getLoggerName();
    }
}
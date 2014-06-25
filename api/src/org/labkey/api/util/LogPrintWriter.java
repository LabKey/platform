package org.labkey.api.util;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.PrintWriter;

/**
 * User: adam
 * Date: 6/24/2014
 * Time: 10:28 PM
 */

// A PrintWriter that sends its output to a log4j Logger, flushing at each line break.
public class LogPrintWriter extends PrintWriter
{
    public LogPrintWriter(Logger logger, Level level)
    {
        super(new LogOutputStream(logger, level));
    }
}

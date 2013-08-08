package org.labkey.api.query;

import org.apache.log4j.Level;

/**
 * Created with IntelliJ IDEA.
 * User: matthew
 * Date: 8/7/13
 * Time: 3:43 PM
 */

public class QueryParseWarning extends QueryParseException
{
    public QueryParseWarning(String message, Throwable cause, int line, int column)
    {
        super(message, cause, line, column);
        _level = Level.WARN_INT;
    }
}

package org.labkey.api.util;

/**
 * Created by IntelliJ IDEA.
 * User: brittp
 * Date: Jun 1, 2009
 * Time: 4:14:31 PM
 *
 * Marker class to allow TabLoader and ConvertHelper to identify and parse time/duration objects.
 */
public class TimeOnlyDate extends java.util.Date
{
    public TimeOnlyDate(long time)
    {
        super(time);
    }
}

package org.labkey.api.util;

/**
 * Marker class to allow ConvertHelper and DataLoader to identify and parse "simple" time objects, without being too lenient
 */
public class SimpleTime extends java.sql.Time
{
    public SimpleTime(long time)
    {
        super(time);
    }
}

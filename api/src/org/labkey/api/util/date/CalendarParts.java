package org.labkey.api.util.date;
import java.time.DayOfWeek;
import java.util.TimeZone;


public record CalendarParts(String string, TimeZone tz, Integer tzOffset, int year, int mon, int mday, DayOfWeek weekDay, int hour, int min, int sec, long nanos)
{
    @lombok.Builder
    static class Builder
    {
        String string = null;
        TimeZone tz = null;
        Integer tzOffset = null;        // can be negative so use null for not set
        int year = -1;
        int mon = -1;
        int mday = -1;
        DayOfWeek weekDay = null;
        int hour = -1;
        int min = -1;
        int sec = -1;
        int nanos = -1;

        Builder setMillis(int ms)
        {
            nanos = ms * 1_000_000;
            return this;
        }

        CalendarParts build()
        {
            return new CalendarParts(string, tz, tzOffset, year, mon, mday, weekDay, hour, min, sec, nanos);
        }
    }
}

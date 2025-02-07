package org.labkey.api.util.date;
import org.labkey.api.util.DateUtil;

import java.time.DayOfWeek;
import java.util.Calendar;
import java.util.TimeZone;

import static java.util.Calendar.DAY_OF_WEEK;
import static java.util.Calendar.HOUR;
import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.ZONE_OFFSET;

/* For now, we are only storing hours not hours/hours_of_day/ampm.
 * that means scanner needs to reconcile 12/24 hour calculation
 *
 * We could extend Calendar.  For instance, see FastDateScanner.NoopCalendar.  However, it would be a broken Calendar,
 * and it's not the easiest interface to use.
 */

public record CalendarParts(String string, TimeZone tz, Integer tzoffsetMinutes, int year, int mon, int mday, DayOfWeek weekDay, int hour, int min, int sec, long nanos)
{
    static CalendarParts from(String string, Calendar calendar)
    {
        var tz = calendar.getTimeZone();
        var tzoffsetMinutes = calendar.isSet(ZONE_OFFSET) ? calendar.get(ZONE_OFFSET) / (60*1000): null;
        var year = calendar.isSet(Calendar.YEAR) ? calendar.get(Calendar.YEAR) : -1;
        var month = calendar.isSet(Calendar.MONTH) ? calendar.get(Calendar.MONTH) : -1;
        var mday = calendar.isSet(Calendar.DAY_OF_MONTH) ? calendar.get(Calendar.DAY_OF_MONTH) : -1;
        var wday = calendar.isSet(DAY_OF_WEEK) ? DayOfWeek.of(calendar.get(DAY_OF_WEEK)) : null;
        var hour = -1;
        if (calendar.isSet(HOUR_OF_DAY))
            hour = calendar.get(HOUR_OF_DAY);
        else if (calendar.isSet(HOUR))
            hour = calendar.get(HOUR);
        if (hour != -1 && calendar.isSet(Calendar.AM_PM))
            hour = DateUtil.convert12to24(hour, Calendar.PM == calendar.get(Calendar.AM_PM));
        var min = calendar.isSet(Calendar.MINUTE) ? calendar.get(Calendar.MINUTE) : -1;
        var sec = calendar.isSet(Calendar.SECOND) ? calendar.get(Calendar.SECOND) : -1;
        var nanos = calendar.isSet(Calendar.MILLISECOND) ? calendar.get(Calendar.MILLISECOND) * 1_000_000 : -1;
        return new CalendarParts(string, tz, tzoffsetMinutes, year, month, mday, wday, hour, min, sec, nanos);
    }

    // TODO use lombok
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

        Builder(String string)
        {
            this.string = string;
        }

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

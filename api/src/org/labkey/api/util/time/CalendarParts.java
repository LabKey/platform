/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.api.util.time;

import java.util.Calendar;
import java.util.TimeZone;

import static java.util.Calendar.DST_OFFSET;
import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.HOUR;
import static java.util.Calendar.MILLISECOND;
import static java.util.Calendar.MINUTE;
import static java.util.Calendar.SECOND;
import static java.util.Calendar.YEAR;
import static java.util.Calendar.ZONE_OFFSET;

// java.text.CalendarBuilder is package private
// java.util.Calendar.Builder is public, but has no getters.  isSet() is private and it does not have get()
// java.util.Calendar can manipulate the set values.  We want the values to remain they are found in the source.
public class CalendarParts
{
    public final long NANOS_IN_MILLI = 1_000_000L;
    public final long NANOS_IN_SECOND = 1_000_000_000L;

    final _Calendar parts = new _Calendar();

    long nanos;

    public CalendarParts setTimeZone(TimeZone value)
    {
        parts.setTimeZone(value);
        return this;
    }

    public CalendarParts set(int field, int value)
    {
        parts.set(field, value);
        if (Calendar.MILLISECOND == field)
            nanos = value * NANOS_IN_MILLI;
        return this;
    }

    public boolean isSet(int field)
    {
        return parts.isSet(field);
    }

    public boolean isSet(int field1, int field2)
    {
        return parts.isSet(field1) && parts.isSet(field2);
    }

    public boolean isSet(int field1, int field2, int field3)
    {
        return parts.isSet(field1) && parts.isSet(field2) && parts.isSet(field3);
    }

    public boolean isHourSet()
    {
        return anySet(HOUR, HOUR_OF_DAY);
    }

    public boolean isTimezoneSet()
    {
        return null != parts.getTimeZone() || parts.isSet(ZONE_OFFSET);
    }

    public void clearTimezone()
    {
        parts.setTimeZone(null);
        parts.clear(ZONE_OFFSET);
        parts.clear(DST_OFFSET);
    }

    public boolean anySet(int field1, int field2)
    {
        return parts.isSet(field1) || parts.isSet(field2);
    }

    public boolean anySet(int field1, int field2, int field3)
    {
        return parts.isSet(field1) || parts.isSet(field2) || parts.isSet(field3);
    }

    public TimeZone getTimeZone()
    {
        return parts.getTimeZone();
    }

    public int get(int field)
    {
        return parts.get(field);
    }

    // Nanos and Millis are not separate fields, they are two representations of the same fields.
    // setting one overwrites the other.
    @SuppressWarnings("LombokGetterMayBeUsed")
    public long getNanos()
    {
        return nanos;
    }

    public int getYear()
    {
        return parts.get(YEAR);
    }

    public CalendarParts setYear(int year)
    {
        parts.set(YEAR, year);
        return this;
    }

    public int getMonth()
    {
        return parts.get(Calendar.MONTH);
    }

    public CalendarParts setMonth(int month)
    {
        parts.set(Calendar.MONTH, month);
        return this;
    }

    public int getDayOfMonth()
    {
        return parts.get(Calendar.DAY_OF_MONTH);
    }

    public CalendarParts setDayOfMonth(int dayOfMonth)
    {
        parts.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        return this;
    }

    public int getHourOfDay()
    {
        return parts.get(Calendar.HOUR_OF_DAY);
    }

    public CalendarParts setHourOfDay(int hours)
    {
        parts.clear(HOUR);
        parts.set(HOUR_OF_DAY, hours);
        return this;
    }

    public CalendarParts setHour(int hours)
    {
        parts.clear(HOUR_OF_DAY);
        parts.set(HOUR, hours);
        return this;
    }

    public CalendarParts setMinute(int minutes)
    {
        parts.set(MINUTE, minutes);
        return this;
    }

    public CalendarParts setSeconds(int seconds)
    {
        parts.set(SECOND, seconds);
        return this;
    }

    public CalendarParts setMilliseconds(int millis)
    {
        set(Calendar.MILLISECOND, millis);
        nanos = millis * NANOS_IN_MILLI;
        return this;
    }

    public CalendarParts setNanoseconds(long nanos)
    {
        this.nanos = nanos;
        if (nanos / NANOS_IN_MILLI >= Integer.MAX_VALUE-1)
            throw new IllegalArgumentException();
        set(Calendar.MILLISECOND, (int)Math.round((double)nanos / NANOS_IN_MILLI));
        return this;
    }


    public void setCalendar(Calendar cal)
    {
        for (int field=0 ; field < Calendar.FIELD_COUNT; field++)
        {
            if (parts.isSet(field))
                cal.set(field, parts.get(field));
        }
        // edge case to avoid failure if cal.lenient()==false
        // don't round up to 1000 ms when populating calendar
        // if you care about nanos you need to be using java.sql.Timestamp anyway
        if (!cal.isLenient())
        {
            if (this.isSet(MILLISECOND) && this.get(MILLISECOND) == 1000 && nanos < NANOS_IN_SECOND)
                cal.set(MILLISECOND, 999);
        }
        if (null != parts.getTimeZone())
            cal.setTimeZone(parts.getTimeZone());
    }


    static public class _Calendar extends Calendar
    {
        @Override
        public void add(int field, int amount)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void computeTime()
        {
        }

        @Override
        protected void computeFields()
        {
        }

        @Override
        public void roll(int field, boolean up)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getMinimum(int field)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getMaximum(int field)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getGreatestMinimum(int field)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getLeastMaximum(int field)
        {
            throw new UnsupportedOperationException();
        }
    }

}

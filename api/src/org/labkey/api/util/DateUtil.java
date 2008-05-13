/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.collections.FastHashMap;
import org.apache.commons.lang.time.DateUtils;
import org.apache.commons.lang.time.FastDateFormat;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.*;


public class DateUtil
{
    private DateUtil()
    {
    }

    private static FastHashMap tzCache = new FastHashMap();
    private static Locale _localeDefault = Locale.getDefault();
    private static TimeZone _timezoneDefault = TimeZone.getDefault();
    private static int currentYear = new GregorianCalendar().get(Calendar.YEAR);
    private static int twoDigitCutoff = (currentYear - 80) % 100;
    private static int defaultCentury = (currentYear - 80) - twoDigitCutoff;

    private static final String _standardDateFormatString = "yyyy-MM-dd";
    private static final String _standardDateTimeFormatString = "yyyy-MM-dd HH:mm";


    /**
     * GregorianCalendar is expensive because it calls computeTime() in setTimeInMillis()
     * (which is called in the constructor)
     */
    private static class _Calendar extends GregorianCalendar
    {
        public _Calendar(TimeZone tz, Locale locale)
        {
            super(tz, locale);
        }


        public _Calendar(TimeZone tz, Locale locale, int year, int mon, int mday, int hour, int min, int sec, int ms)
        {
            super(tz, locale);
            set(year, mon, mday, hour, min, sec);
            set(Calendar.MILLISECOND, ms);
        }

        public _Calendar(TimeZone tz, Locale locale, long l)
        {
            super(tz, locale);
            setTimeInMillis(l);
        }


        public void setTimeInMillis(long millis)
        {
            isTimeSet = true;
            time = millis;
            areFieldsSet = false;
        }
    }


    public static Calendar newCalendar(TimeZone tz, int year, int mon, int mday, int hour, int min, int sec)
    {
        return new _Calendar(tz, _localeDefault, year, mon, mday, hour, min, sec, 0);
    }


    public static Calendar newCalendar(TimeZone tz)
    {
        return new _Calendar(tz, _localeDefault);
    }


    public static Calendar newCalendar()
    {
        return new _Calendar(_timezoneDefault, _localeDefault);
    }


    public static Calendar newCalendar(long l)
    {
        return new _Calendar(_timezoneDefault, _localeDefault, l);
    }


    public static Calendar now()
    {
        return new _Calendar(_timezoneDefault, _localeDefault);
    }


    public static String nowISO()
    {
        return toISO(System.currentTimeMillis());
    }


    public static String toISO(long l, boolean fFullISO)
    {
        StringBuilder sb = new StringBuilder("1999-12-31 23:59:59.999".length());
        Calendar c = newCalendar(l);
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH)+1;
        int day = c.get(Calendar.DAY_OF_MONTH);
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int min = c.get(Calendar.MINUTE);
        int sec = c.get(Calendar.SECOND);
        int ms = c.get(Calendar.MILLISECOND);

        if (year < 0)
            throw new IllegalArgumentException("BC date not supported");
        if (year < 1000)
        {
            sb.append('0');
            if (year < 100)
                sb.append('0');
            if (year < 10)
                sb.append('0');
        }
        sb.append(year);
        sb.append('-');
        if (month < 10)
            sb.append('0');
        sb.append(month);
        sb.append('-');
        if (day < 10)
            sb.append('0');
        sb.append(day);

        if (!fFullISO && hour==0 && min==0 && sec==0 && ms ==0)
            return sb.toString();

        sb.append(' ');
        if (hour < 10)
            sb.append('0');
        sb.append(hour);
        sb.append(':');
        if (min < 10)
            sb.append('0');
        sb.append(min);

        if (!fFullISO && sec==0 && ms==0)
            return sb.toString();

        sb.append(':');
        if (sec < 10)
            sb.append('0');
        sb.append(sec);

        if (!fFullISO && ms==0)
            return sb.toString();

        sb.append('.');
        if (ms < 100)
        {
            sb.append('0');
            if (ms < 10)
                sb.append('0');
        }
        sb.append(ms);
        return sb.toString();
    }

    public static String toISO(long l)
    {
        return toISO(l, true);
    }

    public static String toISO(java.util.Date d)
    {
        return toISO(d.getTime(), true);
    }


    /**
     * Javascript style parsing, assumes US locale
     *
     * Copied from RHINO (www.mozilla.org/rhino) and modified
     */
    public static long parseStringUS(String s, boolean allowTimeOnly)
    {
        int year = -1;
        int mon = -1;
        int mday = -1;
        int hour = -1;
        int min = -1;
        int sec = -1;
        char c = 0;
        char si = 0;
        int i = 0;
        int n = -1;
        int tzoffset = -1;
        char prevc = 0;
        int limit = 0;
        boolean seenplusminus = false;

        limit = s.length();
        while (i < limit)
        {
            c = s.charAt(i);
            i++;
            if (c <= ' ' || c == ',' || c == '-')
            {
                if (i < limit)
                {
                    si = s.charAt(i);
                    if (c == '-' && '0' <= si && si <= '9')
                    {
                        prevc = c;
                    }
                }
                continue;
            }
            if (c == '(')
            {
                int depth = 1;
                while (i < limit)
                {
                    c = s.charAt(i);
                    i++;
                    if (c == '(')
                        depth++;
                    else if (c == ')')
                        if (--depth <= 0)
                            break;
                }
                continue;
            }
            if ('0' <= c && c <= '9')
            {
                n = c - '0';
                while (i < limit && '0' <= (c = s.charAt(i)) && c <= '9')
                {
                    n = n * 10 + c - '0';
                    i++;
                }

                /* allow TZA before the year, so
                 * 'Wed Nov 05 21:49:11 GMT-0800 1997'
                 * works */

                /* uses of seenplusminus allow : in TZA, so Java
                 * no-timezone style of GMT+4:30 works
                 */
                if ((prevc == '+' || prevc == '-')/*  && year>=0 */)
                {
                    /* make ':' case below change tzoffset */
                    seenplusminus = true;

                    /* offset */
                    if (n < 24)
                        n = n * 60; /* EG. "GMT-3" */
                    else
                        n = n % 100 + n / 100 * 60; /* eg "GMT-0430" */
                    if (prevc == '+')       /* plus means east of GMT */
                        n = -n;
                    if (tzoffset != 0 && tzoffset != -1)
                        throw new ConversionException(s);
                    tzoffset = n;
                }
                else if (n >= 70 ||
                        (prevc == '/' && mon >= 0 && mday >= 0 && year < 0))
                {
                    if (year >= 0)
                        throw new ConversionException(s);
                    else if (c <= ' ' || c == ',' || c == '/' || i >= limit)
                    {
                        if (n >= 100)
                            year = n;
                        else if (n > twoDigitCutoff)
                            year = n + defaultCentury;
                        else
                            year = n + defaultCentury + 100;
                    }
                    else
                        throw new ConversionException(s);
                }
                else if (c == ':')
                {
                    if (hour < 0)
                        hour = n;
                    else if (min < 0)
                        min = n;
                    else
                        throw new ConversionException(s);
                }
                else if (c == '/')
                {
                    if (mon < 0)
                        mon = n - 1;
                    else if (mday < 0)
                        mday = n;
                    else
                        throw new ConversionException(s);
                }
                else if (i < limit && c != ',' && c > ' ' && c != '-')
                {
                    throw new ConversionException(s);
                }
                else if (seenplusminus && n < 60)
                {  /* handle GMT-3:30 */
                    if (tzoffset < 0)
                        tzoffset -= n;
                    else
                        tzoffset += n;
                }
                else if (hour >= 0 && min < 0)
                {
                    min = n;
                }
                else if (min >= 0 && sec < 0)
                {
                    sec = n;
                }
                else if (mday < 0)
                {
                    mday = n;
                }
                else
                {
                    throw new ConversionException(s);
                }
                prevc = 0;
            }
            else if (c == '/' || c == ':' || c == '+' || c == '-')
            {
                prevc = c;
            }
            else
            {
                int st = i - 1;
                while (i < limit)
                {
                    c = s.charAt(i);
                    if (!(('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z')))
                        break;
                    i++;
                }
                int letterCount = i - st;
                if (letterCount < 2)
                    throw new ConversionException(s);
                /*
                 * Use ported code from jsdate.c rather than the locale-specific
                 * date-parsing code from Java, to keep js and rhino consistent.
                 * Is this the right strategy?
                 */
                String wtb = "am;pm;"
                        + "monday;tuesday;wednesday;thursday;friday;"
                        + "saturday;sunday;"
                        + "january;february;march;april;may;june;"
                        + "july;august;september;october;november;december;"
                        + "gmt;ut;utc;est;edt;cst;cdt;mst;mdt;pst;pdt;";
                int index = 0;
                for (int wtbOffset = 0; ;)
                {
                    int wtbNext = wtb.indexOf(';', wtbOffset);
                    if (wtbNext < 0)
                        throw new ConversionException(s);
                    if (wtb.regionMatches(true, wtbOffset, s, st, letterCount))
                        break;
                    wtbOffset = wtbNext + 1;
                    ++index;
                }
                if (index < 2)
                {
                    /*
                     * AM/PM. Count 12:30 AM as 00:30, 12:30 PM as
                     * 12:30, instead of blindly adding 12 if PM.
                     */
                    if (hour > 12 || hour < 0)
                    {
                        throw new ConversionException(s);
                    }
                    else if (index == 0)
                    {
                        // AM
                        if (hour == 12)
                            hour = 0;
                    }
                    else
                    {
                        // PM
                        if (hour != 12)
                            hour += 12;
                    }
                }
                else if ((index -= 2) < 7)
                {
                    // ignore week days
                }
                else if ((index -= 7) < 12)
                {
                    // month
                    if (mon < 0)
                    {
                        mon = index;
                    }
                    else if (mday < 0 && prevc == '/')
                    {
                        // handle 01/Jan/2001 case (strange I know, the customer is always right)
                        // of course this probably makes Jan/Feb/2001 legal as well
                        mday = mon+1;
                        mon = index;
                    }
                    else
                    {
                        throw new ConversionException(s);
                    }
                }
                else
                {
                    index -= 12;
                    // timezones
                    switch (index)
                    {
                        case 0 /* gmt */:
                            tzoffset = 0;
                            break;
                        case 1 /* ut */:
                            tzoffset = 0;
                            break;
                        case 2 /* utc */:
                            tzoffset = 0;
                            break;
                        case 3 /* est */:
                            tzoffset = 5 * 60;
                            break;
                        case 4 /* edt */:
                            tzoffset = 4 * 60;
                            break;
                        case 5 /* cst */:
                            tzoffset = 6 * 60;
                            break;
                        case 6 /* cdt */:
                            tzoffset = 5 * 60;
                            break;
                        case 7 /* mst */:
                            tzoffset = 7 * 60;
                            break;
                        case 8 /* mdt */:
                            tzoffset = 6 * 60;
                            break;
                        case 9 /* pst */:
                            tzoffset = 8 * 60;
                            break;
                        case 10 /* pdt */:
                            tzoffset = 7 * 60;
                            break;
                        default:
                            throw new ConversionException(s);
                    }
                }
            }
        }


        if (year < 0 || mon < 0 || mday < 0)
        {
            if (allowTimeOnly && year < 0 && mon < 0 && mday < 0)
                return 1000L * (hour * (60*60) + (min * 60) + sec);
            throw new ConversionException(s);
        }
        if (sec < 0)
            sec = 0;
        if (min < 0)
            min = 0;
        if (hour < 0)
            hour = 0;

        //
        // This part is changed to work with Java
        //

        TimeZone tz;
        if (tzoffset == -1)
            tz = _timezoneDefault;
        else
        {
            tz = (TimeZone) tzCache.get(tzoffset);
            if (null == tz)
            {
                char sign = tzoffset < 0 ? '+' : '-'; // tzoffset seems to switched from TimeZone sense
                int mins = Math.abs(tzoffset);
                int hr = mins / 60;
                int mn = mins % 60;
                String tzString = "GMT" + sign + (hr / 10) + (hr % 10) + (mn / 10) + (mn % 10);
                tz = TimeZone.getTimeZone(tzString);
                tzCache.put(tzoffset, tz);
            }
        }

        Calendar cal = newCalendar(tz, year, mon, mday, hour, min, sec);
        return cal.getTimeInMillis();
    }


    public static long parseStringJDBC(String s)
    {
        try
        {
            int len = s.length();
            long ms;
            if (len <= 10)
            {
                java.sql.Date d = java.sql.Date.valueOf(s);
                ms = d.getTime();
            }
            else
            {
                if (len == 16 && s.charAt(13)==':') // no seconds 2001-02-03 00:00
                    s = s + ":00";
                if (s.charAt(10) == 'T')
                    s = s.substring(0, 10) + ' ' + s.substring(11, s.length());
                Timestamp ts = Timestamp.valueOf(s);
                ms = ts.getTime();
            }
            return ms;
        }
        catch (Exception x)
        {
            ;
        }
        throw new ConversionException(s);
    }


    public static long parseStringJava(String s)
    {
        try
        {
            return DateFormat.getInstance().parse(s).getTime();
        }
        catch (Exception x)
        {
            ;
        }
        try
        {
            //noinspection deprecation
            return Date.parse(s);
        }
        catch (Exception x)
        {
            ;
        }
        try
        {
            // java.util.Date.toString produces dates in the following format.  Try to
            // convert them here.  This is necessary to pass the DRT when running in a
            // non-US timezone:
            return parseDateTime(s, "EEE MMM dd HH:mm:ss zzz yyyy").getTime();
        }
        catch (Exception x)
        {
            ;
        }

        try
        {
            return parseDateTime(s, "ddMMMyy").getTime();
        }
        catch (Exception x)
        {
            ;
        }


        throw new ConversionException(s);
    }


    // Parse using a specific pattern... used where strict parsing or non-standard pattern is required
    // Note: SimpleDateFormat is not thread-safe, so we create a new one for every parse.
    public static Date parseDateTime(String s, String pattern) throws ParseException
    {
        if (null == s)
            throw new ParseException(s, 0);

        return new SimpleDateFormat(pattern).parse(s);
    }


    // Lenient parsing using a variety of standard formats
    public static long parseDateTime(String s)
    {
        try
        {
            // quick check for JDBC/ISO date
            if (s.length() >= 10 && s.charAt(4) == '-' && s.charAt(7) == '-')
                return parseStringJDBC(s);
        }
        catch (ConversionException x)
        {
            ;
        }

        try
        {
            // strip off trailing decimal :00:00.000
            int ms = 0;
            int len = s.length();
            int period = s.lastIndexOf('.');
            if (period > 6 && period >= len - 4 && period < len - 1 &&
                    s.charAt(period - 3) == ':' &&
                    s.charAt(period - 6) == ':')
            {
                String m = s.substring(period + 1);
                ms = Integer.parseInt(m);
                if (m.length() == 1)
                    ms *= 100;
                else if (m.length() == 2)
                    ms *= 10;
                s = s.substring(0, period);
            }
            long time = parseStringUS(s, false);
            return time + ms;
        }
        catch (ConversionException x)
        {
            ;
        }

        return parseStringJava(s);
    }


    public static String getStandardDateFormatString()
    {
        return _standardDateFormatString;
    }


    public static String getStandardDateTimeFormatString()
    {
        return _standardDateTimeFormatString;
    }


    // Format current date using standard pattern
    public static String formatDate()
    {
        return formatDate(new Date());
    }


    // Format date using standard date pattern
    public static String formatDate(Date date)
    {
        return formatDateTime(date, _standardDateFormatString);
    }


    // Format current date & time using standard date & time pattern
    public static String formatDateTime()
    {
        return formatDateTime(new Date());
    }


    // Format specified date using standard date & time pattern
    public static String formatDateTime(Date date)
    {
        return formatDateTime(date, _standardDateTimeFormatString);
    }


    // Format date & time using specified pattern
    // Note: This implementation is thread-safe and reuses formatters -- SimpleDateFormat is neither 
    public static String formatDateTime(Date date, String pattern)
    {
        if (null == date)
            return null;
        else
            return FastDateFormat.getInstance(pattern).format(date);
    }


    // NOT ISO8601, we do not support year and month
    // PnYnMnDTnH nMnS,
    public static long parseDuration(String s)
    {
        int year = -1;
        int month = -1;
        int day = -1;
        boolean time = false;
        int hour = -1;
        int min = -1;
        double sec = -1;

        int startField = 0;
        int i;
Parse:
        for (i=0 ; i<s.length() ; i++)
        {
            char c = s.charAt(i);
            switch (c)
            {
            case 'P':
                if (i != 0)
                    break Parse;
                startField = i+1;
                break;
            case 'Y': case 'y':
                if (year != -1 || month != -1 || day != -1)
                    break Parse;
                year = Integer.parseInt(s.substring(startField,i));
                startField = i+1;
                break;
            case 'M': case 'm':
                if (!time && month == -1)
                {
                    month = Integer.parseInt(s.substring(startField,i));
                }
                else
                {
                    if (min != -1 || sec != -1)
                        break Parse;
                    min = Integer.parseInt(s.substring(startField,i));
                }
                startField = i+1;
                break;
            case 'D': case 'd':
                if (day != -1 || hour != -1)
                    break Parse;
                time = true;
                day = Integer.parseInt(s.substring(startField,i));
                startField = i+1;
                break;
            case 'T': case 't':
                if (hour != -1 || min != -1 || sec != -1)
                    break Parse;
                time = true;
                startField = i+1;
                break;
            case 'H': case 'h':
                if (hour != -1 || min != -1)
                    break Parse;
                time = true;
                hour = Integer.parseInt(s.substring(startField,i));
                startField = i+1;
                break;
            case 'S': case 's':
                if (sec != -1 || i != s.length()-1)
                    break Parse;
                sec = Double.parseDouble(s.substring(startField,i));
                startField = i+1;
                break;
            case '0': case '1': case '2': case '3': case '4' : case '5': case '6': case '7': case'8': case '9':
                break;
            case '.':
                if (i == startField)
                    break Parse;
                break;
            default:
                break Parse;
            }
        }

        if (i < s.length())
            throw new ConversionException("Illegal duration: " + s);

        // check if month should have been minute
        // can only happen if there is no day or hour specified
        if (month != -1 && day == -1 && hour == -1 && min == -1)
        {
            min = month;
            month = -1;
        }

        if (year != -1 || month != -1)
            throw new ConversionException("Year and month not supported: " + s);

        assert day >= 0 || day == -1;
        assert hour >= 0 || hour == -1;
        assert min >= 0 || min == -1;
        assert sec >= 0 || sec == -1;
        return  makeDuration(Math.max(0,day), Math.max(0,hour), Math.max(0,min), Math.max(0,sec));
    }


    private static long makeDuration(int day, int hour, int min, double sec)
    {
        return  day * DateUtils.MILLIS_PER_DAY +
                hour * DateUtils.MILLIS_PER_HOUR +
                min * DateUtils.MILLIS_PER_MINUTE +
                (int)(sec * DateUtils.MILLIS_PER_SECOND);
    }


    // how ISO8601 do we want to be (v. readable)
    public static String formatDuration(long duration)
    {
        if (duration < 0)
            throw new IllegalArgumentException("negative durations not supported");
        if (duration == 0)
            return "0s";

        StringBuilder s = new StringBuilder();
        long r = duration;

        long day = r / DateUtils.MILLIS_PER_DAY;
        r = r % DateUtils.MILLIS_PER_DAY;
        if (day != 0)
            s.append(String.valueOf(day)).append("d");
        if (r == 0)
            return s.toString();

        long hour = r / DateUtils.MILLIS_PER_HOUR;
        r = r % DateUtils.MILLIS_PER_HOUR;
        if (hour != 0 || s.length() > 0)
            s.append(String.valueOf(hour)).append("h");
        if (r == 0)
            return s.toString();

        long min = r / DateUtils.MILLIS_PER_MINUTE;
        r = r % DateUtils.MILLIS_PER_MINUTE;
        if (min != 0 || s.length() > 0)
            s.append(String.valueOf(min)).append("m");
        if (r == 0)
            return s.toString();

        long sec = r / DateUtils.MILLIS_PER_SECOND;
        long ms = r % DateUtils.MILLIS_PER_SECOND;

        s.append(String.valueOf(sec));
        if (ms != 0)
        {
            s.append('.');
            s.append(ms / 100);
            s.append((ms % 100) / 10);
            s.append(ms % 10);
        }
        s.append("s");
        return s.toString();
    }



    public static class TestCase extends junit.framework.TestCase
    {
        public TestCase()
        {
            super();
        }


        public TestCase(String name)
        {
            super(name);
        }


        public void testDateTime()
        {
            long datetimeExpected = java.sql.Timestamp.valueOf("2001-02-03 04:05:06").getTime();
            long dateExpected = java.sql.Date.valueOf("2001-02-03").getTime();

            assertEquals(datetimeExpected, DateUtil.parseDateTime("2001-02-03 04:05:06"));
            assertEquals(datetimeExpected, DateUtil.parseDateTime("2001-02-03T04:05:06"));
            assertEquals(datetimeExpected, DateUtil.parseDateTime(new Date(datetimeExpected).toString()));
            assertEquals(datetimeExpected, DateUtil.parseDateTime("2/3/01 4:05:06"));
            assertEquals(datetimeExpected, DateUtil.parseDateTime("2/3/2001 4:05:06"));
// TODO: assertEquals(datetimeExpected, DateUtil.parseDateTime(ConvertUtils.convert(new Date(datetimeExpected))));
            assertEquals(datetimeExpected, DateUtil.parseDateTime("2/3/2001 4:05:06.000"));

            assertEquals(dateExpected, DateUtil.parseDateTime("2/3/01"));
            assertEquals(dateExpected, DateUtil.parseDateTime("3-Feb-01"));
            assertEquals(dateExpected, DateUtil.parseDateTime("3Feb01"));
            assertEquals(dateExpected, DateUtil.parseDateTime("3Feb2001"));
            assertEquals(dateExpected, DateUtil.parseDateTime("03Feb01"));
            assertEquals(dateExpected, DateUtil.parseDateTime("03Feb2001"));
            assertEquals(dateExpected, DateUtil.parseDateTime("3 Feb 01"));
            assertEquals(dateExpected, DateUtil.parseDateTime("3 Feb 2001"));
            assertEquals(dateExpected, DateUtil.parseDateTime("3-Feb-01"));
            assertEquals(dateExpected, DateUtil.parseDateTime("3Feb01"));
            assertEquals(dateExpected, DateUtil.parseDateTime("3Feb2001"));
            assertEquals(dateExpected, DateUtil.parseDateTime("03Feb01"));
            assertEquals(dateExpected, DateUtil.parseDateTime("03Feb2001"));            assertEquals(dateExpected, DateUtil.parseDateTime("Feb 03 2001"));
            assertEquals(dateExpected, DateUtil.parseDateTime("February 3, 2001"));
// TODO: assertEquals(dateExpected, DateUtil.parseDateTime(ConvertUtils.convert(new Date(dateExpected))));

            // some zero testing
            datetimeExpected = java.sql.Timestamp.valueOf("2001-02-03 00:00:00.000").getTime();
            assertEquals(datetimeExpected, parseDateTime("2001-02-03 00:00:00.000"));
            assertEquals(datetimeExpected, parseDateTime("2001-02-03 00:00:00"));
            assertEquals(datetimeExpected, parseDateTime("2001-02-03 00:00"));
            assertEquals(datetimeExpected, parseDateTime("2001-02-03"));

            // dd/mmm/yy testing
            assertEquals(parseDateTime("3/Feb/01"), dateExpected);
            assertEquals(parseDateTime("3/FEB/01"), dateExpected);
            assertEquals(parseDateTime("3/FeB/2001"), dateExpected);
            assertEquals(parseDateTime("03/feb/2001"), dateExpected);
            assertEquals(parseDateTime("03/FEB/2001"), dateExpected);
        }


        public void testFormat()
        {
            long l = System.currentTimeMillis();
            for (int i=0 ; i<24 ; i++)
            {
                String ts = new java.sql.Timestamp(l).toString();
                String iso = toISO(l);
                assertEquals(ts.substring(0,20),iso.substring(0,20));
                l += 60*60*1000;
            }

            l = parseDateTime("1999-12-31 23:59:59.999");
            assertEquals(toISO(l, false).length(), "1999-12-31 23:59:59.999".length());
            l -= l % 1000;
            assertEquals(toISO(l, false).length(), "1999-12-31 23:59:59".length());
            l -= l % (60 * 1000);
            assertEquals(toISO(l, false).length(), "1999-12-31 23:59".length());
            l -= l % (60 * 60 * 1000);
            assertEquals(toISO(l, false).length(), "1999-12-31 23:00".length());
            Calendar c = newCalendar(l);
            c.set(Calendar.HOUR,0);
            l = c.getTimeInMillis();
            assertEquals(toISO(l, false).length(), "1999-12-31".length());
        }


        public void testDuration()
        {
            assertEquals(61500L, makeDuration(0,0,1,1.5));
            assertEquals(makeDuration(0,0,0,5), parseDuration("5s"));
            assertEquals(makeDuration(0,0,0,5.001), parseDuration("5.001s"));
            assertEquals(makeDuration(0,0,1,0), parseDuration("1m"));
            assertEquals(makeDuration(0,0,1,0), parseDuration("60s"));
            assertEquals(makeDuration(0,0,2,20), parseDuration("2m20s"));
            assertEquals(makeDuration(0,0,2,20), parseDuration("1m80s"));
            assertEquals(makeDuration(0,1,2,3), parseDuration("1h2m3s"));
            assertEquals(makeDuration(1,2,3,4), parseDuration("1d2h3m4s"));
            assertEquals(makeDuration(1,2,3,4.5), parseDuration("1d2h3m4.500s"));
            try
            {
                parseDuration("1m2d3h");
                assertFalse("unsupported conversion", true);
            }
            catch (ConversionException x) {;}

            // one non-zero field
            assertEquals("1s", formatDuration(makeDuration(0,0,0,1)));
            assertEquals("1m", formatDuration(makeDuration(0,0,1,0)));
            assertEquals("1h", formatDuration(makeDuration(0,1,0,0)));
            assertEquals("1d", formatDuration(makeDuration(1,0,0,0)));

            // one zero field
            assertEquals("2h3m4s", formatDuration(makeDuration(0,2,3,4)));
            assertEquals("1d0h3m4s", formatDuration(makeDuration(1,0,3,4)));
            assertEquals("1d2h0m4s", formatDuration(makeDuration(1,2,0,4)));
            assertEquals("1d2h3m", formatDuration(makeDuration(1,2,3,0)));

            // misc and ms
            assertEquals("1d2h3m4s", formatDuration(makeDuration(1,2,3,4)));
            assertEquals("1h2m3.010s", formatDuration(makeDuration(0,1,2,3.010)));
            assertEquals("1h0m0.010s", formatDuration(makeDuration(0,1,0,0.010)));
        }


        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }
}

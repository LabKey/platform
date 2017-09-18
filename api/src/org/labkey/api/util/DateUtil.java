/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfree.util.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.settings.DateParsingMode;
import org.labkey.api.settings.FolderSettingsCache;
import org.labkey.api.settings.LookAndFeelProperties;

import javax.xml.bind.DatatypeConverter;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;


public class DateUtil
{
    private DateUtil()
    {
    }

    private static final Map<Integer, TimeZone> tzCache = new ConcurrentHashMap<>();
    private static final Locale _localeDefault = Locale.getDefault();
    private static final TimeZone _timezoneDefault = TimeZone.getDefault();
    private static final int currentYear = new GregorianCalendar().get(Calendar.YEAR);
    private static final int twoDigitCutoff = (currentYear - 80) % 100;
    private static final int defaultCentury = (currentYear - 80) - twoDigitCutoff;

    private static final String ISO_DATE_FORMAT_STRING = "yyyy-MM-dd";
    private static final String ISO_TIME_FORMAT_STRING = "HH:mm";
    private static final String ISO_DATE_TIME_FORMAT_STRING = ISO_DATE_FORMAT_STRING + " " + ISO_TIME_FORMAT_STRING;
    private static final String LONG_TIME_FORMAT_STRING = "HH:mm:ss";

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
            //noinspection MagicConstant
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


    // disallow date overflow arithmetic
    public static Calendar newCalendarStrict(TimeZone tz, int year, int mon, int mday, int hour, int min, int sec)
    {
        Calendar cal = new _Calendar(tz, _localeDefault, year, mon, mday, hour, min, sec, 0);
        if (cal.get(Calendar.YEAR) != year ||
            cal.get(Calendar.MONTH) != mon ||
            cal.get(Calendar.DAY_OF_MONTH) != mday ||
            cal.get(Calendar.HOUR_OF_DAY) != hour ||
            cal.get(Calendar.MINUTE) != min ||
            cal.get(Calendar.SECOND) != sec)
            throw new IllegalArgumentException();
        return cal;
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
            throw new IllegalArgumentException("BCE date not supported");
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

    enum Month
    {
        january(0),february(1),march(2),april(3),may(4),june(5),july(6),august(7),september(8),october(9),november(10),december(11);
        int month;
        Month(int i)
        {
            month = i;
        }
    }

    enum Weekday
    {
        monday,tuesday,wednesday,thursday,friday,saturday,sunday
    }

    enum AMPM
    {
        am, pm
    }

    enum TZ
    {
        z("UTC"),gmt("UTC"),ut("UTC"),utc("UTC"),
        // North America
        est(-5*60),edt(-4*60),cst(-6*60),cdt(-5*60),mst(-7*60),mdt(-6*60),pst(-8*60),pdt(-7*60),
        // Europe
        cet("CET"), wet("WET")
        ;

        TimeZone tz=null;
        int tzoffset=-1;
        TZ(int tzoffset)
        {
            this.tzoffset = tzoffset;
        }
        TZ(String id)
        {
            tz = TimeZone.getTimeZone(id);
            assert !"GMT".equals(tz.getID());
        }
    }

    enum ISO
    {
        t    // T : time marker
    }

    private static final NavigableMap<String, Enum> PARTS_MAP = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    static
    {
        Stream.of(AMPM.values(), Month.values(), Weekday.values(), TZ.values(), ISO.values())
            .flatMap(Arrays::stream)
            .forEach(e -> PARTS_MAP.put(e.name(), e));
    }

    private static @Nullable Enum resolveDatePartEnum(String s)
    {
        // Require an exact match if s is one character long
        if (s.length() < 2)
            return PARTS_MAP.get(s);

        // If s is longer than one character then find first key with s as its prefix
        Entry<String, Enum> entry = PARTS_MAP.ceilingEntry(s);

        return (null != entry && StringUtils.startsWithIgnoreCase(entry.getKey(), s)) ? entry.getValue() : null;
    }

    private static Object resolveDatePart(String s)
    {
        Enum e = resolveDatePartEnum(s);

        if (null != e)
            return e instanceof TZ && (null != ((TZ)e).tz) ? ((TZ)e).tz : e;

        TimeZone tz = TimeZone.getTimeZone(s);
        // getTimeZone() unhelpfully returns GMT if the id is not recognized
        if ("GMT".equals(tz.getID()))
            return null;
        return tz;
    }


    private enum DateTimeOption
    {
        DateTime,
        DateOnly,
        TimeOnly
    }


    public enum MonthDayOption
    {
        MONTH_DAY,
        DAY_MONTH
    }


    private static long parseDateTimeUS(String s, DateTimeOption option)
    {
        return parseDateTimeEN(s, option, MonthDayOption.MONTH_DAY);
    }


    private static long parseDateTimeEN(String s, DateTimeOption option, MonthDayOption md)
    {
        Month month = null; // set if month is specified using name
        int year = -1;
        int mon = -1;
        int mday = -1;
        int hour = -1;
        int min = -1;
        int sec = -1;
        char c, si;
        int i = 0;
        int n, digits;
        int tzoffset = -1;
        TimeZone tz = null;
        char prevc = 0;
        boolean seenplusminus = false;
        boolean monthexpected = false;

        int limit = s.length();
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
                int start = i;
                while (i < limit && s.charAt(i) != ')')
                    i++;
                if (i == limit)
                    throw new ConversionException("Could not parse timezone specification: " + s.substring(start-1));

                // Parse the text inside parentheses if time zone hasn't been specified yet, otherwise ignore it. See #20932.
                if (null == tz && -1 == tzoffset)
                {
                    String spec = s.substring(start, i);
                    Object dp = resolveDatePart(spec);
                    if (dp instanceof TimeZone)
                        tz = (TimeZone) dp;
                    else if (dp instanceof TZ)
                        tzoffset = ((TZ) dp).tzoffset;
                    else
                        throw new ConversionException("Could not parse timezone specification: " + spec);
                }

                i++;
                continue;
            }

            if ('0' <= c && c <= '9')
            {
                n = c - '0';
                digits = 1;
                while (i < limit && '0' <= (c = s.charAt(i)) && c <= '9')
                {
                    digits++;
                    n = n * 10 + c - '0';
                    i++;
                }

                /* allow TZA before the year, so
                 * 'Wed Nov 05 21:49:11 GMT-0800 1997'
                 * works */

                /* uses of seenplusminus allow : in TZA, so Java
                 * no-timezone style of GMT+4:30 works
                 */
validNum:       {
                    if ((prevc == '+' || prevc == '-') && hour >= 0 /* && year>=0 */)
                    {
                        /* make ':' case below change tzoffset */
                        seenplusminus = true;

                        /* offset */
                        if (n < 24)
                            n = n * 60; /* EG. "GMT-3" */
                        else
                            n = n % 100 + n / 100 * 60; /* eg "GMT-0430" */
                        if (prevc == '-')
                            n = -n;
                        if ((tz != null && tz.getRawOffset() != 0) || (tzoffset != 0 && tzoffset != -1))
                            throw new ConversionException(s);
                        tzoffset = n;
                        tz = null;
                        break validNum;
                    }
                    if (digits > 3 || n >= 70 || ((prevc == '/' || prevc == '-' || prevc == '.') && mon >= 0 && mday >= 0 && year < 0))
                    {
                        if (year >= 0)
                            throw new ConversionException(s);
                        else if (c <= ' ' || c == ',' || c == '/' || c == '-' || c == '.' || i >= limit)
                        {
                            if (n >= 100 || digits > 3)
                                year = n;
                            else if (n > twoDigitCutoff)
                                year = n + defaultCentury;
                            else
                                year = n + defaultCentury + 100;
                        }
                        else
                            throw new ConversionException(s);
                        break validNum;
                    }
                    if (c == ':' || (hour < 0 && option == DateTimeOption.TimeOnly))
                    {
                        if (c == '/' || c == '.')
                            throw new ConversionException(s);
                        else if (hour < 0)
                            hour = n;
                        else if (min < 0)
                            min = n;
                        else
                            throw new ConversionException(s);
                        break validNum;
                    }
                    if (c == '/' || c == '-' || c == '.')
                    {
                        if (c == '/' && option == DateTimeOption.TimeOnly)
                            throw new ConversionException(s);
                        if (md == MonthDayOption.MONTH_DAY || year >= 0)
                        {
                            if (mon < 0)
                                mon = n - 1;
                            else if (mday < 0)
                                mday = n;
                            else
                                throw new ConversionException(s);
                        }
                        else
                        {
                            if (mday < 0)
                                mday = n;
                            else if (mon < 0)
                                mon = n - 1;
                            else
                                throw new ConversionException(s);
                        }
                        break validNum;
                    }
                    if (i < limit)
                    {
                        if (mday < 0 && -1 != "jfmasondJFMASOND".indexOf(c))
                        {
                            monthexpected = true;
                        }
                        else if (c > ' ' && -1 == ",-ZTaApP".indexOf(c))
                        {
                            throw new ConversionException(s);
                        }
                    }
                    if (seenplusminus && n < 60)
                    {  /* handle GMT-3:30 */
                        if (tzoffset < 0)
                            tzoffset -= n;
                        else
                            tzoffset += n;
                        break validNum;
                    }
                    if (hour >= 0 && min < 0)
                    {
                        min = n;
                        break validNum;
                    }
                    if (min >= 0 && sec < 0)
                    {
                        sec = n;
                        break validNum;
                    }
                    if (mday < 0)
                    {
                        mday = n;
                        break validNum;
                    }
                    else
                    {
                        throw new ConversionException(s);
                    }
                } // validNum: end of number handling
                prevc = 0;
            }
            else if (c == '/' || c == ':' || c == '+' || c == '-' || c == '.')
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
                Object dp = resolveDatePart(s.substring(st,i));
                if (null == dp)
                    throw new ConversionException(s);
                if (option != DateTimeOption.TimeOnly && monthexpected && !(dp instanceof Month))
                    throw new ConversionException(s);
                monthexpected = false;
                if (dp == ISO.t)
                {
                    if (hour >= 0 || min >= 0 || sec >= 0)
                        throw new ConversionException(s);
                }
                else if (dp == AMPM.am || dp == AMPM.pm)
                {
                    /*
                     * AM/PM. Count 12:30 AM as 00:30, 12:30 PM as
                     * 12:30, instead of blindly adding 12 if PM.
                     */
                    if (hour > 12 || hour < 0)
                    {
                        throw new ConversionException(s);
                    }
                    else if (dp == AMPM.am)
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
                else //noinspection StatementWithEmptyBody
                    if (dp instanceof Weekday)
                {
                    // ignore week days
                }
                else if (dp instanceof Month)
                {
                    // month
                    if (mon < 0)
                    {
                        month = (Month)dp;
                        mon = month.month;
                    }
                    else if (mday < 0 && month == null)
                    {
                        // handle 01/Jan/2001 case (strange I know, the customer is always right)
                        month = (Month)dp;
                        mday = mon+1;
                        mon = month.month;
                    }
                    else
                    {
                        throw new ConversionException(s);
                    }
                    // handle "01Jan2001" or "01 Jan 2001" pretend we're seeing 01/Jan/2001
                    if (i < limit && year < 0)
                        prevc = '/';
                }
                else if (dp instanceof TimeZone)
                {
                    tz = (TimeZone)dp;
                }
                else
                {
                    tzoffset = ((TZ)dp).tzoffset;
                }
            }
        }

        switch (option)
        {
            case DateOnly:
                if (hour >= 0 || min >= 0 || sec >= 0 || tzoffset >= 0)
                    throw new ConversionException(s);
                // fall through
            case DateTime:
                if (year < 0 || mon < 0 || mday < 0)
                    throw new ConversionException(s);
                break;
            case TimeOnly:
                if (year >= 0 || mon >= 0 || mday >= 0 || tzoffset >= 0)
                    throw new ConversionException(s);
                break;
        }

        if (sec < 0)
            sec = 0;
        if (min < 0)
            min = 0;
        if (hour < 0)
            hour = 0;

        if (option == DateTimeOption.TimeOnly)
        {
            if (hour >= 24 || min >= 60 || sec >= 60)
                throw new ConversionException(s);

            return 1000L * (hour * (60*60) + (min * 60) + sec);
        }
        
        //
        // This part is changed to work with Java
        //

        if (tzoffset != -1 && tz != null)
            throw new ConversionException("ambiguous timezone specification");

        if (tz == null)
        {
            if (tzoffset == -1)
                tz = _timezoneDefault;
            else
            {
                tz = tzCache.get(tzoffset);
                if (null == tz)
                {
                    char sign = tzoffset < 0 ? '-' : '+';
                    int mins = Math.abs(tzoffset);
                    int hr = mins / 60;
                    int mn = mins % 60;
                    String tzString = "GMT" + sign + (hr / 10) + (hr % 10) + (mn / 10) + (mn % 10);
                    tz = TimeZone.getTimeZone(tzString);
                    tzCache.put(tzoffset, tz);
                }
            }
        }

        try
        {
            Calendar cal = newCalendarStrict(tz, year, mon, mday, hour, min, sec);

            return cal.getTimeInMillis();
        }
        catch (IllegalArgumentException x)
        {
            throw new ConversionException(s);
        }
    }


    public static long parseISODateTime(String s)
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
        catch (Exception ignored) {}
        throw new ConversionException(s);
    }


    // These parsers aren't affected by the MDY/DMY setting
    private static long parseSpecialFormats(String s)
    {
        try
        {
            return parseISODateTime(s);
        }
        catch (Exception ignored) {}

        try
        {
            // java.util.Date.toString produces dates in the following format.  Try to
            // convert them here.  This is necessary to pass the DRT when running in a
            // non-US timezone:
            return parseDateTime(s, "EEE MMM dd HH:mm:ss zzz yyyy").getTime();
        }
        catch (Exception ignored) {}

        try
        {
            return parseXMLDate(s);
        }
        catch (IllegalArgumentException ignored) {}

        try
        {
            return parseYYYYMMDD(s);
        }
        catch (ParseException ignored) {}

        throw new ConversionException("Can't parse \"" + s + "\" into a date");
    }

    private static long parseXMLDate(String s)
    {
        if (s.contains(":") || s.contains("Z"))
        {
            return DatatypeConverter.parseDateTime(s).getTimeInMillis();
        }
        else
        {
            throw new IllegalArgumentException();
        }
    }

    // Parse using a specific pattern... used where strict parsing or non-standard pattern is required
    // Note: SimpleDateFormat is not thread-safe, so we create a new one for every parse.
    public static Date parseDateTime(String s, String pattern) throws ParseException
    {
        if (null == s)
            throw new ParseException("Date string is empty", 0);

        return new SimpleDateFormat(pattern).parse(s);
    }


    // Lenient parsing using a variety of standard formats
    @Deprecated  // Use version that takes a Container instead
    public static long parseDateTime(String s)
    {
        //Issue 30004: Remote servers cannot use the database to lookup MonthDayOption, so default to MONTH_DAY
        boolean isNotRunningOnWebServer = PipelineJobService.get() == null ||
                PipelineJobService.get().getLocationType() != PipelineJobService.LocationType.WebServer;
        if (isNotRunningOnWebServer)
        {
            return parseDateTime(s, MonthDayOption.MONTH_DAY);
        }

        return parseDateTime(ContainerManager.getRoot(), s);
    }


    // Lenient parsing using a variety of standard formats
    public static long parseDateTime(Container c, String s)
    {
        String displayFormat = getDateTimeFormatString(c);  // TODO: Pass this into parseDate() as a bonus format?
        MonthDayOption monthDayOption = LookAndFeelProperties.getInstance(c).getDateParsingMode().getDayMonth();

        return parseDateTime(s, monthDayOption);
    }


    private static long parseDateTime(String s, MonthDayOption md)
    {
        int ms = 0;
        try
        {
            // strip off trailing decimal :00:00.000
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

            long time = parseDateTimeEN(s, DateTimeOption.DateTime, md);
            return time + ms;
        }
        catch (ConversionException ignored) {}

        return parseSpecialFormats(s) + ms;
    }


    // Lenient parsing using a variety of standard formats
    @Deprecated  // Use version that takes a Container instead
    public static long parseDate(String s)
    {
        return parseDate(ContainerManager.getRoot(), s);
    }


    // Lenient parsing using a variety of standard formats
    public static long parseDate(Container c, String s)
    {
        String displayFormat = getDateFormatString(c);  // TODO: Pass this into parseDate() as a bonus format
        MonthDayOption monthDayOption = LookAndFeelProperties.getInstance(c).getDateParsingMode().getDayMonth();

        return parseDate(s, monthDayOption);
    }


    private static long parseDate(String s, MonthDayOption md)
    {
        try
        {
            // quick check for JDBC/ISO date
            if (s.length() == 10 && s.charAt(4) == '-' && s.charAt(7) == '-')
                return parseISODateTime(s);
        }
        catch (ConversionException ignored) {}

        try
        {
            return parseDateTimeEN(s, DateTimeOption.DateOnly, md);
        }
        catch (ConversionException e)
        {
            try
            {
                return parseYYYYMMDD(s);
            }
            catch (ParseException ignored) {}

            try
            {
                // One final format to try - handles "2-3-01", "02-03-01", "02-03-2001", etc
                DateFormat format = new SimpleDateFormat("M-d-yy");
                format.setLenient(false);
                return format.parse(s).getTime();
            }
            catch (ParseException pe)
            {
                try
                {
                    return parseXMLDate(s);
                }
                catch (IllegalArgumentException ignored)
                {
                }
            }

            throw e;
        }
    }


    private static long parseYYYYMMDD(String s) throws ParseException
    {
        if (s.length() == 8)
        {
            DateFormat format = new SimpleDateFormat("yyyyMMdd");
            format.setLenient(false);
            Date date = format.parse(s);
            Calendar cal = new GregorianCalendar();
            cal.setTime(date);
            int year = cal.get(Calendar.YEAR);
            if (year >= 1800 && year <= 2200)
            {
                return date.getTime();
            }
            throw new ParseException("Year out of range from 1800-2200: " + year, 0);
        }
        throw new ParseException("Not a date: " + s, 0);
    }


    public static long parseTime(String s)
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
        long time = parseDateTimeUS(s, DateTimeOption.TimeOnly);
        return time + ms;
    }


    public static String getStandardDateFormatString()
    {
        return ISO_DATE_FORMAT_STRING;
    }


    public static String getStandardDateTimeFormatString()
    {
        return ISO_DATE_TIME_FORMAT_STRING;
    }


    public static String getJsonDateTimeFormatString()
    {
        //return "d MMM yyyy HH:mm:ss";
        // MAB: I think this is better, shouldn't use text month in json format
        // strangely yyyy/MM/dd parses in more browsers than yyyy-MM-dd
        return "yyyy/MM/dd HH:mm:ss";
    }


    // TODO: Combine these format ISO methods with toISO() and nowISO()?

    /**
     * Format current date using ISO 8601 pattern. This is appropriate only for persisting dates in machine-readable
     * form, for example, for export or in filenames. Most callers should use formatDate(Container c) instead.
     */
    public static String formatDateISO8601()
    {
        return formatDateISO8601(new Date());
    }


    /**
     * Format date using ISO 8601 pattern. This is appropriate only for persisting dates in machine-readable form,
     * for example, for export or in filenames. Most callers should use formatDate(Container c, Date d) instead.
     */
    public static String formatDateISO8601(Date date)
    {
        return formatDateTime(date, ISO_DATE_FORMAT_STRING);
    }


    /**
     * Format date and time using ISO 8601 pattern. This is appropriate only for persisting dates in machine-readable
     * form, for example, for export or in filenames. Most callers should use formatDateTime(Container c, Date d) instead.
     */
    public static String formatDateTimeISO8601(Date date)
    {
        return formatDateTime(date, ISO_DATE_TIME_FORMAT_STRING);
    }


    /**
     * Format current date using folder-specified default pattern
     *
     * Warning: Return value is unsafe and must be HTML filtered, if rendered to an HTML page
     */
    public static String formatDate(Container c)
    {
        return formatDate(c, new Date());
    }


    /**
     * Format specific date using folder-specified default pattern
     *
     * Warning: Return value is unsafe and must be HTML filtered, if rendered to an HTML page
     */
    public static String formatDate(Container c, Date date)
    {
        return formatDateTime(date, getDateFormatString(c));
    }


    /**
     * Format current date & time using folder-specified default date/time pattern
     *
     * Warning: Return value is unsafe and must be HTML filtered, if rendered to an HTML page
     */
    public static String formatDateTime(Container c)
    {
        return formatDateTime(c, new Date());
    }


    /**
     * Format date & time using folder-specified default date pattern plus standard time format
     *
     * Warning: Return value is unsafe and must be HTML filtered, if rendered to an HTML page
     */
    public static String formatDateTime(Container c, Date date)
    {
        return formatDateTime(date, getDateTimeFormatString(c));
    }


    /**
     * Format date, inferring the appropriate folder-specified default pattern (date vs. date-time) based on class of date
     *
     * Warning: Return value is unsafe and must be HTML filtered, if rendered to an HTML page
     */
    public static String formatDateInfer(Container c, Date date)
    {
        return formatDateTime(date, date instanceof java.sql.Date ? getDateFormatString(c) : getDateTimeFormatString(c));
    }


    /**
     * Format date & time using specified pattern
     * Note: Unlike SimpleDateFormat, this implementation is thread-safe and reuses formatters
     */
    public static String formatDateTime(Date date, String pattern)
    {
        if (null == date)
            return null;
        else
            return FastDateFormat.getInstance(pattern).format(date);
    }


    /**
     * Get the default date format string to use in this Container
     *
     * Note: The display format is specified by an admin; it could contain any characters, hence, it may not be safe.
     * Any value formatted by this pattern must be HTML filtered, if rendered to an HTML page.
     */
    public static String getDateFormatString(Container c)
    {
        return FolderSettingsCache.getDefaultDateFormat(c);
    }


    /**
     * Get the default date/time format string set in this Container (or one of its parents)
     *
     * Note: The display format is specified by an admin; it could contain any characters, hence, it may not be safe.
     * Any value formatted by this pattern must be HTML filtered, if rendered to an HTML page.
     */
    public static String getDateTimeFormatString(Container c)
    {
        return FolderSettingsCache.getDefaultDateTimeFormat(c);
    }


    public static String getTimeFormatString(Container c)
    {
        return LONG_TIME_FORMAT_STRING;
    }


    /**
     * Test a date format string to determine if it matches one of LabKey's special named date formats (Date, DateTime, Time)
     * @param dateFormat Format string to test
     * @return True if the dateFormat matches one of LabKey's special named date formats, otherwise False
     */
    public static boolean isSpecialNamedFormat(String dateFormat)
    {
        return "Date".equals(dateFormat) || "DateTime".equals(dateFormat) || "Time".equals(dateFormat);
    }


    private static final FastDateFormat jsonDateFormat = FastDateFormat.getInstance(getJsonDateTimeFormatString());

    public static String formatJsonDateTime(Date date)
    {
        return jsonDateFormat.format(date);
    }


    private static class _duration
    {
        int year = -1;
        int month = -1;
        int day = -1;
        int hour = -1;
        int min = -1;
        double sec = -1;
    }

    public static boolean isSignedDuration(@NotNull String durationCandidate)
    {
        try{
            if(durationCandidate.isEmpty() || !(durationCandidate.startsWith("+") || durationCandidate.startsWith("-")))
            {
                return false;
            }
            _parseDuration(durationCandidate.substring(1));
            return true;
        }
        catch (ConversionException e)
        {
            return false;
        }
    }

    public static long applySignedDuration(long time, String duration)
    {
        if(duration.startsWith("+"))
        {
             return addDuration(time, duration.substring(1));
        }
        if(duration.startsWith("-"))
        {
            return subtractDuration(time,duration.substring(1));
        }
        throw new IllegalArgumentException("The duration provided is not valid: " + duration);
    }
    public static _duration _parseDuration(String s)
    {
        boolean period = false;
        boolean monthInPeriod = false;
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
                period = true;
                startField = i+1;
                break;
            case 'Y': case 'y':
                if (year != -1 || month != -1 || day != -1)
                    break Parse;
                period = true;
                year = Integer.parseInt(s.substring(startField,i));
                startField = i+1;
                break;
            case 'M': case 'm':
                if (!time && month == -1)
                {
                    month = Integer.parseInt(s.substring(startField,i));
                    monthInPeriod = period;
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
        if ((month != -1 && min == -1) && !monthInPeriod && !time)
        {
            assert -1 == day && -1 == hour;
            min = month;
            month = -1;
        }

        _duration d = new _duration();
        d.year = Math.max(0,year);
        d.month = Math.max(0,month);
        d.day = Math.max(0,day);
        d.hour = Math.max(0,hour);
        d.min = Math.max(0,min);
        d.sec = Math.max(0,sec);
        return d;
    }


    public static long parseDuration(String s)
    {
        _duration d = _parseDuration(s);

        if (d.year != 0 || d.month != 0)
            throw new ConversionException("Year and month not supported: " + s);

        assert d.day >= 0 || d.day == -1;
        assert d.hour >= 0 || d.hour == -1;
        assert d.min >= 0 || d.min == -1;
        assert d.sec >= 0 || d.sec == -1;
        return  makeDuration(d.day, d.hour, d.min, d.sec);
    }


    /** handles year, and month **/
    private static long _addDuration(long start, String s, int sign)
    {
        _duration d = _parseDuration(s);

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(start);

        if (d.year > 0)
            calendar.add(Calendar.YEAR, d.year*sign);
        if (d.month > 0)
            calendar.add(Calendar.MONTH, d.month*sign);
        if (d.day > 0)
            calendar.add(Calendar.DAY_OF_MONTH, d.day*sign);
        if (d.hour > 0)
            calendar.add(Calendar.HOUR_OF_DAY, d.hour*sign);
        if (d.min > 0)
            calendar.add(Calendar.MINUTE, d.min*sign);
        if (d.sec > 0)
            calendar.add(Calendar.MILLISECOND, (int)(1000*d.sec*sign));

        return calendar.getTimeInMillis();
    }


    private static long makeDuration(int day, int hour, int min, double sec)
    {
        return  day * DateUtils.MILLIS_PER_DAY +
                hour * DateUtils.MILLIS_PER_HOUR +
                min * DateUtils.MILLIS_PER_MINUTE +
                (int)(sec * DateUtils.MILLIS_PER_SECOND);
    }


    public static long addDuration(long d, String s)
    {
        return _addDuration(d, s, 1);
    }


    public static long subtractDuration(long d, String s)
    {
        return _addDuration(d, s, -1);
    }


    // how ISO8601 do we want to be (v. readable)
    public static String formatDuration(long duration)
    {
        if (duration==0) //  || duration==Long.MIN_VALUE)
            return "0s";
        if (duration < 0)
            return duration==Long.MIN_VALUE ? "-106751991167d7h12m55.808s" : "-" + formatDuration(-duration);

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


    public static String getSimpleDateFormatDocumentationURL()
    {
        return HelpTopic.getJDKJavaDocLink(SimpleDateFormat.class);
    }


    public static Pair<Date, Date> splitDate(@Nullable Date fullDate)
    {
        if (null == fullDate)
            return new Pair<>(null, null);
        int year = fullDate.getYear();
        int month = fullDate.getMonth();
        int date = fullDate.getDate();
        int hour = fullDate.getHours();
        int mins = fullDate.getMinutes();
        int secs = fullDate.getSeconds();
        Date onlyTime = new Date(70, 0, 1, hour, mins, secs);
        Date onlyDate = new Date(year, month, date);
        return new Pair<>(onlyDate, onlyTime);
    }

    @Nullable
    public static Date combineDateTime(@Nullable Date date, @Nullable Date time)
    {
        if (null == time)
            return date;
        if (null == date)
            return time;
        Date newDate = (Date)date.clone();
        newDate.setHours(time.getHours());
        newDate.setMinutes(time.getMinutes());
        newDate.setSeconds(time.getSeconds());
        return newDate;
    }

    @Nullable
    public static Date getDateOnly(@Nullable Date fullDate)
    {
        if (null == fullDate)
            return null;

        int year = fullDate.getYear();
        int month = fullDate.getMonth();
        int date = fullDate.getDate();

        return new Date(year, month, date);
    }

    private final static Date ZERO_TIME = new Date(70, 0, 1, 0, 0, 0);

    @Nullable
    public static Date getTimeOnly(@Nullable Date fullDate)
    {
        if (null == fullDate)
            return null;

        try
        {
            int hour = fullDate.getHours();
            int mins = fullDate.getMinutes();
            int secs = fullDate.getSeconds();

            return new Date(70, 0, 1, hour, mins, secs);
        }
        catch (IllegalArgumentException ignore)
        {
            return ZERO_TIME;
        }
    }

    public static class TestCase extends Assert
    {
        private long parseDate(String s)
        {
            return DateUtil.parseDate(ContainerManager.getRoot(), s);
        }

        private long parseDateTime(String s)
        {
            return DateUtil.parseDateTime(ContainerManager.getRoot(), s);
        }

        void assertIllegalDate(String s)
        {
            try
            {
                parseDate(s);
                fail("Not a legal date: " + s);
            }
            catch (ConversionException x)
            {
            }
        }

        void assertIllegalDateTime(String s)
        {
            try
            {
                parseDateTime(s);
                fail("Not a legal datetime: " + s);
            }
            catch (ConversionException x)
            {
            }
        }

        void assertIllegalTime(String s)
        {
            try
            {
                parseTime(s);
                fail("Not a legal datetime: " + s);
            }
            catch (ConversionException x)
            {
            }
        }


        @Test
        public void testDateTimeUS() throws ParseException
        {
            long datetimeExpected = java.sql.Timestamp.valueOf("2001-02-03 04:05:06").getTime();
            long dateExpected = java.sql.Date.valueOf("2001-02-03").getTime();

            // DateTime with time
            Date dt = new Date(datetimeExpected);
            assertEquals(datetimeExpected, parseDateTime(dt.toString()));
            assertEquals(datetimeExpected, parseDateTime(dt.toGMTString()));
            assertEquals(datetimeExpected, parseDateTime(dt.toLocaleString()));
            assertEquals(datetimeExpected, parseDateTime(ConvertUtils.convert(dt)));
            assertEquals(datetimeExpected, parseDateTime("2001-02-03 04:05:06"));
            assertEquals(datetimeExpected, parseDateTime("2001-02-03 04:05:06.0"));
            assertEquals(datetimeExpected, parseDateTime("2001-02-03T04:05:06"));
            assertEquals(datetimeExpected, parseDateTime("03 feb 2001 04:05:06"));
            assertEquals(datetimeExpected, parseDateTime("03 feb 2001 04:05:06am"));
            assertEquals(datetimeExpected, parseDateTime("03 feb 2001 04:05:06 am"));
            assertEquals(datetimeExpected-6000, parseDateTime("03 feb 2001 04:05am"));
            assertEquals(datetimeExpected-6000, parseDateTime("03 feb 2001 04:05 am"));
            assertEquals(datetimeExpected, parseDateTime("03-FEB-2001-04:05:06")); // FCS dates

            // illegal
            assertIllegalDateTime("2");
            assertIllegalDateTime("2/3");

            // DateTime without time
            Date d = new Date(dateExpected);
            assertEquals(dateExpected, parseDateTime(d.toString()));
            assertEquals(dateExpected, parseDateTime(d.toGMTString()));
            assertEquals(dateExpected, parseDateTime(d.toLocaleString()));
            assertEquals(dateExpected, parseDateTime(ConvertUtils.convert(d)));
            assertEquals(dateExpected, parseDateTime("2001-02-03"));
            assertEquals(dateExpected, parseDateTime("2001-2-03"));
            assertEquals(dateExpected, parseDateTime("3-Feb-01"));
            assertEquals(dateExpected, parseDateTime("3Feb01"));
            assertEquals(dateExpected, parseDateTime("3Feb2001"));
            assertEquals(dateExpected, parseDateTime("03Feb01"));
            assertEquals(dateExpected, parseDateTime("03Feb2001"));
            assertEquals(dateExpected, parseDateTime("3 Feb 01"));
            assertEquals(dateExpected, parseDateTime("3 Feb 2001"));
            assertEquals(dateExpected, parseDateTime("February 3, 2001"));
            assertEquals(dateExpected, parseDateTime("20010203"));

            // Only recognize years in the "recent" past/future with this all-digit format
            assertIllegalDateTime("17000101");
            assertIllegalDateTime("23000101");

            // Test XML date format
            assertXmlDateMatches(parseDate("2001-02-03+01:00"));
            assertXmlDateMatches(parseDate("2001-02-03Z"));
            assertIllegalDateTime("115468001");

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
            assertIllegalDateTime("Jan/Feb/2001");

            // Z testing
            SimpleDateFormat zo = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
            SimpleDateFormat lo = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            SimpleDateFormat ut = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            ut.setTimeZone(TimeZone.getTimeZone("GMT"));

            long datetimeUTC = zo.parse("2001-02-03 04:05:06 GMT").getTime();
            long datetimeLocal = lo.parse("2001-02-03 04:05:06").getTime();
            assertEquals(zo.parse("2001-02-03 04:05:06 GMT"), ut.parse("2001-02-03 04:05:06"));
            long utcOffset = TimeZone.getDefault().getOffset(datetimeUTC);
            assertEquals(datetimeLocal + utcOffset, datetimeUTC);
            assertEquals(datetimeUTC, parseDateTime("Sat Feb 03 04:05:06 GMT-0000 2001"));

            // Recent JavaScript display formats, see #20932
            assertEquals(datetimeUTC + TimeUnit.HOURS.toMillis(7), parseDateTime("Sat Feb 03 2001 04:05:06 GMT-0700"));
            assertEquals(datetimeUTC + TimeUnit.HOURS.toMillis(7), parseDateTime("Sat Feb 03 2001 04:05:06 GMT-0700 (PDT)"));
            assertEquals(datetimeUTC + TimeUnit.HOURS.toMillis(7), parseDateTime("Sat Feb 03 2001 04:05:06 GMT-0700 (Pacific Daylight Time)"));

            assertEquals(datetimeUTC + TimeUnit.HOURS.toMillis(1), parseDateTime("Sat Feb 03 04:05:06 GMT-1 2001"));
            assertEquals(datetimeUTC + TimeUnit.HOURS.toMillis(1), parseDateTime("Sat Feb 03 04:05:06 GMT-0100 2001"));
            assertEquals(datetimeUTC - TimeUnit.MINUTES.toMillis(270), parseDateTime("Sat Feb 03 04:05:06 GMT+0430 2001"));

            // See #21485
            assertEquals(parseDateTime("2014-07-31 15:00 PDT"), parseDateTime("Fri Aug 01 00:00:00 CEST 2014"));

            // check that parseDateTimeUS handles ISO
            assertEquals(datetimeLocal, parseDateTimeUS("2001-02-03 04:05:06", DateTimeOption.DateTime));
            assertEquals(datetimeLocal, parseDateTimeUS("2001-02-03T04:05:06", DateTimeOption.DateTime));
            assertEquals(datetimeUTC, parseDateTimeUS("2001-02-03 04:05:06Z", DateTimeOption.DateTime));
            assertEquals(datetimeUTC, parseDateTimeUS("2001-02-03T04:05:06Z", DateTimeOption.DateTime));

            assertIllegalDateTime("20131113_Guide Set plate 1.xls");
        }


        @Test
        public void testDateTimeIntl()
        {
            DateParsingMode mode = LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getDateParsingMode();
            long datetimeExpected = java.sql.Timestamp.valueOf(DateParsingMode.US == mode ? "2001-02-03 04:05:06" : "2001-03-02 04:05:06").getTime();
            long dateExpected = java.sql.Date.valueOf(DateParsingMode.US == mode ? "2001-02-03" : "2001-03-02").getTime();

            assertEquals(datetimeExpected, parseDateTime("2/3/01 4:05:06"));
            assertEquals(datetimeExpected, parseDateTime("2/3/2001 4:05:06"));
            assertEquals(datetimeExpected, parseDateTime("2/3/2001 4:05:06.000"));
            assertEquals(datetimeExpected, parseDateTime("2-03-2001 4:05:06"));
            assertEquals(dateExpected, parseDateTime("2/3/01"));
            assertEquals(dateExpected, parseDateTime("2/3/2001"));
        }


        @Test
        public void testDateTimeGB() throws ParseException
        {
            long datetimeExpected = java.sql.Timestamp.valueOf("2001-02-03 04:05:06").getTime();
            long dateExpected = java.sql.Date.valueOf("2001-02-03").getTime();

            // DateTime with time
            Date dt = new Date(datetimeExpected);
            assertEquals(datetimeExpected, DateUtil.parseDateTime(dt.toString(), MonthDayOption.DAY_MONTH));
            assertEquals(datetimeExpected, DateUtil.parseDateTime(dt.toGMTString(), MonthDayOption.DAY_MONTH));
            assertEquals(datetimeExpected, DateUtil.parseDateTime(dt.toLocaleString(), MonthDayOption.DAY_MONTH));
            assertEquals(datetimeExpected, DateUtil.parseDateTime(ConvertUtils.convert(dt), MonthDayOption.DAY_MONTH));
            assertEquals(datetimeExpected, DateUtil.parseDateTime("2001-02-03 04:05:06", MonthDayOption.DAY_MONTH));
            assertEquals(datetimeExpected, DateUtil.parseDateTime("2001-02-03 04:05:06.0", MonthDayOption.DAY_MONTH));
            assertEquals(datetimeExpected, DateUtil.parseDateTime("2001-02-03T04:05:06", MonthDayOption.DAY_MONTH));
            assertEquals(datetimeExpected, DateUtil.parseDateTime("3/2/01 4:05:06", MonthDayOption.DAY_MONTH));
            assertEquals(datetimeExpected, DateUtil.parseDateTime("3/2/2001 4:05:06", MonthDayOption.DAY_MONTH));
            assertEquals(datetimeExpected, DateUtil.parseDateTime("3/2/2001 4:05:06.000", MonthDayOption.DAY_MONTH));
            assertEquals(datetimeExpected, DateUtil.parseDateTime("03 feb 2001 04:05:06", MonthDayOption.DAY_MONTH));
            assertEquals(datetimeExpected, DateUtil.parseDateTime("03 feb 2001 04:05:06am", MonthDayOption.DAY_MONTH));
            assertEquals(datetimeExpected, DateUtil.parseDateTime("03 feb 2001 04:05:06 am", MonthDayOption.DAY_MONTH));
            assertEquals(datetimeExpected-6000, DateUtil.parseDateTime("03 feb 2001 04:05am", MonthDayOption.DAY_MONTH));
            assertEquals(datetimeExpected-6000, DateUtil.parseDateTime("03 feb 2001 04:05 am", MonthDayOption.DAY_MONTH));
            assertEquals(datetimeExpected, DateUtil.parseDateTime("03-FEB-2001-04:05:06", MonthDayOption.DAY_MONTH)); // FCS dates
            assertEquals(datetimeExpected, DateUtil.parseDateTime("3-02-2001 4:05:06", MonthDayOption.DAY_MONTH));

            // illegal
            assertIllegalDateTime("2");
            assertIllegalDateTime("2/3");

            // DateTime without time
            Date d = new Date(dateExpected);
            assertEquals(dateExpected, parseDateTime(d.toString()));
            assertEquals(dateExpected, parseDateTime(d.toGMTString()));
            assertEquals(dateExpected, parseDateTime(d.toLocaleString()));
            assertEquals(dateExpected, parseDateTime(ConvertUtils.convert(d)));
            assertEquals(dateExpected, DateUtil.parseDateTime("2001-02-03", MonthDayOption.DAY_MONTH));
            assertEquals(dateExpected, DateUtil.parseDateTime("2001-2-03", MonthDayOption.DAY_MONTH));
            assertEquals(dateExpected, DateUtil.parseDateTime("3/2/01", MonthDayOption.DAY_MONTH));
            assertEquals(dateExpected, DateUtil.parseDateTime("3/2/2001", MonthDayOption.DAY_MONTH));
            assertEquals(dateExpected, DateUtil.parseDateTime("3-Feb-01", MonthDayOption.DAY_MONTH));
            assertEquals(dateExpected, DateUtil.parseDateTime("3Feb01", MonthDayOption.DAY_MONTH));
            assertEquals(dateExpected, DateUtil.parseDateTime("3Feb2001", MonthDayOption.DAY_MONTH));
            assertEquals(dateExpected, DateUtil.parseDateTime("03Feb01", MonthDayOption.DAY_MONTH));
            assertEquals(dateExpected, DateUtil.parseDateTime("03Feb2001", MonthDayOption.DAY_MONTH));
            assertEquals(dateExpected, DateUtil.parseDateTime("3 Feb 01", MonthDayOption.DAY_MONTH));
            assertEquals(dateExpected, DateUtil.parseDateTime("3 Feb 2001", MonthDayOption.DAY_MONTH));
            assertEquals(dateExpected, DateUtil.parseDateTime("February 3, 2001", MonthDayOption.DAY_MONTH));
            assertEquals(dateExpected, DateUtil.parseDateTime("20010203", MonthDayOption.DAY_MONTH));

            // Only recognize years in the "recent" past/future with this all-digit format
            assertIllegalDateTime("17000101");
            assertIllegalDateTime("23000101");

            // Test XML date format
            assertXmlDateMatches(DateUtil.parseDate("2001-02-03+01:00", MonthDayOption.DAY_MONTH));
            assertXmlDateMatches(DateUtil.parseDate("2001-02-03Z", MonthDayOption.DAY_MONTH));
            assertIllegalDateTime("115468001");

            // some zero testing
            datetimeExpected = java.sql.Timestamp.valueOf("2001-02-03 00:00:00.000").getTime();
            assertEquals(datetimeExpected, DateUtil.parseDateTime("2001-02-03 00:00:00.000", MonthDayOption.DAY_MONTH));
            assertEquals(datetimeExpected, DateUtil.parseDateTime("2001-02-03 00:00:00", MonthDayOption.DAY_MONTH));
            assertEquals(datetimeExpected, DateUtil.parseDateTime("2001-02-03 00:00", MonthDayOption.DAY_MONTH));
            assertEquals(datetimeExpected, DateUtil.parseDateTime("2001-02-03", MonthDayOption.DAY_MONTH));

            // dd/mmm/yy testing
            assertEquals(dateExpected, DateUtil.parseDateTime("3/Feb/01", MonthDayOption.DAY_MONTH));
            assertEquals(dateExpected, DateUtil.parseDateTime("3/FEB/01", MonthDayOption.DAY_MONTH));
            assertEquals(dateExpected, DateUtil.parseDateTime("3/FeB/2001", MonthDayOption.DAY_MONTH));
            assertEquals(dateExpected, DateUtil.parseDateTime("03/feb/2001", MonthDayOption.DAY_MONTH));
            assertEquals(dateExpected, DateUtil.parseDateTime("03/FEB/2001", MonthDayOption.DAY_MONTH));
            assertIllegalDateTime("Jan/Feb/2001");

            // Z testing
            SimpleDateFormat zo = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
            SimpleDateFormat lo = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            SimpleDateFormat ut = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            ut.setTimeZone(TimeZone.getTimeZone("GMT"));

            long datetimeUTC = zo.parse("2001-02-03 04:05:06 GMT").getTime();
            long datetimeLocal = lo.parse("2001-02-03 04:05:06").getTime();
            assertEquals(zo.parse("2001-02-03 04:05:06 GMT"), ut.parse("2001-02-03 04:05:06"));
            long utcOffset = TimeZone.getDefault().getOffset(datetimeUTC);
            assertEquals(datetimeLocal + utcOffset, datetimeUTC);
            assertEquals(datetimeUTC, DateUtil.parseDateTime("Sat Feb 03 04:05:06 GMT-0000 2001", MonthDayOption.DAY_MONTH));
            assertEquals(datetimeUTC+TimeUnit.HOURS.toMillis(1), DateUtil.parseDateTime("Sat Feb 03 04:05:06 GMT-1 2001", MonthDayOption.DAY_MONTH));
            assertEquals(datetimeUTC+TimeUnit.HOURS.toMillis(1), DateUtil.parseDateTime("Sat Feb 03 04:05:06 GMT-0100 2001", MonthDayOption.DAY_MONTH));
            assertEquals(datetimeUTC-TimeUnit.MINUTES.toMillis(270), DateUtil.parseDateTime("Sat Feb 03 04:05:06 GMT+0430 2001", MonthDayOption.DAY_MONTH));

            // check that parseDateTimeUS handles ISO
            assertEquals(datetimeLocal, parseDateTimeEN("2001-02-03 04:05:06", DateTimeOption.DateTime, MonthDayOption.DAY_MONTH));
            assertEquals(datetimeLocal, parseDateTimeEN("2001-02-03T04:05:06", DateTimeOption.DateTime, MonthDayOption.DAY_MONTH));
            assertEquals(datetimeUTC, parseDateTimeEN("2001-02-03 04:05:06Z", DateTimeOption.DateTime, MonthDayOption.DAY_MONTH));
            assertEquals(datetimeUTC, parseDateTimeEN("2001-02-03T04:05:06Z", DateTimeOption.DateTime, MonthDayOption.DAY_MONTH));
        }


        @Test
        public void testDateTimeCH() throws ParseException
        {
            long datetimeExpected = java.sql.Timestamp.valueOf("2001-02-03 04:05:06").getTime();
            long dateExpected = java.sql.Date.valueOf("2001-02-03").getTime();

            // DateTime with time
            assertEquals(datetimeExpected, DateUtil.parseDateTime("3.2.01 4:05:06", MonthDayOption.DAY_MONTH));
            assertEquals(datetimeExpected, DateUtil.parseDateTime("3.2.2001 4:05:06", MonthDayOption.DAY_MONTH));
            assertEquals(datetimeExpected, DateUtil.parseDateTime("3.2.2001 4:05:06.000", MonthDayOption.DAY_MONTH));

            // DateTime with time
            long offset = (long)_timezoneDefault.getRawOffset() - (long)TimeZone.getTimeZone("CET").getRawOffset();
            assertEquals(datetimeExpected+offset, DateUtil.parseDateTime("3.2.2001 4:05:06 (CET)", MonthDayOption.DAY_MONTH));
            assertEquals(datetimeExpected+offset, DateUtil.parseDateTime("3.2.2001 - 4:05:06 (CET)", MonthDayOption.DAY_MONTH));
            assertEquals(datetimeExpected+offset, DateUtil.parseDateTime("3.2.2001 4:05:06 (Europe/Paris)", MonthDayOption.DAY_MONTH));

            // DateTime without time
            assertEquals(dateExpected, DateUtil.parseDateTime("3.2.01", MonthDayOption.DAY_MONTH));
            assertEquals(dateExpected, DateUtil.parseDateTime("3.2.2001", MonthDayOption.DAY_MONTH));
        }


        private void assertXmlDateMatches(long millis)
        {
            Calendar parsedXml = new GregorianCalendar();
            parsedXml.setTimeInMillis(millis);
            assertEquals(2001, parsedXml.get(Calendar.YEAR));
            assertEquals(Calendar.FEBRUARY, parsedXml.get(Calendar.MONTH));
            // Depending on your local time zone, you will get different days
            assertTrue(parsedXml.get(Calendar.DAY_OF_MONTH) == 3 || parsedXml.get(Calendar.DAY_OF_MONTH) == 2);
        }


        @Test
        public void testDate()
        {
            long dateExpected = java.sql.Date.valueOf("2001-02-03").getTime();

            // Date
            assertEquals(dateExpected, parseDateTime("2001-02-03"));
            assertEquals(dateExpected, parseDateTime("2001-2-03"));

            assertEquals(dateExpected, parseDate("3-Feb-01"));
            assertEquals(dateExpected, parseDate("3Feb01"));
            assertEquals(dateExpected, parseDate("3Feb2001"));
            assertEquals(dateExpected, parseDate("03Feb01"));
            assertEquals(dateExpected, parseDate("03Feb2001"));
            assertEquals(dateExpected, parseDate("3 Feb 01"));
            assertEquals(dateExpected, parseDate("3 Feb 2001"));
            assertEquals(dateExpected, parseDate("Feb 03 2001"));
            assertEquals(dateExpected, parseDate("February 3, 2001"));
            assertEquals(dateExpected, parseDate("20010203"));

            // Test XML date format
            assertXmlDateMatches(parseDate("2001-02-03+01:00"));
            assertXmlDateMatches(parseDate("2001-02-03Z"));
            assertIllegalDateTime("115468001");

            // Only recognize years in the "recent" past/future with this all-digit format
            assertIllegalDate("17000101");
            assertIllegalDate("23000101");

            assertIllegalDate("2");
            assertIllegalDate("2/3");
            assertIllegalDate("Feb/Mar/2001");
            assertIllegalDate("2/3/2001 0:00:00");
            assertIllegalDate("2/3/2001 12:00am");
            assertIllegalDate("2/3/2001 12:00pm");
            assertIllegalDate("2/30/2001 12:00pm");
            assertIllegalDate("30/2/2001 12:00pm");
            assertIllegalDate("9/30/2008 45:41:77");  // #19541
        }


        @Test  // Test parsing of dates whose interpretation changes based on US vs. non-US setting
        public void testDateIntl()
        {
            DateParsingMode mode = LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getDateParsingMode();
            long dateExpected = java.sql.Date.valueOf(DateParsingMode.US == mode ? "2001-02-03" : "2001-03-02").getTime();

            assertEquals(dateExpected, parseDate("2/3/01"));
            assertEquals(dateExpected, parseDate("2-3-01"));
            assertEquals(dateExpected, parseDate("2-3-2001"));
            assertEquals(dateExpected, parseDate("2-03-2001"));
            assertEquals(dateExpected, parseDate("02-3-2001"));
            assertEquals(dateExpected, parseDate("2/3/2001"));
            assertEquals(dateExpected, parseDate("02/03/01"));
            assertEquals(dateExpected, parseDate("02-03-01"));
            assertEquals(dateExpected, parseDate("02/03/2001"));
            assertEquals(dateExpected, parseDate("02-03-2001"));

            for (String illegalFormat : mode.getIllegalFormats())
                assertIllegalDate(illegalFormat);
        }


        @Test
        public void testTime()
        {
            long hrs12 = TimeUnit.HOURS.toMillis(12);
            long timeSecExpected = TimeUnit.HOURS.toMillis(4) + TimeUnit.MINUTES.toMillis(5) + TimeUnit.SECONDS.toMillis(6);
            long timeMinExpected = TimeUnit.HOURS.toMillis(4) + TimeUnit.MINUTES.toMillis(5);
            long timeHrExpected = TimeUnit.HOURS.toMillis(4);
            assertEquals(timeHrExpected, parseTime("4"));
            assertEquals(timeHrExpected, parseTime("4 am"));
            assertEquals(timeHrExpected, parseTime("4AM"));
            assertEquals(timeHrExpected + hrs12, parseTime("4pm"));
            assertEquals(timeHrExpected + hrs12, parseTime("16"));
            assertEquals(timeHrExpected + hrs12, parseTime("16:00:00"));
            assertEquals(timeMinExpected, parseTime("4:05"));
            assertEquals(timeSecExpected, parseTime("4:05:06"));
            assertEquals(timeSecExpected, parseTime("4:05:06 am"));
            assertEquals(timeSecExpected, parseTime("4:05:06AM"));
            assertEquals(timeSecExpected, parseTime("4:05:06.0"));
            assertEquals(timeSecExpected, parseTime("4:05:06.00"));
            assertEquals(timeSecExpected, parseTime("4:05:06.000"));
            assertEquals(timeSecExpected+7, parseTime("4:05:06.007"));
            assertEquals(timeSecExpected+70, parseTime("4:05:06.07"));
            assertEquals(timeSecExpected+700, parseTime("4:05:06.7"));
            assertIllegalTime("2/3/2001 4:05:06");
            assertIllegalTime("4/05:06");
            assertIllegalTime("4:05/06");
            assertIllegalTime("28:05:06");
            assertIllegalTime("4:65:06");
            assertIllegalTime("4:65:66");
            assertIllegalTime("4.0");
        }


        @Test
        public void testTimezone()
        {
            // UNDONE
        }


        @Test
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


        @Test
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
            catch (ConversionException x)
            {
            }

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

            // edge cases
            assertEquals("0s", formatDuration(0));
            assertEquals("-1s", formatDuration(-1000));
            assertEquals("106751991167d7h12m55.807s", formatDuration(Long.MAX_VALUE));
            assertEquals("-106751991167d7h12m55.807s", formatDuration(-Long.MAX_VALUE));
            assertEquals("-106751991167d7h12m55.808s", formatDuration(Long.MIN_VALUE));

            long start = parseISODateTime("2010-01-31");
            assertEquals(parseDateTime("2011-01-31"), addDuration(start,"1y"));
            assertEquals(parseDateTime("2010-02-28"), addDuration(start,"P1m"));
            assertEquals(parseDateTime("2010-02-28"), addDuration(start,"1m0d"));
            assertEquals(parseDateTime("2010-02-28"), addDuration(start,"0y1m"));
            assertEquals(parseDateTime("2010-02-01"), addDuration(start,"1d"));
            assertEquals(parseDateTime("2010-01-31 01:00:00"), addDuration(start,"1h"));
            assertEquals(parseDateTime("2010-01-31 00:01:00"), addDuration(start,"1m"));
            assertEquals(parseDateTime("2010-01-31 00:01:00"), addDuration(start,"PT1m"));
            assertEquals(parseDateTime("2010-01-31 00:00:01"), addDuration(start,"1s"));

            assertEquals(parseDateTime("2009-01-31"), subtractDuration(start,"1y"));
            assertEquals(parseDateTime("2009-12-31"), subtractDuration(start,"P1m"));
            assertEquals(parseDateTime("2009-12-31"), subtractDuration(start,"1m0d"));
            assertEquals(parseDateTime("2009-12-31"), subtractDuration(start,"0y1m"));
            assertEquals(parseDateTime("2010-01-30"), subtractDuration(start,"1d"));
            assertEquals(parseDateTime("2010-01-30 23:00:00"), subtractDuration(start,"1h"));
            assertEquals(parseDateTime("2010-01-30 23:59:00"), subtractDuration(start,"1m"));
            assertEquals(parseDateTime("2010-01-30 23:59:00"), subtractDuration(start,"PT1m"));
            assertEquals(parseDateTime("2010-01-30 23:59:59"), subtractDuration(start,"1s"));
        }


        @Test
        public void testJSON()
        {
            Date datetimeExpected = java.sql.Timestamp.valueOf("2001-02-03 04:05:06");
            long msExpected = java.sql.Timestamp.valueOf("2001-02-03 04:05:06").getTime();

            assertEquals(msExpected, parseDateTime(formatJsonDateTime(datetimeExpected)));

            for (Locale l : DateFormat.getAvailableLocales())
            {
                try
                {
                    SimpleDateFormat f = new SimpleDateFormat(getJsonDateTimeFormatString(), l);
                    String s = f.format(datetimeExpected);
                    assertEquals(l.getDisplayName(), msExpected, f.parse(s).getTime());
                }
                catch (ParseException | ConversionException x)
                {
                    fail(" locale test failed: " + l.getDisplayName());
                }
            }
        }

        @Test
        public void testSplitDate()
        {
            Date fullDate = java.sql.Timestamp.valueOf("2001-02-03 04:05:06");
            Date expectedDate = java.sql.Timestamp.valueOf("2001-02-03 00:00:00");
            Date expectedTime = java.sql.Timestamp.valueOf("1970-01-01 04:05:06");

            Date onlyDate = getDateOnly(fullDate);
            Date onlyTime = getTimeOnly(fullDate);
            assertEquals(onlyDate.getTime(), expectedDate.getTime());
            assertEquals(fullDate.getTime(), combineDateTime(onlyDate, onlyTime).getTime());
            assertEquals(onlyTime.getTime(), expectedTime.getTime());

            // Test that splitting methods work for java.sql.Date as well
            Date sqlDate = java.sql.Date.valueOf("2001-02-03");

            onlyDate = getDateOnly(sqlDate);
            onlyTime = getTimeOnly(sqlDate);
            assertEquals(onlyDate.getTime(), expectedDate.getTime());
            assertEquals(sqlDate.getTime(), combineDateTime(onlyDate, onlyTime).getTime());
            assertEquals(onlyTime.getTime(), new Date(70, 0, 1, 0, 0, 0).getTime());
        }
    }
}

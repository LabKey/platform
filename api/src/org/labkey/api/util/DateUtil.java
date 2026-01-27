/*
 * Copyright (c) 2004-2018 Fred Hutchinson Cancer Research Center
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateParser;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.settings.DateParsingMode;
import org.labkey.api.settings.FolderSettingsCache;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.util.time.CalendarParts;
import org.labkey.api.util.time.ParseDateTimeEN;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static java.util.Calendar.AM_PM;
import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.HOUR;
import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.JANUARY;
import static java.util.Calendar.MILLISECOND;
import static java.util.Calendar.MINUTE;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.SECOND;
import static java.util.Calendar.YEAR;


public class DateUtil
{
    private DateUtil()
    {
    }

    private static final Logger LOG = LogHelper.getLogger(DateUtil.class, "Fill in description");
    private static final Map<Integer, TimeZone> tzCache = new ConcurrentHashMap<>();
    private static final Locale _localeDefault = Locale.getDefault();
    private static final TimeZone _timezoneDefault = TimeZone.getDefault();

    public static final String ISO_DATE_FORMAT_STRING = "yyyy-MM-dd";
    public static final String ISO_SHORT_TIME_FORMAT_STRING = "HH:mm";
    public static final String ISO_DATE_SHORT_TIME_FORMAT_STRING = ISO_DATE_FORMAT_STRING + " " + ISO_SHORT_TIME_FORMAT_STRING;
    public static final String ISO_TIME_FORMAT_STRING = "HH:mm:ss";
    public static final String ISO_LONG_TIME_FORMAT_STRING = "HH:mm:ss.SSS";
    public static final String ISO_DATE_TIME_FORMAT_STRING = ISO_DATE_FORMAT_STRING + " " + ISO_LONG_TIME_FORMAT_STRING;

    // SimpleDataFormat does not support microseconds, it can only support up to milliseconds
    private static final Pattern NON_SIMPLE_PRECISION_TIME_PATTERN = Pattern.compile(".*([0-5][0-9]):([0-5][0-9])\\.(\\d{4,6}).*");
    private static final String[] SIMPLE_TIME_FORMATS_WITH_AMPM = {"hh:mm:ss.SSS a", "hh:mm:ss a", "hh:mm a"};
    private static final String[] SIMPLE_TIME_FORMATS_NO_AMPM = {
        ISO_LONG_TIME_FORMAT_STRING,
        ISO_TIME_FORMAT_STRING,
        ISO_SHORT_TIME_FORMAT_STRING
    };

    public static final Set<String> STANDARD_DATE_DISPLAY_FORMATS = PageFlowUtil.set(
            "yyyy-MM-dd",
            "yyyy-MMM-dd",
            "yyyy-MM",
            "dd-MM-yyyy",
            "dd-MMM-yyyy",
            "dd-MMM-yy",
            "ddMMMyyyy",
            "ddMMMyy",
            "MM/dd/yyyy",
            "MM-dd-yyyy",
            "MMMM dd yyyy"
    );

    public static final Set<String> STANDARD_TIME_DISPLAY_FORMATS = PageFlowUtil.set(
        ISO_TIME_FORMAT_STRING,
        ISO_SHORT_TIME_FORMAT_STRING,
        ISO_LONG_TIME_FORMAT_STRING,
        "hh:mm a"
    );

    private static final Pattern SIGNED_TIME_DURATION_PATTERN =
            Pattern.compile("^[+-]\\d+[pymdhtsPMDHTS].*");

    public static boolean isStandardDateDisplayFormat(String dateFormat)
    {
        return STANDARD_DATE_DISPLAY_FORMATS.contains(dateFormat);
    }

    public record DateTimeFormat(String datePortion, @Nullable String timePortion) {}

    // Splits the date and time portions of a standard date-time format, where date portion is required and time portion
    // is optional. Returns null if the format is non-standard, which means we can't split it.
    public static DateTimeFormat splitDateTimeFormat(@NotNull String dateTimeFormat)
    {
        dateTimeFormat = dateTimeFormat.trim();
        String timePortion = null;

        // We can't just split on whitespace because non-standard formats could: have any amount of whitespace between
        // characters, put the time portion before the date portion, or even intermingle date and time characters.
        for (String format : STANDARD_TIME_DISPLAY_FORMATS)
        {
            if (dateTimeFormat.endsWith(" " + format))
                timePortion = format;
        }

        String datePortion = dateTimeFormat;
        if (timePortion != null)
            datePortion = dateTimeFormat.substring(0, dateTimeFormat.length() - timePortion.length()).trim();

        // If it starts with a standard date format pattern then check for standard time portion (or none)
        if (isStandardDateDisplayFormat(datePortion))
        {
            if (timePortion == null || timePortion.isEmpty())
                return new DateTimeFormat(datePortion, null);
            else if (isStandardTimeDisplayFormat(timePortion))
                return new DateTimeFormat(datePortion, timePortion);
        }

        return null; // Non-standard format
    }

    public static boolean isStandardDateTimeDisplayFormat(String dateTimeFormat)
    {
        return splitDateTimeFormat(dateTimeFormat) != null;
    }

    public static boolean isStandardTimeDisplayFormat(String timeFormat)
    {
        return STANDARD_TIME_DISPLAY_FORMATS.contains(timeFormat);
    }

    /**
     * GregorianCalendar is expensive because it calls computeTime() in setTimeInMillis()
     * (which is called in the constructor)
     */
    private static class _Calendar extends GregorianCalendar
    {
        _Calendar(TimeZone tz, Locale locale)
        {
            super(tz, locale);
        }

        _Calendar(TimeZone tz, Locale locale, int year, int mon, int mday, int hour, int min, int sec, int ms)
        {
            super(tz, locale);
            set(year, mon, mday, hour, min, sec);
            set(Calendar.MILLISECOND, ms);
        }

        _Calendar(TimeZone tz, Locale locale, long l)
        {
            super(tz, locale);
            setTimeInMillis(l);
        }

        @Override
        public void setTimeInMillis(long millis)
        {
            isTimeSet = true;
            time = millis;
            areFieldsSet = false;
        }
    }

    // when strict=true, disallow date overflow arithmetic
    private static Calendar newCalendar(TimeZone tz, int year, int mon, int mday, int hour, int min, int sec, int ms, boolean strict)
    {
        Calendar cal = new _Calendar(tz, _localeDefault, year, mon, mday, hour, min, sec, ms);
        if (strict && (cal.get(YEAR) != year ||
                cal.get(MONTH) != mon ||
                cal.get(Calendar.DAY_OF_MONTH) != mday ||
                cal.get(Calendar.HOUR_OF_DAY) != hour ||
                cal.get(Calendar.MINUTE) != min ||
                cal.get(SECOND) != sec ||
                cal.get(Calendar.MILLISECOND) != ms))
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

    // "HH:mm"
    private static final FastDateFormat TIME_FORMATTER_NO_SECONDS = FastDateFormat.getInstance(ISO_SHORT_TIME_FORMAT_STRING);
    // "HH:mm:ss
    private static final FastDateFormat TIME_FORMATTER_NO_MILLIS = FastDateFormat.getInstance(ISO_TIME_FORMAT_STRING);
    // "HH:mm:ss.SSS
    private static final FastDateFormat TIME_FORMATTER_FULL = FastDateFormat.getInstance(ISO_LONG_TIME_FORMAT_STRING);

    /**
     * Formats a Time value using ISO format, potentially (fFullISO == false) using a format that suppresses unnecessary
     * zero padding; this non-full option is appropriate for formatting values in input fields.
     * @param t a Time object
     * @param fFullISO if true, always uses a full ISO time format (including hours, minutes, seconds, and three-digit
     *                 milliseconds, padding with zeroes as necessary); if false, always includes hours and minutes,
     *                 but potentially truncates the time portion that comprises all zeros (i.e., truncate
     *                 milliseconds if zero, truncate seconds and milliseconds if both are zero)
     * @return a formatted time string
     */
    public static String toISO(Time t, boolean fFullISO)
    {
        final FastDateFormat formatter;

        if (fFullISO)
        {
            formatter = TIME_FORMATTER_FULL;
        }
        else
        {
            long l = t.getTime();
            long milliseconds = l % 1000;

            if (milliseconds > 0)
            {
                formatter = TIME_FORMATTER_FULL;
            }
            else
            {
                long seconds = l % 60000 / 1000;

                if (seconds > 0)
                {
                    formatter = TIME_FORMATTER_NO_MILLIS;
                }
                else
                {
                    formatter = TIME_FORMATTER_NO_SECONDS;
                }
            }
        }

        return formatter.format(t);
    }

    /**
     * Formats a date value using ISO format, potentially (fFullISO == false) using a format that suppresses unnecessary
     * zero padding; this non-full option is appropriate for formatting values in input fields.
     * @param l milliseconds representing a date (milliseconds since 1970-01-01)
     * @param fFullISO if true, always uses a full ISO format (including date, hours, minutes, seconds, and
     *                 three-digit milliseconds, padding with zeroes as necessary); if false, always includes the date
     *                 fields, but potentially truncates the time portion that comprises all zeros (i.e., truncate
     *                 milliseconds if zero, truncate seconds and milliseconds if both are zero, truncate full time
     *                 portion if zero)
     * @return a formatted date string
     */
    public static String toISO(long l, boolean fFullISO)
    {
        StringBuilder sb = new StringBuilder("1999-12-31 23:59:59.999".length());
        Calendar c = newCalendar(l);
        int year = c.get(YEAR);
        int month = c.get(MONTH)+1;
        int day = c.get(Calendar.DAY_OF_MONTH);
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int min = c.get(Calendar.MINUTE);
        int sec = c.get(SECOND);
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


    public enum DateTimeOption
    {
        DateTime
                {
                    @Override
                    public boolean toCalendar(CalendarParts parts, Calendar cal)
                    {
                        // consider supporting other date combos, (week of year, etc.)
                        if (!parts.isSet(YEAR, MONTH, DAY_OF_MONTH))
                            return false;
                        if (!parts.isTimezoneSet())
                            parts.setTimeZone(_timezoneDefault);
                        parts.setCalendar(cal);
                        // if lenient==false, then computeTime() (called by getTimeInMillis()) will validate that no parts are out of range
                        cal.getTimeInMillis();
                        assert cal.isLenient() || (
                                cal.get(YEAR) == parts.getYear() &&
                                cal.get(MONTH) == parts.getMonth() &&
                                cal.get(DAY_OF_MONTH) == parts.getDayOfMonth() &&
                                (!parts.isSet(HOUR) || parts.get(HOUR) < 12) &&
                                (!parts.isSet(HOUR_OF_DAY) || parts.get(HOUR_OF_DAY) < 23) ||
                                (!parts.isSet(MINUTE) || parts.get(MINUTE) < 60) ||
                                (!parts.isSet(SECOND) || parts.get(SECOND) < 60) ||
                                (!parts.isSet(MILLISECOND) || parts.getNanos() < 1_000_000_000));
                        return true;
                    }
                },
        DateOnly
                {
                    @Override
                    public boolean toCalendar(CalendarParts parts, Calendar cal)
                    {
                        if (!parts.isSet(YEAR,MONTH,DAY_OF_MONTH))
                            return false;
                        if (parts.isHourSet() || parts.anySet(MINUTE,SECOND))
                            return false;
                        parts.clearTimezone();
                        parts.setTimeZone(_timezoneDefault);
                        parts.setCalendar(cal);
                        // if lenient==false, then computeTime() will validate that no parts are out of range
                        cal.getTimeInMillis();
                        assert cal.isLenient() || (
                                cal.get(YEAR) == parts.getYear() &&
                                cal.get(MONTH) == parts.getMonth() &&
                                cal.get(DAY_OF_MONTH) == parts.getDayOfMonth());
                        return true;
                    }
                },
        TimeOnly
                {
                    @Override
                    public boolean toCalendar(CalendarParts parts, Calendar cal)
                    {
                        if (parts.anySet(YEAR,MONTH,DAY_OF_MONTH))
                        {
                            // We've allowed TimeOnly parsing to ignore the date part of a string (CONSIDER changing that)
                            // However, don't accept part of a date.
                            if (!cal.isLenient() || !parts.isSet(YEAR,MONTH,DAY_OF_MONTH))
                                return false;
                        }
                        if (!parts.isHourSet() && !parts.anySet(MINUTE, SECOND))
                            return false;
                        if (!cal.isLenient())
                        {
                            if (null != parts.getTimeZone())
                                return false;
                        }
                        // just copy time fields
                        for (int field : Arrays.asList(HOUR,AM_PM,HOUR_OF_DAY,MINUTE,SECOND,MILLISECOND))
                            if (parts.isSet(field))
                                cal.set(field, parts.get(field));
                        cal.set(YEAR, 1970);
                        cal.set(MONTH, JANUARY);
                        cal.set(DAY_OF_MONTH, 1);
                        // if lenient==false, then computeTime() will validate that no parts are out of range
                        cal.getTimeInMillis();
                        assert cal.isLenient() ||
                                (!parts.isSet(HOUR) || parts.get(HOUR) < 12) &&
                                (!parts.isSet(HOUR_OF_DAY) || parts.get(HOUR_OF_DAY) < 23) ||
                                (!parts.isSet(MINUTE) || parts.get(MINUTE) < 60) ||
                                (!parts.isSet(SECOND) || parts.get(SECOND) < 60) ||
                                (!parts.isSet(MILLISECOND) || parts.getNanos() < 1_000_000_000);
                        return true;
                    }
                };


        public abstract boolean toCalendar(CalendarParts parts, Calendar cal);

        public java.util.Date toJavaDate(CalendarParts parts, boolean lenient) throws ConversionException
        {
            _Calendar cal = new _Calendar(_timezoneDefault, _localeDefault);
            cal.setLenient(lenient);
            if (!toCalendar(parts, cal))
                return null;
            return cal.getTime();
        }

        public java.sql.Timestamp toSqlTimestamp(CalendarParts parts, boolean lenient) throws ConversionException
        {
            if (parts.getNanos() < 0 || parts.getNanos() >= 1_000_000_000L)
                return null;
            _Calendar cal = new _Calendar(_timezoneDefault, _localeDefault);
            cal.setLenient(lenient);
            if (!toCalendar(parts, cal))
                return null;
            var ts = new java.sql.Timestamp(cal.getTimeInMillis());
            ts.setNanos((int)parts.getNanos());
            return ts;
        }

        public java.sql.Date toSqlDate(CalendarParts parts, boolean lenient) throws ConversionException
        {
            throw new UnsupportedOperationException();
        }

        public java.sql.Timestamp toSqlTime(CalendarParts parts, boolean lenient) throws ConversionException
        {
            throw new UnsupportedOperationException();
        }
    }

    public enum MonthDayOption
    {
        MONTH_DAY,
        DAY_MONTH
    }

    private static long parseDateTimeUS(String s, DateTimeOption option, boolean strict)
    {
        return parseDateTimeEN(s, option, MonthDayOption.MONTH_DAY,  strict);
    }

    public static TimeZone getTimeZoneForOffsetInMinutes(int offsetInMinutes)
    {
        return tzCache.computeIfAbsent(offsetInMinutes, tzoffset ->
            {
                char sign = tzoffset < 0 ? '-' : '+';
                int mins = Math.abs(tzoffset);
                int hr = mins / 60;
                int mn = mins % 60;
                String tzString = "GMT" + sign + (hr / 10) + (hr % 10) + (mn / 10) + (mn % 10);
                return TimeZone.getTimeZone(tzString);
            }
        );
    }

    private static long parseDateTimeEN(String s, DateTimeOption option, MonthDayOption md, boolean strict)
    {
        try
        {
            var lenient = !strict;
            DateParser parser = ParseDateTimeEN.getInstance(_localeDefault, _timezoneDefault, option, md, lenient);
            Date date = parser.parse(s);
            return date.getTime();
        }
        catch (IllegalArgumentException|ParseException ex)
        {
            throw new ConversionException("Could not parse date '" + s + "'", ex);
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
                    s = s.substring(0, 10) + ' ' + s.substring(11);
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

        throw new ConversionException("Can't parse \"" + s + "\" into a date");
    }

    private static DatatypeFactory DATATYPE_FACTORY = null;

    static
    {
        try
        {
            DATATYPE_FACTORY = DatatypeFactory.newInstance();
        }
        catch (DatatypeConfigurationException e)
        {
            LOG.error("Exception initializing DatatypeFactory; XML date parsing is not available!", e);
        }
    }

    // Examples: "2001-02-03+01:00", "2001-02-03Z", "2018-08-02T06:51:26.551Z", "15:02:00.0000000"
    private static long parseXMLDate(String s)
    {
        if (s.contains(":") || s.contains("Z"))
        {
            // Used to be java.xml.bind.DatatypeConverter.parseDateTime(s).getTimeInMillis(), but that package was
            // removed from Java 10. The code below is equivalent and compatible with Java 8/9/10/11...
            s = s.trim();
            return DATATYPE_FACTORY.newXMLGregorianCalendar(s).toGregorianCalendar().getTimeInMillis();
        }

        throw new IllegalArgumentException();
    }


    /* for transition to new parser, this won't work going forward as we want the parser to return calendar parts
     * e.g. java.text.CalendarBuilder or something like it
     */
    static DateParser getSimpleDateFormat(TimeZone zone, String pattern)
    {
        final SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.getDefault());
        format.setTimeZone(zone);
        format.setLenient(false);

        return new DateParser()
        {
            @Override
            public Locale getLocale()
            {
                return Locale.getDefault();
            }

            @Override
            public String getPattern()
            {
                return pattern;
            }

            @Override
            public TimeZone getTimeZone()
            {
                return format.getTimeZone();
            }

            @Override
            public Date parse(String source) throws ParseException
            {
                return format.parse(source);
            }

            @Override
            public Date parse(String source, ParsePosition pos)
            {
                return format.parse(source, pos);
            }

            @Override
            public boolean parse(String source, ParsePosition pos, Calendar calendar)
            {
                return false;
            }

            @Override
            public Object parseObject(String source) throws ParseException
            {
                return parse(source);
            }

            @Override
            public Object parseObject(String source, ParsePosition pos)
            {
                return parse(source, pos);
            }
        };
    }

    static DateParser getFastDateFormat(TimeZone zone, String pattern)
    {
        return FastDateFormat.getInstance(pattern, zone, Locale.getDefault());
    }

    /* Still treat as non-thread safe */
    static DateParser getDateParser(String pattern)
    {
        return getDateParser(_timezoneDefault, pattern);
    }

    // use choke point (with apache DateParser interface) to make it easier to test implementations
    static DateParser getDateParser(TimeZone tz, String pattern)
    {
        return getSimpleDateFormat(tz, pattern);
    }

    // Parse using a specific pattern... used where strict parsing or non-standard pattern is required
    // Note: SimpleDateFormat is not thread-safe, so we create a new one for every parse.
    public static Date parseDateTime(String s, String pattern) throws ParseException
    {
        if (null == s)
            throw new ParseException("Date string is empty", 0);

        return getDateParser(pattern).parse(s);
    }

    // Lenient parsing using a variety of standard formats
    public static long parseDateTime(String s)
    {
        //Issue 30004: Remote servers cannot use the database to lookup MonthDayOption, so default to MONTH_DAY
        boolean isNotRunningOnWebServer = PipelineJobService.get() == null ||
                PipelineJobService.get().getLocationType() != PipelineJobService.LocationType.WebServer;
        if (isNotRunningOnWebServer)
        {
            return parseDateTime(s, MonthDayOption.MONTH_DAY, true, null);
        }

        MonthDayOption monthDayOption = LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getDateParsingMode().getDayMonth();

        return parseDateTime(s, monthDayOption, true, null);
    }

    // TODO: Migrate callers and remove this variant
    @Deprecated
    public static long parseDateTime(Container c, String s)
    {
        return parseDateTime(s);
    }

    private static long parseDateTime(String s, MonthDayOption md)
    {
        return parseDateTime(s, md, true, null);
    }

    public static long parseDateTime(String s, MonthDayOption md, boolean strict, @Nullable String extraParsingPattern)
    {
        // If provided, try the extra parsing pattern first
        if (null != extraParsingPattern)
        {
            try
            {
                return parseDateTime(s, extraParsingPattern).getTime();
            }
            catch (ParseException ignored)
            {
            }
        }

        try
        {
            return parseDateTimeEN(s, DateTimeOption.DateTime, md, strict);
        }
        catch (ConversionException ignored) {}

        return parseSpecialFormats(s);
    }

    // Lenient parsing using a variety of standard formats
    public static long parseDate(String s)
    {
        MonthDayOption monthDayOption = LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getDateParsingMode().getDayMonth();

        return parseDate(s, monthDayOption, null);    }

    // TODO: Migrate callers and remove this variant
    @Deprecated
    public static long parseDate(Container c, String s)
    {
        return parseDate(s);
    }

    private static long parseDate(String s, MonthDayOption md, @Nullable String extraParsingPattern)
    {
        // If provided, try the extra parsing pattern first
        if (null != extraParsingPattern)
        {
            try
            {
                Date parsed = parseDateTime(s, extraParsingPattern);
                return DateUtils.truncate(parsed, Calendar.DAY_OF_MONTH).getTime(); // Truncate to days... should this throw instead?
            }
            catch (ParseException ignored)
            {
            }
        }

        try
        {
            // quick check for JDBC/ISO date
            if (s.length() == 10 && s.charAt(4) == '-' && s.charAt(7) == '-')
                return parseISODateTime(s);
        }
        catch (ConversionException ignored) {}

        try
        {
            return parseDateTimeEN(s, DateTimeOption.DateOnly, md, true);
        }
        catch (ConversionException e)
        {
            try
            {
                return parseXMLDate(s);
            }
            catch (IllegalArgumentException ignored)
            {
            }
            throw e;
        }
    }

    public static @Nullable Date parseSimpleTime(@NotNull String s)
    {
        Date duration = null;
        ParseException parseException = null;
        boolean hasAMPM = s.toLowerCase().endsWith(" am") || s.toLowerCase().endsWith(" pm");
        String[] validFormats = hasAMPM ? SIMPLE_TIME_FORMATS_WITH_AMPM : SIMPLE_TIME_FORMATS_NO_AMPM;
        for (int i = 0; i < validFormats.length && duration == null; i++)
        {
            try
            {
                duration = DateUtil.parseDateTime(s, validFormats[i]);
            }
            catch (ParseException ignore)
            {
                // SimpleDateFormat is not able to parse precision higher than milliseconds
                if (NON_SIMPLE_PRECISION_TIME_PATTERN.matcher(s).matches())
                    return null;
            }
        }
        if (duration == null)
            throw new ConversionException("Could not convert \"" + s + "\" to time.", parseException);
        return duration;
    }

    static final long defaultTimeOffsetMillis = TimeZone.getDefault().getOffset(0);

    public static long parseTimeToMillis(String s, boolean strict)
    {
        long localTime = parseDateTimeUS(s, DateTimeOption.TimeOnly, strict);
        // remove timezone offset
        return localTime + defaultTimeOffsetMillis;
    }

    public static long parseTimeToMillis(String s)
    {
        return parseTimeToMillis(s, true);
    }

    // parse time as Date on epoch 1970/1/1
    public static Time fromTimeString(@NotNull String s, boolean strict)
    {
        return fromTimeString(s, strict, false);
    }

    // parse time as Date on epoch 1970/1/1
    public static Time fromTimeString(@NotNull String s, boolean strict, boolean simpleParsingOnly)
    {
        try
        {
            Date parsedTime = parseSimpleTime(s);
            if (parsedTime != null)
                return new Time(parsedTime.getTime());
        }
        catch (ConversionException ignored)
        {
        }

        if (simpleParsingOnly)
            return null;

        // try parse as a datetime first, if contains more than one spaces, or if contains space not followed by am/pm
        if  (StringUtils.countMatches(s, " ") > 1
                || (StringUtils.countMatches(s, " ") > 0
                && !(s.toLowerCase().endsWith(" am") || s.toLowerCase().endsWith(" pm"))))
        {
            try
            {
                return new Time(parseDateTime(s));
            }
            catch (ConversionException ignored)
            {
            }
        }

        if  (StringUtils.countMatches(s, "T") == 1) // ISO datetime
        {
            try
            {
                return new Time(parseDateTime(s));
            }
            catch (ConversionException ignored)
            {
            }
        }

        if (StringUtils.countMatches(s, " ") == 0 && StringUtils.countMatches(s, ":") == 0) { // try date-only format
            try
            {
                return new Time(parseDate(s));
            }
            catch (ConversionException ignored)
            {
            }
        }

        long time = parseDateTimeUS(s, DateTimeOption.TimeOnly, strict);
        return new Time(time);
    }

    public static String getStandardDateFormatString()
    {
        return ISO_DATE_FORMAT_STRING;
    }

    public static String getStandardDateTimeFormatString()
    {
        return ISO_DATE_SHORT_TIME_FORMAT_STRING;
    }

    public static String getStandardTimeFormatString()
    {
        return ISO_TIME_FORMAT_STRING;
    }

    public static String getJsonDateTimeFormatString()
    {
        // 1. added milliseconds (.SSS) to gap the discrepancy of milliseconds portion stored in database
        // but not being used while checking for work during a remote ETL run as part of implementing Issue 35780.
        // Without millisecond portion here, datetime comparison would show these datetimes as equals,
        // ex: 2019/01/01 11:11:11.117 and 2019/01/01 11:11:11.399 - since milliseconds would be ignored, this would result in No Work.

        // 2. Separately, adding .SSS to the existing string (yyyy/MM/dd HH:mm:ss) worked on Chrome (and MS Edge), but not on Firefox.
        // Strangely, Firefox seem to adhere strictly to ECMA Specification of date string interchange format (i.e. date with
        // hyphens instead of forward slashes) in this particular case with milliseconds. Hence, had to modify the previous string from yyyy/MM/dd to yyyy-MM-dd.
        // Chrome seem to behave as expected with this change (and so does MS Edge, but extensive testing has not been done on this browser).
        return ISO_DATE_TIME_FORMAT_STRING;
    }

    public static String getJsonTimeFormatString()
    {
        return ISO_LONG_TIME_FORMAT_STRING;
    }

    /**
     * Format current date using ISO 8601 pattern. This is appropriate only for persisting dates in machine-readable
     * form, for example, for export or in filenames. Most callers should use formatDate(Container c) instead.
     */
    public static String formatIsoDate()
    {
        return formatIsoDate(new Date());
    }

    /**
     * Format date using ISO 8601 pattern. This is appropriate only for persisting dates in machine-readable form,
     * for example, for export or in filenames. Most callers should use formatDate(Container c, Date d) instead.
     */
    public static String formatIsoDate(@Nullable Date date)
    {
        return formatDateTime(date, ISO_DATE_FORMAT_STRING);
    }

    /**
     * Format date and time using ISO 8601 pattern. This is appropriate only for persisting dates in machine-readable
     * form, for example, for export or in filenames. Most callers should use formatDateTime(Container c, Date d) instead.
     */
    public static String formatIsoDateShortTime(Date date)
    {
        return formatDateTime(date, ISO_DATE_SHORT_TIME_FORMAT_STRING);
    }

    /**
     * Format date and time using ISO 8601 pattern including seconds. This is appropriate only for persisting dates in
     * machine-readable form, for example, for export or in filenames. Most callers should use
     * formatDateTime(Container c, Date d) instead.
     */
    public static String formatIsoDateLongTime(Date date)
    {
        return formatIsoDateLongTime(date, false);
    }

    /**
     * Format date and time using ISO 8601 pattern including seconds. Optionally, the time component can be formatted
     * using the long format of the time which includes milliseconds. This is appropriate only for persisting dates in
     * machine-readable form, for example, for export or in filenames. Most callers should use
     * formatDateTime(Container c, Date d) instead.
     */
    public static String formatIsoDateLongTime(Date date, boolean longTimeFormat)
    {
        String timeFormat = longTimeFormat ? ISO_LONG_TIME_FORMAT_STRING : ISO_TIME_FORMAT_STRING;
        return formatDateTime(date, ISO_DATE_FORMAT_STRING + " " + timeFormat);
    }

    public static String formatIsoLongTime(Time time)
    {
        return formatDateTime(time, ISO_LONG_TIME_FORMAT_STRING);
    }

    /**
     * Format current date using folder-specified default pattern
     * Warning: Return value is unsafe and must be HTML filtered, if rendered to an HTML page
     */
    public static String formatDate(Container c)
    {
        return formatDate(c, new Date());
    }

    /**
     * Format a date using folder-specified default pattern
     * Warning: Return value is unsafe and must be HTML filtered, if rendered to an HTML page
     */
    public static String formatDate(Container c, Date date)
    {
        return formatDateTime(date, getDateFormatString(c));
    }

    /**
     * Format a LocalDate using folder-specified default pattern.
     * Warning: Return value is unsafe and must be HTML filtered, if rendered to an HTML page
     */
    public static String formatDate(Container c, @Nullable LocalDate date)
    {
        if (null == date)
            return null;

        // LocalDate doesn't include time zone, but the display format might specify "z". Add the server's time zone to
        // prevent an exception.
        ZonedDateTime zoned = ZonedDateTime.of(date, LocalTime.MIDNIGHT, ZoneId.systemDefault());
        return zoned.format(DateTimeFormatter.ofPattern(getDateFormatString(c)));
    }

    /**
     * Format current date & time using folder-specified default date/time pattern
     * Warning: Return value is unsafe and must be HTML filtered, if rendered to an HTML page
     */
    public static String formatDateTime(Container c)
    {
        return formatDateTime(c, new Date());
    }

    /**
     * Format date & time using folder-specified default date pattern plus standard time format
     * Warning: Return value is unsafe and must be HTML filtered, if rendered to an HTML page
     */
    public static String formatDateTime(Container c, Date date)
    {
        return formatDateTime(date, getDateTimeFormatString(c));
    }

    /**
     * Format a LocalDateTime using folder-specified default pattern.
     * Warning: Return value is unsafe and must be HTML filtered, if rendered to an HTML page
     */
    public static String formatDateTime(Container c, @Nullable LocalDateTime dateTime)
    {
        if (null == dateTime)
            return null;

        // LocalDateTime doesn't include time zone, but the display format might specify "z". Add the server's time zone to
        // prevent an exception.
        ZonedDateTime zoned = ZonedDateTime.of(dateTime, ZoneId.systemDefault());
        return zoned.format(DateTimeFormatter.ofPattern(getDateTimeFormatString(c)));
    }

    /**
     * Format date, inferring the appropriate folder-specified default pattern (date vs. date-time) based on class of date
     * Warning: Return value is unsafe and must be HTML filtered, if rendered to an HTML page
     */
    public static String formatDateInfer(Container c, Date date)
    {
        String format = getDateTimeFormatString(c);
        if (date instanceof java.sql.Date)
            format = getDateFormatString(c);
        if (date instanceof Time)
            format = getTimeFormatString(c);
        return formatDateTime(date, format);
    }

    /**
     * Format date & time using specified pattern
     * Note: Unlike SimpleDateFormat, this implementation is thread-safe and reuses formatters
     */
    public static String formatDateTime(@Nullable Date date, String pattern)
    {
        if (null == date)
            return null;
        else
            return FastDateFormat.getInstance(pattern).format(date);
    }

    /**
     * Get the default date display format string to use in this Container
     * Note: The display format is specified by an admin; it could contain any characters, hence, it may not be safe.
     * Any value formatted by this pattern must be HTML filtered, if rendered to an HTML page.
     * THIS IS A DISPLAY FORMAT STRING; DO NOT USE IT FOR PARSING!
     */
    public static String getDateFormatString(Container c)
    {
        return FolderSettingsCache.getDefaultDateFormat(c);
    }

    /**
     * Get the default date/time display format string set in this Container (or one of its parents)
     * Note: The display format is specified by an admin; it could contain any characters, hence, it may not be safe.
     * Any value formatted by this pattern must be HTML filtered, if rendered to an HTML page.
     * THIS IS A DISPLAY FORMAT STRING; DO NOT USE IT FOR PARSING!
     */
    public static String getDateTimeFormatString(Container c)
    {
        return FolderSettingsCache.getDefaultDateTimeFormat(c);
    }

    /**
     * Get the default time display format string set in this Container (or one of its parents)
     * Note: The display format is specified by an admin; it could contain any characters, hence, it may not be safe.
     * Any value formatted by this pattern must be HTML filtered, if rendered to an HTML page.
     * THIS IS A DISPLAY FORMAT STRING; DO NOT USE IT FOR PARSING!
     */
    public static String getTimeFormatString(Container c)
    {
        return FolderSettingsCache.getDefaultTimeFormat(c);
    }

    public static final Set<String> META_FORMATS = CaseInsensitiveHashSet.of("Date", "DateTime", "Time");

    /**
     * Test a date format string to determine if it matches one of LabKey's special named date formats (Date, DateTime, Time)
     * @param dateFormat Format string to test
     * @return True if the dateFormat matches one of LabKey's special named date formats, otherwise False
     */
    public static boolean isSpecialNamedFormat(String dateFormat)
    {
        return META_FORMATS.contains(dateFormat);
    }

    private static final FastDateFormat jsonDateFormat = FastDateFormat.getInstance(getJsonDateTimeFormatString());
    private static final FastDateFormat jsonTimeFormat = FastDateFormat.getInstance(getJsonTimeFormatString());

    public static String formatJsonDateTime(Date date)
    {
        if (date instanceof Time)
            return jsonTimeFormat.format(date);

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
        try
        {
            // This should match values starting with [sign][number][date/time duration units] (ex. +1d, -300d).
            // This should not match other signed values like just "+" or "-" or temps "-80C"
            if (!SIGNED_TIME_DURATION_PATTERN.matcher(durationCandidate).matches())
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
        if (duration.startsWith("+"))
             return addDuration(time, duration.substring(1));
        if (duration.startsWith("-"))
            return subtractDuration(time, duration.substring(1));

        throw new IllegalArgumentException("The duration provided is not valid: " + duration);
    }

    private static _duration _parseDuration(String s)
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
            calendar.add(YEAR, d.year*sign);
        if (d.month > 0)
            calendar.add(MONTH, d.month*sign);
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
        if (hour != 0 || !s.isEmpty())
            s.append(String.valueOf(hour)).append("h");
        if (r == 0)
            return s.toString();

        long min = r / DateUtils.MILLIS_PER_MINUTE;
        r = r % DateUtils.MILLIS_PER_MINUTE;
        if (min != 0 || !s.isEmpty())
            s.append(min).append("m");
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

    public static TimeZone getTimeZone()
    {
        return _timezoneDefault;
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

    @SuppressWarnings("deprecation")
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

        private Time parseTime(String s)
        {
            return new Time(DateUtil.parseDateTimeUS(s, DateTimeOption.TimeOnly, false));
        }

        void assertIllegalDate(String s)
        {
            try
            {
                parseDate(s);
                fail("Should not have successfully parsed: " + s);
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
                fail("Should not have successfully parsed: " + s);
            }
            catch (ConversionException x)
            {
            }
        }

        void assertIllegalTime(String s)
        {
            try
            {
                parseTimeToMillis(s);
                fail("Not a legal datetime: " + s);
            }
            catch (ConversionException x)
            {
            }
        }

        void assertIllegalFromTimeString(String s, boolean strict, boolean simpleFormatOnly)
        {
            try
            {
                Time parsed = DateUtil.fromTimeString(s, strict, simpleFormatOnly);
                if (parsed != null)
                    fail("Not a legal time " + (simpleFormatOnly ? "in simple format" : "") + ": " + s);
            }
            catch (ConversionException x)
            {
            }
        }

        void assertIllegalFromTimeString(String s, boolean strict)
        {
            assertIllegalFromTimeString(s, strict, false);
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

            // milliseconds trimmed to 3 decimal places
            assertEquals(datetimeExpected+100, parseDateTime("03-FEB-2001 04:05:06.1"));
            assertEquals(datetimeExpected+120, parseDateTime("03-FEB-2001 04:05:06.12"));
            assertEquals(datetimeExpected+123, parseDateTime("03-FEB-2001 04:05:06.123"));
            assertEquals(datetimeExpected+123, parseDateTime("03-FEB-2001 04:05:06.1234"));
            assertEquals(datetimeExpected+123, parseDateTime("03-FEB-2001 04:05:06.123456"));

            // fractional seconds
            assertEquals(datetimeExpected, parseDateTime("03-FEB-2001 04:05:06:00"));
            // "01" fractional seconds is .01666 seconds, rounded to .017
            assertEquals(datetimeExpected+17, parseDateTime("03-FEB-2001 04:05:06:01"));
            assertEquals(datetimeExpected+17, parseDateTime("03-FEB-2001 4:05:06:01"));
            assertEquals(datetimeExpected+133, parseDateTime("03-FEB-2001 04:05:06:08"));
            assertEquals(datetimeExpected+983, parseDateTime("03-FEB-2001 04:05:06:59"));
            // invalid fractional seconds > 59
            assertIllegalDateTime("03-FEB-2001 04:05:06:60");
            assertIllegalDateTime("03-FEB-2001 04:05:06:61");
            assertIllegalDateTime("03-FEB-2001 04:05:06:91");
            // invalid fractional seconds > 59, but allowed to overflow when strict=false
            assertEquals(datetimeExpected+1000, DateUtil.parseDateTime("03-FEB-2001 4:05:06:60", MonthDayOption.MONTH_DAY, false, null));
            assertEquals(datetimeExpected+1017, DateUtil.parseDateTime("03-FEB-2001 4:05:06:61", MonthDayOption.MONTH_DAY, false, null));
            assertEquals(datetimeExpected+1517, DateUtil.parseDateTime("03-FEB-2001 4:05:06:91", MonthDayOption.MONTH_DAY, false, null));


            // Test parseXMLDate() handling of time and date time values
//            assertEquals(Timestamp.valueOf("2018-08-01 23:51:26.551").getTime(), parseDateTime("2018-08-02T06:51:26.551Z"));
// BUG?     parseDateTime() is not supposed to parse time-only values
            assertEquals(Timestamp.valueOf("1970-01-01 15:02:00").getTime(), parseDateTime("15:02:00.0000000"));
            assertEquals(Timestamp.valueOf("1970-01-01 15:02:00").getTime(), fromTimeString("15:02:00.0000000",true).getTime());

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

            // Test parseXMLDate() handling of XML date formats
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
            DateParser zo = getDateParser("yyyy-MM-dd HH:mm:ss z");
            DateParser lo = getDateParser("yyyy-MM-dd HH:mm:ss");
            DateParser ut = getDateParser(TimeZone.getTimeZone("GMT"), "yyyy-MM-dd HH:mm:ss");

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
            assertEquals(datetimeLocal, parseDateTimeUS("2001-02-03 04:05:06", DateTimeOption.DateTime, true));
            assertEquals(datetimeLocal, parseDateTimeUS("2001-02-03T04:05:06", DateTimeOption.DateTime, true));
            assertEquals(datetimeUTC, parseDateTimeUS("2001-02-03 04:05:06Z", DateTimeOption.DateTime, true));
            assertEquals(datetimeUTC, parseDateTimeUS("2001-02-03T04:05:06Z", DateTimeOption.DateTime, true));

            // Now try with milliseconds too
            assertEquals(datetimeLocal + 213, parseDateTimeUS("2001-02-03 04:05:06.213", DateTimeOption.DateTime, true));
            assertEquals(datetimeLocal + 213, parseDateTimeUS("2001-02-03T04:05:06.213", DateTimeOption.DateTime, true));
            assertEquals(datetimeUTC + 213, parseDateTimeUS("2001-02-03 04:05:06.213Z", DateTimeOption.DateTime, true));
            assertEquals(datetimeUTC + 213, parseDateTimeUS("2001-02-03T04:05:06.213Z", DateTimeOption.DateTime, true));

            assertIllegalDateTime("20131113_Guide Set plate 1.xls");

            // Test extra date-time parsing pattern
            assertIllegalDateTime("03FEB2001:04:05:06"); // Should fail... not a standard format
            assertEquals(datetimeLocal, DateUtil.parseDateTime("2001-02-03 04:05:06", MonthDayOption.MONTH_DAY, true, "ddMMMyyyy:HH:mm:ss")); // Standard parsing patterns still work
            assertEquals(datetimeLocal, DateUtil.parseDateTime("03FEB2001:04:05:06", MonthDayOption.MONTH_DAY, true, "ddMMMyyyy:HH:mm:ss"));

            // Test extra date parsing pattern
            assertIllegalDateTime("03$FEB$2001"); // Should fail... not a standard format
            assertEquals(dateExpected, DateUtil.parseDate("2001-02-03", MonthDayOption.MONTH_DAY, "dd$MMM$yyyy")); // Standard parsing patterns still work
            assertEquals(dateExpected, DateUtil.parseDate("03$FEB$2001", MonthDayOption.MONTH_DAY, "dd$MMM$yyyy"));
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
            assertXmlDateMatches(DateUtil.parseDate("2001-02-03+01:00", MonthDayOption.DAY_MONTH, null));
            assertXmlDateMatches(DateUtil.parseDate("2001-02-03Z", MonthDayOption.DAY_MONTH, null));
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
            DateParser zo = getDateParser("yyyy-MM-dd HH:mm:ss z");
            DateParser lo = getDateParser("yyyy-MM-dd HH:mm:ss");
            DateParser ut = getDateParser(TimeZone.getTimeZone("GMT"), "yyyy-MM-dd HH:mm:ss");

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
            assertEquals(datetimeLocal, parseDateTimeEN("2001-02-03 04:05:06", DateTimeOption.DateTime, MonthDayOption.DAY_MONTH, true));
            assertEquals(datetimeLocal, parseDateTimeEN("2001-02-03T04:05:06", DateTimeOption.DateTime, MonthDayOption.DAY_MONTH, true));
            assertEquals(datetimeUTC, parseDateTimeEN("2001-02-03 04:05:06Z", DateTimeOption.DateTime, MonthDayOption.DAY_MONTH, true));
            assertEquals(datetimeUTC, parseDateTimeEN("2001-02-03T04:05:06Z", DateTimeOption.DateTime, MonthDayOption.DAY_MONTH, true));
        }

        @Test
        public void testDateTimeCH()
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
            assertEquals(2001, parsedXml.get(YEAR));
            assertEquals(Calendar.FEBRUARY, parsedXml.get(MONTH));
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

        private int getMillisecondsFromTimeString(String str, boolean strict, boolean simpleOnly)
        {
            return (int) (fromTimeString(str, strict, simpleOnly).getTime() % 1000);
        }

        private int getMillisecondsFromTimeString(String string, boolean strict)
        {
            return getMillisecondsFromTimeString(string, strict, false);
        }

        @Test
        public void testTime()
        {
            long hrs12 = TimeUnit.HOURS.toMillis(12);
            long timeSecExpected = TimeUnit.HOURS.toMillis(4) + TimeUnit.MINUTES.toMillis(5) + TimeUnit.SECONDS.toMillis(6);
            long timeMinExpected = TimeUnit.HOURS.toMillis(4) + TimeUnit.MINUTES.toMillis(5);
            long timeHrExpected = TimeUnit.HOURS.toMillis(4);
            assertEquals(timeHrExpected, parseTimeToMillis("4"));
            assertEquals(timeHrExpected, parseTimeToMillis("4 am"));
            assertEquals(timeHrExpected, parseTimeToMillis("4AM"));
            assertEquals(timeHrExpected + hrs12, parseTimeToMillis("4pm"));
            assertEquals(timeHrExpected + hrs12, parseTimeToMillis("16"));
            assertEquals(timeHrExpected + hrs12, parseTimeToMillis("16:00:00"));
            assertEquals(timeMinExpected, parseTimeToMillis("4:05"));
            assertEquals(timeSecExpected, parseTimeToMillis("4:05:06"));
            assertEquals(timeSecExpected, parseTimeToMillis("4:05:06 am"));
            assertEquals(timeSecExpected, parseTimeToMillis("4:05:06AM"));
            assertEquals(timeSecExpected, parseTimeToMillis("4:05:06.0"));
            assertEquals(timeSecExpected, parseTimeToMillis("4:05:06.00"));
            assertEquals(timeSecExpected, parseTimeToMillis("4:05:06.000"));
            assertEquals(timeSecExpected+7, parseTimeToMillis("4:05:06.007"));
            assertEquals(timeSecExpected+70, parseTimeToMillis("4:05:06.07"));
            assertEquals(timeSecExpected+700, parseTimeToMillis("4:05:06.7"));

            // milliseconds trimmed to 3 decimal places
            assertEquals(timeSecExpected+100, parseTimeToMillis("4:05:06.1"));
            assertEquals(timeSecExpected+120, parseTimeToMillis("4:05:06.12"));
            assertEquals(timeSecExpected+123, parseTimeToMillis("4:05:06.123"));
            assertEquals(timeSecExpected+123, parseTimeToMillis("4:05:06.1234"));
            assertEquals(timeSecExpected+123, parseTimeToMillis("4:05:06.123456"));

            // fractional seconds
            assertEquals(timeSecExpected, parseTimeToMillis("4:05:06:00"));
            // "01" fractional seconds is .01666 seconds, rounded to .017
            assertEquals(timeSecExpected+17, parseTimeToMillis("4:05:06:01"));
            assertEquals(timeSecExpected+17, parseTimeToMillis("04:05:06:01"));
            assertEquals(timeSecExpected+133, parseTimeToMillis("4:05:06:08"));
            assertEquals(timeSecExpected+983, parseTimeToMillis("4:05:06:59"));
            // invalid fractional seconds > 59
            assertIllegalTime("4:05:06:60");
            assertIllegalTime("4:05:06:61");
            assertIllegalTime("4:05:06:91");
            // invalid fractional seconds > 59, but allowed to overflow when strict=false
            assertEquals(timeSecExpected+1000, parseTimeToMillis("4:05:06:60", false));
            assertEquals(timeSecExpected+1017, parseTimeToMillis("4:05:06:61", false));
            assertEquals(timeSecExpected+1517, parseTimeToMillis("4:05:06:91", false));

            assertEquals(java.sql.Time.valueOf("0:05:06"), fromTimeString("0:05:06", true));
            assertEquals(java.sql.Time.valueOf("12:05:06"), fromTimeString("12:05:06", true));
            assertEquals(java.sql.Time.valueOf("23:05:06"), fromTimeString("23:05:06", true));

            assertIllegalTime("2/3/2001 4:05:06");
            assertIllegalTime("4/05:06");
            assertIllegalTime("4:05/06");
            assertIllegalTime("28:05:06");
            assertIllegalTime("4:65:06");
            assertIllegalTime("4:65:66");
            assertIllegalTime("4.0");

            assertEquals(parseISODateTime("1970-1-1 16:00:00"), fromTimeString("4 pm", true).getTime());

            assertIllegalTime("1830");
            assertIllegalTime("19999 pm");
            assertIllegalFromTimeString("19999 pm", false);
            assertIllegalFromTimeString("19999 pm", true);

            assertEquals(java.sql.Time.valueOf("3:00:00"), fromTimeString("3", true));
            assertEquals(java.sql.Time.valueOf("3:00:00"), fromTimeString("03", true));
            assertEquals(java.sql.Time.valueOf("6:00:00").toString(), fromTimeString("30", false).toString());
            assertIllegalFromTimeString("30", true);
            assertEquals(java.sql.Time.valueOf("21:00:00").toString(), fromTimeString("69", false).toString());
            assertIllegalFromTimeString("69", true);
            assertIllegalFromTimeString("70", false);
            assertIllegalFromTimeString("70", true);
            assertIllegalFromTimeString("1830", false);
            assertIllegalFromTimeString("1830", true);

            assertEquals(java.sql.Time.valueOf("11:22:33").toString(), fromTimeString("11:22:33.1", true, true).toString());
            assertEquals(1, getMillisecondsFromTimeString("11:22:33.1", true, true));
            assertEquals(java.sql.Time.valueOf("11:22:33").toString(), fromTimeString("11:22:33.1", true, false).toString());
            assertEquals(1, getMillisecondsFromTimeString("11:22:33.1", true, false));
            assertEquals(java.sql.Time.valueOf("23:22:33").toString(), fromTimeString("11:22:33.1 PM", true, true).toString());
            assertEquals(1, getMillisecondsFromTimeString("11:22:33.1 PM", true, true));
            assertEquals(java.sql.Time.valueOf("23:22:33").toString(), fromTimeString("11:22:33.1 PM", true, false).toString());
            assertEquals(1, getMillisecondsFromTimeString("11:22:33.1 PM", true, false));
            assertEquals(java.sql.Time.valueOf("11:22:33").toString(), fromTimeString("11:22:33.123", true, true).toString());
            assertEquals(123, getMillisecondsFromTimeString("11:22:33.123", true, true));
            assertEquals(java.sql.Time.valueOf("11:22:33").toString(), fromTimeString("11:22:33.123", true, false).toString());
            assertEquals(123, getMillisecondsFromTimeString("11:22:33.123", true, false));
            assertEquals(java.sql.Time.valueOf("23:22:33").toString(), fromTimeString("11:22:33.123 PM", true, true).toString());
            assertEquals(123, getMillisecondsFromTimeString("11:22:33.123 PM", true, true));
            assertEquals(java.sql.Time.valueOf("23:22:33").toString(), fromTimeString("11:22:33.123 PM", true, false).toString());
            assertEquals(123, getMillisecondsFromTimeString("11:22:33.123 PM", true, false));
            assertIllegalFromTimeString("11:22:33.1234", true, true);
            assertIllegalFromTimeString("11:22:33.3456 PM", true, true);
            assertEquals(java.sql.Time.valueOf("11:22:33").toString(), fromTimeString("11:22:33.3453", true, false).toString());
            assertEquals(345, getMillisecondsFromTimeString("11:22:33.3453", true, false));
            assertEquals(java.sql.Time.valueOf("23:22:33").toString(), fromTimeString("11:22:33.3456 PM", true, false).toString());
            assertEquals(346, getMillisecondsFromTimeString("11:22:33.3456 PM", true, false));

        }

        @Test
        public void testTimezone()
        {
            // UNDONE
        }

        @Test
        public void testIsoDateFormat()
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
            c.clear(HOUR);
            c.clear(AM_PM);
            c.set(HOUR_OF_DAY,0);
            l = c.getTimeInMillis();
            assertEquals(toISO(l, false).length(), "1999-12-31".length());
        }

        @Test
        public void testIsoTimeFormat()
        {
            validateTimeFormat("23:59:59.999", "23:59:59.999", "23:59:59.999");
            validateTimeFormat("23:59:59.500", "23:59:59.500", "23:59:59.500");
            validateTimeFormat("23:59:59.001", "23:59:59.001", "23:59:59.001");

            validateTimeFormat("23:59:59", "23:59:59.000", "23:59:59");
            validateTimeFormat("23:59:31", "23:59:31.000", "23:59:31");
            validateTimeFormat("23:59:01", "23:59:01.000", "23:59:01");

            validateTimeFormat("23:59", "23:59:00.000", "23:59");
            validateTimeFormat("23:31", "23:31:00.000", "23:31");
            validateTimeFormat("23:00:00", "23:00:00.000", "23:00");
        }

        private void validateTimeFormat(String time, String full, String notFull)
        {
            Time t = parseTime(time);
            assertEquals(full, toISO(t, true));
            assertEquals(notFull, toISO(t, false));
        }

        @Test
        public void testDateTimeFormat()
        {
            long longDate = parseDateTime("2019-05-03 04:32:00.123");
            Date date = new Date(longDate);
            Time time = new Time(longDate);

            // formatIsoDateShortTime
            assertEquals("2019-05-03 04:32", formatIsoDateShortTime(date));

            // formatIsoDateLongTime
            assertEquals("2019-05-03 04:32:00", formatIsoDateLongTime(date));
            assertEquals("2019-05-03 04:32:00", formatIsoDateLongTime(date, false));
            assertEquals("2019-05-03 04:32:00.123", formatIsoDateLongTime(date, true));

            // formatIsoLongTime
            assertEquals("04:32:00.123", formatIsoLongTime(time));
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
                // Skipping Japanese Imperial calendar locale, since it doesn't parse the date correctly when we use SimpleDateFormat.
                // ex. Date '2001-02-03' returns '13-02-03' with simple date format yyyy-MM-dd.
                // And datetime '2001-02-03 04:05:06' returns '13-02-03 04:05:06.000' with getJsonDateTimeFormatString()
                // But that's so last era's problem!
                // With the new Japanese era 'Reiwa' beginning May 1, 2019, Date '2019-05-01' returns '元-05-01' with simple date format yyyy-MM-dd
                // This makes it a special case and would require special handling. Hence, skipping!
                if (l.hasExtensions() && ("ca-japanese").equalsIgnoreCase(l.getExtension('u')))
                {
                    continue;
                }
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
            assertNotNull(onlyTime);
            assertEquals(onlyDate.getTime(), expectedDate.getTime());
            assertEquals(sqlDate.getTime(), combineDateTime(onlyDate, onlyTime).getTime());
            assertEquals(onlyTime.getTime(), new Date(70, 0, 1, 0, 0, 0).getTime());
        }

        int h(long m)
        {
            long hrs = m / (60*60*1000L);
            return (int)(hrs % 24);
        }

        @Test
        public void summerTime() throws Exception
        {
            // see https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=41109
            // NOTE: WET, CET, EET automatically adjust according to the date (summer/winter).
            //       WEST, CEST, EEST do not adjust. They are hard coded to summer offset.
            assertEquals(12, h(parseDateTime("2020/1/1 12:00pm WET")));
            assertEquals(11, h(parseDateTime("2020/6/1 12:00pm WET")));
            assertEquals(11, h(parseDateTime("2020/1/1 12:00pm WEST")));
            assertEquals(11, h(parseDateTime("2020/6/1 12:00pm WEST")));

            assertEquals(11, h(parseDateTime("2020/1/1 12:00pm CET")));
            assertEquals(10, h(parseDateTime("2020/6/1 12:00pm CET")));
            assertEquals(10, h(parseDateTime("2020/1/1 12:00pm CEST")));
            assertEquals(10, h(parseDateTime("2020/6/1 12:00pm CEST")));

            assertEquals(10, h(parseDateTime("2020/1/1 12:00pm EET")));
            assertEquals( 9, h(parseDateTime("2020/6/1 12:00pm EET")));
            assertEquals( 9, h(parseDateTime("2020/1/1 12:00pm EEST")));
            assertEquals( 9, h(parseDateTime("2020/6/1 12:00pm EEST")));

            // dateParse(PATTERN)
            DateParser sdf = getDateParser("yyyy/MM/dd hh:mmaa zzz");
            assertEquals(12, h(sdf.parse("2020/1/1 12:00pm WET").getTime()));
//            assertEquals(11, h(sdf.parse("2020/6/1 12:00pm WET").getTime()));
            assertEquals(11, h(sdf.parse("2020/1/1 12:00pm WEST").getTime()));
            assertEquals(11, h(sdf.parse("2020/6/1 12:00pm WEST").getTime()));

            assertEquals(11, h(sdf.parse("2020/1/1 12:00pm CET").getTime()));
//            assertEquals(10, h(sdf.parse("2020/6/1 12:00pm CET").getTime()));
            assertEquals(10, h(sdf.parse("2020/1/1 12:00pm CEST").getTime()));
            assertEquals(10, h(sdf.parse("2020/6/1 12:00pm CEST").getTime()));

            assertEquals(10, h(sdf.parse("2020/1/1 12:00pm EET").getTime()));
//            assertEquals( 9, h(sdf.parse("2020/6/1 12:00pm EET").getTime()));
            assertEquals( 9, h(sdf.parse("2020/1/1 12:00pm EEST").getTime()));
            assertEquals( 9, h(sdf.parse("2020/6/1 12:00pm EEST").getTime()));
        }

        @Test
        public void testStandardDateTimeFormats()
        {
            STANDARD_DATE_DISPLAY_FORMATS.forEach(dateFormat -> {
                assertTrue(isStandardDateDisplayFormat(dateFormat));
                assertTrue(isStandardDateTimeDisplayFormat(dateFormat));
                assertTrue(isStandardDateTimeDisplayFormat("  " + dateFormat + "  "));
                DateTimeFormat dateSplit = splitDateTimeFormat(dateFormat);
                assertNotNull(dateSplit);
                assertEquals(dateFormat, dateSplit.datePortion);
                assertNull(dateSplit.timePortion);

                STANDARD_TIME_DISPLAY_FORMATS.forEach(timeFormat -> {
                    testGoodDateTimeFormat(dateFormat + " " + timeFormat, dateFormat, timeFormat);
                    testGoodDateTimeFormat("  " + dateFormat + "  " + timeFormat + "  ", dateFormat, timeFormat);
                });
            });

            STANDARD_TIME_DISPLAY_FORMATS.forEach(timeFormat -> assertTrue(isStandardTimeDisplayFormat(timeFormat)));
        }

        private void testGoodDateTimeFormat(String combined, String dateFormat, String timeFormat)
        {
            assertTrue(isStandardDateTimeDisplayFormat(combined));
            DateTimeFormat split = splitDateTimeFormat(combined);
            assertNotNull(split);
            assertEquals(dateFormat, split.datePortion);
            assertEquals(timeFormat, split.timePortion);
        }

        @Test
        public void testNonStandardDateTimeFormats()
        {
            List<String> nonStandardDateFormats = List.of("MM/dd/yyy", "ddMMMyyy", "yyyy.MM.dd", "MMMM dd", "MMMM dd yy", "MMMM dd, yyyy");
            List<String> nonStandardTimeFormats = List.of("kk:mm", "hh:mm aa", "hh:mm");

            nonStandardDateFormats.forEach(this::testBadDateFormat);
            nonStandardTimeFormats.forEach(this::testBadTimeFormat);

            nonStandardDateFormats.forEach(dateFormat -> nonStandardTimeFormats.forEach(timeFormat -> testBadDateTimeFormat(dateFormat + " " + timeFormat)));
            STANDARD_DATE_DISPLAY_FORMATS.forEach(dateFormat -> nonStandardTimeFormats.forEach(timeFormat -> testBadDateTimeFormat(dateFormat + " " + timeFormat)));
            STANDARD_TIME_DISPLAY_FORMATS.forEach(timeFormat -> nonStandardDateFormats.forEach(dateFormat -> testBadDateTimeFormat(dateFormat + " " + timeFormat)));
        }

        private void testBadDateFormat(String dateFormat)
        {
            assertFalse(isStandardDateDisplayFormat(dateFormat));
            assertFalse(isStandardDateTimeDisplayFormat(dateFormat));
            assertNull(splitDateTimeFormat(dateFormat));
        }

        private void testBadTimeFormat(String timeFormat)
        {
            assertFalse(isStandardTimeDisplayFormat(timeFormat));
            assertNull(splitDateTimeFormat(timeFormat));
        }

        private void testBadDateTimeFormat(String dateTimeFormat)
        {
            assertFalse(isStandardDateTimeDisplayFormat(dateTimeFormat));
            assertNull(splitDateTimeFormat(dateTimeFormat));
        }
    }
}

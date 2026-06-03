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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.DateUtil;

import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.stream.Stream;

import static java.util.Calendar.AM_PM;
import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.DAY_OF_WEEK;
import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.MILLISECOND;
import static java.util.Calendar.MINUTE;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.SECOND;
import static java.util.Calendar.YEAR;


public class ParseDateTimeEN implements DateParser
{
    public static final long NANOS_PER_MILLI = 1_000_000;
    public static final long NANOS_PER_SECOND = 1_000_000_000;

    final Locale locale;
    final TimeZone defaultTimeZone;
    final DateUtil.DateTimeOption dateTimeOption;
    final DateUtil.MonthDayOption monthDayOption;
    final boolean lenient;


    public static ParseDateTimeEN getInstance(
        @NotNull Locale locale, @Nullable TimeZone timeZone,
        @NotNull DateUtil.DateTimeOption dateTimeOption, @NotNull DateUtil.MonthDayOption monthDayOption,
        boolean lenient)
    {
        // no need to cache this is just a pass through
        return new ParseDateTimeEN(locale ,timeZone , dateTimeOption , monthDayOption , lenient);
    }


    protected ParseDateTimeEN(
            @NotNull Locale locale, @Nullable TimeZone timeZone,
            @NotNull DateUtil.DateTimeOption dateTimeOption, @NotNull DateUtil.MonthDayOption monthDayOption,
            boolean lenient
    )
    {
        this.locale = locale;
        this.defaultTimeZone = timeZone;
        this.dateTimeOption = dateTimeOption;
        this.monthDayOption = monthDayOption;
        this.lenient = lenient;
    }

    protected Calendar newCalendar()
    {
        return new GregorianCalendar(defaultTimeZone, locale);
    }

    @Override
    public Locale getLocale()
    {
        return locale;
    }

    @Override
    public String getPattern()
    {
        return "";
    }

    @Override
    public TimeZone getTimeZone()
    {
        return defaultTimeZone;
    }

    // assume caller wants to consume/match the entire string
    @Override
    public Date parse(String source) throws ParseException
    {
        var pos = new ParsePosition(0);
        var ret = parse(source, pos);
        if (null == ret )
            throw new ParseException(source, pos.getErrorIndex());
        if (pos.getIndex() != source.length())
            throw new ParseException(source, pos.getIndex());
        return ret;
    }

    @Override
    public Date parse(String source, ParsePosition pos)
    {
        var builder = new CalendarParts();
        var scanned = scan(source, pos, builder);
        if (!scanned)
            return null;
        return dateTimeOption.toJavaDate(builder, lenient);
    }

    @Override
    public boolean parse(String source, ParsePosition pos, Calendar calendar)
    {
        if (!lenient)
            calendar.setLenient(false);
        var parts = new CalendarParts();
        var scanned = scan(source, pos, parts);
        if (!scanned)
            return false;
        dateTimeOption.toCalendar(parts, calendar);
        return true;
    }

    @Override
    public Object parseObject(String source) throws ParseException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object parseObject(String source, ParsePosition pos)
    {
        throw new UnsupportedOperationException();
    }


    /**
     * Javascript style parsing, assumes US locale
     * Copied from RHINO (www.mozilla.org/rhino) and modified
     */

    private static final int currentYear = new GregorianCalendar().get(YEAR);
    private static final int twoDigitCutoff = (currentYear - 80) % 100;
    private static final int defaultCentury = (currentYear - 80) - twoDigitCutoff;

    interface CalendarConstant
    {
        int getField();
        int getValue();
    }

    enum Month implements CalendarConstant
    {
        january,february,march,april,may,june,july,august,september,october,november,december;


        @Override
        public int getField()
        {
            return MONTH;
        }

        @Override
        public int getValue()
        {
            return ordinal();
        }
    }

    enum Weekday implements CalendarConstant
    {
        sunday,monday,tuesday,wednesday,thursday,friday,saturday;

        @Override
        public int getField()
        {
            return DAY_OF_WEEK;
        }

        @Override
        public int getValue()
        {
            return ordinal()+1;
        }
    }

    enum AMPM implements CalendarConstant
    {
        am, pm;

        @Override
        public int getField()
        {
            return Calendar.AM_PM;
        }

        @Override
        public int getValue()
        {
            return ordinal();
        }
    }

    @SuppressWarnings("PointlessArithmeticExpression")
    enum TZ
    {
        z("UTC"),gmt("UTC"),ut("UTC"),utc("UTC"),
        // North America
        est(-5*60),edt(-4*60),cst(-6*60),cdt(-5*60),mst(-7*60),mdt(-6*60),pst(-8*60),pdt(-7*60),
        // Europe
        wet("WET"), cet("CET"), eet("EET"),
        west(+1*60), cest(+2*60), eest(+3*60)
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

    private static final NavigableMap<String, Enum<?>> PARTS_MAP = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    static
    {
        Stream.of(AMPM.values(), Month.values(), Weekday.values(), TZ.values(), ISO.values())
                .flatMap(Arrays::stream)
                .forEach(e -> PARTS_MAP.put(e.name(), e));
    }

    private static @Nullable Enum<?> resolveDatePartEnum(String s)
    {
        // Require an exact match if s is one character long
        if (s.length() < 2)
            return PARTS_MAP.get(s);

        // If s is longer than one character then find first key with s as its prefix
        Map.Entry<String, Enum<?>> entry = PARTS_MAP.ceilingEntry(s);

        return (null != entry && StringUtils.startsWithIgnoreCase(entry.getKey(), s)) ? entry.getValue() : null;
    }

    // this handles a token (no spaces) and returns an Enum|TimeZone
    private static Object resolveDatePart(String s)
    {
        Enum<?> e = resolveDatePartEnum(s);

        if (null != e)
            return e instanceof TZ && (null != ((TZ)e).tz) ? ((TZ)e).tz : e;

        TimeZone tz = TimeZone.getTimeZone(s);
        // getTimeZone() unhelpfully returns GMT if the id is not recognized
        if ("GMT".equals(tz.getID()))
            return null;
        return tz;
    }

    // Coming soon...
//    private static final DateParser timezoneFormat = new getDateParser("zzz");
//
//    private static TimeZone lookingAtTZ(String s, ParsePosition pos)
//    {
//        Calendar c = new _Calendar(null);
//        // I like using
//        int startIndex = pos.getIndex();
//        if (!timezoneFormat.parse(s, pos, c))
//            return null;
//        if (null != c.getTimeZone())
//            return c.getTimeZone();
//        if (c.isSet(Calendar.ZONE_OFFSET))
//            return getTimeZoneForOffsetInMinutes(c.get(Calendar.ZONE_OFFSET) / (60*1_000));
//        return null;
//    }

    private boolean isSpace(char c)
    {
        return ' ' == c || 0x0A0 == c || 0x202F == c;
    }

    // TODO interface DateScanner.scan()
    public boolean scan(String s, ParsePosition pos, CalendarParts parts)
    {
        DateUtil.DateTimeOption option = dateTimeOption;

        Month month = null; // set if month is specified using name

        char c;
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
            if (isSpace(c) || c == ',' || c == '-')
            {
                if (i < limit)
                {
                    char si = s.charAt(i);
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
                    // handle yyyyMMdd
                    if (0 == prevc && 8 == digits)
                    {
                        if (parts.anySet(YEAR, MONTH, Calendar.DAY_OF_MONTH))
                            throw new ConversionException(s);
                        parts.setDayOfMonth(n % 100);
                        n = n / 100;
                        parts.setMonth((n % 100) - 1);
                        n = n / 100;
                        parts.setYear(n);
                        // not sure why we have this range check, but this was what parseYYYYMMDD() did
                        if (parts.getYear() < 1800 || parts.getYear() > 2200)
                            throw new ConversionException("Year out of range from 1800-2200: " + parts.getYear(), null);
                        if (parts.getMonth() < Calendar.JANUARY || parts.getMonth() > Calendar.DECEMBER)
                            throw new ConversionException(s);
                        if (parts.getDayOfMonth() < 1 || parts.getDayOfMonth() > 31)
                            throw new ConversionException(s);
                        break validNum;
                    }
                    if ((prevc == '+' || prevc == '-') && parts.isHourSet() /* && isSet(year) */)
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
                    if (digits > 3 || (n >= 70 && prevc != ':') || ((prevc == '/' || prevc == '-' || prevc == '.') &&
                            parts.isSet(MONTH, Calendar.DAY_OF_MONTH) && !parts.isSet(YEAR)))
                    {
                        if (parts.isSet(YEAR))
                            throw new ConversionException(s);
                        if (c <= ' ' || c == ',' || c == '/' || c == '-' || c == '.' || i >= limit)
                        {
                            if (n >= 100 || digits > 3)
                                parts.setYear(n);
                            else if (n > twoDigitCutoff)
                                parts.setYear(n + defaultCentury);
                            else
                                parts.setYear(n + defaultCentury + 100);
                        }
                        else
                            throw new ConversionException(s);
                        break validNum;
                    }
                    if (c == ':' || (!parts.isHourSet() && option == DateUtil.DateTimeOption.TimeOnly))
                    {
                        if (c == '/' || c == '.')
                            throw new ConversionException(s);
                        else if (!parts.isHourSet())
                        {
                            if (parts.isSet(AM_PM))
                            {
                                if (n > 12)
                                    throw new ConversionException(s);
                                parts.setHour(n == 12 ? 0 : n);
                            }
                            else
                            {
                                parts.setHourOfDay(n);
                            }
                        }
                        else if (!parts.isSet(MINUTE))
                            parts.setMinute(n);
                        else if (!parts.isSet(SECOND))
                            parts.setSeconds(n);
                        else
                            throw new ConversionException(s);
                        break validNum;
                    }
                    // '.' can also be a date separator so check for fractional second before parsing date separator
                    if (c == '.' && parts.isSet(MINUTE) && !parts.isSet(SECOND))
                    {
                        parts.setSeconds(n);
                        i++;
                        int decimalPosition = (int)NANOS_PER_SECOND;    // used to compute nanos
                        double nanos = 0;
                        while (i < limit && '0' <= (c = s.charAt(i)) && c <= '9')
                        {
                            decimalPosition /= 10;
                            nanos += decimalPosition * (c - '0');
                            i++;
                        }
                        parts.setNanoseconds(Math.round(nanos));
                        break validNum;
                    }
                    if (c == '/' || c == '-' || c == '.')
                    {
                        if (c == '/' && option == DateUtil.DateTimeOption.TimeOnly)
                            throw new ConversionException(s);
                        if (monthDayOption == DateUtil.MonthDayOption.MONTH_DAY || parts.isSet(YEAR))
                        {
                            if (!parts.isSet(MONTH))
                                parts.setMonth(n - 1);
                            else if (!parts.isSet(Calendar.DAY_OF_MONTH))
                                parts.setDayOfMonth(n);
                            else
                                throw new ConversionException(s);
                        }
                        else
                        {
                            if (!parts.isSet(Calendar.DAY_OF_MONTH))
                                parts.setDayOfMonth(n);
                            else if (!parts.isSet(MONTH))
                                parts.setMonth(n - 1);
                            else
                                throw new ConversionException(s);
                        }
                        break validNum;
                    }
                    if (i < limit)
                    {
                        if (!parts.isSet(Calendar.DAY_OF_MONTH) && -1 != "jfmasondJFMASOND".indexOf(c))
                        {
                            monthexpected = true;
                        }
                        else if (c > ' ' && !isSpace(c) && -1 == ",-ZTaApP".indexOf(c))
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
                    if (parts.isHourSet() && !parts.isSet(MINUTE))
                    {
                        parts.setMinute(n);
                        break validNum;
                    }
                    if (parts.isSet(MINUTE) && !parts.isSet(SECOND))
                    {
                        parts.setSeconds(n);
                        break validNum;
                    }
                    // handle crazy FCS format hh:mm:ss:jiffy format (e.g. 1/60 sec)
                    // NANOS and MILLIS use same field index
                    if (parts.isSet(SECOND) && !parts.isSet(MILLISECOND) && prevc == ':')
                    {
                        parts.setNanoseconds(Math.round((double)NANOS_PER_SECOND * n / 60.0));
                        break validNum;
                    }
                    if (!parts.isSet(DAY_OF_MONTH))
                    {
                        parts.setDayOfMonth(n);
                        break validNum;
                    }
                    else
                    {
                        throw new ConversionException(s);
                    }
                } // validNum: end of number handling
                prevc = 0;
            }
            else if (c == '/' || c == ':' || c == '+' || c == '.')
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
                if (option != DateUtil.DateTimeOption.TimeOnly && monthexpected && !(dp instanceof Month))
                    throw new ConversionException(s);
                monthexpected = false;
                if (dp == ISO.t)
                {
                    if (parts.isHourSet() || parts.anySet(MINUTE, SECOND))
                        throw new ConversionException(s);
                }
                else if (dp instanceof AMPM ampmValue)
                {
                    parts.set(ampmValue.getField(), ampmValue.getValue());
                    if (parts.isSet(HOUR_OF_DAY))
                    {
                        var hour = parts.getHourOfDay();
                        if (hour > 12 || hour < 0)
                        {
                            throw new ConversionException(s);
                        }
                        parts.setHour(hour==12 ? 0 : hour);
                    }
                }
                else if (dp instanceof Weekday weekday)
                {
                    parts.set(weekday.getField(), weekday.getValue());
                }
                else if (dp instanceof Month)
                {
                    // month
                    if (!parts.isSet(MONTH))
                    {
                        month = (Month)dp;
                        parts.set(month.getField(), month.getValue());
                    }
                    else if (!parts.isSet(DAY_OF_MONTH) && month == null)
                    {
                        // handle 01/Jan/2001 case (strange I know, the customer is always right)
                        month = (Month)dp;
                        parts.setDayOfMonth(parts.getMonth()+1);
                        parts.set(month.getField(), month.getValue());
                    }
                    else
                    {
                        throw new ConversionException(s);
                    }
                    // handle "01Jan2001" or "01 Jan 2001" pretend we're seeing 01/Jan/2001
                    if (i < limit && !parts.isSet(YEAR))
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

        //
        // This part is changed to work with Java
        //

        if (tzoffset != -1 && tz != null)
            throw new ConversionException("ambiguous timezone specification");

        if (tz == null)
        {
            if (tzoffset != -1)
                tz = DateUtil.getTimeZoneForOffsetInMinutes(tzoffset);
        }

        parts.setTimeZone(tz);
        pos.setIndex(i);
        return true;
    }
}

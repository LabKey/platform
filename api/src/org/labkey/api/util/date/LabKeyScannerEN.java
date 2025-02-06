package org.labkey.api.util.date;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.DateUtil;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.NavigableMap;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Javascript style parsing, assumes US locale
 * Copied from RHINO (www.mozilla.org/rhino) and modified
 */

public class LabKeyScannerEN implements DateScanner
{
    final DateUtil.DateTimeOption dateTimeOption;
    final DateUtil.MonthDayOption monthDayOption;

    /**
     * @param dateTimeOption The DateTimeOption might not restrict the date parts that are returned, but
     * it can be used by the parser to disambiguate some input patterns.
     *
     * @param monthDayOption MonthDayOption The MonthDayOption may be used to disambiguate some input patterns.
     */

    LabKeyScannerEN(@NotNull DateUtil.DateTimeOption dateTimeOption, @NotNull DateUtil.MonthDayOption monthDayOption)
    {
        this.dateTimeOption = dateTimeOption;
        this.monthDayOption = monthDayOption;
    }

    public static CalendarParts scan(String s, @NotNull DateUtil.DateTimeOption dateTimeOption, @NotNull DateUtil.MonthDayOption monthDayOption)
    {
        return new LabKeyScannerEN(dateTimeOption, monthDayOption).scan(s);
    }


    private static final int currentYear = new GregorianCalendar().get(Calendar.YEAR);
    private static final int twoDigitCutoff = (currentYear - 80) % 100;
    private static final int defaultCentury = (currentYear - 80) - twoDigitCutoff;

    enum Month
    {
        january(0),february(1),march(2),april(3),may(4),june(5),july(6),august(7),september(8),october(9),november(10),december(11);
        final int month;
        Month(int i)
        {
            month = i;
        }
    }

    enum AMPM
    {
        am, pm
    }

    @SuppressWarnings({"UnaryPlus", "PointlessArithmeticExpression"})
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
        Stream.of(AMPM.values(), Month.values(), DayOfWeek.values(), TZ.values(), ISO.values())
                .flatMap(Arrays::stream)
                .forEach(e -> PARTS_MAP.put(e.name(), e));
    }

    private static @Nullable Enum<?> resolveDatePartEnum(String s)
    {
        // Require an exact match if s is one character long
        if (s.length() < 2)
            return PARTS_MAP.get(s);

        // If s is longer than one character then find first key with s as its prefix
        var entry = PARTS_MAP.ceilingEntry(s);

        return (null != entry && StringUtils.startsWithIgnoreCase(entry.getKey(), s)) ? entry.getValue() : null;
    }

    private static Object resolveDatePart(String s)
    {
        var e = resolveDatePartEnum(s);

        if (null != e)
            return e instanceof TZ && (null != ((TZ)e).tz) ? ((TZ)e).tz : e;

        TimeZone tz = TimeZone.getTimeZone(s);
        // getTimeZone() unhelpfully returns GMT if the id is not recognized
        if ("GMT".equals(tz.getID()))
            return null;
        return tz;
    }


    @Override
    public @NotNull CalendarParts scan(String s) throws ConversionException
    {
        Month month = null; // set if month is specified using name
        int year = -1;
        int mon = -1;
        int mday = -1;
        DayOfWeek weekday = null;
        int hour = -1;
        int min = -1;
        int sec = -1;
        double nanos = -1;
        int decimalPosition = 1_000_000_000;    // used to compute nanos
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
                    if (digits > 3 || (n >= 70 && prevc != ':') || ((prevc == '/' || prevc == '-' || prevc == '.') && mon >= 0 && mday >= 0 && year < 0))
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
                    if (c == ':' || (hour < 0 && dateTimeOption == DateUtil.DateTimeOption.TimeOnly))
                    {
                        if (c == '/' || c == '.')
                            throw new ConversionException(s);
                        else if (hour < 0)
                            hour = n;
                        else if (min < 0)
                            min = n;
                        else if (sec < 0)
                            sec = n;
                        else
                            throw new ConversionException(s);
                        break validNum;
                    }
                    // '.' can also be a date separator so check for fractional second before parsing date separator
                    if (c == '.' && min >= 0 && sec < 0)
                    {
                        sec = n;
                        i++;
                        nanos = 0;
                        while (i < limit && '0' <= (c = s.charAt(i)) && c <= '9')
                        {
                            decimalPosition /= 10;
                            nanos += decimalPosition * (c - '0');
                            i++;
                        }
                        break validNum;
                    }
                    if (c == '/' || c == '-' || c == '.')
                    {
                        if (c == '/' && dateTimeOption == DateUtil.DateTimeOption.TimeOnly)
                            throw new ConversionException(s);
                        if (monthDayOption == DateUtil.MonthDayOption.MONTH_DAY || year >= 0)
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
                    // handle crazy FCS format hh:mm:ss:jiffy format (e.g. 1/60 sec)
                    if (sec >= 0 && nanos < 0 && prevc == ':')
                    {
                        nanos = 1_000_000_000.0 * n / 60;
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
                if (dateTimeOption != DateUtil.DateTimeOption.TimeOnly && monthexpected && !(dp instanceof Month))
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
                else if (dp instanceof DayOfWeek)
                {
                    weekday = (DayOfWeek) dp;
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

        return new CalendarParts(s, tz, -1==tzoffset?null:tzoffset, year, mon, mday, weekday, hour, min, sec, (long)nanos);
    }
}

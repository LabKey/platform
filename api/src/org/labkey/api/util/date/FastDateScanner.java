package org.labkey.api.util.date;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.time.DateParser;
import org.apache.commons.lang3.time.FastDateFormat;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.text.ParsePosition;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

public class FastDateScanner implements DateScanner
{
    final DateParser parser;

    public FastDateScanner(String pattern)
    {
        parser = FastDateFormat.getInstance(pattern, null, null);
    }

    @Override
    public @NotNull CalendarParts scan(String source) throws ConversionException
    {
        var pos = new ParsePosition(0);
        Calendar calendar = new NoopCalendar();
        try
        {// _Calendar is faster to construct than GregorianCalendar
            parser.parse(source, pos, calendar);
            return CalendarParts.from(source, calendar);
        }
        catch (IllegalArgumentException e)
        {
            throw new ConversionException(e.getMessage(), e);
        }
    }


    private static class NoopCalendar extends GregorianCalendar
    {
        NoopCalendar()
        {
            super(null, Locale.getDefault());
        }
        @Override
        public void setTimeInMillis(long millis)
        {
        }
        @Override
        protected void complete()
        {
        }
    }


    public static class TestCase extends Assert
    {
        @Test
        public void testHour()
        {
            CalendarParts parts;
            // HOUR
            parts = new FastDateScanner("kk:mm").scan("00:00");
            assertEquals(0, parts.hour());
            parts = new FastDateScanner("kk:mm").scan("12:00");
            assertEquals(12, parts.hour());
            parts = new FastDateScanner("hh:mm aa").scan("12:00 am");
            assertEquals(0, parts.hour());
            parts = new FastDateScanner("hh:mm aa").scan("12:00 pm");
            assertEquals(12, parts.hour());
        }

        @Test
        public void testTzOffset()
        {
            CalendarParts parts = CalendarParts.from("*", new NoopCalendar());
            assertNull(parts.tz());
            assertNull(parts.tzoffsetMinutes());

            parts = new FastDateScanner("kk:mm z").scan("12:00 Pacific Standard Time");
            assertNull(parts.tz());
            assertEquals(-480, (int)parts.tzoffsetMinutes());
            parts = new FastDateScanner("kk:mm z").scan("12:00 PST");
            assertNull(parts.tz());
            assertEquals(-480, (int)parts.tzoffsetMinutes());
            parts = new FastDateScanner("kk:mm z").scan("12:00 GMT-08:00");
            assertNotNull(parts.tz());
            assertNull(parts.tzoffsetMinutes());

            parts = new FastDateScanner("kk:mm Z").scan("12:00 -0800");
            assertNotNull(parts.tz());
            assertNull(parts.tzoffsetMinutes());

            parts = new FastDateScanner("kk:mm X").scan("12:00 -08");
            assertNotNull(parts.tz());
            assertNull(parts.tzoffsetMinutes());
            parts = new FastDateScanner("kk:mm X").scan("12:00 -0800");
            assertNotNull(parts.tz());
            assertNull(parts.tzoffsetMinutes());
            parts = new FastDateScanner("kk:mm X").scan("12:00 -08:00");
            assertNotNull(parts.tz());
            assertNull(parts.tzoffsetMinutes());
        }
    }
}

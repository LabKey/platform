/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.query.suggestions;

import org.apache.commons.beanutils.ConversionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.DateUtil.MonthDayOption;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A grid search term parsed into the forms the suggestion tiers need. */
public class SuggestionTerm
{
    public enum Precision { DAY, MONTH, YEAR }

    /** @param end inclusive */
    public record DateSpan(@NotNull LocalDate start, @NotNull LocalDate end, @NotNull Precision precision) {}

    private static final Pattern NUMBER_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    private static final Pattern LEADING_ZERO_PATTERN = Pattern.compile("^-?0\\d");
    private static final Pattern YEAR_PATTERN = Pattern.compile("^(\\d{4})$");
    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("^(\\d{4})[-/](\\d{1,2})$");
    private static final Pattern MONTH_NAME_YEAR_PATTERN = Pattern.compile("^([A-Za-z]{3,9})\\.?\\s+(\\d{4})$");
    // DateUtil parses bare times as 1970-01-01, and a leading "-" as a separator
    private static final Pattern DATE_CANDIDATE_PATTERN = Pattern.compile("^[A-Za-z0-9](?=.*\\d)(?=.*[-/., ]).*$");
    private static final Pattern TIME_ONLY_PATTERN = Pattern.compile("^\\d{1,2}:\\d{2}");
    // Stripped so an explicit zone can't shift the date when DateUtil converts to the server's zone
    private static final Pattern TRAILING_TIME_PATTERN = Pattern.compile("[ T]\\d{1,2}:\\d{2}(?::\\d{2}(?:\\.\\d+)?)?(?:\\s?[AaPp][Mm])?(?:Z|[+-]\\d{2}:?\\d{2})?$");
    private static final List<String> MONTH_NAMES = List.of("january", "february", "march", "april", "may", "june", "july",
        "august", "september", "october", "november", "december");

    private final String _raw;
    private final @Nullable BigDecimal _number;
    private final boolean _integer;
    private final boolean _leadingZero;
    private final @Nullable DateSpan _dateSpan;

    private SuggestionTerm(String raw, @Nullable BigDecimal number, boolean integer, boolean leadingZero, @Nullable DateSpan dateSpan)
    {
        _raw = raw;
        _number = number;
        _integer = integer;
        _leadingZero = leadingZero;
        _dateSpan = dateSpan;
    }

    /** Full dates follow the site's month/day parsing setting. */
    public static @NotNull SuggestionTerm parse(@Nullable String term)
    {
        return parse(term, DateUtil::parseDateTime);
    }

    public static @NotNull SuggestionTerm parse(@Nullable String term, @NotNull MonthDayOption monthDay)
    {
        return parse(term, s -> DateUtil.parseDateTime(s, monthDay, true, null));
    }

    private static @NotNull SuggestionTerm parse(@Nullable String term, @NotNull ToLongFunction<String> dateTimeParser)
    {
        String raw = term == null ? "" : term.trim();
        if (raw.isEmpty())
            return new SuggestionTerm(raw, null, false, false, null);

        BigDecimal number = null;
        boolean integer = false;
        boolean leadingZero = false;
        if (NUMBER_PATTERN.matcher(raw).matches())
        {
            number = new BigDecimal(raw);
            integer = !raw.contains(".");
            leadingZero = LEADING_ZERO_PATTERN.matcher(raw).lookingAt();
        }

        return new SuggestionTerm(raw, number, integer, leadingZero, parseDateSpan(raw, number != null, dateTimeParser));
    }

    private static @Nullable DateSpan parseDateSpan(String raw, boolean isNumber, ToLongFunction<String> dateTimeParser)
    {
        Matcher m = YEAR_MONTH_PATTERN.matcher(raw);
        if (m.matches())
            return monthSpan(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));

        m = MONTH_NAME_YEAR_PATTERN.matcher(raw);
        if (m.matches())
        {
            int month = monthFromName(m.group(1));
            return month > 0 ? monthSpan(Integer.parseInt(m.group(2)), month) : null;
        }

        m = YEAR_PATTERN.matcher(raw);
        if (m.matches())
        {
            int year = Integer.parseInt(m.group(1));
            return new DateSpan(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), Precision.YEAR);
        }

        if (isNumber || TIME_ONLY_PATTERN.matcher(raw).lookingAt() || !DATE_CANDIDATE_PATTERN.matcher(raw).matches())
            return null;

        String datePart = TRAILING_TIME_PATTERN.matcher(raw).replaceFirst("");
        if (datePart.isEmpty())
            return null;

        try
        {
            LocalDate date = Instant.ofEpochMilli(dateTimeParser.applyAsLong(datePart)).atZone(ZoneId.systemDefault()).toLocalDate();
            return new DateSpan(date, date, Precision.DAY);
        }
        catch (ConversionException | IllegalArgumentException e)
        {
            return null;
        }
    }

    private static @Nullable DateSpan monthSpan(int year, int month)
    {
        if (month < 1 || month > 12)
            return null;
        YearMonth yearMonth = YearMonth.of(year, month);
        return new DateSpan(yearMonth.atDay(1), yearMonth.atEndOfMonth(), Precision.MONTH);
    }

    private static int monthFromName(String name)
    {
        String lc = name.toLowerCase(Locale.ROOT);
        for (int i = 0; i < MONTH_NAMES.size(); i++)
        {
            String month = MONTH_NAMES.get(i);
            if (month.equals(lc) || (lc.length() == 3 && month.startsWith(lc)))
                return i + 1;
        }
        return 0;
    }

    public @NotNull String getRaw()
    {
        return _raw;
    }

    public boolean isBlank()
    {
        return _raw.isEmpty();
    }

    public @Nullable BigDecimal getNumber()
    {
        return _number;
    }

    public boolean isInteger()
    {
        return _integer;
    }

    /** Multi-digit integers with a leading zero (e.g. barcodes) are identifiers, not quantities. */
    public boolean hasLeadingZero()
    {
        return _leadingZero;
    }

    public @Nullable DateSpan getDateSpan()
    {
        return _dateSpan;
    }

    public static class TestCase extends Assert
    {
        private static SuggestionTerm parse(String term)
        {
            return SuggestionTerm.parse(term, MonthDayOption.MONTH_DAY);
        }

        private static DateSpan span(String start, String end, Precision precision)
        {
            return new DateSpan(LocalDate.parse(start), LocalDate.parse(end), precision);
        }

        private static DateSpan day(String date)
        {
            return span(date, date, Precision.DAY);
        }

        private static void assertNumber(String term, String expected, boolean integer, boolean leadingZero)
        {
            SuggestionTerm parsed = parse(term);
            assertNotNull(term, parsed.getNumber());
            assertEquals(term, 0, new BigDecimal(expected).compareTo(parsed.getNumber()));
            assertEquals(term, integer, parsed.isInteger());
            assertEquals(term, leadingZero, parsed.hasLeadingZero());
        }

        @Test
        public void testText()
        {
            SuggestionTerm term = parse("  Sodium  ");
            assertEquals("Sodium", term.getRaw());
            assertNull(term.getNumber());
            assertNull(term.getDateSpan());
            assertFalse(term.isInteger());

            assertTrue(parse(null).isBlank());
            assertTrue(parse("   ").isBlank());
        }

        @Test
        public void testNumbers()
        {
            assertNumber("3", "3", true, false);
            assertNumber("-12", "-12", true, false);
            assertNumber("86.76", "86.76", false, false);
            assertNumber("0.5", "0.5", false, false);
            assertNumber("005004441", "5004441", true, true);
            assertNull(parse("1,000").getNumber());
            assertNull(parse("1e5").getNumber());
        }

        @Test
        public void testYearIsAlsoNumber()
        {
            SuggestionTerm term = parse("2023");
            assertEquals(0, new BigDecimal(2023).compareTo(term.getNumber()));
            assertEquals(span("2023-01-01", "2023-12-31", Precision.YEAR), term.getDateSpan());
        }

        @Test
        public void testFullDates()
        {
            DateSpan day = day("2019-02-03");
            assertEquals(day, parse("2019-02-03").getDateSpan());
            assertEquals(day, parse("2019/2/3").getDateSpan());
            assertEquals(day, parse("2019-02-03 11:38").getDateSpan());
            assertEquals(day, parse("2019-02-03T11:38:05.123").getDateSpan());
            assertEquals(day, parse("2019-02-03T23:30:00Z").getDateSpan());
            assertEquals(day("2023-02-03"), parse("Feb 3, 2023").getDateSpan());
        }

        @Test
        public void testInvalidDaysAndMonths()
        {
            assertNull(parse("2023-02-29").getDateSpan());
            assertEquals(day("2024-02-29"), parse("2024-02-29").getDateSpan());
            assertNull(parse("2023-13").getDateSpan());
            assertNull(parse("2023-00-10").getDateSpan());
        }

        @Test
        public void testMonths()
        {
            DateSpan feb = span("2024-02-01", "2024-02-29", Precision.MONTH);
            assertEquals(feb, parse("2024-02").getDateSpan());
            assertEquals(feb, parse("Feb 2024").getDateSpan());
            assertEquals(feb, parse("february 2024").getDateSpan());
            assertEquals(feb, parse("Feb. 2024").getDateSpan());
            assertNull(parse("Febr 2024").getDateSpan());
        }

        @Test
        public void testSlashDatesFollowMonthDayOption()
        {
            assertEquals(day("2023-03-04"), SuggestionTerm.parse("03/04/2023", MonthDayOption.MONTH_DAY).getDateSpan());
            assertEquals(day("2023-04-03"), SuggestionTerm.parse("03/04/2023", MonthDayOption.DAY_MONTH).getDateSpan());
            assertNull(SuggestionTerm.parse("31/12/2023", MonthDayOption.MONTH_DAY).getDateSpan());
        }

        @Test
        public void testNotDates()
        {
            for (String term : List.of("3", "20", "86.76", "005004441", "asdf", "PL-2023", "PL-5000000-1",
                "SUBJ-028504", "last 7 days", "A+", "Sodium Citrate", "11:38", "15:02:00.0000000", "-5.5.5", "1/2"))
            {
                assertNull(term, parse(term).getDateSpan());
            }
        }
    }
}

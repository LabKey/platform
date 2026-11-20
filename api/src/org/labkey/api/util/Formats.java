/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.settings.FolderSettingsCache;

import java.text.DecimalFormat;
import java.util.stream.IntStream;

public class Formats
{
    public static final SafeDecimalFormat f0 = new SafeDecimalFormat("0");
    public static final SafeDecimalFormat f1 = new SafeDecimalFormat("0.0");
    public static final SafeDecimalFormat f2 = new SafeDecimalFormat("0.00");
    public static final SafeDecimalFormat fv2 = new SafeDecimalFormat("#.##");
    public static final SafeDecimalFormat f3 = new SafeDecimalFormat("0.000");
    public static final SafeDecimalFormat fv3 = new SafeDecimalFormat("#.###");
    public static final SafeDecimalFormat f4 = new SafeDecimalFormat("0.0000");
    public static final SafeDecimalFormat fv4 = new SafeDecimalFormat("#.####");
    public static final SafeDecimalFormat signf4 = new SafeDecimalFormat("+0.0000;-0.0000");
    public static final SafeDecimalFormat percent = new SafeDecimalFormat("0%");
    public static final SafeDecimalFormat percent1 = new SafeDecimalFormat("0.0%");
    public static final SafeDecimalFormat percent2 = new SafeDecimalFormat("0.00%");
    public static final SafeDecimalFormat commaf0 = new SafeDecimalFormat("#,##0");
    public static final SafeDecimalFormat commaf2 = new SafeDecimalFormat("#,##0.##");
    public static final SafeDecimalFormat commaf3 = new SafeDecimalFormat("#,##0.###");

    /** Thread-safe wrapper around a shared DecimalFormat; synchronizes since callers are low-contention display and logging code */
    public static final class SafeDecimalFormat
    {
        private final DecimalFormat _format;

        public SafeDecimalFormat(String pattern)
        {
            _format = new DecimalFormat(pattern);
        }

        /** Returns a new, unshared instance for the few code paths that require a DecimalFormat */
        public DecimalFormat createCopy()
        {
            synchronized (_format)
            {
                return (DecimalFormat) _format.clone();
            }
        }

        public String format(double d)
        {
            synchronized (_format)
            {
                return _format.format(d);
            }
        }

        public String format(long l)
        {
            synchronized (_format)
            {
                return _format.format(l);
            }
        }

        public String format(Object o)
        {
            synchronized (_format)
            {
                return _format.format(o);
            }
        }
    }

    public static String getDecimalFormatDocumentationURL()
    {
        return HelpTopic.getJDKJavaDocLink(DecimalFormat.class);
    }

    /** Get the default number format string to use in this Container */
    public static String getNumberFormatString(Container c)
    {
        return FolderSettingsCache.getDefaultNumberFormat(c);
    }

    /** Format number using folder-specified default pattern */
    public static String formatNumber(Container c, Number n)
    {
        String formatString = getNumberFormatString(c);

        if (null != formatString)
            return new DecimalFormat(formatString).format(n);
        else
            return n.toString();
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testConcurrentFormat()
        {
            // commaf3's pattern takes DecimalFormat's unsynchronized double fast path; most other patterns lock internally, so they can't detect a missing lock
            DecimalFormat reference = commaf3.createCopy();
            String[] expected = IntStream.range(0, 1_000_000).mapToObj(i -> reference.format(i + 0.25)).toArray(String[]::new);

            long mismatches = IntStream.range(0, expected.length).parallel()
                .filter(i -> !commaf3.format(i + 0.25).equals(expected[i]))
                .count();

            assertEquals("Concurrent calls produced wrong results", 0, mismatches);
        }

        @Test
        public void testGetReturnsUnsharedInstance()
        {
            assertNotSame(f2.createCopy(), f2.createCopy());
            assertEquals(f2.format(1.5), f2.createCopy().format(1.5));
        }
    }
}

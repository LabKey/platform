/*
 * Copyright (c) 2009-2026 LabKey Corporation
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

/*
* User: Dave
* Date: Mar 4, 2009
* Time: 11:41:14 AM
*/

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;

import java.io.Serializable;
import java.util.Objects;

import static org.labkey.api.util.IntegerUtils.asIntegerElseNull;

/**
 * Parses a string-based version number into major, minor and revision numbers
 * based on the format "major.minor.revision". Revision can be either an integer
 * or a string.
 */
public class VersionNumber implements Serializable, Comparable<VersionNumber>
{
    private final int _major;
    private int _minor = 0;
    private Object _revision = null;

    public VersionNumber(String version)
    {
        if (null == version || version.isEmpty())
            throw new RuntimeException("Null or empty version number string!");

        String[] versionParts = version.split("\\.");

        if (versionParts.length == 0)
            throw new RuntimeException("Invalid version number string ('" + version + "')");

        _major = Integer.parseInt(versionParts[0]);

        if (versionParts.length > 1)
        {
            // Lenient int parser that allows non-digit characters after the number portion.  This fixes PostgreSQL 8.4 Beta 1
            //  which returns "4beta1" for minor version. 
            String minorString = versionParts[1];
            int i;

            for (i = 0; i < minorString.length(); i++)
                if (!Character.isDigit(minorString.charAt(i)))
                    break;

            _minor = Integer.parseInt(versionParts[1].substring(0, i));
        }

        if (versionParts.length > 2)
        {
            //try to parse as int, otherwise set as string
            try
            {
                _revision = Integer.valueOf(versionParts[2]);
            }
            catch(NumberFormatException e)
            {
                _revision = versionParts[2];
            }
        }
    }

    public VersionNumber(int major, int minor)
    {
        this(major, minor, null);
    }

    public VersionNumber(int major, int minor, int revision)
    {
        _major = major;
        _minor = minor;
        _revision = revision;
    }

    public VersionNumber(int major, int minor, String revision)
    {
        _major = major;
        _minor = minor;
        _revision = revision;
    }

    public int getMajor()
    {
        return _major;
    }

    public int getMinor()
    {
        return _minor;
    }

    public Object getRevision()
    {
        return _revision;
    }

    public int getRevisionAsInt()
    {
        if (asIntegerElseNull(_revision) instanceof Integer i)
            return i;
        else
            return 0;
    }

    @Override
    public String toString()
    {
        if (null != _revision)
            return _major + "." + _minor + "." + _revision;
        else
            return _major + "." + _minor;
    }

    /**
     * Packs major and minor version into an int that is easy to range check, ignoring revision: {@code major * 10 + minor}.
     * For example, 8.3 gives 83, 8.4 gives 84, and 9.0 gives 90.
     * <p>
     * Caveat: a minor version above 9 is divided by ten first, so 26.11 and 26.1 both give 261. Use
     * {@link #compareTo(VersionNumber)} to order versions whose minor may reach two digits.
     *
     * @throws IllegalStateException if the minor version is still outside 0-9 after that adjustment
     */
    public int getVersionInt()
    {
        // Temporary fix for SQL Server 2008 R2 (10.50.1600.1).  TODO: Support two-digit version ints (MMmm)
        int minor = _minor > 9 ? _minor / 10 : _minor;
        if (minor > 9 || minor < 0)
            throw new IllegalStateException("Bad minor version: " + minor);

        return _major * 10 + minor;
    }

    /**
     * Orders by major, then minor, then revision, so unlike {@link #getVersionInt()} this is safe for minor versions
     * above 9. A missing revision sorts first, which puts "26.9-SNAPSHOT" (parsed as 26.9 with no revision) ahead of
     * the released "26.9.0". A non-numeric revision sorts last.
     * <p>
     * Not consistent with equals(), which this class does not override.
     */
    @Override
    public int compareTo(@NotNull VersionNumber o)
    {
        int result = Integer.compare(_major, o._major);
        if (result != 0)
            return result;

        result = Integer.compare(_minor, o._minor);
        if (result != 0)
            return result;

        return compareRevisions(_revision, o._revision);
    }

    private static int compareRevisions(@Nullable Object revision1, @Nullable Object revision2)
    {
        if (Objects.equals(revision1, revision2))
            return 0;
        if (null == revision1)
            return -1;
        if (null == revision2)
            return 1;
        // Non-numeric revisions, e.g. PostgreSQL's "4beta1", all sort after the numeric ones. Comparing them lexically
        // against numbers instead would be intransitive: 2 > "1x" > 10 > 2.
        if (revision1 instanceof Integer int1)
            return revision2 instanceof Integer int2 ? Integer.compare(int1, int2) : -1;
        if (revision2 instanceof Integer)
            return 1;

        return revision1.toString().compareTo(revision2.toString());
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testOrdering()
        {
            assertBefore("26.7.1", "26.9.0");
            assertBefore("26.9.0", "26.9.1");
            assertBefore("25.11.0", "26.3.0");
            assertBefore("26.9", "26.9.0");
            assertBefore("26.9-SNAPSHOT", "26.9.0");

            assertEquals(0, new VersionNumber("26.9.0").compareTo(new VersionNumber("26.9.0")));
            assertEquals(0, new VersionNumber("26.9-SNAPSHOT").compareTo(new VersionNumber("26.9-SNAPSHOT")));
        }

        /** getVersionInt() maps both to 261, so ordering must not be built on it */
        @Test
        public void testDoubleDigitMinor()
        {
            assertBefore("26.1.0", "26.11.0");
            assertEquals(261, new VersionNumber("26.1").getVersionInt());
            assertEquals(261, new VersionNumber("26.11").getVersionInt());
        }

        /** Lexical comparison against numeric revisions would be intransitive, so non-numeric revisions sort last */
        @Test
        public void testNonNumericRevision()
        {
            assertBefore("8.4.2", "8.4.4beta1");
            assertBefore("8.4.10", "8.4.1x");
            assertBefore("8.4.4beta1", "8.4.4rc1");
            assertEquals(0, new VersionNumber("8.4.4beta1").compareTo(new VersionNumber("8.4.4beta1")));
        }

        @Test
        public void testGetVersionIntDoesNotMutate()
        {
            VersionNumber version = new VersionNumber("10.50.1600");
            assertEquals(105, version.getVersionInt());
            assertEquals(50, version.getMinor());
            assertEquals(105, version.getVersionInt());
        }

        private void assertBefore(String earlier, String later)
        {
            VersionNumber first = new VersionNumber(earlier);
            VersionNumber second = new VersionNumber(later);
            assertTrue(earlier + " should sort before " + later, first.compareTo(second) < 0);
            assertTrue(later + " should sort after " + earlier, second.compareTo(first) > 0);
        }
    }
}

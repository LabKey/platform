/*
 * Copyright (c) 2010-2019 LabKey Corporation
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import static java.lang.Math.min;

/**
 * User: adam
 * Date: Mar 7, 2010
 * Time: 6:20:24 PM
 */
public class StringUtilsLabKey
{
    /** Instead of relying on the platform default character encoding, use this Charset */
    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    private static final Random RANDOM = new Random();
    private static final int MAX_LONG_LENGTH = String.valueOf(Long.MAX_VALUE).length() - 1;

    // Finds the longest common prefix present in all elements of the passed in string collection. In other words,
    // the longest string (prefix) such that, for all s in strings, s.startsWith(prefix). An empty collection returns
    // the empty string and a single element collection returns that string.
    public static String findCommonPrefix(@NotNull Collection<String> strings)
    {
        if (strings.isEmpty())
            return "";

        List<String> list = new ArrayList<>(strings);

        if (strings.size() == 1)
            return list.get(0);

        Collections.sort(list);
        String first = list.get(0);
        String last = list.get(list.size() - 1);
        int i = 0;

        while (i < Math.min(first.length(), last.length()) && first.charAt(i) == last.charAt(i))
        {
            i++;
        }

        return first.substring(0, i);
    }

    /**
     * Generate a String of random digits of the specified length. Will not have leading zeros
     */
    public static String getUniquifier(int length)
    {
        if (length <= 0)
        {
            return "";
        }
        return (RANDOM.nextInt(9) + 1) + getPaddedUniquifier(length - 1);
    }

    /**
     * Generate a String of random digits of the specified length. May contain leading zeros
     */
    public static String getPaddedUniquifier(int length)
    {
        StringBuilder builder = new StringBuilder(length);
        int chunkLength = MAX_LONG_LENGTH;
        long maxValue = Double.valueOf(Math.pow(10, MAX_LONG_LENGTH)).longValue();
        while (length > 0)
        {
            if (length > MAX_LONG_LENGTH)
            {
                length -= MAX_LONG_LENGTH;
            }
            else
            {
                chunkLength = length;
                maxValue = Double.valueOf(Math.pow(10, chunkLength)).longValue();
                length = 0;
            }
            String unpadded = String.valueOf(Math.abs(RANDOM.nextLong()) % maxValue);
            builder.append(StringUtils.repeat('0', chunkLength - unpadded.length()));
            builder.append(unpadded);
        }
        return builder.toString();
    }

    // Joins provided strings, separating with separator but skipping any strings that are null, blank, or all whitespace.
    public static String joinNonBlank(String separator, String... stringsToJoin)
    {
        StringBuilder sb = new StringBuilder();
        String sep = "";

        for (String s : stringsToJoin)
        {
            if (StringUtils.isNotBlank(s))
            {
                sb.append(sep);
                sb.append(s);
                sep = separator;
            }
        }

        return sb.toString();
    }

    /** Recognizes strings that start with http://, https://, ftp://, or mailto: */
    private static final String[] URL_PREFIXES = {"http://", "https://", "ftp://", "mailto:"};

    public static boolean startsWithURL(CharSequence s)
    {
        if (s != null)
        {
            for (String prefix : URL_PREFIXES)
                if (StringUtils.startsWithIgnoreCase(s, prefix))
                    return true;
        }

        return false;
    }

    // Does the string have ANY upper-case letters?
    public static boolean containsUpperCase(String s)
    {
        for (char ch : s.toCharArray())
            if (Character.isUpperCase(ch))
                return true;

        return false;
    }


    public static boolean isText(String s)
    {
        for (char c : s.toCharArray())
        {
            if (c <= 32)
            {
                if (Character.isWhitespace(c))
                    continue;
            }
            else if (c < 127)
            {
                continue;
            }
            else if (c == 127)
            {
                // DEL??
                return false;
            }
            else if (Character.isValidCodePoint(c))
            {
                continue;
            }
            return false;
        }
        return true;
    }


    /**
     * <p>Replaces all occurrences of a String within another String, ignoring case.</p>
     *
     * @return String with replacements
     */
    public static String replaceIgnoreCase(final String text, final String searchString, final String replacement)
    {
        return text.replaceAll("(?i)" + Pattern.quote(searchString), replacement);
    }


    /**
     * <p>Replaces first occurrence of a String within another String, ignoring case.</p>
     *
     * @return String with replacements
     */
    public static String replaceFirstIgnoreCase(final String text, final String searchString, final String replacement)
    {
        return text.replaceFirst("(?i)" + Pattern.quote(searchString), replacement);
    }


    // Outputs a formatted count and a noun that's pluralized (by simply adding "s")
    public static String pluralize(long count, String singular)
    {
        return pluralize(count, singular, singular + "s");
    }


    // Outputs a formatted count and a noun that's pluralized (outputting the plural parameter if appropriate)
    public static String pluralize(long count, String singular, String plural)
    {
        return Formats.commaf0.format(count) + " " + (1 == count ? singular : plural);
    }

    // splits strings at camel case boundaries and then joins back together without expanding the number of spaces
    // Splits only on word characters.  Multiple spaces are collapsed into a single space.
    public static String splitCamelCase(String s)
    {
        List<String> stringList = new ArrayList<>();
        boolean appending = false;
        boolean hasSpace = false;
        for (String part : StringUtils.splitByCharacterTypeCamelCase(s))
        {
            if (!StringUtils.isBlank(part))
            {
                if (part.matches("\\p{Alnum}+"))
                {
                    if (appending)
                    {
                        int lastIndex = stringList.size()-1;
                        stringList.set(lastIndex, stringList.get(lastIndex) + part);
                        appending = false;
                    }
                    else
                        stringList.add(part);
                }
                else
                {
                    appending = true;
                    int lastIndex = stringList.size()-1;
                    if (lastIndex < 0 || hasSpace)
                        stringList.add(part);
                    else
                    {
                        stringList.set(lastIndex, stringList.get(lastIndex) + part);
                    }
                }
                hasSpace = false;
            }
            else
            {
                hasSpace = true;
                appending = false;
            }
        }
        return StringUtils.join(stringList, " ");
    }

    public static int toInt(Object value)
    {
        if (null == value)
            return 0;
        else if (String.class.isInstance(value))
            return Integer.valueOf((String) value);
        else if (Number.class.isInstance(value))
            return ((Number) value).intValue();

        throw new IllegalArgumentException("Unable to get int value for value parameter");
    }

    // Domain names can contain only ASCII alphanumeric characters and dashes and may not start or end with a dash.
    // Each domain name can be at most 63 characters.
    public static Pattern domainNamePattern = Pattern.compile("(?!-)[A-Za-z0-9-]{0,62}[A-Za-z0-9]$");

    public static boolean isValidDomainName(String name)
    {
        return !StringUtils.isEmpty(name) && domainNamePattern.matcher(name).matches();
    }

    /**
     * Given a name, transforms it into a valid domain for an internet address, if possible, according to the constraints
     * specified here: https://tools.ietf.org/html/rfc1035
     * @param name the name to be transformed.
     * @return null if the given string contains no characters that can be transformed in the order given to make a valid domain name ; a string containing only alphanumeric characters and dashes
     * that does not start with a dash or
     */
    public static String getDomainName(String name)
    {
        if (StringUtils.isEmpty(name))
            return null;
        // decompose non-ASCII characters into component characters.
        String normalizedName = Normalizer.normalize(name.trim().toLowerCase(), Normalizer.Form.NFD);
        // replaces spaces with dashes and remove all characters that are not alpanumeric or a dash
        normalizedName = normalizedName.replaceAll(" ", "-").replaceAll("[^A-Za-z0-9-]", "");
        int start = 0;
        int end = min(63,normalizedName.length()); // a sub-domain can be at most 63 characters in length
        while (start < end && normalizedName.charAt(start) == '-')
            start++;
        while (end > start && normalizedName.charAt(end-1) == '-')
            end--;
        if (end-start == 0)
            return null;
        if (start > 0 || end < normalizedName.length())
            return normalizedName.substring(start, end);
        else
            return normalizedName;
    }

    /**
     * See description below. This version won't truncate the values.
     */
    public static @NotNull <V> String getMapDifference(@Nullable Map<String, V> oldMap, @Nullable Map<String, V> newMap)
    {
        return getMapDifference(oldMap, newMap, Integer.MAX_VALUE);
    }

    /**
     * Compares two maps of name:value pairs and generates a string that documents the entries that have changed
     * (added, removed, or updated). Useful for audit logging of settings changes. A few examples of output:
     *
     *    enabled: true » false
     *    description: My Configuration » CAS Configuration, enabled: true » false
     *    serverUrl: » http://localhost:8080/labkey/cas, description: » CAS localhost, autoRedirect: » true
     *
     * Null can be passed for either map, in which case an empty map is substituted.
     */
    public static @NotNull <V> String getMapDifference(@Nullable Map<String, V> oldMap, @Nullable Map<String, V> newMap, int truncateLength)
    {
        oldMap = null == oldMap ? Collections.emptyMap() : oldMap;
        newMap = null == newMap ? Collections.emptyMap() : newMap;

        MapDifference<String, Object> difference = Maps.difference(oldMap, newMap);

        List<String> list = new LinkedList<>();

        difference.entriesOnlyOnLeft().entrySet().stream()
            .map(e->e.getKey() + ": " + truncate(e.getValue(), truncateLength) + " » ")
            .forEach(list::add);

        difference.entriesOnlyOnRight().entrySet().stream()
            .map(e->e.getKey() + ": » " + truncate(e.getValue(), truncateLength))
            .forEach(list::add);

        difference.entriesDiffering().entrySet().stream()
            .map(e->e.getKey() + ": " + truncate(e.getValue().leftValue(), truncateLength) + " » " + truncate(e.getValue().rightValue(), truncateLength))
            .forEach(list::add);

        return String.join(", ", list);
    }

    /**
     * Returns the string representation of the {@code Object} argument truncated to the specified length. If truncated,
     * the last three characters of the string are replaced with "..." to flag that truncation occurred. A null argument
     * returns the string "null" (or truncated version of it).
     *
     * @throws IllegalStateException if maxLength < 3
     */
    public static String truncate(@Nullable Object o, int maxLength)
    {
        if (maxLength < 3)
            throw new IllegalStateException("maxLength parameter must be >= 3");
        String s = String.valueOf(o);
        return s.length() > maxLength ? s.substring(0, maxLength - 3) + "..." : s;
    }

    /**
     * Replaces known bad characters (currently curly quotes) with reasonable replacements (non-curly quotes).
     * @param original The string to be sanitized
     * @return the sanitized string
     */
    public static String replaceBadCharacters(String original)
    {
        if (original == null)
            return null;

        return original.replaceAll("[\\u2018\\u2019]", "'")
                        .replaceAll("[\\u201C\\u201D]", "\"");

    }

    public static class TestCase extends Assert
    {
        @Test
        public void testReplaceBadCharacters()
        {
            assertNull(replaceBadCharacters(null));
            assertEquals("", replaceBadCharacters(""));
            assertEquals("It's all good", replaceBadCharacters("It's all good"));
            assertEquals("She said \"yes\"", replaceBadCharacters("She said \"yes\""));
            assertEquals("'It's bad'", replaceBadCharacters("\u2018It\u2018s bad\u2018"));
            assertEquals("It's bad", replaceBadCharacters("It\u2019s bad"));
            assertEquals("\"Stuff\"", replaceBadCharacters("\u201CStuff\u201D"));
            assertEquals("\"It's 'My' Stuff\"", replaceBadCharacters("\u201CIt\u2018s \u2019My\u2019 Stuff\u201D"));
        }

        @Test
        public void testFindCommonPrefix()
        {
            assertEquals("", findCommonPrefix(Collections.emptySet()));
            assertEquals("", findCommonPrefix(Arrays.asList("")));
            assertEquals("abcdefghijklmnopqrstuvwxyz", findCommonPrefix(Arrays.asList("abcdefghijklmnopqrstuvwxyz")));
            assertEquals("abc", findCommonPrefix(Arrays.asList("abcdefghijklmnop", "abcxyz", "abcdefg")));
            assertEquals("xyz", findCommonPrefix(Arrays.asList("xyzabc", "xyzasdfj", "xyzafjf", "xyzpqr")));
            assertEquals("foo", findCommonPrefix(Arrays.asList("foo", "foo2")));
        }

        @Test
        public void testContainsUpperCase()
        {
            assertTrue(containsUpperCase("ABC"));
            assertTrue(containsUpperCase("Abc"));
            assertTrue(containsUpperCase("abC"));
            assertTrue(containsUpperCase("aBc"));
            assertTrue(containsUpperCase("abcdefghijklmnopqrstuvwxyZ"));
            assertTrue(containsUpperCase("123908565938293487A120394902348"));
            assertTrue(containsUpperCase("A230948092830498"));
            assertTrue(containsUpperCase("\u00E4\u00F6\u00FC\u00C5"));
            assertFalse(containsUpperCase("123409523987"));
            assertFalse(containsUpperCase("abcdefghijklmnoopqrstuvwxyz"));
            assertFalse(containsUpperCase("!@#$%^&*^)"));
            assertFalse(containsUpperCase("xyz"));
            assertFalse(containsUpperCase("abc"));
            assertFalse(containsUpperCase("\u00E4\u00F6\u00FC"));
        }

        @Test
        public void testIsText()
        {
            assertTrue(isText("this is a test\n\r"));
            assertTrue(isText(""));
            assertFalse(isText("DEL\u007F"));
            assertFalse(isText("NUL\u0000"));
            assertFalse(isText("NUL\u0001"));
            assertTrue(isText("\u00c0t\u00e9"));
//            assertFalse(isText("\ufffe"));
//            assertFalse(isText("\ufeff"));
        }

        @Test
        public void testPluralize()
        {
            assertEquals("-1 wombats", pluralize(-1, "wombat"));
            assertEquals("0 wombats", pluralize(0, "wombat"));
            assertEquals("1 wombat", pluralize(1, "wombat"));
            assertEquals("2 wombats", pluralize(2, "wombat"));
            assertEquals("27 wombats", pluralize(27, "wombat"));
            assertEquals("1,000,027 wombats", pluralize(1000027, "wombat"));

            assertEquals("-1 octopi", pluralize(-1, "octopus", "octopi"));
            assertEquals("0 octopi", pluralize(0, "octopus", "octopi"));
            assertEquals("1 octopus", pluralize(1, "octopus", "octopi"));
            assertEquals("2 octopi", pluralize(2, "octopus", "octopi"));
            assertEquals("27 octopi", pluralize(27, "octopus", "octopi"));
            assertEquals("1,000,027 octopi", pluralize(1000027, "octopus", "octopi"));
        }

        @Test
        public void testSplitCamelCase()
        {
            assertEquals("lowercase", splitCamelCase("lowercase"));
            assertEquals("Class", splitCamelCase("Class"));
            assertEquals("My Class", splitCamelCase("MyClass"));
            assertEquals("HTML", splitCamelCase("HTML"));
            assertEquals("PDF Loader", splitCamelCase("PDFLoader"));
            assertEquals("A String", splitCamelCase("AString"));
            assertEquals("Simple XML Parser", splitCamelCase("SimpleXMLParser"));
            assertEquals("GL 11 Version", splitCamelCase("GL11Version"));
            assertEquals("99 Bottles", splitCamelCase("99Bottles"));
            assertEquals("May 5", splitCamelCase("May5"));
            assertEquals("BFG 9000", splitCamelCase("BFG9000"));
            assertEquals("Preserve Spaces Don't Expand", splitCamelCase("Preserve SpacesDon't  Expand"));
            assertEquals("Salt & Pepper", splitCamelCase("Salt & Pepper"));
            assertEquals("with_underscores", splitCamelCase("with_underscores"));
        }

        @Test
        public void testGetDomainName()
        {
            assertNull("Null value expected", getDomainName(null));
            assertEquals("No transformation expected", "subdomain", getDomainName("subdomain"));
            assertEquals("Expected to convert to lower case", "subdomain", getDomainName("SubDomain"));
            assertEquals("Expected to convert to lower case and remove spaces at beginning and end", "subdomain", getDomainName(" subDomain   "));
            assertEquals("Expected to replace space with dash", "sub-domain", getDomainName(" sub Domain "));
            assertEquals("Expected to remove leading and trailing dashes after trimming spaces", "sub-domain", getDomainName(" -sub Domain- "));
            assertEquals("Expected to remove invalid characters are normalize accented characters", "aoua", getDomainName("\u2603~!@$&()_+{}-=[],.#\u00E4\u00F6\u00FC\u00C5"));
            assertNull("Null expected if all characters are invalid ", getDomainName("-\u2603~!@$&()_+{}=[],.#-"));
            assertNull("Null expected if all characters are dashes ", getDomainName("-------"));
            assertEquals("Expected to remove invalid characters in the middle, replace spaces with dashes", "my-own--domain-with-dashes", getDomainName("My Own \u2603 D\u00F6main-with-[dashes]"));
            assertEquals("Expected to remove invalid characters and produce a string without any characters truncated", "my-own--domain-with-dashes-789012345678901234567890123456789012", getDomainName("My Own \u2603 D\u00F6main-with-[dashes]-789012345678901234567890123456789012"));
            assertEquals("Expected to truncate characters beyond valid length after removing and converting characters", "my-own--domain-with-dashes-789012345678901234567890123456789012", getDomainName("My Own \u2603 D\u00F6main-with-[dashes]-7890123456789012345678901234567890123"));
        }

        @Test
        public void testIsValidDomainName()
        {
            assertFalse("Null is not a valid domain name", isValidDomainName(null));
            assertFalse("Empty string is not a valid domain name", isValidDomainName(""));
            assertFalse("domain name cannot start with a dash", isValidDomainName("-dashing-before"));
            assertFalse("domain name cannot end with a dash", isValidDomainName("dashing-after-"));
            assertFalse("domain name cannot start and end with a dash", isValidDomainName("-dashing-"));
            assertFalse("domain name cannot contain spaces before or after", isValidDomainName(" spacesNoDashes "));
            assertFalse("domain name cannot contain spaces in the middle", isValidDomainName("spaces in between"));
            assertFalse("domain name cannot contain illegal characters", isValidDomainName("build-a-\u2603-today"));
            assertFalse("domain name cannot contain accented characters", isValidDomainName("build-\u00E4\u00F6\u00FC\u00C5-today"));
            assertFalse("domain name cannot be too long", isValidDomainName("1234567890-1234567890-1234567890-1234567890-12345678901234567890"));
            assertTrue("domain name can be long, but not too long", isValidDomainName("1234567890-1234567890-1234567890-1234567890-1234567890123456789"));
            assertTrue("domain name can be very short", isValidDomainName("1"));
            assertTrue("domain name can contain dashes", isValidDomainName("domain-name"));
            assertTrue("domain name can contain dashes", isValidDomainName("sub-domain-for-you"));
            assertTrue("domain name can contain double dashes", isValidDomainName("sub--domain"));
            assertTrue("domain name can contain only letters", isValidDomainName("subdomain"));
            assertTrue("domain name can contain only numbers", isValidDomainName("123445"));
        }

        @Test
        public void testPaddedUniquifier()
        {
            Set<String> digits = new HashSet<>(Arrays.asList("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"));
            for (int length = 0; length < 64; length++)
            {
                String s = getPaddedUniquifier(length);
                assertEquals("Generated a string with the wrong length: " + s, length, s.length());
                if (!digits.isEmpty())
                {
                    Iterator<String> iter = digits.iterator();
                    if (iter.hasNext())
                    {
                        String next = iter.next();
                        if (s.contains(next))
                            iter.remove();
                    }
                }
            }
            assertTrue("Didn't generate any Strings with: " + digits + ". This is quite unlikely.", digits.isEmpty());
        }

        @Test
        public void testUniquifier()
        {
            Set<String> digits = new HashSet<>(Arrays.asList("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"));
            for (int length = 0; length < 64; length++)
            {
                String s = getUniquifier(length);
                assertEquals("Generated a string with the wrong length: " + s, length, s.length());
                assertFalse("Generated a string with a leading zero: " + s, s.startsWith("0"));
                if (!digits.isEmpty())
                {
                    Iterator<String> iter = digits.iterator();
                    if (iter.hasNext())
                    {
                        String next = iter.next();
                        if (s.contains(next))
                            iter.remove();
                    }
                }
            }
            assertTrue("Didn't generate any Strings with: " + digits + ". This is quite unlikely.", digits.isEmpty());
        }

        @Test
        public void testMapDifference()
        {
            // ImmutableMap.of() maintains entry order
            Map<String, Object> map1 = ImmutableMap.of("prop1", 17, "prop2", "Chicken", "prop3", true);
            Map<String, Object> map2 = ImmutableMap.of("prop1", 18, "prop2", "Chicken", "prop3", false);
            Map<String, Object> map3 = ImmutableMap.of("prop1", 18, "prop2", "Marzipan", "prop3", false);

            assertEquals("", getMapDifference(null, null));
            assertEquals("prop1: » 17, prop2: » Chicken, prop3: » true", getMapDifference(null, map1));
            assertEquals("prop1: 17 » 18, prop3: true » false", getMapDifference(map1, map2));
            assertEquals("prop2: Chicken » Marzipan", getMapDifference(map2, map3));
            assertEquals("prop1: 18 » 17, prop2: Marzipan » Chicken, prop3: false » true", getMapDifference(map3, map1));
            assertEquals("prop1: 17 » , prop2: Chicken » , prop3: true » ", getMapDifference(map1, null));
            assertEquals("prop1: 17 » 18, prop2: C... » M..., prop3: true » f...", getMapDifference(map1, map3, 4));
        }

        @Test
        public void testTruncate()
        {
            String tiny = "A";
            int number = 123456789;
            String s = "ABDEFGHIJKL";

            assertEquals("null", truncate(null, 5));
            assertEquals("null", truncate(null, 4));
            assertEquals("...", truncate(null, 3));

            assertEquals("A", truncate(tiny, 5));
            assertEquals("A", truncate(tiny, 3));

            assertEquals("123456789", truncate(number, 20));
            assertEquals("123456789", truncate(number, 9));
            assertEquals("12...", truncate(number, 5));

            assertEquals("ABDEFGHIJKL", truncate(s, 20));
            assertEquals("ABDEFGHIJKL", truncate(s, 11));
            assertEquals("ABDEFGH...", truncate(s, 10));
            assertEquals("AB...", truncate(s, 5));
            assertEquals("...", truncate(s, 3));
        }

        @Test(expected = IllegalStateException.class)
        public void testTruncateTooShort()
        {
            truncate(null, 2);
        }
    }
}

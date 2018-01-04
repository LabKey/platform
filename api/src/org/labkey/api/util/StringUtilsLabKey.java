/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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

        while (first.charAt(i) == last.charAt(i))
            i++;

        return first.substring(0, i);
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

    public static boolean startsWithURL(String s)
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

    // splits strings at camel case boundaries
    // copied from http://stackoverflow.com/questions/2559759/how-do-i-convert-camelcase-into-human-readable-names-in-java
    public static String splitCamelCase(String s)
    {
        return s.replaceAll(
                String.format("%s|%s|%s",
                        "(?<=[A-Z])(?=[A-Z][a-z])",
                        "(?<=[^A-Z])(?=[A-Z])",
                        "(?<=[A-Za-z])(?=[^A-Za-z])"
                ),
                " "
        );
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

    public static class TestCase extends Assert
    {
        @Test
        public void testFindCommonPrefix()
        {
            assertEquals("", findCommonPrefix(Collections.emptySet()));
            assertEquals("", findCommonPrefix(PageFlowUtil.set("")));
            assertEquals("abcdefghijklmnopqrstuvwxyz", findCommonPrefix(PageFlowUtil.set("abcdefghijklmnopqrstuvwxyz")));
            assertEquals("abc", findCommonPrefix(PageFlowUtil.set("abcdefghijklmnop", "abcxyz", "abcdefg")));
            assertEquals("xyz", findCommonPrefix(PageFlowUtil.set("xyzabc", "xyzasdfj", "xyzafjf", "xyzpqr")));
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
            assertFalse(containsUpperCase("123409523987"));
            assertFalse(containsUpperCase("abcdefghijklmnoopqrstuvwxyz"));
            assertFalse(containsUpperCase("!@#$%^&*^)"));
            assertFalse(containsUpperCase("xyz"));
            assertFalse(containsUpperCase("abc"));
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

            assertEquals("-1 octopi", pluralize(-1, "octopus", "octopi"));
            assertEquals("0 octopi", pluralize(0, "octopus", "octopi"));
            assertEquals("1 octopus", pluralize(1, "octopus", "octopi"));
            assertEquals("2 octopi", pluralize(2, "octopus", "octopi"));
            assertEquals("27 octopi", pluralize(27, "octopus", "octopi"));
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
        }

        @Test
        public void testGetDomainName()
        {
            assertEquals("Null value expected", null, getDomainName(null));
            assertEquals("No transformation expected", "subdomain", getDomainName("subdomain"));
            assertEquals("Expected to convert to lower case", "subdomain", getDomainName("SubDomain"));
            assertEquals("Expected to convert to lower case and remove spaces at beginning and end", "subdomain", getDomainName(" subDomain   "));
            assertEquals("Expected to replace space with dash", "sub-domain", getDomainName(" sub Domain "));
            assertEquals("Expected to remove leading and trailing dashes after trimming spaces", "sub-domain", getDomainName(" -sub Domain- "));
            assertEquals("Expected to remove invalid characters are normalize accented characters", "aoua", getDomainName("\u2603~!@$&()_+{}-=[],.#\u00E4\u00F6\u00FC\u00C5"));
            assertEquals("Null expected if all characters are invalid ", null, getDomainName("-\u2603~!@$&()_+{}=[],.#-"));
            assertEquals("Null expected if all characters are dashes ", null, getDomainName("-------"));
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
    }
}

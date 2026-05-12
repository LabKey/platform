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
import org.apache.commons.lang3.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.view.ViewServlet;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
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
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.Math.min;
import static org.labkey.api.util.IntegerUtils.asIntegerElseNull;

public class StringUtilsLabKey
{
    /** Instead of relying on the platform default character encoding, use this Charset */
    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    /**
     * Matches either:
     * 1. (\\s--[^ ]) - a space followed by two dashes followed by any non-space character
     * 2. (\\s-[^- ]) - a space followed by one dash followed by any character that is not a dash or space
     */
    public static final String SPACE_DASH_EXPRESSION = "(\\s--[^ ])|(\\s-[^- ])";
    public static final Pattern SPACE_DASH_PATTERN = Pattern.compile(SPACE_DASH_EXPRESSION);
    /** Special character strings that can be used by tests in this class and others */
    public static final List<String> specialCharacterTestStrings = List.of(
            "",
            "A",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789",
            "A \" ' ` ~ ! @#$%^&*()_-+= { } [ ] \\ | : ; < > , . ? / 你好 \uD83D\uDC7E",
            "°±²³´µ¶·¸¹º»¼½¾¿",
            "こんにちは世界!",
            "Відношення об'єму великих тромбоцитів (P-LCR)",
            "\uD83D\uDC7EA\uD83D\uDC7E\uD83E\uDD91\uD83C\uDFBB\uD83C\uDFC2",
            "こんにちは世界!\uD83D\uDC7E\uD83D\uDC7E\uD83D\uDC7E\uD83D\uDC7E\uD83D\uDC7E",
            "こAんBにCちDはE世F界G\uD83D\uDC7EH\uD83D\uDC7E☃\uD83D\uDC7EJ\uD83D\uDC7EK\uD83D\uDC7EL\uD83D\uDC7EM\uD83D\uDC7E!",
            "\uD83D\uDC7E\uD83D\uDC7E\uD83D\uDC7E\uD83D\uDC7E\uD83D\uDC7E\uD83D\uDC7E\uD83D\uDC7E"
    );

    /** A list with all distinct characters (keeping surrogate pairs together) from the above test strings */
    public static final List<String> uniqueSpecialChars = specialCharacterTestStrings.stream()
            .flatMap(s -> {
                List<String> moreStrings = new LinkedList<>();
                for (int i = 0; i < s.length(); i++)
                {
                    char c = s.charAt(i);
                    if (Character.isSurrogate(c))
                    {
                        char c2 = s.charAt(i + 1);
                        moreStrings.add(c + "" + c2);
                        i++;
                    }
                    else
                    {
                        moreStrings.add(String.valueOf(c));
                    }
                }
                return moreStrings.stream();
            })
            .distinct()
            .sorted()
            .collect(Collectors.toCollection(ArrayList::new));

    private static final Random RANDOM;

    static
    {
        try
        {
            RANDOM = SecureRandom.getInstanceStrong();
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new ConfigurationException("JVM doesn't have a SecureRandom available", e);
        }
    }

    private static final int MAX_LONG_LENGTH = String.valueOf(Long.MAX_VALUE).length() - 1;

    public static String generateSpecialCharacterString(int length)
    {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++)
        {
            int index = RANDOM.nextInt(uniqueSpecialChars.size());
            sb.append(uniqueSpecialChars.get(index));
        }
        return sb.toString();
    }

    public static List<String> generateSpecialCharacterList(int length)
    {
        List<String> ret = new LinkedList<>();
        for (int i = 0; i < length; i++)
        {
            int index = RANDOM.nextInt(uniqueSpecialChars.size());
            ret.add(uniqueSpecialChars.get(index));
        }
        return ret;
    }

    public static @Nullable String validateLegalNames(String s, @NotNull String illegalCharset, String type)
    {
        if (StringUtils.isBlank(s))
            return type + " must not be blank.";
        if (!ViewServlet.validChars(s))
            return type + " must contain only valid unicode characters.";
        if (StringUtils.containsAny(s, illegalCharset))
            return type + " may not contain any of these characters: " + illegalCharset;
        if (StringUtils.containsAny(s, "\t\n\r"))
            return type + " may not contain 'tab', 'new line', or 'return' characters.";
        if (StringUtils.contains("-$", s.charAt(0)))
            return type + " may not begin with any of these characters: -$";
        Matcher expMatcher = SPACE_DASH_PATTERN.matcher(s);
        if (expMatcher.find())
            return type + " may not contain space followed by dash.";

        return null;
    }

    public static void append(StringBuilder sb, @Nullable Identifiable identifiable)
    {
        if (null != identifiable)
            append(sb, identifiable.getName());
    }

    public static void append(StringBuilder sb, @Nullable String value)
    {
        if (!StringUtils.isEmpty(value))
        {
            if (!sb.isEmpty())
                sb.append(" ");

            sb.append(value);
        }
    }

    // Finds the longest common prefix present in all elements of the passed in string collection. In other words,
    // the longest string (prefix) such that, for all s in strings, s.startsWith(prefix). An empty collection returns
    // the empty string and a single element collection returns that string.
    public static String findCommonPrefix(@NotNull Collection<String> strings)
    {
        if (strings.isEmpty())
            return "";

        List<String> list = new ArrayList<>(strings);

        if (strings.size() == 1)
            return list.getFirst();

        Collections.sort(list);
        String first = list.getFirst();
        String last = list.getLast();
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
                if (Strings.CI.startsWith(s, prefix))
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

    // Does the string have ANY lower-case letters?
    public static boolean containsLowerCase(String s)
    {
        for (char ch : s.toCharArray())
            if (Character.isLowerCase(ch))
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
            else
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
                        int lastIndex = stringList.size() - 1;
                        stringList.set(lastIndex, stringList.get(lastIndex) + part);
                        appending = false;
                    }
                    else
                        stringList.add(part);
                }
                else
                {
                    appending = true;
                    int lastIndex = stringList.size() - 1;
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
        else if (value instanceof String v)
            return Integer.valueOf(v);
        else if (asIntegerElseNull(value) instanceof Integer v)
            return v.intValue();

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
     * specified here: <a href="https://tools.ietf.org/html/rfc1035">RFC 1035</a>
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
        int end = min(63, normalizedName.length()); // a sub-domain can be at most 63 characters in length
        while (start < end && normalizedName.charAt(start) == '-')
            start++;
        while (end > start && normalizedName.charAt(end - 1) == '-')
            end--;
        if (end - start == 0)
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
     * enabled: true » false
     * description: My Configuration » CAS Configuration, enabled: true » false
     * serverUrl: » http://localhost:8080/labkey/cas, description: » CAS localhost, autoRedirect: » true
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
                .map(e -> e.getKey() + ": " + truncate(e.getValue(), truncateLength) + " » ")
                .forEach(list::add);

        difference.entriesOnlyOnRight().entrySet().stream()
                .map(e -> e.getKey() + ": » " + truncate(e.getValue(), truncateLength))
                .forEach(list::add);

        difference.entriesDiffering().entrySet().stream()
                .map(e -> e.getKey() + ": " + truncate(e.getValue().leftValue(), truncateLength) + " » " + truncate(e.getValue().rightValue(), truncateLength))
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
    public static @Nullable String replaceBadCharacters(@Nullable String original)
    {
        if (original == null)
            return null;

        return original.replaceAll("[\\u2018\\u2019]", "'")
                .replaceAll("[\\u201C\\u201D]", "\"");
    }

    public static String unquoteString(@Nullable String original)
    {
        if (original == null)
            return null;

        if (original.length() > 1 && original.startsWith("\"") && original.endsWith("\""))
        {
            String stripped = original.substring(1, original.length() - 1);
            return stripped.replaceAll("\"\"", "\"");
        }

        return original;
    }

    /**
     * Spell out numbers nine and below; use numerals for numbers 10 and above. Per AP stylebook.
     */
    public static String spellOut(int i)
    {
        return switch (i)
        {
            case 0 -> "zero";
            case 1 -> "one";
            case 2 -> "two";
            case 3 -> "three";
            case 4 -> "four";
            case 5 -> "five";
            case 6 -> "six";
            case 7 -> "seven";
            case 8 -> "eight";
            case 9 -> "nine";
            default -> String.valueOf(i);
        };
    }

    /**
     * Join phrases with commas, adding a conjunction (e.g., and, or) before the last element if two or more elements
     * are present. And we're doing the Oxford comma, thanks very much.
     */
    public static String joinWithConjunction(List<String> list, String conjunction)
    {
        String combined = StringUtils.join(list, ", ");
        if (list.size() > 1)
        {
            int lastCommaSpace = combined.lastIndexOf(", ");
            combined = combined.substring(0, lastCommaSpace + (list.size() > 2 ? 1 : 0)) + " " + conjunction + combined.substring(lastCommaSpace + 1);
        }
        return combined;
    }

    /**
     * @return the indefinite article to use with the passed in noun. Returns "a" for words starting with most
     * consonants, "an" for words starting with a vowel, and "a(n)" for words starting with "h". This isn't perfect
     * since it's not distinguishing voiced vs. unvoiced "h", acronyms that are spelled out, and other pronunciation
     * quirks.
     */
    public static String getArticleForNoun(String noun)
    {
        char firstChar = noun.charAt(0);
        return switch (firstChar)
        {
            case 'h', 'H' -> "a(n)";
            case 'a', 'e', 'i', 'o', 'u' -> "an";
            default -> "a";
        };
    }

    /**
     * Replaces tabs, newlines and Separator characters (including non-breaking spaces) with simple spaces
     * @return new string with each separator characters replaces by a single space. Returns null if original string is null.
     */
    public static @Nullable String replaceSeparators(@Nullable String str)
    {
        if (str == null)
            return null;
        // N.B. You might think that \p{Z} includes tabs, but it does not
        return str.replaceAll("[\\p{Z}\\t\\n\\r]", " ");
    }

    /**
     * Issue 51933: the trim() method does not trim off any non-breaking spaces
     * Trims all separator characters (spaces, non-breaking spaces, newlines, tabs) from beginning and end of
     * the string representation of the object provided.
     * @param obj the object whose string value is to be trimmed
     * @return the trimmed string. If the original object is null, returns the empty string.
     */
    public static @NotNull String fullTrimToEmpty(@Nullable Object obj)
    {
        if (obj == null)
            return "";
        String stringVal = obj.toString();
        stringVal = stringVal.replaceAll("[\\p{Z}\\t\\n\\r\\s]+$", "");
        return stringVal.replaceAll("^[\\p{Z}\\t\\n\\r\\s]+", "");
    }

    /**
     * @param obj the object to convert and trim
     * @return A string value representation of the object with leading and trailing spaces removed. In addition, all
     * separator characters will become single spaces everywhere in the string. If obj is null, returns the empty string.
     */
    public static @NotNull String sanitizeSeparatorsAndTrim(@Nullable Object obj)
    {
        if (obj == null)
            return "";
        String stringVal = obj.toString();
        return fullTrimToEmpty(replaceSeparators(stringVal));
    }

    /**
     * Tests whether the provided string could be used as a Java identifier or part of a name in a property file. Does
     * not check for Java keywords.
     */
    public static boolean isValidJavaIdentifier(@Nullable String test)
    {
        boolean valid = !StringUtils.isBlank(test);
        if (valid)
        {
            valid = Character.isJavaIdentifierStart(test.charAt(0));
            int i = 1;
            while (valid && i < test.length())
            {
                valid = Character.isJavaIdentifierPart(test.charAt(i++));
            }
        }
        return valid;
    }

    private static final byte NON_ASCII_MASK = (byte) 0b1000_0000;
    private static final byte START_BYTE_MASK = (byte) 0b1100_0000;

    // Truncates a string to UTF-8 bytes <= maxBytes starting from the first character (truncating the end of the string)
    public static String leftUtf8Bytes(String s, int maxBytes)
    {
        if (maxBytes < 0)
            throw new IllegalArgumentException("maxBytes cannot be negative");

        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxBytes)
        {
            if (maxBytes > 0)
            {
                // Inspect the byte just after the possible truncation point; a start byte (ASCII or non-ASCII) there
                // means the truncation point is the end of a character. If it's not a start byte, back up one byte at
                // a time until one is found and truncate before it. The check below is true if the byte is 0b10xx_xxxx.
                while ((bytes[maxBytes] & START_BYTE_MASK) == NON_ASCII_MASK)
                    maxBytes--;
            }
            s = new String(bytes, 0, maxBytes, StandardCharsets.UTF_8);
        }
        return s;
    }

    // Truncates a string to UTF-8 bytes <= maxBytes starting from the LAST character (truncating the start of the string)
    public static String rightUtf8Bytes(String s, int maxBytes)
    {
        if (maxBytes < 0)
            throw new IllegalArgumentException("maxBytes cannot be negative");

        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        int length = bytes.length;
        if (length > maxBytes)
        {
            int start = length - maxBytes;
            if (maxBytes > 0)
            {
                // Inspect the byte at the possible truncation point; a start byte (ASCII or non-ASCII) here means it's
                // safe to truncate here. If it's not a start byte, move forward one byte at a time until one is found.
                // The check below is true if the byte is 0b10xx_xxxx.
                while (start < length && (bytes[start] & START_BYTE_MASK) == NON_ASCII_MASK)
                    start++;
            }
            s = new String(bytes, start, length - start, StandardCharsets.UTF_8);
        }
        return s;
    }

    // Truncates a string to characters <= maxCharacters starting from the first character (truncating the end of the
    // string) but without splitting a surrogate pair at the end.
    public static String leftSurrogatePairFriendly(String s, int maxCharacters)
    {
        if (maxCharacters < 0)
            throw new IllegalArgumentException("maxCharacters cannot be negative");

        if (s.length() > maxCharacters)
        {
            s = s.substring(0, maxCharacters);
            // Don't split a surrogate pair at the end
            if (maxCharacters > 0 && Character.isHighSurrogate(s.charAt(s.length() - 1)))
                s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    // Truncates a string to characters <= maxCharacters starting from the LAST character (truncating the start of the
    // string) but without splitting a surrogate pair at the start.
    public static String rightSurrogatePairFriendly(String s, int maxCharacters)
    {
        if (maxCharacters < 0)
            throw new IllegalArgumentException("maxCharacters cannot be negative");

        if (s.length() > maxCharacters)
        {
            s = s.substring(s.length() - maxCharacters);
            // Don't split a surrogate pair at the beginning
            if (maxCharacters > 0 && Character.isLowSurrogate(s.charAt(0)))
                s = s.substring(1);
        }
        return s;
    }

    public static boolean hasBrokenSurrogate(String s)
    {
        for (int i = 0; i < s.length(); i++)
        {
            char ch = s.charAt(i);
            if (Character.isHighSurrogate(ch))
            {
                if (i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1)))
                {
                    return true; // High surrogate not followed by low surrogate
                }
                i++; // Skip the low surrogate
            }
            else if (Character.isLowSurrogate(ch))
            {
                return true; // Low surrogate without preceding high surrogate
            }
        }
        return false;
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testFullTrimToEmptyString()
        {
            assertEquals("", fullTrimToEmpty(null));
            assertEquals("", fullTrimToEmpty(""));
            assertEquals("no trimming", fullTrimToEmpty("no trimming"));
            assertEquals("no    trimming", fullTrimToEmpty("no    trimming"));
            assertEquals("standard    trim", fullTrimToEmpty("   standard    trim "));
            assertEquals("no\u2007 \u00A0\t\n   trimming", fullTrimToEmpty("no\u2007 \u00A0\t\n   trimming"));
            assertEquals("with\u2007 \u00A0\t\n   trimming", fullTrimToEmpty("  with\u2007 \u00A0\t\n   trimming\t\n"));
            assertEquals("with\u2007 \u00A0\t\n   trimming", fullTrimToEmpty("\u2007 \u00A0  with\u2007 \u00A0\t\n   trimming\t\n"));
            assertEquals("with\u2007 trimming", fullTrimToEmpty("\u2007 \u00A0  with\u2007 trimming\t\n\u00A0\t\n  "));
            assertEquals("with\u2007 trimming", fullTrimToEmpty("\u2007 \u00A0  with\u2007 trimming  "));
        }

        @Test
        public void testReplaceSeparators()
        {
            assertNull(replaceSeparators(null));
            assertEquals("", replaceSeparators(""));
            assertEquals("", replaceSeparators(""));
            assertEquals("a", replaceSeparators("a"));
            assertEquals("ab ", replaceSeparators("ab\n"));
            assertEquals("a   b", replaceSeparators("a\t\n\tb"));
            assertEquals(" a   b ", replaceSeparators("\ta\t\r\nb\t"));
            assertEquals("a  b  ", replaceSeparators("a\t\tb\n\n"));
            assertEquals("  a    b  ", replaceSeparators("\u2007\ta\u00A0\t\n\tb\u202F\t"));
            assertEquals("a b", replaceSeparators("a\tb"));
        }

        @Test
        public void testSanitizeSeparatorsAndTrim()
        {
            assertEquals("", sanitizeSeparatorsAndTrim(null));
            assertEquals("", sanitizeSeparatorsAndTrim(""));
            assertEquals("no change", sanitizeSeparatorsAndTrim("no change"));
            assertEquals("leading  and trailing", sanitizeSeparatorsAndTrim(" leading  and trailing       "));
            assertEquals("with  tab change", sanitizeSeparatorsAndTrim("with  tab\tchange"));
            assertEquals("tab change", sanitizeSeparatorsAndTrim(" tab\tchange"));
            assertEquals("with newline", sanitizeSeparatorsAndTrim("with newline\n"));
            assertEquals("with all  specialties", sanitizeSeparatorsAndTrim("with\tall\r\nspecialties\u00A0"));
            assertEquals("special    separators", sanitizeSeparatorsAndTrim("special\u00A0 \u00A0 separators\n"));
            assertEquals("special    separators", sanitizeSeparatorsAndTrim("\u202F special\u00A0 \u2007 separators\u00A0 \n"));
        }

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

        @Test
        public void testUnquoteString()
        {
            assertNull(unquoteString(null));
            assertEquals("", unquoteString(""));
            assertEquals("\"", unquoteString("\""));
            assertEquals("abc", unquoteString("abc"));
            assertEquals("abc", unquoteString("\"abc\""));
            assertEquals("ab\"c", unquoteString("ab\"c"));
            assertEquals("\"abc", unquoteString("\"abc"));
            assertEquals("abc\"", unquoteString("abc\""));
            assertEquals("ab\"c", unquoteString("\"ab\"\"c\""));
            assertEquals("WC-1,3", unquoteString("\"WC-1,3\""));
        }

        @Test
        public void testAppend()
        {
            class TestIdentifiable implements Identifiable
            {
                @Override
                public Container getContainer()
                {
                    return null;
                }

                @Override
                public String getLSID()
                {
                    return null;
                }

                @Override
                public String getName()
                {
                    return "TestIdentifiable";
                }
            }

            StringBuilder sb = new StringBuilder();
            append(sb, (Identifiable) null);
            assertEquals("", sb.toString());

            sb = new StringBuilder("first");
            append(sb, "");
            assertEquals("first", sb.toString());
            append(sb, (String) null);
            assertEquals("first", sb.toString());

            append(sb, "second");
            assertEquals("first second", sb.toString());

            append(sb, new TestIdentifiable());
            assertEquals("first second TestIdentifiable", sb.toString());
        }

        @Test
        public void testSpellOut()
        {
            assertEquals("zero", spellOut(0));
            assertEquals("one", spellOut(1));
            assertEquals("two", spellOut(2));
            assertEquals("three", spellOut(3));
            assertEquals("four", spellOut(4));
            assertEquals("five", spellOut(5));
            assertEquals("six", spellOut(6));
            assertEquals("seven", spellOut(7));
            assertEquals("eight", spellOut(8));
            assertEquals("nine", spellOut(9));
            assertEquals("10", spellOut(10));
            assertEquals("11", spellOut(11));
            assertEquals("25", spellOut(25));
            assertEquals("37", spellOut(37));
            assertEquals("123", spellOut(123));
        }

        @Test
        public void testJoinWithConjunction()
        {
            testJoinWithConjunction("");
            testJoinWithConjunction("this", "this");
            testJoinWithConjunction("this and that", "this", "that");
            testJoinWithConjunction("this, that, and something", "this", "that", "something");
            testJoinWithConjunction("this, that, something, and something else", "this", "that", "something", "something else");
        }

        private void testJoinWithConjunction(String expected, String... elements)
        {
            List<String> strings = Arrays.asList(elements);
            assertEquals(expected, joinWithConjunction(strings, "and"));
        }

        @Test
        public void testIsValidIdentifier()
        {
            assertFalse(isValidJavaIdentifier(null));
            assertFalse(isValidJavaIdentifier(""));
            assertFalse(isValidJavaIdentifier(" "));
            assertFalse(isValidJavaIdentifier("   "));
            assertFalse(isValidJavaIdentifier("1"));
            assertFalse(isValidJavaIdentifier("123"));
            assertFalse(isValidJavaIdentifier("I have spaces"));
            assertFalse(isValidJavaIdentifier("!niceTry"));

            assertTrue(isValidJavaIdentifier("A"));
            assertTrue(isValidJavaIdentifier("$"));
            assertTrue(isValidJavaIdentifier("ABC"));
            assertTrue(isValidJavaIdentifier("abc"));
            assertTrue(isValidJavaIdentifier("Abc123"));
            assertTrue(isValidJavaIdentifier("This_and_that"));
            assertTrue(isValidJavaIdentifier("This$and$that"));
            assertTrue(isValidJavaIdentifier("This_"));
            assertTrue(isValidJavaIdentifier("This$"));
            assertTrue(isValidJavaIdentifier("_ABC"));
            assertTrue(isValidJavaIdentifier("$ABC"));
        }

        @Test
        public void testLeftUtf8Bytes()
        {
            // First, a few basic checks
            assertEquals("abc", leftUtf8Bytes("abc", 3));
            assertEquals("ab", leftUtf8Bytes("abc", 2));
            assertEquals("☃☃", leftUtf8Bytes("☃☃", 10));
            assertEquals("☃", leftUtf8Bytes("☃☃", 3));
            assertEquals("", leftUtf8Bytes("☃☃", 2));
            assertEquals("", leftUtf8Bytes("abc", 0));
            assertEquals("", leftUtf8Bytes("☃☃", 0));

            specialCharacterTestStrings.forEach(this::testLeftUtf8Bytes);

            // Test the character list sorted and then reversed
            List<String> uniqueCharsCopy = new ArrayList<>(uniqueSpecialChars);
            testLeftUtf8Bytes(String.join("", uniqueCharsCopy));
            Collections.reverse(uniqueCharsCopy);
            testLeftUtf8Bytes(String.join("", uniqueCharsCopy));

            // Now randomly shuffle all the characters to create a new string and test it (repeat 10 times)
            for (int i = 0; i < 10; i++)
            {
                Collections.shuffle(uniqueCharsCopy);
                String test = String.join("", uniqueCharsCopy);
                testLeftUtf8Bytes(test);
            }
        }

        // Test truncating this string at every possible byte length from 0 to byte length + 1
        private void testLeftUtf8Bytes(String s)
        {
            int byteLength = s.getBytes(StandardCharsets.UTF_8).length;
            String prev = "";
            for (int maxBytes = 0; maxBytes <= byteLength + 1; maxBytes++)
            {
                String truncated = leftUtf8Bytes(s, maxBytes);
                standardUtf8TruncationChecks(s, truncated, prev, maxBytes, String::startsWith);
                prev = truncated;
            }
        }

        private void standardUtf8TruncationChecks(String s, String truncated, String previous, int maxBytes, BiFunction<String, String, Boolean> checkFunction)
        {
            assertTrue("Failed with: " + s + ", maxBytes: " + maxBytes, checkFunction.apply(s, truncated));
            assertTrue("Failed with: " + s + ", maxBytes: " + maxBytes, checkFunction.apply(truncated, previous));
            assertTrue("Failed with: " + s + ", maxBytes: " + maxBytes, truncated.getBytes(StandardCharsets.UTF_8).length <= maxBytes);
            int minChars = maxBytes / 4;
            assertTrue("Failed with: " + s + ", maxBytes: " + maxBytes, truncated.length() >= minChars);
            assertFalse(hasBrokenSurrogate(truncated));
        }

        @Test
        public void testRightUtf8Bytes()
        {
            // First, a few basic checks
            assertEquals("abc", rightUtf8Bytes("abc", 3));
            assertEquals("bc", rightUtf8Bytes("abc", 2));
            assertEquals("☃☃", rightUtf8Bytes("☃☃", 10));
            assertEquals("☃", rightUtf8Bytes("☃☃", 3));
            assertEquals("", rightUtf8Bytes("☃☃", 2));
            assertEquals("", rightUtf8Bytes("abc", 0));
            assertEquals("", rightUtf8Bytes("☃☃", 0));

            specialCharacterTestStrings.forEach(this::testRightUtf8Bytes);

            // Test the character list sorted and then reversed
            List<String> uniqueCharsCopy = new ArrayList<>(uniqueSpecialChars);
            testRightUtf8Bytes(String.join("", uniqueCharsCopy));
            Collections.reverse(uniqueCharsCopy);
            testRightUtf8Bytes(String.join("", uniqueCharsCopy));

            // Now randomly shuffle all the characters to create a new string and test it (repeat 10 times)
            for (int i = 0; i < 10; i++)
            {
                Collections.shuffle(uniqueCharsCopy);
                String test = String.join("", uniqueCharsCopy);
                testRightUtf8Bytes(test);
            }
        }

        // Test truncating this string at every possible byte length from 0 to byte length + 1
        private void testRightUtf8Bytes(String s)
        {
            int byteLength = s.getBytes(StandardCharsets.UTF_8).length;
            String prev = "";
            for (int maxBytes = 0; maxBytes <= byteLength + 1; maxBytes++)
            {
                String truncated = rightUtf8Bytes(s, maxBytes);
                standardUtf8TruncationChecks(s, truncated, prev, maxBytes, String::endsWith);
                prev = truncated;
            }
        }

        @Test
        public void testLeftSurrogatePairFriendly()
        {
            // Simple ASCII
            String test = "abc";
            assertEquals("abc", leftSurrogatePairFriendly(test, 10));
            assertEquals("abc", leftSurrogatePairFriendly(test, 3));
            assertEquals("ab", leftSurrogatePairFriendly(test, 2));
            assertEquals("a", leftSurrogatePairFriendly(test, 1));
            assertEquals("", leftSurrogatePairFriendly(test, 0));

            // No surrogate pairs
            test = "☃☃☃";
            assertEquals("☃☃☃", leftSurrogatePairFriendly(test, 10));
            assertEquals("☃☃☃", leftSurrogatePairFriendly(test, 3));
            assertEquals("☃☃", leftSurrogatePairFriendly(test, 2));
            assertEquals("☃", leftSurrogatePairFriendly(test, 1));
            assertEquals("", leftSurrogatePairFriendly(test, 0));

            // Surrogate pairs
            test = "\uD83D\uDC7E\uD83D\uDC7E";
            assertEquals("\uD83D\uDC7E\uD83D\uDC7E", leftSurrogatePairFriendly(test, 7));
            assertEquals("\uD83D\uDC7E\uD83D\uDC7E", leftSurrogatePairFriendly(test, 4));
            assertEquals("\uD83D\uDC7E", leftSurrogatePairFriendly(test, 3));
            assertEquals("\uD83D\uDC7E", leftSurrogatePairFriendly(test, 2));
            assertEquals("", leftSurrogatePairFriendly(test, 1));
            assertEquals("", leftSurrogatePairFriendly(test, 0));

            // Surrogate pairs and ASCII intermingled
            test = "A\uD83D\uDC7EB\uD83D\uDC7EC";
            assertEquals("A\uD83D\uDC7EB\uD83D\uDC7EC", leftSurrogatePairFriendly(test, 10));
            assertEquals("A\uD83D\uDC7EB\uD83D\uDC7EC", leftSurrogatePairFriendly(test, 7));
            assertEquals("A\uD83D\uDC7EB\uD83D\uDC7E", leftSurrogatePairFriendly(test, 6));
            assertEquals("A\uD83D\uDC7EB", leftSurrogatePairFriendly(test, 5));
            assertEquals("A\uD83D\uDC7EB", leftSurrogatePairFriendly(test, 4));
            assertEquals("A\uD83D\uDC7E", leftSurrogatePairFriendly(test, 3));
            assertEquals("A", leftSurrogatePairFriendly(test, 2));
            assertEquals("A", leftSurrogatePairFriendly(test, 1));
            assertEquals("", leftSurrogatePairFriendly(test, 0));

            specialCharacterTestStrings.forEach(this::testLeftSurrogatePairFriendly);

            // Test the character list sorted and then reversed
            List<String> uniqueCharsCopy = new ArrayList<>(uniqueSpecialChars);
            testLeftSurrogatePairFriendly(String.join("", uniqueCharsCopy));
            Collections.reverse(uniqueCharsCopy);
            testLeftSurrogatePairFriendly(String.join("", uniqueCharsCopy));

            // Now randomly shuffle all the characters to create a new string and test it (repeat 10 times)
            for (int i = 0; i < 10; i++)
            {
                Collections.shuffle(uniqueCharsCopy);
                test = String.join("", uniqueCharsCopy);
                testLeftSurrogatePairFriendly(test);
            }
        }

        // Test truncating this string at every possible byte length from 0 to byte length + 1
        private void testLeftSurrogatePairFriendly(String s)
        {
            String prev = "";
            for (int len = 0; len <= s.length() + 1; len++)
            {
                String truncated = leftSurrogatePairFriendly(s, len);
                assertTrue("Failed with: " + s + ", len: " + len, s.startsWith(truncated));
                assertTrue("Failed with: " + s + ", len: " + len, truncated.startsWith(prev));
                assertTrue("Failed with: " + s + ", len: " + len, truncated.length() <= len);
                int minChars = len / 2;
                assertTrue("Failed with: " + s + ", len: " + len, truncated.length() >= minChars);
                assertFalse(hasBrokenSurrogate(truncated));
                prev = truncated;
            }
        }

        @Test
        public void testRightSurrogatePairFriendly()
        {
            // Simple ASCII
            String test = "abc";
            assertEquals("abc", rightSurrogatePairFriendly(test, 10));
            assertEquals("abc", rightSurrogatePairFriendly(test, 3));
            assertEquals("bc", rightSurrogatePairFriendly(test, 2));
            assertEquals("c", rightSurrogatePairFriendly(test, 1));
            assertEquals("", rightSurrogatePairFriendly(test, 0));

            // No surrogate pairs
            test = "☃☃☃";
            assertEquals("☃☃☃", rightSurrogatePairFriendly(test, 10));
            assertEquals("☃☃☃", rightSurrogatePairFriendly(test, 3));
            assertEquals("☃☃", rightSurrogatePairFriendly(test, 2));
            assertEquals("☃", rightSurrogatePairFriendly(test, 1));
            assertEquals("", rightSurrogatePairFriendly(test, 0));

            // Surrogate pairs
            test = "\uD83D\uDC7E\uD83D\uDC7E";
            assertEquals("\uD83D\uDC7E\uD83D\uDC7E", rightSurrogatePairFriendly(test, 7));
            assertEquals("\uD83D\uDC7E\uD83D\uDC7E", rightSurrogatePairFriendly(test, 4));
            assertEquals("\uD83D\uDC7E", rightSurrogatePairFriendly(test, 3));
            assertEquals("\uD83D\uDC7E", rightSurrogatePairFriendly(test, 2));
            assertEquals("", rightSurrogatePairFriendly(test, 1));
            assertEquals("", rightSurrogatePairFriendly(test, 0));

            // Surrogate pairs and ASCII intermingled
            test = "A\uD83D\uDC7EB\uD83D\uDC7EC";
            assertEquals("A\uD83D\uDC7EB\uD83D\uDC7EC", rightSurrogatePairFriendly(test, 10));
            assertEquals("A\uD83D\uDC7EB\uD83D\uDC7EC", rightSurrogatePairFriendly(test, 7));
            assertEquals("\uD83D\uDC7EB\uD83D\uDC7EC", rightSurrogatePairFriendly(test, 6));
            assertEquals("B\uD83D\uDC7EC", rightSurrogatePairFriendly(test, 5));
            assertEquals("B\uD83D\uDC7EC", rightSurrogatePairFriendly(test, 4));
            assertEquals("\uD83D\uDC7EC", rightSurrogatePairFriendly(test, 3));
            assertEquals("C", rightSurrogatePairFriendly(test, 2));
            assertEquals("C", rightSurrogatePairFriendly(test, 1));
            assertEquals("", rightSurrogatePairFriendly(test, 0));

            specialCharacterTestStrings.forEach(this::testRightSurrogatePairFriendly);

            // Test the character list sorted and then reversed
            List<String> uniqueCharsCopy = new ArrayList<>(uniqueSpecialChars);
            testRightSurrogatePairFriendly(String.join("", uniqueCharsCopy));
            Collections.reverse(uniqueCharsCopy);
            testRightSurrogatePairFriendly(String.join("", uniqueCharsCopy));

            // Now randomly shuffle all the characters to create a new string and test it (repeat 10 times)
            for (int i = 0; i < 10; i++)
            {
                Collections.shuffle(uniqueCharsCopy);
                test = String.join("", uniqueCharsCopy);
                testRightSurrogatePairFriendly(test);
            }
        }

        // Test truncating this string at every possible character length from 0 to length + 1
        private void testRightSurrogatePairFriendly(String s)
        {
            String prev = "";
            for (int len = 0; len <= s.length() + 1; len++)
            {
                String truncated = rightSurrogatePairFriendly(s, len);
                assertTrue("Failed with: " + s + ", len: " + len, s.endsWith(truncated));
                assertTrue("Failed with: " + s + ", len: " + len, truncated.endsWith(prev));
                assertTrue("Failed with: " + s + ", len: " + len, truncated.length() <= len);
                int minChars = len / 2;
                assertTrue("Failed with: " + s + ", len: " + len, truncated.length() >= minChars);
                assertFalse(hasBrokenSurrogate(truncated));
                prev = truncated;
            }
        }

        @Test
        public void testHasBrokenSurrogate()
        {
            assertFalse(hasBrokenSurrogate("abc"));
            assertFalse(hasBrokenSurrogate("☃☃☃"));
            assertFalse(hasBrokenSurrogate("😊"));
            assertFalse(hasBrokenSurrogate("\uD83D\uDC7EB\uD83D\uDC7EC"));

            assertTrue(hasBrokenSurrogate("\uD83D")); // High surrogate without low
            assertTrue(hasBrokenSurrogate("\uDE0A")); // Low surrogate without high
            assertTrue(hasBrokenSurrogate("\uDE0A\uD83D")); // Low before high
        }
    }
}

package org.labkey.api.security;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

public abstract class EntropyPasswordValidator implements PasswordValidator
{
    protected abstract int getRequiredBitsOfEntropy();

    protected int getCharacterEstimate()
    {
        return (int)Math.ceil(getRequiredBitsOfEntropy() / (Math.log(26) / BASE2_LOG));
    }

    @Override
    public boolean isValidForLogin(@NotNull String password, @NotNull User user, @Nullable Collection<String> messages)
    {
        return calculateEntropy(filter(password, user)) >= getRequiredBitsOfEntropy();
    }

    @Override
    public boolean isPreviousPasswordForbidden()
    {
        return true;
    }

    private static final double BASE2_LOG = Math.log(2);

    // Calculate bits of entropy based on https://www.omnicalculator.com/other/password-entropy
    public static double calculateEntropy(String password)
    {
        boolean hasLowerCase = false;
        boolean hasUpperCase = false;
        boolean hasDigit = false;
        boolean hasSymbol = false;

        for (char c : password.toCharArray())
        {
            if (Character.isLowerCase(c))
                hasLowerCase = true;
            else if (Character.isUpperCase(c))
                hasUpperCase = true;
            else if (Character.isDigit(c))
                hasDigit = true;
            else
                hasSymbol = true;
        }

        int poolSize = (hasLowerCase ? 26 : 0) + (hasUpperCase ? 26 : 0) + (hasDigit ? 10 : 0) + (hasSymbol ? 32 : 0);

        return password.length() * Math.log(poolSize) / BASE2_LOG;
    }

    private static final char[][] COMMON_SEQUENCES;

    static
    {
        COMMON_SEQUENCES = Stream.of(
            "`1234567890-=",    // fourth keyboard row
            "~!@#$%^&*()_+",    // fourth keyboard row + shift
            "qwertyuiop[]\\",   // third keyboard row
            "QWERTYUIOP{}|",    // third keyboard row + shift
            "asdfghjkl;’",      // second keyboard row
            "ASDFGHJKL:\"",     // second keyboard row + shift
            "zxcvbnm,./",       // first keyboard row
            "ZXCVBNM<>?",       // first keyboard row + shift
            "abcdefghijklmnopqrstuvwxyz",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

        )
            .sorted(Comparator.comparingInt(String::length)) // Shortest to longest
            .map(String::toCharArray)
            .toArray(char[][]::new);
    }

    // Incoming password should be trimmed of whitespace
    public static String filter(String password, User user)
    {
        // Remove all personal information sequences of 3+ characters
        List<char[]> segments = removePersonalInformation(List.of(password.toCharArray()), user);

        if (!segments.isEmpty())
        {
            // Remove duplicate substrings of 3+ characters
            segments = removeDuplicateSubstringsWithinEachSegment(segments);
            segments = removeDuplicateSubstringsBetweenSegments(segments);

            // Replace all repeated characters with a single character
            segments = replaceRepeatedCharacters(segments);

            // Replace all substrings of common sequences of 3+ characters with the first character of that substring
            segments = replace(segments, COMMON_SEQUENCES);
        }

        return new String(segments.size() == 1 ? segments.get(0) : concatenate(segments));
    }

    private static List<char[]> removePersonalInformation(List<char[]> segments, User user)
    {
        return remove(segments,
            user.getEmail().toCharArray(),
            user.getFriendlyName().toCharArray(),
            StringUtils.trimToEmpty(user.getFirstName()).toCharArray(),
            StringUtils.trimToEmpty(user.getLastName()).toCharArray());
    }

    private static List<char[]> removeDuplicateSubstringsWithinEachSegment(List<char[]> segments)
    {
        List<char[]> ret = new LinkedList<>();

        segments: for (char[] segment : segments)
        {
            int length = segment.length;
            for (int searchLength = length / 2; searchLength >= 3; searchLength--)
            {
                for (int i = 0; i <= length - 2 * searchLength; i++)
                {
                    int idx = indexOf(segment, i + searchLength, length, segment, i, searchLength);
                    if (idx > -1)
                    {
                        if (idx > 0)
                            ret.addAll(removeDuplicateSubstringsWithinEachSegment(List.of(Arrays.copyOfRange(segment, 0, idx))));
                        int from = idx + searchLength;
                        if (from != segment.length)
                            ret.addAll(removeDuplicateSubstringsWithinEachSegment(List.of(Arrays.copyOfRange(segment, from, segment.length))));
                        continue segments;
                    }
                }
            }
            ret.add(segment);
        }

        return ret;
    }

    private static List<char[]> removeDuplicateSubstringsBetweenSegments(List<char[]> segments)
    {
        char[] firstSegment = segments.get(0);
        List<char[]> filtered = remove(segments.subList(1, segments.size()), firstSegment);
        List<char[]> ret = filtered.size() > 1 ? removeDuplicateSubstringsBetweenSegments(filtered) : filtered;
        ret.add(0, firstSegment);

        return ret;
    }

    private static List<char[]> replaceRepeatedCharacters(List<char[]> segments)
    {
        List<char[]> ret = new LinkedList<>();

        segments: for (char[] segment : segments)
        {
            int length = segment.length;

            if (length > 1)
            {
                for (int i = 0; i < length - 1; i++)
                {
                    if (segment[i] == segment[i + 1])
                    {
                        if (i > 0)
                            ret.add(Arrays.copyOf(segment, i));
                        char prev = segment[i];
                        ret.add(new char[]{prev});

                        do
                        {
                            i++;
                        }
                        while (i < length && prev == segment[i]);

                        ret.addAll(replaceRepeatedCharacters(List.of(Arrays.copyOfRange(segment, i, length))));
                        continue segments;
                    }
                }
            }

            ret.add(segment);
        }

        return ret;
    }

    private static char[] concatenate(List<char[]> arrays)
    {
        int finalLength = 0;
        for (char[] array: arrays)
            finalLength += array.length;

        char[] ret = new char[finalLength];
        int destPos = 0;

        for (char[] array: arrays)
        {
            System.arraycopy(array, 0, ret, destPos, array.length);
            destPos += array.length;
        }

        return ret;
    }

    private static List<char[]> remove(List<char[]> segments, char[]... searchSource)
    {
        for (char[] searchSeq : searchSource)
        {
            segments = remove(segments, searchSeq);
        }

        return segments;
    }

    private static List<char[]> replace(List<char[]> segments, char[]... searchSource)
    {
        for (char[] searchSeq : searchSource)
        {
            segments = replace(segments, searchSeq);
        }

        return segments;
    }

    private interface Handler
    {
        void handle(List<char[]> ret, char[] segment, int idx, int length);
    }

    private static List<char[]> removeOrReplace(List<char[]> segments, char[] searchSource, Handler handler)
    {
        List<char[]> ret = new LinkedList<>();

        segments: for (char[] segment : segments)
        {
            for (int searchLength = Math.min(segment.length, searchSource.length); searchLength >= 3; searchLength--)
            {
                for (int i = 0; i <= searchSource.length - searchLength; i++)
                {
                    int idx = indexOf(segment, 0, segment.length, searchSource, i, searchLength);
                    if (idx > -1)
                    {
                        if (idx > 0)
                            ret.addAll(removeOrReplace(List.of(Arrays.copyOfRange(segment, 0, idx)), searchSource, handler));
                        handler.handle(ret, segment, idx, searchLength);
                        int from = idx + searchLength;
                        if (from != segment.length)
                            ret.addAll(removeOrReplace(List.of(Arrays.copyOfRange(segment, from, segment.length)), searchSource, handler));
                        continue segments;
                    }
                }
            }
            ret.add(segment);
        }

        return ret;
    }

    private static final Handler REMOVE_HANDLER = (ret, segment, idx, length) -> {};
    private static final Handler REPLACE_HANDLER = (ret, segment, idx, length) -> {
        char[] replacement = Arrays.copyOfRange(segment, idx, idx + 1);
        ret.add(replacement);
    };

    private static List<char[]> remove(List<char[]> segments, char[] searchSource)
    {
        return removeOrReplace(segments, searchSource, REMOVE_HANDLER);
    }

    private static List<char[]> replace(List<char[]> segments, char[] searchSource)
    {
        return removeOrReplace(segments, searchSource, REPLACE_HANDLER);
    }

    private static int indexOf(char[] seq, int seqStart, int seqLength, char[] searchSeq, int searchSeqStart, int searchSeqLength)
    {
        for (int k = seqStart; k <= seqLength - searchSeqLength; k++)
            if (equals(seq, k, searchSeq, searchSeqStart, searchSeqLength))
                return k;

        return -1;
    }

    private static boolean equals(char[] array1, int start1, char[] array2, int start2, int length)
    {
        for (int i = 0; i < length; i++)
            if (array1[start1 + i] != array2[start2 + i])
                return false;

        return true;
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testEntropy()
        {
            User user = new User();
            user.setEmail("auth@labkey.test");
            user.setDisplayName("StrongAuthTest");
            user.setFirstName("First");
            user.setLastName("Last");

            assertEquals(42.3, calculateEntropy("incorrect"), 0.01);
            assertEquals(51.3, calculateEntropy("Incorrect"), 0.01);
            assertEquals(65.5, calculateEntropy("IncoRRect77"), 0.01);
            assertEquals(91.76, calculateEntropy("IncoRRect77$%&"), 0.01);

            assertEquals("", filter("auth@labkey.testFirLast", user));
            assertEquals("wxyz", filter("wauth@labkey.testxLasyrstz", user));
            assertEquals("adam", filter("adutham", user));

            assertEquals("bugcatdogratfox", filter("bugcatdogratfoxcatdogfoxratbug", user));
            assertEquals("bug", filter("bugauth@labkey.testbug", user));

            assertEquals("incorect", filter("incorrect", user));
            assertEquals("abcd", filter("aaabbbcccddd", user));
            assertEquals("ab", filter("aaFirstbb", user));
            assertEquals("aaa", filter("aLastaFira", user));
            assertEquals("a", filter("aaa", user));
            assertEquals("ax", filter("aaaaax", user));
            assertEquals("xa", filter("xaa", user));

            assertEquals("q", filter("qwerty", user));
            assertEquals("Q", filter("QWERTYUIOP", user));
            assertEquals("v", filter("vbnm,", user));
            assertEquals("`", filter("`12", user));
            assertEquals("a1x7", filter("abc123xyz789", user));
        }
    }
}

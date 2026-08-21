/*
 * Copyright (c) 2016-2026 LabKey Corporation
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

import java.util.Comparator;

public class SortHelpers
{

    /**
     * Comparator that orders strings case-insensitively, but when two strings are equal ignoring case
     * breaks ties by preferring uppercase characters before lowercase characters.
     *
     * Example ordering: "Ab", "a", "B", "b"
     */
    public static final Comparator<String> CASE_INSENSITIVE_UPPERCASE_FIRST = new Comparator<>()
    {
        @Override
        public int compare(String s1, String s2)
        {
            if (s1 == s2) return 0;
            if (s1 == null) return -1;
            if (s2 == null) return 1;

            int len1 = s1.length();
            int len2 = s2.length();
            int min = Math.min(len1, len2);

            // Compare character-by-character using lowercased characters for primary ordering,
            // but if the lowercased chars are equal and the raw chars differ in case, prefer uppercase.
            for (int i = 0; i < min; i++)
            {
                char c1 = s1.charAt(i);
                char c2 = s2.charAt(i);
                char lc1 = Character.toLowerCase(c1);
                char lc2 = Character.toLowerCase(c2);

                // Primary: case-insensitive ordering
                if (lc1 != lc2)
                    return lc1 - lc2;

                // If same ignoring case but raw chars differ, prefer uppercase
                if (c1 != c2)
                {
                    boolean u1 = Character.isUpperCase(c1);
                    boolean u2 = Character.isUpperCase(c2);
                    if (u1 != u2)
                        return u1 ? -1 : 1;

                    // Fallback deterministic tie-breaker if necessary
                    int diff = c1 - c2;
                    if (diff != 0) return diff;
                }
            }

            // If identical up to min length, shorter string first (keeps deterministic ordering)
            return len1 - len2;
        }
    };

    // Natural sort ordering
    public static int compareNatural(Object obj1, Object obj2)
    {
        // converts null values to "null" for comparison purposes
        String s1 = String.valueOf(obj1);
        String s2 = String.valueOf(obj2);

        return compareNatural(s1, s2);
    }

    // Natural sort ordering, mostly taken from http://stackoverflow.com/a/27530518
    // Possibly improve by deleting spaces and/or leading zeroes and/or trailing zeroes?
    public static int compareNatural(String s1, String s2)
    {
        // assume null is less than any String value
        if(s1 == null)
        {
            if(s2 == null)
                return 0;
            else
                return -1;
        }
        else
        {
            if(s2 == null)
                return 1;
        }

        // Skip all identical characters
        int len1 = s1.length();
        int len2 = s2.length();
        int i;
        char c1, c2;
        // Lower-case chars to make sort case-insensitive
        for (i = 0, c1 = 0, c2 = 0; (i < len1) && (i < len2) && (c1 = Character.toLowerCase(s1.charAt(i))) == (c2 = Character.toLowerCase(s2.charAt(i))); i++);

        // Check end of string
        if (c1 == c2)
            return(len1 - len2);

        // Check digit in first string
        if (Character.isDigit(c1))
        {
            // Check digit only in first string
            if (!Character.isDigit(c2))
                return((i > 0) && Character.isDigit(s1.charAt(i - 1)) ? 1 : c1 - c2);

            // Scan all integer digits
            int x1, x2;
            for (x1 = i + 1; (x1 < len1) && Character.isDigit(s1.charAt(x1)); x1++);
            for (x2 = i + 1; (x2 < len2) && Character.isDigit(s2.charAt(x2)); x2++);

            // Longer integer wins, first digit otherwise
            return(x2 == x1 ? c1 - c2 : x1 - x2);
        }

        // Check digit only in second string
        if (Character.isDigit(c2))
            return((i > 0) && Character.isDigit(s2.charAt(i - 1)) ? -1 : c1 - c2);

        // No digits, so let's compare chars like Java does
        return(c1 - c2);
    }

    public static Comparator<String> getNaturalOrderStringComparator()
    {
        return SortHelpers::compareNatural;
    }

    public static Comparator<Object> getNaturalOrderObjectComparator()
    {
        return SortHelpers::compareNatural;
    }
}

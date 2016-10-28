/*
 * Copyright (c) 2016 LabKey Corporation
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

/**
 * Created by eyounske on 5/10/16.
 */
public class SortHelpers
{
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
        return (String s1, String s2) -> compareNatural(s1, s2);
    }

    public static Comparator<Object> getNaturalOrderObjectComparator()
    {
        return (Object obj1, Object obj2) -> compareNatural(obj1, obj2);
    }
}

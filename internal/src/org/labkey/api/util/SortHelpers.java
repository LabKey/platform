package org.labkey.api.util;

import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * Created by eyounske on 5/10/16.
 */
public class SortHelpers
{
    // Natural sort ordering
    public static int compare(Object obj1, Object obj2)
    {
        String s1 = obj1.toString();
        String s2 = obj2.toString();

        if (s1.equals(s2)) return 0;

        return compare(s1, s2);
    }

    // Natural sort ordering, mostly taken from http://stackoverflow.com/a/27530518
    // Possibly improve by deleting spaces and/or leading zeroes?
    public static int compare (String s1, String s2)
    {
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

        // No digits
        return(c1 - c2);
    }
}

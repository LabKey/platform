/*
 * Copyright (c) 2010-2012 LabKey Corporation
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * User: adam
 * Date: Mar 7, 2010
 * Time: 6:20:24 PM
 */
public class StringUtilsLabKey
{
    // Finds the longest common prefix of the passed in string collection.  In other words, the longest string (prefix)
    // such that, for all s in strings, s.startsWith(prefix).  An empty collection returns the empty string and a single
    // element collection returns that string.
    public static String findCommonPrefix(@NotNull Collection<String> strings)
    {
        if (strings.isEmpty())
            return "";

        List<String> list = new ArrayList<String>(strings);
        Collections.sort(list);

        if (strings.size() == 1)
            return list.get(0);

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


    public static class TestCase extends Assert
    {
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
    }
}

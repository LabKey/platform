package org.labkey.api.util;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * User: adam
 * Date: Mar 7, 2010
 * Time: 6:20:24 PM
 */
public class StringUtils
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
}

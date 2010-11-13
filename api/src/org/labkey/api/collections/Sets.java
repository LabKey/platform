package org.labkey.api.collections;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * User: adam
 * Date: Nov 12, 2010
 * Time: 4:25:37 PM
 */
public class Sets
{
    public static Set<String> newCaseInsensitiveHashSet()
    {
        return Collections.newSetFromMap(new CaseInsensitiveHashMap<Boolean>());
    }

    public static Set<String> newCaseInsensitiveHashSet(int count)
    {
        return Collections.newSetFromMap(new CaseInsensitiveHashMap<Boolean>(count));
    }

    public static Set<String> newCaseInsensitiveHashSet(String... values)
    {
        Set<String> set = newCaseInsensitiveHashSet(values.length);
        set.addAll(Arrays.asList(values));
        return set;
    }

    public static Set<String> newCaseInsensitiveHashSet(Collection<String> col)
    {
        Set<String> set = newCaseInsensitiveHashSet(col.size());
        set.addAll(col);
        return set;
    }
}

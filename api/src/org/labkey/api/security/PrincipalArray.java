package org.labkey.api.security;

import com.google.common.primitives.Ints;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/*
    Immutable wrapper that manages a list of principal IDs, ensuring it's sorted, searched efficiently, etc.
    Can represent a group's membership list OR user's list of group memberships.
 */
public class PrincipalArray implements Iterable<Integer>
{
    private static final PrincipalArray EMPTY_PRINCIPAL_ARRAY = new PrincipalArray(Collections.emptyList());

    // The set of principal IDs is fixed at construction time, so sort it and hold it in both array and list form
    // for convenience and performance.
    private final int[] _array;
    private final List<Integer> _list;

    public PrincipalArray(Collection<Integer> principals)
    {
        _array = new int[principals.size()];
        int i = 0;
        for (int principal : principals)
            _array[i++] = principal;
        Arrays.sort(_array);
        _list = Ints.asList(_array);
    }

    // Needed for pipeline deserialization
    public PrincipalArray()
    {
        _array = null;
        _list = null;
    }

    public boolean contains(int groupId)
    {
        return Arrays.binarySearch(_array, groupId) >= 0;
    }

    public Stream<Integer> stream()
    {
        return _list.stream();
    }

    @Override
    public String toString()
    {
        return "PrincipalArray" + Arrays.toString(_array);
    }

    public int size()
    {
        return _list.size();
    }

    @NotNull
    @Override
    public Iterator<Integer> iterator()
    {
        return _list.iterator();
    }

    // Returns an immutable list of principals
    public List<Integer> getList()
    {
        return _list;
    }

    public static PrincipalArray getEmptyPrincipalArray()
    {
        return EMPTY_PRINCIPAL_ARRAY;
    }
}

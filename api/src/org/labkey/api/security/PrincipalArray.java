package org.labkey.api.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Stream;

/*
    Simple wrapper that limits the ability to mutate the int[] and ensures it's sorted, searched efficiently, etc.
    Can represent a group's membership list OR user's list of group memberships.
 */
public class PrincipalArray
{
    private static final PrincipalArray EMPTY_PRINCIPAL_ARRAY = new PrincipalArray(Collections.emptyList());

    private final int[] _principals; // Always sorted

    public PrincipalArray(Collection<Integer> principals)
    {
        _principals = new int[principals.size()];
        int i = 0;
        for (int group : principals)
            _principals[i++] = group;
        Arrays.sort(_principals);
    }

    // Needed for pipeline deserialization
    public PrincipalArray()
    {
        _principals = null;
    }

    public boolean contains(int groupId)
    {
        return Arrays.binarySearch(_principals, groupId) >= 0;
    }

    // Package private: for now, security classes can access underlying int[] for performance
    int[] getPrincipals()
    {
        return _principals;
    }

    // Preferred method for accessing the principal IDs
    public Stream<Integer> stream()
    {
        return Arrays.stream(_principals).boxed();
    }

    public static PrincipalArray getEmptyPrincipalArray()
    {
        return EMPTY_PRINCIPAL_ARRAY;
    }

    @Override
    public String toString()
    {
        return "PrincipalArray" + Arrays.toString(_principals);
    }
}

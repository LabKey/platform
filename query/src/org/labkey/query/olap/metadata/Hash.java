package org.labkey.query.olap.metadata;

import org.jetbrains.annotations.NotNull;

/**
* Created by matthew on 9/8/14.
*/
public class Hash
{
    final boolean caseSensitive;

    Hash(boolean caseSensitive)
    {
        this.caseSensitive = caseSensitive;
    }

    long l=-1;

    long get()
    {
        return l;
    }

    void add(int i)
    {
        l *= 37;
        l += i;
    }
    void add(@NotNull String s)
    {
        if (caseSensitive)
            add(s.hashCode());
        else
            add(s.toUpperCase().hashCode());
    }
}

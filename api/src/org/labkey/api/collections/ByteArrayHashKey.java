package org.labkey.api.collections;

import java.util.Arrays;

/**
* User: adam
* Date: 1/12/12
* Time: 9:21 PM
*/

// Simple wrapper that allows use of byte[] as a HashMap key or member of a HashSet
public class ByteArrayHashKey
{
    private final byte[] _bytes;
    private final int _hashCode;

    public ByteArrayHashKey(byte[] bytes)
    {
        // Make a copy to prevent caller from modifying our internal byte[]
        _bytes = bytes.clone();
        _hashCode = Arrays.hashCode(_bytes);   // Calculate once for efficiency... can't allow modifications to _bytes
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ByteArrayHashKey that = (ByteArrayHashKey) o;

        return Arrays.equals(_bytes, that._bytes);
    }

    @Override
    public int hashCode()
    {
        return _hashCode;
    }

    // Return a copy to prevent callers from modifying our internal byte[]
    public byte[] getBytes()
    {
        return _bytes.clone();
    }
}

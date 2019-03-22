/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
package org.labkey.api.collections;

import java.util.Arrays;

/**
 * Simple wrapper that allows use of byte[] as a HashMap key or member of a HashSet
 * User: adam
 * Date: 1/12/12
 */
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

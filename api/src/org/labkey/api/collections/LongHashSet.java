/*
 * Copyright (c) 2025-2026 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Enforce that get(K) is always called with Long. */
public class LongHashSet extends HashSet<Long>
{
    public LongHashSet()
    {
        super();
    }

    public LongHashSet(int size)
    {
        super(size);
    }

    public LongHashSet(Set<Long> set)
    {
        super(set);
    }

    public LongHashSet(Collection<Long> col)
    {
        super(col);
    }

    private Long _long(Object key)
    {
        if (null != key && key.getClass() != Long.class)
            throw new IllegalStateException();
        return (Long)key;
    }

    @Override
    public boolean contains(Object o)
    {
        return super.contains(_long(o));
    }

    @Override
    public boolean remove(Object o)
    {
        return super.remove(_long(o));
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends Long> c)
    {
        boolean modified = false;
        for (Object o  : c)
            modified |= add(_long(o));
        return modified;
    }
}

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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Enforce that get(K) is always called with Long. */
public class IntHashSet extends HashSet<Integer>
{
    public IntHashSet()
    {
        super();
    }

    public IntHashSet(int size)
    {
        super(size);
    }

    public IntHashSet(Set<Integer> set)
    {
        super(set);
    }

    public IntHashSet(Collection<Integer> c)
    {
        super(c);
    }

    private Integer _int(Object key)
    {
        if (null != key && key.getClass() != Integer.class)
            throw new IllegalStateException();
        return (Integer)key;
    }

    @Override
    public boolean contains(Object o)
    {
        return super.contains(_int(o));
    }

    @Override
    public boolean remove(Object o)
    {
        return super.remove(_int(o));
    }
}

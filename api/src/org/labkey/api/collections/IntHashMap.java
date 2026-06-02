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

import java.util.HashMap;

/** Enforce that get(K) is always called with Integer. */
public class IntHashMap<V> extends HashMap<Integer, V>
{
    public IntHashMap()
    {
        super();
    }

    public IntHashMap(int size)
    {
        super(size);
    }

    private Integer _int(Object key)
    {
        if (null != key && key.getClass() != Integer.class)
            throw new IllegalStateException();
        return (Integer)key;
    }

    @Override
    public V get(Object key)
    {
        return super.get(_int(key));
    }

    @Override
    public V getOrDefault(Object key, V defaultValue)
    {
        return super.getOrDefault(_int(key), defaultValue);
    }

    @Override
    public boolean containsKey(Object key)
    {
        return super.containsKey(_int(key));
    }

    @Override
    public V remove(Object key)
    {
        return super.remove(_int(key));
    }
}

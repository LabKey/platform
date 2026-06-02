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
import java.util.Map;

/** Enforce that get(K) is always called with Long. */
public class LongHashMap<V> extends HashMap<Long, V>
{
    public LongHashMap()
    {
        super();
    }

    public LongHashMap(int size)
    {
        super(size);
    }

    public LongHashMap(Map<Long, V> map)
    {
        super(map);
    }

    private Long _long(Object key)
    {
        if (null != key && key.getClass() != Long.class)
            throw new IllegalStateException();
        return (Long)key;
    }

    @Override
    public V get(Object key)
    {
        return super.get(_long(key));
    }

    @Override
    public V getOrDefault(Object key, V defaultValue)
    {
        return super.getOrDefault(_long(key), defaultValue);
    }

    @Override
    public boolean containsKey(Object key)
    {
        return super.containsKey(_long(key));
    }

    @Override
    public V remove(Object key)
    {
        return super.remove(_long(key));
    }
}

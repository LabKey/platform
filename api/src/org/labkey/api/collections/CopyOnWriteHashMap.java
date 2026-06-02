/*
 * Copyright (c) 2023-2026 LabKey Corporation
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

/**
 * A thread-safe version of {@link HashMap} in which all operations that change the Map are implemented by making
 * a new copy of the underlying Map. This is appropriate for scenarios where reads vastly outnumber writes.
 */
public class CopyOnWriteHashMap<K, V> extends CopyOnWriteMap<K, V, HashMap<K, V>>
{
    public CopyOnWriteHashMap()
    {
    }

    public CopyOnWriteHashMap(int initialCapacity)
    {
        super(initialCapacity);
    }

    public CopyOnWriteHashMap(Map<K, V> data)
    {
        super(data);
    }

    @Override
    protected HashMap<K, V> newMap()
    {
        return new HashMap<>();
    }

    @Override
    protected HashMap<K, V> newMap(int initialCapacity)
    {
        return new HashMap<>(initialCapacity);
    }

    @Override
    protected HashMap<K, V> newMap(Map<K, V> data)
    {
        return new HashMap<>(data);
    }
}

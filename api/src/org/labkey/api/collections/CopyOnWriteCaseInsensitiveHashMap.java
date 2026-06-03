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

import java.util.Map;

/**
 * A thread-safe version of {@link CaseInsensitiveHashMap} in which all operations that change the Map are implemented
 * by making a new copy of the underlying Map. This is appropriate for scenarios where reads vastly outnumber writes.
 */
public class CopyOnWriteCaseInsensitiveHashMap<V> extends CopyOnWriteMap<String, V, CaseInsensitiveHashMap<V>> implements CaseInsensitiveCollection
{
    public CopyOnWriteCaseInsensitiveHashMap()
    {
    }

    public CopyOnWriteCaseInsensitiveHashMap(int initialCapacity)
    {
        super(initialCapacity);
    }

    public CopyOnWriteCaseInsensitiveHashMap(Map<String, V> data)
    {
        super(data);
    }

    @Override
    protected CaseInsensitiveHashMap<V> newMap()
    {
        return new CaseInsensitiveHashMap<>();
    }

    @Override
    protected CaseInsensitiveHashMap<V> newMap(int initialCapacity)
    {
        return new CaseInsensitiveHashMap<>(initialCapacity);
    }

    @Override
    protected CaseInsensitiveHashMap<V> newMap(Map<String, V> data)
    {
        return new CaseInsensitiveHashMap<>(data);
    }
}

/*
 * Copyright (c) 2024-2026 LabKey Corporation
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

import org.apache.commons.collections4.multimap.AbstractSetValuedMap;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// A concurrent multivalued map that's appropriate when each key is mapped to a small number of values
public class ConcurrentSetValuedMap<K, V> extends AbstractSetValuedMap<K, V>
{
    public ConcurrentSetValuedMap()
    {
        super(new ConcurrentHashMap<>());
    }

    @Override
    protected Set<V> createCollection()
    {
        // Be sure to review all usages of ConcurrentSetValuedMap before changing this implementation. Callers
        // currently expect the behavior of synchronizedSet().
        return Collections.synchronizedSet(new HashSet<>(5));
    }
}

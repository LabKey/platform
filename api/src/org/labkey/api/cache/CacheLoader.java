/*
 * Copyright (c) 2010-2026 LabKey Corporation
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

package org.labkey.api.cache;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Something that knows how to fetch a value when it's not yet available from a cache.
 */
public interface CacheLoader<K, V>
{
    V load(@NotNull K key, @Nullable Object argument);

    /**
     * Returns an object that can be used to synchronize access to the cache. Typically, the cache itself but it's
     * OK to swap with another lock object when running inside of a transaction to reduce the likelihood of deadlocks.
     */
    default Object getSyncObject(Cache<?, ?> cache)
    {
        return cache;
    }
}

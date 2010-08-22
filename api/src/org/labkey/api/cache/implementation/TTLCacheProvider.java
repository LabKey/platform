/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.api.cache.implementation;

import org.labkey.api.cache.BasicCache;
import org.labkey.api.cache.CacheProvider;
import org.labkey.api.util.MemTracker;

/**
 * User: adam
 * Date: Jul 8, 2010
 * Time: 10:35:38 AM
 */
public class TTLCacheProvider implements CacheProvider
{
    private static final CacheProvider INSTANCE = new TTLCacheProvider();

    public static CacheProvider getInstance()
    {
        return INSTANCE;
    }

    private TTLCacheProvider()
    {
    }

    @Override
    public <K, V> BasicCache<K, V> getBasicCache(String debugName, int limit, long defaultTimeToLive, boolean temporary)
    {
        BasicCache<K, V> cache = new CacheImpl<K, V>(limit, defaultTimeToLive);

        // No one should be holding onto temporary caches
        if (temporary)
            assert MemTracker.put(cache);

        return cache;
    }
}

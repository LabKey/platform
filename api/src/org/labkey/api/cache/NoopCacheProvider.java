/*
 * Copyright (c) 2016 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.Filter;

import java.util.Collections;
import java.util.Set;

import static org.labkey.api.cache.CacheType.DeterministicLRU;

/**
 * Created by adam on 4/19/2016.
 */
public class NoopCacheProvider implements CacheProvider
{
    @Override
    public <K, V> SimpleCache<K, V> getSimpleCache(String debugName, int limit, long defaultTimeToLive, long defaultTimeToIdle, boolean temporary)
    {
        return new NoopCache<>();
    }

    @Override
    public void shutdown()
    {
    }

    private static class NoopCache<K, V> implements SimpleCache<K, V>
    {
        @Override
        public void put(K key, V value)
        {
        }

        @Override
        public void put(K key, V value, long timeToLive)
        {
        }

        @Nullable
        @Override
        public V get(K key)
        {
            return null;
        }

        @Override
        public void remove(K key)
        {
        }

        @Override
        public int removeUsingFilter(Filter<K> filter)
        {
            return 0;
        }

        @Override
        public Set<K> getKeys()
        {
            return Collections.emptySet();
        }

        @Override
        public void clear()
        {
        }

        @Override
        public int getLimit()
        {
            return 0;
        }

        @Override
        public int size()
        {
            return 0;
        }

        @Override
        public boolean isEmpty()
        {
            return false;
        }

        @Override
        public long getDefaultExpires()
        {
            return 0;
        }

        @Override
        public void close()
        {
        }

        @Override
        public CacheType getCacheType()
        {
            return DeterministicLRU;
        }

        @Override
        public void log()
        {
        }
    }
}

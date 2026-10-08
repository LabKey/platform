/*
 * Copyright (c) 2026 LabKey Corporation
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

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

// A cache that accepts keys of one type but caches using keys of a different type. Functions are passed in to map the
// keys in both directions. The purpose is to allow cache calling code to work with cache keys that are convenient to
// use, but holding those keys for long periods of time is expensive or otherwise undesirable.
public class SimpleKeyMappingCache<K1, K2, V> implements SimpleCache<K1, V>
{
    private final SimpleCache<K2, V> _delegate;
    private final Function<K1, K2> _toKey;
    private final Function<K2, K1> _fromKey;

    public SimpleKeyMappingCache(@NotNull SimpleCache<K2, V> delegate, Function<K1, K2> toKey, Function<K2, K1> fromKey)
    {
        _delegate = delegate;
        _toKey = toKey;
        _fromKey = fromKey;
    }

    @Override
    public void put(K1 key, V value)
    {
        _delegate.put(_toKey.apply(key), value);
    }

    @Override
    public void put(K1 key, V value, long timeToLive)
    {
        _delegate.put(_toKey.apply(key), value, timeToLive);
    }

    @Override
    public @Nullable V get(K1 key)
    {
        return _delegate.get(_toKey.apply(key));
    }

    @Override
    public void remove(K1 key)
    {
        _delegate.remove(_toKey.apply(key));
    }

    @Override
    public int removeUsingFilter(Predicate<K1> filter)
    {
        return removeAll(getKeys().filter(filter));
    }

    @Override
    public Stream<K1> getKeys()
    {
        // A key whose underlying entity no longer exists (e.g., a deleted Container) maps to null
        return _delegate.getKeys()
            .map(_fromKey)
            .filter(Objects::nonNull);
    }

    @Override
    public void clear()
    {
        _delegate.clear();
    }

    @Override
    public int getLimit()
    {
        return _delegate.getLimit();
    }

    @Override
    public int size()
    {
        return _delegate.size();
    }

    @Override
    public int getExpirations()
    {
        return _delegate.getExpirations();
    }

    @Override
    public int getEvictions()
    {
        return _delegate.getEvictions();
    }

    @Override
    public boolean isEmpty()
    {
        return _delegate.isEmpty();
    }

    @Override
    public long getDefaultExpires()
    {
        return _delegate.getDefaultExpires();
    }

    @Override
    public void close()
    {
        _delegate.close();
    }

    @Override
    public CacheType getCacheType()
    {
        return _delegate.getCacheType();
    }

    @Override
    public void log()
    {
        _delegate.log();
    }
}

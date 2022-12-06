/*
 * Copyright (c) 2010-2019 LabKey Corporation
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

public class CacheStats implements Comparable<CacheStats>
{
    private final String _description;
    @Nullable
    private final StackTraceElement[] _stackTrace;
    private final int _limit;
    private final long _expirations;
    private final long _evictions;

    private final long _gets;
    private final long _misses;
    private final long _puts;
    private final long _removes;
    private final long _clears;
    private final long _maxSize;

    private final long _size;

    public CacheStats(TrackingCache<?, ?> cache, Stats stats, int size)
    {
        _description = cache.getDebugName();
        _stackTrace = cache.getCreationStackTrace();
        _limit = cache.getLimit();
        _expirations = cache.getExpirations();
        _evictions = cache.getEvictions();

        _gets = stats.gets.get();
        _misses = stats.misses.get();
        _puts = stats.puts.get();
        _removes = stats.removes.get();
        _clears = stats.clears.get();
        _maxSize = stats.max_size.get();

        _size = size;
    }

    public String getDescription()
    {
        return _description;
    }

    @Nullable
    public StackTraceElement[] getCreationStackTrace()
    {
        return _stackTrace;
    }

    public long getSize()
    {
        return _size;
    }

    public long getMaxSize()
    {
        return _maxSize;
    }

    public Long getLimit()
    {
        if (CacheManager.UNLIMITED == _limit)
            return null;

        return (long) _limit;
    }

    public long getGets()
    {
        return _gets;
    }

    public long getMisses()
    {
        return _misses;
    }

    public long getPuts()
    {
        return _puts;
    }

    public long getRemoves()
    {
        return _removes;
    }

    public long getClears()
    {
        return _clears;
    }

    public long getExpirations()
    {
        return _expirations;
    }

    public long getEvictions()
    {
        return _evictions;
    }

    public double getMissRatio()
    {
        long gets = getGets();
        return 0 != gets ? getMisses() / (double)gets : 0;
    }

    @Override
    public int compareTo(CacheStats cs2)
    {
        return Double.compare(cs2.getMissRatio(), getMissRatio());   // Highest to lowest miss ratio
    }
}

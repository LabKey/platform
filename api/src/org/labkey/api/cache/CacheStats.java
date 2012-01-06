/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

public class CacheStats implements Comparable<CacheStats>
{
    private final String _description;
    private final StackTraceElement[] _stackTrace;
    private final long _gets;
    private final long _misses;
    private final long _puts;
    private final long _expirations;
    private final long _removes;
    private final long _clears;
    private final long _size;
    private final long _maxSize;
    private final int _limit;


    public CacheStats(String description, StackTraceElement[] stackTrace, Stats stats, int size, int limit)
    {
        this(description, stackTrace, stats.gets.get(), stats.misses.get(), stats.puts.get(), stats.expirations.get(), stats.removes.get(), stats.clears.get(), size, stats.max_size.get(), limit);
    }

    private CacheStats(String description, StackTraceElement[] stackTrace, long gets, long misses, long puts, long expirations, long removes, long clears, long size, long maxSize, int limit)
    {
        _description = description;
        _stackTrace = stackTrace;
        _gets = gets;
        _misses = misses;
        _puts = puts;
        _expirations = expirations;
        _removes = removes;
        _clears = clears;
        _size = size;
        _maxSize = maxSize;
        _limit = limit;
    }

    public String getDescription()
    {
        return _description;
    }

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

    public double getMissRatio()
    {
        long gets = getGets();
        return 0 != gets ? getMisses() / (double)gets : 0;
    }

    public int compareTo(CacheStats cs2)
    {
        return Double.compare(cs2.getMissRatio(), getMissRatio());   // Highest to lowest miss ratio
    }
}

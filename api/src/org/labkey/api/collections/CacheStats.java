/*
 * Copyright (c) 2009 LabKey Corporation
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

public class CacheStats implements Comparable<CacheStats>
{
    private final String _description;
    private final long _hits;
    private final long _misses;
    private final long _puts;
    private final long _expirations;
    private final long _removes;
    private final long _size;


    public CacheStats(String description, long hits, long misses, long puts, long expirations, long removes, long size)
    {
        _description = description;
        _hits = hits;
        _misses = misses;
        _puts = puts;
        _expirations = expirations;
        _removes = removes;
        _size = size;
    }

    public String getDescription()
    {
        return _description;
    }

    public long getHits()
    {
        return _hits;
    }

    public long getMisses()
    {
        return _misses;
    }

    public long getPuts()
    {
        return _puts;
    }

    public long getTotal()
    {
        return _hits + _misses;
    }

    public double getMissRatio()
    {
        long total = getTotal();
        return 0 != total ? getMisses() / (double)total : 0;
    }

    public long getSize()
    {
        return _size;
    }

    public long getExpirations()
    {
        return _expirations;
    }

    public long getRemoves()
    {
        return _removes;
    }

    public int compareTo(CacheStats cs2)
    {
        return Double.compare(cs2.getMissRatio(), getMissRatio());   // Highest to lowest miss ratio
    }
}

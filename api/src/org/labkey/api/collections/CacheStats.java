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
    private final long _gets;
    private final long _misses;
    private final long _puts;
    private final long _expirations;
    private final long _removes;
    private final long _clears;
    private final long _size;


    public CacheStats(String description, long gets, long misses, long puts, long expirations, long removes, long clears, long size)
    {
        _description = description;
        _gets = gets;
        _misses = misses;
        _puts = puts;
        _expirations = expirations;
        _removes = removes;
        _clears = clears;
        _size = size;
    }

    public String getDescription()
    {
        return _description;
    }

    public long getSize()
    {
        return _size;
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

/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.util;

import static java.lang.Math.max;

/** Not thread safe */

// C is the class that does the accumulation, E is the element that gets accumulated
// Each subclass decides how to accumulate these elements and how to return the total count
public abstract class RateAccumulator<C, E>
{
    private final long _start;
    protected C _counter;

    public RateAccumulator(long start, C counter)
    {
        _start = start;
        _counter = counter;
    }

    public long getStart()
    {
        return _start;
    }

    public C getCounter()
    {
        return _counter;
    }

    double getRate(long now)
    {
        return (double)getCount() / max(1000, now - getStart());
    }

    long getDelay(long now, Rate target)
    {
        if (getRate(now) <= target.getRate())
            return 0;
        double remainder = ((double)getCount() / target.getRate()) - (now - getStart());
        return (long)max(0, remainder);
    }

    public abstract void accumulate(E add);
    public abstract long getCount();
}

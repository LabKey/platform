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

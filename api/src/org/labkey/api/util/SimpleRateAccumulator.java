package org.labkey.api.util;

/**
 * User: adam
 * Date: 6/11/13
 * Time: 11:31 AM
 */

// Basic case... the counter is a Long that accumulates Longs via addition
public class SimpleRateAccumulator extends RateAccumulator<Long, Long>
{
    public SimpleRateAccumulator(long start)
    {
        this(start, 0L);
    }

    public SimpleRateAccumulator(long start, long count)
    {
        super(start, count);
    }

    @Override
    public void accumulate(Long add)
    {
        _counter += add;
    }

    @Override
    public long getCount()
    {
        return _counter;
    }
}

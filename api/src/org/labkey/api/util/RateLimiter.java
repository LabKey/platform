/*
 * Copyright (c) 2010-2016 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.min;

/**
 * User: matthewb
 * Date: Jan 14, 2010
 * Time: 10:01:10 AM
 */

public class RateLimiter
{
    static final Logger _log = Logger.getLogger(RateLimiter.class);

    final String _name;
    final Rate _target;
    boolean useSystem = false; // for small intervals or testing
    long minPause;
    long maxPause;

    // the interval over which history is gathered, and rate is enforced
    final long historyInterval;
    SimpleRateAccumulator _long;

    // size of sub-windows within the history interval
    final long accumulateInterval;
    SimpleRateAccumulator _short;

    // collection of 'short' intervals
    ArrayList<RateAccumulator> _history = new ArrayList<>(4);

    public RateLimiter(String name, Rate rate)
    {
        this(name, rate, 60000, 0);
    }

    public RateLimiter(String name, long count, TimeUnit unit)
    {
        this(name, new Rate(count, 1, unit), 60000, 0);
    }

    // set accum small to avoid jumpiness (testing)
    public RateLimiter(String name, Rate rate, long history, long accum)
    {
        _name = name;
        _target = rate;
        if (history < TimeUnit.SECONDS.toMillis(20))
            useSystem = true;
        long now = currentTimeMillis();
        _short = new SimpleRateAccumulator(now);
        _long = new SimpleRateAccumulator(now);
        historyInterval = history;
        accumulateInterval = 0==accum ? history/3 : accum;
        minPause = 200;
        maxPause = history;
    }


    public void setMaxPause(long ms)
    {
        maxPause = ms;
    }


    public Rate getTarget()
    {
        return _target;
    }
    

    private SimpleRateAccumulator aggregateRate(long now)
    {
        long start = now;
        long count = 0;
        for (RateAccumulator a : _history)
        {
            if (a.getStart() + historyInterval < now)
                continue;
            start = min(start, a.getStart());
            count += a.getCount();
        }
        return new SimpleRateAccumulator(start, count);
    }


    private long currentTimeMillis()
    {
        return useSystem ? System.currentTimeMillis() : HeartBeat.currentTimeMillis();
    }


    @Override
    public String toString()
    {
        return "RateLimiter:" + _name + " " + _target.toString();
    }


   /*
    * RateLimiter.add() is thread-safe
    * returns how far (in ms) we are ahead of the target rate
    */
    public synchronized long add(long count, boolean wait)
    {
        long delay = _updateCounts(count);
        if (!wait)
            return delay;
        return _pause(delay);
    }


    private long _pause(long delay)
    {
        if  (delay < minPause)
            return delay;
        try { this.wait(min(delay,maxPause)); } catch (InterruptedException x) { /* */}
        return getDelay();
    }


    private synchronized long getDelay()
    {
        return _long.getDelay(currentTimeMillis(), _target);
    }


    private synchronized long _updateCounts(long count)
    {
        long now = currentTimeMillis();
        if (_short.getStart() + accumulateInterval < now)
        {
            while (!_history.isEmpty())
            {
                RateAccumulator last = _history.get(_history.size()-1);
                if (last.getStart() + accumulateInterval > now - historyInterval)
                    break;
                _history.remove(_history.size()-1);
            }
            _history.add(0, _short);
            _short = new SimpleRateAccumulator(now);
            _long = aggregateRate(now); // consider: reuse RateAccumulator instead of new
        }
        _short.accumulate(count);
        _long.accumulate(count);
        return _long.getDelay(now, _target);
    }


    public static class TestCase extends Assert
    {
        private static final double DELTA = 1E-8;

        long _end = 0;

        @org.junit.Test
        public void test()
        {
            final RateLimiter l = new RateLimiter("test", new Rate(1, TimeUnit.MILLISECONDS), 10000, 500);
            l.minPause = 1;
            assertEquals("RateLimiter:test 1/MILLISECOND", l.toString());
            assertEquals(1000.0, l.getTarget().getRate(TimeUnit.SECONDS), DELTA);

            Runnable run = new Runnable()
            {
                public void run()
                {
                    while (System.currentTimeMillis() < _end)
                    {
                        l.add(1,true);
                        l.add(4,true);
                        l.add(2,true);
                        l.add(5,true);
                    }
                }
            };
            Thread[] threads = new Thread[4];
            for (int i=0 ; i<4 ; i++)
                threads[i] = new Thread(run);

            _end = System.currentTimeMillis() + 5000;
            for (int i=0 ; i<4 ; i++)
                threads[i].start();
            for (int i=0 ; i<4 ; i++)
                try {threads[i].join(20000);}catch(InterruptedException x){}

            // count should be about 1.0
            RateAccumulator counter = l._long;
            double a = counter.getRate(_end);
            assertTrue(a < 2.0);
            assertTrue(a > 0.1);
        }

        @org.junit.Test
        public void test2()
        {
            final RateLimiter l = new RateLimiter("test",new Rate(1,TimeUnit.SECONDS),10000,500);
            long start = System.currentTimeMillis();
            for (int i=0 ; i<10 ; i++)
            {
                l.add(1,true);
            }
            long finish = System.currentTimeMillis();
            long duration = finish-start;
            assertTrue(duration > 5000);
            assertTrue(duration < 15000);
        }
    }
}
    
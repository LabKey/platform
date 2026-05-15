/*
 * Copyright (c) 2012-2019 LabKey Corporation
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

import org.junit.Assert;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.min;

/// Enforces a maximum throughput over a sliding time window.
///
/// Callers accumulate units of work (bytes, requests, operations) against a target [Rate].
/// Internally, time is divided into sub-windows that are rotated as time passes, so the
/// enforced rate reflects recent activity rather than an all-time average.
///
/// ## Throttling a background thread
///
/// Use [#add(long)] to block until the rate budget allows. The return value is the
/// number of milliseconds spent waiting.
///
/// ```java
/// var limiter = new RateLimiter("file io", 1_000_000, TimeUnit.SECONDS); // 1 MB/s
/// for (File f : files) {
///     limiter.add(f.length()); // blocks if over rate
///     index(f);
/// }
/// ```
///
/// ## Recording without blocking
///
/// Use [#tryAdd(long)] to accumulate without pausing — for threads that should track
/// rate but never stall:
///
/// ```java
/// limiter.tryAdd(1); // record the event, return current delay in ms
/// ```
///
/// ## Probing the current delay
///
/// Use [#getDelay()] to check how far ahead of the target rate the limiter is,
/// without accumulating or blocking:
///
/// ```java
/// if (limiter.getDelay() > THRESHOLD_MS)
///     return TOO_MANY_REQUESTS;
/// ```
public class RateLimiter
{
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


    /** Accumulate {@code count} units and block until the rate budget allows. Returns ms spent waiting. */
    public synchronized long add(long count)
    {
        return _pause(_updateCounts(count));
    }

    /** Accumulate {@code count} units without blocking. Returns how far ahead of the target rate we are (ms). */
    public synchronized long tryAdd(long count)
    {
        return _updateCounts(count);
    }

    /** @deprecated Use {@link #add(long)} or {@link #tryAdd(long)} */
    @Deprecated
    public synchronized long add(long count, boolean wait)
    {
        return wait ? add(count) : tryAdd(count);
    }


    private long _pause(long delay)
    {
        if  (delay < minPause)
            return delay;
        try { this.wait(min(delay,maxPause)); } catch (InterruptedException x) { /* */}
        return getDelay();
    }


    public synchronized long getDelay()
    {
        return _long.getDelay(currentTimeMillis(), _target);
    }

    public synchronized long getCount() { return _long.getCount(); }


    private synchronized long _updateCounts(long count)
    {
        long now = currentTimeMillis();
        if (_short.getStart() + accumulateInterval < now)
        {
            while (!_history.isEmpty())
            {
                RateAccumulator last = _history.getLast();
                if (last.getStart() + accumulateInterval > now - historyInterval)
                    break;
                _history.removeLast();
            }
            _history.addFirst(_short);
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

        @org.junit.Test
        public void test()
        {
            final RateLimiter l = new RateLimiter("test", new Rate(1, TimeUnit.MILLISECONDS), 10000, 500);
            l.minPause = 1;
            assertEquals("RateLimiter:test 1/MILLISECOND", l.toString());
            assertEquals(1000.0, l.getTarget().getRate(TimeUnit.SECONDS), DELTA);

            long end = System.currentTimeMillis() + 5000;
            Runnable run = () ->
            {
                while (System.currentTimeMillis() < end)
                {
                    l.add(1);
                    l.add(4);
                    l.add(2);
                    l.add(5);
                }
            };
            Thread[] threads = new Thread[4];
            for (int i=0 ; i<4 ; i++)
                threads[i] = new Thread(run);
            for (int i=0 ; i<4 ; i++)
                threads[i].start();
            for (int i=0 ; i<4 ; i++)
                try {threads[i].join(20000);} catch (InterruptedException x) {}

            // target is 1/ms; after ~5s count should be roughly 5000
            double rate = (double) l.getCount() / 5000.0;
            assertTrue(rate < 2.0);
            assertTrue(rate > 0.1);
        }

        @org.junit.Test
        public void test2()
        {
            final RateLimiter l = new RateLimiter("test", new Rate(1, TimeUnit.SECONDS), 10000, 500);
            long start = System.currentTimeMillis();
            for (int i=0 ; i<10 ; i++)
                l.add(1);
            long duration = System.currentTimeMillis() - start;
            assertTrue(duration > 5000);
            assertTrue(duration < 15000);
        }

        @org.junit.Test
        public void testTryAdd()
        {
            // history < 20s so useSystem=true; accum=500 for fast sub-window turnover
            RateLimiter l = new RateLimiter("test", new Rate(10, TimeUnit.SECONDS), 5000, 500);

            // Under rate: 5 units against a 10/s target — the 1s rate floor means this reads as under-rate
            assertEquals(0, l.tryAdd(5));

            // Way over rate: must return positive delay without blocking
            long start = System.currentTimeMillis();
            long delay = l.tryAdd(10000);
            assertTrue("tryAdd must not block", System.currentTimeMillis() - start < 200);
            assertTrue("should report delay when over rate", delay > 0);
        }

        @org.junit.Test
        public void testGetDelay()
        {
            RateLimiter l = new RateLimiter("test", new Rate(10, TimeUnit.SECONDS), 5000, 500);
            assertEquals("fresh limiter has no delay", 0, l.getDelay());
            l.tryAdd(10000);
            assertTrue("over-rate limiter should report positive delay", l.getDelay() > 0);
        }

        @org.junit.Test
        public void testGetCount()
        {
            RateLimiter l = new RateLimiter("test", new Rate(1, TimeUnit.SECONDS), 5000, 500);
            l.tryAdd(7);
            l.tryAdd(3);
            assertEquals(10, l.getCount());
        }
    }
}
    
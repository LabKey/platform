/*
 * Copyright (c) 2010 LabKey Corporation
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

import junit.framework.Test;
import junit.framework.TestSuite;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Jan 14, 2010
 * Time: 10:01:10 AM
 */

public class RateLimiter
{
    final Rate _target;
    ArrayList<RateAccumulator> _history = new ArrayList<RateAccumulator>(4);
    RateAccumulator _short;
    RateAccumulator _long;

    /* this roughly corresponds to the interval over which history is gathered */
    final long historyInterval;
    final long accumulateInterval;
    long minPause = 500;

    public RateLimiter(long count, long duration)
    {
        this(count,duration,60000);
    }

    public RateLimiter(long count, long duration, long history)
    {
        this._target = new Rate(count,duration,TimeUnit.MILLISECONDS);
        long now = System.currentTimeMillis();
        _short = new RateAccumulator(now);
        _long = new RateAccumulator(now);
        historyInterval = history;
        accumulateInterval = history/3;
    }

    private final RateAccumulator aggregateRate(long now)
    {
        long start = now;
        long count = 0;
        for (RateAccumulator a : _history)
        {
            if (a._start + historyInterval < now)
                continue;
            start = Math.min(start,a._start);
            count += a._count;
        }
        return new RateAccumulator(start,count);
    }


    public long add(int count, boolean wait)
    {
        long delay;
        
        synchronized (this)
        {
            long now = System.currentTimeMillis();
            if (_short._start + accumulateInterval < now)
            {
                int size = _history.size();
                if (size > 3)
                    _history.remove(size-1);
                _history.add(0, _short);
                _short = new RateAccumulator(now);
                _long = aggregateRate(now);
            }
            _short.accumulate(count);
            _long.accumulate(count);
            delay = _long.getDelay(now,_target);
        }
        
        if (wait && delay > minPause)
        {
            do
            {
                try {Thread.sleep(delay);}catch(InterruptedException x){}
                synchronized (this)
                {
                    delay = _long.getDelay(System.currentTimeMillis(), _target);
                }
            }
            while (delay > 0);
        }
        return delay;
    }


    static class Rate
    {
        double _rate;
        Rate(long count, long duration, TimeUnit unit)
        {
            _rate = (double)count / (double)unit.toMillis(duration);
        }
    }


    static class RateAccumulator
    {
        final long _start;
        long _count = 0;
        RateAccumulator(long now)
        {
            _start = now;
        }
        RateAccumulator(long start, long count)
        {
            _start = start;
            _count = count;
        }
        void accumulate(int add)
        {
            _count += add;
        }
        double getRate(long now)
        {
            return now == 0 ? (double)_count : (double)_count / Math.max(1, now-_start);
        }
        long getDelay(long now, Rate target)
        {
            if (getRate(now) <= target._rate)
                return 0;
            double remainder = (double)_count/target._rate + _start - now;
            return (long)Math.max(0,remainder);
        }
    }


    public static class TestCase extends junit.framework.TestCase
    {
        public TestCase()
        {
            super();
        }

        public TestCase(String name)
        {
            super(name);
        }

        long _end = 0;
        
        public void test()
        {
            final RateLimiter l = new RateLimiter(1,1,1000);
            l.minPause = 1;

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

        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }
}
    
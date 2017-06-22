/*
 * Copyright (c) 2016-2017 LabKey Corporation
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

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.min;

/**
 * Created by xingyang on 12/12/16.
 */
public class CountLimiter
{
    static final Logger _log = Logger.getLogger(CountLimiter.class);

    final String _name;
    boolean useSystem = false; // for small intervals or testing

    // the interval over which history is gathered, and count is calculated
    final long historyInterval;
    SimpleRateAccumulator _long;

    // size of sub-windows within the history interval
    final long accumulateInterval;
    SimpleRateAccumulator _short;

    // the count limit for history period
    long _countLimit;

    // the timestamp of the last item added that exceeds countLimit in history period
    long _limitReachedTimeStamp;

    // collection of 'short' intervals
    ArrayList<RateAccumulator> _history = new ArrayList<>(4);


    // set accum small to avoid jumpiness
    public CountLimiter(String name, long history, long accum, long countLimit)
    {
        _name = name;
        if (history < TimeUnit.SECONDS.toMillis(20))
            useSystem = true;
        long now = currentTimeMillis();
        _short = new SimpleRateAccumulator(now);
        _long = new SimpleRateAccumulator(now);
        _limitReachedTimeStamp = 0;
        _countLimit = countLimit;
        historyInterval = history;
        accumulateInterval = 0==accum ? history/3 : accum;
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
        return "CountLimiter:" + _name;
    }

    /*
     * CountLimiter.add() is thread-safe
     * Each time add is called, the limiter will update _limitReachedTimeStamp either to now if the countLimit has been reached, or to 0 otherwise.
     */
    public synchronized void add(long count)
    {
        _updateCounts(count);
    }

    private synchronized void _updateCounts(long count)
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
            _long = aggregateRate(now);
        }
        _short.accumulate(count);
        _long.accumulate(count);

        if (_long.getCount() >= _countLimit)
        {
            _limitReachedTimeStamp = now;
        }
        else
        {
            _limitReachedTimeStamp = 0;
        }
    }

    public SimpleRateAccumulator getLong()
    {
        return _long;
    }

    public long getLimitReachedTimeStamp()
    {
        return _limitReachedTimeStamp;
    }

    public void reset()
    {
        long now = currentTimeMillis();
        _short = new SimpleRateAccumulator(now);
        _long = new SimpleRateAccumulator(now);
        _limitReachedTimeStamp = 0;
    }

}
    
/*
 * Copyright (c) 2004-2018 Fred Hutchinson Cancer Research Center
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Lightweight utility to capture how much time is spent doing specifically instrumented actions.
 */
public class CPUTimer
{
    private static final Logger _log = LogManager.getLogger(CPUTimer.class);

	//
	// cumulative timers
	//

	private static final WeakHashMap<CPUTimer, Object> timers = new WeakHashMap<>();

	private final String _name;
	private long _cumulative = 0;
    private long _min = Long.MAX_VALUE;
    private long _max = Long.MIN_VALUE;
    private long _first = 0;
    private long _last = 0;
	private long _start = 0;
    private long _stop = 0;
	private int _calls = 0;

	public CPUTimer(String name)
    {
		synchronized(timers)
        {
			if (null == name)
				name = "timer " + timers.size();
			_name = name;
			timers.put(this, null);
        }
    }

	public boolean start()
    {
        _stop = 0;
		_start = System.nanoTime();
		return true;
    }

    public long getStart()
    {
        return _start;
    }

    public boolean started()
    {
        return _start > 0;
    }

    /**
     * Will stop the timer and save all accumulated data (duration, max, min etc...).
     *
     * @return Always returns true.
     */
	public boolean stop()
    {
		_stop = System.nanoTime();
        save();
		return true;
    }

    /**
     * Will stop the timer but will not save any information (duration, min, max, etx...). The information is persisted
     * can can be saved by calling the save method. If the timer is stopped by this method and then start is called again
     * before the save method is called, the old information will be lost.
     *
     * @return Always returns true.
     */
    public boolean stopWithoutSave()
    {
        _stop = System.nanoTime();
        return true;
    }

    /**
     * Will save the various information (duration, max, min, etc...) if not already saved. Should be called if
     * stopWithoutSave is called.
     *
     * @return Always returns true.
     */
    public boolean save()
    {
        if (_stop > _start)
            _update(_stop - _start);
        _start = 0;
        _stop = 0;
        return true;
    }

    protected void _update(long elapsed)
    {
        synchronized(timers)
        {
            _cumulative += elapsed;
            _min = Math.min(_min, elapsed);
            _max = Math.max(_max, elapsed);
            if (_first == 0)
            {
                _first = elapsed;
            }
            _last = elapsed;
            _calls++;
        }
    }

    public boolean saveTo(CPUTimer accumulator)
    {
        if (_stop > _start)
        {
            synchronized (accumulator)
            {
                accumulator._update(_stop-_start);
            }
        }
        _start = 0;
        _stop = 0;
        return true;
    }

    /**
     * Reset all the values in the timer.
     *
     * @return Always returns true.
     */
	public boolean clear()
    {
        synchronized(timers)
        {
            _cumulative = 0;
            _min = Long.MAX_VALUE;
            _max = Long.MIN_VALUE;
            _first = 0;
            _last = 0;
            _start = 0;
            _stop = 0;
            _calls = 0;
        }
        return true;
    }

    /**
     * Remove this time from the internal collection of timers.
     *
     * @return Always return true.
     */
    public boolean remove()
    {
        this.stop();
        clear();
        synchronized(timers)
        {
            timers.remove(this);
        }
        return true;
    }

    public String getName()
    {
        return _name;
    }

    /**
     * @return Total amount of time, all start/stops, in nanoseconds.
     */
	public long getTotal()
    {
        return _cumulative;
    }

    /**
     * @return Shortest start/stop call in nanoseconds.
     */
    public long getMin()
    {
        return _min;
    }

    public long getMinMilliseconds()
    {
        return (long)(_min * msFactor);
    }

    /**
     * @return Longest start/stop call in nanoseconds.
     */
    public long getMax()
    {
        return _max;
    }

    public long getMaxMilliseconds()
    {
        return (long)(_max * msFactor);
    }

    /**
     * Get the elapsed time of the first start/stop call. Returns nanoseconds.
     *
     * @return Duration in nanoseconds of first start/stop call.
     */
    public long getFirst()
    {
        return _first;
    }

    /**
     * Get the elapsed time of the first start/stop call. Returns milliseconds.
     *
     * @return Duration in milliseconds of first start/stop call.
     */
    public long getFirstMilliseconds()
    {
        return (long)(_first * msFactor);
    }

    /**
     * Get the elapsed time of the last start/stop call. Returns nanoseconds.
     *
     * @return Duration in nanoseconds of last start/stop call.
     */
    public long getLast()
    {
        return _last;
    }

    /**
     * Get the elapsed time of the last start/stop call. Returns milliseconds.
     *
     * @return Duration in milliseconds of last start/stop call.
     */
    public long getLastMilliseconds()
    {
        return (long)(_last * msFactor);
    }

    /**
     * Get the total amount of time, including all start and stops, in milliseconds.
     *
     * @return Total amount of time in milliseconds.
     */
    public long getTotalMilliseconds()
    {
        return (long)(_cumulative * msFactor);
    }

    /**
     * Get the total amount of milliseconds, including all start and stops, in a formatted string.
     *
     * @return Formatted amount of time in milliseconds.
     */
    public String getDuration()
    {
        return DateUtil.formatDuration(getTotalMilliseconds());
    }

    /**
     * @return Get average in milliseconds.
     */
    public float getAverage()
    {
        return _calls == 0 ? 0 : (float)getTotalMilliseconds() / _calls;
    }

    public long getCalls()
    {
        return _calls;
    }

    /**
     * @return Output the timer information in milliseconds.
     */
    public static String dumpAllTimers()
    {
		synchronized(timers)
        {
			Set<CPUTimer> set = timers.keySet();
			CPUTimer[] a = set.toArray(new CPUTimer[set.size()]);

			Arrays.sort(a, Comparator.comparing(o -> o._name));

            StringBuilder sb = new StringBuilder();
            sb.append("TIMER SUMMARY: ").append(new Date().toString()).append("\n");
            sb.append(header());
            sb.append("\n");
            for (CPUTimer cpuTimer : a)
            {
                sb.append(format(cpuTimer));
                sb.append("\n");
            }
            logDebug(sb);
            return sb.toString();
        }
    }

    // Converts nanoseconds into milliseconds.
    private static final double msFactor = 1.0e-6;

    public static String header()
    {
        return String.format("%20s\t%12s\t%12s\t%12s\t%12s\t%12s\t%12s\t%12s",
            "", "cumulative", "min", "max", "first", "last", "average", "calls");
    }

    /**
     * @return Output the time in milliseconds.
     */
    public static String format(CPUTimer t)
    {
        double ms = t._cumulative * msFactor;
        return String.format("%20s\t%12f\t%12f\t%12f\t%12f\t%12f\t%12f\t%12d",
                t._name,
                ms,
                t._min * msFactor,
                t._max * msFactor,
                t._first * msFactor,
                t._last * msFactor,
                t.getAverage(),
                t._calls);
    }

	@Override
	public String toString()
    {
        return format(this);
    }

	private static void format(Object l, int width, StringBuilder sb)
    {
		String s = String.valueOf(l);
		for (int p=width-s.length() ; p>0 ; p--)
			sb.append(' ');
		sb.append(s);
    }

    private static void logDebug(CharSequence s)
    {
        _log.debug(s);
    }

    public static void main(String[] args)
    {
        CPUTimer timerA = new CPUTimer("timerA");
        double x = 0;
        timerA.start();
        for (int i=0 ;i<1000; i++)
            x = x * Math.sin(x*i);
        timerA.stop();
        CPUTimer.dumpAllTimers();

        CPUTimer calibrate = new CPUTimer("test");
        long a = System.currentTimeMillis();
        calibrate.start();
        try {Thread.sleep(10000);} catch(Exception ex){}
        long b = System.currentTimeMillis();
        calibrate.stop();
        double f = (double)calibrate.getTotal() / (double)(b-a);
        System.err.println(f);
        assert msFactor*.999 < f && f < msFactor * 1.001;
    }

}

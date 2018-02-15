/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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

import java.util.*;

/**
 * Lightweight utility to capture how much time is spent doing specifically instrumented actions.
 */
public class CPUTimer
{
    private static final Logger _log = Logger.getLogger(CPUTimer.class);


	//
	// cumulative timers
	//

	private static final WeakHashMap<CPUTimer, Object> timers = new WeakHashMap<>();

	private final String _name;
	private long _cumulative = 0;
    private long _min = Long.MAX_VALUE;
    private long _max = Long.MIN_VALUE;
	private long _start = 0;
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

	public boolean stop()
    {
		long stop = System.nanoTime();
		if (stop > _start)
        {
            long elapsed = (stop - _start);
			_cumulative += elapsed;
			_min = Math.min(_min, elapsed);
			_max = Math.max(_max, elapsed);
			_calls++;
        }
		_start = 0;
		return true;
    }


	public boolean clear()
    {
		_cumulative = 0;
        return true;
    }

    public String getName()
    {
        return _name;
    }

	public long getTotal()
    {
        return _cumulative;
    }

    public long getMin()
    {
        return _min;
    }

    public long getMax()
    {
        return _max;
    }


    public long getTotalMilliseconds()
    {
        return (long)(_cumulative * msFactor);
    }


    public String getDuration()
    {
        return DateUtil.formatDuration(getTotalMilliseconds());
    }
    

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
            for (CPUTimer cpuTimer : a)
            {
                sb.append(format(cpuTimer));
                sb.append("\n");
            }
            logDebug(sb);
            return sb.toString();
        }
    }


    private static final double msFactor = 1.0e-6;

    public static String header()
    {
        return String.format("%20s\t%12s\t%12s\t%12s\t%12s\t%12s",
            "", "cumulative", "min", "max", "average", "calls");
    }

    public static String format(CPUTimer t)
    {
        double ms = t._cumulative * msFactor;
        return String.format("%20s\t%12f\t%12f\t%12f\t%12f\t%12d",
                t._name,
                ms,
                t._min * msFactor,
                t._max * msFactor,
                (t._calls == 0 ? 0 : ms / t._calls),
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

/*
 * Copyright (c) 2010-2014 LabKey Corporation
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

import com.google.common.collect.Ordering;
import org.apache.commons.lang3.mutable.MutableLong;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.ValueComparableMap;

import java.text.Format;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * User: adam
 * Date: May 29, 2010
 * Time: 2:03:25 PM
 */
public class MultiPhaseCPUTimer<K extends Enum<K>>
{
    private final Map<K, MutableLong> _accumulationMap;
    private final Class<K> _clazz;
    private final K[] _values;

    private long _count = 0;

    public MultiPhaseCPUTimer(Class<K> clazz, K[] values)
    {
        _clazz = clazz;
        _values = values;
        _accumulationMap = getEnumMap(_clazz, _values);
    }

    public InvocationTimer<K> getInvocationTimer()
    {
        return new InvocationTimer<>(_clazz, _values);
    }

    public void releaseInvocationTimer(InvocationTimer<K> timer)
    {
        timer.close();

        synchronized (_accumulationMap)
        {
            _count++;

            for (Map.Entry<K, MutableLong> entry : timer._map.entrySet())
                _accumulationMap.get(entry.getKey()).add(entry.getValue());
        }
    }

    // Return a copy of the current stats as a map of phase name -> average time in milliseconds
    public Map<String, Double> getTimes()
    {
        Map<String, Double> map = new LinkedHashMap<>();

        synchronized (_accumulationMap)
        {
            // Return stats for the phases we've seen, but in order of enum values
            for (K value : _values)
            {
                MutableLong nanos = _accumulationMap.get(value);

                if (null != nanos)
                {
                    double d = (0 == _count ? 0 : nanos.doubleValue() / (_count * 1000000.0));   // average and convert from nanos to millis
                    map.put(value.toString(), d);
                }
            }
        }

        return map;
    }

    // Create an enum map and populate it with MutableLongs for each value
    private static <ENUM extends Enum<ENUM>> Map<ENUM, MutableLong> getEnumMap(Class<ENUM> clazz, ENUM[] values)
    {
        Map<ENUM, MutableLong> map = new EnumMap<>(clazz);

        for (ENUM phase : values)
            map.put(phase, new MutableLong());

        return map;
    }

    public static class InvocationTimer<K2 extends Enum<K2>>
    {
        private final Map<K2, MutableLong> _map;

        private K2 _currentPhase = null;
        private long _beginningNanos = 0;
        private boolean _closed = false;

        private InvocationTimer(Class<K2> clazz, K2[] values)
        {
            _beginningNanos = System.nanoTime();
            _map = getEnumMap(clazz, values);
        }

        public K2 getCurrentPhase()
        {
            return _currentPhase;
        }

        public void setPhase(@Nullable K2 phase)
        {
            long prevBeginning = _beginningNanos;
            _beginningNanos = System.nanoTime();

            if (null != _currentPhase)
                _map.get(_currentPhase).add(_beginningNanos - prevBeginning);

            _currentPhase = phase;
            assert !_closed;
        }

        public enum Order
        {
            EnumOrder
                    {
                        @Override
                        Map<String, Double> createMap()
                        {
                            return new LinkedHashMap<>();
                        }
                    },
            Alphabetical
                    {
                        @Override
                        Map<String, Double> createMap()
                        {
                            return new TreeMap<>();
                        }
                    },
            LowToHigh
                    {
                        @Override
                        Map<String, Double> createMap()
                        {
                            return new ValueComparableMap<>(Ordering.natural());
                        }
                    },
            HighToLow
                    {
                        @Override
                        Map<String, Double> createMap()
                        {
                            return new ValueComparableMap<>(Ordering.natural().reverse());
                        }
                    };

            abstract Map<String, Double> createMap();
        }

        // Return phase -> time (milliseconds) map in the specified order
        public Map<String, Double> getMap(Order order)
        {
            Map<String, Double> map = order.createMap();

            for (Map.Entry<K2, MutableLong> entry : _map.entrySet())
            {
                MutableLong nanos = entry.getValue();
                double d = nanos.doubleValue() / 1000000.0;   // convert from nanos to millis
                map.put(entry.getKey().toString(), d);
            }

            return map;
        }

        // Consider: make public, do release, and implement closeable - would allow try-with-resources
        private void close()
        {
            setPhase(null);
            _closed = true;
        }

        public String getTimings(String prefix, Order order, String deliminator)
        {
            Format df = Formats.f2;
            StringBuilder sb = new StringBuilder(prefix);
            Map<String, Double> map = getMap(order);

            for (Map.Entry<String, Double> phase : map.entrySet())
            {
                sb.append("\n").append(phase.getKey()).append(deliminator).append(df.format(phase.getValue()));
            }

            return sb.toString();
        }

        @Override
        protected void finalize() throws Throwable
        {
            assert _closed && null == _currentPhase;
            super.finalize();
        }
    }
}

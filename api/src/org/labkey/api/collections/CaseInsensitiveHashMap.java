/*
 * Copyright (c) 2005-2016 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.collections;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.util.JunitUtil;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;

/**
 * {@link Map} implementation that uses case-insensitive {@link String}s for keys. Unlike many other implementations,
 * retains the original case of the String for use when iterating and such.
 * User: arauch
 * Date: Dec 25, 2004
 */
public class CaseInsensitiveHashMap<V> extends CaseInsensitiveMapWrapper<V> implements Serializable
{
    public CaseInsensitiveHashMap()
    {
        super(new HashMap<>());
    }

    public CaseInsensitiveHashMap(int count)
    {
        super(new HashMap<>(count));
    }

    public CaseInsensitiveHashMap(Map<String, V> map)
    {
        this(map.size());

        for (Map.Entry<String, V> entry : map.entrySet())
            put(entry.getKey(), entry.getValue());
    }

    /** Share the canonical key casing with the caseMapping instance */
    public CaseInsensitiveHashMap(Map<String, V> map, CaseInsensitiveMapWrapper<V> caseMapping)
    {
        this(map.size(), caseMapping);

        for (Map.Entry<String, V> entry : map.entrySet())
            put(entry.getKey(), entry.getValue());
    }

    /** Share the canonical key casing with the caseMapping instance */
    public CaseInsensitiveHashMap(int size, CaseInsensitiveMapWrapper<V> caseMapping)
    {
        super(new HashMap<>(size), caseMapping);
    }

    public static <V> CaseInsensitiveHashMap<V> of()
    {
        return new CaseInsensitiveHashMap<>();
    }

    public static <V> CaseInsensitiveHashMap<V> of(String k1, V v1)
    {
        CaseInsensitiveHashMap<V> map = new CaseInsensitiveHashMap<>();
        map.put(k1, v1);
        return map;
    }

    public static <V> CaseInsensitiveHashMap<V> of(String k1, V v1, String k2, V v2)
    {
        CaseInsensitiveHashMap<V> map = new CaseInsensitiveHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }

    public static <V> CaseInsensitiveHashMap<V> of(String k1, V v1, String k2, V v2, String k3, V v3)
    {
        CaseInsensitiveHashMap<V> map = new CaseInsensitiveHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        return map;
    }

    public static <V> CaseInsensitiveHashMap<V> of(String k1, V v1, String k2, V v2, String k3, V v3, String k4, V v4)
    {
        CaseInsensitiveHashMap<V> map = new CaseInsensitiveHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        return map;
    }

    public static <V> CaseInsensitiveHashMap<V> of(String k1, V v1, String k2, V v2, String k3, V v3, String k4, V v4, String k5, V v5)
    {
        CaseInsensitiveHashMap<V> map = new CaseInsensitiveHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        map.put(k5, v5);
        return map;
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testDupes()
        {
            Map<String, Integer> map = new CaseInsensitiveHashMap<>();
            map.put("a", 2);
            assertEquals(new Integer(2), map.put("A", 3));
            assertEquals(1, map.size());
        }

        @Test
        // Our original CaseInsensitiveHashMap had a get() method that mutated state, which lead to thread safety issues in
        // multi-threaded usages. This test was developed to demonstrate and fix that problem.
        public void multiThreadStressTest() throws InterruptedException, ExecutionException
        {
            final int races = 1000;
            final int threads = 5;

            final String key = "ThisIsATestOfTheCaseInsensitiveMap";
            final Object value = new Object();
            final Map<String, Object> map = new CaseInsensitiveHashMap<>();
            final List<String> keys = new LinkedList<>();

            map.put(key, value);

            Random random = new Random();

            // Create a list containing <races> random casings of <key>, each repeated <threads> times
            for (int i = 0; i < races; i++)
            {
                StringBuilder candidate = new StringBuilder(key.length());

                for (int j = 0; j < key.length(); j++)
                {
                    if (random.nextBoolean())
                        candidate.append(Character.toLowerCase(key.charAt(j)));
                    else
                        candidate.append(Character.toUpperCase(key.charAt(j)));
                }

                String s = candidate.toString();

                for (int j = 0; j < threads; j++)
                    keys.add(s);
            }

            final Iterator<String> iter = keys.iterator();

            JunitUtil.createRaces(() -> {
                Object test = map.get(iter.next());

                Assert.assertEquals(value, test);
            }, threads, races, 5);
        }
    }
}

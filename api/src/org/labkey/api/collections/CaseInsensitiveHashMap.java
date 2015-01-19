/*
 * Copyright (c) 2005-2014 Fred Hutchinson Cancer Research Center
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
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * User: arauch
 * Date: Dec 25, 2004
 * Time: 4:07:57 PM
 */

public class CaseInsensitiveHashMap<V> extends CaseInsensitiveMapWrapper<V> implements Serializable
{
    public CaseInsensitiveHashMap()
    {
        super(new HashMap<String, V>());
    }

    public CaseInsensitiveHashMap(int count)
    {
        super(new HashMap<String, V>(count));
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
        super(new HashMap<String, V>(size), caseMapping);
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
        // multi-threaded usages. This test was developed to demonstrate and fix the problem.
        public void multiThreadStressTest() throws InterruptedException, ExecutionException
        {
            final String key = "ThisIsATestOfTheCaseInsensitiveMap";
            final Object value = new Object();
            final Map<String, Object> map = new CaseInsensitiveHashMap<>();
            final Set<String> keys = new LinkedHashSet<>();

            int count = 1000;
            int threads = 5;
            map.put(key, value);

            Random random = new Random();

            // Create an ordered set containing <count> unique random casings of <key>
            while (keys.size() < count)
            {
                StringBuilder candidate = new StringBuilder(key.length());

                for (int i = 0; i < key.length(); i++)
                {
                    if (random.nextBoolean())
                        candidate.append(Character.toLowerCase(key.charAt(i)));
                    else
                        candidate.append(Character.toUpperCase(key.charAt(i)));
                }

                String s = candidate.toString();

                if (!keys.contains(s))
                    keys.add(s);
            }

            for (final String s : keys)
            {
                JunitUtil.createRace(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        Object test = map.get(s);

                        Assert.assertEquals(value, test);
                    }
                }, threads, threads).awaitTermination(5, TimeUnit.SECONDS);
            }
        }
    }
}

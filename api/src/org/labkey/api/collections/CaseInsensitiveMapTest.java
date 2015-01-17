/*
 * Copyright (c) 2010-2015 LabKey Corporation
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
import org.labkey.api.util.MemTracker;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

/**
 * User: adam
 * Date: Aug 30, 2010
 * Time: 3:28:25 PM
 */
public class CaseInsensitiveMapTest extends Assert
{
    @Test
    public void multiThreadStressTest() throws InterruptedException, ExecutionException
    {
        final String key = "ThisIsATestOfTheCaseInsensitiveMap";
        final Object value = new Object();
        final Map<String, Object> map = new OldCaseInsensitiveHashMap<>();
        final Set<String> keys = new LinkedHashSet<>();

        int count = 1000;
        int threads = 2;
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

        ExecutorService exec = Executors.newFixedThreadPool(threads, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r)
            {
                Thread t = new Thread(r, "CaseInsensitiveMapTest thread");
                MemTracker.getInstance().put(t);
                return t;
            }
        });
        final CyclicBarrier barrier = new CyclicBarrier(threads);
        List<Callable<Integer>> tasks = new LinkedList<>();

        for (int i = 0; i < threads; i++)
        {
            tasks.add(new Callable<Integer>() {
                @Override
                public Integer call() throws Exception
                {
                    int count = 0;

                    for (String s : keys)
                    {
                        try
                        {
                            barrier.await();
                        }
                        catch (InterruptedException | BrokenBarrierException e)
                        {
                            Thread.currentThread().interrupt();
                        }

                        Object test = map.get(s);

                        if (value != test)
                        {
                            count++;
                            barrier.reset();
                        }
                    }

                    return count;
                }
            });
        }

        List<Future<Integer>> futures = exec.invokeAll(tasks);

        for (Future<Integer> future : futures)
        {
            assertEquals(0, future.get().intValue());
        }
    }
}

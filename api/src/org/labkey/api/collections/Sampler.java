/*
 * Copyright (c) 2014 LabKey Corporation
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

import org.apache.commons.lang3.RandomUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: adam
 * Date: 11/23/2014
 * Time: 4:12 PM
 */

/**
 * This class samples from a (potentially very large) stream of elements and saves a subset of elements limited to a
 * specified maximum, replacing elements over time to maintain a uniform distribution over all elements seen.
 *
 * Elements added are accepted and saved in order, up until maxSize elements have been added. After that, elements are
 * accepted (replacing an existing element) or rejected to simulate uniform sampling of the entire population. The
 * probability of the nth element replacing an existing element is: maxSize / n
 * @param <E>
 */
public class Sampler<E>
{
    private final List<E> _list;
    private final int _maxSize;

    private int _addCount;

    public Sampler(int initialCapacity, int maxSize)
    {
        _list = new ArrayList<>(initialCapacity);
        _maxSize = maxSize;
    }

    public void add(E e)
    {
        _addCount++;
        int size = _list.size();

        if (size < _maxSize)
        {
            _list.add(e);
        }
        else
        {
            int bucket = RandomUtils.nextInt(0, _addCount);

            if (bucket < _maxSize)
                _list.set(bucket, e);
        }
    }

    public List<E> getList()
    {
        return Collections.unmodifiableList(_list);
    }

    public int getAddCount()
    {
        return _addCount;
    }


    public static class TestCase extends Assert
    {
        @Test
        public void test()
        {
            Sampler<Integer> sampler = new Sampler<>(10, 100);

            addIntegers(sampler, 50);
            List<Integer> list = sampler.getList();
            assertEquals(50, list.size());
            assertEquals(50, countValuesEqualIndex(list));

            addIntegers(sampler, 50);
            list = sampler.getList();
            assertEquals(100, list.size());
            assertEquals(100, countValuesEqualIndex(list));

            addIntegers(sampler, 1);
            list = sampler.getList();
            assertEquals(100, list.size());
            int countValuesEqualIndex = countValuesEqualIndex(list);
            assertTrue(countValuesEqualIndex >= 99);    // Likely 99... but could be 100

            addIntegers(sampler, 99);
            list = sampler.getList();
            assertEquals(100, list.size());

            addIntegers(sampler, 9800);
            list = sampler.getList();
            assertEquals(100, list.size());
            getDistribution(list, 10, 10000);   // Not testing this (it will vary), but should be roughly 10 values per bin
        }

        private void addIntegers(Sampler<Integer> sampler, int count)
        {
            int addCount = sampler.getAddCount();

            for (int i = 0; i < count; i++)
                sampler.add(addCount + i);
        }

        private int countValuesEqualIndex(List<Integer> list)
        {
            int count = 0;

            for (int i = 0; i < list.size(); i++)
                if (list.get(i) == i)
                    count++;

            return count;
        }

        private int[] getDistribution(List<Integer> list, int binCount, int maxValue)
        {
            int binSize = maxValue / binCount;
            int[] bins = new int[binCount];

            for (Integer value : list)
            {
                int index = value / binSize;
                bins[index]++;
            }

            return bins;
        }
    }
}

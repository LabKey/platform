/*
 * Copyright (c) 2017 LabKey Corporation
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

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Static methods that return a {@link Collector} that can be used to collect elements of a {@link Stream} into a
 * {@link MultiValuedMap}. The approach mimics {@link Collectors#toMap(Function, Function)}, i.e., the returned
 * {@code Collector} accumulates elements into a {@code MultiValuedMap} whose keys and values are the result of
 * applying the provided mapping functions to the input elements.
 *
 * Created by adam on 5/4/2017.
 */
public class MultiValuedMapCollectors
{
    /**
     * Returns a {@code Collector} that accumulates elements into a {@code MultiValuedMap} whose keys and values are the
     * result of applying the provided mapping functions to the input elements.
     *
     * @param <T> the type of the input elements
     * @param <K> the output type of the key mapping function
     * @param <V> the output type of the value mapping function
     * @param keyMapper a mapping function to produce keys
     * @param valueMapper a mapping function to produce values
     * @return a {@code Collector} that collects elements into a {@code MultiValuedMap} whose keys and values are the
     * result of applying mapping functions to the input elements
     */
    public static <T, K, V> Collector<T, ?, MultiValuedMap<K, V>> of(Function<? super T, ? extends K> keyMapper,
                                                                     Function<? super T, ? extends V> valueMapper)
    {
        return of(keyMapper, valueMapper, ArrayListValuedHashMap::new);
    }

    /**
     * Returns a {@code Collector} that accumulates elements into a {@code MultiValuedMap} whose keys and values are the
     * result of applying the provided mapping functions to the input elements. The {@code MultiValuedMap} is created by
     * a provided supplier function.
     *
     * @param <T> the type of the input elements
     * @param <K> the output type of the key mapping function
     * @param <V> the output type of the value mapping function
     * @param keyMapper a mapping function to produce keys
     * @param valueMapper a mapping function to produce values
     * @param supplier a function that returns a new, empty {@code MultiValuedMap} into which the results will be inserted
     * @return a {@code Collector} that collects elements into a {@code MultiValuedMap} whose keys and values are the
     * result of applying mapping functions to the input elements
     */
    public static <T, K, V> Collector<T, ?, MultiValuedMap<K, V>> of(Function<? super T, ? extends K> keyMapper,
                                                                     Function<? super T, ? extends V> valueMapper,
                                                                     Supplier<MultiValuedMap<K, V>> supplier)
    {
        return Collector.of(
            supplier,
            (MultiValuedMap<K, V> mmap, T t) -> mmap.put(keyMapper.apply(t), valueMapper.apply(t)),
            (mmap1, mmap2) ->
            {
                MultiValuedMap<K, V> mmap = supplier.get();
                mmap.putAll(mmap1);
                mmap.putAll(mmap2);
                return mmap;
            });
    }


    public static class TestCase extends Assert
    {
        @Test
        public void test()
        {
            Map<String, String> map = new HashMap<>(4);
            map.put("A", "one");
            map.put("B", "two");
            map.put("C", "three");
            map.put("D", "four");

            MultiValuedMap<String, String> mmap = map.entrySet().stream().collect(of(Entry::getKey, Entry::getValue));
            assertEquals(4, mmap.size());

            List<Integer> list = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 2, 4, 6, 8, 10);
            MultiValuedMap<Integer, Integer> mmap2 = list.stream().collect(of(i -> i, i -> i));
            assertEquals(15, mmap2.size());
            assertEquals(10, mmap2.keySet().size());
        }
    }
}

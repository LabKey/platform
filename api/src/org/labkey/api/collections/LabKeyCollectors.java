/*
 * Copyright (c) 2020-2026 LabKey Corporation
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

import com.google.common.collect.Comparators;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.json.JSONArray;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Static methods that return custom {@link Collector}s that can be used to collect elements of a {@link Stream} into a
 * variety of useful collections.
 */
public class LabKeyCollectors
{
    /**
     * A {@link Collectors#toMap(Function, Function, BinaryOperator, Supplier)} alternative that allows {@code null} values.
     * The standard method does not allow {@code null} values because it uses {@link Map#merge(Object, Object, BiFunction)}
     * which throws NPE on {@code null}. See <a href="https://bugs.openjdk.org/browse/JDK-8148463">JDK bug</a>.
     */
    public static <T, K, V, M extends Map<K, V>> Collector<T, ?, M> toMapNullSafe(
        Function<? super T, ? extends K> keyMapper,
        Function<? super T, ? extends V> valueMapper,
        BinaryOperator<V> mergeFunction,
        Supplier<M> mapFactory)
    {
        // Effectively the same as toMap() except for the use of the null-supporting merge() method below
        return Collector.of(
            mapFactory,
            (M map, T t) -> merge(map, keyMapper.apply(t), valueMapper.apply(t), mergeFunction),
            (M map1, M map2) -> {
                for (Map.Entry<K, V> e : map2.entrySet())
                    merge(map1, e.getKey(), e.getValue(), mergeFunction);
                return map1;
            }
        );
    }

    /**
     * A {@link Map#merge(Object, Object, BiFunction)} alternative that allows null values and maintains null mappings
     */
    private static <K, V> void merge(Map<K, V> map, K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction)
    {
        Objects.requireNonNull(remappingFunction);
        V newValue = map.containsKey(key) ? remappingFunction.apply(map.get(key), value) : value;
        map.put(key, newValue);
    }

    /**
     * Standard map collector that accepts null values and rejects duplicate keys
     * @throws IllegalStateException if duplicate keys are detected
     */
    public static <T, K, V, M extends Map<K, V>> Collector<T, ?, M> toMapNullSafe(
        Function<? super T, ? extends K> keyMapper,
        Function<? super T, ? extends V> valueMapper,
        Supplier<M> mapFactory)
    {
        return toMapNullSafe(
            keyMapper,
            valueMapper,
            (u, v) -> {
                throw new IllegalStateException(String.format("Duplicate key %s", u));
            },
            mapFactory);
    }

    /**
     * Returns a {@link Collector} that builds a {@link LinkedHashMap}, for cases where caller wants a map that preserves {@link Stream} order.
     * <a href="https://stackoverflow.com/questions/29090277/how-do-i-keep-the-iteration-order-of-a-list-when-using-collections-tomap-on-a">Stackoverflow source</a>
     * Accepts {@code null} values.
     * @throws IllegalStateException if duplicate keys are detected
     */
    public static <T, K, U> Collector<T, ?, Map<K, U>> toLinkedMap(
        Function<? super T, ? extends K> keyMapper,
        Function<? super T, ? extends U> valueMapper)
    {
        return toMapNullSafe(
            keyMapper,
            valueMapper,
            LinkedHashMap::new
        );
    }

    /**
     * Returns a {@link Collector} that builds a {@link CaseInsensitiveHashMap}. Accepts {@code null} values.
     * @throws IllegalStateException if duplicate keys are detected
     */
    public static <T, U> Collector<T, ?, Map<String, U>> toCaseInsensitiveMap(
        Function<? super T, String> keyMapper,
        Function<? super T, ? extends U> valueMapper)
    {
        return toMapNullSafe(
            keyMapper,
            valueMapper,
            CaseInsensitiveHashMap::new
        );
    }

    /**
     * Returns a {@link Collector} that builds a {@link CaseInsensitiveLinkedHashMap}. Accepts {@code null} values.
     * @throws IllegalStateException if duplicate keys are detected
     */
    public static <T, U> Collector<T, ?, Map<String, U>> toCaseInsensitiveLinkedMap(
        Function<? super T, String> keyMapper,
        Function<? super T, ? extends U> valueMapper)
    {
        return toMapNullSafe(
            keyMapper,
            valueMapper,
            CaseInsensitiveLinkedHashMap::new
        );
    }

    /**
     * Returns a {@link Collector} that accumulates elements into a {@link MultiValuedMap} whose keys and values are the
     * result of applying the provided mapping functions to the input elements, an approach that mimics {@link Collectors#toMap(Function, Function)}.
     *
     * @param <T>         the type of the input elements
     * @param <K>         the output type of the key mapping function
     * @param <V>         the output type of the value mapping function
     * @param keyMapper   a mapping function to produce keys
     * @param valueMapper a mapping function to produce values
     * @return a {@code Collector} that collects elements into a {@code MultiValuedMap} whose keys and values are the
     * result of applying mapping functions to the input elements
     */
    public static <T, K, V> Collector<T, ?, MultiValuedMap<K, V>> toMultiValuedMap(Function<? super T, ? extends K> keyMapper,
                                                                                   Function<? super T, ? extends V> valueMapper)
    {
        return toMultiValuedMap(keyMapper, valueMapper, ArrayListValuedHashMap::new);
    }

    /**
     * Returns a {@link Collector} that accumulates elements into a {@link MultiValuedMap} whose keys and values are the
     * result of applying the provided mapping functions to the input elements, an approach that mimics {@link Collectors#toMap(Function, Function)}.
     * The {@link MultiValuedMap} is created by a provided supplier function.
     *
     * @param <T>         the type of the input elements
     * @param <K>         the output type of the key mapping function
     * @param <V>         the output type of the value mapping function
     * @param keyMapper   a mapping function to produce keys
     * @param valueMapper a mapping function to produce values
     * @param supplier    a function that returns a new, empty {@code MultiValuedMap} into which the results will be inserted
     * @return a {@code Collector} that collects elements into a {@code MultiValuedMap} whose keys and values are the
     * result of applying mapping functions to the input elements
     */
    public static <T, K, V> Collector<T, ?, MultiValuedMap<K, V>> toMultiValuedMap(Function<? super T, ? extends K> keyMapper,
                                                                                   Function<? super T, ? extends V> valueMapper,
                                                                                   Supplier<MultiValuedMap<K, V>> supplier)
    {
        return Collector.of(
            supplier,
            (MultiValuedMap<K, V> mmap, T t) -> mmap.put(keyMapper.apply(t), valueMapper.apply(t)),
            (mmap1, mmap2) ->
            {
                mmap1.putAll(mmap2);
                return mmap1;
            });
    }

    /**
     * Returns a {@link Collector} that builds a {@link JSONArray} from the {@link Stream}.
     */
    public static Collector<Object, JSONArray, JSONArray> toJSONArray()
    {
        return Collector.of(
            JSONArray::new,
            JSONArray::put,
            JSONArray::putAll
        );
    }

    /**
     * Returns a {@link Collector} that builds a {@link CaseInsensitiveHashSet} from a {@link Stream} of {@link String}s
     */
    public static Collector<String, ?, Set<String>> toCaseInsensitiveHashSet()
    {
        return Collectors.toCollection(CaseInsensitiveHashSet::new);
    }

    /**
     * Returns a {@link Collector} that builds a case-insensitive linked hash set from a {@link Stream} of {@link String}s.
     * The resulting sets are appropriate for TableSelectors that need stable-ordered column sets since
     * CollectionUtils.isStableOrderedSet() knows they are stable-ordered.
     */
    public static Collector<String, ?, Set<String>> toCaseInsensitiveLinkedHashSet()
    {
        return Collectors.toCollection(() -> Collections.newSetFromMap(new CaseInsensitiveLinkedHashMap<>()));
    }

    /**
     * Returns a {@link Collector} that joins {@link HtmlString}s into a single {@link HtmlString} separated by delimiter
     */
    public static Collector<HtmlString, HtmlStringBuilder, HtmlString> joining(HtmlString delimiter) {
        return Collector.of(
            HtmlStringBuilder::of,
            (builder, hs) -> {
                if (!builder.isEmpty())
                    builder.append(delimiter);
                builder.append(hs);
            },
            (h1, h2) -> { h1.append(h2.getHtmlString()); return h1; },
            HtmlStringBuilder::getHtmlString
        );
    }

    /**
     * Returns a {@link Collector} that joins {@link SQLFragment}s into a single {@link SQLFragment} separated by delimiter
     */
    public static Collector<SQLFragment, List<SQLFragment>, SQLFragment> joining(SQLFragment delimiter) {
        return Collector.of(
            LinkedList::new,
            List::add,
            (list1, list2) -> {list1.addAll(list2); return list1;},
            (list) -> SQLFragment.join(list, delimiter)
        );
    }

    public static class TestCase extends Assert
    {
        @Test
        public void test()
        {
            Map<String, String> map = Map.of(
                "A", "one",
                "B", "two",
                "C", "three",
                "D", "four"
            );

            MultiValuedMap<String, String> mmap = map.entrySet().stream().collect(toMultiValuedMap(Map.Entry::getKey, Map.Entry::getValue));
            assertEquals(4, mmap.size());

            Map<String, String> lhmap = map.entrySet().stream().sorted(Map.Entry.comparingByKey()).collect(toLinkedMap(Map.Entry::getKey, Map.Entry::getValue));
            assertTrue(Comparators.isInOrder(lhmap.keySet(), Comparator.naturalOrder()));

            JSONArray jsonArray = map.keySet().stream().sorted().collect(toJSONArray());
            assertEquals(4, jsonArray.length());
            assertEquals("[\"A\",\"B\",\"C\",\"D\"]", jsonArray.toString());

            List<Integer> list = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 2, 4, 6, 8, 10);
            MultiValuedMap<Integer, Integer> mmap2 = list.stream().collect(toMultiValuedMap(i -> i, i -> i));
            assertEquals(15, mmap2.size());
            assertEquals(10, mmap2.keySet().size());
        }

        @Test
        public void testNullSafe()
        {
            Map<String, String> someNulls = new HashMap<>();
            someNulls.put("ABC", "one");
            someNulls.put("B", "two");
            someNulls.put("C", null);
            someNulls.put("D", "four");
            someNulls.put("E", null);

            Map<String, String> insensitive = someNulls.entrySet().stream()
                .collect(toCaseInsensitiveMap(Map.Entry::getKey, Map.Entry::getValue));
            assertEquals(5, insensitive.size());

            assertEquals("one", insensitive.get("abc"));
            assertEquals("two", insensitive.get("b"));
            assertNull(insensitive.get("c"));
            assertEquals("four", insensitive.get("D"));
            assertNull(insensitive.get("E"));

            Map<String, String> linked = someNulls.entrySet().stream()
                .collect(toLinkedMap(Map.Entry::getKey, Map.Entry::getValue));
            assertEquals(5, linked.size());

            Map<String, String> insensitiveLinked = someNulls.entrySet().stream()
                    .collect(toCaseInsensitiveLinkedMap(Map.Entry::getKey, Map.Entry::getValue));
            assertEquals(5, insensitiveLinked.size());
        }

        @Test(expected = IllegalStateException.class)
        public void testDuplicatesLinkedMap()
        {
            List<String> list = List.of(
                "one",
                "two",
                "three",
                "four",
                "six",
                "two",
                "five"
            );

            //noinspection ResultOfMethodCallIgnored
            list.stream().collect(toLinkedMap(v->v, v->v));
        }

        @Test(expected = IllegalStateException.class)
        public void testDuplicatesCaseInsensitive()
        {
            List<String> list = List.of(
                "one",
                "two",
                "three",
                "four",
                "six",
                "two",
                "five"
            );

            //noinspection ResultOfMethodCallIgnored
            list.stream().collect(toCaseInsensitiveMap(v->v, v->v));
        }
    }
}

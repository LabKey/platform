package org.labkey.api.collections;

import com.google.common.collect.Comparators;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.json.JSONArray;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

/**
 * Static methods that return custom {@link Collector}s that can be used to collect elements of a {@link Stream} into a
 * a variety of useful collections.
 */
public class LabKeyCollectors
{
    /**
     * Returns a {@link Collector} that builds a {@link LinkedHashMap}, for cases where caller wants a map that preserves {@link Stream} order.
     * https://stackoverflow.com/questions/29090277/how-do-i-keep-the-iteration-order-of-a-list-when-using-collections-tomap-on-a
     */
    public static <T, K, U> Collector<T, ?, Map<K,U>> toLinkedMap(
        Function<? super T, ? extends K> keyMapper,
        Function<? super T, ? extends U> valueMapper)
    {
        return toMap(
            keyMapper,
            valueMapper,
            (u, v) -> {
                throw new IllegalStateException(String.format("Duplicate key %s", u));
            },
            LinkedHashMap::new
        );
    }

    /**
     * Returns a {@link Collector} that accumulates elements into a {@link MultiValuedMap} whose keys and values are the
     * result of applying the provided mapping functions to the input elements, an approach that mimics {@link Collectors#toMap(Function, Function)}.
     *
     * @param <T> the type of the input elements
     * @param <K> the output type of the key mapping function
     * @param <V> the output type of the value mapping function
     * @param keyMapper a mapping function to produce keys
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
     * @param <T> the type of the input elements
     * @param <K> the output type of the key mapping function
     * @param <V> the output type of the value mapping function
     * @param keyMapper a mapping function to produce keys
     * @param valueMapper a mapping function to produce values
     * @param supplier a function that returns a new, empty {@code MultiValuedMap} into which the results will be inserted
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
                MultiValuedMap<K, V> mmap = supplier.get();
                mmap.putAll(mmap1);
                mmap.putAll(mmap2);
                return mmap;
            });
    }

    /**
     * Returns a {@link Collector} that builds a {@link JSONArray} from the {@link Stream}.
     */
    public static Collector<Object, JSONArray, JSONArray> toJSONArray()
    {
        return JSONArray.collector();
    }

    /**
     * Returns a {@link Collector} that builds a {@link CaseInsensitiveHashSet} from a {@link Stream} of {@link String}s
     */
    public static Collector<String, ?, Set<String>> toCaseInsensitiveHashSet()
    {
        return Collectors.toCollection(CaseInsensitiveHashSet::new);
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
            Comparators.isInOrder(lhmap.keySet(), Comparator.naturalOrder());

            JSONArray jsonArray = map.keySet().stream().sorted().collect(toJSONArray());
            assertEquals(4, jsonArray.length());
            assertEquals("[\"A\",\"B\",\"C\",\"D\"]", jsonArray.toString());

            List<Integer> list = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 2, 4, 6, 8, 10);
            MultiValuedMap<Integer, Integer> mmap2 = list.stream().collect(toMultiValuedMap(i -> i, i -> i));
            assertEquals(15, mmap2.size());
            assertEquals(10, mmap2.keySet().size());
        }

        @Test(expected = IllegalStateException.class)
        public void testDuplicates()
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
    }
}

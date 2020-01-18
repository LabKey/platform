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
import org.labkey.api.annotations.RemoveIn20_1;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

@RemoveIn20_1
public class MultiValuedMapCollectors
{
    @Deprecated // Use Collectors.toMultiValuedMap()
    public static <T, K, V> Collector<T, ?, MultiValuedMap<K, V>> of(Function<? super T, ? extends K> keyMapper,
                                                                     Function<? super T, ? extends V> valueMapper)
    {
        return LabKeyCollectors.toMultivaluedMap(keyMapper, valueMapper);
    }

    @Deprecated // Use Collectors.toMultiValuedMap()
    public static <T, K, V> Collector<T, ?, MultiValuedMap<K, V>> of(Function<? super T, ? extends K> keyMapper,
                                                                     Function<? super T, ? extends V> valueMapper,
                                                                     Supplier<MultiValuedMap<K, V>> supplier)
    {
        return LabKeyCollectors.toMultivaluedMap(keyMapper, valueMapper, supplier);
    }
}

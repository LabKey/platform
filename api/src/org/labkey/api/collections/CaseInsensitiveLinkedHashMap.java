/*
 * Copyright (c) 2018 LabKey Corporation
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

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link Map} implementation that uses case-insensitive {@link String}s for keys and an underlying LinkedHashMap,
 * so order is preserved.
 *
 * User: daveb  
 * Date: 5/17/2018
 */
public class CaseInsensitiveLinkedHashMap<V> extends CaseInsensitiveMapWrapper<V> implements Serializable
{
    public CaseInsensitiveLinkedHashMap()
    {
        super(new LinkedHashMap<>());
    }

    public CaseInsensitiveLinkedHashMap(int count)
    {
        super(new LinkedHashMap<>(count));
    }

    public CaseInsensitiveLinkedHashMap(Map<String, V> map)
    {
        this(map.size());

        putAll(map);
    }
}

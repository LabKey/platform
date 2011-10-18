/*
 * Copyright (c) 2011 LabKey Corporation
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
package org.labkey.api.cache;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.Filter;

/**
 * User: adam
 * Date: 10/12/11
 * Time: 4:58 AM
 */
public class BlockingStringKeyCache<V> extends BlockingCache<String, V>
{
    public BlockingStringKeyCache(Cache<String, Object> cache)
    {
        super(cache);
    }

    public BlockingStringKeyCache(Cache<String, Object> cache, @Nullable CacheLoader<String, V> cacheLoader)
    {
        super(cache, cacheLoader);
    }

    public int removeUsingPrefix(final String prefix)
    {
        return removeUsingFilter(new Filter<String>(){
            @Override
            public boolean accept(String s)
            {
                return s.startsWith(prefix);
            }
        });
    }
}

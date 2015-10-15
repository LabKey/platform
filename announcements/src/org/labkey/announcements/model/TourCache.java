/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.announcements.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;

/**
 * Created by Marty on 2/25/2015.
 */
public class TourCache
{
    private static final CommSchema _comm = CommSchema.getInstance();
    private static final TourCacheLoader tourLoader = new TourCacheLoader<TourCollections>();
    private static final BlockingStringKeyCache<Object> BLOCKING_CACHE = CacheManager.getBlockingStringKeyCache(50000, CacheManager.DAY, "Tours", tourLoader);
    private static final TourCollections _emptyCollection = new TourCollections(new TableSelector(_comm.getTableInfoTours()));

    public static class TourCacheLoader<V> implements CacheLoader<String, V>
    {
        @Override
        public V load(String key, Object argument)
        {
            Container c = (Container) argument;
            if (c == null)
                return (V) _emptyCollection;

            Selector selector = new TableSelector(_comm.getTableInfoTours(), SimpleFilter.createContainerFilter(c), null);

            if (selector.getRowCount() > 0)
                return (V) new TourCollections(selector);
            else
                return (V) _emptyCollection;
        }
    }

    static TourCollections getTourCollections(Container c)
    {
        return get(c);
    }

    public static void uncache(Container c)
    {
        BLOCKING_CACHE.removeUsingPrefix(getCacheKey(c));
    }

    // Private methods below

    private static String getCacheKey(Container c)
    {
        return "tours/" + c.getId();
    }

    @Nullable
    private static <V> V get(Container c)
    {
        if (c == null)
            return null;

        return (V) BLOCKING_CACHE.get(getCacheKey(c), c);
    }

}

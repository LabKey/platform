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
package org.labkey.api.view;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.util.Pair;
import org.labkey.api.view.Portal.WebPart;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User: adam
 * Date: 10/21/11
 * Time: 10:45 PM
 */
public class WebPartCache
{
    private static final BlockingStringKeyCache<Pair<ArrayList<WebPart>, Map<Integer, WebPart>>> CACHE = CacheManager.getBlockingStringKeyCache(10000, CacheManager.DAY, "Webparts", null);

    private static String getCacheKey(@NotNull Container c, @Nullable String id)
    {
        String result = c.getId();
        if (null != id)
        {
            result += "/" + id.toLowerCase();
        }
        return result;
    }

    static ArrayList<WebPart> getWebParts(@NotNull Container c, @NotNull String pageId)
    {
        return get(c, pageId).getKey();
    }

    static WebPart getWebPart(@NotNull Container c, @NotNull String pageId, int index)
    {
        return get(c, pageId).getValue().get(index);
    }

    private static Pair<ArrayList<WebPart>, Map<Integer, WebPart>> get(@NotNull final Container c, @NotNull final String pageId)
    {
        String key = getCacheKey(c, pageId);
        return CACHE.get(key, null, new CacheLoader<String, Pair<ArrayList<WebPart>, Map<Integer, WebPart>>>()
        {
            @Override
            public Pair<ArrayList<WebPart>, Map<Integer, WebPart>> load(String key, Object argument)
            {
                SimpleFilter filter = new SimpleFilter("PageId", pageId);
                filter.addCondition("Container", c.getId());
                Map<Integer, WebPart> map = new LinkedHashMap<Integer, WebPart>();

                ArrayList<WebPart> list = new ArrayList<WebPart>(new TableSelector(Portal.getTableInfoPortalWebParts(), Table.ALL_COLUMNS, filter, new Sort("Index")).getCollection(WebPart.class));

                // List order should match index, but use a map to be safe.  TODO: In 12.1, just index the list and switch map to RowId->WebPart
                for (WebPart webPart : list)
                    map.put(webPart.getIndex(), webPart);

                return new Pair<ArrayList<WebPart>, Map<Integer, WebPart>>(list, map);
            }
        });
    }

    static void remove(Container c, String pageId)
    {
        CACHE.remove(getCacheKey(c, pageId));
    }

    static void remove(Container c)
    {
        CACHE.removeUsingPrefix(getCacheKey(c, null));
    }
}

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
package org.labkey.api.specimen.location;

import org.labkey.api.Constants;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.IntHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.specimen.SpecimenSchema;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class LocationCache
{
    private static final Cache<Container, LocationCollections> CACHE = CacheManager.getBlockingCache(Constants.getMaxContainers(), CacheManager.DAY, "Study locations", (c, argument) -> new LocationCollections(c));

    private static class LocationCollections
    {
        private final List<LocationImpl> _list;
        private final Map<Integer, LocationImpl> _byRowId;
        private final Map<String, LocationImpl> _byLabel;

        public LocationCollections(Container c)
        {
            List<LocationImpl> list = new LinkedList<>();
            Map<Integer, LocationImpl> byRowId = new IntHashMap<>();
            Map<String, LocationImpl> byLabel = new HashMap<>();

            TableInfo tableInfo = SpecimenSchema.get().getTableInfoLocation(c);
            new TableSelector(tableInfo, null, new Sort(FieldKey.fromParts("Label"))).forEachMap(map->{
                LocationImpl location = new LocationImpl(c, map);
                list.add(location);
                byRowId.put(location.getRowId(), location);
                byLabel.put(location.getLabel(), location);
            });

            _list = Collections.unmodifiableList(list);
            _byRowId = Collections.unmodifiableMap(byRowId);
            _byLabel = Collections.unmodifiableMap(byLabel);
        }

        public List<LocationImpl> getLocations()
        {
            return _list;
        }

        public LocationImpl getForRowId(int rowId)
        {
            return _byRowId.get(rowId);
        }

        public LocationImpl getForLabel(String label)
        {
            return _byLabel.get(label);
        }
    }

    public static List<LocationImpl> getLocations(Container c)
    {
        return CACHE.get(c).getLocations();
    }

    public static LocationImpl getForRowId(Container c, int rowId)
    {
        return CACHE.get(c).getForRowId(rowId);
    }

    public static LocationImpl getForLabel(Container c, String label)
    {
        return CACHE.get(c).getForLabel(label);
    }

    public static void clear(Container c)
    {
        CACHE.remove(c);
    }
}

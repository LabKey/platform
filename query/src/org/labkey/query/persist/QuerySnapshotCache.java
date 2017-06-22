/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
package org.labkey.query.persist;

import org.apache.commons.collections4.MultiMapUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by adam on 10/4/2015.
 */
public class QuerySnapshotCache
{
    private static final Cache<Container, QuerySnapshotCollections> QUERY_SNAPSHOT_DEF_CACHE = CacheManager.getBlockingCache(CacheManager.UNLIMITED, CacheManager.DAY, "Query Snapshot Cache", new CacheLoader<Container, QuerySnapshotCollections>()
    {
        @Override
        public QuerySnapshotCollections load(Container c, @Nullable Object argument)
        {
            return new QuerySnapshotCollections(c);
        }
    });

    static @NotNull Collection<QuerySnapshotDef> getQuerySnapshotDefs(@Nullable Container c, @Nullable String schemaName)
    {
        QuerySnapshotCollections collections = QUERY_SNAPSHOT_DEF_CACHE.get(c);

        return null != schemaName ? collections.getForSchema(schemaName) : collections.getAllDefs();
    }

    static @Nullable QuerySnapshotDef getQuerySnapshotDef(@NotNull Container c, @NotNull String schemaName, @NotNull String snapshotName)
    {
        assert null != c && null != schemaName && null != snapshotName;
        return QUERY_SNAPSHOT_DEF_CACHE.get(c).getForSchemaAndName(schemaName, snapshotName);
    }

    static void uncache(Container c)
    {
        QUERY_SNAPSHOT_DEF_CACHE.remove(c);
        QUERY_SNAPSHOT_DEF_CACHE.remove(null);  // Clear out the full list (used for dependency tracking)
    }

    // Convenience method that handles null check
    static void uncache(QuerySnapshotDef def)
    {
        Container c = def.lookupContainer();

        if (null != c)
            uncache(c);
    }

    private static class QuerySnapshotCollections
    {
        private final Collection<QuerySnapshotDef> _allDefs;
        private final MultiValuedMap<String, QuerySnapshotDef> _bySchema;
        private final Map<String, Map<String, QuerySnapshotDef>> _bySchemaAndName;

        private QuerySnapshotCollections(@Nullable Container c)
        {
            SimpleFilter filter = null != c ? SimpleFilter.createContainerFilter(c) : new SimpleFilter();

            _allDefs = Collections.unmodifiableCollection(
                new TableSelector(QueryManager.get().getTableInfoQuerySnapshotDef(), filter, null)
                    .getCollection(QuerySnapshotDef.class)
            );

            MultiValuedMap<String, QuerySnapshotDef> bySchema = new ArrayListValuedHashMap<>();
            Map<String, Map<String, QuerySnapshotDef>> bySchemaAndName = new HashMap<>();

            for (QuerySnapshotDef def : _allDefs)
            {
                bySchema.put(def.getSchema(), def);

                Map<String, QuerySnapshotDef> map = bySchemaAndName.computeIfAbsent(def.getSchema(), k -> new HashMap<>());

                map.put(def.getName(), def);
            }

            _bySchema = MultiMapUtils.unmodifiableMultiValuedMap(bySchema);
            _bySchemaAndName = Collections.unmodifiableMap(bySchemaAndName);
        }

        private @NotNull Collection<QuerySnapshotDef> getAllDefs()
        {
            return _allDefs;
        }

        private @NotNull Collection<QuerySnapshotDef> getForSchema(@NotNull String schemaName)
        {
            Collection<QuerySnapshotDef> defs = _bySchema.get(schemaName);
            return null != defs ? Collections.unmodifiableCollection(defs) : Collections.emptyList();
        }

        private @Nullable QuerySnapshotDef getForSchemaAndName(@NotNull String schemaName, @NotNull String snapshotName)
        {
            Map<String, QuerySnapshotDef> snapshotDefMap = _bySchemaAndName.get(schemaName);

            return null != snapshotDefMap ? snapshotDefMap.get(snapshotName) : null;
        }
    }
}

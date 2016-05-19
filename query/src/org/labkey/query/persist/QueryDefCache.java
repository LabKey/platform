/*
 * Copyright (c) 2015-2016 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by klum on 10/21/2015.
 */
public class QueryDefCache
{
    private static final Cache<Container, QueryDefCollections> QUERY_DEF_DB_CACHE = CacheManager.getBlockingCache(CacheManager.UNLIMITED, CacheManager.DAY, "QueryDef Database Cache", new CacheLoader<Container, QueryDefCollections>()
    {
        @Override
        public QueryDefCollections load(Container c, @Nullable Object argument)
        {
            return new QueryDefCollections(c);
        }
    });

    private static class QueryDefCollections
    {
        // schemaName, queryDefName keys
        private final Map<String, Map<String, QueryDef>> _queryDefs;
        private final Map<String, Map<String, QueryDef>> _customQueryDefs;
        private final Map<Integer, QueryDef> _queryDefIdMap;

        private QueryDefCollections(Container c)
        {
            Map<String, Map<String, QueryDef>> queryDefs = new CaseInsensitiveHashMap<>();
            Map<String, Map<String, QueryDef>> customQueryDefs = new CaseInsensitiveHashMap<>();
            Map<Integer, QueryDef> queryDefIdMap = new HashMap<>();

            new TableSelector(QueryManager.get().getTableInfoQueryDef(), SimpleFilter.createContainerFilter(c), null).forEach(queryDef -> {

                if (queryDef.getSql() != null)
                {
                    Map<String, QueryDef> customQueryDefMap = ensureQueryDefMap(customQueryDefs, queryDef.getSchema());
                    customQueryDefMap.put(queryDef.getName(), queryDef);
                }
                else
                {
                    Map<String, QueryDef> queryDefMap = ensureQueryDefMap(queryDefs, queryDef.getSchema());
                    queryDefMap.put(queryDef.getName(), queryDef);
                }
                queryDefIdMap.put(queryDef.getQueryDefId(), queryDef);

            }, QueryDef.class);

            _queryDefs = Collections.unmodifiableMap(queryDefs);
            _customQueryDefs = Collections.unmodifiableMap(customQueryDefs);
            _queryDefIdMap = Collections.unmodifiableMap(queryDefIdMap);
        }

        private Map<String, QueryDef> ensureQueryDefMap(Map<String, Map<String, QueryDef>> queryDefs, String schemaName)
        {
            if (!queryDefs.containsKey(schemaName))
            {
                queryDefs.put(schemaName, new CaseInsensitiveMapWrapper<>(new LinkedHashMap<>()));
            }
            return queryDefs.get(schemaName);
        }

        private @NotNull
        Collection<QueryDef> getQueryDefs(String schemaName, boolean customQuery)
        {
            Map<String, Map<String, QueryDef>> queryDefs = customQuery ? _customQueryDefs : _queryDefs;

            if (schemaName == null)
            {
                List<QueryDef> queries = new ArrayList<>();

                // all query definitions in the container
                for (Map<String, QueryDef> queryMap : queryDefs.values())
                {
                    queries.addAll(queryMap.values());
                }
                return Collections.unmodifiableCollection(queries);
            }
            else if (queryDefs.containsKey(schemaName))
            {
                return Collections.unmodifiableCollection(queryDefs.get(schemaName).values());
            }
            return Collections.emptyList();
        }

        private @NotNull
        Map<String, QueryDef> getQueryMap(@NotNull String schemaName, boolean customQuery)
        {
            Map<String, Map<String, QueryDef>> queryDefs = customQuery ? _customQueryDefs : _queryDefs;

            if (queryDefs.containsKey(schemaName))
            {
                return queryDefs.get(schemaName);
            }
            return Collections.emptyMap();
        }
    }

    public static @NotNull
    List<QueryDef> getQueryDefs(Container container, String schema, boolean inheritableOnly, boolean includeSnapshots, boolean customQuery)
    {
        List<QueryDef> queries = new ArrayList<>();

        for (QueryDef queryDef : QUERY_DEF_DB_CACHE.get(container).getQueryDefs(schema, customQuery))
        {
            int mask = 0;
            int value = 0;

            if (inheritableOnly)
            {
                mask |= QueryManager.FLAG_INHERITABLE;
                value |= QueryManager.FLAG_INHERITABLE;
            }

            if (!includeSnapshots)
                mask |= QueryManager.FLAG_SNAPSHOT;

            if (mask != 0)
            {
                if ((queryDef.getFlags() & mask) == value)
                    queries.add(queryDef.clone());
            }
            else
                queries.add(queryDef.clone());
        }

        return Collections.unmodifiableList(queries);
    }

    public static @Nullable
    QueryDef getQueryDef(Container container, @NotNull String schemaName, @NotNull String name, boolean customQuery)
    {
        assert schemaName != null : "schemaName must be specified";

        QueryDef def = QUERY_DEF_DB_CACHE.get(container).getQueryMap(schemaName, customQuery).get(name);
        return def != null ? def.clone() : null;
    }

    public static @Nullable
    QueryDef getQueryDefById(Container container, int queryDefId)
    {
        return QUERY_DEF_DB_CACHE.get(container)._queryDefIdMap.get(queryDefId);
    }

    public static void uncache(Container c)
    {
        QUERY_DEF_DB_CACHE.remove(c);
    }
}
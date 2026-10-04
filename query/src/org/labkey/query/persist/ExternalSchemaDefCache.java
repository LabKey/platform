/*
 * Copyright (c) 2015-2026 LabKey Corporation
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
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.GUID;
import org.labkey.query.persist.AbstractExternalSchemaDef.SchemaType;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by adam on 11/14/2015.
 */
public class ExternalSchemaDefCache
{
    private static final Cache<GUID, ExternalSchemaCollections> EXTERNAL_SCHEMA_DEF_CACHE = CacheManager.getBlockingCache(CacheManager.UNLIMITED, CacheManager.DAY, "External/linked schema definitions", (id, argument) -> getCollectionsToCache(id));

    @Nullable
    public static <T extends AbstractExternalSchemaDef> T getSchemaDef(Container c, @Nullable String userSchemaName, Class<T> clazz)
    {
        if (userSchemaName == null)
            return null;

        return getCollections(c).getSchemaDef(userSchemaName, clazz);
    }

    @Nullable
    public static <T extends AbstractExternalSchemaDef> T getSchemaDef(Container c, int rowId, Class<T> clazz)
    {
        return getCollections(c).getSchemaDef(rowId, clazz);
    }

    @NotNull
    public static <T extends AbstractExternalSchemaDef> List<T> getSchemaDefs(@Nullable Container c, Class<T> clazz)
    {
        return getCollections(c).getSchemaDefs(clazz);
    }

    private static ExternalSchemaCollections getCollections(@Nullable Container c)
    {
        return EXTERNAL_SCHEMA_DEF_CACHE.get(null == c ? null : c.getEntityId());
    }

    public static void uncache(@Nullable Container c)
    {
        if (null != c)
            EXTERNAL_SCHEMA_DEF_CACHE.remove(c.getEntityId());
        EXTERNAL_SCHEMA_DEF_CACHE.remove(null);  // Clear out the full list
    }

    private static final ExternalSchemaCollections EMPTY_COLLECTION = new ExternalSchemaCollections(Collections.emptyMap(), Collections.emptyMap());

    /** @param containerId the container to use, or null for data for ALL containers */
    private static ExternalSchemaCollections getCollectionsToCache(@Nullable GUID containerId)
    {
        Map<Class<? extends AbstractExternalSchemaDef>, Map<String, AbstractExternalSchemaDef>> byName = new HashMap<>();
        Map<Class<? extends AbstractExternalSchemaDef>, Map<Integer, AbstractExternalSchemaDef>> byRowId = new HashMap<>();

        SimpleFilter filter = null != containerId ? new SimpleFilter(FieldKey.fromParts("Container"), containerId.toString()) : new SimpleFilter();

        new TableSelector(QueryManager.get().getTableInfoExternalSchema(), filter, null).forEach(rs -> {
            String schemaTypeName = rs.getString("SchemaType");
            SchemaType type = SchemaType.valueOf(schemaTypeName);
            AbstractExternalSchemaDef def = type.handle(rs);
            byRowId.computeIfAbsent(type.getSchemaDefClass(), x -> new HashMap<>()).put(def.getExternalSchemaId(), def);
            byRowId.computeIfAbsent(AbstractExternalSchemaDef.class, x -> new HashMap<>()).put(def.getExternalSchemaId(), def);

            // Don't bother in the null case (site-wide list)... we only need one map and by-name is likely not unique
            if (null != containerId)
            {
                byName.computeIfAbsent(type.getSchemaDefClass(), x -> new CaseInsensitiveHashMap<>()).put(def.getUserSchemaName(), def);
                byName.computeIfAbsent(AbstractExternalSchemaDef.class, x -> new CaseInsensitiveHashMap<>()).put(def.getUserSchemaName(), def);
            }
        });

        // Issue 53472 - when there are tens of thousands of containers, it takes a lot of memory
        // to cache with unique empty collections. Use a shared empty collection
        if (byName.isEmpty() && byRowId.isEmpty())
        {
            return EMPTY_COLLECTION;
        }
        return new ExternalSchemaCollections(Collections.unmodifiableMap(byName), Collections.unmodifiableMap(byRowId));
    }

    private static class ExternalSchemaCollections
    {
        private final Map<Class<? extends AbstractExternalSchemaDef>, Map<String, AbstractExternalSchemaDef>> _byName;
        private final Map<Class<? extends AbstractExternalSchemaDef>, Map<Integer, AbstractExternalSchemaDef>> _byRowId;

        private ExternalSchemaCollections(Map<Class<? extends AbstractExternalSchemaDef>, Map<String, AbstractExternalSchemaDef>> byName, Map<Class<? extends AbstractExternalSchemaDef>, Map<Integer, AbstractExternalSchemaDef>> byRowId)
        {
            _byName = byName;
            _byRowId = byRowId;
        }

        @Nullable
        private <T extends AbstractExternalSchemaDef> T getSchemaDef(String userSchemaName, Class<T> clazz)
        {
            Map<String, AbstractExternalSchemaDef> map = _byName.get(clazz);
            return map == null ? null : (T) map.get(userSchemaName);
        }

        @Nullable
        private <T extends AbstractExternalSchemaDef> T getSchemaDef(int rowId, Class<T> clazz)
        {
            Map<Integer, AbstractExternalSchemaDef> map = _byRowId.get(clazz);
            return map == null ? null : (T) map.get(rowId);
        }

        @NotNull
        private <T extends AbstractExternalSchemaDef> List<T> getSchemaDefs(Class<T> clazz)
        {
            Map<Integer, AbstractExternalSchemaDef> map = _byRowId.get(clazz);
            if (map == null)
            {
                return Collections.emptyList();
            }
            Collection<T> collection = (Collection<T>) map.values();
            return new LinkedList<>(collection);
        }
    }
}

/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.study;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.collections.LabKeyCollectors;
import org.labkey.api.data.Container;
import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableInfoGetter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.security.User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class QueryHelper<K, T extends StudyCachable<K, T>, SC extends QueryHelper.StudyCacheCollections<K, T>>
{
    private final BlockingCache<Container, SC> _cache;
    private final Class<T> _objectClass;
    private final TableInfoGetter _tableInfoGetter;
    protected final String _defaultSortString;

    public QueryHelper(TableInfoGetter tableInfoGetter, Class<T> objectClass)
    {
        this(tableInfoGetter, objectClass, null);
    }

    public QueryHelper(TableInfoGetter tableInfoGetter, Class<T> objectClass, @Nullable String defaultSortString)
    {
        _tableInfoGetter = tableInfoGetter;
        _objectClass = objectClass;
        _defaultSortString = defaultSortString;
        TableInfo tableInfo = _tableInfoGetter.getTableInfo();
        _cache = DatabaseCache.get(tableInfo.getSchema().getScope(), tableInfo.getCacheSize(), "StudyCache: " + tableInfo.getName(), (key, argument) ->
            createCollections(
                getTableSelector(key).stream(_objectClass)
                    .peek(StudyCachable::lock)
                    .toList()
            ));
    }

    /**
     * Return a Collection of all T objects in the provided Container, in default order
     */
    public @NotNull Collection<T> getCollection(Container c)
    {
        return getCollections(c).getCollection();
    }

    public T get(final Container c, final K pk)
    {
        return getCollections(c).get(pk);
    }

    protected TableSelector getTableSelector(Container c)
    {
        return new TableSelector(getTableInfo(), SimpleFilter.createContainerFilter(c), new Sort(_defaultSortString));
    }

    protected SC getCollections(Container c)
    {
        return _cache.get(c, null);
    }

    protected SC createCollections(Collection<T> collection)
    {
        return (SC) new QueryHelper.StudyCacheCollections<>(collection);
    }

    public T create(User user, T obj)
    {
        T ret = Table.insert(user, getTableInfo(), obj);
        clearCache(obj.getContainer());
        return ret;
    }

    public T update(User user, T obj)
    {
        return update(user, obj, obj.getPrimaryKey());
    }

    public T update(User user, T obj, Object... pk)
    {
        T ret = Table.update(user, getTableInfo(), obj, pk);
        clearCache(obj.getContainer());
        return ret;
    }

    public void delete(T obj)
    {
        Table.delete(getTableInfo(), obj.getPrimaryKey());
        clearCache(obj.getContainer());
    }

    public TableInfo getTableInfo()
    {
        return _tableInfoGetter.getTableInfo();
    }

    public void clearCache(Container c)
    {
        _cache.remove(c);
    }

    public void clearCache()
    {
        _cache.clear();
    }

    // By default, holds a single map of PK -> V, but helpers can override to add other collections
    public static class StudyCacheCollections<K, V extends StudyCachable<K, V>>
    {
        private final Map<K, V> _map;

        // Receives a collection of locked T objects
        public StudyCacheCollections(Collection<V> collection)
        {
            _map = Collections.unmodifiableMap(collection.stream()
                .collect(LabKeyCollectors.toLinkedMap(StudyCachable::getPrimaryKey, v -> v)));
        }

        public @Nullable V get(K key)
        {
            return _map.get(key);
        }

        public @NotNull Collection<V> getCollection()
        {
            return _map.values();
        }
    }
}

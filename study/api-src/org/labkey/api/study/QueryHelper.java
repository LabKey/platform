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
import org.labkey.api.collections.LabKeyCollectors;
import org.labkey.api.data.Container;
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

public class QueryHelper<K extends StudyCachable<K>>
{
    private final Class<K> _objectClass;
    private final TableInfoGetter _tableInfoGetter;
    protected final String _defaultSortString;

    public QueryHelper(TableInfoGetter tableInfoGetter, Class<K> objectClass)
    {
        this(tableInfoGetter, objectClass, null);
    }

    public QueryHelper(TableInfoGetter tableInfoGetter, Class<K> objectClass, @Nullable String defaultSortString)
    {
        _tableInfoGetter = tableInfoGetter;
        _objectClass = objectClass;
        _defaultSortString = defaultSortString;
    }

    /**
     * Return a Collection of all K objects in the provided Container, in default order
     */
    public @NotNull Collection<K> getCollection(Container c)
    {
        return getCollections(c).getCollection();
    }

    public K get(final Container c, final int rowId)
    {
        return getCollections(c).get(rowId);
    }

    protected StudyCacheCollections getCollections(Container c)
    {
        return StudyCache.get(getTableInfo(), c, (key, argument) ->
            createCollections(
                new TableSelector(getTableInfo(), SimpleFilter.createContainerFilter(c), new Sort(_defaultSortString)).stream(_objectClass)
                    .peek(StudyCachable::lock)
                    .toList()
            )
        );
    }

    protected StudyCacheCollections createCollections(Collection<K> collection)
    {
        return new StudyCacheCollections(collection);
    }

    public K create(User user, K obj)
    {
        K ret = Table.insert(user, getTableInfo(), obj);
        clearCache(obj.getContainer());
        return ret;
    }

    public K update(User user, K obj)
    {
        return update(user, obj, obj.getPrimaryKey());
    }

    public K update(User user, K obj, Object... pk)
    {
        K ret = Table.update(user, getTableInfo(), obj, pk);
        clearCache(obj.getContainer());
        return ret;
    }

    public void delete(K obj)
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
        StudyCache.clearCache(getTableInfo(), c);
    }

    public void clearCache()
    {
        StudyCache.clearCache(getTableInfo());
    }

    // By default, holds a single map of PK -> StudyCachable<K>, but helpers can override to add other collections
    public class StudyCacheCollections
    {
        private final Map<Object, K> _map;

        // Receives a collection of locked K objects
        public StudyCacheCollections(Collection<K> collection)
        {
            _map = Collections.unmodifiableMap(collection.stream()
                .collect(LabKeyCollectors.toLinkedMap(StudyCachable::getPrimaryKey, v -> v)));
        }

        public @Nullable K get(Object key)
        {
            return _map.get(key);
        }

        public @NotNull Collection<K> getCollection()
        {
            return _map.values();
        }
    }
}

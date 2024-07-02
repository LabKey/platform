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
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.collections.LabKeyCollectors;
import org.labkey.api.data.Container;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableInfoGetter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class QueryHelper<K extends StudyCachable<K>>
{
    private final Class<K> _objectClass;
    private final TableInfoGetter _tableInfoGetter;
    private final String _defaultSortString;
    private final FieldKey _rowIdFieldKey;

    public QueryHelper(TableInfoGetter tableInfoGetter, Class<K> objectClass)
    {
        this(tableInfoGetter, objectClass, null);
    }

    public QueryHelper(TableInfoGetter tableInfoGetter, Class<K> objectClass, @Nullable String defaultSortString)
    {
        this(tableInfoGetter, objectClass, defaultSortString, null);
    }

    public QueryHelper(TableInfoGetter tableInfoGetter, Class<K> objectClass, @Nullable String defaultSortString, @Nullable String rowIdColumnName)
    {
        _tableInfoGetter = tableInfoGetter;
        _objectClass = objectClass;
        _defaultSortString = defaultSortString;
        _rowIdFieldKey = FieldKey.fromString(null != rowIdColumnName ? rowIdColumnName : "RowId");
    }

    // Dataset helper uses this to:
    // - Filter by Cohort + Type
    // - Filter by Category
    // - Select by Label (case-insensitive. Note: study.Datasets has a unique constraint on Container, LOWER(Label))
    // - Select by EntityId
    // - Select by Name (case-insensitive. Note: study.Datasets has a unique constraint on Container, LOWER(Name))
    public List<K> getList(Container c, SimpleFilter filterArg)
    {
        String cacheId = getCacheId(filterArg);

        CacheLoader<String, Object> loader = (key, argument) -> {
            SimpleFilter filter = null;

            if (null != filterArg)
            {
                filter = filterArg;
            }
            else if (null != getTableInfo().getColumn("container"))
            {
                filter = SimpleFilter.createContainerFilter(c);
            }

            if (null != filter && !filter.hasContainerEqualClause())
                filter.addCondition(FieldKey.fromParts("Container"), c);

            List<K> objs = new TableSelector(getTableInfo(), filter, null).getArrayList(_objectClass);
            // Make both the objects and the list itself immutable so that we don't end up with a corrupted
            // version in the cache
            for (StudyCachable<K> obj : objs)
                obj.lock();
            return Collections.unmodifiableList(objs);
        };
        return (List<K>)StudyCache.get(getTableInfo(), c, cacheId, loader);
    }

    /**
     * Return a Collection of all K objects in the provided Container, in default order
     */
    public @NotNull Collection<K> getCollection(Container c)
    {
        return getMap(c).getCollection();
    }

    public K get(final Container c, final int rowId)
    {
        CacheLoader<String, Object> loader = (key, argument) -> {
            SimpleFilter filter = SimpleFilter.createContainerFilter(c);
            filter.addCondition(_rowIdFieldKey, rowId);
            StudyCachable<K> obj = new TableSelector(getTableInfo(), filter, null).getObject(_objectClass);
            if (obj != null)
                obj.lock();
            return obj;
        };
        Object obj = StudyCache.get(getTableInfo(), c, rowId, loader);

        K obj2 = getMap(c).get(rowId);

        assert Objects.equals(obj, obj2);

        return (K)obj;
    }

    protected StudyCacheMap getMap(Container c)
    {
        return StudyCache.get(getTableInfo(), c, (key, argument) ->
            createMap(new TableSelector(getTableInfo(), SimpleFilter.createContainerFilter(c), new Sort(_defaultSortString)).getCollection(_objectClass)));
    }

    protected StudyCacheMap createMap(Collection<K> collection)
    {
        return new StudyCacheMap(collection);
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

    protected String getCacheId(@Nullable Filter filter)
    {
        if (filter == null)
            return "~ALL";
        else
        {
            return filter.toSQLString(getTableInfo().getSqlDialect());
        }
    }

    public class StudyCacheMap
    {
        private final Map<Object, K> _map;

        // Receives a collection of locked K objects
        public StudyCacheMap(Collection<K> collection)
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

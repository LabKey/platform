/*
 * Copyright (c) 2006-2016 LabKey Corporation
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

package org.labkey.study;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheLoader;
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
import org.labkey.api.study.StudyCachable;

import java.util.Collections;
import java.util.List;

/**
 * User: brittp
 * Date: Feb 24, 2006
 * Time: 2:04:14 PM
 */
public class QueryHelper<K extends StudyCachable>
{
    private final Class<K> _objectClass;
    private final TableInfoGetter _tableInfoGetter;

    public QueryHelper(TableInfoGetter tableInfoGetter, Class<K> objectClass)
    {
        _tableInfoGetter = tableInfoGetter;
        _objectClass = objectClass;
    }

    public List<K> get(Container c)
    {
        return get(c, null, null);
    }

    public List<K> get(Container c, String sortString)
    {
        return get(c, null, sortString);
    }

    public List<K> get(Container c, SimpleFilter filter)
    {
        return get(c, filter, null);
    }

    public List<K> get(final Container c, @Nullable final SimpleFilter filterArg, @Nullable final String sortString)
    {
        String cacheId = getCacheId(filterArg);
        if (sortString != null)
            cacheId += "; sort = " + sortString;

        CacheLoader<String, Object> loader = new CacheLoader<String, Object>()
        {
            @Override
            public Object load(String key, Object argument)
            {
                SimpleFilter filter = null;

                if(null != filterArg)
                {
                    filter = filterArg;
                }
                else if(null != getTableInfo().getColumn("container"))
                {
                    filter = SimpleFilter.createContainerFilter(c);
                }

                if (null != filter && !filter.hasContainerEqualClause())
                    filter.addCondition(FieldKey.fromParts("Container"), c);

                Sort sort = null;
                if (sortString != null)
                    sort = new Sort(sortString);
                List<K> objs = new TableSelector(getTableInfo(), filter, sort).getArrayList(_objectClass);
                // Make both the objects and the list itself immutable so that we don't end up with a corrupted
                // version in the cache
                for (StudyCachable obj : objs)
                    obj.lock();
                return Collections.unmodifiableList(objs);
            }
        };
        return (List<K>)StudyCache.get(getTableInfo(), c, cacheId, loader);
    }

    public K get(Container c, double rowId)
    {
        return get(c, (Object)rowId, "RowId");
    }

    public K get(Container c, int rowId)
    {
        return get(c, (Object)rowId, "RowId");
    }

    public K get(Container c, double rowId, String rowIdColumnName)
    {
        return get(c, (Object)rowId, rowIdColumnName);
    }

    public K get(Container c, int rowId, String rowIdColumnName)
    {
        return get(c, (Object)rowId, rowIdColumnName);
    }

    private K get(final Container c, final Object rowId, final String rowIdColumnName)
    {
        CacheLoader<String, Object> loader = new CacheLoader<String,Object>()
        {
            @Override
            public Object load(String key, Object argument)
            {
                SimpleFilter filter = SimpleFilter.createContainerFilter(c);
                filter.addCondition(rowIdColumnName, rowId);
                StudyCachable obj = new TableSelector(getTableInfo(), filter, null).getObject(_objectClass);
                if (obj != null)
                    obj.lock();
                return obj;
            }
        };
        Object obj = StudyCache.get(getTableInfo(), c, rowId, loader);
        return (K)obj;
    }

    public K create(User user, K obj)
    {
        clearCache(obj);
        return Table.insert(user, getTableInfo(), obj);
    }

    public K update(User user, K obj)
    {
        return update(user, obj, obj.getPrimaryKey());
    }

    public K update(User user, K obj, Object... pk)
    {
        clearCache(obj);
        return Table.update(user, getTableInfo(), obj, pk);
    }

    public void delete(K obj)
    {
        clearCache(obj);
        Table.delete(getTableInfo(), obj.getPrimaryKey());
    }

    public TableInfo getTableInfo()
    {
        return _tableInfoGetter.getTableInfo();
    }

    public void clearCache(Container c)
    {
        StudyCache.clearCache(getTableInfo(), c);
    }

    public void clearCache(K obj)
    {
        StudyCache.uncache(getTableInfo(), obj.getContainer(), obj.getPrimaryKey().toString());
    }

    protected String getCacheId(Filter filter)
    {
        if (filter == null)
            return "~ALL";
        else
        {
            String cacheId = filter.toSQLString(StudySchema.getInstance().getSqlDialect());
            return cacheId;
        }
    }
}

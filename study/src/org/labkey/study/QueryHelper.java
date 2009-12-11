/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.study.model.StudyCachable;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 * User: brittp
 * Date: Feb 24, 2006
 * Time: 2:04:14 PM
 */
public class QueryHelper<K extends StudyCachable>
{
    private Class<K> _objectClass;
    private Set<String> _cachedFilters = new HashSet<String>();
    private final TableInfoGetter _tableInfoGetter;
    private final K _missMarker;

    public QueryHelper(TableInfoGetter tableInfoGetter, Class<K> objectClass)
    {
        _tableInfoGetter = tableInfoGetter;
        _objectClass = objectClass;

        try
        {
            _missMarker = objectClass.newInstance();
        }
        catch (Exception e)
        {
            // Something has gone horribly wrong
            throw new RuntimeException(e);
        }
    }

    public K[] get(Container c) throws SQLException
    {
        return get(c, null, null);
    }

    public K[] get(Container c, String sortString) throws SQLException
    {
        return get(c, null, sortString);
    }

    public K[] get(Container c, SimpleFilter filter) throws SQLException
    {
        return get(c, filter, null);
    }

    public K[] get(Container c, SimpleFilter filter, String sortString) throws SQLException
    {
        String cacheId = getCacheId(filter);
        if (sortString != null)
            cacheId += "; sort = " + sortString;
        K[] objs = (K[])StudyCache.getCached(getTableInfo(), c.getId(), cacheId);
        if (objs == null)
        {
            if (filter == null)
                filter = new SimpleFilter("Container", c.getId());
            else if (!filter.hasContainerEqualClause())
                filter.addCondition("Container", c.getId());
            Sort sort = null;
            if (sortString != null)
                sort = new Sort(sortString);
            objs = Table.select(getTableInfo(), Table.ALL_COLUMNS,
                    filter, sort, _objectClass);
            StudyCache.cache(getTableInfo(), c.getId(), cacheId, objs);
            _cachedFilters.add(cacheId);
        }
        return objs;
    }

    public K get(Container c, double rowId) throws SQLException
    {
        return get(c, (Object)rowId, "RowId");
    }

    public K get(Container c, int rowId) throws SQLException
    {
        return get(c, (Object)rowId, "RowId");
    }

    public K get(Container c, double rowId, String rowIdColumnName) throws SQLException
    {
        return get(c, (Object)rowId, rowIdColumnName);
    }

    public K get(Container c, int rowId, String rowIdColumnName) throws SQLException
    {
        return get(c, (Object)rowId, rowIdColumnName);
    }

    private K get(Container c, Object rowId, String rowIdColumnName) throws SQLException
    {
        K obj = (K) StudyCache.getCached(getTableInfo(), c.getId(), rowId);
        if (obj == null)
        {
            SimpleFilter filter = new SimpleFilter("Container", c.getId());
            filter.addCondition(rowIdColumnName, rowId);
            obj = Table.selectObject(getTableInfo(), Table.ALL_COLUMNS, filter, null, _objectClass);
            if (null == obj)
                obj = _missMarker;
            StudyCache.cache(getTableInfo(), c.getId(), rowId, obj);
        }
        return (_missMarker == obj ? null : obj);
    }

    public K create(User user, K obj) throws SQLException
    {
        clearCache(obj);
        return Table.insert(user, getTableInfo(), obj);
    }

    public K update(User user, K obj) throws SQLException
    {
        return update(user, obj, new Object[] { obj.getPrimaryKey() });
    }

    public K update(User user, K obj, Object[] pk) throws SQLException
    {
        clearCache(obj);
        return Table.update(user, getTableInfo(), obj, pk);
    }

    public void delete(K obj, Object rowId, Object rowVersion) throws SQLException
    {
        clearCache(obj);
        Table.delete(getTableInfo(), rowId);
    }

    public void delete(K obj) throws SQLException
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
        StudyCache.clearCache(getTableInfo(), c.getId());
    }

    public void clearCache(K obj)
    {
        for (String filter : _cachedFilters)
            StudyCache.uncache(getTableInfo(), obj.getContainer().getId(), filter);
        _cachedFilters.clear();
        StudyCache.uncache(getTableInfo(), obj.getContainer().getId(), obj.getPrimaryKey().toString());
    }

    private String getCacheId(Filter filter)
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

/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import org.labkey.study.model.StudyCachable;
import org.labkey.api.data.*;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.Set;
import java.util.HashSet;

/**
 * User: brittp
 * Date: Feb 24, 2006
 * Time: 2:04:14 PM
 */
public class QueryHelper<K extends StudyCachable>
{
    private TableInfo _tableInfo;
    private Class<K> _objectClass;
    private Set<String> _cachedFilters = new HashSet<String>();

    public QueryHelper(TableInfo tableInfo, Class<K> objectClass)
    {
        _tableInfo = tableInfo;
        _objectClass = objectClass;
    }

    public K[] get(Container c) throws SQLException
    {
        return get(c, null, null);
    }

    public K[] get(Container c, String sortString) throws SQLException
    {
        return get(c, null, sortString);
    }

    public K[] get(Container c, Filter filter) throws SQLException
    {
        return get(c, filter, null);
    }

    public K[] get(Container c, Filter filter, String sortString) throws SQLException
    {
        String cacheId = getCacheId(filter);
        if (sortString != null)
            cacheId += "; sort = " + sortString;
        K[] objs = (K[])StudyCache.getCached(_tableInfo, c.getId(), cacheId);
        if (objs == null)
        {
            if (filter == null)
                filter = new SimpleFilter("Container", c.getId());
            Sort sort = null;
            if (sortString != null)
                sort = new Sort(sortString);
            objs = Table.select(_tableInfo, Table.ALL_COLUMNS,
                    filter, sort, _objectClass);
            StudyCache.cache(_tableInfo, c.getId(), cacheId, objs);
            _cachedFilters.add(cacheId);
        }
        return objs;
    }

    public K get(Container c, double rowId) throws SQLException
    {
        return get(c, rowId, "RowId");
    }

    public K get(Container c, int rowId) throws SQLException
    {
        return get(c, rowId, "RowId");
    }

    public K get(Container c, int rowId, String rowIdColumnName) throws SQLException
    {
        K obj = (K) StudyCache.getCached(_tableInfo, c.getId(), rowId);
        if (obj == null)
        {
            SimpleFilter filter = new SimpleFilter("Container", c.getId());
            filter.addCondition(rowIdColumnName, rowId);
            obj = Table.selectObject(_tableInfo, Table.ALL_COLUMNS,
                    filter, null, _objectClass);
            StudyCache.cache(_tableInfo, c.getId(), rowId, obj);
        }
        return obj;
    }

    public K get(Container c, double rowId, String rowIdColumnName) throws SQLException
    {
        K obj = (K) StudyCache.getCached(_tableInfo, c.getId(), rowId);
        if (obj == null)
        {
            SimpleFilter filter = new SimpleFilter("Container", c.getId());
            filter.addCondition(rowIdColumnName, rowId);
            obj = Table.selectObject(_tableInfo, Table.ALL_COLUMNS,
                    filter, null, _objectClass);
            StudyCache.cache(_tableInfo, c.getId(), rowId, obj);
        }
        return obj;
    }

    public K create(User user, K obj) throws SQLException
    {
        clearCache(obj);
        return Table.insert(user, _tableInfo, obj);
    }

    public K update(User user, K obj) throws SQLException
    {
        return update(user, obj, new Object[] { obj.getPrimaryKey() });
    }

    public K update(User user, K obj, Object[] pk) throws SQLException
    {
        clearCache(obj);
        return Table.update(user, _tableInfo, obj, pk, null);
    }

    public void delete(K obj, Object rowId, Object rowVersion) throws SQLException
    {
        clearCache(obj);
        Table.delete(_tableInfo, rowId, rowVersion);
    }

    public void delete(K obj) throws SQLException
    {
        clearCache(obj);
        Table.delete(_tableInfo, obj.getPrimaryKey(), null);
    }

    public TableInfo getTableInfo()
    {
        return _tableInfo;
    }

    public void clearCache(Container c)
    {
        StudyCache.clearCache(_tableInfo, c.getId());
    }

    public void clearCache(K obj)
    {
        for (String filter : _cachedFilters)
            StudyCache.uncache(_tableInfo, obj.getContainer().getId(), filter);
        _cachedFilters.clear();
        StudyCache.uncache(_tableInfo, obj.getContainer().getId(), obj.getPrimaryKey().toString());
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

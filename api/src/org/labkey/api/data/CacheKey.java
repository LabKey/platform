/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

package org.labkey.api.data;

import org.apache.commons.lang3.ObjectUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.DbCache;
import org.labkey.api.util.PageFlowUtil;

import java.sql.SQLException;


public class CacheKey<T, C extends Enum<C>> implements Cloneable, CacheLoader<String,Object>
{
    private void addBitMaskFilter(ColumnInfo column, int mask, int value)
    {
        SQLFragment ret = new SQLFragment("(((");
        ret.append(column.getAlias());
        ret.append(") &");
        ret.append(mask);
        ret.append(") = ");
        ret.append(value);
        ret.append(")");
        _filter.addWhereClause(ret.getSQL(), ret.getParams().toArray(), column.getName());
        addConditionToString(column.getName() + "&" + mask, value);
    }

    private TableInfo _table;
    private SimpleFilter _filter;
    private Class<T> _clazz;
    private StringBuilder _toString;
    
    protected CacheKey(TableInfo table, Class<T> clazz, @Nullable Container container)
    {
        _clazz = clazz;
        _table = table;
        _filter = new SimpleFilter();
        _toString = new StringBuilder(clazz.toString());
        if (container != null)
        {
            _filter.addCondition("container", container.getId());
            addConditionToString("container", container.getId());
        }
    }

    public void addCondition(C column, Object value)
    {
        addCondition(column, value, CompareType.EQUAL);
    }

    public void addIsNull(C column)
    {
        addCondition(column, null, CompareType.ISBLANK);
    }

    public void addIsNotNull(C column)
    {
        addCondition(column, null, CompareType.NONBLANK);
    }

    public void addCaseInsensitive(C column, String value)
    {
        String selectName = column.name();
        value = value.toLowerCase();
        _filter.addWhereClause("lower(" + selectName + ") = lower(?)", new Object[] { value }, column.name());
        addConditionToString(column.toString() + "~iequal", value);
    }

    private void addCondition(C column, Object value, CompareType ct)
    {
        _filter.addCondition(column.toString(), value, ct);
        addConditionToString(column.toString() + "~" + ct.getPreferredUrlKey(), value);        
    }


    private void addConditionToString(String columnName, Object value)
    {
        _toString.append("&");
        _toString.append(PageFlowUtil.encode(columnName));
        if (value != null)
        {
            _toString.append("=");
            _toString.append(PageFlowUtil.encode(ObjectUtils.toString(value)));
        }
    }


    public void setFlagMask(int mask, int value)
    {
        addBitMaskFilter(_table.getColumn("flags"), mask, value);
    }


    public String toString()
    {
        return _toString.toString();
    }


    public Object load(String stringKey, Object arg)
    {
        try
        {
            Object[] ret = Table.select(_table, Table.ALL_COLUMNS, _filter, null, _clazz);
            return ret;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    private BlockingCache getCache()
    {
        return new BlockingCache<String, Object>(DbCache.getCache(_table), null);
    }


    public T[] select() throws SQLException
    {
        return (T[])getCache().get(toString(), null, this);
    }


    public T selectObject() throws SQLException
    {
        T[] arr = select();
        assert null == arr || arr.length == 0 || arr.length == 1;
        return null == arr || 0==arr.length ? null : arr[0];
    }
}

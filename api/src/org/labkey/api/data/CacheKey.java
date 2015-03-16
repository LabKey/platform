/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.DbCache;
import org.labkey.api.cache.Wrapper;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;

import java.util.Objects;


public class CacheKey<T, C extends Enum<C>> implements Cloneable, CacheLoader<String, T[]>
{
    private final TableInfo _table;
    private final SimpleFilter _filter;
    private final Class<T> _clazz;
    private final StringBuilder _toString;
    
    protected CacheKey(TableInfo table, Class<T> clazz, @Nullable Container container)
    {
        _clazz = clazz;
        _table = table;
        _filter = new SimpleFilter();
        _toString = new StringBuilder(clazz.toString());
        if (container != null)
        {
            _filter.addCondition(FieldKey.fromParts("container"), container);
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
        _filter.addWhereClause("lower(" + selectName + ") = lower(?)", new Object[] { value }, FieldKey.fromParts(column.name()));
        addConditionToString(column.toString() + "~iequal", value);
    }

    private void addCondition(C column, @Nullable Object value, CompareType ct)
    {
        _filter.addCondition(column.toString(), value, ct);
        addConditionToString(column.toString() + "~" + ct.getPreferredUrlKey(), value);        
    }


    private void addConditionToString(String columnName, @Nullable Object value)
    {
        _toString.append("&");
        _toString.append(PageFlowUtil.encode(columnName));
        if (value != null)
        {
            _toString.append("=");
            _toString.append(PageFlowUtil.encode(Objects.toString(value, "")));
        }
    }


    private void addBitMaskFilter(ColumnInfo column, int mask, int value)
    {
        SQLFragment ret = new SQLFragment("(((");
        ret.append(column.getAlias());
        ret.append(") &");
        ret.append(mask);
        ret.append(") = ");
        ret.append(value);
        ret.append(")");
        _filter.addWhereClause(ret.getSQL(), ret.getParams().toArray(), column.getFieldKey());
        addConditionToString(column.getName() + "&" + mask, value);
    }


    public void setFlagMask(int mask, int value)
    {
        addBitMaskFilter(_table.getColumn("flags"), mask, value);
    }


    public String toString()
    {
        return _toString.toString();
    }


    public T[] load(String stringKey, Object arg)
    {
        return new TableSelector(_table, _filter, null).getArray(_clazz);
    }


    private BlockingCache<String, T[]> getCache()
    {
        DatabaseCache<Wrapper<T[]>> cache = DbCache.getCacheGeneric(_table);
        return new BlockingCache<>(cache, null);
    }


    public T[] select()
    {
        Cache<String, T[]> cache = getCache();
        return cache.get(toString(), null, this);
    }


    public T selectObject()
    {
        T[] arr = select();
        assert null == arr || arr.length == 0 || arr.length == 1;
        return null == arr || 0==arr.length ? null : arr[0];
    }
}

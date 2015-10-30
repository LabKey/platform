/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.data.Entity;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.util.UnexpectedException;

import java.io.Serializable;

public final class CstmView extends Entity implements Cloneable, Serializable
{
    private String _schema;
    private String _queryName;
    private int _customViewId;
    private Integer _owner;

    private String _name;
    private String _columns;
    private String _filter;
    private int _flags;

    public int getCustomViewId()
    {
        return _customViewId;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setCustomViewId(int id)
    {
        _customViewId = id;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public Integer getCustomViewOwner()
    {
        return _owner;
    }

    public void setCustomViewOwner(Integer owner)
    {
        _owner = owner;
    }

    public boolean isShared()
    {
        return null == _owner;
    }

    public String getColumns()
    {
        return _columns;
    }

    public void setColumns(String columns)
    {
        _columns = columns;
    }

    public String getSchema()
    {
        return _schema;
    }

    public void setSchema(String schema)
    {
        _schema = schema;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public void setQueryName(String name)
    {
        _queryName = name;
    }

    public int getFlags()
    {
        return _flags;
    }

    public void setFlags(int flags)
    {
        _flags = flags;
    }

    public String getFilter()
    {
        return _filter;
    }

    public void setFilter(String filter)
    {
        _filter = filter;
    }

    public String toString()
    {
        return getQueryName() + "." + getName() + ": " + _columns;
    }

    public CstmView clone()
    {
        try
        {
            return (CstmView) super.clone();
        }
        catch (CloneNotSupportedException cnse)
        {
            throw UnexpectedException.wrap(cnse);
        }
    }

    static
    {
        ObjectFactory.Registry.register(CstmView.class, new CstmViewObjectFactory());
    }

    public static class CstmViewObjectFactory extends BeanObjectFactory<CstmView>
    {
        CstmViewObjectFactory()
        {
            super(CstmView.class);
        }

        // there is a UQ constraint on (container, schema, queryname, customviewowner, name)
        // however UQ does not consider multiple nulls a problem...

//        @Override
//        protected void fixupMap(Map<String, Object> m, CstmView o)
//        {
//            if (null == StringUtils.trimToNull((String)m.get("name")))
//                m.put("name", "");
//        }
//
//        @Override
//        protected void fixupBean(CstmView v)
//        {
//            if (null == StringUtils.trimToNull(v._name))
//                v._name = null;
//        }
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CstmView that = (CstmView) o;

        if (_schema != null ? !_schema.equals(that._schema) : that._schema != null) return false;
        if (_queryName != null ? !_queryName.equals(that._queryName) : that._queryName != null) return false;
        if (_customViewId != that._customViewId) return false;
        if (_owner != null ? !_owner.equals(that._owner) : that._owner != null) return false;

        if (_name != null ? !_name.equals(that._name) : that._name != null) return false;
        if (_columns != null ? !_columns.equals(that._columns) : that._columns != null) return false;
        if (_filter != null ? !_filter.equals(that._filter) : that._filter != null) return false;

        return _flags == that._flags;
    }

    @Override
    public int hashCode()
    {
        int result = _schema != null ? _schema.hashCode() : 0;

        result = 31 * result + (_queryName != null ? _queryName.hashCode() : 0);
        result = 31 * result + _customViewId;
        result = 31 * result + (_owner != null ? _owner.hashCode() : 0);
        result = 31 * result + (_name != null ? _name.hashCode() : 0);
        result = 31 * result + (_columns != null ? _columns.hashCode() : 0);
        result = 31 * result + (_filter != null ? _filter.hashCode() : 0);
        result = 31 * result + _flags;

        return result;
    }
}

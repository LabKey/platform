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
}

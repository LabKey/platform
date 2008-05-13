/*
 * Copyright (c) 2006-2007 LabKey Corporation
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

import org.labkey.api.data.Entity;
import org.labkey.api.data.Container;
import org.labkey.api.data.CacheKey;
import org.labkey.api.security.User;
import org.labkey.api.util.UnexpectedException;

import java.io.Serializable;

public class CstmView extends Entity implements Cloneable, Serializable
{
    public enum Column
    {
        container,
        schema,
        queryname,
        name,
        customviewowner,
        flags
    }
    public static class Key extends CacheKey<CstmView, Column>
    {
        public Key(Container container)
        {
            super(QueryManager.get().getTableInfoCustomView(), CstmView.class, container);
        }
        public void setSchema(String schema)
        {
            addCondition(Column.schema, schema);
        }
        public void setQueryName(String queryName)
        {
            addCondition(Column.queryname, queryName);
        }
        public void setName(String name)
        {
            addCondition(Column.name, name);
        }
        
        public void setUser(User user)
        {
            if (user == null)
                addIsNull(Column.customviewowner);
            else
                addCondition(Column.customviewowner, user.getUserId());
        }
    }

    String _schema;
    String _queryName;
    int _customViewId;
    Integer _owner;

    private String _name;
    String _columns;
    String _filter;
    int _flags;

    public int getCustomViewId()
    {
        return _customViewId;
    }

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
}

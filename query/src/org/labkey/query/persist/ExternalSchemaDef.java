/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

import org.labkey.api.data.CacheKey;
import org.labkey.api.data.Container;
import org.labkey.api.data.Entity;

public class ExternalSchemaDef extends Entity
{
    public enum Column
    {
        userschemaname
    }

    static public class Key extends CacheKey<ExternalSchemaDef, Column>
    {
        public Key(Container container)
        {
            super(QueryManager.get().getTableInfoExternalSchema(), ExternalSchemaDef.class, container);
        }
        public void setUserSchemaName(String name)
        {
            addCondition(Column.userschemaname, name);
        }
    }

    private int _externalSchemaId;
    private String _userSchemaName;
    private String _dbSchemaName;
    private boolean _editable;
    private String _metaData;
    private String _dataSource;
    private boolean _indexable;
    private String _tables = "*";

    public int getExternalSchemaId()
    {
        return _externalSchemaId;
    }

    public void setExternalSchemaId(int id)
    {
        _externalSchemaId = id;
    }

    public String getUserSchemaName()
    {
        return _userSchemaName;
    }

    public void setUserSchemaName(String name)
    {
        _userSchemaName = name;
    }

    public String getDbSchemaName()
    {
        return _dbSchemaName;
    }

    public void setDbSchemaName(String name)
    {
        _dbSchemaName = name;
    }

    public String getMetaData()
    {
        return _metaData;
    }

    public void setMetaData(String metaData)
    {
        _metaData = metaData;
    }

    public String getDataSource()
    {
        return _dataSource;
    }

    public void setDataSource(String dataSource)
    {
        _dataSource = dataSource;
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ExternalSchemaDef that = (ExternalSchemaDef) o;

        if (_externalSchemaId != that._externalSchemaId) return false;
        if (_dbSchemaName != null ? !_dbSchemaName.equals(that._dbSchemaName) : that._dbSchemaName != null)
            return false;
        if (_metaData != null ? !_metaData.equals(that._metaData) : that._metaData != null) return false;
        if (_userSchemaName != null ? !_userSchemaName.equals(that._userSchemaName) : that._userSchemaName != null)
            return false;

        return true;
    }

    public int hashCode()
    {
        int result;
        result = _externalSchemaId;
        result = 31 * result + (_userSchemaName != null ? _userSchemaName.hashCode() : 0);
        result = 31 * result + (_dbSchemaName != null ? _dbSchemaName.hashCode() : 0);
        result = 31 * result + (_metaData != null ? _metaData.hashCode() : 0);
        return result;
    }

    public boolean isEditable()
    {
        return _editable;
    }

    public void setEditable(boolean editable)
    {
        _editable = editable;
    }

    public boolean isIndexable()
    {
        return _indexable;
    }

    public void setIndexable(boolean indexable)
    {
        _indexable = indexable;
    }

    public String getTables()
    {
        return _tables;
    }

    public void setTables(String tables)
    {
        _tables = tables;
    }
}

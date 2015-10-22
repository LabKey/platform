/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
import org.labkey.api.query.SchemaKey;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.UnexpectedException;

public class QueryDef extends Entity implements Cloneable
{
    public enum Column
    {
        container,
        sql,
        schema,
        name,
        queryDefId,
    }

    static public class Key extends CacheKey<QueryDef, Column>
    {
        public Key(Container container, boolean customQuery)
        {
            super(QueryManager.get().getTableInfoQueryDef(), QueryDef.class, container);

            if (customQuery)
            {
                addIsNotNull(Column.sql);
            }
            else
            {
                // Metadata for built-in tables is stored with a NULL value for the SQL
                addIsNull(Column.sql);
            }
        }

        public void setSchema(String schema)
        {
            addCondition(Column.schema, schema);
        }

        public void setQueryName(String queryName)
        {
            addCaseInsensitive(Column.name, queryName);
        }

        public void setQueryDefId(int id)
        {
            addCondition(Column.queryDefId, id);
        }
    }

    public QueryDef()
    {
        assert MemTracker.getInstance().put(this);
    }

    private int _queryDefId;
    private String _sql;
    private String _metadata;
    private double _schemaVersion;
    private int _flags;
    private String _name;
    private String _description;
    private SchemaKey _schema;

    public int getQueryDefId()
    {
        return _queryDefId;
    }

    public void setQueryDefId(int id)
    {
        _queryDefId = id;
    }

    public String getSql()
    {
        return _sql;
    }
    public void setSql(String sql)
    {
        _sql = sql;
    }
    public String getMetaData()
    {
        return _metadata;
    }
    public void setMetaData(String tableInfo)
    {
        _metadata = tableInfo;
    }
    public double getSchemaVersion()
    {
        return _schemaVersion;
    }
    public void setSchemaVersion(double schemaVersion)
    {
        _schemaVersion = schemaVersion;
    }
    public int getFlags()
    {
        return _flags;
    }
    public void setFlags(int flags)
    {
        _flags = flags;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getSchema()
    {
        return _schema.toString();
    }

    public void setSchema(String schema)
    {
        _schema = SchemaKey.fromString(schema);
    }

    public SchemaKey getSchemaPath()
    {
        return _schema;
    }

    public void setSchemaPath(SchemaKey schemaName)
    {
        _schema = schemaName;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String toString()
    {
        return getName() + ": " + getSql() + " -- " + getDescription();
    }

    public QueryDef clone()
    {
        try
        {
            return (QueryDef) super.clone();
        }
        catch (CloneNotSupportedException cnse)
        {
            throw UnexpectedException.wrap(cnse);
        }
    }
    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        QueryDef that = (QueryDef) o;

        if (_sql != null ? !_sql.equals(that._sql) : that._sql != null) return false;
        if (_metadata != null ? !_metadata.equals(that._metadata) : that._metadata != null) return false;
        if (_schemaVersion != that._schemaVersion) return false;
        if (_flags != that._flags) return false;
        if (_name != null ? !_name.equals(that._name) : that._name != null) return false;
        if (_description != null ? !_description.equals(that._description) : that._description != null) return false;
        if (_schema != null ? !_schema.toString().equals(that._schema.toString()) : that._schema != null) return false;

        return _queryDefId == that._queryDefId;
    }

    @Override
    public int hashCode()
    {
        int result = _queryDefId;

        result = 31 * result + (_sql != null ? _sql.hashCode() : 0);
        result = 31 * result + (_metadata != null ? _metadata.hashCode() : 0);
        result = 31 * result + (int)_schemaVersion;
        result = 31 * result + _flags;
        result = 31 * result + (_name != null ? _name.hashCode() : 0);
        result = 31 * result + (_description != null ? _description.hashCode() : 0);
        result = 31 * result + (_schema != null ? _schema.toString().hashCode() : 0);

        return result;
    }
}

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

package org.labkey.query.persist;

import org.labkey.api.data.Entity;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.UnexpectedException;

public class QueryDef extends Entity implements Cloneable
{
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
}

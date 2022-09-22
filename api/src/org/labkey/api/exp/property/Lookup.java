/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.exp.property;

import org.json.old.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.query.SchemaKey;

import java.util.Objects;

public class Lookup
{
    Container _container;
    SchemaKey _schemaKey;
    String _queryName;

    public Lookup()
    {
    }

    // Callers need to be explicit about how this is encoded.  This constructor assumes "SchemaKey.toString()" encoding.
    // Consider using SchemaKey constructor instead.
    @Deprecated
    public Lookup(Container c, String schema, String query)
    {
        _container = c;
        _schemaKey = null == schema ? null : SchemaKey.fromString(schema);
        _queryName = query;
    }

    public Lookup(Container c, SchemaKey schema, String query)
    {
        _container = c;
        _schemaKey = schema;
        _queryName = query;
    }

    public Container getContainer()
    {
        return _container;
    }

    @Deprecated
    public String getSchemaName()
    {
        return Objects.toString(_schemaKey,null);
    }

    public SchemaKey getSchemaKey()
    {
        return _schemaKey;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public void setSchemaName(String name)
    {
        _schemaKey = null == name ? null : SchemaKey.fromString(name);
    }

    public void setQueryName(String name)
    {
        _queryName = name;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Lookup lookup = (Lookup) o;

        if (_container != null ? !_container.equals(lookup._container) : lookup._container != null) return false;
        if (_queryName != null ? !_queryName.equalsIgnoreCase(lookup._queryName) : lookup._queryName != null) return false;
        if (_schemaKey != null ? !_schemaKey.equals(lookup._schemaKey) : lookup._schemaKey != null) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = _container != null ? _container.hashCode() : 0;
        result = 31 * result + (_schemaKey != null ? _schemaKey.hashCode() : 0);
        result = 31 * result + (_queryName != null ? _queryName.hashCode() : 0);
        return result;
    }

    public String toJSONString()
    {
        JSONObject json = new JSONObject();

        json.put("schemaName", getSchemaName());
        json.put("queryName", getQueryName());
        if (getContainer() != null)
            json.put("containerId", getContainer().getId());

        return json.toString();
    }

    public void fromJSONString(String jsonStr)
    {
        JSONObject json = new JSONObject(jsonStr);
        setSchemaName(json.getString("schemaName"));
        setQueryName(json.getString("queryName"));
        if (json.has("containerId"))
            setContainer(ContainerManager.getForId(json.getString("containerId")));
    }
}

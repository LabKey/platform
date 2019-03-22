/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;

public class Lookup
{
    Container _container;
    String _schemaName;
    String _queryName;

    public Lookup()
    {
    }

    public Lookup(Container c, String schema, String query)
    {
        _container = c;
        _schemaName = schema;
        _queryName = query;
    }

    public Container getContainer()
    {
        return _container;
    }

    public String getSchemaName()
    {
        return _schemaName;
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
        _schemaName = name;
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
        if (_schemaName != null ? !_schemaName.equalsIgnoreCase(lookup._schemaName) : lookup._schemaName != null) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = _container != null ? _container.hashCode() : 0;
        result = 31 * result + (_schemaName != null ? _schemaName.hashCode() : 0);
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

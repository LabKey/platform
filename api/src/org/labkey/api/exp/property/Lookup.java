/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

import org.labkey.api.data.Container;

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
}

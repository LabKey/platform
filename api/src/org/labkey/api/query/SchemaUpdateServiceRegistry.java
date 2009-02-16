/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.query;

import org.labkey.api.security.User;
import org.labkey.api.data.Container;

import java.util.Map;
import java.util.HashMap;

/*
* User: Dave
* Date: Jun 13, 2008
* Time: 1:13:40 PM
*/
/**
 * Provides a singleton-style registry of SchemaUpdateService implementations.
 * The registry is keyed by schema name. Implementations of SchemaUpdateService
 * should register themselves at module startup with this class.
 */
public class SchemaUpdateServiceRegistry
{
    private static SchemaUpdateServiceRegistry _instance = null;
    private final Map<String,SchemaUpdateService> _registry = new HashMap<String,SchemaUpdateService>();

    /**
     * Returns the one and only instance of the class
     * @return The singleton instance
     */
    public static synchronized SchemaUpdateServiceRegistry get()
    {
        if(null == _instance)
            _instance = new SchemaUpdateServiceRegistry();
        return _instance;
    }

    /**
     * Registers a new SchemaUpdateService implementation. Call this
     * during your module startup to register your implementations.
     * @param service The service instance.
     */
    public void register(SchemaUpdateService service)
    {
        synchronized(_registry)
        {
            _registry.put(service.getSchemaName().toLowerCase(), service);
        }
    }

    /**
     * Returns the SchemaUpdateService implementation for the given schema name.
     * If there is no implementation registered for that name, it returns null.
     * @param schemaName The schema name.
     * @return The SchemaUpdateService instance for that schema name, or null.
     */
    public SchemaUpdateService getService(String schemaName)
    {
        SchemaUpdateService ret;
        synchronized(_registry)
        {
            ret = _registry.get(schemaName.toLowerCase());
        }
        return ret;
    }

    //private c-tor for singleton
    private SchemaUpdateServiceRegistry()
    {
    }
}
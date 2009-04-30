/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
import org.labkey.api.data.*;
import org.labkey.api.collections.CaseInsensitiveHashMap;

import java.util.*;

/**
 * A schema, scoped to a particular container
 */
final public class DefaultSchema extends AbstractSchema
{
    static public abstract class SchemaProvider
    {
        abstract public QuerySchema getSchema(DefaultSchema schema);
    }
    static final Map<String, SchemaProvider> _providers = Collections.synchronizedMap(new CaseInsensitiveHashMap<SchemaProvider>());

    static public void registerProvider(String name, SchemaProvider provider)
    {
        _providers.put(name, provider);
    }

    final Map<String, QuerySchema> _schemas;
    static
    {
        registerProvider("Folder", new FolderSchemaProvider()
        {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                return new FolderSchema(schema.getUser(), schema.getContainer(), null);
            }
        });
        registerProvider("Project", new FolderSchemaProvider()
        {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                Container container = schema.getContainer().getProject();
                return new FolderSchema(schema.getUser(), container, DefaultSchema.get(schema.getUser(), container));
            }
        });
        registerProvider("Shared", new FolderSchemaProvider(){
            public QuerySchema getSchema(DefaultSchema schema)
            {
                Container container = ContainerManager.getSharedContainer();
                return new FolderSchema(schema.getUser(), container, DefaultSchema.get(schema.getUser(), container));
            }
        });
    }
    static public DefaultSchema get(User user, Container container)
    {
        return new DefaultSchema(user, container);
    }

    private DefaultSchema(User user, Container container)
    {
        super(CoreSchema.getInstance().getSchema(), user, container);
        _schemas = new CaseInsensitiveHashMap<QuerySchema>();
    }

    public TableInfo getTable(String name)
    {
        return null;
    }

    public QuerySchema getSchema(String name)
    {
        QuerySchema ret = _schemas.get(name);
        if (ret != null)
            return ret;
        SchemaProvider provider = _providers.get(name);
        if (provider == null)
        {
            return QueryService.get().getDbUserSchemas(this).get(name);
        }
        return provider.getSchema(this);
    }

    public Set<String> getSchemaNames()
    {
        Set<String> ret = new TreeSet<String>(_providers.keySet());
        ret.addAll(QueryService.get().getDbUserSchemas(this).keySet());
        return Collections.unmodifiableSet(ret);
    }

    public Set<String> getUserSchemaNames()
    {
        Set<String> ret = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        for (String schemaName : getSchemaNames())
        {
            QuerySchema schema = getSchema(schemaName);
            if (!(schema instanceof UserSchema))
            {
                continue;
            }
            UserSchema userSchema = (UserSchema) schema;
            if (userSchema.getSchemaName() == null)
                continue;
            ret.add(schemaName);
        }
        return ret;
    }
}

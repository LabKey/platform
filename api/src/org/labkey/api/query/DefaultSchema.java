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

package org.labkey.api.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.collections.ConcurrentCaseInsensitiveSortedMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A schema, scoped to a particular container
 */
final public class DefaultSchema extends AbstractSchema
{
    static public abstract class SchemaProvider
    {
        abstract public @Nullable QuerySchema getSchema(DefaultSchema schema);
    }

    private static final Map<String, SchemaProvider> _providers = new ConcurrentCaseInsensitiveSortedMap<SchemaProvider>();

    static public void registerProvider(String name, SchemaProvider provider)
    {
        _providers.put(name, provider);
    }

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
    }

    public TableInfo getTable(String name)
    {
        return null;
    }

    public QuerySchema getSchema(String name)
    {
        SchemaProvider provider = _providers.get(name);

        if (provider == null && name != null && name.startsWith("/"))
        {
            Container project = ContainerManager.getForPath(name);
            if (project != null && project.hasPermission(getUser(), ReadPermission.class))
            {
                return new FolderSchemaProvider.FolderSchema(getUser(), project, DefaultSchema.get(getUser(), project));
            }
        }

        if (provider == null)
        {
            return QueryService.get().getExternalSchema(this, name);
        }

        return provider.getSchema(this);
    }

    public Set<String> getSchemaNames()
    {
        Set<String> ret = new TreeSet<String>(_providers.keySet());    // TODO: Return a set in case-insensitive order?
        ret.addAll(QueryService.get().getExternalSchemas(this).keySet());
        return Collections.unmodifiableSet(ret);
    }

    public Set<String> getUserSchemaNames()
    {
        Set<String> ret = new CaseInsensitiveTreeSet();

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

    public String getName()
    {
        return "default";
    }

    public String getDescription()
    {
        return null;
    }
}

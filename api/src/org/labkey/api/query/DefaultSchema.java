/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.collections.ConcurrentCaseInsensitiveSortedMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A schema, scoped to a particular container
 */
final public class DefaultSchema extends AbstractSchema
{
    static public abstract class SchemaProvider
    {
        abstract public @Nullable QuerySchema getSchema(DefaultSchema schema);
    }

    static public abstract class DynamicSchemaProvider
    {
        abstract public @Nullable QuerySchema getSchema(User user, Container container, String name);
        abstract public @NotNull Collection<String> getSchemaNames(User user, Container container);
    }

    private static final Map<String, SchemaProvider> _providers = new ConcurrentCaseInsensitiveSortedMap<SchemaProvider>();
    private static final List<DynamicSchemaProvider> _dynamicProviders = new CopyOnWriteArrayList<DynamicSchemaProvider>();

    static public void registerProvider(String name, SchemaProvider provider)
    {
        _providers.put(name, provider);
    }

    static public void registerProvider(DynamicSchemaProvider provider)
    {
        _dynamicProviders.add(provider);
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

    /**
     * Get QuerySchema for SchemaKey encoded schema path.
     *
     * @param user
     * @param container
     * @param schemaPath SchemaKey encoded schema path.
     * @return The QuerySchema resolved by the schema path.
     * @see QueryService#getUserSchema(org.labkey.api.security.User, org.labkey.api.data.Container, String)
     */
    static public QuerySchema get(User user, Container container, String schemaPath)
    {
        if (schemaPath == null || schemaPath.length() == 0)
            return null;

        return get(user, container, SchemaKey.fromString(schemaPath));
    }

    /**
     * Get QuerySchema for SchemaKey schema path.
     *
     * @param user
     * @param container
     * @param schemaPath SchemaKey schema path.
     * @return The QuerySchema resolved by the schema path.
     * @see QueryService#getUserSchema(org.labkey.api.security.User, org.labkey.api.data.Container, String)
     */
    static public QuerySchema get(User user, Container container, SchemaKey schemaPath)
    {
        if (schemaPath == null)
            return null;

        List<String> parts = schemaPath.getParts();
        if (parts.size() == 0)
            return null;

        QuerySchema schema = DefaultSchema.get(user, container);
        for (String part : parts)
        {
            schema = schema.getSchema(part);
            if (schema == null)
                return null;
        }

        return schema;
    }

    private DefaultSchema(User user, Container container)
    {
        super(CoreSchema.getInstance().getSchema(), user, container);
    }

    public TableInfo getTable(String name)
    {
        return null;
    }

    @Override
    public Set<String> getTableNames()
    {
        return Collections.emptySet();
    }

    public QuerySchema getSchema(String name)
    {
        SchemaProvider provider = _providers.get(name);
        if (provider != null)
            return provider.getSchema(this);

        if (name != null && name.startsWith("/"))
        {
            Container project = ContainerManager.getForPath(name);
            if (project != null && project.hasPermission(getUser(), ReadPermission.class))
            {
                return new FolderSchemaProvider.FolderSchema(getUser(), project, DefaultSchema.get(getUser(), project));
            }
        }

        for (DynamicSchemaProvider dynamicProvider : _dynamicProviders)
        {
            QuerySchema schema = dynamicProvider.getSchema(getUser(), getContainer(), name);
            if (schema != null)
                return schema;
        }

        return null;
    }

    public Set<String> getSchemaNames()
    {
        Set<String> ret = new TreeSet<String>(_providers.keySet());    // TODO: Return a set in case-insensitive order?
        for (DynamicSchemaProvider dynamicProvider : _dynamicProviders)
            ret.addAll(dynamicProvider.getSchemaNames(getUser(), getContainer()));
        return Collections.unmodifiableSet(ret);
    }

    /**
     * Get immediate UserSchema children names.
     * @return
     */
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
            if (userSchema.getName() == null || userSchema.isHidden())
                continue;
            ret.add(schemaName);
        }

        return ret;
    }

    /**
     * Get recursive set of all schema paths as SchemaKeys in case-insensitive string order.
     *
     * @return Set of all schema paths.
     */
    public Set<SchemaKey> getUserSchemaPaths()
    {
        SimpleSchemaTreeVisitor<Set<SchemaKey>, Void> visitor = new SimpleSchemaTreeVisitor<Set<SchemaKey>, Void>()
        {
            @Override
            public Set<SchemaKey> reduce(Set<SchemaKey> r1, Set<SchemaKey> r2)
            {
                if (r1 == null)
                    return r2;
                if (r2 == null)
                    return r1;

                Set<SchemaKey> names = new TreeSet<SchemaKey>(SchemaKey.CASE_INESNSITIVE_STRING_ORDER);
                names.addAll(r1);
                names.addAll(r2);
                return names;
            }

            @Override
            public Set<SchemaKey> visitUserSchema(UserSchema schema, Path path, Void param)
            {
                Set<SchemaKey> r = Collections.singleton(path.schemaPath);
                return visitAndReduce(schema.getSchemas(), path, param, r);
            }
        };

        return visitor.visitTop(getSchemas(), null);
    }

    public String getName()
    {
        return "default";
    }

    public String getDescription()
    {
        return null;
    }

    /** Returns a SchemaKey encoded name for this schema. */
    public String getSchemaName()
    {
        return getName();
    }

    @Override
    public <R, P> R accept(SchemaTreeVisitor<R, P> visitor, SchemaTreeVisitor.Path path, P param)
    {
        return visitor.visitDefaultSchema(this, path, param);
    }

}

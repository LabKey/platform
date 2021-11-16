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

package org.labkey.api.query;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.collections.ConcurrentCaseInsensitiveSortedMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.Module;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.MemTracker;
import org.labkey.api.visualization.VisualizationProvider;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A schema, scoped to a particular container and user
 *
 * For performance a DefaultSchema caches resolved UserSchema objects. The DefaultSchema itself should not
 * be cached.  It should only be held onto for a short time (e.g. request or query scope).
 */
final public class DefaultSchema extends AbstractSchema implements QuerySchema.ContainerSchema
{
    static public abstract class SchemaProvider
    {
        private final Module _module;

        public SchemaProvider(@NotNull Module module)
        {
            _module = module;
        }

        public final @NotNull Module getModule() { return _module; }

        public final @Nullable QuerySchema getSchema(DefaultSchema schema)
        {
            if (!isAvailable(schema, _module))
                return null;

            return createSchema(schema, _module);
        }

        /**
         * Returns true if the schema should be made available in the schema tree in this container.
         * The default implementation will only publish the schema if the module is active in the container.
         */
        public boolean isAvailable(DefaultSchema schema, Module module)
        {
            return module == null || schema.getContainer().getActiveModules().contains(module);
        }

        abstract public @Nullable QuerySchema createSchema(DefaultSchema schema, Module module);
    }

    static public abstract class DynamicSchemaProvider
    {
        abstract public @Nullable QuerySchema getSchema(User user, Container container, String name);
        abstract public @NotNull Collection<String> getSchemaNames(User user, Container container);
    }

    private static final ConcurrentNavigableMap<String, SchemaProvider> _providers = new ConcurrentCaseInsensitiveSortedMap<>();
    private static final List<DynamicSchemaProvider> _dynamicProviders = new CopyOnWriteArrayList<>();

    static public void registerProvider(String name, SchemaProvider provider)
    {
        if (null != _providers.putIfAbsent(name, provider))
            throw new IllegalStateException("Query schema \"" + name + "\" already has a registered SchemaProvider");
    }

    static public void registerProvider(DynamicSchemaProvider provider)
    {
        _dynamicProviders.add(provider);
    }

    static
    {
        registerProvider("Folder", new FolderSchemaProvider()
        {
            @Override
            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new FolderSchema("Folder", schema.getUser(), schema.getContainer(), null);
            }
        });
        registerProvider("Project", new FolderSchemaProvider()
        {
            @Override
            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                Container container = schema.getContainer().getProject();
                if (container == null)
                {
                    // No project available from the root container
                    return null;
                }
                return new FolderSchema("Project", schema.getUser(), container, DefaultSchema.get(schema.getUser(), container));
            }
        });
        registerProvider("Shared", new FolderSchemaProvider(){
            @Override
            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                Container container = ContainerManager.getSharedContainer();
                return new FolderSchema("Shared", schema.getUser(), container, DefaultSchema.get(schema.getUser(), container));
            }
        });
        registerProvider("Site", new FolderSchemaProvider(){
            @Override
            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                Container container = ContainerManager.getRoot();
                return new FolderSchema("Site", schema.getUser(), container, null);
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

    Map<SchemaKey,QuerySchema> cache = Collections.synchronizedMap(new HashMap<>());

    /**
     * Get QuerySchema for SchemaKey schema path.
     *
     * @param schemaPath SchemaKey schema path.
     * @return The QuerySchema resolved by the schema path.
     * @see QueryService#getUserSchema(org.labkey.api.security.User, org.labkey.api.data.Container, String)
     */
    static public QuerySchema get(User user, Container container, SchemaKey schemaPath)
    {
        if (schemaPath == null)
            return null;

        if (schemaPath.size() == 0)
            return null;

        DefaultSchema schema = DefaultSchema.get(user, container);
        return resolve(schema, schemaPath);
    }

    public static QuerySchema resolve(QuerySchema schema, SchemaKey schemaPath)
    {
        DefaultSchema ds = schema.getDefaultSchema();
        var cache = null==ds ? null : ds.cache;
        SchemaKey subPath = null;
        List<String> parts = schemaPath.getParts();
        for (String part : parts)
        {
            subPath = new SchemaKey(subPath, part);
            QuerySchema child = null==cache ? null : cache.get(subPath);
            if (null == child)
            {
                child = schema.getSchema(part);
                if (null == child)
                    return null;
                if (null != cache)
                    cache.put(subPath, child);
            }
            schema = child;
        }
        return schema;
    }

    private DefaultSchema(User user, Container container)
    {
        super(null, user, container);
        MemTracker.getInstance().put(this);
    }

    @Override
    public TableInfo getTable(String name, ContainerFilter cf)
    {
        return null;
    }

    @Override
    public Set<String> getTableNames()
    {
        return Collections.emptySet();
    }

    @Override
    public QuerySchema getSchema(@NotNull String name)
    {
        SchemaKey skey = new SchemaKey(null, name);
        QuerySchema ret = cache.get(skey);
        if (ret != null)
        {
            assert !(ret instanceof UserSchema.HasContextualRoles) || ((UserSchema.HasContextualRoles)ret).getContextualRoles().isEmpty();
            return ret;
        }
        ret = _getSchema(name);
        if (null != ret)
        {
            // If QuerySchema had getSchemaKey(), it would be nice to cache under a canonical name
            assert !(ret instanceof UserSchema.HasContextualRoles) || ((UserSchema.HasContextualRoles)ret).getContextualRoles().isEmpty();
            cache.put(skey,ret);
            if (ret.getContainer().equals(getContainer()))
            {
                ret.setDefaultSchema(this);
            }
        }
        return ret;
    }

    private QuerySchema _getSchema(@NotNull String name)
    {
        Objects.requireNonNull(name, "null schema name");

        SchemaProvider provider = _providers.get(name);
        if (provider != null)
        {
            QuerySchema result = provider.getSchema(this);
            // Not all providers will be enabled in the current folder. For example, some are based on whether
            // the module is enabled or not. If they don't resolve the schema, pass through to let dynamic providers
            // (like linked schemas) claim the same name. This is helpful for automated testing.
            if (result != null)
            {
                return result;
            }
        }

        if (name.startsWith("/"))
        {
            Container project = ContainerManager.getForPath(name);
            if (project != null && project.hasPermission(getUser(), ReadPermission.class))
            {
                return new FolderSchemaProvider.FolderSchema(name, getUser(), project, DefaultSchema.get(getUser(), project));
            }
        }

        // support external queries for nested schema (for example, ODBC driver uses "[assay.General.A Gpat. Assay With Dot.in Name].[Batches]" as schema.table name)
        if (name.contains("."))
        {
            QuerySchema resolvedSchema = null;
            String[] schemaParts = name.split("\\.");
            for (int i = 0; i < schemaParts.length; i++)
            {
                List<String> parts = Arrays.asList(schemaParts).subList(0, i);
                provider = _providers.get(StringUtils.join(parts, '.'));
                if (provider != null)
                {
                    resolvedSchema = provider.getSchema(this);
                    break;
                }
            }

            if (resolvedSchema != null && !resolvedSchema.getName().equalsIgnoreCase(name))
            {
                String[] remainingParts = name.substring(resolvedSchema.getName().length() + 1).split("\\.");
                for (int j = 0; j < remainingParts.length; j++)
                {
                    for (int k = j + 1; k <= remainingParts.length; k++)
                    {
                        List<String> subRemainingParts = Arrays.asList(remainingParts).subList(j, k);
                        QuerySchema subSchema = resolvedSchema.getSchema(StringUtils.join(subRemainingParts, '.'));
                        if (subSchema != null)
                        {
                            resolvedSchema = subSchema;
                            j = k - 1;
                            break;
                        }
                    }
                }
                return resolvedSchema;
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

    @Override
    public Set<String> getSchemaNames()
    {
        Set<String> ret = new TreeSet<>(_providers.keySet());    // TODO: Return a set in case-insensitive order?
        for (DynamicSchemaProvider dynamicProvider : _dynamicProviders)
            ret.addAll(dynamicProvider.getSchemaNames(getUser(), getContainer()));
        return Collections.unmodifiableSet(ret);
    }

    /**
     * Get immediate UserSchema children names.
     */
    public Set<String> getUserSchemaNames(boolean includeHidden)
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
            if (userSchema.isFolder())
                continue;
            if (!includeHidden && userSchema.isHidden())
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
    public Set<SchemaKey> getUserSchemaPaths(boolean includeHidden)
    {
        SimpleSchemaTreeVisitor<Set<SchemaKey>, Void> visitor = new SimpleSchemaTreeVisitor<>(includeHidden)
        {
            @Override
            public Set<SchemaKey> reduce(Set<SchemaKey> r1, Set<SchemaKey> r2)
            {
                if (r1 == null)
                    return r2;
                if (r2 == null)
                    return r1;

                Set<SchemaKey> names = new TreeSet<>(SchemaKey.CASE_INSENSITIVE_STRING_ORDER);
                names.addAll(r1);
                names.addAll(r2);
                return names;
            }

            @Override
            public Set<SchemaKey> visitUserSchema(UserSchema schema, Path path, Void param)
            {
                Set<SchemaKey> r = Collections.singleton(path.schemaPath);
                return visitAndReduce(schema.getSchemas(_includeHidden), path, param, r);
            }
        };

        return visitor.visitTop(getSchemas(includeHidden), null);
    }

    @Override
    public @NotNull String getName()
    {
        return "default";
    }

    @Override
    public String getDescription()
    {
        return null;
    }

    /** Returns a SchemaKey encoded name for this schema. */
    @Override
    @NotNull
    public String getSchemaName()
    {
        return getName();
    }

    @Override
    public <R, P> R accept(SchemaTreeVisitor<R, P> visitor, SchemaTreeVisitor.Path path, P param)
    {
        return visitor.visitDefaultSchema(this, path, param);
    }

    @Override
    public VisualizationProvider<?> createVisualizationProvider()
    {
        return null;
    }

    @Override
    public DefaultSchema getDefaultSchema()
    {
        return this;
    }
    static DefaultSchema getFor(QuerySchema qs)
    {
        return qs.getDefaultSchema();
    }
    static DefaultSchema getFor(TableInfo t)
    {
        UserSchema s = t.getUserSchema();
        if (null == s)
            throw new IllegalStateException();
        return getFor(s);
    }
}

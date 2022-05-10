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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.view.NavTree;
import org.labkey.api.visualization.VisualizationProvider;
import org.labkey.api.writer.ContainerUser;

import java.util.Collection;
import java.util.Set;

public interface QuerySchema extends SchemaTreeNode, ContainerUser
{
    @Override
    User getUser();

    @Override
    Container getContainer();

    void setDefaultSchema(DefaultSchema schema);

    DefaultSchema getDefaultSchema();

    DbSchema getDbSchema();

    /** getTable(name) is equivalent to getTable(name, null) */
    default TableInfo getTable(String name)
    {
        return getTable(name, null);
    }

    /** Consider using getTableWithFactory(String, ContainerFilter.Factory) instead */
    TableInfo getTable(String name, @Nullable ContainerFilter cf);

    @NotNull
    default TableInfo getTableOrThrow(String name)
    {
        TableInfo result = getTable(name);
        if (result == null)
        {
            throw new IllegalArgumentException("Could not find table '" + name + "' in schema '" + getSchemaName() + "'");
        }
        return result;
    }

    @NotNull
    default TableInfo getTableOrThrow(String name, @Nullable ContainerFilter cf)
    {
        TableInfo result = getTable(name, cf);
        if (result == null)
        {
            throw new IllegalArgumentException("Could not find table '" + name + "' in schema '" + getSchemaName() + "'");
        }
        return result;
    }

    /**
     * The schema already knows its container and user, we don't need to redundantly create a ContainerFilter with the
     * same info.
     *
     * NOTE: getTable(String,ContainerFilter) takes @Nullable ContainerFilter, therefore getTable(String,ContainerFilter.Factory)
     * would cause a lot of ambiguous code errors.
     *
     * @param name Name of requested table
     * @param factory ContainerFilter factory, null means use default (usually ContainerFilter.Type.Current)
     * @return TableInfo
     */
    default TableInfo getTableCFF(String name, ContainerFilter.Factory factory)
    {
        return getTable(name, null==factory ? getDefaultContainerFilter() : factory.create(this.getContainer(), this.getUser()));
    }

    default ContainerFilter getDefaultContainerFilter()
    {
        return ContainerFilter.current(getContainer());
    }

    Set<String> getTableNames();

    Collection<TableInfo> getTables();

    /** Could be null if, for example, provider hides schema when module is inactive. */
    @Nullable QuerySchema getSchema(String name);

    Set<String> getSchemaNames();

    Collection<QuerySchema> getSchemas(boolean includeHidden);

    /** @return the simple name for this schema, excluding any parent schema names */
    @Override
    @NotNull
    String getName();

    /** @return a SchemaKey encoded name for this schema. */
    @NotNull
    String getSchemaName();

    /** @return short description of the content and purpose of the schema */
    @Nullable String getDescription();

    NavTree getSchemaBrowserLinks(User user);

    boolean isHidden();

    /** @return the VisualizationProvider to be used for queries in this schema, or null if it's not supported */
    @Nullable
    VisualizationProvider createVisualizationProvider();

    // marker interface to indicate that this schema is bound to a container, does and does not itself have tables
    // e.g. DefaultSchema, FolderSchema, etc
    interface ContainerSchema extends QuerySchema
    {
    }
}

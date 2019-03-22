/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.view.NavTree;
import org.labkey.api.visualization.VisualizationProvider;

import java.util.Collection;
import java.util.Set;

public interface QuerySchema extends SchemaTreeNode
{
    User getUser();

    Container getContainer();

    DbSchema getDbSchema();

    TableInfo getTable(String name);

    Set<String> getTableNames();

    Collection<TableInfo> getTables();

    /** Could be null if, for example, provider hides schema when module is inactive. */
    @Nullable QuerySchema getSchema(String name);

    Set<String> getSchemaNames();

    Collection<QuerySchema> getSchemas(boolean includeHidden);

    /** @return the simple name for this schema, excluding any parent schema names */
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
}

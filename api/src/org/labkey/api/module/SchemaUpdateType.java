/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
package org.labkey.api.module;

import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SqlScriptRunner.SqlScript;
import org.labkey.api.data.SqlScriptRunner.SqlScriptException;
import org.labkey.api.data.SqlScriptRunner.SqlScriptProvider;

import java.util.Collection;
import java.util.LinkedList;

/**
* User: adam
* Date: 8/28/13
* Time: 4:19 PM
*/
enum SchemaUpdateType
{
    Before
    {
        @Nullable
        @Override
        SqlScript getScript(SqlScriptProvider provider, DbSchema schema) throws SqlScriptException
        {
            return provider.getDropScript(schema);
        }

        @NotNull
        @Override
        Collection<DbSchema> orderSchemas(Collection<DbSchema> schemas)
        {
            // Execute the drop scripts in reverse order, e.g., test-drop.sql before core-drop.sql
            return Lists.reverse(new LinkedList<>(schemas));
        }
    },

    After
    {
        @Nullable
        @Override
        SqlScript getScript(SqlScriptProvider provider, DbSchema schema) throws SqlScriptException
        {
            // Execute the drop scripts in order, e.g., core-drop.sql before test-drop.sql
            return provider.getCreateScript(schema);
        }

        @NotNull
        @Override
        Collection<DbSchema> orderSchemas(Collection<DbSchema> schemas)
        {
            return schemas;
        }
    };

    abstract @NotNull Collection<DbSchema> orderSchemas(Collection<DbSchema> schemas);
    abstract @Nullable SqlScript getScript(SqlScriptProvider provider, DbSchema schema) throws SqlScriptException;
}

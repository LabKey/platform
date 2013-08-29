/*
 * Copyright (c) 2013 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SqlScriptRunner;

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
        SqlScriptRunner.SqlScript getScript(SqlScriptRunner.SqlScriptProvider provider, DbSchema schema) throws SqlScriptRunner.SqlScriptException
        {
            return provider.getDropScript(schema);
        }
    },

    After
    {
        @Nullable
        @Override
        SqlScriptRunner.SqlScript getScript(SqlScriptRunner.SqlScriptProvider provider, DbSchema schema) throws SqlScriptRunner.SqlScriptException
        {
            return provider.getCreateScript(schema);
        }
    };

    abstract @Nullable SqlScriptRunner.SqlScript getScript(SqlScriptRunner.SqlScriptProvider provider, DbSchema schema) throws SqlScriptRunner.SqlScriptException;
}

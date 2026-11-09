/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.api.migration;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SchemaTableInfoFactory;
import org.labkey.api.module.Module;

import java.util.Map;

// A special DbSchema used for LabKey database migration. The scope is an external data source, but XML metadata is
// applied as if it were a normal module schema. This ensures mixed-case table names, virtual FKs, etc.
public class MigrationDbSchema extends DbSchema
{
    public MigrationDbSchema(String name, DbSchemaType type, DbScope scope, Map<String, SchemaTableInfoFactory> tableInfoFactoryMap, Module module)
    {
        super(name, type, scope, tableInfoFactoryMap, module);
    }

    @Override
    public String getResourcePrefix()
    {
        return getName();
    }
}

/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.core;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SchemaTableInfoFactory;
import org.labkey.api.module.ModuleLoader;

import java.util.Map;

// Special subclass to handle the peculiarities of the "labkey" schema that gets created in all module-required
// external data sources.
public class LabKeyDbSchema extends DbSchema
{
    public LabKeyDbSchema(DbScope scope, Map<String, SchemaTableInfoFactory> tableInfoFactoryMap)
    {
        super("labkey", DbSchemaType.Module, scope, tableInfoFactoryMap, ModuleLoader.getInstance().getCoreModule());
    }

    // Used to retrieve schema XML file and scripts. Override so this is not datasource-qualified (we want to always resolve to labkey.xml, labkey-0.00-14.20.sql, etc.)
    public String getResourcePrefix()
    {
        return "labkey";
    }

    @Override
    public String toString()
    {
        return "LabKeyDbSchema in \"" + getScope().getDisplayName() + "\"";
    }
}

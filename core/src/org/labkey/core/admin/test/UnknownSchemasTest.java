/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.core.admin.test;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.test.TestWhen;

import java.util.Set;

/**
 * Detect unknown schemas, which might be module schemas that aren't properly claimed. Issue #51040.
 */
@TestWhen(TestWhen.When.SMOKE)
public class UnknownSchemasTest extends Assert
{
    @Test
    public void testUnknownSchemas()
    {
        DbScope labkeyScope = DbScope.getLabKeyScope();
        SqlDialect dialect = labkeyScope.getSqlDialect();

        // Collect all schemas in the LabKey database
        Set<String> allSchemas = new CaseInsensitiveHashSet(labkeyScope.getSchemaNames()) {
            @Override
            public boolean remove(Object o)
            {
                // Extract simple schema name from any fully-qualified key (e.g., labware.GW_LABKEY -> GW_LABKEY)
                String schemaName = (String) o;
                int idx = schemaName.lastIndexOf('.');
                if (idx != -1)
                    schemaName = schemaName.substring(idx + 1);

                return super.remove(schemaName);
            }
        };

        // Remove schemas claimed by existing modules
        ModuleLoader.getInstance().getModules().stream()
            .flatMap(module -> module.getSchemaNames().stream())
            .forEach(allSchemas::remove);

        // Remove schemas claimed by "unknown" modules... these are expected on development deployments and dealt with
        // via admin warnings on production deployments
        ModuleLoader.getInstance().getUnknownModuleContexts().values().stream()
            .flatMap(context -> context.getSchemaList().stream())
            .forEach(allSchemas::remove);

        // Remove the database's built-in schemas (public, information_schema, pg_catalog, dbo, etc.)
        allSchemas.removeIf(dialect::isSystemSchema);

        // Remove special schema added if database is treated as an external data source
        allSchemas.remove("labkey");

        // Anything left might be a module schema that hasn't been declared
        assertTrue("Unknown schemas: " + allSchemas, allSchemas.isEmpty());
    }
}

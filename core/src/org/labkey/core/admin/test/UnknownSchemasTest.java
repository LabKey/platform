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

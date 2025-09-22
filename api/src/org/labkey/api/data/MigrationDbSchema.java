package org.labkey.api.data;

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

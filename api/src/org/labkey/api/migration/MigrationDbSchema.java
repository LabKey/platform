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

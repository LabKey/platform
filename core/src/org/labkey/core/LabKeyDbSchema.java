package org.labkey.core;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.resource.Resource;

import java.io.IOException;
import java.util.Map;

// Special subclass to handle the peculiarities of the "labkey" schema that gets created in all module-required
// external data sources. Key changes:
// 1. Override getDisplayName() to eliminate the standard datasource prefix, so labkey-*-*.sql scripts are found
// 2. Override getSchemaResource() to resolve labkey.xml
public class LabKeyDbSchema extends DbSchema
{
    public LabKeyDbSchema(DbScope scope, Map<String, String> metaDataTableNames)
    {
        super("labkey", DbSchemaType.Module, scope, metaDataTableNames);
    }

    @Override
    public String getDisplayName()
    {
        return "labkey";
    }

    @Override
    public Resource getSchemaResource(String schemaName) throws IOException
    {
        // CoreModule does not claim the "labkey" schema because we don't want to install this schema in the labkey
        // datasource. Override here so we find labkey.xml; this eliminates warnings and supports junit tests.
        return getSchemaResource(ModuleLoader.getInstance().getCoreModule(), schemaName);
    }

    @Override
    public String toString()
    {
        return "LabKeyDbSchema in \"" + getScope().getDisplayName() + "\"";
    }
}

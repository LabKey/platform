package org.labkey.api.assay;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;

public class AssaySchema
{
    private static final AssaySchema INSTANCE = new AssaySchema();
    private static final String SCHEMA_NAME = "study";

    public static AssaySchema getInstance()
    {
        return INSTANCE;
    }

    private AssaySchema()
    {
    }

    public String getSchemaName()
    {
        return SCHEMA_NAME;
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME, DbSchemaType.Module);
    }

    public DbScope getScope()
    {
        return getSchema().getScope();
    }

    public TableInfo getTableInfoPlate()
    {
        return getSchema().getTable("Plate");
    }

    public TableInfo getTableInfoWellGroup()
    {
        return getSchema().getTable("WellGroup");
    }

    public TableInfo getTableInfoWell()
    {
        return getSchema().getTable("Well");
    }
}

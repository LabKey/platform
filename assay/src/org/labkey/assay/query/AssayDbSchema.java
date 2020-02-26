package org.labkey.assay.query;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;

public class AssayDbSchema
{
    private static final AssayDbSchema INSTANCE = new AssayDbSchema();
    private static final String SCHEMA_NAME = "assay";

    public static AssayDbSchema getInstance()
    {
        return INSTANCE;
    }

    private AssayDbSchema()
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

    public TableInfo getTableInfoWellGroupPositions()
    {
        return getSchema().getTable("WellGroupPositions");
    }

}

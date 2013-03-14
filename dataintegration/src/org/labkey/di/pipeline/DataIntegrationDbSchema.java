package org.labkey.di.pipeline;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;

/**
 * User: jeckels
 * Date: 3/13/13
 */
public class DataIntegrationDbSchema
{
    public static final String SCHEMA_NAME = "dataintegration";

    public static DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME);
    }

    public static TableInfo getTransformRunTableInfo()
    {
        return getSchema().getTable("transformrun");
    }

    public static TableInfo getTranformConfigurationTableInfo()
    {
        return getSchema().getTable("transformconfiguration");
    }
}

package org.labkey.api.data;

import org.labkey.api.data.dialect.SqlDialect;

/**
* User: adam
* Date: 10/8/13
* Time: 10:57 PM
*/
public class PropertySchema
{
    private static final PropertySchema _instance = new PropertySchema();
    private static final String SCHEMA_NAME = "prop";

    public static PropertySchema getInstance()
    {
        return _instance;
    }

    PropertySchema()
    {
    }

    public String getSchemaName()
    {
        return SCHEMA_NAME;
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME);
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    public TableInfo getTableInfoProperties()
    {
        return getSchema().getTable("Properties");
    }

    public TableInfo getTableInfoPropertyEntries()
    {
        return getSchema().getTable("PropertyEntries");
    }

    public TableInfo getTableInfoPropertySets()
    {
        return getSchema().getTable("PropertySets");
    }
}

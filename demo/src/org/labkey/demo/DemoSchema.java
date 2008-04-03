package org.labkey.demo;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SqlDialect;
import org.labkey.api.data.TableInfo;

/**
 * User: brittp
 * Date: Jan 23, 2006
 * Time: 1:20:25 PM
 */
public class DemoSchema
{
    private static DemoSchema _instance = null;
    private static final String SCHEMA_NAME = "demo";

    public static DemoSchema getInstance()
    {
        if (null == _instance)
            _instance = new DemoSchema();

        return _instance;
    }

    private DemoSchema()
    {
        // private contructor to prevent instantiation from
        // outside this class: this singleton should only be 
        // accessed via cpas.demo.DemoSchema.getInstance()
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

    public TableInfo getTableInfoPerson()
    {
        return getSchema().getTable("Person");
    }
}

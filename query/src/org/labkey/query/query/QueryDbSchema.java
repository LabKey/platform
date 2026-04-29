package org.labkey.query.query;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.TableInfo;

public class QueryDbSchema
{
    private static final QueryDbSchema instance = new QueryDbSchema();
    private static final String SCHEMA_NAME = "query";

    public static QueryDbSchema getInstance()
    {
        return instance;
    }

    private QueryDbSchema()
    {
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME, DbSchemaType.Module);
    }

    public String getSchemaName()
    {
        return SCHEMA_NAME;
    }

    public TableInfo getTableInfoCustomView()
    {
        return getSchema().getTable("CustomView");
    }
}

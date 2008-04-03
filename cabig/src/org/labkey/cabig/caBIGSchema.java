package org.labkey.cabig;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SqlDialect;
import org.labkey.api.data.TableInfo;

public class caBIGSchema
{
    private static caBIGSchema _instance = null;

    public static caBIGSchema getInstance()
    {
        if (null == _instance)
            _instance = new caBIGSchema();

        return _instance;
    }

    private caBIGSchema()
    {
        // private contructor to prevent instantiation from
        // outside this class: this singleton should only be
        // accessed via cpas.cabig.caBIGSchema.getInstance()
    }

    public DbSchema getSchema()
    {
        return DbSchema.get("cabig");
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    public TableInfo getTableInfoContainers()
    {
        return getSchema().getTable("Containers");
    }
}

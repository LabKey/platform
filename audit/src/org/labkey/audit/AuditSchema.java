package org.labkey.audit;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SqlDialect;

public class AuditSchema
{
    private static AuditSchema _instance = null;

    public static AuditSchema getInstance()
    {
        if (null == _instance)
            _instance = new AuditSchema();

        return _instance;
    }

    private AuditSchema()
    {
        // private contructor to prevent instantiation from
        // outside this class: this singleton should only be
        // accessed via cpas.audit.AuditSchema.getInstance()
    }

    public DbSchema getSchema()
    {
        return DbSchema.get("audit");
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }
}

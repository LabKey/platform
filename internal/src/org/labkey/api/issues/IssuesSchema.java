package org.labkey.api.issues;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SqlDialect;
import org.labkey.api.data.TableInfo;

/**
 * User: Tamra Myers
 * Date: Sep 29, 2006
 * Time: 12:44:32 PM
 */
public class IssuesSchema
{
    private static IssuesSchema instance = null;
    private static final String SCHEMA_NAME = "issues";

    public static IssuesSchema getInstance()
    {
        if (null == instance)
            instance = new IssuesSchema();

        return instance;
    }

    private IssuesSchema()
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

    public TableInfo getTableInfoComments()
    {
        return getSchema().getTable("Comments");
    }

    public TableInfo getTableInfoEmailPrefs()
    {
        return getSchema().getTable("EmailPrefs");
    }

    public TableInfo getTableInfoIssueKeywords()
    {
        return getSchema().getTable("IssueKeywords");
    }

    public TableInfo getTableInfoIssues()
    {
        return getSchema().getTable("Issues");
    }

}

package org.labkey.api.data;

import java.sql.SQLException;

/**
 * User: adam
 * Date: 10/26/11
 * Time: 12:05 AM
 */
public class LegacySqlExecutor
{
    protected final SqlExecutor _executor;

    public LegacySqlExecutor(DbSchema schema, SQLFragment sql)
    {
        _executor = new SqlExecutor(schema, sql);
        _executor.setExceptionFramework(ExceptionFramework.JDBC);
    }

    public int execute() throws SQLException
    {
        try
        {
            return _executor.execute();
        }
        catch (RuntimeSQLException e)
        {
            throw e.getSQLException();
        }
    }
}
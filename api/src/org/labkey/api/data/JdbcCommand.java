package org.labkey.api.data;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * User: adam
 * Date: 10/25/11
 * Time: 11:35 PM
 */
public abstract class JdbcCommand
{
    private final DbScope _scope;
    private ExceptionFramework _exceptionFramework = ExceptionFramework.Spring;

    abstract SQLFragment getSql();

    protected JdbcCommand(DbScope scope)
    {
        _scope = scope;
    }

    public Connection getConnection() throws SQLException
    {
        return _scope.getConnection();
    }

    public DbScope getScope()
    {
        return _scope;
    }

    public void setExceptionFramework(ExceptionFramework exceptionFramework)
    {
        _exceptionFramework = exceptionFramework;
    }

    public ExceptionFramework getExceptionFramework()
    {
        return _exceptionFramework;
    }
}

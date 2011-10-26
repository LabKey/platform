package org.labkey.api.data;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * User: adam
 * Date: 10/25/11
 * Time: 11:27 PM
 */
public class SqlExecutor extends JdbcCommand
{
    private final SQLFragment _sql;

    // Execute select SQL against a scope
    public SqlExecutor(DbScope scope, SQLFragment sql)
    {
        super(scope);
        _sql = sql;
    }

    // Execute select SQL against a scope
    public SqlExecutor(DbScope scope, String sql)
    {
        this(scope, new SQLFragment(sql));
    }

    // Execute select SQL against a schema
    public SqlExecutor(DbSchema schema, SQLFragment sql)
    {
        this(schema.getScope(), sql);
    }

    public SQLFragment getSql()
    {
        return _sql;
    }

    public int execute()
    {
        Connection conn = null;

        try
        {
            conn = getConnection();
            return Table.execute(conn, _sql.getSQL(), _sql.getParamsArray());
        }
        catch(SQLException e)
        {
            Table.doCatch( _sql.getSQL(), _sql.getParamsArray(), conn, e);
            throw getExceptionFramework().translate(getScope(), "Message", _sql.getSQL(), e);  // TODO: Change message
        }
        finally
        {
            Table.doFinally(null, null, conn, getScope());
        }
    }
}

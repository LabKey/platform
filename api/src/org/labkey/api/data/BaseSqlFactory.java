package org.labkey.api.data;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
* User: adam
* Date: 1/22/12
* Time: 3:51 PM
*/
public abstract class BaseSqlFactory implements SqlFactory
{
    private final BaseSelector _selector;

    BaseSqlFactory(BaseSelector selector)
    {
        _selector = selector;
    }

    @Override
    public <K> K handleResultSet(BaseSelector.ResultSetHandler<K> handler)
    {
        Connection conn = null;
        ResultSet rs = null;
        SQLFragment sql = getSql();

        try
        {
            conn = _selector.getConnection();
            rs = Table._executeQuery(conn, sql.getSQL(), sql.getParamsArray());
            processResultSet(rs);

            return handler.handle(rs);
        }
        catch(SQLException e)
        {
            // TODO: Substitute SQL parameter placeholders with values?
            Table.doCatch(sql.getSQL(), sql.getParamsArray(), conn, e);
            throw _selector.getExceptionFramework().translate(_selector.getScope(), "Message", sql.getSQL(), e);  // TODO: Change message
        }
        finally
        {
            Table.doFinally(rs, null, conn, _selector.getScope());
        }
    }

    protected void processResultSet(ResultSet rs) throws SQLException
    {
    }
}

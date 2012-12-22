package org.labkey.api.data;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User: adam
 * Date: 12/10/12
 * Time: 10:43 PM
 */
public interface ResultSetFactory
{
    ResultSet getResultSet(Connection conn) throws SQLException;
    boolean shouldClose();
    void handleSqlException(SQLException e, Connection conn);
}

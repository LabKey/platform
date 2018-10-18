package org.labkey.api.data.dialect;

import java.sql.Connection;

public interface ConnectionHandler
{
    Connection getConnection();
    void releaseConnection(Connection conn);
}

package org.labkey.api.data;

import org.apache.commons.dbcp.ConnectionFactory;
import org.apache.commons.dbcp.PoolableConnectionFactory;
import org.apache.commons.dbcp.PoolingDataSource;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class ConnectionPool implements AutoCloseable
{
    private final DataSource _dataSource;
    private final @NotNull GenericObjectPool _connectionPool;

    public ConnectionPool(DbScope scope, int maxActive, String validationQuery)
    {
        // Create an ObjectPool that serves as the pool of connections.
        _connectionPool = new GenericObjectPool();
        _connectionPool.setMaxActive(maxActive);
        _connectionPool.setTestOnBorrow(true);

        // Create a ConnectionFactory that the pool will use to create Connections.
        ConnectionFactory connectionFactory = scope::getUnpooledConnection;

        // Create a PoolableConnectionFactory that wraps the "real" Connections created by the
        // ConnectionFactory with the classes that implement the pooling functionality.
        new PoolableConnectionFactory(connectionFactory,
            _connectionPool,
            null,
                validationQuery,  // validationQuery
            true, // defaultReadOnly
            true) // defaultAutoCommit
        {
            @Override
            public boolean validateObject(Object obj)
            {
                return ConnectionPool.this.validateConnection((Connection)obj) && super.validateObject(obj);
            }
        };

        // Create the PoolingDataSource
        _dataSource = new PoolingDataSource(_connectionPool);
    }

    public Connection getConnection() throws SQLException
    {
        return _dataSource.getConnection();
    }

    protected boolean validateConnection(Connection conn)
    {
        return true;
    }

    public void close()
    {
        _connectionPool.clear();
        try
        {
            _connectionPool.close();
        }
        catch(Exception ignored)
        {
        }
    }
}

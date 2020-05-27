/*
 * Copyright (c) 2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    @Override
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

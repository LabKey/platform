/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
package org.labkey.query.jdbc;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.security.PrincipalType;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * User: matthewb
 * Date: 4/25/12
 * Time: 6:17 PM
 */
public class QueryConnection implements Connection
{
    final User _user;
    final String _containerId;
    final String _schemaName;

    boolean _closed =false;
    boolean _autoCommit = true;
    boolean _readOnly = true;


    QueryConnection(User user, Container c, String defaultSchemaName)
    {
        //Currently can only be used by internal service users
        assert user.getPrincipalType() == PrincipalType.SERVICE;
        _user = user;
        _containerId = c.getId();
        _schemaName = defaultSchemaName;
    }

    QuerySchema getQuerySchema()
    {
        return DefaultSchema.get(getUser(), getContainer()).getSchema(getSchemaName());
    }

    User getUser()
    {
        return _user;
    }

    Container getContainer()
    {
        return ContainerManager.getForId(_containerId);
    }

    String getSchemaName()
    {
        return _schemaName;
    }

    @Override
    public Statement createStatement() throws SQLException
    {
        return new QueryStatement(this);
    }

    @Override
    public PreparedStatement prepareStatement(String s) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public CallableStatement prepareCall(String s) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String nativeSQL(String s) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAutoCommit(boolean b) throws SQLException
    {
        _autoCommit = b;
    }

    @Override
    public boolean getAutoCommit() throws SQLException
    {
        return _autoCommit;
    }

    @Override
    public void commit() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void rollback() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws SQLException
    {
        if (_closed)
            return;
        _closed = true;
    }

    @Override
    public boolean isClosed() throws SQLException
    {
        return _closed;
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException
    {
        return new QueryDatabaseMetaData(this);
    }

    @Override
    public void setReadOnly(boolean b) throws SQLException
    {
        _readOnly = b;
    }

    @Override
    public boolean isReadOnly() throws SQLException
    {
        return _readOnly;
    }

    @Override
    public void setCatalog(String s) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getCatalog() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTransactionIsolation(int i) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getTransactionIsolation() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException
    {
        // TODO return _sqlWarning
        return null;
    }

    @Override
    public void clearWarnings() throws SQLException
    {
        //TODO _sqlWarning = null;
    }

    @Override
    public Statement createStatement(int i, int i1) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public PreparedStatement prepareStatement(String s, int i, int i1) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public CallableStatement prepareCall(String s, int i, int i1) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> stringClassMap) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setHoldability(int i) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getHoldability() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Savepoint setSavepoint() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Savepoint setSavepoint(String s) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Statement createStatement(int i, int i1, int i2) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public PreparedStatement prepareStatement(String s, int i, int i1, int i2) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public CallableStatement prepareCall(String s, int i, int i1, int i2) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public PreparedStatement prepareStatement(String s, int i) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public PreparedStatement prepareStatement(String s, int[] ints) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public PreparedStatement prepareStatement(String s, String[] strings) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Clob createClob() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Blob createBlob() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public NClob createNClob() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isValid(int i) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setClientInfo(String s, String s1) throws SQLClientInfoException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getClientInfo(String s) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Properties getClientInfo() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Array createArrayOf(String s, Object[] objects) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Struct createStruct(String s, Object[] objects) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T unwrap(Class<T> tClass) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isWrapperFor(Class<?> aClass) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void setSchema(String schema) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public String getSchema() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void abort(Executor executor) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public int getNetworkTimeout() throws SQLException
    {
        throw new UnsupportedOperationException();
    }
}

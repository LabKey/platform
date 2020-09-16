/*
 * Copyright (c) 2012-2018 LabKey Corporation
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

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
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
    public Statement createStatement()
    {
        return new QueryStatement(this);
    }

    @Override
    public PreparedStatement prepareStatement(String s)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public CallableStatement prepareCall(String s)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String nativeSQL(String s)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAutoCommit(boolean b)
    {
        _autoCommit = b;
    }

    @Override
    public boolean getAutoCommit()
    {
        return _autoCommit;
    }

    @Override
    public void commit()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void rollback()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close()
    {
        if (_closed)
            return;
        _closed = true;
    }

    @Override
    public boolean isClosed()
    {
        return _closed;
    }

    @Override
    public DatabaseMetaData getMetaData()
    {
        return new QueryDatabaseMetaData(this);
    }

    @Override
    public void setReadOnly(boolean b)
    {
        _readOnly = b;
    }

    @Override
    public boolean isReadOnly()
    {
        return _readOnly;
    }

    @Override
    public void setCatalog(String s)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getCatalog()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTransactionIsolation(int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getTransactionIsolation()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public SQLWarning getWarnings()
    {
        // TODO return _sqlWarning
        return null;
    }

    @Override
    public void clearWarnings()
    {
        //TODO _sqlWarning = null;
    }

    @Override
    public Statement createStatement(int i, int i1)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public PreparedStatement prepareStatement(String s, int i, int i1)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public CallableStatement prepareCall(String s, int i, int i1)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, Class<?>> getTypeMap()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> stringClassMap)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setHoldability(int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getHoldability()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Savepoint setSavepoint()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Savepoint setSavepoint(String s)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void rollback(Savepoint savepoint)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Statement createStatement(int i, int i1, int i2)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public PreparedStatement prepareStatement(String s, int i, int i1, int i2)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public CallableStatement prepareCall(String s, int i, int i1, int i2)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public PreparedStatement prepareStatement(String s, int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public PreparedStatement prepareStatement(String s, int[] ints)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public PreparedStatement prepareStatement(String s, String[] strings)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Clob createClob()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Blob createBlob()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public NClob createNClob()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public SQLXML createSQLXML()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isValid(int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setClientInfo(String s, String s1)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setClientInfo(Properties properties)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getClientInfo(String s)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Properties getClientInfo()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Array createArrayOf(String s, Object[] objects)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Struct createStruct(String s, Object[] objects)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T unwrap(Class<T> tClass)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isWrapperFor(Class<?> aClass)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setSchema(String schema)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getSchema()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void abort(Executor executor)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getNetworkTimeout()
    {
        throw new UnsupportedOperationException();
    }
}

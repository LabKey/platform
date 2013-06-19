/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

import org.apache.commons.beanutils.ConvertUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.JdbcType;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * User: matthewb
 * Date: 4/25/12
 * Time: 6:12 PM
 */
public class QueryDriver implements Driver
{
    /* wrap derby so we can log database commands */
    static QueryDriver _instanceLabKeyMemoryDriver = new QueryDriver();

    static AtomicInteger _spid = new AtomicInteger();

    private QueryDriver()
    {
        try
        {
            DriverManager.registerDriver(this);
        }
        catch (RuntimeException x)
        {
            throw x;
        }
        catch (Exception x)
        {
            throw new RuntimeException("Error initializing Derby database", x);
        }
    }


    @Override
    public Connection connect(String s, Properties properties) throws SQLException
    {
        if (!s.startsWith("jdbc:labkey:query:"))
            return null;

        String[] a = s.split(":");
        for (String part : a)
        {
            int equals = part.indexOf("=");
            if (-1==equals) continue;
            properties.put(part.substring(0,equals),part.substring(equals+1));
        }
        Integer userId = (Integer)JdbcType.INTEGER.convert(properties.get("user"));
        Integer containerId = (Integer)JdbcType.INTEGER.convert(properties.get("container"));
        String schemaName = (String)properties.get("schema");
        if (null == userId || null==containerId || null==schemaName)
            throw new SQLException("connection must specify user, container, and schema");

        User user = UserManager.getUser(userId);
        Container c = ContainerManager.getForRowId(containerId);
        return new QueryConnection(user, c, schemaName);
    }


    @Override
    public boolean acceptsURL(String s) throws SQLException
    {
        if (!s.startsWith("jdbc:labkey:query:"))
            return false;
        return true;
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String s, Properties properties) throws SQLException
    {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion()
    {
        return 0;
    }

    @Override
    public int getMinorVersion()
    {
        return 1;
    }

    @Override
    public boolean jdbcCompliant()
    {
        return true;
    }

    public Logger getParentLogger() throws SQLFeatureNotSupportedException
    {
        throw new SQLFeatureNotSupportedException("Method not implemented");
    }
}

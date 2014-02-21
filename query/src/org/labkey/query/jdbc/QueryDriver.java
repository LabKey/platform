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

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.JdbcType;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.PrincipalType;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.RoleManager;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Collections;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * User: matthewb
 * Date: 4/25/12
 * Time: 6:12 PM
 */
public class QueryDriver implements Driver
{
    static QueryDriver _instanceLabKeyMemoryDriver = new QueryDriver();

    public static void register()
    {
        // this doesn't do anything but, calling this will make sure the static initializer gets called
    }

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
            throw new RuntimeException("Error initializing LabKey database driver", x);
        }
    }


    @Override
    public Connection connect(String connectString, Properties properties) throws SQLException
    {
        if (!acceptsURL(connectString))
            return null;

        String[] a = connectString.split(":");
        for (String part : a)
        {
            int equals = part.indexOf("=");
            if (-1==equals) continue;
            properties.put(part.substring(0,equals),part.substring(equals+1));
        }

        if (null==properties.get("container") || null==properties.get("schema"))
            throw new SQLException("connection must specify container and schema");

        // NOTE: This code is only accessible from within LabKey Server when using components that require their own
        //JDBC Connection (e.g. Mondrian). The user must currently be a limited service user that cannot update
        User user = new LimitedUser(User.guest, new int[0], Collections.singleton(RoleManager.getRole(ReaderRole.class)), false);
        user.setPrincipalType(PrincipalType.SERVICE);
        user.setDisplayName("Internal JDBC Service User");
        user.setEmail("internaljdbc@labkey.org");

        // CONTAINER
        Integer containerId = (Integer)JdbcType.INTEGER.convert(properties.get("container"));

        // SCHEMA
        String schemaName = null == properties.get("schema") ? null : String.valueOf(properties.get("schema"));
        if (StringUtils.isEmpty(schemaName))
            schemaName = "core";

        if (null==containerId || null==schemaName)
            throw new SQLException("connection must specify container and schema");

        Container c = ContainerManager.getForRowId(containerId);
        return new QueryConnection(user, c, schemaName);
    }


    @Override
    public boolean acceptsURL(String s) throws SQLException
    {
        return s.startsWith("jdbc:labkey:query:");
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

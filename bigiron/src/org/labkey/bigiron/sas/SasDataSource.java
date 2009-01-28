/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.bigiron.sas;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.DriverManager;
import java.io.PrintWriter;
import java.util.Properties;

/**
 * User: adam
 * Date: Jan 21, 2009
 * Time: 10:14:56 AM
 */
public class SasDataSource implements DataSource
{
    private String _url;

    public SasDataSource(String url) throws ClassNotFoundException
    {
        _url = url;
    }

    public void addLibRef(String name, String path)
    {

    }

    public String getDriverClassName()
    {
        return "com.sas.net.sharenet.ShareNetDriver";
    }

    public String getUrl()
    {
        return _url;
    }

    public Connection getConnection() throws SQLException
    {
        Properties properties = new Properties();
        properties.put("librefs", "saved 'C:\\Documents and Settings\\adam\\My Documents\\sas'");

        //shoes 'C:\\Program Files\\SAS\\SAS 9.1\\reporter\\metadata\\tailor made shoes company'"

        return DriverManager.getConnection(_url, properties);
    }

    public Connection getConnection(String username, String password) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public PrintWriter getLogWriter() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void setLogWriter(PrintWriter out) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void setLoginTimeout(int seconds) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public int getLoginTimeout() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public <T> T unwrap(Class<T> iface) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException
    {
        throw new UnsupportedOperationException();
    }
}

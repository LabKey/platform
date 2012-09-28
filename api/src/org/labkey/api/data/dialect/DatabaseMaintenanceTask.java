/*
 * Copyright (c) 2010-2012 LabKey Corporation
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

package org.labkey.api.data.dialect;

import org.apache.log4j.Logger;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Table;
import org.labkey.api.util.SystemMaintenance;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

class DatabaseMaintenanceTask implements SystemMaintenance.MaintenanceTask
{
    private static final Logger _log = Logger.getLogger(DatabaseMaintenanceTask.class);

    public String getDescription()
    {
        return "Database maintenance";
    }

    @Override
    public String getName()
    {
        return "Database";
    }

    @Override
    public boolean canDisable()
    {
        return true;
    }

    public void run()
    {
        DbScope scope = DbScope.getLabkeyScope();

        Connection conn = null;
        String sql = scope.getSqlDialect().getDatabaseMaintenanceSql();
        DataSource ds = scope.getDataSource();

        String url = null;

        try
        {
            SqlDialect.DataSourceProperties props = new SqlDialect.DataSourceProperties(scope.getDataSourceName(), ds);
            url = props.getUrl();
            _log.info("Database maintenance on " + url + " started");
        }
        catch (Exception e)
        {
            // Shouldn't happen, but we can survive without the url
            _log.error("Exception retrieving url", e);
        }

        try
        {
            if (null != sql)
            {
                conn = scope.getConnection();
                Table.execute(conn, sql);
            }
        }
        catch(SQLException e)
        {
            // Nothing to do here... table layer will log any errors
        }
        finally
        {
            if (null != conn) scope.releaseConnection(conn);
        }

        if (null != url)
            _log.info("Database maintenance on " + url + " complete");
    }
}

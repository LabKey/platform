/*
 * Copyright (c) 2010-2016 LabKey Corporation
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
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;
import org.springframework.jdbc.BadSqlGrammarException;

class DatabaseMaintenanceTask implements MaintenanceTask
{
    public String getDescription()
    {
        return "Database maintenance";
    }

    @Override
    public String getName()
    {
        return "Database";
    }

    public void run(Logger log)
    {
        DbScope scope = DbScope.getLabKeyScope();
        String url = null;

        try
        {
            SqlDialect.DataSourceProperties props = new SqlDialect.DataSourceProperties(scope.getDataSourceName(), scope.getDataSource());
            url = props.getUrl();
            log.info("Database maintenance on " + url + " started");
        }
        catch (Exception e)
        {
            // Shouldn't happen, but we can survive without the url
            log.error("Exception retrieving url", e);
        }

        String sql = scope.getSqlDialect().getDatabaseMaintenanceSql();
        if (null != sql)
        {
            try
            {
                new SqlExecutor(scope).execute(sql);
            }
            catch (BadSqlGrammarException e)
            {
                throw new ConfigurationException(e.getMessage());
            }
        }

        if (null != url)
            log.info("Database maintenance on " + url + " complete");
    }
}

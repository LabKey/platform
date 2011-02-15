/*
 * Copyright (c) 2010-2011 LabKey Corporation
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

package org.labkey.bigiron.mysql;

import org.apache.log4j.Logger;
import org.labkey.api.data.dialect.DatabaseNotSupportedException;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.SqlDialectFactory;
import org.labkey.api.util.VersionNumber;

import java.util.Collection;
import java.util.Collections;

/*
* User: adam
* Date: Nov 26, 2010
* Time: 10:11:46 PM
*/
public class MySqlDialectFactory extends SqlDialectFactory
{
    private static final Logger _log = Logger.getLogger(MySqlDialectFactory.class);

    private String getProductName()
    {
        return "MySQL";
    }

    @Override
    public boolean claimsDriverClassName(String driverClassName)
    {
        return false;    // Only used to create a new database, which we never do on MySQL
    }

    @Override
    public boolean claimsProductNameAndVersion(String dataBaseProductName, VersionNumber databaseProductVersion, String jdbcDriverVersion, boolean logWarnings) throws DatabaseNotSupportedException
    {
        if (!getProductName().equals(dataBaseProductName))
            return false;

        int version = databaseProductVersion.getVersionInt();

        // Version 5.1 or greater is allowed...
        if (version >= 51)
        {
            // ...but warn for anything greater than 5.5
            if (logWarnings && version > 55)
                _log.warn("LabKey Server has not been tested against " + getProductName() + " version " + databaseProductVersion + ".  " +  getProductName() + " 5.5 is the recommended version.");

            return true;
        }

        throw new DatabaseNotSupportedException(getProductName() + " version " + databaseProductVersion + " is not supported.  You must upgrade your database server installation to " + getProductName() + " version 5.1 or greater.");
    }

    @Override
    public SqlDialect create()
    {
        return new MySqlDialect();
    }

    @Override
    public Collection<? extends Class> getJUnitTests()
    {
        return Collections.emptyList();
    }
}

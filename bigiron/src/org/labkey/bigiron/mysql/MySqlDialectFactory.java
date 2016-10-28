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

package org.labkey.bigiron.mysql;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
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
public class MySqlDialectFactory implements SqlDialectFactory
{
    private static final Logger _log = Logger.getLogger(MySqlDialectFactory.class);

    private String getProductName()
    {
        return "MySQL";
    }

    @Override
    public @Nullable SqlDialect createFromDriverClassName(String driverClassName)
    {
        return "com.mysql.jdbc.Driver".equals(driverClassName) ? new MySqlDialect() : null;
    }

    @Override
    public @Nullable SqlDialect createFromProductNameAndVersion(String dataBaseProductName, String databaseProductVersion, String jdbcDriverVersion, boolean logWarnings, boolean primaryDataSource) throws DatabaseNotSupportedException
    {
        if (!getProductName().equals(dataBaseProductName))
            return null;

        VersionNumber versionNumber = new VersionNumber(databaseProductVersion);
        int version = versionNumber.getVersionInt();

        // Version 5.1 or greater is allowed...
        if (version >= 51)
        {
            // ...but warn for anything greater than 5.7
            if (logWarnings && version > 57)
                _log.warn("LabKey Server has not been tested against " + getProductName() + " version " + databaseProductVersion + ". " +  getProductName() + " 5.7 is the recommended version.");

            if (version >= 57)
                return new MySql57Dialect();

            if (version >= 56)
                return new MySql56Dialect();

            return new MySqlDialect();
        }

        throw new DatabaseNotSupportedException(getProductName() + " version " + databaseProductVersion + " is not supported. You must upgrade your database server installation to " + getProductName() + " version 5.1 or greater.");
    }

    @Override
    public Collection<? extends Class> getJUnitTests()
    {
        return Collections.emptyList();
    }

    @Override
    public Collection<? extends SqlDialect> getDialectsToTest()
    {
        return Collections.emptyList();
    }
}

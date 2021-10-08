/*
 * Copyright (c) 2010-2018 LabKey Corporation
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

import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.dialect.DatabaseNotSupportedException;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.SqlDialectFactory;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.util.VersionNumber;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;

/*
* User: adam
* Date: Nov 26, 2010
* Time: 10:11:46 PM
*/
public class MySqlDialectFactory implements SqlDialectFactory
{
    private static final Logger _log = LogManager.getLogger(MySqlDialectFactory.class);

    private static String getProductName()
    {
        return "MySQL";
    }

    public MySqlDialectFactory()
    {
        // MySQL JDBC driver should not be present in <tomcat>/lib
        DbScope.registerForbiddenTomcatFilenamePredicate(filename->filename.equalsIgnoreCase("mysql.jar"));

        // Terminate MySQL AbandonedConnectionCleanupThread at shutdown to avoid Tomcat logging IllegalStateException, Issue 42117
        ContextListener.addShutdownListener(new ShutdownListener()
        {
            @Override
            public String getName()
            {
                return "MySQL JDBC driver clean up";
            }

            @Override
            public void shutdownPre()
            {
                AbandonedConnectionCleanupThread.uncheckedShutdown();
            }

            @Override
            public void shutdownStarted()
            {
            }
        });
    }

    @Override
    public @Nullable SqlDialect createFromDriverClassName(String driverClassName)
    {
        return "com.mysql.jdbc.Driver".equals(driverClassName) ? new MySqlDialect() : null;
    }

    private final static String RECOMMENDED = getProductName() + " 8.0.x is the recommended version.";

    @Override
    public @Nullable SqlDialect createFromMetadata(DatabaseMetaData md, boolean logWarnings, boolean primaryDataSource) throws SQLException, DatabaseNotSupportedException
    {
        if (!getProductName().equals(md.getDatabaseProductName()))
            return null;

        String databaseProductVersion = md.getDatabaseProductVersion();
        VersionNumber versionNumber = new VersionNumber(databaseProductVersion);
        int version = versionNumber.getVersionInt();

        // Version 5.1 or greater is allowed...
        if (version >= 51)
        {
            // ...but warn for anything greater than 8.0.x
            if (logWarnings && version > 80)
                _log.warn("LabKey Server has not been tested against " + getProductName() + " version " + databaseProductVersion + ". " +  RECOMMENDED);

            if (version >= 80)
                return new MySql80Dialect();

            if (version >= 57)
                return new MySql57Dialect();

            if (version >= 56)
                return new MySql56Dialect();

            _log.warn(getProductName() + " version " + databaseProductVersion + " has reached end of life and is no longer supported or tested by LabKey. " +  RECOMMENDED);

            return new MySqlDialect();
        }

        throw new DatabaseNotSupportedException(getProductName() + " version " + databaseProductVersion + " is not supported. " + RECOMMENDED);
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

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

package org.labkey.api.data.dialect;

import org.jetbrains.annotations.NotNull;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
* User: adam
* Date: Nov 26, 2010
* Time: 9:05:41 PM
*/
public class SqlDialectManager
{
    // Ensures that all SqlDialectFactories are registered and SqlDialectFactory modifiers are applied before the first
    // dialect is created. See #33518.
    private static final List<SqlDialectFactory> FACTORIES = SqlDialectRegistry.getFactories();

    /**
     * Getting the SqlDialect from the datasource properties won't return the version-specific dialect -- use
     * getFromMetaData() if possible.
     * @throws SqlDialectNotSupportedException if database is not supported
     */
    public static @NotNull SqlDialect getFromDriverClassname(String dsName, String driverClassName)
    {
        for (SqlDialectFactory factory : FACTORIES)
        {
            SqlDialect dialect = factory.createFromDriverClassName(driverClassName);

            if (null != dialect)
                return dialect;
        }

        throw new SqlDialectNotSupportedException("The database driver \"" + driverClassName + "\" specified in data source \"" + dsName + "\" is not supported in your installation.");
    }


    /**
     * @throws SqlDialectNotSupportedException if database is not supported
     * @param primaryDataSource whether the data source is the primary LabKey Server database, or an external/secondary database
     */
    public static @NotNull SqlDialect getFromMetaData(DatabaseMetaData md, boolean logWarnings, boolean primaryDataSource) throws SQLException, SqlDialectNotSupportedException, DatabaseNotSupportedException
    {
        for (SqlDialectFactory factory : FACTORIES)
        {
            SqlDialect dialect = factory.createFromMetadata(md, logWarnings, primaryDataSource);

            if (null != dialect)
                return dialect;
        }

        throw new SqlDialectNotSupportedException("The requested product name and version -- " + md.getDatabaseProductName() + " " + md.getDatabaseProductVersion() + " -- is not supported by your LabKey installation.");
    }


    public static Collection<? extends Class> getAllJUnitTests()
    {
        Set<Class> classes = new HashSet<>();

        for (SqlDialectFactory factory : FACTORIES)
            classes.addAll(factory.getJUnitTests());

        return classes;
    }


    // Returns instances of all dialect implementations for testing purposes
    public static Collection<? extends SqlDialect> getAllDialectsToTest()
    {
        Set<SqlDialect> dialects = new HashSet<>();

        for (SqlDialectFactory factory : FACTORIES)
        {
            for (SqlDialect dialect : factory.getDialectsToTest())
            {
                // Must initialize all dialects before using; most convenient to do it here instead of forcing each
                // dialect to do this
                dialect.initialize();
                dialects.add(dialect);
            }
        }

        return dialects;
    }
}

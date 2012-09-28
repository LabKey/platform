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

import javax.servlet.ServletException;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/*
* User: adam
* Date: Nov 26, 2010
* Time: 9:05:41 PM
*/
public class SqlDialectManager
{
    private static List<SqlDialectFactory> _factories = new CopyOnWriteArrayList<SqlDialectFactory>();

    public static void register(SqlDialectFactory factory)
    {
        _factories.add(factory);
    }

    /**
     * Getting the SqlDialect from the datasource properties won't return the version-specific dialect -- use
     * getFromMetaData() if possible.
     */
    public static SqlDialect getFromDriverClassname(String dsName, String driverClassName) throws ServletException
    {
        for (SqlDialectFactory factory : _factories)
        {
            SqlDialect dialect = factory.createFromDriverClassName(driverClassName);

            if (null != dialect)
                return dialect;
        }

        throw new SqlDialectNotSupportedException("The database driver \"" + driverClassName + "\" specified in data source \"" + dsName + "\" is not supported in your installation.");
    }


    public static SqlDialect getFromMetaData(DatabaseMetaData md, boolean logWarnings) throws SQLException, SqlDialectNotSupportedException, DatabaseNotSupportedException
    {
        return getFromProductName(md.getDatabaseProductName(), md.getDatabaseProductVersion(), md.getDriverVersion(), logWarnings);
    }

    public static SqlDialect getFromProductName(String dataBaseProductName, String databaseProductVersion, String jdbcDriverVersion, boolean logWarnings) throws SqlDialectNotSupportedException, DatabaseNotSupportedException
    {
        for (SqlDialectFactory factory : _factories)
        {
            SqlDialect dialect = factory.createFromProductNameAndVersion(dataBaseProductName, databaseProductVersion, jdbcDriverVersion, logWarnings);

            if (null != dialect)
                return dialect;
        }

        throw new SqlDialectNotSupportedException("The requested product name and version -- " + dataBaseProductName + " " + databaseProductVersion + " -- is not supported by your LabKey installation.");
    }


    public static Collection<? extends Class> getAllJUnitTests()
    {
        Set<Class> classes = new HashSet<Class>();

        for (SqlDialectFactory factory : _factories)
            classes.addAll(factory.getJUnitTests());

        classes.add(RegularExpressionTest.class);

        return classes;
    }


    // Returns instances of all dialect implementations for testing purposes
    public static Collection<? extends SqlDialect> getAllDialectsToTest()
    {
        Set<SqlDialect> dialects = new HashSet<SqlDialect>();

        for (SqlDialectFactory factory : _factories)
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

/*
 * Copyright (c) 2011-2019 LabKey Corporation
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

package org.labkey.bigiron.oracle;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.dialect.AbstractDialectRetrievalTestCase;
import org.labkey.api.data.dialect.DatabaseNotSupportedException;
import org.labkey.api.data.dialect.JdbcHelperTest;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.SqlDialectFactory;
import org.labkey.api.data.dialect.SqlDialectManager;
import org.labkey.api.util.VersionNumber;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * User: trent
 * Date: 6/10/11
 * Time: 3:40 PM
 */
public class OracleDialectFactory implements SqlDialectFactory
{
    private static final Logger _log = LogManager.getLogger(OracleDialectFactory.class);

    public OracleDialectFactory()
    {
        // Oracle JDBC driver should not be present in <tomcat>/lib
        DbScope.registerForbiddenTomcatFilenamePredicate(filename-> StringUtils.startsWithIgnoreCase(filename, "ojdbc"));
    }

    private String getProductName()
    {
        return "Oracle";
    }

    @Override
    public SqlDialect createFromDriverClassName(String driverClassName)
    {
        return "oracle.jdbc.driver.OracleDriver".equals(driverClassName) ? new Oracle11gR1Dialect() : null;
    }

    private final static String PRE_VERSION_CLAUSE = "Release ";
    private final static String POST_VERSION_CLAUSE = "-";

    @Override
    public @Nullable SqlDialect createFromMetadata(DatabaseMetaData md, boolean logWarnings, boolean primaryDataSource) throws SQLException, DatabaseNotSupportedException
    {
        if (!md.getDatabaseProductName().equals(getProductName()))
            return null;

        /*
            Parse the product version from the metadata, to return only the version number (i.e. remove text)
            For the jdbcdriver I have, version is returned like:

            Oracle Database 11g Enterprise Edition Release 11.2.0.2.0 - 64bit Production
            With the Partitioning, OLAP, Data Mining and Real Application Testing options
        */
        String databaseProductVersion = md.getDatabaseProductVersion();

        int startIndex = databaseProductVersion.indexOf(PRE_VERSION_CLAUSE) + PRE_VERSION_CLAUSE.length();
        int endIndex = databaseProductVersion.indexOf(POST_VERSION_CLAUSE) - 1;

        VersionNumber versionNumber = new VersionNumber(databaseProductVersion.substring(startIndex, endIndex));

        // piggy back on Oracle11gDialect until incompatibilities are discovered
        if (versionNumber.getMajor() == 10)
            return new Oracle11gR1Dialect();

        // Restrict to 11g
        if (versionNumber.getMajor() == 11)
        {
            if (versionNumber.getVersionInt() == 111)
                return new Oracle11gR1Dialect();

            if (versionNumber.getVersionInt() >= 112)
                return new Oracle11gR2Dialect();
        }
        if (versionNumber.getMajor() == 12)
            return new Oracle12cDialect();

        if (versionNumber.getMajor() > 12)
        {
            // Trust that's it's backwards compatible enough to treat like 12c
            if (logWarnings)
                _log.warn("LabKey Server has not been tested against Oracle version " + databaseProductVersion + ". 11g or 12c are recommended.");

            return new Oracle12cDialect();
        }

        throw new DatabaseNotSupportedException(getProductName() + " version " + databaseProductVersion + " is not supported. You must upgrade your database server installation to " + getProductName() + " version 11g or greater.");
    }

    @Override
    public Collection<? extends Class> getJUnitTests()
    {
        return Arrays.asList(JdbcHelperTestCase.class, DialectRetrievalTestCase.class);
    }

    @Override
    public Collection<? extends SqlDialect> getDialectsToTest()
    {
        return Collections.emptyList();
    }

    public static class DialectRetrievalTestCase extends AbstractDialectRetrievalTestCase
    {
        @Override
        public void testDialectRetrieval()
        {
            validateVersion(Oracle11gR1Dialect.class, "Oracle Database 11g Enterprise Edition Release 11.1.0.2.0 - 64bit Production\n" +
                    "With the Partitioning, OLAP, Advanced Analytics and Real Application Testing options");
            validateVersion(Oracle11gR2Dialect.class, "Oracle Database 11g Enterprise Edition Release 11.2.0.2.0 - 64bit Production\n" +
                    "With the Partitioning, OLAP, Advanced Analytics and Real Application Testing options");
            validateVersion(Oracle11gR2Dialect.class, "Oracle Database 11g Enterprise Edition Release 11.3.0.2.0 - 64bit Production\n" +
                    "With the Partitioning, OLAP, Advanced Analytics and Real Application Testing options");
            validateVersion(Oracle12cDialect.class, "Oracle Database 12c Enterprise Edition Release 12.1.0.2.0 - 64bit Production\n" +
                    "With the Partitioning, OLAP, Advanced Analytics and Real Application Testing options");
        }

        private void validateVersion(Class<? extends SqlDialect> expectedDialectClass, String version)
        {
            try
            {
                assertEquals(expectedDialectClass, SqlDialectManager.getFromMetaData(getMockedMetadata("Oracle", version, null, null, null), false, false).getClass());
            }
            catch (Exception e)
            {
                fail(e.getClass() + " thrown for version: " + version + "\n Message: " + e.getMessage());
            }
        }
    }


    public static class JdbcHelperTestCase extends Assert
    {
        @Test
        public void testJdbcHelper()
        {
            JdbcHelperTest test = new JdbcHelperTest() {
                @NotNull
                @Override
                protected SqlDialect getDialect()
                {
                    return new Oracle11gR1Dialect();
                }

                @NotNull
                @Override
                protected Set<String> getGoodUrls()
                {
                    return new CsvSet(
                        // New syntax without id/password
                        "jdbc:oracle:thin:@//myhost:1521/database," +
                        "jdbc:oracle:thin:@//127.0.0.1:1521/database," +
                        "jdbc:oracle:thin:@//localhost:8300/database," +
                        "jdbc:oracle:thin:@//127.0.0.1/database," +
                        "jdbc:oracle:thin:@//localhost/database," +

                        // New syntax with id/password
                        "jdbc:oracle:thin:LabKey/TopSecrect@//myhost:1521/database," +
                        "jdbc:oracle:thin:LabKey/TopSecrect@//127.0.0.1:1521/database," +
                        "jdbc:oracle:thin:LabKey/TopSecrect@//localhost:8300/database," +
                        "jdbc:oracle:thin:LabKey/TopSecrect@//127.0.0.1/database," +
                        "jdbc:oracle:thin:LabKey/TopSecrect@//localhost/database," +

                        // Old syntax without id/password
                        "jdbc:oracle:thin:@myhost:1521:database," +
                        "jdbc:oracle:thin:@127.0.0.1:1521:database," +
                        "jdbc:oracle:thin:@localhost:8300:database," +
                        "jdbc:oracle:thin:@127.0.0.1:database," +
                        "jdbc:oracle:thin:@localhost:database," +

                        // Old syntax with id/password
                        "jdbc:oracle:thin:LabKey/TopSecrect@myhost:1521:database," +
                        "jdbc:oracle:thin:LabKey/TopSecrect@127.0.0.1:1521:database," +
                        "jdbc:oracle:thin:LabKey/TopSecrect@localhost:8300:database," +
                        "jdbc:oracle:thin:LabKey/TopSecrect@127.0.0.1:database," +
                        "jdbc:oracle:thin:LabKey/TopSecrect@localhost:database," +

                        // Others (from http://www.herongyang.com/JDBC/Oracle-JDBC-Driver-Connection-URL.html)
                        "jdbc:oracle:thin:LabKey/TopSecret@localhost:1521:database," +
                        "jdbc:oracle:thin:LabKey/TopSecret@:1521:database," +
                        "jdbc:oracle:thin:LabKey/TopSecret@//localhost:1521/database," +
                        "jdbc:oracle:thin:LabKey/TopSecret@//:1521/database," +
                        "jdbc:oracle:thin:LabKey/TopSecret@//localhost/database," +
                        "jdbc:oracle:thin:LabKey/TopSecret@///database");
                }

                @NotNull
                @Override
                protected Set<String> getBadUrls()
                {
                    return new CsvSet("jddc:oracle:thin:@database," +
                            "jdbc:oracle:thin://www.host.comdatabase");
                }
            };

            test.test();
        }
    }
}

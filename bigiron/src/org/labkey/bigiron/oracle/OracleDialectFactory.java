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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.dialect.AbstractDialectRetrievalTestCase;
import org.labkey.api.data.dialect.DatabaseNotSupportedException;
import org.labkey.api.data.dialect.JdbcHelperTest;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.SqlDialectFactory;
import org.labkey.api.data.dialect.SqlDialectManager;
import org.labkey.api.util.VersionNumber;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class OracleDialectFactory implements SqlDialectFactory
{
    public OracleDialectFactory()
    {
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
            Some examples we need to parse:

            Oracle Database 11g Enterprise Edition Release 11.2.0.2.0 - 64bit Production
            With the Partitioning, OLAP, Data Mining and Real Application Testing options

            Oracle Database 19c Enterprise Edition Release 19.0.0.0.0 - Production
            Version 19.3.0.0.0

            Oracle Database 23c Free, Release 23.0.0.0.0 - Developer-Release
            Version 23.2.0.0.0
        */
        String databaseProductVersion = md.getDatabaseProductVersion();

        int startIndex = databaseProductVersion.indexOf(PRE_VERSION_CLAUSE) + PRE_VERSION_CLAUSE.length();
        int endIndex = databaseProductVersion.indexOf(POST_VERSION_CLAUSE) - 1;

        VersionNumber versionNumber = new VersionNumber(databaseProductVersion.substring(startIndex, endIndex));

        OracleVersion ov = OracleVersion.get(versionNumber.getVersionInt());

        if (OracleVersion.ORACLE_UNSUPPORTED == ov)
            throw new DatabaseNotSupportedException(getProductName() + " version " + databaseProductVersion + " is not supported. You must upgrade your database server installation to " + getProductName() + " version 11g or greater.");

        return ov.getDialect();
    }

    @Override
    public Collection<? extends Class<?>> getJUnitTests()
    {
        return List.of(
            DialectRetrievalTestCase.class,
            JdbcHelperTestCase.class
        );
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
            validateVersion(Oracle19cDialect.class, "Oracle Database 19c Enterprise Edition Release 19.0.0.0.0 - Production\n" +
                "Version 19.3.0.0.0");
            validateVersion(Oracle23cDialect.class, "Oracle Database 23c Free, Release 23.0.0.0.0 - Developer-Release\n" +
                "Version 23.2.0.0.0");
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
                    return Set.of(
                        // New for 19c?
                        "jdbc:oracle:thin:@localhost:1521:database",

                        // New syntax without id/password
                        "jdbc:oracle:thin:@//myhost:1521/database",
                        "jdbc:oracle:thin:@//127.0.0.1:1521/database",
                        "jdbc:oracle:thin:@//localhost:8300/database",
                        "jdbc:oracle:thin:@//127.0.0.1/database",
                        "jdbc:oracle:thin:@//localhost/database",

                        // New syntax with id/password
                        "jdbc:oracle:thin:LabKey/TopSecrect@//myhost:1521/database",
                        "jdbc:oracle:thin:LabKey/TopSecrect@//127.0.0.1:1521/database",
                        "jdbc:oracle:thin:LabKey/TopSecrect@//localhost:8300/database",
                        "jdbc:oracle:thin:LabKey/TopSecrect@//127.0.0.1/database",
                        "jdbc:oracle:thin:LabKey/TopSecrect@//localhost/database",

                        // Old syntax without id/password
                        "jdbc:oracle:thin:@myhost:1521:database",
                        "jdbc:oracle:thin:@127.0.0.1:1521:database",
                        "jdbc:oracle:thin:@localhost:8300:database",
                        "jdbc:oracle:thin:@127.0.0.1:database",
                        "jdbc:oracle:thin:@localhost:database",

                        // Old syntax with id/password
                        "jdbc:oracle:thin:LabKey/TopSecrect@myhost:1521:database",
                        "jdbc:oracle:thin:LabKey/TopSecrect@127.0.0.1:1521:database",
                        "jdbc:oracle:thin:LabKey/TopSecrect@localhost:8300:database",
                        "jdbc:oracle:thin:LabKey/TopSecrect@127.0.0.1:database",
                        "jdbc:oracle:thin:LabKey/TopSecrect@localhost:database",

                        // Others (from http://www.herongyang.com/JDBC/Oracle-JDBC-Driver-Connection-URL.html)
                        "jdbc:oracle:thin:LabKey/TopSecret@localhost:1521:database",
                        "jdbc:oracle:thin:LabKey/TopSecret@:1521:database",
                        "jdbc:oracle:thin:LabKey/TopSecret@//localhost:1521/database",
                        "jdbc:oracle:thin:LabKey/TopSecret@//:1521/database",
                        "jdbc:oracle:thin:LabKey/TopSecret@//localhost/database",
                        "jdbc:oracle:thin:LabKey/TopSecret@///database"
                    );
                }

                @NotNull
                @Override
                protected Set<String> getBadUrls()
                {
                    return Set.of(
                        "jddc:oracle:thin:@database",
                        "jdbc:oracle:thin://www.host.comdatabase"
                    );
                }
            };

            test.test();
        }
    }
}

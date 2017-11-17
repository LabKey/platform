/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

package org.labkey.core.dialect;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.data.dialect.AbstractDialectRetrievalTestCase;
import org.labkey.api.data.dialect.DatabaseNotSupportedException;
import org.labkey.api.data.dialect.JdbcHelperTest;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.SqlDialectFactory;
import org.labkey.api.data.dialect.TestUpgradeCode;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.VersionNumber;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/*
* User: adam
* Date: Nov 26, 2010
* Time: 9:19:38 PM
*/
public class PostgreSqlDialectFactory implements SqlDialectFactory
{
    private static final Logger _log = Logger.getLogger(PostgreSqlDialectFactory.class);

    @Override
    public @Nullable SqlDialect createFromDriverClassName(String driverClassName)
    {
        return "org.postgresql.Driver".equals(driverClassName) ? new PostgreSql93Dialect() : null;
    }

    final static String PRODUCT_NAME = "PostgreSQL";
    final static String RECOMMENDED = PRODUCT_NAME + " 10.x is the recommended version.";

    @Override
    public @Nullable SqlDialect createFromProductNameAndVersion(String dataBaseProductName, String databaseProductVersion, String jdbcDriverVersion, boolean logWarnings, boolean primaryDataSource) throws DatabaseNotSupportedException
    {
        if (!PRODUCT_NAME.equals(dataBaseProductName))
            return null;

        int betaIdx = databaseProductVersion.indexOf("beta");

        if (-1 != betaIdx)
            databaseProductVersion = StringUtils.left(databaseProductVersion, betaIdx);

        int rcIdx = databaseProductVersion.indexOf("rc");

        if (-1 != rcIdx)
            databaseProductVersion = StringUtils.left(databaseProductVersion, rcIdx);

        VersionNumber versionNumber = new VersionNumber(databaseProductVersion);

        // Get the appropriate dialect and stash version information
        SqlDialect dialect = getDialect(versionNumber, databaseProductVersion, logWarnings);
        int versionInt = versionNumber.getVersionInt();
        dialect.setDatabaseVersion(versionInt);
        dialect.setProductVersion(String.valueOf(versionInt/(double)10));

        return dialect;
    }

    private @NotNull SqlDialect getDialect(VersionNumber versionNumber, String databaseProductVersion, boolean logWarnings)
    {
        int version = versionNumber.getVersionInt();

        // Version 9.3 or greater is allowed
        if (version >= 93)
        {
            // This approach is used when it's time to deprecate a version of PostgreSQL. Also, change the old dialect's
            // addAdminWarnings() method to add a message that gets displayed in the page header for admins.
//            if (93 == version)
//            {
//                // PostgreSQL 9.3 is deprecated; support will be removed soon
//                if (logWarnings)
//                    _log.warn("LabKey Server no longer supports " + PRODUCT_NAME + " version " + databaseProductVersion + ". " + RECOMMENDED);
//
//                return new PostgreSql93Dialect();
//            }

            if (93 == version)
                return new PostgreSql93Dialect();

            if (94 == version)
                return new PostgreSql94Dialect();

            if (95 == version)
                return new PostgreSql95Dialect();

            if (96 == version)
                return new PostgreSql96Dialect();

            // Any 10.x version is okay; 11.x+ gets a warning.
            // Note: PostgreSQL version format changed from x.y.z to x.y starting with 10.0.
            if (version >= 110 && logWarnings)
                _log.warn("LabKey Server has not been tested against " + PRODUCT_NAME + " version " + databaseProductVersion + ". " + RECOMMENDED);

            return new PostgreSql100Dialect();
        }

        throw new DatabaseNotSupportedException(PRODUCT_NAME + " version " + databaseProductVersion + " is not supported. You must upgrade your database server installation; " + RECOMMENDED);
    }


    public Collection<? extends Class> getJUnitTests()
    {
        return Arrays.<Class>asList(DialectRetrievalTestCase.class, InlineProcedureTestCase.class, JdbcHelperTestCase.class);
    }

    @Override
    public Collection<? extends SqlDialect> getDialectsToTest()
    {
        // PostgreSQL dialects are nearly identical, so just test 9.3
        return PageFlowUtil.set(
            new PostgreSql93Dialect(true),
            new PostgreSql93Dialect(false)
        );
    }

    public static class DialectRetrievalTestCase extends AbstractDialectRetrievalTestCase
    {
        public void testDialectRetrieval()
        {
            // These should result in bad database exception
            badProductName("Gobbledygood", 8.0, 9.6, "");
            badProductName("Postgres", 8.0, 9.6, "");
            badProductName("postgresql", 8.0, 9.6, "");

            // < 9.3 should result in bad version number exception
            badVersion("PostgreSQL", 0.0, 9.2, null);

            // >= 9.3 should be good
            good("PostgreSQL", 9.3, 9.4, "", PostgreSql93Dialect.class);
            good("PostgreSQL", 9.4, 9.5, "", PostgreSql94Dialect.class);
            good("PostgreSQL", 9.5, 9.6, "", PostgreSql95Dialect.class);
            good("PostgreSQL", 9.6, 9.7, "", PostgreSql96Dialect.class);
            good("PostgreSQL", 9.7, 11.0, "", PostgreSql100Dialect.class);
        }
    }

    public static class InlineProcedureTestCase extends Assert
    {
        @Test
        public void testJavaUpgradeCode()
        {
            String goodSql =
                    "SELECT core.executeJavaUpgradeCode('upgradeCode');\n" +                       // Normal
                            "    SELECT     core.executeJavaUpgradeCode    ('upgradeCode')    ;     \n" +  // Lots of whitespace
                            "select CORE.EXECUTEJAVAUPGRADECODE('upgradeCode');\n" +                       // Case insensitive
                            "SELECT core.executeJavaUpgradeCode('upgradeCode');";                          // No line ending


            String badSql =
                    "/* SELECT core.executeJavaUpgradeCode('upgradeCode');\n" +       // Inside block comment
                            "   more comment\n" +
                            "*/" +
                            "    -- SELECT core.executeJavaUpgradeCode('upgradeCode');\n" +   // Inside single-line comment
                            "SELECTcore.executeJavaUpgradeCode('upgradeCode');\n" +           // Bad syntax
                            "SELECT core. executeJavaUpgradeCode('upgradeCode');\n" +         // Bad syntax
                            "SEECT core.executeJavaUpgradeCode('upgradeCode');\n" +           // Misspell SELECT
                            "SELECT core.executeJaavUpgradeCode('upgradeCode');\n" +          // Misspell function name
                            "SELECT core.executeJavaUpgradeCode('upgradeCode')\n";            // No semicolon

            SqlDialect dialect = new PostgreSql93Dialect();
            TestUpgradeCode good = new TestUpgradeCode();
            dialect.runSql(null, goodSql, good, null, null);
            assertEquals(4, good.getCounter());

            TestUpgradeCode bad = new TestUpgradeCode();
            dialect.runSql(null, badSql, bad, null, null);
            assertEquals(0, bad.getCounter());
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
                    return new PostgreSql93Dialect();
                }

                @NotNull
                @Override
                protected Set<String> getGoodUrls()
                {
                    return new CsvSet("jdbc:postgresql:database," +
                        "jdbc:postgresql://localhost/database," +
                        "jdbc:postgresql://localhost:8300/database," +
                        "jdbc:postgresql://www.host.com/database," +
                        "jdbc:postgresql://www.host.com:8499/database," +
                        "jdbc:postgresql:database?user=fred&password=secret&ssl=true," +
                        "jdbc:postgresql://localhost/database?user=fred&password=secret&ssl=true," +
                        "jdbc:postgresql://localhost:8672/database?user=fred&password=secret&ssl=true," +
                        "jdbc:postgresql://www.host.com/database?user=fred&password=secret&ssl=true," +
                        "jdbc:postgresql://www.host.com:8992/database?user=fred&password=secret&ssl=true");
                }

                @NotNull
                @Override
                protected Set<String> getBadUrls()
                {
                    return new CsvSet("jddc:postgresql:database," +
                            "jdbc:postgres://localhost/database," +
                            "jdbc:postgresql://www.host.comdatabase");
                }
            };

            test.test();
        }
    }
}

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

package org.labkey.core.dialect;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.dialect.AbstractDialectRetrievalTestCase;
import org.labkey.api.data.dialect.DatabaseNotSupportedException;
import org.labkey.api.data.dialect.JdbcHelperTest;
import org.labkey.api.data.dialect.PostgreSqlServerType;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.SqlDialectFactory;
import org.labkey.api.data.dialect.TestUpgradeCode;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.VersionNumber;
import org.postgresql.jdbc.PgConnection;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/*
* User: adam
* Date: Nov 26, 2010
* Time: 9:19:38 PM
*/
public class PostgreSqlDialectFactory implements SqlDialectFactory
{
    private static final Logger _log = LogManager.getLogger(PostgreSqlDialectFactory.class);

    public PostgreSqlDialectFactory()
    {
        // PostgreSQL JDBC driver should not be present in <tomcat>/lib
        DbScope.registerForbiddenTomcatFilenamePredicate(filename->filename.equalsIgnoreCase("postgresql.jar"));
    }

    @Override
    public @Nullable SqlDialect createFromDriverClassName(String driverClassName)
    {
        return "org.postgresql.Driver".equals(driverClassName) ? new PostgreSql_10_Dialect() : null;
    }

    final static String PRODUCT_NAME = "PostgreSQL";
    final static String RECOMMENDED = PRODUCT_NAME + " 14.x is the recommended version.";
    final static String JDBC_PREFIX = "jdbc:postgresql:";

    @Override
    public @Nullable SqlDialect createFromMetadata(DatabaseMetaData md, boolean logWarnings, boolean primaryDataSource) throws SQLException, DatabaseNotSupportedException
    {
        if (!(StringUtils.startsWithIgnoreCase(md.getURL(), JDBC_PREFIX)))
            return null;

        String databaseProductVersion = md.getDatabaseProductVersion();

        int betaIdx = databaseProductVersion.indexOf("beta");
        if (-1 != betaIdx)
            databaseProductVersion = StringUtils.left(databaseProductVersion, betaIdx);

        int rcIdx = databaseProductVersion.indexOf("rc");
        if (-1 != rcIdx)
            databaseProductVersion = StringUtils.left(databaseProductVersion, rcIdx);

        int parenIdx = databaseProductVersion.indexOf("(");
        if (-1 != parenIdx)
            databaseProductVersion = StringUtils.left(databaseProductVersion, parenIdx);

        VersionNumber versionNumber = new VersionNumber(databaseProductVersion);
        PostgreSqlVersion psv = PostgreSqlVersion.get(versionNumber.getVersionInt());

        if (PostgreSqlVersion.POSTGRESQL_UNSUPPORTED == psv)
            throw new DatabaseNotSupportedException(PRODUCT_NAME + " version " + databaseProductVersion + " is not supported. You must upgrade your database server installation; " + RECOMMENDED);

        PostgreSql_10_Dialect dialect = psv.getDialect();

        Connection conn = md.getConnection();
        Map<String, String> parameterStatuses = (conn instanceof PgConnection ? ((PgConnection) conn).getParameterStatuses() : Collections.emptyMap());
        PostgreSqlServerType serverType = PostgreSqlServerType.getFromParameterStatuses(parameterStatuses);
        dialect.setServerType(serverType);

        if (logWarnings)
        {
            if (!psv.isTested())
            {
                _log.warn("LabKey Server has not been tested against " + PRODUCT_NAME + " version " + databaseProductVersion + ". " + RECOMMENDED);
            }
            else if (psv.isDeprecated())
            {
                String deprecationWarning = "LabKey Server no longer supports " + PRODUCT_NAME + " version " + databaseProductVersion + ". " + RECOMMENDED;
                _log.warn(deprecationWarning);
                dialect.setAdminWarning(HtmlString.of(deprecationWarning));
            }
        }

        return dialect;
    }

    @Override
    public Collection<? extends Class<?>> getJUnitTests()
    {
        return Arrays.asList(DialectRetrievalTestCase.class, InlineProcedureTestCase.class, JdbcHelperTestCase.class);
    }

    @Override
    public Collection<? extends SqlDialect> getDialectsToTest()
    {
        // PostgreSQL dialects are nearly identical, so just test 10.x
        PostgreSql_10_Dialect conforming = new PostgreSql_10_Dialect();
        conforming.setStandardConformingStrings(true);
        PostgreSql_10_Dialect nonconforming = new PostgreSql_10_Dialect();
        nonconforming.setStandardConformingStrings(false);

        return PageFlowUtil.set(
            conforming,
            nonconforming
        );
    }

    public static class DialectRetrievalTestCase extends AbstractDialectRetrievalTestCase
    {
        @Override
        public void testDialectRetrieval()
        {
            final String connectionUrl = "jdbc:postgresql:";

            // < 10.0 should result in bad version number exception. Note: versions 9.7, 9.8, and 9.9 never existed.
            badVersion("PostgreSQL", 0.0, 10.0, null, connectionUrl);

            // Test good versions
            good("PostgreSQL", 10.0, 11.0, "", connectionUrl, null, PostgreSql_10_Dialect.class);
            good("PostgreSQL", 11.0, 12.0, "", connectionUrl, null, PostgreSql_11_Dialect.class);
            good("PostgreSQL", 12.0, 13.0, "", connectionUrl, null, PostgreSql_12_Dialect.class);
            good("PostgreSQL", 13.0, 14.0, "", connectionUrl, null, PostgreSql_13_Dialect.class);
            good("PostgreSQL", 14.0, 15.0, "", connectionUrl, null, PostgreSql_14_Dialect.class);
            good("PostgreSQL", 15.0, 16.0, "", connectionUrl, null, PostgreSql_14_Dialect.class);
        }
    }

    public static class InlineProcedureTestCase extends Assert
    {
        @Test
        public void testJavaUpgradeCode()
        {
            String goodSql =
                "SELECT core.executeJavaUpgradeCode('upgradeCode');\n" +                       // Normal
                "SELECT core.executeJavaInitializationCode('upgradeCode');\n" +                // executeJavaInitializationCode works as a synonym
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

            SqlDialect dialect = new PostgreSql_10_Dialect();
            TestUpgradeCode good = new TestUpgradeCode();
            dialect.runSql(null, goodSql, good, null, null);
            assertEquals(5, good.getCounter());

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
                    return new PostgreSql_10_Dialect();
                }

                @NotNull
                @Override
                protected Set<String> getGoodUrls()
                {
                    return new CsvSet
                    (
                        "jdbc:postgresql:database," +
                        "jdbc:postgresql://localhost/database," +
                        "jdbc:postgresql://localhost:8300/database," +
                        "jdbc:postgresql://www.host.com/database," +
                        "jdbc:postgresql://www.host.com:8499/database," +
                        "jdbc:postgresql:database?user=fred&password=secret&ssl=true," +
                        "jdbc:postgresql://localhost/database?user=fred&password=secret&ssl=true," +
                        "jdbc:postgresql://localhost:8672/database?user=fred&password=secret&ssl=true," +
                        "jdbc:postgresql://www.host.com/database?user=fred&password=secret&ssl=true," +
                        "jdbc:postgresql://www.host.com:8992/database?user=fred&password=secret&ssl=true"
                    );
                }

                @NotNull
                @Override
                protected Set<String> getBadUrls()
                {
                    return new CsvSet
                    (
                        "jddc:postgresql:database," +
                        "jdbc:postgres://localhost/database," +
                        "jdbc:postgresql://www.host.comdatabase"
                    );
                }
            };

            test.test();
        }
    }
}

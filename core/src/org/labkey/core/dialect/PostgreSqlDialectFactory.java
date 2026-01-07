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
import org.apache.commons.lang3.Strings;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.dialect.AbstractDialectRetrievalTestCase;
import org.labkey.api.data.dialect.DatabaseNotSupportedException;
import org.labkey.api.data.dialect.JdbcHelperTest;
import org.labkey.api.data.dialect.PostgreSqlServerType;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.SqlDialectFactory;
import org.labkey.api.data.dialect.TestUpgradeCodeCounter;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.VersionNumber;
import org.labkey.api.util.logging.LogHelper;
import org.postgresql.jdbc.PgConnection;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class PostgreSqlDialectFactory implements SqlDialectFactory
{
    private static final Logger _log = LogHelper.getLogger(PostgreSqlDialectFactory.class, "PostgreSQL version warnings");

    public PostgreSqlDialectFactory()
    {
    }

    @Override
    public @Nullable SqlDialect createFromDriverClassName(String driverClassName)
    {
        return "org.postgresql.Driver".equals(driverClassName) ? getOldestSupportedDialect() : null;
    }

    final static String JDBC_PREFIX = "jdbc:postgresql:";

    @Override
    public @Nullable SqlDialect createFromMetadata(DatabaseMetaData md, boolean logWarnings, boolean primaryDataSource) throws SQLException, DatabaseNotSupportedException
    {
        if (!(Strings.CI.startsWith(md.getURL(), JDBC_PREFIX)))
            return null;

        String databaseProductVersion = md.getDatabaseProductVersion();

        int betaIdx = databaseProductVersion.indexOf("beta");
        if (-1 != betaIdx)
            databaseProductVersion = StringUtils.left(databaseProductVersion, betaIdx).trim();

        int rcIdx = databaseProductVersion.indexOf("rc");
        if (-1 != rcIdx)
            databaseProductVersion = StringUtils.left(databaseProductVersion, rcIdx).trim();

        int parenIdx = databaseProductVersion.indexOf("(");
        if (-1 != parenIdx)
            databaseProductVersion = StringUtils.left(databaseProductVersion, parenIdx).trim();

        VersionNumber versionNumber = new VersionNumber(databaseProductVersion);
        PostgreSqlVersion psv = PostgreSqlVersion.get(versionNumber.getVersionInt());

        if (PostgreSqlVersion.POSTGRESQL_UNSUPPORTED == psv)
            throw new DatabaseNotSupportedException(getStandardWarningMessage("does not support", databaseProductVersion));

        PostgreSql_11_Dialect dialect = psv.getDialect();

        Connection conn = md.getConnection();
        Map<String, String> parameterStatuses = (conn instanceof PgConnection ? ((PgConnection) conn).getParameterStatuses() : Collections.emptyMap());
        PostgreSqlServerType serverType = PostgreSqlServerType.getFromParameterStatuses(parameterStatuses);
        dialect.setServerType(serverType);
        dialect.setMajorVersion(versionNumber.getMajor());

        if (logWarnings)
        {
            if (!psv.isTested())
            {
                _log.warn(getStandardWarningMessage("has not been tested against", databaseProductVersion));
            }
            else if (psv.isDeprecated())
            {
                String deprecationWarning = getStandardWarningMessage("no longer supports", databaseProductVersion);
                _log.warn(deprecationWarning);
                dialect.setAdminWarning(HtmlString.of(deprecationWarning));
            }
        }

        return dialect;
    }

    public static String getStandardWarningMessage(String warning, String databaseProductVersion)
    {
        return "LabKey Server " + warning + " " + PostgreSql92Dialect.PRODUCT_NAME + " version " + databaseProductVersion + ". " + PostgreSqlVersion.RECOMMENDED;
    }

    @Override
    public Collection<? extends Class<?>> getJUnitTests()
    {
        return Arrays.asList(DialectRetrievalTestCase.class, InlineProcedureTestCase.class, JdbcHelperTestCase.class);
    }

    @Override
    public Collection<? extends SqlDialect> getDialectsToTest()
    {
        // PostgreSQL dialects are nearly identical, so just test the oldest supported one
        PostgreSql_13_Dialect conforming = getOldestSupportedDialect();
        conforming.setStandardConformingStrings(true);
        PostgreSql_13_Dialect nonconforming = getOldestSupportedDialect();
        nonconforming.setStandardConformingStrings(false);

        return PageFlowUtil.set(
            conforming,
            nonconforming
        );
    }

    public static PostgreSql_13_Dialect getOldestSupportedDialect()
    {
        return new PostgreSql_13_Dialect();
    }

    public static class DialectRetrievalTestCase extends AbstractDialectRetrievalTestCase
    {
        @Override
        public void testDialectRetrieval()
        {
            final String connectionUrl = "jdbc:postgresql:";

            // < 13.0 should result in bad version number exception
            badVersion("PostgreSQL", 0.0, 13.0, null, connectionUrl);

            // Test good versions
            good("PostgreSQL", 13.0, 14.0, "", connectionUrl, null, PostgreSql_13_Dialect.class);
            good("PostgreSQL", 14.0, 15.0, "", connectionUrl, null, PostgreSql_14_Dialect.class);
            good("PostgreSQL", 15.0, 16.0, "", connectionUrl, null, PostgreSql_15_Dialect.class);
            good("PostgreSQL", 16.0, 17.0, "", connectionUrl, null, PostgreSql_16_Dialect.class);
            good("PostgreSQL", 17.0, 18.0, "", connectionUrl, null, PostgreSql_17_Dialect.class);
            good("PostgreSQL", 18.0, 19.0, "", connectionUrl, null, PostgreSql_18_Dialect.class);
            good("PostgreSQL", 19.0, 20.0, "", connectionUrl, null, PostgreSql_18_Dialect.class);
        }
    }

    public static class InlineProcedureTestCase extends Assert
    {
        @Test
        public void testJavaUpgradeCode()
        {
            String goodSql =
                "SELECT core.executeJavaUpgradeCode('testUpgradeCode');\n" +                       // Normal
                "SELECT core.executeJavaInitializationCode('testUpgradeCode');\n" +                // executeJavaInitializationCode works as a synonym
                "    SELECT     core.executeJavaUpgradeCode    ('testUpgradeCode')    ;     \n" +  // Lots of whitespace
                "select CORE.EXECUTEJAVAUPGRADECODE('testUpgradeCode');\n" +                       // Case-insensitive
                "SELECT core.executeJavaUpgradeCode('testUpgradeCode');";                          // No line ending

            String badSql =
                "/* SELECT core.executeJavaUpgradeCode('testUpgradeCode');\n" +       // Inside block comment
                "   more comment\n" +
                "*/" +
                "    -- SELECT core.executeJavaUpgradeCode('testUpgradeCode');\n" +   // Inside single-line comment
                "SELECTcore.executeJavaUpgradeCode('testUpgradeCode');\n" +           // Bad syntax
                "SELECT core. executeJavaUpgradeCode('testUpgradeCode');\n" +         // Bad syntax
                "SEECT core.executeJavaUpgradeCode('testUpgradeCode');\n" +           // Misspell SELECT
                "SELECT core.executeJaavUpgradeCode('testUpgradeCode');\n" +          // Misspell function name
                "SELECT core.executeJavaUpgradeCode('testUpgradeCode')\n";            // No semicolon

            SqlDialect dialect = getOldestSupportedDialect();
            ModuleContext coreContext = ModuleLoader.getInstance().getModuleContext(ModuleLoader.getInstance().getCoreModule());
            String description = "PostgreSQL InlineProcedureTestCase";

            TestUpgradeCodeCounter.resetCounter();
            dialect.runSql(description, null, goodSql, coreContext, null);
            assertEquals(5, TestUpgradeCodeCounter.getCount());

            TestUpgradeCodeCounter.resetCounter();
            dialect.runSql(description, null, badSql, coreContext, null);
            assertEquals(0, TestUpgradeCodeCounter.getCount());

            // Verify that fallbackHandler() is called if upgrade method doesn't exist
            String fallbackTestSql = goodSql.replace("'testUpgradeCode'", "'bogusUpgradeCode'");
            TestUpgradeCodeCounter.resetCounter();
            try
            {
                dialect.runSql(description, null, fallbackTestSql, coreContext, null);
                fail("Expected fallthroughHandler() to throw a RuntimeException");
            }
            catch (RuntimeException e)
            {
                // Default fallthroughHandler() throws with the below message
                assertEquals("Can't find method bogusUpgradeCode(ModuleContext moduleContext) on class org.labkey.core.CoreUpgradeCode", e.getMessage());
                assertEquals(0, TestUpgradeCodeCounter.getCount());
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
                    return getOldestSupportedDialect();
                }

                @NotNull
                @Override
                protected Set<String> getGoodUrls()
                {
                    return Set.of
                    (
                        "jdbc:postgresql:database",
                        "jdbc:postgresql://localhost/database",
                        "jdbc:postgresql://localhost:8300/database",
                        "jdbc:postgresql://www.host.com/database",
                        "jdbc:postgresql://www.host.com:8499/database",
                        "jdbc:postgresql:database?user=fred&password=secret&ssl=true",
                        "jdbc:postgresql://localhost/database?user=fred&password=secret&ssl=true",
                        "jdbc:postgresql://localhost:8672/database?user=fred&password=secret&ssl=true",
                        "jdbc:postgresql://www.host.com/database?user=fred&password=secret&ssl=true",
                        "jdbc:postgresql://www.host.com:8992/database?user=fred&password=secret&ssl=true"
                    );
                }

                @NotNull
                @Override
                protected Set<String> getBadUrls()
                {
                    return Set.of
                    (
                        "jddc:postgresql:database",
                        "jdbc:postgres://localhost/database",
                        "jdbc:postgresql://www.host.comdatabase"
                    );
                }
            };

            test.test();
        }
    }
}

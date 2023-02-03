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

package org.labkey.bigiron.mssql;

import org.apache.logging.log4j.Logger;
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
import org.labkey.api.data.dialect.TestUpgradeCode;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.VersionNumber;
import org.labkey.api.util.logging.LogHelper;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/*
* User: adam
* Date: Nov 26, 2010
* Time: 9:51:40 PM
*/
public class MicrosoftSqlServerDialectFactory implements SqlDialectFactory
{
    private static final Logger LOG = LogHelper.getLogger(MicrosoftSqlServerDialectFactory.class, "Warnings about SQL Server versions");
    public static final String PRODUCT_NAME = "Microsoft SQL Server";

    public MicrosoftSqlServerDialectFactory()
    {
    }

    @Override
    public @Nullable SqlDialect createFromDriverClassName(String driverClassName)
    {
        return "com.microsoft.sqlserver.jdbc.SQLServerDriver".equals(driverClassName) ? new MicrosoftSqlServer2014Dialect() : null;
    }

    static final String RECOMMENDED = PRODUCT_NAME + " 2022 is the recommended version.";

    @Override
    public @Nullable SqlDialect createFromMetadata(DatabaseMetaData md, boolean logWarnings, boolean primaryDataSource) throws SQLException, DatabaseNotSupportedException
    {
        if (!md.getDatabaseProductName().equals(PRODUCT_NAME))
            return null;

        String jdbcProductVersion = md.getDatabaseProductVersion();
        VersionNumber versionNumber = new VersionNumber(jdbcProductVersion);
        int version = versionNumber.getVersionInt();

        // Get the appropriate dialect and stash the version year
        BaseMicrosoftSqlServerDialect dialect = getDialect(version, jdbcProductVersion, logWarnings, primaryDataSource);
        String className = dialect.getClass().getSimpleName();
        dialect.setVersionYear(className.substring(18, className.indexOf("Dialect")));

        String driverName = md.getDriverName();

        if (!driverName.startsWith("Microsoft"))
            LOG.warn("LabKey Server has not been tested against " + driverName + ". Instead, we recommend configuring the Microsoft SQL Server JDBC Driver, which is distributed with LabKey Server.");

        return dialect;
    }

    private BaseMicrosoftSqlServerDialect getDialect(int version, String databaseProductVersion, boolean logWarnings, boolean primaryDataSource)
    {
        MicrosoftSqlServerVersion ssv = MicrosoftSqlServerVersion.get(version, primaryDataSource);

        if (MicrosoftSqlServerVersion.SQL_SERVER_UNSUPPORTED == ssv)
            throw new DatabaseNotSupportedException(getStandardWarningMessage("does not support", databaseProductVersion));

        MicrosoftSqlServer2008R2Dialect dialect = ssv.getDialect();

        if (logWarnings)
        {
            // It's an old version being used as an external schema... we allow this but still warn to encourage upgrades
            if (!ssv.isAllowedAsPrimaryDataSource())
            {
                LOG.warn(getStandardWarningMessage("no longer supports", databaseProductVersion));
            }

            if (!ssv.isTested())
            {
                LOG.warn(getStandardWarningMessage("has not been tested against", databaseProductVersion));
            }
            else if (ssv.isDeprecated())
            {
                String deprecationWarning = getStandardWarningMessage("no longer supports", databaseProductVersion);
                LOG.warn(deprecationWarning);
                dialect.setAdminWarning(HtmlString.of(deprecationWarning));
            }
        }

        return dialect;
    }

    public static String getStandardWarningMessage(String warning, String databaseProductVersion)
    {
        return "LabKey Server " + warning + " " + PRODUCT_NAME + " version " + databaseProductVersion + ". " + RECOMMENDED;
    }

    @Override
    public Collection<? extends Class<?>> getJUnitTests()
    {
        return Arrays.asList(DialectRetrievalTestCase.class, InlineProcedureTestCase.class, JdbcHelperTestCase.class);
    }

    @Override
    public Collection<? extends SqlDialect> getDialectsToTest()
    {
        // The SQL Server dialects are identical, so just test one
        return Set.of(new MicrosoftSqlServer2014Dialect());
    }

    private static SqlDialect getEarliestSqlDialect()
    {
        return SqlDialectManager.getFromDriverClassname("TEST", "com.microsoft.sqlserver.jdbc.SQLServerDriver");
    }

    public static class DialectRetrievalTestCase extends AbstractDialectRetrievalTestCase
    {
        @Override
        public void testDialectRetrieval()
        {
            // These should result in bad database exception
            badProductName("Gobbledygook", 1.0, 14.0, "", null);
            badProductName("SQL Server", 1.0, 14.0, "", null);
            badProductName("sqlserver", 1.0, 14.0, "", null);

            // < 10.0 should result in bad version error
            badVersion("Microsoft SQL Server", 0.0, 10.0, null, null);

            String driverName = "Microsoft JDBC Driver";

            // >= 10.0 and < 11.0 should result in MicrosoftSqlServer2008R2Dialect
            good("Microsoft SQL Server", 10.0, 11.0, "", null, driverName, MicrosoftSqlServer2008R2Dialect.class);

            // >= 11.0 and < 12.0 should result in MicrosoftSqlServer2012Dialect
            good("Microsoft SQL Server", 11.0, 12.0, "", null, driverName, MicrosoftSqlServer2012Dialect.class);

            // >= 12.0 and < 13.0 should result in MicrosoftSqlServer2014Dialect
            good("Microsoft SQL Server", 12.0, 13.0, "", null, driverName, MicrosoftSqlServer2014Dialect.class);

            // >= 13.0 and < 14.0 should result in MicrosoftSqlServer2016Dialect
            good("Microsoft SQL Server", 13.0, 14.0, "", null, driverName, MicrosoftSqlServer2016Dialect.class);

            // >= 14.0 and < 15.0 should result in MicrosoftSqlServer2017Dialect
            good("Microsoft SQL Server", 14.0, 15.0, "", null, driverName, MicrosoftSqlServer2017Dialect.class);

            // >= 15.0 and < 16.0 should result in MicrosoftSqlServer2019Dialect
            good("Microsoft SQL Server", 15.0, 16.0, "", null, driverName, MicrosoftSqlServer2019Dialect.class);

            // >= 16.0 should result in MicrosoftSqlServer2022Dialect
            good("Microsoft SQL Server", 16.0, 18.0, "", null, driverName, MicrosoftSqlServer2022Dialect.class);
        }
    }

    public static class InlineProcedureTestCase extends Assert
    {
        @Test
        public void testJavaUpgradeCode()
        {
            String goodSql =
                "EXEC core.executeJavaUpgradeCode 'upgradeCode'\n" +                       // Normal
                "EXECUTE core.executeJavaUpgradeCode 'upgradeCode'\n" +                    // EXECUTE
                "execute core.executeJavaUpgradeCode'upgradeCode'\n" +                     // execute

                "EXEC core.executeJavaInitializationCode 'upgradeCode'\n" +                // executeJavaInitializationCode works as a synonym
                "EXECUTE core.executeJavaInitializationCode 'upgradeCode'\n" +             // EXECUTE
                "execute core.executeJavaInitializationCode'upgradeCode'\n" +              // execute

                "    EXEC     core.executeJavaUpgradeCode    'upgradeCode'         \n" +   // Lots of whitespace
                "exec CORE.EXECUTEJAVAUPGRADECODE 'upgradeCode'\n" +                       // Case insensitive
                "execute core.executeJavaUpgradeCode'upgradeCode';\n" +                    // execute (with ;)
                "    EXEC     core.executeJavaUpgradeCode    'upgradeCode'    ;     \n" +  // Lots of whitespace with ; in the middle
                "exec CORE.EXECUTEJAVAUPGRADECODE 'upgradeCode';     \n" +                 // Case insensitive (with ;)
                "EXEC core.executeJavaUpgradeCode 'upgradeCode'     ;\n" +                 // Lots of whitespace with ; at end
                "EXEC core.executeJavaUpgradeCode 'upgradeCode'";                          // No line ending


            String badSql =
                "/* EXEC core.executeJavaUpgradeCode 'upgradeCode'\n" +           // Inside block comment
                "   more comment\n" +
                "*/" +
                "    -- EXEC core.executeJavaUpgradeCode 'upgradeCode'\n" +       // Inside single-line comment
                "EXECcore.executeJavaUpgradeCode 'upgradeCode'\n" +               // Bad syntax: EXECcore
                "EXEC core. executeJavaUpgradeCode 'upgradeCode'\n" +             // Bad syntax: core. execute...
                "EXECUT core.executeJavaUpgradeCode 'upgradeCode'\n" +            // Misspell EXECUTE
                "EXECUTEUTE core.executeJavaUpgradeCode 'upgradeCode'\n" +        // Misspell EXECUTE -- previous regex allowed this
                "EXEC core.executeJaavUpgradeCode 'upgradeCode'\n" +              // Misspell executeJavaUpgradeCode
                "EXEC core.executeJavaUpgradeCode 'upgradeCode';;\n" +            // Bad syntax: two semicolons
                "EXEC core.executeJavaUpgradeCode('upgradeCode')\n";              // Bad syntax: parentheses

            SqlDialect dialect = getEarliestSqlDialect();
            TestUpgradeCode good = new TestUpgradeCode();
            dialect.runSql(null, goodSql, good, null, null);
            assertEquals(13, good.getCounter());

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
                    return getEarliestSqlDialect();
                }

                @NotNull
                @Override
                protected Set<String> getGoodUrls()
                {
                    return Set.of
                    (
                        "jdbc:sqlserver://;databaseName=database",
                        "jdbc:sqlserver://;servername=localhost;databaseName=database",
                        "jdbc:sqlserver://localhost;databaseName=database",
                        "jdbc:sqlserver://localhost:1433;databaseName=database",
                        "jdbc:sqlserver://localhost\\instancename;databaseName=database",
                        "jdbc:sqlserver://localhost\\instancename:1433;databaseName=database",
                        "jdbc:sqlserver://www.host.com\\instancename;databaseName=database",
                        "jdbc:sqlserver://www.host.com\\instancename:1433;databaseName=database",
                        "jdbc:sqlserver://www.host.com:1433;databaseName=database",
                        "jdbc:sqlserver://www.host.com:1433\\instanceName;databaseName=database;",
                        "jdbc:sqlserver://www.host.com:1433;SelectMethod=cursor;databaseName=database",
                        "jdbc:sqlserver://www.host.com:1433;SelectMethod=cursor;databaseName=database;",
                        "jdbc:sqlserver://;servername=www.host.com;databaseName=database",
                        "jdbc:sqlserver://;database=database",
                        "jdbc:sqlserver://;servername=localhost;database=database",
                        "jdbc:sqlserver://localhost;database=database",
                        "jdbc:sqlserver://localhost:1433;database=database",
                        "jdbc:sqlserver://localhost\\instancename;database=database",
                        "jdbc:sqlserver://localhost\\instancename:1433;database=database",
                        "jdbc:sqlserver://www.host.com\\instancename;database=database",
                        "jdbc:sqlserver://www.host.com\\instancename:1433;database=database",
                        "jdbc:sqlserver://www.host.com:1433;database=database",
                        "jdbc:sqlserver://www.host.com:1433\\instanceName;database=database;",
                        "jdbc:sqlserver://www.host.com:1433;SelectMethod=cursor;database=database",
                        "jdbc:sqlserver://www.host.com:1433;SelectMethod=cursor;database=database;",
                        "jdbc:sqlserver://;servername=www.host.com;database=database"
                    );
                }

                @NotNull
                @Override
                protected Set<String> getBadUrls()
                {
                    return Set.of
                    (
                        "jdbc:sqlserver://",
                        "jdbc:sqlserver://;servername=localhost",
                        "jdbc:sqlserver://localhost",
                        "jdbc:sqlserver://localhost:1433",
                        "jdbc:jtds:sqlserver://localhost/database",
                        "jdbc:jtds:sqlserver://localhost:1433/database",
                        "jdb:sqlserver://localhost/database",
                        "jdbc:sqlerver://localhost/database",
                        "jdbc:sqlserver://localhostdatabase",
                        "jdbc:sqlserver:database",
                        "jdbc:sqlserver://localhost\\instancename",
                        "jdbc:sqlserver://localhost\\instancename:1433",
                        "jdbc:sqlserver://www.host.com\\instancename",
                        "jdbc:sqlserver://www.host.com\\instancename:1433"
                    );
                }
            };

            test.test();
        }
    }
}

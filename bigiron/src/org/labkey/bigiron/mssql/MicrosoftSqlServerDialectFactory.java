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

package org.labkey.bigiron.mssql;

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
import org.labkey.api.data.dialect.SqlDialectManager;
import org.labkey.api.data.dialect.StandardTableResolver;
import org.labkey.api.data.dialect.TableResolver;
import org.labkey.api.data.dialect.TestUpgradeCode;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.VersionNumber;

import javax.servlet.ServletException;
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
    private static final Logger LOG = Logger.getLogger(MicrosoftSqlServerDialectFactory.class);
    public static final String PRODUCT_NAME = "Microsoft SQL Server";

    private volatile TableResolver _tableResolver = new StandardTableResolver();

    private String getProductName()
    {
        return PRODUCT_NAME;
    }

    @Override
    public @Nullable SqlDialect createFromDriverClassName(String driverClassName)
    {
        switch (driverClassName)
        {
            case "net.sourceforge.jtds.jdbc.Driver":
            case "com.microsoft.sqlserver.jdbc.SQLServerDriver":
                return new MicrosoftSqlServer2012Dialect(_tableResolver);
            default:
                return null;
        }
    }

    static final String RECOMMENDED = PRODUCT_NAME + " 2016 is the recommended version.";

    @Override
    public @Nullable SqlDialect createFromProductNameAndVersion(String dataBaseProductName, String databaseProductVersion, String jdbcDriverVersion, boolean logWarnings, boolean primaryDataSource) throws DatabaseNotSupportedException
    {
        if (!dataBaseProductName.equals(getProductName()))
            return null;

        VersionNumber versionNumber = new VersionNumber(databaseProductVersion);
        int version = versionNumber.getVersionInt();

        // Get the appropriate dialect and stash version information
        SqlDialect dialect = getDialect(version, databaseProductVersion, logWarnings, primaryDataSource);
        dialect.setDatabaseVersion(version);
        String className = dialect.getClass().getSimpleName();
        dialect.setProductVersion(className.substring(18, className.indexOf("Dialect")));

        return dialect;
    }

    private SqlDialect getDialect(int version, String databaseProductVersion, boolean logWarnings, boolean primaryDataSource)
    {
        // Good resources for past & current SQL Server version numbers:
        // - http://www.sqlteam.com/article/sql-server-versions
        // - http://sqlserverbuilds.blogspot.se/

        // We support only 2012 and higher as the primary data source, or 2008/2008R2 as an external data source
        if (version >= 100)
        {
            if (version >= 140)
                return new MicrosoftSqlServer2017Dialect(_tableResolver);

            if (version >= 130)
                return new MicrosoftSqlServer2016Dialect(_tableResolver);

            if (version >= 120)
                return new MicrosoftSqlServer2014Dialect(_tableResolver);

            if (version >= 110)
                return new MicrosoftSqlServer2012Dialect(_tableResolver);

            // Accept 2008 or 2008R2 as an external/supplemental database, but not as the primary database
            if (!primaryDataSource)
            {
                if (logWarnings)
                    LOG.warn("LabKey Server no longer supports " + getProductName() + " version " + databaseProductVersion + ". " + RECOMMENDED);

                return new MicrosoftSqlServer2008R2Dialect(_tableResolver);
            }
        }

        throw new DatabaseNotSupportedException(getProductName() + " version " + databaseProductVersion + " is not supported.");
    }

    @Override
    public Collection<? extends Class> getJUnitTests()
    {
        return Arrays.asList(DialectRetrievalTestCase.class, InlineProcedureTestCase.class, JdbcHelperTestCase.class);
    }

    @Override
    public Collection<? extends SqlDialect> getDialectsToTest()
    {
        // The SQL Server dialects are identical, so just test one
        return PageFlowUtil.set(new MicrosoftSqlServer2012Dialect(_tableResolver));
    }

    @Override
    public void setTableResolver(TableResolver tableResolver)
    {
        _tableResolver = tableResolver;
    }


    private static SqlDialect getEarliestSqlDialect() throws ServletException
    {
        return  SqlDialectManager.getFromDriverClassname("TEST", "net.sourceforge.jtds.jdbc.Driver");
    }

    public static class DialectRetrievalTestCase extends AbstractDialectRetrievalTestCase
    {
        public void testDialectRetrieval()
        {
            // These should result in bad database exception
            badProductName("Gobbledygook", 1.0, 14.0, "");
            badProductName("SQL Server", 1.0, 14.0, "");
            badProductName("sqlserver", 1.0, 14.0, "");

            // < 10.5 should result in bad version error
            badVersion("Microsoft SQL Server", 0.0, 10.0, null);

            // >= 10.0 and < 11.0 should result in MicrosoftSqlServer2008R2Dialect
            good("Microsoft SQL Server", 10.0, 11.0, "", MicrosoftSqlServer2008R2Dialect.class);

            // >= 11.0 and < 12.0 should result in MicrosoftSqlServer2012Dialect
            good("Microsoft SQL Server", 11.0, 12.0, "", MicrosoftSqlServer2012Dialect.class);

            // >= 12.0 should result in MicrosoftSqlServer2014Dialect
            good("Microsoft SQL Server", 12.0, 13.0, "", MicrosoftSqlServer2014Dialect.class);

            // >= 13.0 should result in MicrosoftSqlServer2016Dialect
            good("Microsoft SQL Server", 13.0, 14.0, "", MicrosoftSqlServer2016Dialect.class);

            // >= 14.0 should result in MicrosoftSqlServer2017Dialect
            good("Microsoft SQL Server", 14.0, 16.0, "", MicrosoftSqlServer2017Dialect.class);
        }
    }

    public static class InlineProcedureTestCase extends Assert
    {
        @Test
        public void testJavaUpgradeCode() throws ServletException
        {
            String goodSql =
                    "EXEC core.executeJavaUpgradeCode 'upgradeCode'\n" +                       // Normal
                    "EXECUTE core.executeJavaUpgradeCode 'upgradeCode'\n" +                    // EXECUTE
                    "execute core.executeJavaUpgradeCode'upgradeCode'\n" +                     // execute
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
            assertEquals(10, good.getCounter());

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
                    try
                    {
                        return getEarliestSqlDialect();
                    }
                    catch (ServletException e)
                    {
                        throw new RuntimeException(e);
                    }
                }

                @NotNull
                @Override
                protected Set<String> getGoodUrls()
                {
                    return new CsvSet("jdbc:jtds:sqlserver://localhost/database," +
                        "jdbc:jtds:sqlserver://localhost:1433/database," +
                        "jdbc:jtds:sqlserver://localhost/database;SelectMethod=cursor," +
                        "jdbc:jtds:sqlserver://localhost:1433/database;SelectMethod=cursor," +
                        "jdbc:jtds:sqlserver://www.host.com/database," +
                        "jdbc:jtds:sqlserver://www.host.com:1433/database," +
                        "jdbc:jtds:sqlserver://www.host.com/database;SelectMethod=cursor," +
                        "jdbc:jtds:sqlserver://www.host.com:1433/database;SelectMethod=cursor");
                }

                @NotNull
                @Override
                protected Set<String> getBadUrls()
                {
                    return new CsvSet("jdb:jtds:sqlserver://localhost/database," +
                        "jdbc:jts:sqlserver://localhost/database," +
                        "jdbc:jtds:sqlerver://localhost/database," +
                        "jdbc:jtds:sqlserver://localhostdatabase," +
                        "jdbc:jtds:sqlserver:database");
                }
            };

            test.test();
        }
    }
}

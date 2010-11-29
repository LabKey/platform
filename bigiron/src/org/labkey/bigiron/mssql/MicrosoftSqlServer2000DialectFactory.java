/*
 * Copyright (c) 2010 LabKey Corporation
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
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.dialect.AbstractDialectRetrievalTestCase;
import org.labkey.api.data.dialect.JdbcHelper;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.SqlDialectFactory;
import org.labkey.api.data.dialect.TestUpgradeCode;

import javax.servlet.ServletException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;

/*
* User: adam
* Date: Nov 26, 2010
* Time: 9:51:40 PM
*/
public class MicrosoftSqlServer2000DialectFactory extends SqlDialectFactory
{
    private static final Logger _log = Logger.getLogger(MicrosoftSqlServer2000DialectFactory.class);

    @Override
    public boolean claimsDriverClassName(String driverClassName)
    {
        return "net.sourceforge.jtds.jdbc.Driver".equals(driverClassName);
    }

    @Override
    public boolean claimsProductNameAndVersion(String dataBaseProductName, int databaseMajorVersion, int databaseMinorVersion, String jdbcDriverVersion, boolean logWarnings)
    {
        boolean ret = dataBaseProductName.equals("Microsoft SQL Server") && (databaseMajorVersion < 9);

        if (ret && logWarnings)
            _log.warn("Support for Microsoft SQL Server 2000 has been deprecated. Please upgrade to version 2005 or later.");

        return ret;
    }

    @Override
    public SqlDialect create()
    {
        return new MicrosoftSqlServer2000Dialect();
    }

    @Override
    public Collection<? extends Class> getJUnitTests()
    {
        return Arrays.asList(DialectRetrievalTestCase.class, JavaUpgradeCodeTestCase.class, JdbcHelperTestCase.class);
    }

    public static class DialectRetrievalTestCase extends AbstractDialectRetrievalTestCase
    {
        public void testDialectRetrieval()
        {
            // These should result in bad database exception
            badProductName("Gobbledygood", 1.0, 12.0, "");
            badProductName("SQL Server", 1.0, 12.0, "");
            badProductName("sqlserver", 1.0, 12.0, "");

            // < 9.0 should result in MicrosoftSqlServer2000Dialect -- no bad versions at the moment
            good("Microsoft SQL Server", 0.0, 8.9, "", MicrosoftSqlServer2000Dialect.class);

            // >= 9.0 should result in MicrosoftSqlServer2005Dialect
            good("Microsoft SQL Server", 9.0, 11.0, "", MicrosoftSqlServer2005Dialect.class);
        }
    }

    public static class JavaUpgradeCodeTestCase extends Assert
    {
        @Test
        public void testJavaUpgradeCode()
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
                            "EXEC core.executeJaavUpgradeCode 'upgradeCode'\n" +              // Misspell executeJavaUpgradeCode
                            "EXEC core.executeJavaUpgradeCode 'upgradeCode';;\n" +            // Bad syntax: two semicolons
                            "EXEC core.executeJavaUpgradeCode('upgradeCode')\n";              // Bad syntax: Parentheses

            try
            {
                SqlDialect dialect = new MicrosoftSqlServer2000Dialect();
                TestUpgradeCode good = new TestUpgradeCode();
                dialect.runSql(null, goodSql, good, null);
                assertEquals(10, good.getCounter());

                TestUpgradeCode bad = new TestUpgradeCode();
                dialect.runSql(null, badSql, bad, null);
                assertEquals(0, bad.getCounter());
            }
            catch (SQLException e)
            {
                fail("SQL Exception running test: " + e.getMessage());
            }
        }
    }

    public static class JdbcHelperTestCase extends Assert
    {
        @Test
        public void testJdbcHelper()
        {
            SqlDialect dialect = new MicrosoftSqlServer2000Dialect();
            JdbcHelper helper = dialect.getJdbcHelper();

            try
            {
                String goodUrls = "jdbc:jtds:sqlserver://localhost/database\n" +
                        "jdbc:jtds:sqlserver://localhost:1433/database\n" +
                        "jdbc:jtds:sqlserver://localhost/database;SelectMethod=cursor\n" +
                        "jdbc:jtds:sqlserver://localhost:1433/database;SelectMethod=cursor\n" +
                        "jdbc:jtds:sqlserver://www.host.com/database\n" +
                        "jdbc:jtds:sqlserver://www.host.com:1433/database\n" +
                        "jdbc:jtds:sqlserver://www.host.com/database;SelectMethod=cursor\n" +
                        "jdbc:jtds:sqlserver://www.host.com:1433/database;SelectMethod=cursor";

                for (String url : goodUrls.split("\n"))
                    assertEquals(helper.getDatabase(url), "database");
            }
            catch (Exception e)
            {
                fail("Exception running JdbcHelper test: " + e.getMessage());
            }

            String badUrls = "jdb:jtds:sqlserver://localhost/database\n" +
                    "jdbc:jts:sqlserver://localhost/database\n" +
                    "jdbc:jtds:sqlerver://localhost/database\n" +
                    "jdbc:jtds:sqlserver://localhostdatabase\n" +
                    "jdbc:jtds:sqlserver:database";

            for (String url : badUrls.split("\n"))
            {
                try
                {
                    if (helper.getDatabase(url).equals("database"))
                        fail("JdbcHelper test failed: database in " + url + " should not have resolved to 'database'");
                }
                catch (ServletException e)
                {
                    // Skip -- we expect to fail on some of these
                }
            }
        }
    }
}

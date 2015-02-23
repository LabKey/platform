/*
 * Copyright (c) 2010-2015 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.TableInfo;
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
* Time: 9:51:40 PM
*/
public class MicrosoftSqlServerDialectFactory extends SqlDialectFactory
{
    private String getProductName()
    {
        return "Microsoft SQL Server";
    }

    @Override
    public @Nullable SqlDialect createFromDriverClassName(String driverClassName)
    {
        switch (driverClassName)
        {
            case "net.sourceforge.jtds.jdbc.Driver":
            case "com.microsoft.sqlserver.jdbc.SQLServerDriver":
                return new MicrosoftSqlServer2008R2Dialect();
            default:
                return null;
        }
    }

    private final String _recommended = getProductName() + " 2014 is the recommended version.";

    @Override
    public @Nullable SqlDialect createFromProductNameAndVersion(String dataBaseProductName, String databaseProductVersion, String jdbcDriverVersion, boolean logWarnings) throws DatabaseNotSupportedException
    {
        if (!dataBaseProductName.equals(getProductName()))
            return null;

        VersionNumber versionNumber = new VersionNumber(databaseProductVersion);
        int version = versionNumber.getVersionInt();

        // Get the appropriate dialect and stash version information
        SqlDialect dialect = getDialect(version, databaseProductVersion, logWarnings);
        dialect.setDatabaseVersion(version);
        String className = dialect.getClass().getSimpleName();
        dialect.setProductVersion(className.substring(18, className.indexOf("Dialect")));

        return dialect;
    }

    private SqlDialect getDialect(int version, String databaseProductVersion, boolean logWarnings)
    {
        // Good resources for past & current SQL Server version numbers:
        // - http://www.sqlteam.com/article/sql-server-versions
        // - http://sqlserverbuilds.blogspot.se/

        // As of 13.1, we support only 2008 R2 and higher
        if (version >= 105)
        {
            if (version >= 120)
                return new MicrosoftSqlServer2014Dialect();

            if (version >= 110)
                return new MicrosoftSqlServer2012Dialect();

            if (version >= 105)
                return new MicrosoftSqlServer2008R2Dialect();
        }

        throw new DatabaseNotSupportedException(getProductName() + " version " + databaseProductVersion + " is not supported.");
    }

    @Override
    public Collection<? extends Class> getJUnitTests()
    {
        return Arrays.asList(DialectRetrievalTestCase.class, InlineProcedureTestCase.class, JdbcHelperTestCase.class, SynonymTestCase.class);
    }

    @Override
    public Collection<? extends SqlDialect> getDialectsToTest()
    {
        // The SQL Server dialects are identical, so just test one
        return PageFlowUtil.set(new MicrosoftSqlServer2008R2Dialect());
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
            badVersion("Microsoft SQL Server", 0.0, 10.5, null);

            // >= 10.5 and < 11.0 should result in MicrosoftSqlServer2008R2Dialect
            good("Microsoft SQL Server", 10.5, 11.0, "", MicrosoftSqlServer2008R2Dialect.class);

            // >= 11.0 and < 12.0 should result in MicrosoftSqlServer2012Dialect
            good("Microsoft SQL Server", 11.0, 12.0, "", MicrosoftSqlServer2012Dialect.class);

            // >= 12.0 should result in MicrosoftSqlServer2014Dialect
            good("Microsoft SQL Server", 12.0, 14.0, "", MicrosoftSqlServer2014Dialect.class);
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

            SqlDialect dialect = new MicrosoftSqlServer2008R2Dialect();
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
                    return new MicrosoftSqlServer2008R2Dialect();
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

    public static class SynonymTestCase extends Assert
    {
        @Test
        public void testSynonyms()
        {
            if (CoreSchema.getInstance().getSqlDialect().isSqlServer())
            {
                DbSchema schema = DbSchema.get("test", DbSchemaType.Bare);

                TableInfo containerAliases2 = schema.getTable("ContainerAliases2");
                assertEquals(containerAliases2.getTableType(), DatabaseTableType.TABLE);
                ColumnInfo containerId = containerAliases2.getColumn("ContainerId");
                ForeignKey containerIdFk = containerId.getFk();
                assertEquals("test", containerIdFk.getLookupSchemaName());
                assertEquals("Containers2", containerIdFk.getLookupTableName());

                TableInfo containers2 = schema.getTable("Containers2");
                assertEquals(containers2.getTableType(), DatabaseTableType.TABLE);
                ColumnInfo parent = containers2.getColumn("Parent");
                ForeignKey parentFk = parent.getFk();
                assertEquals("test", parentFk.getLookupSchemaName());
                assertEquals("Containers2", parentFk.getLookupTableName());
                assertEquals(containers2.getColumn("Searchable").getJdbcType(), JdbcType.BOOLEAN);
                assertEquals(containers2.getColumn("Type").getJdbcType(), JdbcType.VARCHAR);
                assertFalse(containers2.getColumn("Searchable").isNullable());
                assertFalse(containers2.getColumn("Type").isNullable());

                TableInfo testTable3 = schema.getTable("TestTable3");
                assertEquals(testTable3.getTableType(), DatabaseTableType.TABLE);
                assertEquals(1, testTable3.getPkColumns().size());
                assertEquals("RowId", testTable3.getPkColumns().get(0).getName());
                assertEquals(testTable3.getColumn("IntNull").getJdbcType(), JdbcType.INTEGER);
                assertEquals(testTable3.getColumn("IntNotNull").getJdbcType(), JdbcType.INTEGER);
                assertTrue(testTable3.getColumn("IntNull").isNullable());
                assertFalse(testTable3.getColumn("IntNotNull").isNullable());
                assertEquals(testTable3.getColumn("BitNull").getJdbcType(), JdbcType.BOOLEAN);
                assertEquals(testTable3.getColumn("BitNotNull").getJdbcType(), JdbcType.BOOLEAN);
                assertTrue(testTable3.getColumn("BitNull").isNullable());
                assertFalse(testTable3.getColumn("BitNotNull").isNullable());

                TableInfo users2 = schema.getTable("Users2");
                assertEquals(users2.getTableType(), DatabaseTableType.VIEW);
                assertEquals(users2.getColumn("Active").getJdbcType(), JdbcType.BOOLEAN);
                assertEquals(users2.getColumn("UserId").getJdbcType(), JdbcType.INTEGER);
                assertEquals(users2.getColumn("Mobile").getJdbcType(), JdbcType.VARCHAR);
                assertFalse(users2.getColumn("UserId").isNullable());
                assertFalse(users2.getColumn("DisplayName").isNullable());
                assertFalse(users2.getColumn("Active").isNullable());

                // Make sure the bare schema doesn't apply test.xml
                assertNull(testTable3.getDescription());

                // Make sure the module schema applies test.xml
                DbSchema moduleSchema = DbSchema.get("test");
                assertEquals("This is used to test synonyms on SQL Server", moduleSchema.getTable("TestTable3").getDescription());
            }
        }
    }
}

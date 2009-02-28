/*
 * Copyright (c) 2005-2009 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.util.CaseInsensitiveHashSet;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.data.*;

import javax.servlet.ServletException;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

import junit.framework.TestCase;
import junit.framework.TestSuite;


/**
 * User: arauch
 * Date: Dec 28, 2004
 * Time: 8:58:25 AM
 */

// Dialect specifics for Microsoft SQL Server
public class SqlDialectMicrosoftSQLServer extends SqlDialect
{
    protected String _tempTablePrefix = "##";

    public SqlDialectMicrosoftSQLServer()
    {
        reservedWordSet = new CaseInsensitiveHashSet(PageFlowUtil.set(
            "ADD", "ALL", "ALTER", "AND", "ANY", "AS", "ASC", "AUTHORIZATION", "BACKUP", "BEGIN", "BETWEEN", "BREAK", "BROWSE", "BULK",
            "BY", "CASCADE", "CASE", "CHECK", "CHECKPOINT", "CLOSE", "CLUSTERED", "COALESCE", "COLLATE", "COLUMN", "COMMIT", "COMPUTE",
            "CONSTRAINT", "CONTAINS", "CONTAINSTABLE", "CONTINUE", "CONVERT", "CREATE", "CROSS", "CURRENT", "CURRENT_DATE",
            "CURRENT_TIME", "CURRENT_TIMESTAMP", "CURRENT_USER", "CURSOR", "DATABASE", "DBCC", "DEALLOCATE", "DECLARE", "DEFAULT",
            "DELETE", "DENY", "DESC", "DISK", "DISTINCT", "DISTRIBUTED", "DOUBLE", "DROP", "DUMMY", "DUMP", "ELSE", "END", "ERRLVL",
            "ESCAPE", "EXCEPT", "EXEC", "EXECUTE", "EXISTS", "EXIT", "FETCH", "FILE", "FILLFACTOR", "FOR", "FOREIGN", "FREETEXT",
            "FREETEXTTABLE", "FROM", "FULL", "FUNCTION", "GOTO", "GRANT", "GROUP", "HAVING", "HOLDLOCK", "IDENTITY", "IDENTITY_INSERT",
            "IDENTITYCOL", "IF", "IN", "INDEX", "INNER", "INSERT", "INTERSECT", "INTO", "IS", "JOIN", "KEY", "KILL", "LEFT", "LIKE",
            "LINENO", "LOAD", "NATIONAL", "NOCHECK", "NONCLUSTERED", "NOT", "NULL", "NULLIF", "OF", "OFF", "OFFSETS", "ON", "OPEN",
            "OPENDATASOURCE", "OPENQUERY", "OPENROWSET", "OPENXML", "OPTION", "OR", "ORDER", "OUTER", "OVER", "PERCENT", "PLAN",
            "PRECISION", "PRIMARY", "PRINT", "PROC", "PROCEDURE", "PUBLIC", "RAISERROR", "READ", "READTEXT", "RECONFIGURE",
            "REFERENCES", "REPLICATION", "RESTORE", "RESTRICT", "RETURN", "REVOKE", "RIGHT", "ROLLBACK", "ROWCOUNT", "ROWGUIDCOL",
            "RULE", "SAVE", "SCHEMA", "SELECT", "SESSION_USER", "SET", "SETUSER", "SHUTDOWN", "SOME", "STATISTICS", "SYSTEM_USER",
            "TABLE", "TEXTSIZE", "THEN", "TO", "TOP", "TRAN", "TRANSACTION", "TRIGGER", "TRUNCATE", "TSEQUAL", "UNION", "UNIQUE",
            "UPDATE", "UPDATETEXT", "USE", "USER", "VALUES", "VARYING", "VIEW", "WAITFOR", "WHEN", "WHERE", "WHILE", "WITH",
            "WRITETEXT"
        ));
    }

    protected void addSqlTypeNames(Map<String, Integer> sqlTypeNameMap)
    {
        //Added for SQL Server SBEAMS. Not official type in jdbc
        sqlTypeNameMap.put("INT", Types.INTEGER);
        sqlTypeNameMap.put("INT IDENTITY", Types.INTEGER);
        sqlTypeNameMap.put("DATETIME", Types.TIMESTAMP);
        sqlTypeNameMap.put("TEXT", Types.LONGVARCHAR);
        sqlTypeNameMap.put("NTEXT", Types.LONGVARCHAR);
        sqlTypeNameMap.put("NVARCHAR", Types.VARCHAR);
        sqlTypeNameMap.put("UNIQUEIDENTIFIER", Types.VARCHAR);
        sqlTypeNameMap.put("TIMESTAMP", Types.BINARY); // SQL SERVER type
    }

    protected void addSqlTypeInts(Map<Integer, String> sqlTypeIntMap)
    {
        sqlTypeIntMap.put(Types.BIT, "BIT");
        sqlTypeIntMap.put(Types.BOOLEAN, "BIT");
        sqlTypeIntMap.put(Types.CHAR, "NCHAR");
        sqlTypeIntMap.put(Types.LONGVARBINARY, "IMAGE");
        sqlTypeIntMap.put(Types.LONGVARCHAR, "NTEXT");
        sqlTypeIntMap.put(Types.VARCHAR, "NVARCHAR");
        sqlTypeIntMap.put(Types.TIMESTAMP, "DATETIME");
        sqlTypeIntMap.put(Types.DOUBLE, "FLOAT");
        sqlTypeIntMap.put(Types.FLOAT, "FLOAT");
    }

    protected boolean claimsDriverClassName(String driverClassName)
    {
        return "net.sourceforge.jtds.jdbc.Driver".equals(driverClassName);
    }

    protected boolean claimsProductNameAndVersion(String dataBaseProductName, int majorVersion, int minorVersion)
    {
        return dataBaseProductName.equals("Microsoft SQL Server") && (majorVersion < 9);
    }

    public boolean isSqlServer()
    {
        return true;
    }

    public boolean isPostgreSQL()
    {
        return false;
    }

    protected String getProductName()
    {
        return "Sql Server";
    }

    public String getSQLScriptPath(boolean source)
    {
        //TODO: change this once we adjust the source tree to match the built module structure
        return source ? "sql server" : "sqlserver";
    }

    public String getDefaultDateTimeDatatype()
    {
        return "DATETIME";
    }

    public String getUniqueIdentType()
    {
        return "INT IDENTITY (1,1)";
    }


    @Override
    public void appendStatement(StringBuilder sql, String statement)
    {
        sql.append('\n');
        sql.append(statement);
    }


    protected void checkSqlScript(String lower, String lowerNoWhiteSpace, Collection<String> errors)
    {
    }


    @Override
    public void appendSelectAutoIncrement(StringBuilder sql, TableInfo tableName, String columnName)
    {
        appendStatement(sql, "SELECT @@IDENTITY");
    }


    @Override
    public SQLFragment limitRows(SQLFragment frag, int rowCount)
    {
        if (rowCount > 0)
        {
            String sql = frag.getSQL();
            if (!sql.substring(0, 6).equalsIgnoreCase("SELECT"))
                throw new IllegalArgumentException("ERROR: Limit SQL Doesn't Start with SELECT: " + sql);

            frag.insert(6, " TOP " + rowCount);
        }
        return frag;
    }

    @Override
    public SQLFragment limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, String order, int rowCount, long offset)
    {
        if (select == null)
            throw new IllegalArgumentException("select");
        if (from == null)
            throw new IllegalArgumentException("from");

        if (rowCount == 0 || (rowCount > 0 && offset == 0))
        {
            SQLFragment sql = new SQLFragment();
            sql.append(select);
            sql.append("\n").append(from);
            if (filter != null) sql.append("\n").append(filter);
            if (order != null) sql.append("\n").append(order);

            return limitRows(sql, rowCount);
        }
        else
        {
            return _limitRows(select, from, filter, order, rowCount, offset);
        }
    }

    @Override
    public boolean supportOffset()
    {
        return false;
    }

    protected SQLFragment _limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, String order, int rowCount, long offset)
    {
        throw new UnsupportedOperationException("limitRows() with an offset not supported in SQLServer 2000");
    }


    // Execute a stored procedure/function with the specified parameters
    public String execute(DbSchema schema, String procedureName, String parameters)
    {
        return "EXEC " + schema.getOwner() + "." + procedureName + " " + parameters;
    }


    public String getConcatenationOperator()
    {
        return "+";
    }


    public String getCharClassLikeOperator()
    {
        return "LIKE";
    }

    public String getCaseInsensitiveLikeOperator()
    {
        return "LIKE";
    }

    public String getVarcharLengthFunction()
    {
        return "len";
    }

    public String getStdDevFunction()
    {
        return "stdev";
    }

    public String getClobLengthFunction()
    {
        return "datalength";
    }

    public String getStringIndexOfFunction(String stringToFind, String stringToSearch)
    {
        return "patindex('%' + " + stringToFind + " + '%', " + stringToSearch + ")";
    }

    public String getSubstringFunction(String s, String start, String length)
    {
        return "substring(" + s + ", " + start + ", " + length + ")";
    }

    public String getTempTableKeyword()
    {
        return "";
    }

    // UNDONE: why ## instead of #?
    public String getTempTablePrefix()
    {
        return "##";
    }


    public String getGlobalTempTablePrefix()
    {
        return "tempdb..";
    }


    public boolean isNoDatabaseException(SQLException e)
    {
        return "S1000".equals(e.getSQLState());
    }

    public boolean isSortableDataType(String sqlDataTypeName)
    {
        return !("text".equalsIgnoreCase(sqlDataTypeName) ||
            "ntext".equalsIgnoreCase(sqlDataTypeName) ||
            "image".equalsIgnoreCase(sqlDataTypeName));
    }

    public String getDropIndexCommand(String tableName, String indexName)
    {
        return "DROP INDEX " + tableName + "." + indexName;
    }

    public String getCreateDatabaseSql(String dbName)
    {
        return "CREATE DATABASE " + dbName;
    }

    // Do nothing
    public void prepareNewDbSchema(DbSchema schema)
    {
    }

    public String getCreateSchemaSql(String schemaName)
    {
        return "EXEC sp_addapprole '" + schemaName + "', 'password'";
    }

    public String getDateDiff(int part, String value1, String value2)
    {
        String partName;
        switch (part)
        {
            case Calendar.DATE:
            {
                partName = "day";
                break;
            }
            case Calendar.HOUR:
            {
                partName = "hour";
                break;
            }
            case Calendar.MINUTE:
            {
                partName = "minute";
                break;
            }
            case Calendar.SECOND:
            {
                partName = "second";
                break;
            }
            default:
            {
                throw new IllegalArgumentException("Unsupported time unit: " + part);
            }
        }
        return "DATEDIFF(" + partName + ", " + value2 + ", " + value1 + ")";
    }

    public String getDateTimeToDateCast(String expression)
    {
        return "convert(datetime, convert(varchar, (" + expression + "), 101))";
    }

    public String getRoundFunction(String valueToRound)
    {
        return "ROUND(" + valueToRound + ", 0)";
    }


    protected String getSystemTableNames()
    {
        return "dtproperties,sysconstraints,syssegments";
    }


    public String getColumnSelectName(String columnName)
    {
        if (reservedWordSet.contains(columnName))
            return "\"" + columnName + "\"";    // SQL Server wants quotes around column names that are key words
        else
            return columnName;
    }

    public String getTableSelectName(String tableName)
    {
        return getColumnSelectName(tableName);  // Same as column names
    }

    public String getOwnerSelectName(String ownerName)
    {
        return getColumnSelectName(ownerName);  // Same as column names
    }

    public String sanitizeException(SQLException ex)
    {
        if ("01004".equals(ex.getSQLState()))
        {
            return INPUT_TOO_LONG_ERROR_MESSAGE;
        }
        return GENERIC_ERROR_MESSAGE;
    }

    public String getAnalyzeCommandForTable(String tableName)
    {
        return "UPDATE STATISTICS " + tableName + ";";
    }

    protected String getSIDQuery()
    {
        return "SELECT @@spid";
    }

    public String getBooleanDatatype()
    {
        return "BIT";
    }

    /**
     * Wrap one or more INSERT statements to allow explicit specification
     * of values for autoincrementing columns (e.g. IDENTITY in SQL Server
     * or SERIAL in Postgres). The input StringBuffer is modified.
     *
     * @param statements the insert statements. If more than one,
     *                   they must have been joined by appendStatement
     *                   and must all refer to the same table.
     * @param tinfo      table used in the insert(s)
     */
    public void overrideAutoIncrement(StringBuilder statements, TableInfo tinfo)
    {
        statements.insert(0, "SET IDENTITY_INSERT " + tinfo + " ON\n");
        statements.append("SET IDENTITY_INSERT ").append(tinfo).append(" OFF");
    }

    private static final Pattern GO_PATTERN = Pattern.compile("^\\s*GO\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern JAVA_CODE_PATTERN = Pattern.compile("^\\s*EXEC(?:UTE)*\\s+core\\.executeJavaUpgradeCode\\s*'(.+)'\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    public void runSql(DbSchema schema, String sql, UpgradeCode upgradeCode, ModuleContext moduleContext) throws SQLException
    {
        SqlScriptParser parser = new SqlScriptParser(sql, GO_PATTERN, JAVA_CODE_PATTERN, schema, upgradeCode, moduleContext);
        parser.execute();
    }

    public String getMasterDataBaseName()
    {
        return "master";
    }


    public JdbcHelper getJdbcHelper(String url) throws ServletException
    {
        return new JtdsJdbcHelper(url);
    }


    /*  jTDS example connection URLs we need to parse:

        jdbc:jtds:sqlserver://host:1433/database
        jdbc:jtds:sqlserver://host/database;SelectMethod=cursor

    */
    public static class JtdsJdbcHelper extends JdbcHelper
    {
        protected JtdsJdbcHelper(String url) throws ServletException
        {
            if (!url.startsWith("jdbc:jtds:sqlserver"))
                throw new ServletException("Unsupported connection url: " + url);

            int dbEnd = url.indexOf(';');
            if (-1 == dbEnd)
                dbEnd = url.length();
            int dbDelimiter = url.lastIndexOf('/', dbEnd);
            if (-1 == dbDelimiter)
                throw new ServletException("Invalid jTDS connection url: " + url);
            _database = url.substring(dbDelimiter + 1, dbEnd);
        }
    }

    public static class JdbcHelperTestCase extends TestCase
    {
        public JdbcHelperTestCase()
        {
            super("testJdbcHelper");
        }

        public void testJdbcHelper()
        {
            try
            {
                String goodUrls =   "jdbc:jtds:sqlserver://localhost/database\n" +
                                    "jdbc:jtds:sqlserver://localhost:1433/database\n" +
                                    "jdbc:jtds:sqlserver://localhost/database;SelectMethod=cursor\n" +
                                    "jdbc:jtds:sqlserver://localhost:1433/database;SelectMethod=cursor\n" +
                                    "jdbc:jtds:sqlserver://www.host.com/database\n" +
                                    "jdbc:jtds:sqlserver://www.host.com:1433/database\n" +
                                    "jdbc:jtds:sqlserver://www.host.com/database;SelectMethod=cursor\n" +
                                    "jdbc:jtds:sqlserver://www.host.com:1433/database;SelectMethod=cursor";

                for (String url : goodUrls.split("\n"))
                    assertEquals(new JtdsJdbcHelper(url).getDatabase(), "database");
            }
            catch(Exception e)
            {
                fail("Exception running JdbcHelper test: " + e.getMessage());
            }

            String badUrls =    "jdb:jtds:sqlserver://localhost/database\n" +
                                "jdbc:jts:sqlserver://localhost/database\n" +
                                "jdbc:jtds:sqlerver://localhost/database\n" +
                                "jdbc:jtds:sqlserver://localhostdatabase\n" +
                                "jdbc:jtds:sqlserver:database";

            for (String url : badUrls.split("\n"))
            {
                try
                {
                    if (new JtdsJdbcHelper(url).getDatabase().equals("database"))
                        fail("JdbcHelper test failed: database in " + url + " should not have resolved to 'database'");
                }
                catch (ServletException e)
                {
                    // Skip -- we expect to fail on some of these
                }
            }
        }
    }

    public SQLFragment sqlLocate(SQLFragment littleString, SQLFragment bigString)
    {
        SQLFragment ret = new SQLFragment("(CHARINDEX(");
        ret.append(littleString);
        ret.append(",");
        ret.append(bigString);
        ret.append("))");
        return ret;
    }

    public SQLFragment sqlLocate(SQLFragment littleString, SQLFragment bigString, SQLFragment startIndex)
    {
        SQLFragment ret = new SQLFragment("(CHARINDEX(");
        ret.append(littleString);
        ret.append(",");
        ret.append(bigString);
        ret.append(",");
        ret.append(startIndex);
        ret.append("))");
        return ret;
    }

    public boolean allowSortOnSubqueryWithoutLimit()
    {
        return false;
    }

    public void initializeConnection(Connection conn) throws SQLException
    {
        Statement stmt = conn.createStatement();
        stmt.execute("SET ARITHABORT ON");
        stmt.close();
    }

    public void purgeTempSchema(Map<String, TempTableTracker> createdTableNames)
    {
        // Do nothing -- SQL Server cleans up temp tables automatically
    }

    public boolean isCaseSensitive()
    {
        return false;
    }

    public ColumnMetaDataReader getColumnMetaDataReader(ResultSet rsCols)
    {
        return new SqlServerColumnMetaDataReader(rsCols);
    }

    private static class SqlServerColumnMetaDataReader extends ColumnMetaDataReader
    {
        private SqlServerColumnMetaDataReader(ResultSet rsCols)
        {
            super(rsCols);

            _nameKey = "COLUMN_NAME";
            _sqlTypeKey = "DATA_TYPE";
            _sqlTypeNameKey = "TYPE_NAME";
            _scaleKey = "COLUMN_SIZE";
            _nullableKey = "NULLABLE";
            _postionKey = "ORDINAL_POSITION";
        }

        public boolean isAutoIncrement() throws SQLException
        {
            return getSqlTypeName().equalsIgnoreCase("int identity");
        }
    }


    public PkMetaDataReader getPkMetaDataReader(ResultSet rs)
    {
        return new PkMetaDataReader(rs, "COLUMN_NAME", "KEY_SEQ");
    }

    /**
     * @return any additional information that should be sent to the mothership in the case of a SQLException
     */
    public String getExtraInfo(SQLException e)
    {
        // Deadlock between two different DB connections
        if ("40001".equals(e.getSQLState()))
        {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet())
            {
                Thread thread = entry.getKey();
                // Dump out any thread that was talking to the database
                Set<Integer> spids = ConnectionWrapper.getSPIDsForThread(thread);
                if (!spids.isEmpty())
                {
                    if (sb.length() == 0)
                    {
                        sb.append("Other threads with active database connections:\n");
                    }
                    else
                    {
                        sb.append("\n");
                    }
                    sb.append(thread.getName());
                    sb.append(", SPIDs = ");
                    sb.append(spids);
                    sb.append("\n");
                    for (StackTraceElement stackTraceElement : entry.getValue())
                    {
                        sb.append("\t");
                        sb.append(stackTraceElement);
                        sb.append("\n");
                    }
                }
            }
            return sb.length() > 0 ? sb.toString() : null;
        }
        return null;
    }


    public TestSuite getTestSuite()
    {
        TestSuite suite = new TestSuite();
        suite.addTest(new JavaUpgradeCodeTestCase());
        suite.addTest(new JdbcHelperTestCase());
        return suite;
    }



    public class JavaUpgradeCodeTestCase extends TestCase
    {
        public JavaUpgradeCodeTestCase()
        {
            super("testJavaUpgradeCode");
        }

        public void testJavaUpgradeCode()
        {
            String goodSql =
                "EXEC core.executeJavaUpgradeCode 'upgradeCode'\n" +                       // Normal
                "EXECUTE core.executeJavaUpgradeCode 'upgradeCode'\n" +                    // EXECUTE
                "execute core.executeJavaUpgradeCode'upgradeCode'\n" +                     // execute
                "    EXEC     core.executeJavaUpgradeCode    'upgradeCode'         \n" +   // Lots of whitespace
                "exec CORE.EXECUTEJAVAUPGRADECODE 'upgradeCode'\n" +                       // Case insensitive
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
                "EXEC core.executeJavaUpgradeCode 'upgradeCode';\n" +             // Bad syntax: semicolon
                "EXEC core.executeJavaUpgradeCode('upgradeCode')\n";              // Bad syntax: Parentheses

            try
            {
                TestUpgradeCode good = new TestUpgradeCode();
                runSql(null, goodSql, good, null);
                assertEquals(6, good.getCounter());

                TestUpgradeCode bad = new TestUpgradeCode();
                runSql(null, badSql, bad, null);
                assertEquals(0, bad.getCounter());
            }
            catch (SQLException e)
            {
                fail("SQL Exception running test: " + e.getMessage());
            }
        }
    }
}

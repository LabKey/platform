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

package org.labkey.core;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.*;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.AliasManager;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;

import javax.servlet.ServletException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Calendar;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * User: arauch
 * Date: Dec 28, 2004
 * Time: 8:58:25 AM
 */

// Dialect specifics for PostgreSQL
class SqlDialectPostgreSQL extends SqlDialect
{
    SqlDialectPostgreSQL()
    {
        reservedWordSet = new CaseInsensitiveHashSet(PageFlowUtil.set(
            "ALL", "ANALYSE", "ANALYZE", "AND", "ANY", "ARRAY", "AS", "ASC", "ASYMMETRIC",
            "AUTHORIZATION", "BETWEEN", "BINARY", "BOTH", "CASE", "CAST", "CHECK", "COLLATE", "COLUMN", "CONSTRAINT",
            "CREATE", "CROSS", "CURRENT_DATE", "CURRENT_ROLE", "CURRENT_TIME", "CURRENT_TIMESTAMP", "CURRENT_USER", "DEFAULT", "DEFERRABLE", "DESC",
            "DISTINCT", "DO", "ELSE", "END", "EXCEPT", "FALSE", "FOR", "FOREIGN", "FREEZE",
            "FROM", "FULL", "GRANT", "GROUP", "HAVING", "ILIKE", "IN", "INITIALLY", "INNER",
            "INTERSECT", "INTO", "IS", "ISNULL", "JOIN", "LEADING", "LEFT", "LIKE", "LIMIT",
            "LOCALTIME", "LOCALTIMESTAMP", "NATURAL", "NEW", "NOT", "NOTNULL", "NULL", "OFF", "OFFSET",
            "OLD", "ON", "ONLY", "OR", "ORDER", "OUTER", "OVERLAPS", "PLACING", "PRIMARY",
            "REFERENCES", "RIGHT", "SELECT", "SESSION_USER", "SIMILAR", "SOME", "SYMMETRIC", "TABLE", "THEN",
            "TO", "TRAILING", "TRUE", "UNION", "UNIQUE", "USER", "USING", "VERBOSE", "WHEN", "WHERE"
        ));
    }

    protected void addSqlTypeNames(Map<String, Integer> sqlTypeNameMap)
    {
        //Added for PostgreSQL, which returns type names like "userid," not underlying type name
        sqlTypeNameMap.put("USERID", Types.INTEGER);
        sqlTypeNameMap.put("SERIAL", Types.INTEGER);
        sqlTypeNameMap.put("ENTITYID", Types.VARCHAR);
        sqlTypeNameMap.put("INT2", Types.INTEGER);
        sqlTypeNameMap.put("INT4", Types.INTEGER);
        sqlTypeNameMap.put("INT8", Types.BIGINT);
        sqlTypeNameMap.put("FLOAT4", Types.REAL);
        sqlTypeNameMap.put("FLOAT8", Types.DOUBLE);
        sqlTypeNameMap.put("BOOL", Types.BOOLEAN);
        sqlTypeNameMap.put("BPCHAR", Types.CHAR);
        sqlTypeNameMap.put("LSIDTYPE", Types.VARCHAR);
        sqlTypeNameMap.put("TIMESTAMP", Types.TIMESTAMP);
    }

    protected void addSqlTypeInts(Map<Integer, String> sqlTypeIntMap)
    {
        sqlTypeIntMap.put(Types.BIT, "BOOLEAN");
        sqlTypeIntMap.put(Types.BOOLEAN, "BOOLEAN");
        sqlTypeIntMap.put(Types.CHAR, "CHAR");
        sqlTypeIntMap.put(Types.LONGVARBINARY, "LONGVARBINARY");
        sqlTypeIntMap.put(Types.LONGVARCHAR, "LONGVARCHAR");
        sqlTypeIntMap.put(Types.VARCHAR, "VARCHAR");
        sqlTypeIntMap.put(Types.TIMESTAMP, "TIMESTAMP");
        sqlTypeIntMap.put(Types.DOUBLE, "DOUBLE PRECISION");
        sqlTypeIntMap.put(Types.FLOAT, "DOUBLE PRECISION");
    }

    protected boolean claimsDriverClassName(String driverClassName)
    {
        return "org.postgresql.Driver".equals(driverClassName);
    }

    protected boolean claimsProductNameAndVersion(String dataBaseProductName, int databaseMajorVersion, int databaseMinorVersion, String jdbcDriverVersion, boolean logWarnings) throws DatabaseNotSupportedException
    {
        if (!getProductName().equals(dataBaseProductName))
            return false;

        int version = databaseMajorVersion * 10 + databaseMinorVersion;   // 8.2 => 82, 8.3 => 83, 8.4 => 84, etc.

        // Version 8.2 or greater is allowed...
        if (version >= 82)
        {
            // ...but warn for anything greater than 8.4
            if (logWarnings && version > 84)
                _log.warn("LabKey Server has not been tested against " + getProductName() + " version " + databaseMajorVersion + "." + databaseMinorVersion + ".  PostgreSQL 8.4 is the recommended version.");

            return true;
        }

        throw new DatabaseNotSupportedException(getProductName() + " version " + databaseMajorVersion + "." + databaseMinorVersion + " is not supported.  You must upgrade your database server installation to " + getProductName() + " version 8.2 or greater.");
    }

    public boolean isSqlServer()
    {
        return false;
    }

    public boolean isPostgreSQL()
    {
        return true;
    }

    protected String getProductName()
    {
        return "PostgreSQL";
    }

    public String getSQLScriptPath()
    {
        return "postgresql";
    }

    public String getDefaultDateTimeDatatype()
    {
        return "TIMESTAMP";
    }

    public String getUniqueIdentType()
    {
        return "SERIAL";
    }

    @Override
    public void appendStatement(StringBuilder sql, String statement)
    {
        sql.append(";\n");
        sql.append(statement);
    }


    @Override
    public void appendSelectAutoIncrement(StringBuilder sql, TableInfo table, String columnName)
    {
        if (null == table.getSequence())
            appendStatement(sql, "SELECT CURRVAL('" + table.toString() + "_" + columnName + "_seq')");
        else
            appendStatement(sql, "SELECT CURRVAL('" + table.getSequence() + "')");
    }

    @Override
    public SQLFragment limitRows(SQLFragment frag, int rowCount)
    {
        return limitRows(frag, rowCount, 0);
    }

    private SQLFragment limitRows(SQLFragment frag, int rowCount, long offset)
    {
        if (rowCount > 0)
        {
            frag.append("\nLIMIT ");
            frag.append(Integer.toString(rowCount));

            if (offset > 0)
            {
                frag.append(" OFFSET ");
                frag.append(Long.toString(offset));
            }
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

        SQLFragment sql = new SQLFragment();
        sql.append(select);
        sql.append("\n").append(from);
        if (filter != null) sql.append("\n").append(filter);
        if (order != null) sql.append("\n").append(order);

        return limitRows(sql, rowCount, offset);
    }

    @Override
    public boolean supportsOffset()
    {
        return true;
    }

    public boolean supportsComments()
    {
        return true;
    }

    public String execute(DbSchema schema, String procedureName, String parameters)
    {
        return "SELECT " + schema.getOwner() + "." + procedureName + "(" + parameters + ")";
    }


    public String getConcatenationOperator()
    {
        return "||";
    }


    public String getCharClassLikeOperator()
    {
        return "SIMILAR TO";
    }

    public String getCaseInsensitiveLikeOperator()
    {
        return "ILIKE";
    }

    public String getVarcharLengthFunction()
    {
        return "length";
    }

    public String getStdDevFunction()
    {
        return "stddev";
    }

    public String getClobLengthFunction()
    {
        return "length";
    }

    public String getStringIndexOfFunction(String stringToFind, String stringToSearch)
    {
        return "position(" + stringToFind + " in " + stringToSearch + ")";
    }

    public String getSubstringFunction(String s, String start, String length)
    {
        return "substr(" + s + ", " + start + ", " + length + ")"; 
    }

    protected String getSystemTableNames()
    {
        return "pg_logdir_ls";
    }

    public String sanitizeException(SQLException ex)
    {
        if ("22001".equals(ex.getSQLState()))
        {
            return INPUT_TOO_LONG_ERROR_MESSAGE;
        }
        return GENERIC_ERROR_MESSAGE;
    }

    public String getAnalyzeCommandForTable(String tableName)
    {
        return "ANALYZE " + tableName;
    }

    protected String getSIDQuery()
    {
        return "SELECT pg_backend_pid();";
    }

    public String getBooleanDatatype()
    {
        return "BOOLEAN";
    }

    @Override
    public String getBooleanTRUE()
    {
        return "true";
    }

    @Override
    public String getBooleanFALSE()
    {
        return "false";
    }

    @Override
    public String getBooleanLiteral(boolean b)
    {
        return Boolean.toString(b);
    }

    public String getTempTableKeyword()
    {
        return "TEMPORARY";
    }

    public String getTempTablePrefix()
    {
        return "";
    }

    public String getGlobalTempTablePrefix()
    {
        return "temp.";
    }

    public boolean isNoDatabaseException(SQLException e)
    {
        return "3D000".equals(e.getSQLState());
    }

    public boolean isSortableDataType(String sqlDataTypeName)
    {
        return true;
    }

    public String getDropIndexCommand(String tableName, String indexName)
    {
        return "DROP INDEX " + indexName;
    }

    public String getCreateDatabaseSql(String dbName)
    {
        return "CREATE DATABASE \"" + dbName + "\" WITH ENCODING 'UTF8';\n" +
                "ALTER DATABASE \"" + dbName + "\" SET default_with_oids TO OFF";
    }

    public String getCreateSchemaSql(String schemaName)
    {
        if (!AliasManager.isLegalName(schemaName) || reservedWordSet.contains(schemaName))
            throw new IllegalArgumentException("Not a legal schema name: " + schemaName);
        //Quoted schema names are bad news
        return "CREATE SCHEMA " + schemaName;
    }

    public String getDateDiff(int part, String value1, String value2)
    {
        int divideBy;
        switch (part)
        {
            case Calendar.DATE:
            {
                divideBy = 60 * 60 * 24;
                break;
            }
            case Calendar.HOUR:
            {
                divideBy = 60 * 60;
                break;
            }
            case Calendar.MINUTE:
            {
                divideBy = 60;
                break;
            }
            case Calendar.SECOND:
            {
                divideBy = 1;
                break;
            }
            default:
            {
                throw new IllegalArgumentException("Unsupported time unit: " + part);
            }
        }
        return "(EXTRACT(EPOCH FROM (" + value1 + " - " + value2 + ")) / " + divideBy + ")::INT";
    }

    public String getDateTimeToDateCast(String columnName)
    {
        return "DATE(" + columnName + ")";
    }

    public String getRoundFunction(String valueToRound)
    {
        return "ROUND(" + valueToRound + "::double precision)";
    }

    public boolean supportsRoundDouble()
    {
        return false;
    }

    public void handleCreateDatabaseException(SQLException e) throws ServletException
    {
        if ("55006".equals(e.getSQLState()))
        {
            _log.error("You must close down pgAdmin III and all other applications accessing PostgreSQL.");
            throw(new ServletException("Close down or disconnect pgAdmin III and all other applications accessing PostgreSQL", e));
        }
        else
        {
            super.handleCreateDatabaseException(e);
        }
    }


    // Make sure that the PL/pgSQL language is enabled in the associated database.  If not, throw.  It would be nice
    // to CREATE LANGUAGE at this point, however, that requires SUPERUSER permissions and takes us down the path of
    // creating call handlers and other complexities.  It looks like PostgreSQL 8.1 has a simpler form of CREATE LANGUAGE...
    // once we require 8.1 we should consider using it here.
    public void prepareNewDatabase(DbSchema schema) throws ServletException
    {
        ResultSet rs = null;

        try
        {
            rs = Table.executeQuery(schema, "SELECT * FROM pg_language WHERE lanname = 'plpgsql'", null);

            if (rs.next())
                return;

            String dbName = schema.getScope().getDatabaseName();
            String message = "PL/pgSQL is not enabled in the \"" + dbName + "\" database because it is not enabled in your Template1 master database.  Use PostgreSQL's 'createlang' command line utility to enable PL/pgSQL in the \"" + dbName + "\" database then restart Tomcat.";
            _log.error(message);
            throw new ServletException(message);
        }
        catch(SQLException e)
        {
             _log.error("Exception attempting to verify PL/pgSQL", e);
             throw new ServletException("Failure attempting to verify language PL/pgSQL", e);
        }
        finally
        {
            try {if (null != rs) rs.close();} catch(SQLException e) {_log.error("prepareNewDatabase", e);}
        }
    }


    // Do dialect-specific work after schema load, if necessary
    public void prepareNewDbSchema(DbSchema schema)
    {
        ResultSet rsSeq = null;
        try
        {
            // MAB: hacky, let's hope there is some system function that does the same thing
            rsSeq = Table.executeQuery(schema,
                    "SELECT relname, attname, adsrc\n" +
                            "FROM pg_attrdef \n" +
                            "\tJOIN pg_attribute ON pg_attrdef.adnum = pg_attribute.attnum AND pg_attrdef.adrelid=pg_attribute.attrelid \n" +
                            "\tJOIN pg_class on pg_attribute.attrelid=pg_class.oid \n" +
                            "\tJOIN pg_namespace ON pg_class.relnamespace=pg_namespace.oid\n" +
                            "WHERE nspname=?",
                    new Object[]{schema.getOwner()});

            while (rsSeq.next())
            {
                SchemaTableInfo t = schema.getTable(rsSeq.getString("relname"));
                if (null == t) continue;
                ColumnInfo c = t.getColumn(rsSeq.getString("attname"));
                if (null == c) continue;
                if (!c.isAutoIncrement())
                    continue;
                String src = rsSeq.getString("adsrc");
                int start = src.indexOf('\'');
                int end = src.lastIndexOf('\'');
                if (end > start)
                {
                    String sequence = src.substring(start + 1, end);
                    if (!sequence.toLowerCase().startsWith(schema.getOwner().toLowerCase() + "."))
                        sequence = schema.getOwner() + "." + sequence;
                    t.setSequence(sequence);
                }
            }
        }
        catch (Exception x)
        {
            _log.error("Error trying to find auto-increment sequences", x);
        }
        finally
        {
            ResultSetUtil.close(rsSeq);
        }
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
        // Nothing special to do for the Postgres dialect
    }


    private static final Pattern JAVA_CODE_PATTERN = Pattern.compile("^\\s*SELECT\\s+core\\.executeJavaUpgradeCode\\s*\\(\\s*'(.+)'\\s*\\)\\s*;\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    public void runSql(DbSchema schema, String sql, UpgradeCode upgradeCode, ModuleContext moduleContext) throws SQLException
    {
        SqlScriptParser parser = new SqlScriptParser(sql, null, JAVA_CODE_PATTERN, schema, upgradeCode, moduleContext);
        parser.execute();
    }

    protected void checkSqlScript(String lower, String lowerNoWhiteSpace, Collection<String> errors)
    {
        if (lowerNoWhiteSpace.contains("setsearch_pathto"))
            errors.add("Do not use \"SET search_path TO <schema>\".  Instead, schema-qualify references to all objects.");

        if (!lowerNoWhiteSpace.endsWith(";"))
            errors.add("Script must end with a semicolon");
    }


    public String getMasterDataBaseName()
    {
        return "template1";
    }


    public JdbcHelper getJdbcHelper(String url) throws ServletException
    {
        return new PostgreSQLJdbcHelper(url);
    }


    /*  PostgreSQL example connection URLs we need to parse:

        jdbc:postgresql:database
        jdbc:postgresql://host/database
        jdbc:postgresql://host:port/database
        jdbc:postgresql:database?user=fred&password=secret&ssl=true
        jdbc:postgresql://host/database?user=fred&password=secret&ssl=true
        jdbc:postgresql://host:port/database?user=fred&password=secret&ssl=true

    */
    public static class PostgreSQLJdbcHelper extends JdbcHelper
    {
        protected PostgreSQLJdbcHelper(String url) throws ServletException
        {
            if (!url.startsWith("jdbc:postgresql"))
                throw new ServletException("Unsupported connection url: " + url);

            int dbEnd = url.indexOf('?');
            if (-1 == dbEnd)
                dbEnd = url.length();
            int dbDelimiter = url.lastIndexOf('/', dbEnd);
            if (-1 == dbDelimiter)
                dbDelimiter = url.lastIndexOf(':', dbEnd);
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
                String goodUrls =   "jdbc:postgresql:database\n" +
                                    "jdbc:postgresql://localhost/database\n" +
                                    "jdbc:postgresql://localhost:8300/database\n" +
                                    "jdbc:postgresql://www.host.com/database\n" +
                                    "jdbc:postgresql://www.host.com:8499/database\n" +
                                    "jdbc:postgresql:database?user=fred&password=secret&ssl=true\n" +
                                    "jdbc:postgresql://localhost/database?user=fred&password=secret&ssl=true\n" +
                                    "jdbc:postgresql://localhost:8672/database?user=fred&password=secret&ssl=true\n" +
                                    "jdbc:postgresql://www.host.com/database?user=fred&password=secret&ssl=true\n" +
                                    "jdbc:postgresql://www.host.com:8992/database?user=fred&password=secret&ssl=true";

                for (String url : goodUrls.split("\n"))
                    assertEquals(new PostgreSQLJdbcHelper(url).getDatabase(), "database");
            }
            catch(Exception e)
            {
                fail("Exception running JdbcHelper test: " + e.getMessage());
            }

            String badUrls =    "jddc:postgresql:database\n" +
                                "jdbc:postgres://localhost/database\n" +
                                "jdbc:postgresql://www.host.comdatabase";

            for (String url : badUrls.split("\n"))
            {
                try
                {
                    if (new PostgreSQLJdbcHelper(url).getDatabase().equals("database"))
                        fail("JdbcHelper test failed: database in " + url + " should not have resolved to 'database'");
                }
                catch (ServletException e)
                {
                    // Skip -- we expect to fail on these
                }
            }
        }
    }

    protected String getDatabaseMaintenanceSql()
    {
        return "VACUUM ANALYZE;";
    }

    public SQLFragment sqlLocate(SQLFragment littleString, SQLFragment bigString)
    {
        SQLFragment ret = new SQLFragment(" POSITION(");
        ret.append(littleString);
        ret.append(" IN ");
        ret.append(bigString);
        ret.append(") ");
        return ret;
    }

    public SQLFragment sqlLocate(SQLFragment littleString, SQLFragment bigString, SQLFragment startIndex)
    {
        SQLFragment tmp = new SQLFragment("position(");
        tmp.append(littleString);
        tmp.append(" in substring(");
        tmp.append(bigString);
        tmp.append(" from ");
        tmp.append(startIndex);
        tmp.append("))");
        SQLFragment ret = new SQLFragment("((");
        ret.append(startIndex);
        // TODO: code review this: I believe that this -1 is necessary to produce the correct results.
        ret.append(" - 1)");
        ret.append(" * sign(");
        ret.append(tmp);
        ret.append(")+");
        ret.append(tmp);
        ret.append(")");
        return ret;
    }

    public boolean allowSortOnSubqueryWithoutLimit()
    {
        return true;
    }

    static final private Pattern s_patStringLiteral = Pattern.compile("\\'([^\\\\\\']|(\\'\\')|(\\\\.))*\\'");
    protected Pattern patStringLiteral()
    {
        return s_patStringLiteral;
    }

    protected String quoteStringLiteral(String str)
    {
        return "'" + StringUtils.replace(StringUtils.replace(str, "\\", "\\\\"), "'", "''") + "'";
    }

    public void initializeConnection(Connection conn) throws SQLException
    {
    }

    public void purgeTempSchema(Map<String, TempTableTracker> createdTableNames)
    {
        try
        {
            DbSchema coreSchema = CoreSchema.getInstance().getSchema();
            DbScope scope = coreSchema.getScope();
            String tempSchemaName = getGlobalTempTablePrefix();
            if (tempSchemaName.endsWith("."))
                tempSchemaName = tempSchemaName.substring(0, tempSchemaName.length()-1);
            String dbName = getDatabaseName(scope.getDataSourceName(), scope.getDataSource());

            Connection conn = null;
            ResultSet rs = null;
            Object noref = new Object();
            try
            {
                conn = scope.getConnection();
                rs = conn.getMetaData().getTables(dbName, tempSchemaName, "%", new String[] {"TABLE"});
                while (rs.next())
                {
                    String table = rs.getString("TABLE_NAME");
                    String tempName = getGlobalTempTablePrefix() + table;
                    if (!createdTableNames.containsKey(tempName))
                        TempTableTracker.track(coreSchema, tempName, noref);
                }
            }
            finally
            {
                ResultSetUtil.close(rs);
                if (null != conn)
                    scope.releaseConnection(conn);
            }
        }
        catch (SQLException x)
        {
            _log.warn("error cleaning up temp schema", x);
        }
        catch (ServletException x)
        {
            _log.warn("error cleaning up temp schema", x);
        }
    }

    public boolean isCaseSensitive()
    {
        return true;
    }

    public boolean isEditable()
    {
        return true;
    }

    public ColumnMetaDataReader getColumnMetaDataReader(ResultSet rsCols)
    {
        return new PostgreSQLColumnMetaDataReader(rsCols);
    }


    private class PostgreSQLColumnMetaDataReader extends ColumnMetaDataReader
    {
        public PostgreSQLColumnMetaDataReader(ResultSet rsCols)
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
            String typeName = getSqlTypeName();
            return typeName.equalsIgnoreCase("serial") || typeName.equalsIgnoreCase("bigserial");
        }

        @Override
        public int getSqlType() throws SQLException
        {
            int sqlType = super.getSqlType();

            // PostgreSQL 8.3 returns DISTINCT for user-defined types
            if (Types.DISTINCT == sqlType)
                return _rsCols.getInt("SOURCE_DATA_TYPE");
            else
                return sqlType;
        }
    }

    public PkMetaDataReader getPkMetaDataReader(ResultSet rs)
    {
        return new PkMetaDataReader(rs, "COLUMN_NAME", "KEY_SEQ");
    }


    public String getExtraInfo(SQLException e)
    {
        // Deadlock between two different DB connections
        if ("40P01".equals(e.getSQLState()))
        {
            return getOtherDatabaseThreads();
        }
        return null;
    }

    public TestSuite getTestSuite()
    {
        TestSuite suite = new TestSuite();
        suite.addTest(new DialectRetrievalTestCase());
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

            try
            {
                TestUpgradeCode good = new TestUpgradeCode();
                runSql(null, goodSql, good, null);
                assertEquals(4, good.getCounter());

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

    public static class DialectRetrievalTestCase extends AbstractDialectRetrievalTestCase
    {
        public void testDialectRetrieval()
        {
            // These should result in bad database exception
            badProductName("Gobbledygood", 8.0, 8.5, "");
            badProductName("Postgres", 8.0, 8.5, "");
            badProductName("postgresql", 8.0, 8.5, "");

            // 8.1 or lower should result in bad version number
            badVersion("PostgreSQL", -5.0, 8.1, null);

            //  > 8.1 should be good
            good("PostgreSQL", 8.2, 11.0, "", SqlDialectPostgreSQL.class);
        }
    }
}

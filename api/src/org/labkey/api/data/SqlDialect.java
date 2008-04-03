/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.data;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.tomcat.dbcp.dbcp.BasicDataSource;
import org.labkey.api.util.CaseInsensitiveHashSet;
import org.labkey.api.util.SystemMaintenance;

import javax.servlet.ServletException;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: arauch
 * Date: Dec 28, 2004
 * Time: 8:58:25 AM
 */

// Isolate the big SQL differences between database servers
public abstract class SqlDialect
{
    protected static final Logger _log = Logger.getLogger(SqlDialect.class);
    private static Map<String, SqlDialect> _dialects = new HashMap<String, SqlDialect>(10);

    public static final String GENERIC_ERROR_MESSAGE = "The database experienced an unexpected problem. Please check your input and try again.";
    public static final String INPUT_TOO_LONG_ERROR_MESSAGE = "The input you provided was too long.";
    protected Set<String> reservedWordSet = new CaseInsensitiveHashSet();

    static final private Pattern s_patStringLiteral = Pattern.compile("\\'([^\\']|(\'\'))*\\'");
    static final private Pattern s_patQuotedIdentifier = Pattern.compile("\\\"([^\\\"]|(\\\"\\\"))*\\\"");
    static final private Pattern s_patParameter = Pattern.compile("\\?");

    static
    {
        SystemMaintenance.addTask(new DatabaseMaintenanceTask());
    }


    private static class DatabaseMaintenanceTask implements SystemMaintenance.MaintenanceTask
    {
        public String getMaintenanceTaskName()
        {
            return "Database maintenance";
        }

        public void run()
        {
            Map<String, DbScope> scopes = DbSchema.getDbScopes();

            for (DbScope scope : scopes.values())
            {
                Connection conn = null;
                String sql = scope.getSqlDialect().getDatabaseMaintenanceSql();
                BasicDataSource ds = (BasicDataSource)scope.getDataSource();
                _log.info("Database maintenance on " + ds.getUrl() + " started");

                try
                {
                    if (null != sql)
                    {
                        conn = ds.getConnection();
                        Table.execute(conn, sql, null);
                    }
                }
                catch(SQLException e)
                {
                    // Nothing to do here... table layer will log any errors
                }
                finally
                {
                    try {  if (null != conn) conn.close(); } catch (SQLException e) { /**/ }
                }

                _log.info("Database maintenance on " + ds.getUrl() + " complete");
            }
        }
    }


    protected String getDatabaseMaintenanceSql()
    {
        return null;
    }

    /**
     * Getting the SqlDialect from the driver class name won't return the version
     * specific dialect -- use getFromMetaData() if possible.
     */
    public static SqlDialect getFromDriverClassName(String driverClassName)
    {
        if ("org.postgresql.Driver".equals(driverClassName))
            return getFromProductName("PostgreSQL", 0, 0);

        if ("net.sourceforge.jtds.jdbc.Driver".equals(driverClassName))
            return getFromProductName("Microsoft SQL Server", 0, 0);

        return null;
    }

    public static SqlDialect getFromMetaData(DatabaseMetaData md) throws SQLException
    {
        return getFromProductName(md.getDatabaseProductName(), md.getDatabaseMajorVersion(), md.getDatabaseMinorVersion());
    }

    public static SqlDialect getFromProductName(String dataBaseProductName, int majorVersion, int minorVersion)
    {
        if (dataBaseProductName.equals("PostgreSQL"))
        {
            return SqlDialectPostgreSQL.getInstance();
        }
        else if (dataBaseProductName.equals("Microsoft SQL Server"))
        {
            if (majorVersion >= 9)
            {
                return SqlDialectMicrosoftSQLServer9.getInstance();
            }
            else
            {
                return SqlDialectMicrosoftSQLServer.getInstance();
            }
        }
        _log.error("SqlDialect: no dialect for " + dataBaseProductName + " (" + majorVersion + "." + minorVersion + ")");
        return null;
    }

    public SqlDialect()
    {
    }

    // Do dialect-specific work after schema load
    public abstract void prepareNewDbSchema(DbSchema schema);

    public abstract String getProductName();

    public abstract String getSQLScriptPath();

    public abstract void appendStatement(StringBuilder sql, String statement);

    public abstract void appendSelectAutoIncrement(StringBuilder sql, TableInfo table, String columnName);

    /**
     * Limit a SELECT query to the specified number of rows (0 == no limit).
     * @param sql a SELECT query
     * @param rowCount return the first rowCount number of rows (0 == no limit).
     * @return the query
     */
    public abstract SQLFragment limitRows(SQLFragment sql, int rowCount);

    public void limitRows(StringBuilder builder, int rowCount)
    {
        SQLFragment frag = new SQLFragment();
        frag.append(builder);
        limitRows(frag, rowCount);
        builder.replace(0, builder.length(), frag.getSQL());
    }

    /**
     * Composes the fragments into a SQL query that will be limited by rowCount
     * starting at the given 0-based offset.
     * 
     * @param select must not be null
     * @param from must not be null
     * @param filter may be null
     * @param order may be null
     * @param rowCount 0 means all rows, >0 limits result set
     * @param offset 0 based
     * @return the query
     */
    public abstract SQLFragment limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, String order, int rowCount, long offset);

    /** Does the dialect support limitRows() with an offset? */
    public abstract boolean supportOffset();

    public abstract String execute(DbSchema schema, String procedureName, String parameters);

    public abstract String getConcatenationOperator();

    /**
     * Return the operator which supports, in addition to the usual LIKE things '%', and '_', also supports
     * character classes. (i.e. [abc] matching a,b or c)
     * If you do not need the functionality of character classes, then "LIKE" will work just fine with all SQL dialects.
     */
    public abstract String getCharClassLikeOperator();

    public abstract String getCaseInsensitiveLikeOperator();

    public abstract String getVarcharLengthFunction();

    public abstract String getStdDevFunction();

    public abstract String getClobLengthFunction();

    public abstract String getStringIndexOfFunction(String stringToFind, String stringToSearch);

    public abstract String getSubstringFunction(String s, String start, String length);

    public abstract int getNextSqlBlock(StringBuffer block, DbSchema schema, String sql, int i);

    public abstract String getMasterDataBaseName();

    public abstract String getDefaultDateTimeDatatype();

    public abstract String getUniqueIdentType();

    public abstract String getTempTableKeyword();

    public abstract String getTempTablePrefix();

    public abstract String getGlobalTempTablePrefix();

    public abstract boolean isNoDatabaseException(SQLException e);

    public abstract boolean isSortableDataType(String sqlDataTypeName);

    public abstract String getDropIndexCommand(String tableName, String indexName);

    public abstract String getCreateDatabaseSql(String dbName);

    public abstract String getCreateSchemaSql(String schemaName);

    /** @param part the java.util.Calendar field for the unit of time, such as Calendar.DATE or Calendar.MINUTE */
    public abstract String getDateDiff(int part, String value1, String value2);

    /** @param expression The expression with datetime value for which a date value is desired */
    public abstract String getDateTimeToDateCast(String expression);

    public abstract String getRoundFunction(String valueToRound);

    // Do nothing by default
    public void prepareNewDatabase(DbSchema schema) throws ServletException
    {
    }

    public void handleCreateDatabaseException(SQLException e) throws ServletException
    {
        throw(new ServletException("Can't create database", e));
    }

    /**
     * Wrap one or more INSERT statements to allow explicit specification
     * of values for auto-incrementing columns (e.g. IDENTITY in SQL Server
     * or SERIAL in Postgres). The input StringBuffer is modified to
     * wrap the statements in dialect-specific code to allow this.
     *
     * @param statements the insert statements. If more than one,
     *                   they must have been joined by appendStatement
     *                   and must all refer to the same table.
     * @param tinfo      table used in the insert(s)
     */
    public abstract void overrideAutoIncrement(StringBuffer statements, TableInfo tinfo);

    protected String getSystemTableNames()
    {
        return "";
    }

    private Set<String> systemTableSet = new CaseInsensitiveHashSet(Arrays.asList(getSystemTableNames().split(",")));

    public boolean isSystemTable(String tableName)
    {
        return systemTableSet.contains(tableName);
    }


    // Just return name by default... subclasses can override and (for example) put quotes around keywords
    public String getColumnSelectName(String columnName)
    {
        if (reservedWordSet.contains(columnName))
            return "\"" + columnName.toLowerCase() + "\"";
        else
            return columnName;
    }

    // Just return name by default... subclasses can override and (for example) put quotes around keywords
    public String getTableSelectName(String tableName)
    {
        return tableName;
    }

    // Just return name by default... subclasses can override and (for example) put quotes around keywords
    public String getOwnerSelectName(String ownerName)
    {
        return ownerName;
    }

    // String version for convenience
    public String appendSelectAutoIncrement(String sql, TableInfo tinfo, String columnName)
    {
        StringBuilder sbSql = new StringBuilder(sql);
        appendSelectAutoIncrement(sbSql, tinfo, columnName);
        return sbSql.toString();
    }


    public final void checkSqlScript(String sql, double version) throws SQLSyntaxException
    {
        if (version <= 2.10)
            return;

        Collection<String> errors = new ArrayList<String>();
        String lower = sql.toLowerCase();
        String lowerNoWhiteSpace = lower.replaceAll("\\s", "");

        if (lowerNoWhiteSpace.contains("primarykey,"))
            errors.add("Do not designate PRIMARY KEY on the column definition line; this creates a PK with an arbitrary name, making it more difficult to change it later.  Instead, create the PK as a named contraint (e.g., PK_MyTable).");

        checkSqlScript(lower, lowerNoWhiteSpace, errors);

        if (!errors.isEmpty())
            throw new SQLSyntaxException(errors);
    }


    abstract void checkSqlScript(String lower, String lowerNoWhiteSpace, Collection<String> errors);

    protected class SQLSyntaxException extends SQLException
    {
        private Collection<String> _errors;

        protected SQLSyntaxException(Collection<String> errors)
        {
            _errors = errors;
        }

        @Override
        public String getMessage()
        {
            return StringUtils.join(_errors.iterator(), '\n');
        }
    }

    public void runSql(DbSchema schema, String sql) throws SQLException
    {
        int i = 0;

        while (i < sql.length())
        {
            StringBuffer block = new StringBuffer();
            i = getNextSqlBlock(block, schema, sql, i);

            if (0 != block.length())
                Table.execute(schema, block.toString(), new Object[]{});
        }
    }

    /**
     * Transform the JDBC error message into something the user is more likely
     * to understand.
     */
    public abstract String sanitizeException(SQLException ex);

    public abstract String getAnalyzeCommandForTable(String tableName);

    protected abstract String getSIDQuery();

    public Integer getSPID(Connection result) throws SQLException
    {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try
        {
            stmt = result.prepareStatement(getSIDQuery());
            rs = stmt.executeQuery();
            if (!rs.next())
            {
                throw new SQLException("SID query returned no results");
            }
            return rs.getInt(1);
        }
        finally
        {
            if (stmt != null) { try { stmt.close(); } catch (SQLException e) {} }
            if (rs != null) { try { rs.close(); } catch (SQLException e) {} }
        }
    }

    public abstract String getBooleanDatatype();


    public static String getDatabaseName(BasicDataSource ds) throws ServletException
    {
        return getJdbcHelper(ds).getDatabase();
    }


    // We need to determine the database name from a data source, so we've implemented a helper that parses
    // the JDBC connection string for each driver we support.  This is necessary because, unfortunately, there
    // appears to be no standard, reliable way to ask a JDBC driver for individual components of the URL or
    // to programmatically assemble a new connection URL.  Driver.getPropertyInfo(), for example, doesn't
    // return the database name on PostgreSQL if it's specified as part of the URL.
    //
    // Currently, JdbcHelper only finds the database name.  It could be extended if we require querying
    // other components or if replacement/reassembly becomes necessary.
    public static JdbcHelper getJdbcHelper(BasicDataSource ds) throws ServletException
    {
        String url = ds.getUrl();

        if (url.startsWith("jdbc:jtds:sqlserver"))
            return new SqlDialectMicrosoftSQLServer.JtdsJdbcHelper(url);
        else if (url.startsWith("jdbc:postgresql"))
            return new SqlDialectPostgreSQL.PostgreSQLJdbcHelper(url);
        else
            throw new ServletException("Unsupported connection url: " + url);
    }

    public static abstract class JdbcHelper
    {
        protected String _database;

        public String getDatabase()
        {
            return _database;
        }
    }

    /**
     * Drop a schema if it exists.
     * Throws an exception if schema exists, and could not be dropped. 
     */
    public void dropSchema(DbSchema schema, String schemaName) throws SQLException
    {
        Object[] parameters = new Object[]{"*", schemaName, "SCHEMA", null};
        String sql = schema.getSqlDialect().execute(CoreSchema.getInstance().getSchema(), "fn_dropifexists", "?, ?, ?, ?");
        Table.execute(schema, sql, parameters);
    }

    /**
     * Drop an object (table, view) or subobject (index) if it exists
     *
     * @param schema  dbSchema in which the object lives
     * @param objectName the name of the table or view to be dropped, or the table on which the index is defined
     * @param objectType "TABLE", "VIEW", "INDEX"
     * @param subObjectName index name;  ignored if not an index
     */
    public void dropIfExists (DbSchema schema, String objectName, String objectType, String subObjectName) throws SQLException
    {
        Object[] parameters = new Object[]{objectName, schema.getOwner(), objectType, subObjectName};
        String sql = schema.getSqlDialect().execute(CoreSchema.getInstance().getSchema(), "fn_dropifexists", "?, ?, ?, ?");
        Table.execute(schema, sql, parameters);
    }

    /**
     * Returns a SQL fragment for the integer expression indicating the (1-based) first occurrence of littleString in bigString
     */
    abstract public SQLFragment sqlLocate(SQLFragment littleString, SQLFragment bigString);

    /**
     * Returns a SQL fragment for the integer expression indicating the (1-based) first occurrence of littleString in bigString starting at (1-based) startIndex.
     */
    abstract public SQLFragment sqlLocate(SQLFragment littleString, SQLFragment bigString, SQLFragment startIndex);

    abstract public boolean allowSortOnSubqueryWithoutLimit();

    protected Pattern patStringLiteral()
    {
        return s_patStringLiteral;
    }

    protected Pattern patQuotedIdentifier()
    {
        return s_patQuotedIdentifier;
    }

    protected String quoteStringLiteral(String str)
    {
        return "'" + StringUtils.replace(str, "'", "''") + "'";
    }

    /**
     * Substitute the parameter values into the SQL statement.
     * Iterates through the SQL string
     */
    public String substituteParameters(SQLFragment frag)
    {
        CharSequence sql = frag.getSqlCharSequence();
        Matcher matchIdentifier = patQuotedIdentifier().matcher(sql);
        Matcher matchStringLiteral = patStringLiteral().matcher(sql);
        Matcher matchParam = s_patParameter.matcher(sql);

        StringBuilder ret = new StringBuilder();
        List<Object> params = new ArrayList<Object>(frag.getParams());
        int ich = 0;
        while (ich < sql.length())
        {
            int ichSkipTo = sql.length();
            int ichSkipPast = sql.length();
            if (matchIdentifier.find(ich))
            {
                if (matchIdentifier.start() < ichSkipTo)
                {
                    ichSkipTo = matchIdentifier.start();
                    ichSkipPast = matchIdentifier.end();
                }
            }
            if (matchStringLiteral.find(ich))
            {
                if (matchStringLiteral.start() < ichSkipTo)
                {
                    ichSkipTo = matchStringLiteral.start();
                    ichSkipPast = matchStringLiteral.end();
                }
            }
            if (matchParam.find(ich))
            {
                if (matchParam.start() < ichSkipTo)
                {
                    ret.append(frag.getSqlCharSequence().subSequence(ich, matchParam.start()));
                    ret.append(" ");
                    ret.append(quoteStringLiteral(ObjectUtils.toString(params.remove(0))));
                    ret.append(" ");
                    ich = matchParam.start() + 1;
                    continue;
                }
            }
            ret.append(frag.getSqlCharSequence().subSequence(ich, ichSkipPast));
            ich = ichSkipPast;
        }
        return ret.toString();
    }
}

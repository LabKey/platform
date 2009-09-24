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

package org.labkey.api.data;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.framework.TestCase;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.query.AliasManager;
import org.jetbrains.annotations.Nullable;

import javax.servlet.ServletException;
import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
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
    private static List<SqlDialect> _dialects = new CopyOnWriteArrayList<SqlDialect>();

    public static final String GENERIC_ERROR_MESSAGE = "The database experienced an unexpected problem. Please check your input and try again.";
    public static final String INPUT_TOO_LONG_ERROR_MESSAGE = "The input you provided was too long.";
    protected Set<String> reservedWordSet = new CaseInsensitiveHashSet();
    private Map<String, Integer> sqlTypeNameMap = new CaseInsensitiveHashMap<Integer>();
    private Map<Integer, String> sqlTypeIntMap = new HashMap<Integer, String>();

    static final private Pattern s_patStringLiteral = Pattern.compile("\\'([^\\']|(\'\'))*\\'");
    static final private Pattern s_patQuotedIdentifier = Pattern.compile("\\\"([^\\\"]|(\\\"\\\"))*\\\"");
    static final private Pattern s_patParameter = Pattern.compile("\\?");

    public static void register(SqlDialect dialect)
    {
        _dialects.add(dialect);
    }

    protected String getOtherDatabaseThreads()
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
        return sb.length() > 0 ? sb.toString() : "No other threads with active database connections to report.";
    }

    public abstract String getBooleanLiteral(boolean b);


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
            Collection<DbScope> scopes = DbScope.getDbScopes();

            for (DbScope scope : scopes)
            {
                Connection conn = null;
                String sql = scope.getSqlDialect().getDatabaseMaintenanceSql();
                DataSource ds = scope.getDataSource();

                String url = null;

                try
                {
                    DataSourceProperties props = new DataSourceProperties(scope.getDataSourceName(), ds);
                    url = props.getUrl();
                    _log.info("Database maintenance on " + url + " started");
                }
                catch (Exception e)
                {
                    // Shouldn't happen, but we can survive without the url
                    _log.error("Exception retrieving url", e);
                }

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

                if (null != url)
                    _log.info("Database maintenance on " + url + " complete");
            }
        }
    }


    protected SqlDialect()
    {
        initializeSqlTypeNameMap();
        initializeSqlTypeIntMap();
    }


    private void initializeSqlTypeNameMap()
    {
        sqlTypeNameMap.put("ARRAY", Types.ARRAY);
        sqlTypeNameMap.put("BIGINT", Types.BIGINT);
        sqlTypeNameMap.put("BINARY", Types.BINARY);
        sqlTypeNameMap.put("BIT", Types.BIT);
        sqlTypeNameMap.put("BLOB", Types.BLOB);
        sqlTypeNameMap.put("BOOLEAN", Types.BOOLEAN);
        sqlTypeNameMap.put("CHAR", Types.CHAR);
        sqlTypeNameMap.put("CLOB", Types.CLOB);
        sqlTypeNameMap.put("DATALINK", Types.DATALINK);
        sqlTypeNameMap.put("DATE", Types.DATE);
        sqlTypeNameMap.put("DECIMAL", Types.DECIMAL);
        sqlTypeNameMap.put("DISTINCT", Types.DISTINCT);
        sqlTypeNameMap.put("DOUBLE", Types.DOUBLE);
        sqlTypeNameMap.put("DOUBLE PRECISION", Types.DOUBLE);
        sqlTypeNameMap.put("INTEGER", Types.INTEGER);
        sqlTypeNameMap.put("INT", Types.INTEGER);
        sqlTypeNameMap.put("JAVA_OBJECT", Types.JAVA_OBJECT);
        sqlTypeNameMap.put("LONGVARBINARY", Types.LONGVARBINARY);
        sqlTypeNameMap.put("LONGVARCHAR", Types.LONGVARCHAR);
        sqlTypeNameMap.put("NULL", Types.NULL);
        sqlTypeNameMap.put("NUMERIC", Types.NUMERIC);
        sqlTypeNameMap.put("OTHER", Types.OTHER);
        sqlTypeNameMap.put("REAL", Types.REAL);
        sqlTypeNameMap.put("REF", Types.REF);
        sqlTypeNameMap.put("SMALLINT", Types.SMALLINT);
        sqlTypeNameMap.put("STRUCT", Types.STRUCT);
        sqlTypeNameMap.put("TIME", Types.TIME);
        sqlTypeNameMap.put("TINYINT", Types.TINYINT);
        sqlTypeNameMap.put("VARBINARY", Types.VARBINARY);
        sqlTypeNameMap.put("VARCHAR", Types.VARCHAR);

        addSqlTypeNames(sqlTypeNameMap);
    }


    private void initializeSqlTypeIntMap()
    {
        sqlTypeIntMap.put(Types.ARRAY, "ARRAY");
        sqlTypeIntMap.put(Types.BIGINT, "BIGINT");
        sqlTypeIntMap.put(Types.BINARY, "BINARY");
        sqlTypeIntMap.put(Types.BLOB, "BLOB");
        sqlTypeIntMap.put(Types.CLOB, "CLOB");
        sqlTypeIntMap.put(Types.DATALINK, "DATALINK");
        sqlTypeIntMap.put(Types.DATE, "DATE");
        sqlTypeIntMap.put(Types.DECIMAL, "DECIMAL");
        sqlTypeIntMap.put(Types.DISTINCT, "DISTINCT");
        sqlTypeIntMap.put(Types.INTEGER, "INTEGER");
        sqlTypeIntMap.put(Types.JAVA_OBJECT, "JAVA_OBJECT");
        sqlTypeIntMap.put(Types.NULL, "NULL");
        sqlTypeIntMap.put(Types.NUMERIC, "NUMERIC");
        sqlTypeIntMap.put(Types.OTHER, "OTHER");
        sqlTypeIntMap.put(Types.REAL, "REAL");
        sqlTypeIntMap.put(Types.REF, "REF");
        sqlTypeIntMap.put(Types.SMALLINT, "SMALLINT");
        sqlTypeIntMap.put(Types.STRUCT, "STRUCT");
        sqlTypeIntMap.put(Types.TIME, "TIME");
        sqlTypeIntMap.put(Types.TINYINT, "TINYINT");
        sqlTypeIntMap.put(Types.VARBINARY, "VARBINARY");

        addSqlTypeInts(sqlTypeIntMap);
    }


    protected abstract void addSqlTypeNames(Map<String, Integer> sqlTypeNameMap);
    protected abstract void addSqlTypeInts(Map<Integer, String> sqlTypeIntMap);

    public int sqlTypeIntFromSqlTypeName(String sqlTypeName)
    {
        Integer i = sqlTypeNameMap.get(sqlTypeName);

        if (null != i)
            return i.intValue();
        else
        {
            _log.info("Unknown SQL Type Name \"" + sqlTypeName + "\"; using String instead.");
            return Types.OTHER;
        }
    }


    public String sqlTypeNameFromSqlTypeInt(int sqlTypeInt)
    {
        String sqlTypeName = sqlTypeIntMap.get(sqlTypeInt);

        return null != sqlTypeName ? sqlTypeName : "OTHER";
    }


    public String sqlTypeNameFromSqlType(int sqlType)
    {
        return sqlTypeNameFromSqlTypeInt(sqlType);
    }


    protected String getDatabaseMaintenanceSql()
    {
        return null;
    }


    public static class SqlDialectNotSupportedException extends ConfigurationException
    {
        private SqlDialectNotSupportedException(String advice)
        {
            super("JDBC database driver is not supported.", advice);
        }
    }


    public static class DatabaseNotSupportedException extends ConfigurationException
    {
        public DatabaseNotSupportedException(String message)
        {
            super(message);
        }
    }


    /**
     * Getting the SqlDialect from the driver class name won't return the version
     * specific dialect -- use getFromMetaData() if possible.
     * @param props
     * @return SqlDialect
     */
    public static SqlDialect getFromDataSourceProperties(DataSourceProperties props) throws ServletException
    {
        String driverClassName = props.getDriverClassName();

        for (SqlDialect dialect : _dialects)
            if (dialect.claimsDriverClassName(driverClassName))
                return dialect;

        throw new SqlDialectNotSupportedException("The database driver \"" + props.getDriverClassName() + "\" specified in data source \"" + props.getDataSourceName() + "\" is not supported in your installation.");
    }


    public static SqlDialect getFromMetaData(DatabaseMetaData md) throws SQLException, SqlDialectNotSupportedException, DatabaseNotSupportedException
    {
        return getFromProductName(md.getDatabaseProductName(), md.getDatabaseMajorVersion(), md.getDatabaseMinorVersion(), true);
    }


    private static SqlDialect getFromProductName(String dataBaseProductName, int majorVersion, int minorVersion, boolean logWarnings) throws SqlDialectNotSupportedException, DatabaseNotSupportedException
    {
        for (SqlDialect dialect : _dialects)
            if (dialect.claimsProductNameAndVersion(dataBaseProductName, majorVersion, minorVersion, logWarnings))
                return dialect;

        throw new SqlDialectNotSupportedException("The requested product name and version -- " + dataBaseProductName + " " + majorVersion + "." + minorVersion + " -- is not supported by your LabKey installation.");
    }

    /**
     * @return any additional information that should be sent to the mothership in the case of a SQLException
     */
    public String getExtraInfo(SQLException e)
    {
        return null;
    }

    public static boolean isConstraintException(SQLException x)
    {
        String sqlState = x.getSQLState();
        if (!sqlState.startsWith("23"))
            return false;
        return sqlState.equals("23000") || sqlState.equals("23505") || sqlState.equals("23503");
    }

    protected abstract boolean claimsDriverClassName(String driverClassName);

    // Implementation should throw only if it's responsible for the specified database server but doesn't support the specified version
    protected abstract boolean claimsProductNameAndVersion(String dataBaseProductName, int majorVersion, int minorVersion, boolean logWarnings) throws DatabaseNotSupportedException;

    // Do dialect-specific work after schema load
    public abstract void prepareNewDbSchema(DbSchema schema);

    protected abstract String getProductName();

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
    public abstract boolean supportsOffset();

    public abstract boolean supportsComments();

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

    public abstract void runSql(DbSchema schema, String sql, UpgradeCode upgradeCode, ModuleContext moduleContext) throws SQLException;

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


    // does provider support ROUND(double,x) where x != 0
    public abstract boolean supportsRoundDouble();

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
    public abstract void overrideAutoIncrement(StringBuilder statements, TableInfo tinfo);

    protected String getSystemTableNames()
    {
        return "";
    }

    private Set<String> systemTableSet = new CaseInsensitiveHashSet(Arrays.asList(getSystemTableNames().split(",")));

    public boolean isSystemTable(String tableName)
    {
        return systemTableSet.contains(tableName);
    }


    public boolean isReserved(String word)
    {
        return reservedWordSet.contains(word);
    }


    // Just return name by default... subclasses can override and (for example) put quotes around keywords
    public String getColumnSelectName(String columnName)
    {
        if (reservedWordSet.contains(columnName))
            return "\"" + columnName.toLowerCase() + "\"";
        else
            return columnName;
    }


    // quote column identifier if necessary
    public String quoteColumnIdentifier(String id)
    {
        if (!AliasManager.isLegalName(id) || reservedWordSet.contains(id))
            return "\"" + id.toLowerCase() + "\"";
        else
            return id;
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


    abstract protected void checkSqlScript(String lower, String lowerNoWhiteSpace, Collection<String> errors);

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
    
    public String getBooleanTRUE()
    {
        return "CAST(1 AS " + getBooleanDatatype() + ")";
    }

    public String getBooleanFALSE()
    {
        return "CAST(0 AS " + getBooleanDatatype() + ")";
    }


    // We need to determine the database name from a data source, so we've implemented a helper that parses
    // the JDBC connection string for each driver we support.  This is necessary because, unfortunately, there
    // appears to be no standard, reliable way to ask a JDBC driver for individual components of the URL or
    // to programmatically assemble a new connection URL.  Driver.getPropertyInfo(), for example, doesn't
    // return the database name on PostgreSQL if it's specified as part of the URL.
    //
    // Currently, JdbcHelper only finds the database name.  It could be extended if we require querying
    // other components or if replacement/reassembly becomes necessary.
    public String getDatabaseName(String dsName, DataSource ds) throws ServletException
    {
        try
        {
            DataSourceProperties props = new DataSourceProperties(dsName, ds);
            String url = props.getUrl();
            return getDatabaseName(url);
        }
        catch (Exception e)
        {
            throw new ServletException("Error retrieving database name from DataSource", e);
        }
    }


    public String getDatabaseName(String url) throws ServletException
    {
        return getJdbcHelper(url).getDatabase();
    }


    public abstract JdbcHelper getJdbcHelper(String url) throws ServletException;

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


    // Trying to be DataSource implementation agnostic here.  DataSource interface doesn't provide access to any of
    // these properties, but we don't want to cast to a specific implementation class, so use reflection to get them.
    public static class DataSourceProperties
    {
        private final String _dsName;
        private final DataSource _ds;

        public DataSourceProperties(String dsName, DataSource ds)
        {
            _dsName = dsName;
            _ds = ds;
        }

        private String getProperty(String methodName) throws ServletException
        {
            try
            {
                Method getUrl = _ds.getClass().getMethod(methodName);
                return (String)getUrl.invoke(_ds);
            }
            catch (Exception e)
            {
                throw new ServletException("Unable to retrieve DataSource property via " + methodName, e);
            }
        }

        public String getDataSourceName()
        {
            return _dsName;
        }

        public String getUrl() throws ServletException
        {
            return getProperty("getUrl");
        }


        public String getDriverClassName() throws ServletException
        {
            return getProperty("getDriverClassName");
        }


        public String getUsername() throws ServletException
        {
            return getProperty("getUsername");
        }


        public String getPassword() throws ServletException
        {
            return getProperty("getPassword");
        }
    }


    // Handles standard reading of column meta data
    public static abstract class ColumnMetaDataReader
    {
        protected ResultSet _rsCols;
        protected String _nameKey, _sqlTypeKey, _sqlTypeNameKey, _scaleKey, _nullableKey, _postionKey;

        public ColumnMetaDataReader(ResultSet rsCols)
        {
            _rsCols = rsCols;
        }

        public String getName() throws SQLException
        {
            return _rsCols.getString(_nameKey);
        }

        public int getSqlType() throws SQLException
        {
            int sqlType = _rsCols.getInt(_sqlTypeKey);

            if (Types.OTHER == sqlType)
                return Types.NULL;
            else
                return sqlType;
        }

        public String getSqlTypeName() throws SQLException
        {
            return _rsCols.getString(_sqlTypeNameKey);
        }

        public int getScale() throws SQLException
        {
            // TODO: Use type int instead?
            String typeName = getSqlTypeName();

            if (typeName.equalsIgnoreCase("ntext") || typeName.equalsIgnoreCase("text"))
                return 0x7FFF;
            else
                return _rsCols.getInt(_scaleKey);
        }

        public boolean isNullable() throws SQLException
        {
            return _rsCols.getInt(_nullableKey) == 1;
        }

        public int getPosition() throws SQLException
        {
            return _rsCols.getInt(_postionKey);
        }

        public abstract boolean isAutoIncrement() throws SQLException;
    }


    // Handles standard reading of column meta data
    public static class PkMetaDataReader
    {
        private ResultSet _rsCols;
        private String _nameKey, _seqKey;

        public PkMetaDataReader(ResultSet rsCols, String nameKey, String seqKey)
        {
            _rsCols = rsCols;
            _nameKey = nameKey;
            _seqKey = seqKey;
        }

        public String getName() throws SQLException
        {
            return _rsCols.getString(_nameKey);
        }

        public int getKeySeq() throws SQLException
        {
            return _rsCols.getInt(_seqKey);
        }
    }


    public abstract void initializeConnection(Connection conn) throws SQLException;
    public abstract void purgeTempSchema(Map<String, TempTableTracker> createdTableNames);
    public abstract boolean isCaseSensitive();
    public abstract boolean isEditable();
    public abstract boolean isSqlServer();
    public abstract boolean isPostgreSQL();
    public abstract ColumnMetaDataReader getColumnMetaDataReader(ResultSet rsCols);
    public abstract PkMetaDataReader getPkMetaDataReader(ResultSet rs);
    public abstract TestSuite getTestSuite();


    // JUnit test case
    public static class SqlDialectTestCase extends junit.framework.TestCase
    {
        public static Test suite()
        {
            return CoreSchema.getInstance().getSqlDialect().getTestSuite();
        }
    }


    public static class TestUpgradeCode implements UpgradeCode
    {
        private int _counter = 0;

        @SuppressWarnings({"UnusedDeclaration"})
        public void upgradeCode(ModuleContext moduleContext)
        {
            _counter++;
        }

        public int getCounter()
        {
            return _counter;
        }
    }


    protected static abstract class AbstractDialectRetrievalTestCase extends TestCase
    {
        public AbstractDialectRetrievalTestCase()
        {
            super("testDialectRetrieval");
        }

        public abstract void testDialectRetrieval();

        protected void good(String databaseName, double beginVersion, double endVersion, Class<? extends SqlDialect> expectedDialectClass)
        {
            testRange(databaseName, beginVersion, endVersion, expectedDialectClass, null);
        }

        protected void badProductName(String databaseName, double beginVersion, double endVersion)
        {
            testRange(databaseName, beginVersion, endVersion, null, SqlDialectNotSupportedException.class);
        }

        protected void badVersion(String databaseName, double beginVersion, double endVersion)
        {
            testRange(databaseName, beginVersion, endVersion, null, DatabaseNotSupportedException.class);
        }

        private void testRange(String databaseName, double beginVersion, double endVersion, @Nullable Class<? extends SqlDialect> expectedDialectClass, @Nullable Class<? extends ConfigurationException> expectedExceptionClass)
        {
            int begin = (int)Math.round(beginVersion * 10);
            int end = (int)Math.round(endVersion * 10);

            for (int i = begin; i < end; i++)
            {
                int majorVersion = i / 10;
                int minorVersion = i % 10;

                String description = databaseName + " version " + majorVersion + "." + minorVersion;

                try
                {
                    SqlDialect dialect = getFromProductName(databaseName, majorVersion, minorVersion, false);
                    assertNotNull(description + " returned " + dialect.getClass().getSimpleName() + "; expected failure", expectedDialectClass);
                    assertEquals(description + " returned " + dialect.getClass().getSimpleName() + "; expected " + expectedDialectClass.getSimpleName(), dialect.getClass(), expectedDialectClass);
                }
                catch (Exception e)
                {
                    assertTrue(description + " failed; expected success", null == expectedDialectClass);
                    assertEquals(description + " resulted in a " + e.getClass().getSimpleName() + "; expected " + expectedExceptionClass, e.getClass(), expectedExceptionClass);
                }
            }
        }
    }
}

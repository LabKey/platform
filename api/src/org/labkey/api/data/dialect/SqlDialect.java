/*
 * Copyright (c) 2005-2017 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.data.dialect;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.*;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.AliasManager;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.SystemMaintenance;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.jdbc.BadSqlGrammarException;

import javax.servlet.ServletException;
import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Method;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * User: arauch
 * Date: Dec 28, 2004
 * Time: 8:58:25 AM
 */

// Isolate the big SQL differences between database servers. A new SqlDialect instance is created for each DbScope; the
// dialect holds state specific to each database, for example, reserved words, user defined type information, etc.
public abstract class SqlDialect
{
    protected static final Logger LOG = Logger.getLogger(SqlDialect.class);
    protected static final String INPUT_TOO_LONG_ERROR_MESSAGE = "The input you provided was too long.";
    public static final String GENERIC_ERROR_MESSAGE = "The database experienced an unexpected problem. Please check your input and try again.";
    public static final String GUID_TYPE = "ENTITYID";
    protected static final int MAX_VARCHAR_SIZE = 4000;  //Any length over this will be set to nvarchar(max)/text

    public static final String CUSTOM_UNIQUE_ERROR_MESSAGE = "Constraint violation: cannot insert duplicate value for column";

    private int _databaseVersion = 0;
    private String _productVersion = "0";
    private DialectStringHandler _stringHandler = null;

    private final Set<String> _reservedWordSet;
    private final Map<String, Integer> _sqlTypeNameMap = new CaseInsensitiveHashMap<>();
    private final Map<Integer, String> _sqlTypeIntMap = new HashMap<>();
    private final Map<String, DatabaseTableType> _tableTypeMap = new HashMap<>();
    private final String[] _tableTypes;

    protected SqlDialect()
    {
        initializeSqlTypeNameMap();
        initializeSqlTypeIntMap();
        initializeJdbcTableTypeMap(_tableTypeMap);
        Set<String> types = _tableTypeMap.keySet();
        _tableTypes = types.toArray(new String[types.size()]);

        _reservedWordSet = getReservedWords();

        MemTracker.getInstance().put(this);
    }

    protected void initializeJdbcTableTypeMap(Map<String, DatabaseTableType> map)
    {
        for (DatabaseTableType type : DatabaseTableType.values())
            map.put(type.name(), type);
    }

    public DatabaseTableType getTableType(String tableTypeName)
    {
        DatabaseTableType tableType = _tableTypeMap.get(tableTypeName);

        if (null == tableType)
            throw new IllegalStateException("Unknown table type: " + tableTypeName);

        return tableType;
    }

    public @Nullable String getTableDescription(@Nullable String description)
    {
        return description;
    }

    public String nameIndex(String tableName, String[] indexedColumns){
        throw new UnsupportedOperationException("Update " + this.getClass().getSimpleName() + " to add this functionality");
    }

    protected abstract @NotNull Set<String> getReservedWords();

    protected String getOtherDatabaseThreads()
    {
        StringBuilder sb = new StringBuilder();

        // Per 18789, also include threads without db connections.
        List<Thread> dbThreads = new ArrayList<>();

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
                dbThreads.add(thread);
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

        if (dbThreads.size() == 0)
            sb.append("No other threads with active database connections to report.\n");

        sb.append("All other threads without active database connections:\n");
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet())
        {
            Thread thread = entry.getKey();
            if (!dbThreads.contains(thread))
            {
                sb.append("\n");
                sb.append(thread.getName());
                sb.append("\n");

                for (StackTraceElement stackTraceElement : entry.getValue())
                {
                    sb.append("\t");
                    sb.append(stackTraceElement);
                    sb.append("\n");
                }
            }
        }
        return sb.toString();
    }

    static
    {
        SystemMaintenance.addTask(new DatabaseMaintenanceTask());
    }


    private void initializeSqlTypeNameMap()
    {
        _sqlTypeNameMap.put("ARRAY", Types.ARRAY);
        _sqlTypeNameMap.put("BIGINT", Types.BIGINT);
        _sqlTypeNameMap.put("BIT", Types.BIT);
        _sqlTypeNameMap.put("BLOB", Types.BLOB);
        _sqlTypeNameMap.put("BOOLEAN", Types.BOOLEAN);
        _sqlTypeNameMap.put("CHAR", Types.CHAR);
        _sqlTypeNameMap.put("CLOB", Types.CLOB);
        _sqlTypeNameMap.put("DATALINK", Types.DATALINK);
        _sqlTypeNameMap.put("DATE", Types.DATE);
        _sqlTypeNameMap.put("DECIMAL", Types.DECIMAL);
        _sqlTypeNameMap.put("DISTINCT", Types.DISTINCT);
        _sqlTypeNameMap.put("DOUBLE", Types.DOUBLE);
        _sqlTypeNameMap.put("DOUBLE PRECISION", Types.DOUBLE);
        _sqlTypeNameMap.put("INTEGER", Types.INTEGER);
        _sqlTypeNameMap.put("INT", Types.INTEGER);
        _sqlTypeNameMap.put("JAVA_OBJECT", Types.JAVA_OBJECT);
        _sqlTypeNameMap.put("LONGVARBINARY", Types.LONGVARBINARY);
        _sqlTypeNameMap.put("LONGVARCHAR", Types.LONGVARCHAR);
        _sqlTypeNameMap.put("NULL", Types.NULL);
        _sqlTypeNameMap.put("NUMERIC", Types.NUMERIC);
        _sqlTypeNameMap.put("OTHER", Types.OTHER);
        _sqlTypeNameMap.put("REAL", Types.REAL);
        _sqlTypeNameMap.put("REF", Types.REF);
        _sqlTypeNameMap.put("SMALLINT", Types.SMALLINT);
        _sqlTypeNameMap.put("STRUCT", Types.STRUCT);
        _sqlTypeNameMap.put("TIME", Types.TIME);
        _sqlTypeNameMap.put("TINYINT", Types.TINYINT);
        _sqlTypeNameMap.put("VARBINARY", Types.VARBINARY);
        _sqlTypeNameMap.put("VARCHAR", Types.VARCHAR);

        addSqlTypeNames(_sqlTypeNameMap);
    }


    private void initializeSqlTypeIntMap()
    {
        _sqlTypeIntMap.put(Types.ARRAY, "ARRAY");
        _sqlTypeIntMap.put(Types.BIGINT, "BIGINT");
        _sqlTypeIntMap.put(Types.BLOB, "BLOB");
        _sqlTypeIntMap.put(Types.CHAR, "CHAR");
        _sqlTypeIntMap.put(Types.CLOB, "CLOB");
        _sqlTypeIntMap.put(Types.DATALINK, "DATALINK");
        _sqlTypeIntMap.put(Types.DATE, "DATE");
        _sqlTypeIntMap.put(Types.DECIMAL, "DECIMAL");
        _sqlTypeIntMap.put(Types.DISTINCT, "DISTINCT");
        _sqlTypeIntMap.put(Types.INTEGER, "INTEGER");
        _sqlTypeIntMap.put(Types.JAVA_OBJECT, "JAVA_OBJECT");
        _sqlTypeIntMap.put(Types.NULL, "NULL");
        _sqlTypeIntMap.put(Types.NUMERIC, "NUMERIC");
        _sqlTypeIntMap.put(Types.OTHER, "OTHER");
        _sqlTypeIntMap.put(Types.REAL, "REAL");
        _sqlTypeIntMap.put(Types.REF, "REF");
        _sqlTypeIntMap.put(Types.SMALLINT, "SMALLINT");
        _sqlTypeIntMap.put(Types.STRUCT, "STRUCT");
        _sqlTypeIntMap.put(Types.TIME, "TIME");
        _sqlTypeIntMap.put(Types.TIMESTAMP, "TIMESTAMP");
        _sqlTypeIntMap.put(Types.TINYINT, "TINYINT");
        _sqlTypeIntMap.put(Types.VARBINARY, "VARBINARY");
        _sqlTypeIntMap.put(Types.VARCHAR, "VARCHAR");

        addSqlTypeInts(_sqlTypeIntMap);
    }


    protected abstract void addSqlTypeNames(Map<String, Integer> sqlTypeNameMap);
    protected abstract void addSqlTypeInts(Map<Integer, String> sqlTypeIntMap);

    public int sqlTypeIntFromSqlTypeName(String sqlTypeName)
    {
        Integer i = _sqlTypeNameMap.get(sqlTypeName);

        if (null != i)
            return i;
        else
        {
            LOG.info("Unknown SQL Type Name \"" + sqlTypeName + "\"; using String instead.");
            return Types.OTHER;
        }
    }


    @NotNull
    public String sqlTypeNameFromSqlType(int sqlType)
    {
        String sqlTypeName = _sqlTypeIntMap.get(sqlType);

        return null != sqlTypeName ? sqlTypeName : "OTHER";
    }


    @Nullable
    public String sqlTypeNameFromJdbcType(JdbcType type)
    {
        switch (type)
        {
            case GUID:
                return getGuidType();
            default:
                return _sqlTypeIntMap.get(type.sqlType);
        }
    }

    @Nullable
    public String sqlCastTypeNameFromJdbcType(JdbcType type)
    {
        return sqlTypeNameFromJdbcType(type);   // Override for alternate behavior
    }

    public abstract String sqlTypeNameFromSqlType(PropertyStorageSpec prop);


    protected String getDatabaseMaintenanceSql()
    {
        return null;
    }

    public void configureToDisableJdbcCaching(Connection connection, DbScope scope, SQLFragment sql) throws SQLException
    {
        // No-op by default
    }

    /**
     * @return any additional information that should be sent to the mothership in the case of a SQLException
     */
    public String getExtraInfo(SQLException e)
    {
        return null;
    }

    public static boolean isCancelException(SQLException x)
    {
        String sqlState = x.getSQLState();
        if (null == sqlState || !sqlState.startsWith("57"))
            return false;
        return sqlState.equals("57014"); // TODO verify SQL Server
    }


    public static boolean isTransactionException(Exception x)
    {
        if (x instanceof ConcurrencyFailureException)
            return true;
        if (x instanceof RuntimeSQLException)
            x = ((RuntimeSQLException)x).getSQLException();
        if (x instanceof SQLException)
        {
            String msg = StringUtils.defaultString(x.getMessage(), "");
            String state = StringUtils.defaultString(((SQLException)x).getSQLState(), "");
            return msg.toLowerCase().contains("deadlock") || state.startsWith("25") || state.startsWith("40");
        }
        return false;
    }

    public static boolean isObjectNotFoundException(Exception x)
    {
        SQLException sqlx = null;
        if (x instanceof BadSqlGrammarException)
            sqlx = ((BadSqlGrammarException)x).getSQLException();
        else if (x instanceof RuntimeSQLException)
            sqlx = ((RuntimeSQLException)x).getSQLException();
        else if (x instanceof SQLException)
            sqlx = (SQLException)x;
        if (null == sqlx)
            return false;
        String sqlstate = sqlx.getSQLState();
        return ("42P01".equals(sqlstate) || "42S02".equals(sqlstate) || "S0002".equals(sqlstate));
    }

    public static boolean isConfigurationException(Exception x)
    {
        SQLException sqlx = null;
        if (x instanceof RuntimeSQLException)
            sqlx = ((RuntimeSQLException)x).getSQLException();
        else if (x instanceof SQLException)
            sqlx = (SQLException)x;
        if (null == sqlx)
            return false;
        String sqlState = sqlx.getSQLState();
        if (null == sqlState)
            return false;
        return sqlState.equals("42501"); // Insufficient Privilege // TODO verify SQL Server
    }

    // JDBC driver has been loaded and first connection is about to be attempted. May be called multiple times on the
    // same driver.
    public void prepareDriver(Class<Driver> driverClass)
    {
    }

    // We're bootstrapping a new labkey database; do nothing by default
    public void prepareNewLabKeyDatabase(DbScope scope)
    {
    }

    // Core module is done upgrading. Do any required work in the core database, nothing by default.
    public void afterCoreUpgrade(ModuleContext context)
    {
    }

    // Do scope-specific initialization work for this dialect. Note: this might be called multiple times, for example,
    // during bootstrap or upgrade.
    public void prepare(DbScope scope)
    {
        initialize();
    }

    public abstract void prepareConnection(Connection conn) throws SQLException;

    // Post construction initialization that doesn't require a scope
    void initialize()
    {
        _stringHandler = createStringHandler();
    }

    // Called once when new scope is being prepared
    protected DialectStringHandler createStringHandler()
    {
        return new StandardDialectStringHandler();
    }

    public DialectStringHandler getStringHandler()
    {
        return _stringHandler;
    }

    // Set of keywords returned by DatabaseMetaData.getMetaData() plus the SQL 2003 keywords
    protected Set<String> getJdbcKeywords(SqlExecutor executor) throws SQLException, IOException
    {
        Set<String> keywordSet = new CaseInsensitiveHashSet();
        keywordSet.addAll(KeywordCandidates.get().getSql2003Keywords());
        String keywords = executor.getConnection().getMetaData().getSQLKeywords();
        keywordSet.addAll(new CsvSet(keywords));

        return keywordSet;
    }

    // Internal version number
    public int getDatabaseVersion()
    {
        return _databaseVersion;
    }

    public void setDatabaseVersion(int databaseVersion)
    {
        _databaseVersion = databaseVersion;
    }

    // Human readable product version number
    public String getProductVersion()
    {
        return _productVersion;
    }

    public void setProductVersion(String productVersion)
    {
        _productVersion = productVersion;
    }

    public abstract String getProductName();

    public @Nullable String getProductEdition()
    {
        return null;
    }

    public abstract String getSQLScriptPath();

    // Note: SQLFragment and StringBuilder both implement Appendable
    public abstract void appendStatement(Appendable sql, String statement);

    /**
     * Adds dialect-specific SQL that re-selects a value (e.g., from an auto-increment column) at INSERT or UPDATE time,
     * returning it either as a result set (proposedVariable = null) or into a SQL variable (proposedVariable = not null).
     * Limitations: Can only select an INTEGER value from a single column where a single row has been inserted or updated.
     * In the future, we may enhance support for other scenarios, but that's not needed yet.
     *
     * @param sql And INSERT or UPDATE statement that needs re-selecting
     * @param column Column from which to reselect
     * @param proposedVariable Null to return a result set via code; Not null to select the value into a SQL variable
     * @return If proposedVariable is not null then actual variable used in the SQL. Otherwise null. Callers using
     * proposedVariable must use the returned variable name in subsequent code, since it may differ from what was
     * proposed.
     */
    public abstract String addReselect(SQLFragment sql, ColumnInfo column, @Nullable String proposedVariable);

    // Could be INSERT, UPDATE, or DELETE statement
    public abstract @NotNull ResultSet executeWithResults(@NotNull PreparedStatement stmt) throws SQLException;

    private static final InClauseGenerator DEFAULT_GENERATOR = new ParameterMarkerInClauseGenerator();

    public SQLFragment appendInClauseSql(SQLFragment sql, @NotNull Collection<?> params)
    {
        return DEFAULT_GENERATOR.appendInClauseSql(sql, params);
    }

    public abstract boolean requiresStatementMaxRows();

    /**
     * Limit a SELECT query to the specified number of rows (Table.ALL_ROWS == no limit).
     * @param sql a SELECT query
     * @param maxRows return the first maxRows number of rows (Table.ALL_ROWS == no limit).
     * @return the query
     */
    public abstract SQLFragment limitRows(SQLFragment sql, int maxRows);

    /**
     * Composes the fragments into a SQL query that will be limited by rowCount
     * starting at the given 0-based offset.
     * 
     * @param select must not be null
     * @param from must not be null
     * @param filter may be null
     * @param order may be null
     * @param groupBy may be null
     * @param maxRows Table.ALL_ROWS means all rows, 0 (Table.NO_ROWS) means no rows, > 0 limits result set
     * @param offset 0 based   @return the query
     */
    public abstract SQLFragment limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, String order, String groupBy, int maxRows, long offset);

    /** Does the dialect support limitRows() with an offset? */
    public abstract boolean supportsOffset();

    public abstract boolean supportsComments();

    public abstract String execute(DbSchema schema, String procedureName, String parameters);

    public abstract SQLFragment execute(DbSchema schema, String procedureName, SQLFragment parameters);

    public abstract String concatenate(String... args);

    public abstract SQLFragment concatenate(SQLFragment... args);

    /**
     * Return the operator which, in addition to the usual LIKE things ('%' and '_'), also supports
     * character classes. (i.e. [abc] matching a,b or c)
     * If you do not need the functionality of character classes, then "LIKE" will work just fine with all SQL dialects.
     */
    public abstract String getCharClassLikeOperator();

    public abstract String getCaseInsensitiveLikeOperator();

    public abstract String getVarcharLengthFunction();

    public abstract String getStdDevFunction();

    public abstract String getClobLengthFunction();

    public abstract SQLFragment getStringIndexOfFunction(SQLFragment toFind, SQLFragment toSearch);

    public abstract String getSubstringFunction(String s, String start, String length);

    public abstract SQLFragment getSubstringFunction(SQLFragment s, SQLFragment start, SQLFragment length);

    public String getXorOperator()
    {
        return "^";
    }

    /**
     * Converts an EXISTS SQL fragment into an expression that returns true or false. Example fragments that could be passed:
     *
     *     EXISTS (SELECT 1 FROM core.Users)
     *     EXISTS (SELECT * FROM comm.Messages WHERE CreatedBy = ?) OR EXISTS (SELECT * FROM comm.Pages WHERE CreatedBy = ?)")
     *
     * The method wraps the fragment with syntax required by this database to produce a SQL statement that can be used with SELECT.
     * For example, PostgreSQL can SELECT EXISTS directly, but SQL Server can't... it requires wrapping with a CASE statement.
     */
    public abstract SQLFragment wrapExistsExpression(SQLFragment existsSQL);

    public abstract boolean supportsGroupConcat();

    // GroupConcat is usable as an aggregate function within a GROUP BY
    public SQLFragment getGroupConcat(SQLFragment sql, boolean distinct, boolean sorted)
    {
        return getGroupConcat(sql, distinct, sorted, "','");
    }

    public abstract SQLFragment getGroupConcat(SQLFragment sql, boolean distinct, boolean sorted, @NotNull String delimiterSQL);

    public abstract boolean supportsSelectConcat();

    // SelectConcat returns SQL that will generate a comma separated list of the results from the passed in select SQL.
    // This is not generally usable within a GROUP BY. Include distinct, order by, etc. in the selectSql if desired
    public abstract SQLFragment getSelectConcat(SQLFragment selectSql, String delimiter);

    public final void runSql(DbSchema schema, String sql, @Nullable UpgradeCode upgradeCode, ModuleContext moduleContext, @Nullable Connection conn)
    {
        SqlScriptExecutor parser = new SqlScriptExecutor(sql, getSQLScriptSplitPattern(), getSQLScriptProcPattern(), schema, upgradeCode, moduleContext, conn, getBooleanLiteral(true));
        parser.execute();
    }

    protected abstract @Nullable Pattern getSQLScriptSplitPattern();

    /**
     * @return A dialect-specific regex pattern for finding executeJavaCode and bulkImport stored procedure calls in a SQL script.
     *         The regex must match either procedure name plus the associated parameters and define these specific capturing groups:
     *              Group 2: executeJavaCode procedure name and parameter
     *              Group 3: executeJavaCode parameter value
     *              Group 4: bulkImport procedure name and parameters
     *              Group 5: bulkImport parameter #1 (schema name)
     *              Group 6: bulkImport parameter #2 (table name)
     *              Group 7: bulkImport parameter #3 (source filename)
     */
    protected abstract @NotNull Pattern getSQLScriptProcPattern();

    public abstract String getMasterDataBaseName();

    public abstract String getDefaultDateTimeDataType();

    public abstract String getUniqueIdentType();

    public abstract String getGuidType();

    public abstract String getLsidType();

    public abstract String getTempTableKeyword();

    public abstract String getTempTablePrefix();

    public abstract String getGlobalTempTablePrefix();

    public abstract boolean isNoDatabaseException(SQLException e);

    public abstract boolean isSortableDataType(String sqlDataTypeName);

    public abstract String getDropIndexCommand(String tableName, String indexName);

    public abstract String getCreateDatabaseSql(String dbName);

    public abstract String getTruncateSql(String tableName);

    public abstract String getCreateSchemaSql(String schemaName);

    /** @param part the java.util.Calendar field for the unit of time, such as Calendar.DATE or Calendar.MINUTE */
    public abstract String getDateDiff(int part, String value1, String value2);

    /** @param part the java.util.Calendar field for the unit of time, such as Calendar.DATE or Calendar.MINUTE */
    public abstract SQLFragment getDateDiff(int part, SQLFragment value1, SQLFragment value2);

    /** @param part the java.util.Calendar field for the unit of time, such as Calendar.DATE or Calendar.MINUTE */
    public abstract String getDatePart(int part, String value);

    /** @param expression The expression with datetime value for which a date value is desired */
    public abstract String getDateTimeToDateCast(String expression);

    public abstract String getRoundFunction(String valueToRound);


    // does provider support ROUND(double,x) where x != 0
    public abstract boolean supportsRoundDouble();

    /**
     * Does the dialect have native greatest() and least() methods, in which case they will be passed through,
     * or should an alternate explicit SQL construct be used instead?
     */
    public abstract boolean supportsNativeGreatestAndLeast();

    /**
     * The alternate SQL construct to use for dialects without native support for greatest() and least()
     * @param method "greatest" or "least"
     * @param arguments Arguments passed from the LK SQL
     * @return the dialect equivalent SQLFragrment
     */
    public SQLFragment getGreatestAndLeastSQL(String method, SQLFragment... arguments)
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
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

    private Set<String> systemTableSet = Sets.newCaseInsensitiveHashSet(new CsvSet(getSystemTableNames()));

    public boolean isSystemTable(String tableName)
    {
        return systemTableSet.contains(tableName);
    }

    public abstract boolean isSystemSchema(String schemaName);

    public boolean isReserved(String word)
    {
        return _reservedWordSet.contains(word);
    }


    public String getColumnSelectName(String columnName)
    {
        // Special case "*"... otherwise, just makeLegalIdentifier()
        if ("*".equals(columnName))
            return columnName;
        else
            return makeLegalIdentifier(columnName);
    }


    // Translates database metadata name into a name that can be used in a select.  Most dialects simply turn them into
    // legal identifiers (e.g., adding quotes if special symbols are present).
    public String getSelectNameFromMetaDataName(String metaDataName)
    {
        return makeLegalIdentifier(metaDataName);
    }

    // Create comma-separated list of legal identifiers
    public String makeLegalIdentifiers(String[] names)
    {
        return makeLegalIdentifiers(names, ", ");
    }

    // Create list of legal identifiers
    public String makeLegalIdentifiers(String[] names, String sep)
    {
        String s = "";
        StringBuilder sb = new StringBuilder();
        for (String name : names)
        {
            sb.append(s).append(makeLegalIdentifier(name));
            s = sep;
        }
        return sb.toString();
    }


    // If necessary, quote identifier
    public String makeLegalIdentifier(String id)
    {
        if (shouldQuoteIdentifier(id))
            return quoteIdentifier(id);
        else
            return id;
    }


    // Escape quotes and quote the identifier  // TODO: Move to DialectStringHandler?
    public String quoteIdentifier(String id)
    {
        return "\"" + id.replaceAll("\"", "\"\"") + "\"";
    }


    protected boolean shouldQuoteIdentifier(String id)
    {
        return isReserved(id) || !AliasManager.isLegalName(id);
    }


    public void testDialectKeywords(SqlExecutor executor)
    {
        Set<String> candidates = KeywordCandidates.get().getCandidates();
        Set<String> shouldAdd = new TreeSet<>();
        Set<String> shouldRemove = new TreeSet<>();

        // First, test the test: execute the test SQL with an identifier that definitely isn't a keyword. If this
        // fails, there's a syntax issue with the test SQL.
        if (isKeyword(executor, "abcdefghi"))
            throw new IllegalStateException("Legitimate identifier generated an error on " + getProductName());

        for (String candidate : candidates)
        {
            boolean reserved = isKeyword(executor, candidate);

            if (isReserved(candidate) != reserved)
            {
                if (reserved)
                {
                    if (!_reservedWordSet.contains(candidate))
                        shouldAdd.add(candidate);
                }
                else
                {
                    if (_reservedWordSet.contains(candidate))
                        shouldRemove.add(candidate);
                }
            }
        }

        if (!shouldAdd.isEmpty())
            throw new IllegalStateException("Need to add " + shouldAdd.size() + " keywords to " + getProductName() + " reserved word list: " + shouldAdd);

        if (!shouldRemove.isEmpty())
            LOG.info("Should remove " + shouldRemove.size() + " keywords from " + getClass().getName() + " reserved word list: " + shouldRemove);
    }


    protected boolean isKeyword(SqlExecutor executor, String candidate)
    {
        String sql = getIdentifierTestSql(candidate);

        try
        {
            executor.execute(sql);
            return false;
        }
        catch (Exception e)
        {
            return true;
        }
    }


    public void testKeywordCandidates(SqlExecutor executor) throws IOException, SQLException
    {
        Set<String> jdbcKeywords = getJdbcKeywords(executor);

        if (!KeywordCandidates.get().containsAll(jdbcKeywords, getProductName()))
            throw new IllegalStateException("JDBC keywords from " + getProductName() + " are not all in the keyword candidate list (sqlKeywords.txt)");

        if (!KeywordCandidates.get().containsAll(_reservedWordSet, getProductName()))
            throw new IllegalStateException(getProductName() + " reserved words are not all in the keyword candidate list (sqlKeywords.txt)");
    }

    public int getIdentifierMaxLength()
    {
        // 63 probably works, but save 2 chars for appending chars to
        // create aliases for extra tables used in the lookup (e.g. junctionAlias = getTableAlias() + "_j")
        return 61;
    }

    protected String getIdentifierTestSql(String candidate)
    {
        String keyword = getTempTableKeyword();
        String name = getTempTablePrefix() + candidate;

        return "SELECT " + candidate + " FROM (SELECT 1 AS " + candidate + ") x ORDER BY " + candidate + ";\n" +
               "CREATE " + keyword + " TABLE " + name + " (" + candidate + " VARCHAR(50));\n" +
               "DROP TABLE " + name + ";";
    }


    public final void checkSqlScript(String sql) throws SQLSyntaxException
    {
        // SQL script writers can choose to bypass our normal syntax checking by including a @SkipLabKeySyntaxCheck "annotation"
        // in a comment somewhere in the file. While typically not recommended, this is helpful in cases where clients provide
        // us SQL scripts that don't conform to our rules.
        if (sql.contains("@SkipLabKeySyntaxCheck"))
            return;

        Collection<String> errors = new ArrayList<>();
        String lower = sql.toLowerCase();
        String lowerNoWhiteSpace = lower.replaceAll("\\s", "");

        if (lowerNoWhiteSpace.contains("primarykey,"))
            errors.add("Do not designate PRIMARY KEY on the column definition line; this creates a PK with an arbitrary name, making it more difficult to change later. Instead, create the PK as a named constraint (e.g., PK_MyTable).");

        try
        {
            new SqlScanner(sql).stripComments();
        }
        catch (Exception e)
        {
            errors.add(e.getMessage());
        }

        checkSqlScript(lower, lowerNoWhiteSpace, errors);

        if (!errors.isEmpty())
            throw new SQLSyntaxException(errors);
    }


    abstract protected void checkSqlScript(String lower, String lowerNoWhiteSpace, Collection<String> errors);

    public String getJDBCArrayType(Object object)
    {
        return StringUtils.lowerCase(getSqlTypeNameFromObject(object));
    }

    public Collection<String> getScriptWarnings(String name, String sql)
    {
        return Collections.emptyList();
    }

    private static final TableResolver STANDARD_TABLE_RESOLVER = new StandardTableResolver();

    protected TableResolver getTableResolver()
    {
        return STANDARD_TABLE_RESOLVER;
    }

    public final void addTableInfoFactories(Map<String, SchemaTableInfoFactory> map, DbScope scope, String schemaName) throws SQLException
    {
        getTableResolver().addTableInfoFactories(map, scope, schemaName);
    }

    public final JdbcMetaDataLocator getJdbcMetaDataLocator(DbScope scope, @Nullable String schemaName, @Nullable String requestedTableName) throws SQLException
    {
        return getTableResolver().getJdbcMetaDataLocator(scope, schemaName, requestedTableName);
    }

    public final ForeignKeyResolver getForeignKeyResolver(DbScope scope, @Nullable String schemaName, @Nullable String tableName)
    {
        return getTableResolver().getForeignKeyResolver(scope, schemaName, tableName);
    }

    public abstract boolean canExecuteUpgradeScripts();

    public DatabaseMetaData wrapDatabaseMetaData(DatabaseMetaData md, DbScope scope)
    {
        return md;
    }

    /** Not all databases support checking of indices on non-tables (such as views) */
    public boolean canCheckIndices(TableInfo ti)
    {
        return true;
    }

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

    public Integer getSPID(Connection conn) throws SQLException
    {
        try (PreparedStatement stmt = conn.prepareStatement(getSIDQuery()); ResultSet rs = stmt.executeQuery())
        {
            if (!rs.next())
            {
                throw new SQLException("SID query returned no results");
            }
            return rs.getInt(1);
        }
    }

    public boolean updateStatistics(TableInfo table)
    {
        String sql = getAnalyzeCommandForTable(table.getSelectName());
        if (sql != null)
        {
            new SqlExecutor(table.getSchema()).execute(sql);
            return true;
        }
        else
            return false;
    }


    public abstract String getBooleanDataType();
    
    public String getBooleanTRUE()
    {
        return "CAST(1 AS " + getBooleanDataType() + ")";
    }

    public String getBooleanFALSE()
    {
        return "CAST(0 AS " + getBooleanDataType() + ")";
    }

    public abstract String getBooleanLiteral(boolean b);

    public abstract String getBinaryDataType();

    // We need to determine the database name from a data source, so we've implemented a helper that parses the JDBC connection
    // string for each driver we support. This is necessary because, unfortunately, there appears to be no standard, reliable
    // way to ask a JDBC driver for individual components of the URL or to programmatically assemble a new connection URL.
    // Driver.getPropertyInfo(), for example, doesn't return the database name on PostgreSQL if it's specified as part of the URL.
    //
    // Currently, JdbcHelper only finds the database name. It could be extended if we require querying other components or if
    // replacement/reassembly becomes necessary.
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
        return getJdbcHelper().getDatabase(url);
    }


    public abstract JdbcHelper getJdbcHelper();

    /**
     * Drop a schema if it exists.
     * Throws an exception if schema exists, and could not be dropped. 
     */
    public void dropSchema(DbSchema schema, String schemaName) throws SQLException
    {
        String sql = schema.getSqlDialect().execute(CoreSchema.getInstance().getSchema(), "fn_dropifexists", "?, ?, ?, ?");
        new SqlExecutor(schema).execute(sql, "*", schemaName, "SCHEMA", null);
    }

    /**
     * Drop an object (table, view) or subobject (index) if it exists
     *
     * @param schema  dbSchema in which the object lives
     * @param objectName the name of the table or view to be dropped, or the table on which the index is defined
     * @param objectType "TABLE", "VIEW", "INDEX"
     * @param subObjectName index name;  ignored if not an index
     */
    public void dropIfExists(DbSchema schema, String objectName, String objectType, @Nullable String subObjectName)
    {
        String sql = schema.getSqlDialect().execute(CoreSchema.getInstance().getSchema(), "fn_dropifexists", "?, ?, ?, ?");
        String schemaName = schema.getName();
        if (StringUtils.contains(objectName,".") && StringUtils.startsWith(objectName,getGlobalTempTablePrefix()))
        {
            schemaName = objectName.substring(0,objectName.indexOf("."));
            objectName = objectName.substring(objectName.indexOf(".")+1);
        }
        new SqlExecutor(schema).execute(sql, objectName, schemaName, objectType, subObjectName);
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

    // Substitute the parameter values into the SQL statement.
    public String substituteParameters(SQLFragment frag)
    {
        return _stringHandler.substituteParameters(frag);
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
            return callGetter(methodName);
        }

        private <K> K callGetter(String methodName) throws ServletException
        {
            try
            {
                Method getUrl = _ds.getClass().getMethod(methodName);
                return (K)getUrl.invoke(_ds);
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
            // Special handling for Tomcat JDBC connection pool; getPassword() returns a fixed string
            if ("org.apache.tomcat.jdbc.pool.DataSource".equals(_ds.getClass().getName()))
            {
                Properties props = callGetter("getDbProperties");
                return props.getProperty("password");
            }
            else
            {
                return getProperty("getPassword");
            }
        }
    }


    // All statement creation passes through these two methods.  We return our standard statement wrappers in most
    // cases, but dialects can return their own subclasses of StatementWrapper to work around JDBC driver bugs.
    public StatementWrapper getStatementWrapper(ConnectionWrapper conn, Statement stmt)
    {
        return new StatementWrapper(conn, stmt);
    }


    public StatementWrapper getStatementWrapper(ConnectionWrapper conn, Statement stmt, String sql)
    {
        return new StatementWrapper(conn, stmt, sql);
    }

    protected String getDatePartName(int part)
    {
        String partName;

        switch (part)
        {
            case Calendar.YEAR:
            {
                partName = "year";
                break;
            }
            case Calendar.MONTH:
            {
                partName = "month";
                break;
            }
            case Calendar.DAY_OF_MONTH:
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
            case Calendar.MILLISECOND:
            {
                partName = "millisecond";
                break;
            }
            default:
            {
                throw new IllegalArgumentException("Unsupported time unit: " + part);
            }
        }

        return partName;
    }


    /**
     *  ISO without the "T" "2012-02-03 11:01:03.000" like DateUtil.toISO()
     */
    public SQLFragment getISOFormat(SQLFragment date)
    {
        // ONLY primary dialects need to support this for now
        throw new UnsupportedOperationException();
    }


    public JdbcType getJdbcType(int type, String typeName)
    {
        JdbcType t = JdbcType.valueOf(type);
        if ((t == JdbcType.VARCHAR || t == JdbcType.CHAR) && null != typeName)
        {
            if (StringUtils.equalsIgnoreCase("entityid",typeName) ||
                StringUtils.equalsIgnoreCase("uniqueidentifier",typeName))
                t = JdbcType.GUID;
        }
        return t;
    }


    public SQLFragment implicitConvertToString(JdbcType from, SQLFragment sql)
    {
        if (from == JdbcType.GUID)
        {
            SQLFragment ret = new SQLFragment();
            ret.append("CAST(");
            ret.append(sql).append(" AS ");
            ret.append(sqlTypeNameFromSqlType(from.sqlType)).append("(36))");
            return ret;
        }
        // let server do default conversion
        return sql;
    }


    protected int getJdbcVersion(DbScope scope)
    {
        try (Connection conn = scope.getConnection())
        {
            DatabaseMetaData dbmd = conn.getMetaData();

            return Math.min(dbmd.getJDBCMajorVersion(), getTomcatJdbcVersion());
        }
        catch (SQLException e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
            return 2;
        }
    }


    private int getTomcatJdbcVersion()
    {
        int version = 2;  // Assume JDBC2
        String serverInfo = ModuleLoader.getServletContext().getServerInfo();

        if (serverInfo.startsWith("Apache Tomcat/"))
        {
            String[] versionParts = serverInfo.substring(14).split("\\.");
            int majorVersion = Integer.valueOf(versionParts[0]);

            if (majorVersion >= 6)
                version = 4;
        }

        return version;
    }

    // Return the SQL type name for this object if it can be found, otherwise null
    public @Nullable String getSqlTypeNameFromObject(Object o)
    {
        JdbcType jdbcType = JdbcType.valueOf(o.getClass());

        if (null == jdbcType)
            return null;

        return _sqlTypeIntMap.get(jdbcType.sqlType);
    }


    /**
     * Encode wildcard characters in search string used by LIKE operator to force exact match of those charactors.
     * Currently only encodes single wildcard character '_' and '%' used by LIKE, while more complicated pattern such as [], {} are not encoded.
     * Example:
     *      String searchString = "search_string";
     *      SqlFragment sqlFragment = ...
     *      ...
     *      sqlFragment.append(" LIKE ?");
     *      sqlFragment.add("_" + encodeLikeSearchString(searchString) + "%");
     *
     *    ##encodeLikeSearchString(searchString) will ensure exact match of substring "search_string" and won't treate the the underscore as a wildcard.
     * @param search
     * @return encoded search string
     */
    public String encodeLikeOpSearchString(String search)
    {
        return search;
    }

    // Return the interesting table types for this dialect. Types array is passed to DatabaseMetaData.getTables().
    public String[] getTableTypes()
    {
        return _tableTypes;
    }

    public abstract boolean canShowExecutionPlan();
    protected abstract Collection<String> getQueryExecutionPlan(DbScope scope, SQLFragment sql);

    // Public method ensures that execution plan queries are done in a transaction that is never committed.
    // This ensures that unusual connection settings and other side effects are always discarded.
    public final Collection<String> getExecutionPlan(DbScope scope, SQLFragment sql)
    {
        try (DbScope.Transaction ignored = scope.beginTransaction())
        {
            return getQueryExecutionPlan(scope, sql);
        }
    }

    // Add any database configuration warnings (e.g., missing aggregate function or deprecated database server version)
    // to display in the page header for administrators. This will be called:
    // - Only on the LabKey DataSource's dialect instance (not external data sources)
    // - After the core module has been upgraded and the dialect has been prepared for the last time, meaning the dialect
    //   should reflect the final database configuration
    public void addAdminWarningMessages(Collection<String> messages)
    {
    }

    public abstract List<String> getChangeStatements(TableChange change);

    public abstract void purgeTempSchema(Map<String, TempTableTracker> createdTableNames);

    // Track any tables found in the temp schema that aren't already known
    protected void trackTempTables(Map<String, TempTableTracker> createdTableNames) throws SQLException
    {
        Object noref = new Object();
        DbSchema schema = DbSchema.getTemp();
        Map<String, SchemaTableInfoFactory> tableInfoFactoryMap = DbSchema.loadTableMetaData(schema.getScope(), schema.getName(), false);
        for (String tableName : tableInfoFactoryMap.keySet())
        {
            String tempName = schema.getName() + "." + tableName;
            if (!createdTableNames.containsKey(tempName))
                TempTableTracker.track(tableName, noref);
        }
    }

    // Defragment an index, if necessary
    public void defragmentIndex(DbSchema schema, String tableSelectName, String indexName)
    {
        // By default do nothing
    }

    public boolean isTableExists(DbScope scope, String schema, String name)
    {
        /* Does not handle overloaded functions for dialects that support them (Postgres) */
        SQLFragment sqlf = new SQLFragment("SELECT 1 FROM information_schema.tables WHERE UPPER(table_schema) = UPPER(?) AND UPPER(table_name) = UPPER(?)");
        sqlf.add(schema);
        sqlf.add(name);
        return new SqlSelector(scope, sqlf).exists();
    }

    public boolean hasTriggers(DbSchema scope, String schema, String tableName)
    {
        SQLFragment sqlf = new SQLFragment("SELECT 1 FROM information_schema.triggers WHERE UPPER(event_object_schema) = UPPER(?) AND UPPER(event_object_table) = UPPER(?)");
        sqlf.add(schema);
        sqlf.add(tableName);
        return new SqlSelector(scope, sqlf).exists();
    }

    public boolean isTriggerExists(DbSchema scope, String schema, String triggerName)
    {
        SQLFragment sqlf = new SQLFragment("SELECT 1 FROM information_schema.triggers WHERE UPPER(trigger_schema) = UPPER(?) AND UPPER(trigger_name) = UPPER(?)");
        sqlf.add(schema);
        sqlf.add(triggerName);
        return new SqlSelector(scope, sqlf).exists();
    }

    public abstract boolean isCaseSensitive();
    public abstract boolean isEditable();
    public abstract boolean isSqlServer();
    public abstract boolean isPostgreSQL();
    public abstract boolean isOracle();

    /**
     *
     * @return true If the dialect is one supported for the backend LabKey database. ie, Postgres or SQL Server
     */
    public boolean isLabKeyDbDialect()
    {
        return isPostgreSQL() || isSqlServer();
    }

    public abstract ColumnMetaDataReader getColumnMetaDataReader(ResultSet rsCols, TableInfo table);
    public abstract PkMetaDataReader getPkMetaDataReader(ResultSet rs);

    /* Procedure- / function-related methods */

    public boolean isProcedureExists(DbScope scope, String schema, String name)
    {
        /* Does not handle overloaded functions for dialects that support them (Postgres) */
        SQLFragment sqlf = new SQLFragment("SELECT 1 FROM information_schema.routines WHERE UPPER(specific_schema) = UPPER(?) AND UPPER(routine_name) = UPPER(?)");
        sqlf.add(schema);
        sqlf.add(name);
        return new SqlSelector(scope, sqlf).exists();
    }

    public boolean isProcedureSupportsInlineResults()
    {
        return false;
    }

    public enum ParamTraits
    {
        direction,
        datatype,
        required
    }

    public final class MetadataParameterInfo
    {
        private Map<ParamTraits, Integer> paramTraits = new HashMap<>();
        private Object value = new Object();

        public MetadataParameterInfo(Map<ParamTraits, Integer> paramTraits)
        {
            this.paramTraits = paramTraits;
        }

        public Map<ParamTraits, Integer> getParamTraits()
        {
            return paramTraits;
        }

        public Object getParamValue()
        {
            return value;
        }

        public void setParamValue(Object value)
        {
            this.value = value;
        }
    }

    /**
     * Queries the database in a dialect-specific way to determine the procedure's parameter names, datatypes, and directions.
     * @param scope
     * @param procSchema
     * @param procName
     * @return A map of parameter name / ParameterInfo pairs
     * @throws SQLException
     */
    public abstract Map<String, MetadataParameterInfo> getParametersFromDbMetadata(DbScope scope, String procSchema, String procName) throws SQLException;

    /**
     * Build the dialect-specific string to call the procedure, with the correct number and placement of parameter placeholders
     * @param procSchema
     * @param procName
     * @param paramCount The total number of parameters to include in the invocation string
     * @param hasReturn  true if the procedure has a return code/status, false if not
     * @param assignResult true if the call string should include an assignment (e.g., "? = CALL...) Some dialects always need this; for others it is dependent on return type
     * @return
     */
    public abstract String buildProcedureCall(String procSchema, String procName, int paramCount, boolean hasReturn, boolean assignResult);

    /**
     * Register and set the input value for each INPUT or INPUT/OUTPUT parameter from the parameters map into the CallableStatement, and register
     * the output parameters.
     * @param scope
     * @param stmt
     * @param parameters
     * @param registerOutputAssignment true if the assigned result (see buildProcedureCall) of the proc also needs to be registered as an output parameter
     * @throws SQLException
     */
    public abstract void registerParameters(DbScope scope, CallableStatement stmt, Map<String, MetadataParameterInfo> parameters, boolean registerOutputAssignment) throws SQLException;

    /**
     * Read the values of each INPUT/OUTPUT or OUTPUT parameter, and write them into the parameters map.
     * @param scope
     * @param stmt
     * @param parameters
     * @return The return code/status from the procedure, if any. Return -1 if procedure does not have a return code.
     * @throws SQLException
     */
    public abstract int readOutputParameters(DbScope scope, CallableStatement stmt, Map<String, MetadataParameterInfo> parameters) throws SQLException;

    /**
     * Convert parameter names between dialect specific conventions (for example, SQL Server parameters have a "@" prefix), and plain
     * alphanumeric text.
     * @param name
     * @param dialectSpecific true to convert to dialect specific convention, false to convert to plain alphanumeric text
     * @return
     */
    public abstract String translateParameterName(String name, boolean dialectSpecific);


    public SQLFragment formatFunction(SQLFragment target, String fn, SQLFragment... arguments)
    {
        target.append(fn);
        target.append("(");
        String comma = "";
        for (SQLFragment argument : arguments)
        {
            target.append(comma);
            comma = ",";
            target.append(argument);
        }
        target.append(")");
        return target;
    }

    public SQLFragment formatJdbcFunction(String fn, SQLFragment... arguments)
    {
        SQLFragment ret = new SQLFragment();
        ret.append("{fn ");
        formatFunction(ret, fn, arguments);
        ret.append("}");
        return ret;
    }
}

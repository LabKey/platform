/*
 * Copyright (c) 2005-2018 Fred Hutchinson Cancer Research Center
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

import jakarta.servlet.ServletException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.collections.CopyOnWriteHashMap;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.ConnectionWrapper.Closer;
import org.labkey.api.data.Constraint;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseIdentifier;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbScope.LabKeyDataSource;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MetadataSqlSelector;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.PropertyStorageSpec.Index;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.Selector.ForEachBlock;
import org.labkey.api.data.SqlExecutingSelector.ConnectionFactory;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableChange;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TempTableTracker;
import org.labkey.api.data.dialect.LimitRowsSqlGenerator.LimitRowsCustomizer;
import org.labkey.api.data.dialect.LimitRowsSqlGenerator.StandardLimitRowsCustomizer;
import org.labkey.api.query.AliasManager;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.template.Warnings;
import org.labkey.remoteapi.collections.CaseInsensitiveHashMap;
import org.springframework.jdbc.BadSqlGrammarException;

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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Base dialect for PostgreSQL AND Redshift. IMPORTANT: Make sure everything added here applies to Redshift as well;
// if not, put it in PostgreSql92Dialect.
public abstract class BasePostgreSqlDialect extends SqlDialect
{
    // Issue 52190: Expose troubleshooting data that supports postgreSQL-specific analysis
    public static final String POSTGRES_SCHEMA_NAME = "postgres";

    private final Map<String, Integer> _domainScaleMap = new CopyOnWriteHashMap<>();
    private final AtomicBoolean _arraySortFunctionExists = new AtomicBoolean(false);

    private HtmlString _adminWarning = null;

    // Default to 9 and let newer versions be refreshed later
    private int _majorVersion = 9;

    // Specifies if this PostgreSQL server treats backslashes in string literals as normal characters (as per the SQL
    // standard) or as escape characters (old, non-standard behavior). As of PostgreSQL 9.1, the setting
    // standard_conforming_strings is on by default; before 9.1, it was off by default. We check the server setting
    // when we prepare a new DbScope and use this when we escape and parse string literals.
    private Boolean _standardConformingStrings = Boolean.TRUE;
    private PostgreSqlServerType _serverType = PostgreSqlServerType.PostgreSQL;

    public boolean getStandardConformingStrings()
    {
        // make sure we're not calling this before finishing instance init
        assert _standardConformingStrings != null;
        return _standardConformingStrings == null || _standardConformingStrings;
    }

    public void setStandardConformingStrings(boolean standardConformingStrings)
    {
        _standardConformingStrings = standardConformingStrings;
    }

    public PostgreSqlServerType getServerType()
    {
        return _serverType;
    }

    public void setServerType(PostgreSqlServerType serverType)
    {
        _serverType = serverType;
    }

    @Override
    protected @NotNull Set<String> getReservedWords()
    {
        return Sets.newCaseInsensitiveHashSet(new CsvSet(
            "all, analyse, analyze, and, any, array, as, asc, asymmetric, authorization, binary, both, case, cast, " +
            "check, collate, column, concurrently, constraint, create, cross, current_catalog, current_date, " +
            "current_role, current_schema, current_time, current_timestamp, current_user, default, deferrable, desc, " +
            "distinct, do, else, end, end-exec, except, false, fetch, for, foreign, freeze, from, full, grant, group, having, " +
            "ilike, in, initially, inner, intersect, into, is, isnull, join, leading, left, like, limit, localtime, " +
            "localtimestamp, natural, not, notnull, null, offset, on, only, or, order, outer, over, overlaps, placing, " +
            "primary, references, returning, right, select, session_user, similar, some, symmetric, table, then, to, " +
            "trailing, true, union, unique, user, using, variadic, verbose, when, where, window, with"));
    }

    @Override
    public StatementWrapper getStatementWrapper(ConnectionWrapper conn, Statement stmt)
    {
        StatementWrapper statementWrapper = super.getStatementWrapper(conn, stmt);
        configureStatementWrapper(statementWrapper);
        return statementWrapper;
    }

    @Override
    public SQLFragment getDatabaseSizeSql(String databaseName)
    {
        return new SQLFragment("SELECT pg_database_size(?)", databaseName);
    }

    @Override
    public StatementWrapper getStatementWrapper(ConnectionWrapper conn, Statement stmt, String sql)
    {
        StatementWrapper statementWrapper = super.getStatementWrapper(conn, stmt, sql);
        configureStatementWrapper(statementWrapper);
        return statementWrapper;
    }

    private void configureStatementWrapper(StatementWrapper statementWrapper)
    {
        try
        {
            //pgSQL JDBC driver will load all results locally unless this is set along with autoCommit=false on the connection
            statementWrapper.setFetchSize(1000);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    @Override
    protected void addSqlTypeNames(Map<String, Integer> sqlTypeNameMap)
    {
        //Added for PostgreSQL, which returns type names like "userid," not underlying type name
        sqlTypeNameMap.put("USERID", Types.INTEGER);
        sqlTypeNameMap.put("SERIAL", Types.INTEGER);
        sqlTypeNameMap.put("BIGSERIAL", Types.BIGINT);
        sqlTypeNameMap.put("BYTEA", Types.BINARY);
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

    @Override
    protected void addSqlTypeInts(Map<Integer, String> sqlTypeIntMap)
    {
        sqlTypeIntMap.put(Types.TINYINT, "SMALLINT");  // PostgreSQL doesn't support TINYINT

        sqlTypeIntMap.put(Types.BINARY, "BYTEA");
        sqlTypeIntMap.put(Types.BIT, "BOOLEAN");
        sqlTypeIntMap.put(Types.BOOLEAN, "BOOLEAN");
        sqlTypeIntMap.put(Types.CHAR, "CHAR");
        sqlTypeIntMap.put(Types.LONGVARBINARY, "LONGVARBINARY");
        sqlTypeIntMap.put(Types.LONGVARCHAR, "TEXT");
        sqlTypeIntMap.put(Types.VARCHAR, "VARCHAR");
        sqlTypeIntMap.put(Types.TIMESTAMP, "TIMESTAMP");
        sqlTypeIntMap.put(Types.DOUBLE, "DOUBLE PRECISION");
        sqlTypeIntMap.put(Types.FLOAT, "DOUBLE PRECISION");
    }

    @Override
    public boolean isSqlServer()
    {
        return false;
    }

    @Override
    public boolean isPostgreSQL()
    {
        return true;
    }

    @Override
    public boolean isOracle()
    {
        return false;
    }

    @Override
    public String getSQLScriptPath()
    {
        return "postgresql";
    }

    @Override
    public String getDefaultDateTimeDataType()
    {
        return "TIMESTAMP";
    }

    @Override
    public String getUniqueIdentType()
    {
        return "SERIAL";
    }

    @Override
    public String getGuidType()
    {
        return "VARCHAR(36)";
    }

    @Override
    public String getLsidType()
    {
        return "VARCHAR(300)";
    }

    public int getMajorVersion()
    {
        return _majorVersion;
    }

    public void setMajorVersion(int majorVersion)
    {
        _majorVersion = majorVersion;
    }

    @Override
    public String addReselect(SQLFragment sql, ColumnInfo column, @Nullable String proposedVariable)
    {
        var columnIdentifier = column.getSelectIdentifier();
        sql.append("\nRETURNING ").appendIdentifier(columnIdentifier);
        if (null != proposedVariable)
            sql.append(" INTO ").appendIdentifier(proposedVariable);

        return proposedVariable;
    }

    @Override
    public @NotNull ResultSet executeWithResults(@NotNull PreparedStatement stmt) throws SQLException
    {
        return stmt.executeQuery();
    }

    @Override
    public boolean requiresStatementMaxRows()
    {
        return false;
    }

    private static final LimitRowsCustomizer CUSTOMIZER = new StandardLimitRowsCustomizer(true);

    @Override
    public SQLFragment limitRows(SQLFragment frag, int maxRows)
    {
        return LimitRowsSqlGenerator.limitRows(frag, maxRows, 0, CUSTOMIZER);
    }

    @Override
    public SQLFragment limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, SQLFragment order, SQLFragment groupBy, int maxRows, long offset)
    {
        return LimitRowsSqlGenerator.limitRows(select, from, filter, order, groupBy, maxRows, offset, CUSTOMIZER);
    }

    @Override
    public boolean supportsOffset()
    {
        return true;
    }

    @Override
    public boolean supportsComments()
    {
        return true;
    }

    @Override
    public String execute(DbSchema schema, String procedureName, String parameters)
    {
        return "SELECT " + schema.getName() + "." + procedureName + "(" + parameters + ")";
    }

    @Override
    public SQLFragment execute(DbSchema schema, String procedureName, SQLFragment parameters)
    {
        SQLFragment select = new SQLFragment("SELECT " + schema.getName() + "." + procedureName + "(");
        select.append(parameters);
        select.append(")");
        return select;
    }

    @Override
    public String concatenate(String... args)
    {
        return StringUtils.join(args, " || ");
    }


    @Override
    public SQLFragment concatenate(SQLFragment... args)
    {
        SQLFragment ret = new SQLFragment();
        String op = "";
        for (SQLFragment arg : args)
        {
            ret.append(op).append(arg);
            op = " || ";
        }
        return ret;
    }


    @Override
    public String getCharClassLikeOperator()
    {
        return "SIMILAR TO";
    }

    @Override
    public String getCaseInsensitiveLikeOperator()
    {
        return "ILIKE";
    }

    @Override
    public String getVarcharLengthFunction()
    {
        return "length";
    }

    @Override
    public String getStdDevFunction()
    {
        return "stddev";
    }

    @Override
    public String getStdDevPopFunction()
    {
        return "stddev_pop";
    }

    @Override
    public String getVarianceFunction()
    {
        return "variance";
    }

    @Override
    public String getVarPopFunction()
    {
        return "var_pop";
    }

    @Override
    public String getClobLengthFunction()
    {
        return "length";
    }

    @Override
    public SQLFragment getStringIndexOfFunction(SQLFragment toFind, SQLFragment toSearch)
    {
        SQLFragment result = new SQLFragment("POSITION(");
        result.append(toFind);
        result.append(" IN ");
        result.append(toSearch);
        result.append(")");
        return result;
    }

    @Override
    public String getSubstringFunction(String s, String start, String length)
    {
        return "substr(" + s + ", " + start + ", " + length + ")";
    }

    @Override
    public SQLFragment getSubstringFunction(SQLFragment s, SQLFragment start, SQLFragment length)
    {
        return new SQLFragment("substr(").append(s).append(", ").append(start).append(", ").append(length).append(")");
    }

    @Override
    public String getXorOperator()
    {
        return "#";
    }

    @Override
    // PostgreSQL can SELECT boolean expressions like EXISTS, e.g., SELECT EXISTS (SELECT 1 WHERE RowId IN (1,3,4)) FROM core.Containers
    public SQLFragment wrapBooleanExpression(SQLFragment booleanSql)
    {
        return booleanSql;
    }

    @Override
    public boolean supportsGroupConcat()
    {
        return getServerType().supportsGroupConcat();
    }

    @Override
    public boolean supportsSelectConcat()
    {
        return true;
    }

    @Override
    public SQLFragment getSelectConcat(SQLFragment selectSql, String delimiter)
    {
        SQLFragment result = new SQLFragment("array_to_string(array(");
        result.append(selectSql);
        result.append("), ");
        result.append(getStringHandler().quoteStringLiteral(delimiter));
        result.append(")");
        return result;
    }

    @Override
    public SQLFragment getGroupConcat(SQLFragment sql, boolean distinct, boolean sorted, @NotNull SQLFragment delimiterSQL, boolean includeNulls)
    {
        // Sort function might not exist in external datasource; skip that syntax if not
        boolean useSortFunction = sorted && _arraySortFunctionExists.get();
        SQLFragment result = new SQLFragment();

        if (useSortFunction)
        {
            result.append("array_to_string(");
            result.append("core.sort(");   // TODO: Switch to use ORDER BY option inside array aggregate instead of our custom function
            result.append("array_agg(");
            if (distinct)
            {
                result.append("DISTINCT ");
            }

            if (includeNulls)
            {
                result.append("COALESCE(CAST(");
                result.append(sql);
                result.append(" AS VARCHAR), '')");
            }
            else
            {
                result.append(sql);
            }

            result.append(")"); // array_agg
            result.append(")"); // core.sort
        }
        else
        {
            result.append("string_agg(");
            if (distinct)
            {
                result.append("DISTINCT ");
            }

            if (includeNulls)
            {
                result.append("COALESCE(");
                result.append(sql);
                result.append("::text, '')");
            }
            else
            {
                result.append(sql);
                result.append("::text");
            }
        }

        result.append(", ");
        result.append(delimiterSQL);
        result.append(")"); // array_to_string | string_agg

        return result;
    }

    @Override
    protected String getSystemTableNames()
    {
        return "pg_logdir_ls";
    }

    @Override
    public boolean isSystemSchema(String schemaName)
    {
        return  schemaName.equals("public") ||
                schemaName.equals("information_schema") ||
                schemaName.equals("pg_catalog") ||
                schemaName.startsWith("pg_temp_") ||
                schemaName.startsWith("pg_toast_temp_");
    }

    @Override
    protected String getSIDQuery()
    {
        return "SELECT pg_backend_pid();";
    }

    @Override
    public String getBooleanDataType()
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
    public String getBinaryDataType()
    {
        return "BYTEA";
    }

    @Override
    public String getTempTableKeyword()
    {
        return "TEMPORARY";
    }

    @Override
    public String getTempTablePrefix()
    {
        return "";
    }

    @Override
    public String getGlobalTempTablePrefix()
    {
        return DbSchema.TEMP_SCHEMA_NAME + ".";
    }

    @Override
    public boolean isNoDatabaseException(SQLException e)
    {
        return "3D000".equals(e.getSQLState());
    }

    @Override
    public boolean isSortableDataType(String sqlDataTypeName)
    {
        return !"json".equals(sqlDataTypeName) && !"jsonb".equals(sqlDataTypeName);
    }

    @Override
    public String getDropIndexCommand(String tableName, String indexName)
    {
        return "DROP INDEX " + indexName;
    }

    @Override
    public String getCreateDatabaseSql(String dbName)
    {
        // This will handle both mixed case and special characters on PostgreSQL
        var legal = makeIdentifierFromMetaDataName(dbName);
        return new SQLFragment("CREATE DATABASE ").appendIdentifier(legal).append(" WITH ENCODING 'UTF8'").getRawSQL();
    }

    @Override
    public String getCreateSchemaSql(String schemaName)
    {
        if (!isLegalName(schemaName) || isReserved(schemaName))
            throw new IllegalArgumentException("Not a legal schema name: " + schemaName);

        //Quoted schema names are bad news
        return "CREATE SCHEMA " + schemaName;
    }

    @Override
    public String getTruncateSql(String tableName)
    {
        // To be consistent with MS SQL server, always restart the sequence.  Note that the default for postgres
        // is to continue the sequence but we don't have this option with MS SQL Server
        return "TRUNCATE TABLE " + tableName + " RESTART IDENTITY";
    }

    @Override
    public String getDateDiff(int part, String value1, String value2)
    {
        return getDateDiff(part, new SQLFragment(value1), new SQLFragment(value2)).getSQL();
    }

    @Override
    public SQLFragment getDateDiff(int part, SQLFragment value1, SQLFragment value2)
    {
        double divideBy;
        switch (part)
        {
            case Calendar.MONTH:
            {
                return new SQLFragment("((EXTRACT(YEAR FROM ").append(value1).append(") - EXTRACT(YEAR FROM ").append(value2).append(")) * 12 + EXTRACT(MONTH FROM ").append(value1).append(") - EXTRACT(MONTH FROM ").append(value2).append("))::INT");
            }
            case Calendar.YEAR:
            {
                return new SQLFragment("(EXTRACT(YEAR FROM ").append(value1).append(") - EXTRACT(YEAR FROM ").append(value2).append("))::INT");
            }
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
            case Calendar.MILLISECOND:
            {
                divideBy = .001;
                break;
            }
            default:
            {
                throw new IllegalArgumentException("Unsupported time unit: " + part);
            }
        }
        return new SQLFragment("(EXTRACT(EPOCH FROM (").append(value1).append(" - ").append(value2).append(")) / ").append(String.valueOf(divideBy)).append(")::INT");
    }

    @Override
    public String getDatePart(int part, String value)
    {
        return "EXTRACT(" + getDatePartName(part) + " FROM " + value + ")";
    }

    @Override
    public String getDateTimeToDateCast(String columnName)
    {
        return "DATE(" + columnName + ")";
    }

    @Override
    public String getDateTimeToTimeCast(String columnName)
    {
        return String.format("(%s::date + %s::time)", "'1970-01-01'", columnName);
    }

    @Override
    public String getRoundFunction(String valueToRound)
    {
        return "ROUND(" + valueToRound + "::double precision)";
    }

    @Override
    public boolean supportsRoundDouble()
    {
        return false;
    }

    @Override
    public void handleCreateDatabaseException(SQLException e) throws ServletException
    {
        if ("55006".equals(e.getSQLState()))
        {
            LOG.error("You must close down pgAdmin III and all other applications accessing PostgreSQL.");
            throw (new ServletException("Close down or disconnect pgAdmin III and all other applications accessing PostgreSQL", e));
        }
        else
        {
            super.handleCreateDatabaseException(e);
        }
    }


    @Override
    public void prepareDriver(Class<Driver> driverClass)
    {
        // PostgreSQL driver 42.0.0 added logging via the Java Logging API (java.util.logging). This caused the driver to
        // start logging SQLExceptions (such as the initial connection failure on bootstrap) to the console... harmless
        // but annoying. This code suppresses the driver logging.
        Logger pgjdbcLogger = LogManager.getLogManager().getLogger("org.postgresql");

        if (null != pgjdbcLogger)
            pgjdbcLogger.setLevel(Level.OFF);
    }


    // Make sure that the PL/pgSQL language is enabled in the associated database. If not, throw. Since 9.0, PostgreSQL has
    // shipped with PL/pgSQL enabled by default, so the check is no longer critical, but continue to verify just to be safe.
    @Override
    public void prepareNewLabKeyDatabase(DbScope scope)
    {
        if (new SqlSelector(scope, "SELECT * FROM pg_language WHERE lanname = 'plpgsql'").exists())
            return;

        String dbName = scope.getDatabaseName();
        String message = "PL/pgSQL is not enabled in the \"" + dbName + "\" database because it is not enabled in your Template1 master database.";
        String advice = "Use PostgreSQL's 'createlang' command line utility to enable PL/pgSQL in the \"" + dbName + "\" database then restart Tomcat.";

        throw new ConfigurationException(message, advice);
    }

    @Override
    public void prepare(LabKeyDataSource dataSource)
    {
        // PostgreSQL JDBC driver introduced caching of PreparedStatements starting with 9.4.1202, with no provision for uncaching.
        // This has caused many problems. See Issue 26116 and Issue 49216.
        if (dataSource.isPrimary())
        {
            dataSource.setConnectionProperty("preparedThreshold", "0");
            dataSource.setConnectionProperty("preparedStatementCacheQueries", "0");
        }
    }

    @Override
    public String prepare(DbScope scope)
    {
        initializeUserDefinedTypes(scope);
        determineSettings(scope);
        determineIfArraySortFunctionExists(scope);
        return super.prepare(scope);
    }

    @Override
    public void prepareConnection(Connection conn)
    {
    }

    // When a new PostgreSQL DbScope is created, we enumerate the domains (user-defined types) in the public schema
    // of the datasource, determine their "scale," and stash that information in a map associated with the DbScope.
    // When the PostgreSQLColumnMetaDataReader reads metadata, it returns these scale values for all domains.
    private void initializeUserDefinedTypes(DbScope scope)
    {
        // Skip domains query if connecting to LabKey Server - it has no user-defined types
        if (getServerType().supportsSpecialMetadataQueries())
        {
            Selector selector = new SqlSelector(scope, "SELECT * FROM information_schema.domains");
            selector.forEach(rs -> {
                String schemaName = rs.getString("domain_schema");
                String domainName = rs.getString("domain_name");
                String dataType = rs.getString("data_type");
                int scale;

                if (dataType.startsWith("character"))
                {
                    String maxLength = rs.getString("character_maximum_length");

                    // VARCHAR with no specific size has null maxLength... but character_octet_length seems okay
                    scale = Integer.valueOf(null != maxLength ? maxLength : rs.getString("character_octet_length"));
                }
                else
                {
                    // Assume everything else is an integer for now. We should support more types for better external schema handling.
                    scale = 4;
                }

                String key = getDomainKey(schemaName, domainName);
                _domainScaleMap.put(key, scale);
            });
        }
    }


    private String getDomainKey(String schemaName, String domainName)
    {
        // Domain names are returned from column metadata fully qualified and quoted, so save them that way. See #26149.
        return ("public".equals(schemaName) ? domainName : "\"" + schemaName + "\".\"" + domainName + "\"");
    }

    // Query any settings that may affect dialect behavior. Right now, only "standard_conforming_strings".
    protected void determineSettings(DbScope scope)
    {
        if (getServerType().supportsSpecialMetadataQueries())
        {
            Selector selector = new SqlSelector(scope, "SELECT setting FROM pg_settings WHERE name = 'standard_conforming_strings'");
            _standardConformingStrings = "on".equalsIgnoreCase(selector.getObject(String.class));
        }
    }


    // Does this datasource include our sort array function? The LabKey datasource should always have it, but external datasources might not
    private void determineIfArraySortFunctionExists(DbScope scope)
    {
        if (getServerType().supportsSpecialMetadataQueries())
        {
            Selector selector = new SqlSelector(scope, "SELECT * FROM pg_catalog.pg_namespace n INNER JOIN pg_catalog.pg_proc p ON pronamespace = n.oid WHERE nspname = 'core' AND proname = 'sort'");
            _arraySortFunctionExists.set(selector.exists());
        }

        // Array sort function should always exist in LabKey scope (for now)
        assert !scope.isLabKeyScope() || _arraySortFunctionExists.get();
    }


    /**
     * Wrap one or more INSERT statements to allow explicit specification
     * of values for autoincrementing columns (e.g. IDENTITY in SQL Server
     * or SERIAL in Postgres). The input StringBuilder is modified.
     *
     * @param statements the insert statements. If more than one,
     *                   they must have been joined by appendStatement
     *                   and must all refer to the same table.
     * @param tinfo      table used in the insert(s)
     */
    @Override
    public void overrideAutoIncrement(StringBuilder statements, TableInfo tinfo)
    {
        // Nothing special to do for the PostgreSQL dialect
    }

    @Override
    public DatabaseIdentifier makeIdentifierFromMetaDataName(String metaDataName)
    {
        // In addition to quoting keywords and names with special characters, quote any name with an upper case
        // character. PostgreSQL normally stores column/table names in all lower case, so an upper case character
        // coming out of metadata means the name must have been quoted at creation time and needs to be quoted. #11181
        if (StringUtilsLabKey.containsUpperCase(metaDataName))
            return new _DatabaseIdentifier(metaDataName, new SQLFragment().appendIdentifier(quoteIdentifier(metaDataName)), this);
        else
            return super.makeIdentifierFromMetaDataName(metaDataName);
    }

    // Create a DatabaseIdentifier for the desired alias
    @Override
    public DatabaseIdentifier makeDatabaseIdentifier(String alias)
    {
        if (isIdentifierTooLong(alias))
            throw new UnsupportedOperationException("Name is too long: " + alias);

        // TODO always quote, for now be as backward compatible as possible
        if (shouldQuoteIdentifier(alias))
        {
            return new _DatabaseIdentifier(alias, quoteIdentifier(alias), this);
        }
        else
        {
            return new _DatabaseIdentifier(alias.toLowerCase(), alias, this);
        }
    }

    private static final Pattern PROC_PATTERN = Pattern.compile("^\\s*SELECT\\s+core\\.(executeJava(?:Upgrade|Initialization)Code\\s*\\(\\s*'(.+)'\\s*\\))\\s*;\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    @NotNull
    @Override
    protected Pattern getSQLScriptProcPattern()
    {
        return PROC_PATTERN;
    }

    @Override
    protected void checkSqlScript(String lowerNoComments, String lowerNoCommentsNoWhiteSpace, Collection<String> errors)
    {
        if (lowerNoCommentsNoWhiteSpace.contains("setsearch_pathto"))
            errors.add("Do not use \"SET search_path TO <schema>\". Instead, schema-qualify references to all objects.");

        if (!lowerNoCommentsNoWhiteSpace.endsWith(";"))
            errors.add("Script must end with a semicolon");
    }

    @Override
    public String getJDBCArrayType(Object object)
    {
        // The Postgres JDBC driver doesn't support "double precision" as the data type for a JDBC array, so use
        // alternative mappings for Float and Double
        if (object instanceof Float)
        {
            return "real";
        }
        else if (object instanceof Double)
        {
            return "numeric";
        }
        return super.getJDBCArrayType(object);
    }


    @Override
    public boolean canExecuteUpgradeScripts()
    {
        return true;
    }


    @Override
    public String getDefaultDatabaseName()
    {
        return "template1";
    }


    /*
        PostgreSQL example connection URLs we need to parse:

        jdbc:postgresql:database
        jdbc:postgresql://host/database
        jdbc:postgresql://host:port/database
        jdbc:postgresql:database?user=fred&password=secret&ssl=true
        jdbc:postgresql://host/database?user=fred&password=secret&ssl=true
        jdbc:postgresql://host:port/database?user=fred&password=secret&ssl=true
    */

    @Override
    protected @Nullable String getDatabaseMaintenanceSql()
    {
        return "VACUUM ANALYZE;";
    }

    @Override
    public SQLFragment sqlLocate(SQLFragment littleString, SQLFragment bigString)
    {
        SQLFragment ret = new SQLFragment(" POSITION(");
        ret.append(littleString);
        ret.append(" IN ");
        ret.append(bigString);
        ret.append(") ");
        return ret;
    }

    @Override
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

    @Override
    public boolean allowSortOnSubqueryWithoutLimit()
    {
        return true;
    }


    @Override
    public List<SQLFragment> getChangeStatements(TableChange change)
    {
        List<SQLFragment> result = new ArrayList<>();
        switch (change.getType())
        {
            case CreateTable -> result.addAll(getCreateTableStatements(change));
            case DropTable -> {
                SQLFragment f = new SQLFragment("DROP TABLE ");
                f.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
                result.add(f);
            }
            case AddColumns -> result.addAll(getAddColumnsStatements(change));
            case DropColumns -> result.add(getDropColumnsStatement(change));
            case RenameColumns -> result.addAll(getRenameColumnsStatement(change));
            case DropIndicesByName -> result.addAll(getDropIndexByNameStatements(change));
            case AddIndices -> result.addAll(getCreateIndexStatements(change));
            case ResizeColumns, ChangeColumnTypes -> result.addAll(getChangeColumnTypeStatement(change));
            case DropConstraints -> result.addAll(getDropConstraintsStatement(change));
            case AddConstraints -> result.addAll(getAddConstraintsStatement(change));
            default -> throw new IllegalArgumentException("Unsupported change type: " + change.getType());
        }

        return result;
    }

    private Collection<? extends SQLFragment> getDropIndexByNameStatements(TableChange change)
    {
        List<SQLFragment> statements = new ArrayList<>();
        for (String indexName : change.getIndicesToBeDroppedByName())
        {
            statements.add(getDropIndexCommand(change, indexName));
        }
        return statements;
    }

    private SQLFragment getDropIndexCommand(TableChange change, String indexName)
    {
        SQLFragment f = new SQLFragment("DROP INDEX ");
        f.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(indexName);
        return f;
    }


    /**
     * Generate the Alter Table statement to change the size or type of the column
     * <p>
     * NOTE: expects data size check to be done prior,
     *       will throw a SQL exception if not able to change size due to existing data
     */
    private List<SQLFragment> getChangeColumnTypeStatement(TableChange change)
    {
        List<SQLFragment> statements = new ArrayList<>();

        // Postgres allows executing multiple ALTER COLUMN statements under one ALTER TABLE
        List<SQLFragment> nonDateTimeClauses = new ArrayList<>();

        for (PropertyStorageSpec column : change.getColumns())
        {
            DatabaseIdentifier columnIdent = makePropertyIdentifier(column.getName());
            if (column.getJdbcType().isDateOrTime())
            {
                String tempColumnName = column.getName() + "~~temp~~";
                DatabaseIdentifier tempColumnIdent = makePropertyIdentifier(tempColumnName);

                // 1) ADD temp column
                SQLFragment addTemp = new SQLFragment("ALTER TABLE ");
                addTemp.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
                addTemp.append(" ADD COLUMN ").append(getSqlColumnSpec(column, tempColumnName));
                statements.add(addTemp);

                // 2) UPDATE: copy casted value to temp column
                SQLFragment update = new SQLFragment("UPDATE ");
                update.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
                update.append(" SET ").appendIdentifier(tempColumnIdent);
                update.append(" = CAST(").appendIdentifier(columnIdent).append(" AS ").append(getSqlTypeName(column)).append(")");
                statements.add(update);

                // 3) DROP original column
                SQLFragment drop = new SQLFragment("ALTER TABLE ");
                drop.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
                drop.append(" DROP COLUMN ").appendIdentifier(columnIdent);
                statements.add(drop);

                // 4) RENAME temp column to original column name
                SQLFragment rename = new SQLFragment("ALTER TABLE ");
                rename.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
                rename.append(" RENAME COLUMN ").appendIdentifier(tempColumnIdent).append(" TO ").appendIdentifier(columnIdent);
                statements.add(rename);
            }
            else
            {
                String dbType;
                if (column.getJdbcType().isText())
                {
                    // Using the common default max size to make type change to text
                    dbType = column.getSize() == -1 || column.getSize() > SqlDialect.MAX_VARCHAR_SIZE ?
                            getSqlTypeName(JdbcType.LONGVARCHAR) :
                            getSqlTypeName(column.getJdbcType()) + "(" + column.getSize().toString() + ")";
                }
                else if (column.getJdbcType().isDecimal())
                {
                    dbType = getSqlTypeName(column.getJdbcType()) + DEFAULT_DECIMAL_SCALE_PRECISION;
                }
                else
                {
                    dbType = getSqlTypeName(column.getJdbcType());
                }

                SQLFragment clause = new SQLFragment();
                clause.append("ALTER COLUMN ").appendIdentifier(columnIdent).append(" TYPE ").append(dbType);
                nonDateTimeClauses.add(clause);
            }
        }

        if (!nonDateTimeClauses.isEmpty())
        {
            SQLFragment alter = new SQLFragment("ALTER TABLE ");
            alter.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
            alter.append(" ");
            String sep = "";
            for (SQLFragment c : nonDateTimeClauses)
            {
                alter.append(sep).append(c);
                sep = ", ";
            }
            statements.add(alter);
        }

        return statements;
    }

    private List<SQLFragment> getRenameColumnsStatement(TableChange change)
    {
        List<SQLFragment> statements = new ArrayList<>();
        for (Map.Entry<String, String> oldToNew : change.getColumnRenames().entrySet())
        {
            DatabaseIdentifier oldIdentifier = makePropertyIdentifier(oldToNew.getKey());
            DatabaseIdentifier newIdentifier = makePropertyIdentifier(oldToNew.getValue());
            if (!oldIdentifier.equals(newIdentifier))
            {
                SQLFragment f = new SQLFragment("ALTER TABLE ");
                f.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
                f.append(" RENAME COLUMN ").appendIdentifier(oldIdentifier).append(" TO ").appendIdentifier(newIdentifier);
                statements.add(f);
            }
        }

        // TODO: This loop should not guess the name of the old indices; instead, it should look them up.
        // TableChange.setIndexedColumns() could set _indexRenames providing the name, and then this code uses that info.
        // Or maybe schemaTableInfo.getAllIndices() and then use Index.isSameIndex() to find names. Issue 53838.
        for (Map.Entry<Index, Index> oldToNew : change.getIndexRenames().entrySet())
        {
            Index oldIndex = oldToNew.getKey();
            Index newIndex = oldToNew.getValue();
            String oldName = nameIndex(change.getTableName(), oldIndex.columnNames); // TODO: Look up name
            String newName = nameIndex(change.getTableName(), newIndex.columnNames);
            if (!oldName.equals(newName))
            {
                SQLFragment f = new SQLFragment("ALTER INDEX ");
                f.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(oldName);
                f.append(" RENAME TO ").appendIdentifier(newName);
                statements.add(f);
            }
        }

        return statements;
    }

    private SQLFragment getDropColumnsStatement(TableChange change)
    {
        List<SQLFragment> sqlParts = new ArrayList<>();
        for (PropertyStorageSpec prop : change.getColumns())
        {
            SQLFragment sql = new SQLFragment("DROP COLUMN ");
            if (prop.getExactName())
            {
                sql.append(quoteIdentifier(prop.getName()));
            }
            else
            {
                sql.appendIdentifier(makePropertyIdentifier(prop.getName()));
            }
            sqlParts.add(sql);
        }

        SQLFragment f = new SQLFragment("ALTER TABLE ");
        f.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
        f.append(" ").append(sqlParts, ", ");
        return f;
    }

    // TODO if there are cases where user-defined columns need indices, this method will need to support
    // creating indices like getCreateTableStatement does.

    private List<SQLFragment> getAddColumnsStatements(TableChange change)
    {
        List<SQLFragment> statements = new ArrayList<>();
        String pkColumn = null;
        Constraint constraint = null;

        List<SQLFragment> columnSpecs = new ArrayList<>();
        for (PropertyStorageSpec prop : change.getColumns())
        {
            columnSpecs.add(getSqlColumnSpec(prop));
            if (prop.isPrimaryKey())
            {
                assert null == pkColumn : "no more than one primary key defined";
                pkColumn = prop.getName();
                constraint = new Constraint(change.getTableName(), Constraint.CONSTRAINT_TYPES.PRIMARYKEY, false, null);
            }
        }

        SQLFragment alter = new SQLFragment("ALTER TABLE ");
        alter.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
        alter.append(" ");
        String sep = "";
        for (SQLFragment col : columnSpecs)
        {
            alter.append(sep);
            alter.append("ADD COLUMN ");
            alter.append(col);
            sep = ", ";
        }
        statements.add(alter);
        if (null != pkColumn)
        {
            SQLFragment addPk = new SQLFragment("ALTER TABLE ");
            addPk.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
            addPk.append(" ADD CONSTRAINT ").appendIdentifier(constraint.getName())
                 .append(" ").append(constraint.getType().toString()).append(" (")
                 .appendIdentifier(makePropertyIdentifier(pkColumn)).append(")");
            statements.add(addPk);
        }

        return statements;
    }

    private List<SQLFragment> getDropConstraintsStatement(TableChange change)
    {
        return change.getConstraints().stream().map(constraint -> {
            SQLFragment f = new SQLFragment("ALTER TABLE ");
            f.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
            f.append(" DROP CONSTRAINT ").appendIdentifier(constraint.getName());
            return f;
        }).collect(Collectors.toList());
    }

    private List<SQLFragment> getAddConstraintsStatement(TableChange change)
    {
        List<SQLFragment> statements = new ArrayList<>();
        Collection<Constraint> constraints = change.getConstraints();

        if (null!=constraints && !constraints.isEmpty())
        {
            statements = constraints.stream().map(constraint -> {
                List<SQLFragment> columns = new ArrayList<>();
                for (String col : constraint.getColumns())
                {
                    columns.add(new SQLFragment().appendIdentifier(col));
                }

                SQLFragment f = new SQLFragment();
                f.append("DO $$\nBEGIN\nIF NOT EXISTS\n(SELECT 1 FROM information_schema.constraint_column_usage\nWHERE table_name = ")
                        .appendStringLiteral(change.getSchemaName() + "." + change.getTableName(), this)
                        .append(" and constraint_name = ")
                        .appendStringLiteral(constraint.getName(), this)
                        .append(") THEN\nALTER TABLE ");
                f.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
                f.append(" ADD CONSTRAINT ").appendIdentifier(constraint.getName()).append(" ")
                        .append(constraint.getType().toString()).append(" (")
                        .append(columns, ",")
                        .append(")").appendEOS().append("\nEND IF)").appendEOS().append("\nEND$$").appendEOS();
                return f;
            }).collect(Collectors.toList());
        }

        return statements;
    }

    private List<SQLFragment> getCreateTableStatements(TableChange change)
    {
        List<SQLFragment> statements = new ArrayList<>();
        List<SQLFragment> createTableSqlParts = new ArrayList<>();
        String pkColumn = null;
        for (PropertyStorageSpec prop : change.getColumns())
        {
            createTableSqlParts.add(getSqlColumnSpec(prop));
            if (prop.isPrimaryKey())
            {
                assert null == pkColumn : "no more than one primary key defined";
                pkColumn = prop.getName();
            }
        }

        for (PropertyStorageSpec.ForeignKey foreignKey : change.getForeignKeys())
        {
            DbSchema schema = DbSchema.get(foreignKey.getSchemaName(), DbSchemaType.Module);
            TableInfo tableInfo = foreignKey.isProvisioned() ?
                    foreignKey.getTableInfoProvisioned() :
                    schema.getTable(foreignKey.getTableName());
            String constraintName = "fk_" + foreignKey.getColumnName() + "_" + change.getTableName() + "_" + tableInfo.getName();
            SQLFragment fkFrag = new SQLFragment("CONSTRAINT ");
            fkFrag.appendIdentifier(constraintName)
                  .append(" FOREIGN KEY (")
                  .appendIdentifier(makePropertyIdentifier(foreignKey.getColumnName()))
                  .append(") REFERENCES ")
                  .appendIdentifier(tableInfo.getSchema().getName()).append(".").appendIdentifier(tableInfo.getName())
                  .append(" (")
                  .appendIdentifier(makePropertyIdentifier(foreignKey.getForeignColumnName()))
                  .append(")");
            createTableSqlParts.add(fkFrag);
        }

        SQLFragment create = new SQLFragment("CREATE TABLE ");
        create.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
        create.append(" (").append(createTableSqlParts, ", ").append(")");
        statements.add(create);
        if (null != pkColumn)
        {
            // Making this just for consistent naming
            Constraint constraint = new Constraint(change.getTableName(), Constraint.CONSTRAINT_TYPES.PRIMARYKEY, false, null);

            SQLFragment addPk = new SQLFragment("ALTER TABLE ");
            addPk.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
            addPk.append(" ADD CONSTRAINT ").appendIdentifier(constraint.getName())
                 .append(" ").append(constraint.getType().toString()).append(" (")
                 .appendIdentifier(makePropertyIdentifier(pkColumn)).append(")");
            statements.add(addPk);
        }

        statements.addAll(getCreateIndexStatements(change));
        statements.addAll(getAddConstraintsStatement(change));
        return statements;
    }

    private List<SQLFragment> getCreateIndexStatements(TableChange change)
    {
        List<SQLFragment> statements = new ArrayList<>();
        for (Index index : change.getIndexedColumns())
        {
            String newIndexName = nameIndex(change.getTableName(), index.columnNames);
            SQLFragment f = new SQLFragment("CREATE ");
            if (index.isUnique)
                f.append("UNIQUE ");
            f.append("INDEX ").appendIdentifier(newIndexName).append(" ON ");
            f.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
            f.append(" (");
            String separator = "";
            for (String columnName : index.columnNames)
            {
                f.append(separator).appendIdentifier(makePropertyIdentifier(columnName));
                separator = ", ";
            }
            f.append(")");
            f.appendEOS();
            statements.add(f);

            if (index.isClustered)
            {
                SQLFragment c = new SQLFragment();
                c.append(PropertyStorageSpec.CLUSTER_TYPE.CLUSTER.toString()).append(" ");
                c.appendIdentifier(change.getSchemaName()).append(".").appendIdentifier(change.getTableName());
                c.append(" USING ").appendIdentifier(newIndexName);
                statements.add(c);
            }
        }
        return statements;
    }

    @Override
    public String nameIndex(String tableName, String[] indexedColumns)
    {
        return AliasManager.makeLegalName(tableName + '_' + StringUtils.join(indexedColumns, "_"), this);
    }

    private SQLFragment getSqlColumnSpec(PropertyStorageSpec prop)
    {
        return getSqlColumnSpec(prop, prop.getName());
    }

    private SQLFragment getSqlColumnSpec(PropertyStorageSpec prop, String columnName)
    {
        SQLFragment colSpec = new SQLFragment();
        colSpec.appendIdentifier(makePropertyIdentifier(columnName)).append(" ");
        colSpec.append(getSqlTypeName(prop));

        // Apply size and precision to varchar and Decimal types
        if (prop.getJdbcType() == JdbcType.VARCHAR && prop.getSize() != -1 && prop.getSize() <= SqlDialect.MAX_VARCHAR_SIZE)
        {
            colSpec.append("(").append(prop.getSize().toString()).append(")");
        }
        else if (prop.getJdbcType() == JdbcType.DECIMAL)
        {
            colSpec.append(DEFAULT_DECIMAL_SCALE_PRECISION);
        }

        if (prop.isPrimaryKey() || !prop.isNullable())
            colSpec.append(" NOT NULL");

        if (null != prop.getDefaultValue())
        {
            if (prop.getJdbcType() == JdbcType.BOOLEAN)
            {
                colSpec.append(" DEFAULT ");
                colSpec.append((Boolean)prop.getDefaultValue() ? getBooleanTRUE() : getBooleanFALSE());
            }
            else if (prop.getJdbcType() == JdbcType.VARCHAR)
            {
                colSpec.append(" DEFAULT ");
                colSpec.append(getStringHandler().quoteStringLiteral(prop.getDefaultValue().toString()));
            }
            else
            {
                throw new IllegalArgumentException("Default value on type " + prop.getJdbcType().name() + " is not supported.");
            }
        }
        return colSpec;
    }

    @Override
    public String getSqlTypeName(PropertyStorageSpec prop)
    {
        if (prop.isAutoIncrement())
        {
            if (prop.getJdbcType() == JdbcType.INTEGER)
            {
                return "SERIAL";
            }
            else if (prop.getJdbcType() == JdbcType.BIGINT)
            {
                return "BIGSERIAL";
            }
            else
            {
                throw new IllegalArgumentException("AutoIncrement is not supported for JdbcType " + prop.getJdbcType() + " (" + getSqlTypeName(prop.getJdbcType()) + ")");
            }
        }
        else if (prop.getJdbcType() == JdbcType.GUID)
        {
            // Create EntityId columns using our custom ENTITYID type. We recognize this type and translate to JdbcType.GUID when
            // reading meta data. TODO: But wait... why doesn't getGuidType() return "ENTITYID"? If it did, this clause would go away.
            return "ENTITYID";
        }
        //If varchar longer than common limit, then switch type to Text
        else if (prop.getJdbcType() == JdbcType.VARCHAR && (prop.getSize() == -1 || prop.getSize() > SqlDialect.MAX_VARCHAR_SIZE))
        {
            return getSqlTypeName(JdbcType.LONGVARCHAR);
        }
        else
        {
            return getSqlTypeName(prop.getJdbcType());
        }
    }

    /**
     * We've historically created lower-cased column names in provisioned tables in Postgres. Keep doing that
     * for consistency, though ideally we'd stop doing this and update all existing provisioned tables.
     */
    private DatabaseIdentifier makePropertyIdentifier(String name)
    {
        if (isIdentifierTooLong(name))
            throw new UnsupportedOperationException("Name is too long: " + name);
        return new _DatabaseIdentifier(name, quoteIdentifier(name.toLowerCase()), this);
    }

    @Override
    public void purgeTempSchema(Map<String, TempTableTracker> createdTableNames)
    {
        try
        {
            trackTempTables(createdTableNames);
        }
        catch (SQLException e)
        {
            LOG.warn("error cleaning up temp schema", e);
        }

        DbSchema coreSchema = CoreSchema.getInstance().getSchema();
        SqlExecutor executor = new SqlExecutor(coreSchema);

        //rs = conn.getMetaData().getFunctions(dbName, tempSchemaName, "%");

        new SqlSelector(coreSchema, "SELECT proname AS SPECIFIC_NAME, CAST(proargtypes AS VARCHAR) FROM pg_proc WHERE pronamespace=(select oid from pg_namespace where nspname = ?)", DbSchema.getTemp().getName()).forEach(
            new ForEachBlock<>()
            {
                private Map<String, String> _types = null;

                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    if (null == _types)
                    {
                        _types = new HashMap<>();
                        new SqlSelector(coreSchema, "SELECT CAST(oid AS VARCHAR) as oid, typname, (select nspname from pg_namespace where oid = typnamespace) as nspname FROM pg_type").forEach(type ->
                                _types.put(type.getString(1), quoteIdentifier(type.getString(3)) + "." + quoteIdentifier(type.getString(2))));
                    }

                    String name = rs.getString(1);
                    String[] oids = StringUtils.split(rs.getString(2), ' ');
                    SQLFragment drop = new SQLFragment("DROP FUNCTION temp.").append(name);
                    drop.append("(");
                    String comma = "";
                    for (String oid : oids)
                    {
                        drop.append(comma).append(_types.get(oid));
                        comma = ",";
                    }
                    drop.append(")");

                    try
                    {
                        executor.execute(drop);
                    }
                    catch (BadSqlGrammarException x)
                    {
                        LOG.warn("could not clean up postgres function : temp." + name, x);
                    }
                }
            });

        // TODO delete types in temp schema as well! search for "CREATE TYPE" in StatementUtils.java
    }

    @Override
    public boolean isCaseSensitive()
    {
        return true;
    }

    @Override
    public boolean isEditable()
    {
        return true;
    }

    @Override
    public ColumnMetaDataReader getColumnMetaDataReader(ResultSet rsCols, TableInfo table)
    {
        // Retrieve and pass in the previously queried scale values for this scope.
        return new PostgreSqlColumnMetaDataReader(rsCols, table);
    }

    @Override
    public Map<String, MetadataParameterInfo> getParametersFromDbMetadata(DbScope scope, String procSchema, String procName) throws SQLException
    {
        CaseInsensitiveMapWrapper<MetadataParameterInfo> parameters = new CaseInsensitiveMapWrapper<>(new LinkedHashMap<>());

        // Get the parameters for the function and also a placeholder for the return if this function returns a resultset
        SQLFragment sqlf = new SQLFragment(
                "SELECT p.parameter_name, p.data_type, p.parameter_mode, p.ordinal_position FROM information_schema.parameters p" +
                        " JOIN information_schema.routines r ON p.specific_schema = r.specific_schema AND p.specific_name = r.specific_name " +
                        " WHERE p.specific_schema ILIKE ? AND r.routine_name ILIKE ? " +
                " UNION SELECT 'resultSet', data_type, 'OUT', 0 FROM information_schema.routines" +
                        " WHERE specific_schema ILIKE ? AND routine_name ILIKE ? AND data_type = 'refcursor' ORDER BY ordinal_position");
        sqlf.add(procSchema);
        sqlf.add(procName);
        sqlf.add(procSchema);
        sqlf.add(procName);

        /* DOES NOT HANDLE OVERLOADED FUNCTIONS! */
        try (ResultSet rs = (new MetadataSqlSelector(scope,sqlf)).getResultSet())
        {
            while (rs.next())
            {
                Map<ParamTraits, Integer> traitMap = new HashMap<>();
                int type = switch (rs.getString("data_type"))
                {
                    case "integer" -> Types.INTEGER;
                    case "timestamp without time zone" -> Types.TIMESTAMP;
                    case "boolean" -> Types.BOOLEAN;
                    case "numeric" -> Types.NUMERIC;
                    case "refcursor" -> // the return resultset
                            Types.OTHER;   // for containerId. Not trying to further distinguish the underlying type for other user defined types
                    default -> Types.VARCHAR;
                };
                int direction;
                switch (rs.getString("parameter_mode"))
                {
                    case "IN":
                        direction = DatabaseMetaData.procedureColumnIn;
                        break;
                    case "INOUT":
                        direction = DatabaseMetaData.procedureColumnInOut;
                        break;
                    case "OUT":
                        direction = DatabaseMetaData.procedureColumnOut;
                        break;
                    default:
                        // Other arg modes are not supported, ignore the parameter
                        continue;
                }
                traitMap.put(ParamTraits.direction, direction);
                traitMap.put(ParamTraits.datatype, type);
                parameters.put(rs.getString("parameter_name"), new MetadataParameterInfo(traitMap));
            }
        }

        return parameters;
    }

    @Override
    public String buildProcedureCall(String procSchema, String procName, int paramCount, boolean hasReturn, boolean assignResult, DbScope procScope)
    {
        if (hasReturn || assignResult)
            paramCount--; // this param isn't included in the argument list of the CALL statement
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        if (assignResult)
            sb.append("? = ");
        sb.append("CALL ").append(procSchema).append(".").append(procName).append("(");
        String comma = "";
        for (int i = 0; i < paramCount; i++)
        {
            sb.append(comma);
            sb.append("?");
            comma = ",";
        }
        sb.append(")}");
        return sb.toString();
    }

    @Override
    public void registerParameters(DbScope scope, CallableStatement stmt, Map<String, MetadataParameterInfo> parameters, boolean registerOutputAssignment) throws SQLException
    {
        int position = 0;
        if (registerOutputAssignment)
        {
            position++;
            stmt.registerOutParameter(position, Types.OTHER);
        }
        for (MetadataParameterInfo paramInfo : parameters.values())
        {
            if (paramInfo.getParamTraits().get(ParamTraits.direction) != DatabaseMetaData.procedureColumnOut)
            {
                position++;
                stmt.setObject(position, paramInfo.getParamValue(), paramInfo.getParamTraits().get(ParamTraits.datatype));
            }
        }
    }

    @Override
    public int readOutputParameters(DbScope scope, CallableStatement stmt, Map<String, MetadataParameterInfo> parameters) throws SQLException
    {
        ResultSet rs = stmt.getResultSet();
        rs.next();
        int returnVal = -1;
        for (Map.Entry<String, MetadataParameterInfo> parameter : parameters.entrySet())
        {
            String paramName = parameter.getKey();
            MetadataParameterInfo paramInfo = parameter.getValue();
            int direction = paramInfo.getParamTraits().get(ParamTraits.direction).intValue();
            if (direction == DatabaseMetaData.procedureColumnInOut)
                paramInfo.setParamValue(rs.getObject(paramName));
            else if (direction == DatabaseMetaData.procedureColumnOut && paramInfo.getParamTraits().get(ParamTraits.datatype).intValue() == Types.INTEGER)
                returnVal = rs.getInt(paramName);
        }
        return returnVal;
    }

    @Override
    public String translateParameterName(String name, boolean dialectSpecific)
    {
        return name;
    }

    @Override
    public boolean supportsNativeGreatestAndLeast()
    {
        return true;
    }

    private class PostgreSqlColumnMetaDataReader extends ColumnMetaDataReader
    {
        private final TableInfo _table;

        public PostgreSqlColumnMetaDataReader(ResultSet rsCols, TableInfo table)
        {
            super(rsCols);

            _table = table;
            _nameKey = "COLUMN_NAME";
            _sqlTypeKey = "DATA_TYPE";
            _sqlTypeNameKey = "TYPE_NAME";
            _scaleKey = "COLUMN_SIZE";
            _decimalDigitsKey = "DECIMAL_DIGITS";
            _nullableKey = "NULLABLE";
            _postionKey = "ORDINAL_POSITION";

            // Postgres JDBC driver doesn't include "IS_GENERATED" yet
            // https://github.com/pgjdbc/pgjdbc/issues/285
            // http://postgresql.nabble.com/Reading-schema-information-td5850903.html
            _generatedKey = null;
        }

        @Override
        public boolean isAutoIncrement() throws SQLException
        {
            String isAutoIncrement = _rsCols.getString("IS_AUTOINCREMENT");
            return "YES".equalsIgnoreCase(isAutoIncrement);
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

        @Override
        public int getScale() throws SQLException
        {
            int sqlType = super.getSqlType();

            return Types.DISTINCT == sqlType ? getDomainScale(getSqlTypeName()) : super.getScale();
        }

        private int getDomainScale(String domainName) throws SQLException
        {
            Integer scale = _domainScaleMap.get(domainName);

            if (null == scale)
            {
                // Some domain wasn't there when we initialized the datasource, so reload now. This will happen at bootstrap.
                DbSchema schema = _table.getSchema();
                initializeUserDefinedTypes(schema.getScope());
                scale = _domainScaleMap.get(domainName);

                // If scale is still null, then we have a problem. We've seen occasional exception reports showing this,
                // but haven't had the information to track it down... so log additional info.
                if (null == scale)
                {
                    String message = "Null scale for \"" + domainName + "\" in column \"" + _table.getName() + "." + getName() + "\" in schema \"" + schema.getName() + "\"";
                    ExceptionUtil.logExceptionToMothership(null, new Exception(message));
                    assert false : message;
                    return 4;   // Return something on production servers so schema can continue to load
                }
            }

            return scale.intValue();
        }

        @Nullable
        @Override
        public String getDefault() throws SQLException
        {
            return _rsCols.getString("COLUMN_DEF");
        }
    }


    @Override
    public PkMetaDataReader getPkMetaDataReader(ResultSet rs)
    {
        return new PkMetaDataReader(rs, "COLUMN_NAME", "KEY_SEQ");
    }

    @Override
    public String getExtraInfo(SQLException e)
    {
        // Deadlock between two different DB connections
        if ("40P01".equals(e.getSQLState()))
        {
            return getOtherDatabaseThreads();
        }
        return null;
    }

    @Override
    public ConnectionFactory getConnectionFactory(boolean useJdbcCaching, DbScope scope, SQLFragment sql)
    {
        // Fiddle with the Connection settings only if asked to turn off JDBC caching, we're not inside a transaction,
        // and it's a read-only statement (a SELECT), so we won't mess up any state the caller is relying on.
        if (useJdbcCaching || scope.isTransactionActive() || !Table.isSelect(sql.getSQL()))
        {
            return null;
        }
        else
        {
            // Factory that gets a fresh, read-only connection directly from the pool (not shared with the thread) and
            // configures it to not cache ResultSet data in the JDBC driver, making it suitable for streaming very large
            // ResultSets. See #39753 and #39888.
            return () -> {
                ConnectionWrapper conn = scope.getPooledConnection(DbScope.ConnectionType.Pooled, null);
                Closer closer = configureToDisableJdbcCaching(conn, scope);
                conn.setRunOnClose(closer);
                return conn;
            };
        }
    }

    private Closer configureToDisableJdbcCaching(ConnectionWrapper connection, DbScope scope) throws SQLException
    {
        assert connection.getAutoCommit(); // We just got a new connection... it better be set to auto commit

        try
        {
            // See http://stackoverflow.com/questions/1468036/java-jdbc-ignores-setfetchsize
            int previousTransactionIsolation = connection.getTransactionIsolation();
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
            connection.setAutoCommit(false);

            Closer previous = connection.getRunOnClose(); // We know this is a no-op closer, but do this just in case these get shared or wrapped in the future

            return () -> {
                previous.close();
                connection.setAutoCommit(true);
                connection.setTransactionIsolation(previousTransactionIsolation);
            };
        }
        catch (SQLException e)
        {
            LOG.error("SQLException hit for " + connection);
            scope.logCurrentConnectionState();
            throw e;
        }
    }

    @Override
    public SQLFragment getISOFormat(SQLFragment date)
    {
        // http://www.postgresql.org/docs/9.1/static/functions-formatting.html
        SQLFragment iso = new SQLFragment("to_char(CAST((");
        iso.append(date);
        iso.append(") AS TIMESTAMP), 'YYYY-MM-DD HH24:MI:SS.MS')");
        return iso;
    }

    @Override
    public String encodeLikeOpSearchString(String search)
    {
        return search.replaceAll("_", "\\\\_").replaceAll("%", "\\\\%");
    }


    @Override
    public boolean canShowExecutionPlan(ExecutionPlanType type)
    {
        return true;
    }

    @Override
    protected Collection<String> getQueryExecutionPlan(Connection conn, DbScope scope, SQLFragment sql, ExecutionPlanType type)
    {
        SQLFragment copy = new SQLFragment(sql);
        copy.insert(0, type == ExecutionPlanType.Estimated ? "EXPLAIN " : "EXPLAIN ANALYZE ");

        return new SqlSelector(scope, conn, copy).getCollection(String.class);
    }


    // This list is definitely not exhaustive, can be used for any function where the parameter count and
    // order are exactly the same as the JDBC equivalent
    static final CaseInsensitiveHashMap<String> passthroughFn = new CaseInsensitiveHashMap<>();
    static
    {
        passthroughFn.put("floor","floor");
        passthroughFn.put("lcase","lower");
        passthroughFn.put("ucase","upper");
        passthroughFn.put("now","now");
        // JDBC driver seems broken, rand() gets passed through as rand() instead of random()
        passthroughFn.put("rand","random");
    }

    @Override
    public SQLFragment formatJdbcFunction(String fn, SQLFragment... arguments)
    {
        SQLFragment call = new SQLFragment();
        String nativeFn = passthroughFn.get(fn);
        if (null != nativeFn)
            return formatFunction(call, nativeFn, arguments);
        else if (fn.equalsIgnoreCase("timestampdiff"))
            return timestampdiff(arguments);
        else
            return super.formatJdbcFunction(fn, arguments);
    }

    @Override
    public boolean isLabKeyWithSupported()
    {
        return true;
    }

    @Override
    public boolean isWithRecursiveKeywordRequired()
    {
        return true;
    }


    /* 25146: timestampdiff() inconsistent between sql server and postgres
     * As of dec/2015 {fn timestampdiff()} is not implemented correctly in pgjdbc
     */
    private SQLFragment timestampdiff(SQLFragment... arguments)
    {
        if (arguments[0].getSQL().equals("SQL_TSI_DAY"))
            return super.formatJdbcFunction("timestampdiff", arguments);

        SQLFragment epoch = new SQLFragment("EXTRACT(epoch FROM ");
        epoch.append("(").append(arguments[2]).append(") - (").append(arguments[1]).append("))");

        if (arguments[0].getSQL().equals("SQL_TSI_SECOND"))
            return epoch;

        if (arguments[0].getSQL().equals("SQL_TSI_MINUTE"))
            return epoch.append("/60.0");

        if (arguments[0].getSQL().equals("SQL_TSI_HOUR"))
            return epoch.append("/3600.0");

        return super.formatJdbcFunction("timestampdiff", arguments);
    }

    @Override
    public boolean supportsBatchGeneratedKeys()
    {
        return true;
    }

    @Override
    public boolean allowAsynchronousExecute()
    {
        return true;
    }

    public void setAdminWarning(HtmlString warning)
    {
        _adminWarning = warning;
    }

    @Override
    public void addAdminWarningMessages(Warnings warnings, boolean showAllWarnings)
    {
        if (null != _adminWarning)
            warnings.add(_adminWarning);
    }

    @Override
    public boolean isProcedureExists(DbScope scope, String schema, String name)
    {
        // Don't bother querying LabKey for stored procedures
        return getServerType().supportsSpecialMetadataQueries() && super.isProcedureExists(scope, schema, name);
    }

    @Override
    public boolean shouldTest()
    {
        // Don't test a LabKey data source
        return getServerType().shouldTest();
    }

    @Override
    public @Nullable String getApplicationNameParameter()
    {
        return "ApplicationName";
    }

    @Override
    public @Nullable String getApplicationNameSql()
    {
        return "SELECT current_setting('application_name')";
    }

    @Override
    public @Nullable String getDefaultApplicationName()
    {
        return "PostgreSQL JDBC Driver";
    }

    @Override
    public @NotNull String getApplicationConnectionsSql()
    {
        return "SELECT pid, usename, client_addr, client_hostname, xact_start, query_start, state, application_name, query FROM pg_stat_activity WHERE pid <> pg_backend_pid() AND datname = ? AND application_name = ?";
    }
}

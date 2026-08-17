/*
 * Copyright (c) 2005-2026 Fred Hutchinson Cancer Research Center
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
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.collections.CopyOnWriteHashMap;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.ConnectionWrapper.Closer;
import org.labkey.api.data.DatabaseIdentifier;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbScope.LabKeyDataSource;
import org.labkey.api.data.ExceptionFramework;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MetadataSqlSelector;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SqlExecutingSelector.ConnectionFactory;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.LimitRowsSqlGenerator.LimitRowsCustomizer;
import org.labkey.api.data.dialect.LimitRowsSqlGenerator.StandardLimitRowsCustomizer;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.template.Warnings;
import org.labkey.remoteapi.collections.CaseInsensitiveHashMap;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

// Base dialect for PostgreSQL AND Redshift. IMPORTANT: Make sure everything added here applies to Redshift as well;
// if not, put it in PostgreSql92Dialect.
public abstract class BasePostgreSqlDialect extends SqlDialect
{
    // Issue 52190: Expose troubleshooting data that supports postgreSQL-specific analysis
    public static final String POSTGRES_SCHEMA_NAME = "postgres";

    public static final String POSTGRES_STAT_ACTIVITY_TABLE_NAME = "pg_stat_activity";
    public static final String POSTGRES_LOCKS_TABLE_NAME = "pg_locks";
    public static final String POSTGRES_TABLE_SIZES_TABLE_NAME = "pg_tablesizes";

    private final Map<String, Integer> _domainScaleMap = new CopyOnWriteHashMap<>();

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
    public boolean cancelQueries(DbScope scope, Collection<ConnectionWrapper> connections, boolean terminate)
    {
        // Postgres delivers these on a side channel, so they land even when the target backend is mid-query. Run them
        // on our own connection; the target's belongs to the thread we're interrupting.
        String function = terminate ? "pg_terminate_backend" : "pg_cancel_backend";

        try (Connection conn = scope.getPooledConnection())
        {
            // Spring's translator would hand back a DataAccessException, which the per-connection catch below can't narrow on
            SqlExecutor executor = new SqlExecutor(scope, conn).setExceptionFramework(ExceptionFramework.JDBC);
            for (ConnectionWrapper connection : connections)
            {
                Integer spid = connection.getSPID();

                // Re-check as late as possible: if the thread let go while we were getting the connection above, the pool
                // may have handed that physical connection, and this SPID, straight back out to someone else
                if (!connection.isAllocated())
                {
                    LOG.debug("Skipping {}({}); the thread released that connection first", function, spid);
                    continue;
                }

                try
                {
                    // Reports an already-exited backend by returning false, not by throwing
                    boolean signalled = executor.executeWithResults(new SQLFragment("SELECT " + function + "(?)", spid), (rs, c) -> rs.next() && rs.getBoolean(1));

                    if (!signalled)
                    {
                        // A cancel losing the race to the query finishing is routine; a terminate finding nothing means we're out of options
                        LOG.log(terminate ? Level.WARN : Level.DEBUG, "{}({}) found no such backend", function, spid);
                    }
                }
                catch (RuntimeSQLException e)
                {
                    LOG.warn("{}({}) failed", function, spid, e);
                }
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        return true;
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
    public String getDefaultDateTimeDataType()
    {
        return "TIMESTAMP";
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
    protected SQLFragment doExecute(SQLFragment qualifiedProcName, SQLFragment parameters)
    {
        SQLFragment select = new SQLFragment("SELECT ");
        select.append(qualifiedProcName);
        select.append("(");
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
                    scale = Integer.parseInt(null != maxLength ? maxLength : rs.getString("character_octet_length"));
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
        else if (PropertyType.MULTI_CHOICE.getTypeUri().equals(prop.getTypeURI()) && prop.getJdbcType() == JdbcType.ARRAY)
        {
            return "text[]";
        }
        else
        {
            return getSqlTypeName(prop.getJdbcType());
        }
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
    protected SQLFragment doBuildProcedureCall(SQLFragment qualifiedProcName, int paramCount, boolean hasReturn, boolean assignResult, DbScope procScope)
    {
        if (hasReturn || assignResult)
            paramCount--; // this param isn't included in the argument list of the CALL statement
        SQLFragment sb = new SQLFragment();
        sb.append("{");
        if (assignResult)
            sb.append("? = ");
        sb.append("CALL ").append(qualifiedProcName).append("(");
        String comma = "";
        for (int i = 0; i < paramCount; i++)
        {
            sb.append(comma);
            sb.append("?");
            comma = ",";
        }
        sb.append(")}");
        return sb;
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
            int direction = paramInfo.getParamTraits().get(ParamTraits.direction);
            if (direction == DatabaseMetaData.procedureColumnInOut)
                paramInfo.setParamValue(rs.getObject(paramName));
            else if (direction == DatabaseMetaData.procedureColumnOut && paramInfo.getParamTraits().get(ParamTraits.datatype) == Types.INTEGER)
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

    @Override
    public boolean supportsNativeIsDistinctFrom()
    {
        return true;
    }

    @Override
    public boolean supportsIsNumeric()
    {
        return true;
    }

    @Override
    public SQLFragment isNumericExpr(SQLFragment expression)
    {
        return new SQLFragment("(CASE WHEN CAST((").append(expression)
                .append(") AS TEXT) ~ '^[+-]?([0-9]+([.][0-9]*)?|[.][0-9]+)$' THEN 1 ELSE 0 END)");
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

            return scale;
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
    public ConnectionFactory getConnectionFactory(boolean useJdbcCaching, boolean selfContained, DbScope scope, SQLFragment sql)
    {
        // Fiddle with the Connection settings only if asked to turn off JDBC caching, we're not inside a transaction,
        // and it's a read-only statement (a SELECT), so we won't mess up any state the caller is relying on.
        if (useJdbcCaching || scope.isTransactionActive() || !Table.isSelect(sql.getSQL()))
        {
            return null;
        }
        else if (selfContained)
        {
            // Borrow the thread's shared, ref-counted connection via scope.getConnection() rather than a
            // separate one, so nested queries reuse it (avoiding pool exhaustion) and connection-local state (temp tables,
            // session settings, etc) stays visible. Only the outermost borrower — isThreadConnectionActive() ==
            // false — disables JDBC caching and registers the restore via runOnClose (fired when the ref count returns to
            // 0); nested borrows reuse it as-is.
            return () -> {
                boolean alreadyHeld = scope.isThreadConnectionActive();
                Connection conn = scope.getConnection();

                try
                {
                    if (!alreadyHeld && conn instanceof ConnectionWrapper cw && cw.getAutoCommit())
                        cw.setRunOnClose(configureToDisableJdbcCaching(cw, scope));
                }
                catch (SQLException | RuntimeException e)
                {
                    // scope.getConnection() already bumped the ref count, so release it before propagating
                    try
                    {
                        conn.close();
                    }
                    catch (SQLException suppressed)
                    {
                        e.addSuppressed(suppressed);
                    }
                    throw e;
                }

                return conn;
            };
        }
        else
        {
            // The connection escapes the selector call (a live, streaming ResultSet/Stream is handed back to the caller)
            // or the caller explicitly disabled caching, so use a fresh, read-only connection directly from the pool
            // (not shared with the thread) whose lifetime the caller controls. See #39753 and #39888.
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
            LOG.error("SQLException hit for {}", connection);
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

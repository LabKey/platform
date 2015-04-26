/*
 * Copyright (c) 2005-2015 Fred Hutchinson Cancer Research Center
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

package org.labkey.core.dialect;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.InClauseGenerator;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.ParameterMarkerInClauseGenerator;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableChange;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TempTableTracker;
import org.labkey.api.data.dialect.ColumnMetaDataReader;
import org.labkey.api.data.dialect.DialectStringHandler;
import org.labkey.api.data.dialect.JdbcHelper;
import org.labkey.api.data.dialect.PkMetaDataReader;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.StandardJdbcHelper;
import org.labkey.api.data.dialect.StatementWrapper;
import org.labkey.api.query.AliasManager;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.StringUtilsLabKey;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * User: arauch
 * Date: Dec 28, 2004
 * Time: 8:58:25 AM
 */

// Dialect specifics for PostgreSQL
public class PostgreSql91Dialect extends SqlDialect
{
    private final Map<String, Integer> _domainScaleMap = new ConcurrentHashMap<>();
    private final AtomicBoolean _arraySortFunctionExists = new AtomicBoolean(false);

    private InClauseGenerator _inClauseGenerator = null;

    // Specifies if this PostgreSQL server treats backslashes in string literals as normal characters (as per the SQL
    // standard) or as escape characters (old, non-standard behavior). As of PostgreSQL 9.1, the setting
    // standard_conforming_strings in on by default; before 9.1, it was off by default. We check the server setting
    // when we prepare a new DbScope and use this when we escape and parse string literals.
    private Boolean _standardConformingStrings = null;

    // Standard constructor used by factory and subclasses
    PostgreSql91Dialect()
    {
    }

    // Constructor used to test standardConformingStrings setting
    PostgreSql91Dialect(boolean standardConformingStrings)
    {
        _standardConformingStrings = standardConformingStrings;
    }

    @Override
    public String getAdminWarningMessage()
    {
        return "LabKey Server no longer supports " + getProductName() + " " + getProductVersion() + ". " + PostgreSqlDialectFactory.RECOMMENDED;
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
    public String getProductName()
    {
        return PostgreSqlDialectFactory.PRODUCT_NAME;
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

    @Override
    public void appendStatement(Appendable sql, String statement)
    {
        try
        {
            sql.append(";\n");
            sql.append(statement);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void appendSelectAutoIncrement(Appendable sql, String columnName, @Nullable String variable)
    {
        try
        {
            sql.append("\nRETURNING ").append(columnName);
            if (null != variable)
                sql.append(" INTO ").append(variable);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addReselect(SQLFragment sql, String columnName, @Nullable String variable)
    {
        appendSelectAutoIncrement(sql, columnName, variable);
    }

    @Override
    public SQLFragment appendInClauseSql(SQLFragment sql, @NotNull Collection<?> params)
    {
        return _inClauseGenerator.appendInClauseSql(sql, params);
    }

    @Override
    public ResultSet executeWithResults(@NotNull PreparedStatement stmt) throws SQLException
    {
        return stmt.executeQuery();
    }

    public boolean requiresStatementMaxRows()
    {
        return false;
    }

    @Override
    public SQLFragment limitRows(SQLFragment frag, int maxRows)
    {
        return limitRows(frag, maxRows, 0);
    }

    private SQLFragment limitRows(SQLFragment frag, int rowCount, long offset)
    {
        if (rowCount != Table.ALL_ROWS)
        {
            frag.append("\nLIMIT ");
            frag.append(Integer.toString(Table.NO_ROWS == rowCount ? 0 : rowCount));

            if (offset > 0)
            {
                frag.append(" OFFSET ");
                frag.append(Long.toString(offset));
            }
        }
        return frag;
    }

    @Override
    public SQLFragment limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, String order, String groupBy, int maxRows, long offset)
    {
        if (select == null)
            throw new IllegalArgumentException("select");
        if (from == null)
            throw new IllegalArgumentException("from");

        SQLFragment sql = new SQLFragment();
        sql.append(select);
        sql.append("\n").append(from);
        if (filter != null) sql.append("\n").append(filter);
        if (groupBy != null) sql.append("\n").append(groupBy);
        if (order != null) sql.append("\n").append(order);

        return limitRows(sql, maxRows, offset);
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
    // PostgreSQL can evaluate EXISTS as a function, e.g., SELECT EXISTS (SELECT 1 WHERE RowId IN (1,3,4)) FROM core.Containers
    public SQLFragment wrapExistsExpression(SQLFragment existsSQL)
    {
        return existsSQL;
    }

    @Override
    public boolean supportsGroupConcat()
    {
        return true;
    }

    @Override
    public boolean supportsSelectConcat()
    {
        return true;
    }

    @Override
    public SQLFragment getSelectConcat(SQLFragment selectSql, String delimeter)
    {
        SQLFragment result = new SQLFragment("array_to_string(array(");
        result.append(selectSql);
        result.append("), '");
        result.append(delimeter);
        result.append("')");

        return result;
    }

    // 8.4 added a built-in array_agg() aggregate function. TODO: drop our custom aggregate function.
    protected String getArrayAggregateFunctionName()
    {
        return "array_agg";
    }

    @Override
    public SQLFragment getGroupConcat(SQLFragment sql, boolean distinct, boolean sorted, @NotNull String delimiterSQL)
    {
        // Sort function might not exist in external datasource; skip that syntax if not
        boolean useSortFunction = sorted && _arraySortFunctionExists.get();

        // TODO: PostgreSQL 9.0 also has "string_agg()" which we should use in place of array_to_string(array_agg())
        SQLFragment result = new SQLFragment("array_to_string(");
        if (useSortFunction)
        {
            result.append("core.sort(");   // TODO: Switch PostgreSQL 9.0 dialect to use ORDER BY option inside array aggregate instead of our custom function
        }
        result.append(getArrayAggregateFunctionName()).append("(");
        if (distinct)
        {
            result.append("DISTINCT ");
        }
        result.append(sql);
        result.append(")");
        if (useSortFunction)
        {
            result.append(")");
        }
        result.append(", ");
        result.append(delimiterSQL);
        result.append(")");

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
        return schemaName.equals("information_schema") || schemaName.equals("pg_catalog") || schemaName.startsWith("pg_toast_temp_");
    }

    @Override
    public String sanitizeException(SQLException ex)
    {
        if ("22001".equals(ex.getSQLState()))
        {
            return INPUT_TOO_LONG_ERROR_MESSAGE;
        }
        return GENERIC_ERROR_MESSAGE;
    }

    @Override
    public String getAnalyzeCommandForTable(String tableName)
    {
        return "ANALYZE " + tableName;
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
    public String getBooleanLiteral(boolean b)
    {
        return Boolean.toString(b);
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
        return true;
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
        String legal = getSelectNameFromMetaDataName(dbName);
        return "CREATE DATABASE " + legal + " WITH ENCODING 'UTF8';\n" +
                "ALTER DATABASE " + legal + " SET default_with_oids TO OFF";
    }

    @Override
    public String getCreateSchemaSql(String schemaName)
    {
        if (!AliasManager.isLegalName(schemaName) || isReserved(schemaName))
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
        return new SQLFragment("(EXTRACT(EPOCH FROM (").append(value1).append(" - ").append(value2).append( ")) / " + divideBy + ")::INT");
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


    // Make sure that the PL/pgSQL language is enabled in the associated database. If not, throw. It would be nice
    // to use CREATE LANGUAGE at this point, however, that requires SUPERUSER permissions and takes us down the path of
    // creating call handlers and other complexities. It looks like PostgreSQL 8.1 has a simpler form of CREATE LANGUAGE...
    // once we require 8.1 we should consider using it here.

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
    public void prepare(DbScope scope)
    {
        initializeUserDefinedTypes(scope);
        initializeInClauseGenerator(scope);
        determineSettings(scope);
        determineIfArraySortFunctionExists(scope);
        super.prepare(scope);
    }

    // When a new PostgreSQL DbScope is created, we enumerate the domains (user-defined types) in the public schema
    // of the datasource, determine their "scale," and stash that information in a map associated with the DbScope.
    // When the PostgreSQLColumnMetaDataReader reads meta data, it returns these scale values for all domains.
    private void initializeUserDefinedTypes(DbScope scope)
    {
        Selector selector = new SqlSelector(scope, "SELECT * FROM information_schema.domains");
        selector.forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
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
            }
        });
    }


    private String getDomainKey(String schemaName, String domainName)
    {
        return ("public".equals(schemaName) ? "" : schemaName + ".") + domainName;
    }


    private void initializeInClauseGenerator(DbScope scope)
    {
        _inClauseGenerator = getJdbcVersion(scope) >= 4 ? new ArrayParameterInClauseGenerator(scope) : new ParameterMarkerInClauseGenerator();
    }


    // Query any settings that may affect dialect behavior. Right now, only "standard_conforming_strings".
    private void determineSettings(DbScope scope)
    {
        Selector selector = new SqlSelector(scope, "SELECT setting FROM pg_settings WHERE name = 'standard_conforming_strings'");
        _standardConformingStrings = "on".equalsIgnoreCase(selector.getObject(String.class));
    }


    // Does this datasource include our sort array function?  The LabKey datasource should always have it, but external datasources might not
    private void determineIfArraySortFunctionExists(DbScope scope)
    {
        Selector selector = new SqlSelector(scope, "SELECT * FROM pg_catalog.pg_namespace n INNER JOIN pg_catalog.pg_proc p ON pronamespace = n.oid WHERE nspname = 'core' AND proname = 'sort'");
        _arraySortFunctionExists.set(selector.exists());

        // Array sort function should always exist in LabKey scope (for now)
        assert !scope.isLabKeyScope() || _arraySortFunctionExists.get();
    }


    @Override
    protected DialectStringHandler createStringHandler()
    {
        // TODO: Isn't this the wrong setting?  Should we be looking at the "backslash_quote" setting instead?
        if (_standardConformingStrings)
            return super.createStringHandler();
        else
            return new PostgreSqlNonConformingStringHandler();
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
    public String getSelectNameFromMetaDataName(String metaDataName)
    {
        // In addition to quoting keywords and names with special characters, quote any name with an upper case
        // character. PostgreSQL normally stores column/table names in all lower case, so an upper case character
        // coming out of metadata means the name must have been quoted at creation time and needs to be quoted. #11181
        if (StringUtilsLabKey.containsUpperCase(metaDataName))
            return quoteIdentifier(metaDataName);
        else
            return super.getSelectNameFromMetaDataName(metaDataName);
    }

    private static final Pattern PROC_PATTERN = Pattern.compile("^\\s*SELECT\\s+core\\.((executeJavaUpgradeCode\\s*\\(\\s*'(.+)'\\s*\\))|(bulkImport\\s*\\(\\s*'(.+)'\\s*,\\s*'(.+)'\\s*,\\s*'(.+)'\\s*\\)))\\s*;\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    @Override
    // No need to split up PostgreSQL scripts; execute all statements in a single block (unless we have a special stored proc call).
    protected Pattern getSQLScriptSplitPattern()
    {
        return null;
    }

    @NotNull
    @Override
    protected Pattern getSQLScriptProcPattern()
    {
        return PROC_PATTERN;
    }

    @Override
    protected void checkSqlScript(String lower, String lowerNoWhiteSpace, Collection<String> errors)
    {
        if (lowerNoWhiteSpace.contains("setsearch_pathto"))
            errors.add("Do not use \"SET search_path TO <schema>\".  Instead, schema-qualify references to all objects.");

        if (!lowerNoWhiteSpace.endsWith(";"))
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
    public String getMasterDataBaseName()
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
    public JdbcHelper getJdbcHelper()
    {
        return new StandardJdbcHelper("jdbc:postgresql:");
    }

    @Override
    protected String getDatabaseMaintenanceSql()
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
    public List<String> getChangeStatements(TableChange change)
    {
        List<String> sql = new ArrayList<>();
        switch (change.getType())
        {
            case CreateTable:
                sql.addAll(getCreateTableStatements(change));
                break;
            case DropTable:
                sql.add("DROP TABLE " + makeTableIdentifier(change));
                break;
            case AddColumns:
                sql.addAll(getAddColumnsStatements(change));
                break;
            case DropColumns:
                sql.add(getDropColumnsStatement(change));
                break;
            case RenameColumns:
                sql.addAll(getRenameColumnsStatement(change));
                break;
            case DropIndices:
                sql.addAll(getDropIndexStatements(change));
                break;
            case AddIndices:
                sql.addAll(getCreateIndexStatements(change));
                break;
        }

        return sql;
    }

    private List<String> getRenameColumnsStatement(TableChange change)
    {
        List<String> statements = new ArrayList<>();
        for (Map.Entry<String, String> oldToNew : change.getColumnRenames().entrySet())
        {
            String oldIdentifier = makePropertyIdentifier(oldToNew.getKey());
            String newIdentifier = makePropertyIdentifier(oldToNew.getValue());
            if (!oldIdentifier.equals(newIdentifier))
            {
                statements.add(String.format("ALTER TABLE %s.%s RENAME COLUMN %s TO %s",
                        change.getSchemaName(), change.getTableName(),
                        oldIdentifier,
                        newIdentifier));
            }
        }

        for (Map.Entry<PropertyStorageSpec.Index, PropertyStorageSpec.Index> oldToNew : change.getIndexRenames().entrySet())
        {
            PropertyStorageSpec.Index oldIndex = oldToNew.getKey();
            PropertyStorageSpec.Index newIndex = oldToNew.getValue();
            String oldName = nameIndex(change.getTableName(), oldIndex.columnNames);
            String newName = nameIndex(change.getTableName(), newIndex.columnNames);
            if (!oldName.equals(newName))
            {
                statements.add(String.format("ALTER INDEX %s.%s RENAME TO %s",
                        change.getSchemaName(),
                        oldName,
                        newName));
            }
        }

        return statements;
    }

    private String getDropColumnsStatement(TableChange change)
    {
        List<String> sqlParts = new ArrayList<>();
        for (PropertyStorageSpec prop : change.getColumns())
        {
            String name = prop.getExactName() ? quoteIdentifier(prop.getName()) : makePropertyIdentifier(prop.getName());
            sqlParts.add("DROP COLUMN " + name);
        }

        return String.format("ALTER TABLE %s %s", makeTableIdentifier(change), StringUtils.join(sqlParts, ", "));
    }

    // TODO if there are cases where user-defined columns need indices, this method will need to support
    // creating indices like getCreateTableStatement does.

    private List<String> getAddColumnsStatements(TableChange change)
    {
        List<String> statements = new ArrayList<>();
        List<String> sqlParts = new ArrayList<>();
        String pkColumn = null;

        for (PropertyStorageSpec prop : change.getColumns())
        {
            sqlParts.add("ADD COLUMN " + getSqlColumnSpec(prop));
            if (prop.isPrimaryKey())
            {
                assert null == pkColumn : "no more than one primary key defined";
                pkColumn = prop.getName();
            }
        }

        statements.add(String.format("ALTER TABLE %s %s", makeTableIdentifier(change), StringUtils.join(sqlParts, ", ")));
        if (null != pkColumn)
        {
            statements.add(String.format("ALTER TABLE %s ADD CONSTRAINT %s PRIMARY KEY(%s)",
                    makeTableIdentifier(change),
                    change.getTableName() + "_pk",
                    makePropertyIdentifier(pkColumn)));
        }

        return statements;
    }

    private List<String> getCreateTableStatements(TableChange change)
    {
        List<String> statements = new ArrayList<>();
        List<String> createTableSqlParts = new ArrayList<>();
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
            StringBuilder fkString = new StringBuilder("CONSTRAINT ");
            DbSchema schema = DbSchema.get(foreignKey.getSchemaName());
            TableInfo tableInfo = foreignKey.isProvisioned() ?
                    foreignKey.getTableInfoProvisioned() :
                    schema.getTable(foreignKey.getTableName());
            String constraintName = "fk_" + foreignKey.getColumnName() + "_" + change.getTableName() + "_" + tableInfo.getName();
            fkString.append(constraintName).append(" FOREIGN KEY (")
                    .append(foreignKey.getColumnName()).append(") REFERENCES ")
                    .append(tableInfo.getSelectName()).append(" (")
                    .append(foreignKey.getForeignColumnName()).append(")");
            createTableSqlParts.add(fkString.toString());
        }

        statements.add(String.format("CREATE TABLE %s (%s)", makeTableIdentifier(change), StringUtils.join(createTableSqlParts, ", ")));
        if (null != pkColumn)
            statements.add(String.format("ALTER TABLE %s ADD CONSTRAINT %s PRIMARY KEY(%s)",
                    makeTableIdentifier(change),
                    change.getTableName() + "_pk",
                    makePropertyIdentifier(pkColumn)));

        addCreateIndexStatements(statements, change);
        return statements;
    }

    private List<String> getCreateIndexStatements(TableChange change)
    {
        List<String> statements = new ArrayList<>();
        addCreateIndexStatements(statements, change);
        return statements;
    }

    private void addCreateIndexStatements(List<String> statements, TableChange change)
    {
        for (PropertyStorageSpec.Index index : change.getIndexedColumns())
        {
            statements.add(String.format("CREATE %s INDEX %s ON %s (%s)",
                    index.isUnique ? "UNIQUE" : "",
                    nameIndex(change.getTableName(), index.columnNames),
                    makeTableIdentifier(change),
                    makePropertyIdentifiers(index.columnNames)));
        }
    }

    private List<String> getDropIndexStatements(TableChange change)
    {
        List<String> statements = new ArrayList<>();
        addDropIndexStatements(statements, change);
        return statements;
    }

    private void addDropIndexStatements(List<String> statements, TableChange change)
    {
        for (PropertyStorageSpec.Index index : change.getIndexedColumns())
        {
            statements.add(String.format("DROP INDEX %s.%s",
                    change.getSchemaName(),
                    nameIndex(change.getTableName(), index.columnNames)));
        }
    }

    private String nameIndex(String tableName, String[] indexedColumns)
    {
        return AliasManager.makeLegalName(tableName + '_' + StringUtils.join(indexedColumns, "_"), this);
    }

    private String getSqlColumnSpec(PropertyStorageSpec prop)
    {
        List<String> colSpec = new ArrayList<>();
        colSpec.add(makePropertyIdentifier(prop.getName()));
        colSpec.add(sqlTypeNameFromSqlType(prop));
        if (prop.getJdbcType().sqlType == Types.VARCHAR && !prop.isEntityId())
            colSpec.add("(" + prop.getSize() + ")");
        else if (prop.getJdbcType() == JdbcType.DECIMAL)
            colSpec.add("(15,4)");
        if (prop.isPrimaryKey() || !prop.isNullable())
            colSpec.add("NOT NULL");
        if (null != prop.getDefaultValue())
        {
            if (prop.getJdbcType().sqlType == Types.BOOLEAN)
            {
                String defaultClause = " DEFAULT " +
                        ((Boolean)prop.getDefaultValue() ? getBooleanTRUE() : getBooleanFALSE());
                colSpec.add(defaultClause);
            }
            else if (prop.getJdbcType().sqlType == Types.VARCHAR)
            {
                colSpec.add(" DEFAULT '" + prop.getDefaultValue().toString() + "'");
            }
            else
            {
                throw new IllegalArgumentException("Default value on type " + prop.getJdbcType().name() + " is not supported.");
            }
        }
        return StringUtils.join(colSpec, ' ');
    }

    private String makeTableIdentifier(TableChange change)
    {
        assert AliasManager.isLegalName(change.getTableName());
        return change.getSchemaName() + "." + change.getTableName();
    }

    @Override
    public String sqlTypeNameFromSqlType(PropertyStorageSpec prop)
    {
        if (prop.isAutoIncrement())
        {
            if (prop.getJdbcType().sqlType == Types.INTEGER)
            {
                return "SERIAL";
            }
            else if (prop.getJdbcType().sqlType == Types.BIGINT)
            {
                return "BIGSERIAL";
            }
            else
            {
                throw new IllegalArgumentException("AutoIncrement is not supported for SQL type " + prop.getJdbcType().sqlType + " (" + sqlTypeNameFromSqlType(prop.getJdbcType().sqlType) + ")");
            }
        }
        else if (prop.isEntityId())
        {
            if (prop.getJdbcType().sqlType == Types.VARCHAR)
            {
                return SqlDialect.GUID_TYPE;
            }
            else
            {
                throw new IllegalArgumentException("EntityId is not supported for SQL type " + prop.getJdbcType().sqlType + " (" + sqlTypeNameFromSqlType(prop.getJdbcType().sqlType) + ")");
            }
        }
        else
        {
            return sqlTypeNameFromSqlType(prop.getJdbcType().sqlType);
        }
    }

    // Create comma-separated list of property identifiers
    private String makePropertyIdentifiers(String[] names)
    {
        String sep = "";
        StringBuilder sb = new StringBuilder();
        for (String name : names)
        {
            sb.append(sep).append(makePropertyIdentifier(name));
            sep = ", ";
        }
        return sb.toString();
    }

    private String makePropertyIdentifier(String name)
    {
        return quoteIdentifier(name.toLowerCase());
    }


    @Override
    public void initializeConnection(Connection conn) throws SQLException
    {
    }

    @Override
    public void purgeTempSchema(Map<String, TempTableTracker> createdTableNames)
    {
        try
        {
            trackTempTables(createdTableNames);

            DbSchema coreSchema = CoreSchema.getInstance().getSchema();
            SqlExecutor executor = new SqlExecutor(coreSchema);

            //rs = conn.getMetaData().getFunctions(dbName, tempSchemaName, "%");
            Map<String, String> types = null;

            try (ResultSet rs = new SqlSelector(coreSchema, "SELECT proname AS SPECIFIC_NAME, CAST(proargtypes AS VARCHAR) FROM pg_proc WHERE pronamespace=(select oid from pg_namespace where nspname = ?)", DbSchema.getTemp().getName()).getResultSet())
            {
                while (rs.next())
                {
                    if (null == types)
                        types = new SqlSelector(coreSchema, "SELECT CAST(oid AS VARCHAR), typname FROM pg_type").getValueMap();

                    String name = rs.getString(1);
                    String[] oids = StringUtils.split(rs.getString(2), ' ');
                    SQLFragment drop = new SQLFragment("DROP FUNCTION temp.").append(name);
                    drop.append("(");
                    String comma = "";
                    for (String oid : oids)
                    {
                        drop.append(comma).append(types.get(oid));
                        comma = ",";
                    }
                    drop.append(")");

                    executor.execute(drop);
                }
            }
        }
        catch (SQLException x)
        {
            LOG.warn("error cleaning up temp schema", x);
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
        return new PostgreSQLColumnMetaDataReader(rsCols, table);
    }

    @Override
    public Map<String, MetadataParameterInfo> getParametersFromDbMetadata(DbScope scope, String procSchema, String procName) throws SQLException
    {
        CaseInsensitiveMapWrapper<MetadataParameterInfo> parameters = new CaseInsensitiveMapWrapper<>(new LinkedHashMap<String, MetadataParameterInfo>());

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
        try (ResultSet rs = (new SqlSelector(scope,sqlf)).getResultSet())
        {
            while (rs.next())
            {
                Map<ParamTraits, Integer> traitMap = new HashMap<>();
                int type;
                switch (rs.getString("data_type"))
                {
                    case "integer":
                        type = Types.INTEGER;
                        break;
                    case "timestamp without time zone":
                        type = Types.TIMESTAMP;
                        break;
                    case "boolean":
                        type = Types.BOOLEAN;
                        break;
                    case "numeric":
                        type = Types.NUMERIC;
                        break;
                    case "refcursor": // the return resultset
                        type = Types.OTHER;
                        break;
                    case "USER-DEFINED":   // for containerId. Not trying to further distinguish the underlying type for other user defined types
                    case "character varying":
                    default:
                        type = Types.VARCHAR;
                        break;
                }
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
    public String buildProcedureCall(String procSchema, String procName, int paramCount, boolean hasReturn, boolean assignResult)
    {
        if (hasReturn || assignResult)
            paramCount--; // this param isn't included in the argument list of the CALL statement
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        if (assignResult)
            sb.append("? = ");
        sb.append("CALL " + procSchema + "." + procName +"(");
        for (int i = 0; i < paramCount; i++)
        {
            sb.append("?,");
        }
        sb.setLength(sb.length() - 1); // Postgres chokes on the trailing comma
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
                stmt.setObject(position, paramInfo.getParamValue() ,paramInfo.getParamTraits().get(ParamTraits.datatype));
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

    private class PostgreSQLColumnMetaDataReader extends ColumnMetaDataReader
    {
        private final TableInfo _table;

        public PostgreSQLColumnMetaDataReader(ResultSet rsCols, TableInfo table)
        {
            super(rsCols);

            _table = table;
            _nameKey = "COLUMN_NAME";
            _sqlTypeKey = "DATA_TYPE";
            _sqlTypeNameKey = "TYPE_NAME";
            _scaleKey = "COLUMN_SIZE";
            _nullableKey = "NULLABLE";
            _postionKey = "ORDINAL_POSITION";
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
            DbSchema schema = _table.getSchema();
            Integer scale = getDomainScale(schema, domainName);

            if (null == scale)
            {
                // Some domain wasn't there when we initialized the datasource, so reload now.  This will happpen at bootstrap.
                initializeUserDefinedTypes(schema.getScope());
                scale = getDomainScale(schema, domainName);

                // If scale is still null, then we have a problem. We've seen occassional exception reports showing this,
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

        // Domain could be defined in the current schema or in the "public" schema
        private Integer getDomainScale(DbSchema schema, String domainName)
        {
            // Check the schema first
            String key = getDomainKey(schema.getName(), domainName);
            Integer scale = _domainScaleMap.get(key);

            // Not there, check "public"
            if (null == scale)
                scale = _domainScaleMap.get(domainName);

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
    public void configureToDisableJdbcCaching(Connection connection, DbScope scope, SQLFragment sql) throws SQLException
    {
        // Only fiddle with the Connection settings if we're fairly certain that it's a read-only statement (starting
        // with SELECT) and we're not inside of a transaction, so we won't mess up any state the caller is relying on.

        // !scope.isTransactionActive() is apparently not sufficient for a few isolated cases, like DbSequenceManager test. TODO: Figure out this discrepancy
        if (Table.isSelect(sql.getSQL()) && !scope.isTransactionActive() && connection.getAutoCommit())
        {
            try
            {
                connection.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
                connection.setAutoCommit(false);
            }
            catch (SQLException e)
            {
                scope.logCurrentConnectionState();
                throw e;
            }
        }
        else
        {
           // TODO: Temporary check to track down #21294
            if (Table.isSelect(sql.getSQL()) && !scope.isTransactionActive() && !connection.getAutoCommit())
                throw new IllegalStateException("Why am I in a transaction?");
        }
    }

    // TODO: Move to special test case for PostgreSQL
    public void testParameterSubstitution()
    {
        if (_standardConformingStrings)
        {
            assert "'this'".equals(getStringHandler().quoteStringLiteral("this"));
            assert "'th\\is'".equals(getStringHandler().quoteStringLiteral("th\\is"));
            assert "'th\\''is'".equals(getStringHandler().quoteStringLiteral("th\\'is"));

            // Backslashes are normal characters in standard SQL, so we have five question marks outside of string literals here
//            testParameterSubstitution(new SQLFragment("'this\\??\\\\''\\'\\\\????\\?''\\'??\\\\?\\\\?''?''?\\'\\'\\?'", 1, 2, 3, 4, 5), "'this\\??\\\\''\\'\\\\'1''2''3''4'\\'5'''\\'??\\\\?\\\\?''?''?\\'\\'\\?'");
        }
        else
        {
            assert "'this'".equals(getStringHandler().quoteStringLiteral("this"));
            assert "'th\\\\is'".equals(getStringHandler().quoteStringLiteral("th\\is"));
            assert "'th\\\\''is'".equals(getStringHandler().quoteStringLiteral("th\\'is"));

            // Backslashes are escape characters in non-conforming strings mode, so we have no question marks outside of string literals here
//            testParameterSubstitution(new SQLFragment("'this\\??\\\\''\\'\\\\????\\?''\\'??\\\\?\\\\?''?''?\\'\\'\\?'"), "'this\\??\\\\''\\'\\\\????\\?''\\'??\\\\?\\\\?''?''?\\'\\'\\?'");
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
    public boolean canShowExecutionPlan()
    {
        return true;
    }

    @Override
    protected Collection<String> getQueryExecutionPlan(DbScope scope, SQLFragment sql)
    {
        SQLFragment copy = new SQLFragment(sql);
        copy.insert(0, "EXPLAIN ANALYZE ");

        return new SqlSelector(scope, copy).getCollection(String.class);
    }
}

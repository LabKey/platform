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

package org.labkey.bigiron.mssql;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.DatabaseMetaDataWrapper;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.InClauseGenerator;
import org.labkey.api.data.InlineInClauseGenerator;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MetadataSqlSelector;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableChange;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TempTableTracker;
import org.labkey.api.data.dialect.ColumnMetaDataReader;
import org.labkey.api.data.dialect.JdbcHelper;
import org.labkey.api.data.dialect.PkMetaDataReader;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.TableResolver;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.AliasManager;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;

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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Pattern;


/**
 * User: arauch
 * Date: Dec 28, 2004
 * Time: 8:58:25 AM
 */

// Dialect specifics for Microsoft SQL Server
public class MicrosoftSqlServer2008R2Dialect extends SqlDialect
{
    private static final Logger LOG = Logger.getLogger(MicrosoftSqlServer2008R2Dialect.class);

    private volatile boolean _groupConcatInstalled = false;
    private volatile Edition _edition = Edition.Unknown;

    @SuppressWarnings("unused")
    enum Edition
    {
        Unknown(false),
        Express(false),
        Standard(false),
        Developer(true),
        Enterprise(true);

        private final boolean _onlineSupported;

        Edition(boolean onlineSupported)
        {
            _onlineSupported = onlineSupported;
        }

        private static Edition getByEdition(String editionString)
        {
            Edition edition = Unknown;

            try
            {
                edition = valueOf(editionString);
            }
            catch (IllegalArgumentException e)
            {
            }

            return edition;
        }

        public boolean isOnlineSupported()
        {
            return _onlineSupported;
        }
    }

    private final InClauseGenerator _defaultGenerator = new InlineInClauseGenerator(this);
    private final TableResolver _tableResolver;

    public MicrosoftSqlServer2008R2Dialect(TableResolver tableResolver)
    {
        _tableResolver = tableResolver;
    }

    @Override
    protected @NotNull Set<String> getReservedWords()
    {
        return Sets.newCaseInsensitiveHashSet(new CsvSet(
            "add, all, alter, and, any, as, asc, authorization, backup, begin, between, break, browse, bulk, by, cascade, " +
            "case, check, checkpoint, close, clustered, coalesce, collate, column, commit, compute, constraint, contains, " +
            "containstable, continue, convert, create, cross, current, current_date, current_time, current_timestamp, " +
            "current_user, cursor, database, dbcc, deallocate, declare, default, delete, deny, desc, distinct, distributed, " +
            "double, drop, else, end, end-exec, errlvl, escape, except, exec, execute, exists, exit, external, fetch, file, " +
            "fillfactor, for, foreign, freetext, freetexttable, from, full, function, goto, grant, group, having, holdlock, " +
            "identity, identity_insert, identitycol, if, in, index, inner, insert, intersect, into, is, join, key, kill, " +
            "left, like, lineno, merge, national, nocheck, nonclustered, not, null, nullif, of, off, offsets, on, open, " +
            "opendatasource, openquery, openrowset, openxml, option, or, order, outer, over, percent, pivot, plan, primary, " +
            "print, proc, procedure, public, raiserror, read, readtext, reconfigure, references, replication, restore, " +
            "restrict, return, revert, revoke, right, rollback, rowcount, rowguidcol, rule, save, schema, select, " +
            "session_user, set, setuser, shutdown, some, statistics, system_user, table, tablesample, textsize, then, to, " +
            "top, tran, transaction, trigger, truncate, tsequal, union, unique, unpivot, update, updatetext, use, user, " +
            "values, varying, view, waitfor, when, where, while, with, writetext"
        ));
    }

    @Override
    protected void addSqlTypeNames(Map<String, Integer> sqlTypeNameMap)
    {
        sqlTypeNameMap.put("BINARY", Types.BINARY);
        sqlTypeNameMap.put("FLOAT", Types.DOUBLE);
        sqlTypeNameMap.put("INT IDENTITY", Types.INTEGER);
        sqlTypeNameMap.put("BIGINT IDENTITY", Types.BIGINT);
        sqlTypeNameMap.put("DATETIME", Types.TIMESTAMP);
        sqlTypeNameMap.put("TEXT", Types.LONGVARCHAR);
        sqlTypeNameMap.put("NTEXT", Types.LONGVARCHAR);
        sqlTypeNameMap.put("NVARCHAR", Types.VARCHAR);
        sqlTypeNameMap.put("UNIQUEIDENTIFIER", Types.VARCHAR);
        sqlTypeNameMap.put("TIMESTAMP", Types.BINARY);

        // LabKey custom data types
        sqlTypeNameMap.put("ENTITYID", Types.VARCHAR);
        sqlTypeNameMap.put("LSIDTYPE", Types.VARCHAR);
    }

    @Override
    protected void addSqlTypeInts(Map<Integer, String> sqlTypeIntMap)
    {
        sqlTypeIntMap.put(Types.BINARY, "BINARY");
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

    @Override
    public String sqlTypeNameFromSqlType(PropertyStorageSpec prop)
    {
        if (prop.isAutoIncrement())
        {
            if (prop.getJdbcType().sqlType == Types.INTEGER)
            {
                return "INT IDENTITY (1, 1)";
            }
            else if (prop.getJdbcType().sqlType == Types.BIGINT)
            {
                return "BIGINT IDENTITY (1, 1)";
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
        else if (JdbcType.DATE.equals(prop.getJdbcType()) || JdbcType.TIME.equals(prop.getJdbcType()))
        {
            // This is because the jtds driver has a bug where it returns these from the db as strings
            return "DATETIME";
        }
        else
        {
            return sqlTypeNameFromSqlType(prop.getJdbcType().sqlType);
        }
    }

    @Override
    @Nullable
    public String sqlCastTypeNameFromJdbcType(JdbcType type)
    {
        if (type.equals(JdbcType.VARCHAR))
            return "NVARCHAR(MAX)";
        return sqlTypeNameFromJdbcType(type);   // Override for alternate behavior
    }

    @Override
    public boolean isSqlServer()
    {
        return true;
    }

    @Override
    public boolean isPostgreSQL()
    {
        return false;
    }

    @Override
    public boolean isOracle()
    {
        return false;
    }

    @Override
    public String getProductName()
    {
        return MicrosoftSqlServerDialectFactory.PRODUCT_NAME;
    }

    @Nullable
    @Override
    public String getProductEdition()
    {
        return _edition.name() + " Edition";
    }

    @Override
    public String getSQLScriptPath()
    {
        return "sqlserver";
    }

    @Override
    public String getDefaultDateTimeDataType()
    {
        return "DATETIME";
    }

    @Override
    public SQLFragment appendInClauseSql(SQLFragment sql, @NotNull Collection<?> params)
    {
        return _defaultGenerator.appendInClauseSql(sql, params);
    }

    @Override
    public String getUniqueIdentType()
    {
        return "INT IDENTITY (1,1)";
    }

    @Override
    public String getGuidType()
    {
        return "UNIQUEIDENTIFIER";
    }

    @Override
    public String getLsidType()
    {
        return "NVARCHAR(300)";
    }

    @Override
    public void appendStatement(Appendable sql, String statement)
    {
        try
        {
            sql.append('\n');
            sql.append(statement);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }


    @Override
    protected void checkSqlScript(String lower, String lowerNoWhiteSpace, Collection<String> errors)
    {
    }


    @Override
    public void appendSelectAutoIncrement(Appendable sql, String columnName, @Nullable String variable)
    {
        if (null == variable)
            appendStatement(sql, "SELECT @@IDENTITY");
        else
            appendStatement(sql, "SELECT " + variable + "=@@IDENTITY");
    }


    @Override
    public void addReselect(SQLFragment sql, String columnName, @Nullable String variable)
    {
        String trimmed = sql.getRawSQL().trim();

        if (StringUtils.startsWithIgnoreCase(trimmed, "INSERT") || StringUtils.startsWithIgnoreCase(trimmed, "UPDATE"))
        {
            StringBuilder outputSql = new StringBuilder("OUTPUT INSERTED.");
            outputSql.append(columnName);
            outputSql.append(" ");

            if (null != variable)
            {
                outputSql.append("INTO ");
                outputSql.append(variable);
                outputSql.append(" ");
            }

            int idx = StringUtils.indexOfIgnoreCase(sql, "WHERE");

            if (idx > -1)
                sql.insert(idx, outputSql.toString());
            else
                sql.append(outputSql);
        }
        else
        {
            throw new IllegalStateException("Can re-select only from INSERT or UPDATE statement");
        }
    }

    @Override
    public @Nullable ResultSet executeWithResults(@NotNull PreparedStatement stmt) throws SQLException
    {
        return stmt.executeQuery();
    }


    @Override
    public boolean requiresStatementMaxRows()
    {
        return false;
    }

    @Override
    public SQLFragment limitRows(SQLFragment frag, int maxRows)
    {
        if (maxRows != Table.ALL_ROWS)
        {
            String sql = frag.getCompareSQL();
            if (!sql.substring(0, 6).equalsIgnoreCase("SELECT"))
                throw new IllegalArgumentException("ERROR: Limit SQL doesn't start with SELECT: " + sql);

            int offset = 6;
            if (sql.substring(0, 15).equalsIgnoreCase("SELECT DISTINCT"))
                offset = 15;
            frag.insert(offset, " TOP " + (Table.NO_ROWS == maxRows ? 0 : maxRows));
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

        if (maxRows == Table.ALL_ROWS || maxRows == Table.NO_ROWS || offset == 0)
        {
            SQLFragment sql = new SQLFragment();
            sql.append(select);
            sql.append("\n").append(from);
            if (filter != null) sql.append("\n").append(filter);
            if (groupBy != null) sql.append("\n").append(groupBy);
            if (order != null) sql.append("\n").append(order);

            return limitRows(sql, maxRows);
        }
        else
        {
            if (order == null || order.trim().length() == 0)
                throw new IllegalArgumentException("ERROR: ORDER BY clause required to limit");

            return _limitRows(select, from, filter, order, groupBy, maxRows, offset);
        }
    }

    // Called only if rowCount and offset are both > 0
    protected SQLFragment _limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, @NotNull String order, String groupBy, int maxRows, long offset)
    {
        SQLFragment sql = new SQLFragment();
        sql.append("SELECT * FROM (\n");
        sql.append(select);
        sql.append(",\nROW_NUMBER() OVER (\n");
        sql.append(order);
        sql.append(") AS _RowNum\n");
        sql.append(from);
        if (filter != null) sql.append("\n").append(filter);
        if (groupBy != null) sql.append("\n").append(groupBy);
        sql.append("\n) AS z\n");
        sql.append("WHERE _RowNum BETWEEN ");
        sql.append(offset + 1);
        sql.append(" AND ");
        sql.append(offset + maxRows);

        return sql;
    }

    @Override
    public boolean supportsComments()
    {
        return true;
    }

    // Execute a stored procedure/function with the specified parameters

    @Override
    public String execute(DbSchema schema, String procedureName, String parameters)
    {
        return "EXEC " + schema.getName() + "." + procedureName + " " + parameters;
    }

    @Override
    public SQLFragment execute(DbSchema schema, String procedureName, SQLFragment parameters)
    {
        SQLFragment exec = new SQLFragment("EXEC " + schema.getName() + "." + procedureName + " ");
        exec.append(parameters);
        return exec;
    }

    @Override
    public String concatenate(String... args)
    {
        return StringUtils.join(args, " + ");
    }


    @Override
    public SQLFragment concatenate(SQLFragment... args)
    {
        SQLFragment ret = new SQLFragment();
        String op = "";
        for (SQLFragment arg : args)
        {
            ret.append(op).append(arg);
            op = " + ";
        }
        return ret;
    }


    @Override
    public String getCharClassLikeOperator()
    {
        return "LIKE";
    }

    @Override
    public String getCaseInsensitiveLikeOperator()
    {
        return "LIKE";
    }

    @Override
    public String getVarcharLengthFunction()
    {
        return "len";
    }

    @Override
    public String getStdDevFunction()
    {
        return "stdev";
    }

    @Override
    public String getClobLengthFunction()
    {
        return "datalength";
    }

    @Override
    public SQLFragment getStringIndexOfFunction(SQLFragment toFind, SQLFragment toSearch)
    {
        SQLFragment result = new SQLFragment("patindex('%' + ");
        result.append(toFind);
        result.append(" + '%', ");
        result.append(toSearch);
        result.append(")");
        return result;
    }

    @Override
    public String getSubstringFunction(String s, String start, String length)
    {
        return "substring(" + s + ", " + start + ", " + length + ")";
    }

    @Override
    public SQLFragment getSubstringFunction(SQLFragment s, SQLFragment start, SQLFragment length)
    {
        return new SQLFragment("substring(").append(s).append(", ").append(start).append(", ").append(length).append(")");
    }

    @Override
    // SQL Server doesn't evaluate EXISTS as a function... it can only be used in a WHERE or CASE
    public SQLFragment wrapExistsExpression(SQLFragment existsSQL)
    {
        return new SQLFragment("CAST(CASE WHEN\n").append(existsSQL).append("\nTHEN 1 ELSE 0 END AS BIT)");
    }

    @Override
    public boolean supportsGroupConcat()
    {
        return _groupConcatInstalled;
    }

    // Uses custom CLR aggregate function defined in group_concat_install.sql
    @Override
    public SQLFragment getGroupConcat(SQLFragment sql, boolean distinct, boolean sorted, @NotNull String delimiterSQL)
    {
        // SQL Server does not support aggregates on sub-queries; return a string constant in that case to keep from
        // blowing up. TODO: Don't pass sub-selects into group_contact.
        if (StringUtils.containsIgnoreCase(sql.getSQL(), "SELECT"))
            return new SQLFragment("'NOT SUPPORTED'");

        if (!supportsGroupConcat())
            return new SQLFragment("'NOT SUPPORTED'");

        SQLFragment result = new SQLFragment("core.GROUP_CONCAT_D");

        if (sorted)
        {
            result.append("S");
        }

        result.append("(");

        if (distinct)
        {
            result.append("DISTINCT ");
        }

        result.append(sql);
        result.append(", ");
        result.append(delimiterSQL);

        if (sorted)
        {
            result.append(", 1");
        }

        result.append(")");

        return result;
    }

    @Override
    public boolean supportsSelectConcat()
    {
        return true;
    }

    @Override
    public boolean supportsOffset()
    {
        return true;
    }

    @Override
    public SQLFragment getSelectConcat(SQLFragment selectSql, String delimeter)
    {
        String sql = selectSql.getSQL().toUpperCase();

        // Use SQLServer's FOR XML syntax to concat multiple values together
        // We want them separated by commas, so prefix each value with a comma and then use SUBSTRING to strip
        // off the leading comma - this is easier than stripping a trailing comma because we don't have to determine
        // the length of the string

        // The goal is to get something of the form:
        // SUBSTRING((SELECT ',' + c$Titration$.Name AS [data()] FROM luminex.AnalyteTitration c
        // INNER JOIN luminex.Titration c$Titration$ ON (c.TitrationId = c$Titration$.RowId) WHERE child.AnalyteId = c.AnalyteId FOR XML PATH ('')), 2, 2147483647) AS Titration$Name

        // TODO - There is still an issue if the individual input values contain commas. We need to escape or otherwise handle that
        SQLFragment ret = new SQLFragment(selectSql);
        int startIndex = 0;
        int fromIndex;
        int parensCount = 0;
        do
        {
            // We need to find the FROM that's not part of a subselect. We'll do a simplistic count of open and close
            // parens to determine if we're inside of a subselect
            fromIndex = sql.indexOf("FROM", startIndex);
            for (int i = startIndex; i <= fromIndex; i++)
            {
                char c = sql.charAt(i);
                // This will get confused if there are embedded strings in the SQL that contain parens, etc
                if (c == '(')
                {
                    parensCount++;
                }
                else if (c == ')')
                {
                    parensCount--;
                }
            }
            startIndex = fromIndex + 1;
        }
        while (parensCount > 0 && fromIndex != -1);
        if (fromIndex == -1)
        {
            throw new IllegalArgumentException("Can't handle SQL: " + sql);
        }
        ret.insert(fromIndex, "AS NVARCHAR) AS [text()] ");
        int selectIndex = sql.indexOf("SELECT");
        ret.insert(selectIndex + "SELECT".length(), "'" + delimeter + "' + CAST(");
        ret.insert(0, "SUBSTRING ((");
        ret.append(" FOR XML PATH ('')), ");
        // Trim off the first delimeter
        ret.append(delimeter.length() + 1);
        // We want all the characters, so use a ridiculously long value to ensure that we don't truncate
        ret.append(", 2147483647)");

        return ret;
    }

    @Override
    public String getTempTableKeyword()
    {
        return "";
    }

    @Override
    public String getTempTablePrefix()
    {
        return getGlobalTempTablePrefix();
    }


    @Override
    public String getGlobalTempTablePrefix()
    {
        return DbSchema.TEMP_SCHEMA_NAME + ".";
    }


    @Override
    public boolean isNoDatabaseException(SQLException e)
    {
        return "S1000".equals(e.getSQLState());
    }

    @Override
    public boolean isSortableDataType(String sqlDataTypeName)
    {
        return !("text".equalsIgnoreCase(sqlDataTypeName) ||
                "ntext".equalsIgnoreCase(sqlDataTypeName) ||
                "image".equalsIgnoreCase(sqlDataTypeName));
    }

    @Override
    public String getDropIndexCommand(String tableName, String indexName)
    {
        return "DROP INDEX " + tableName + "." + indexName;
    }

    @Override
    public String getCreateDatabaseSql(String dbName)
    {
        return "CREATE DATABASE " + makeLegalIdentifier(dbName);
    }

    @Override
    public String getCreateSchemaSql(String schemaName)
    {
        // Now using the SQL2005 syntax for creating SCHEMAs... though fn_dropIfExists should work on old-style owners
        // (e.g., EXEC sp_addapprole 'foo', 'password')

        if (!AliasManager.isLegalName(schemaName) || isReserved(schemaName))
            throw new IllegalArgumentException("Not a legal schema name: " + schemaName);

        //Quoted schema names are bad news
        return "CREATE SCHEMA " + schemaName;
    }

    @Override
    public String getTruncateSql(String tableName)
    {
        return "TRUNCATE TABLE " + tableName;
    }

    @Override
    public String getDatePart(int part, String value)
    {
        String partName = getDatePartName(part);
        return "DATEPART(" + partName + ", " + value + ")";
    }

    @Override
    public String getDateDiff(int part, String value1, String value2)
    {
        String partName = getDatePartName(part);
        return "DATEDIFF(" + partName + ", " + value2 + ", " + value1 + ")";
    }

    @Override
    public SQLFragment getDateDiff(int part, SQLFragment value1, SQLFragment value2)
    {
        String partName = getDatePartName(part);
        return new SQLFragment("DATEDIFF(" + partName + ", ").append(value2).append(", ").append(value1).append(")");
    }


    @Override
    public String getDateTimeToDateCast(String expression)
    {
        return "CONVERT(DATETIME, CONVERT(VARCHAR, (" + expression + "), 101))";
    }

    @Override
    public String getRoundFunction(String valueToRound)
    {
        return "ROUND(" + valueToRound + ", 0)";
    }

    @Override
    public boolean supportsRoundDouble()
    {
        return true;
    }

    @Override
    protected String getSystemTableNames()
    {
        return "dtproperties,sysconstraints,syssegments";
    }

    private static final Set<String> SYSTEM_SCHEMAS = PageFlowUtil.set("db_accessadmin", "db_backupoperator",
            "db_datareader", "db_datawriter", "db_ddladmin", "db_denydatareader", "db_denydatawriter", "db_owner",
            "db_securityadmin", "guest", "INFORMATION_SCHEMA", "sys");

    @Override
    public boolean isSystemSchema(String schemaName)
    {
        return SYSTEM_SCHEMAS.contains(schemaName);
    }

    @Override
    public String sanitizeException(SQLException ex)
    {
        if ("01004".equals(ex.getSQLState()))
        {
            return INPUT_TOO_LONG_ERROR_MESSAGE;
        }
        return GENERIC_ERROR_MESSAGE;
    }

    @Override
    public String getAnalyzeCommandForTable(String tableName)
    {
        return "UPDATE STATISTICS " + tableName + ";";
    }

    @Override
    protected String getSIDQuery()
    {
        return "SELECT @@spid";
    }

    @Override
    public String getBooleanDataType()
    {
        return "BIT";
    }

    @Override
    public String getBooleanLiteral(boolean b)
    {
        return b ? "1" : "0";
    }

    @Override
    public String getBinaryDataType()
    {
        return "IMAGE";
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
        statements.insert(0, "SET IDENTITY_INSERT " + tinfo + " ON\n");
        statements.append("SET IDENTITY_INSERT ").append(tinfo).append(" OFF");
    }

    private static final Pattern GO_PATTERN = Pattern.compile("^\\s*GO\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern PROC_PATTERN = Pattern.compile("^\\s*EXEC(?:UTE)?\\s+core\\.((executeJavaUpgradeCode\\s*'(.+)')|(bulkImport\\s*'(.+)'\\s*,\\s*'(.+)'\\s*,\\s*'(.+)'))\\s*;?\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    @Override
    // Split Microsoft SQL scripts on GO statements
    protected Pattern getSQLScriptSplitPattern()
    {
        return GO_PATTERN;
    }

    @NotNull
    @Override
    protected Pattern getSQLScriptProcPattern()
    {
        return PROC_PATTERN;
    }

    @Override
    public String getMasterDataBaseName()
    {
        return "master";
    }


    @Override
    public JdbcHelper getJdbcHelper()
    {
        return new JtdsJdbcHelper();
    }


    /*
        jTDS example connection URLs we need to parse:

        jdbc:jtds:sqlserver://host:1433/database
        jdbc:jtds:sqlserver://host/database;SelectMethod=cursor
    */

    public static class JtdsJdbcHelper implements JdbcHelper
    {
        @Override
        public String getDatabase(String url) throws ServletException
        {
            if (url.startsWith("jdbc:jtds:sqlserver"))
            {
                int dbEnd = url.indexOf(';');
                if (-1 == dbEnd)
                    dbEnd = url.length();
                int dbDelimiter = url.lastIndexOf('/', dbEnd);
                if (-1 == dbDelimiter)
                    throw new ServletException("Invalid jTDS connection url: " + url);
                return url.substring(dbDelimiter + 1, dbEnd);
            }
            else if (url.startsWith("jdbc:sqlserver"))
            {
                int dbDelimiter = url.indexOf(";database=");
                if (-1 == dbDelimiter)
                    throw new ServletException("Invalid sql server connection url: " + url);
                dbDelimiter += ";database=".length();
                int dbEnd = url.indexOf(";",dbDelimiter);
                if (-1 == dbEnd)
                    dbEnd = url.length();
                return url.substring(dbDelimiter, dbEnd);
            }
            else
                throw new ServletException("Unsupported connection url: " + url);
        }
    }

    @Override
    public SQLFragment sqlLocate(SQLFragment littleString, SQLFragment bigString)
    {
        SQLFragment ret = new SQLFragment("(CHARINDEX(");
        ret.append(littleString);
        ret.append(",");
        ret.append(bigString);
        ret.append("))");
        return ret;
    }

    @Override
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

    @Override
    public boolean allowSortOnSubqueryWithoutLimit()
    {
        return false;
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
                sql.add("DROP TABLE " + change.getSchemaName() + "." + change.getTableName());
                break;
            case AddColumns:
                sql.addAll(getAddColumnsStatements(change));
                break;
            case DropColumns:
                sql.add(getDropColumnsStatement(change));
                break;
            case RenameColumns:
                sql.addAll(getRenameColumnsStatements(change));
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

        statements.add(String.format("CREATE TABLE %s (%s)", makeTableIdentifier(change), StringUtils.join(createTableSqlParts, ",\n")));

        if (null != pkColumn)
            statements.add(String.format("ALTER TABLE %s ADD CONSTRAINT %s PRIMARY KEY (%s)",
                    makeTableIdentifier(change),
                    change.getTableName() + "_pk",
                    makeLegalIdentifier(pkColumn)));


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
                    makeLegalIdentifiers(index.columnNames)));
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
            statements.add(String.format("DROP INDEX %s ON %s",
                    nameIndex(change.getTableName(), index.columnNames),
                    makeTableIdentifier(change)
                    ));
        }
    }

    private String makeTableIdentifier(TableChange change)
    {
        assert AliasManager.isLegalName(change.getTableName());
        return change.getSchemaName() + "." + change.getTableName();
    }

    private String nameIndex(String tableName, String[] indexedColumns)
    {
        return AliasManager.makeLegalName(tableName + '_' + StringUtils.join(indexedColumns, "_"), this);
    }

    private List<String> getRenameColumnsStatements(TableChange change)
    {
        List<String> statements = new ArrayList<>();
        for (Map.Entry<String, String> oldToNew : change.getColumnRenames().entrySet())
        {
            String oldName = oldToNew.getKey();
            String newName = oldToNew.getValue();
            if (!oldName.equals(newName))
            {
                statements.add(String.format("EXEC sp_rename '%s','%s','COLUMN'",
                        makeTableIdentifier(change) + ".\"" + oldName + "\"", newName));
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
                statements.add(String.format("EXEC sp_rename '%s','%s','INDEX'",
                        makeTableIdentifier(change) + "." + oldName,
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
            sqlParts.add(makeLegalIdentifier(prop.getName()));
        }

        return String.format("ALTER TABLE %s DROP COLUMN %s", change.getSchemaName() + "." + change.getTableName(), StringUtils.join(sqlParts, ",\n"));
    }

    private List<String> getAddColumnsStatements(TableChange change)
    {
        List<String> statements = new ArrayList<>();
        List<String> sqlParts = new ArrayList<>();
        String pkColumn = null;

        for (PropertyStorageSpec prop : change.getColumns())
        {
            sqlParts.add(getSqlColumnSpec(prop));
            if (prop.isPrimaryKey())
            {
                assert null == pkColumn : "no more than one primary key defined";
                pkColumn = prop.getName();
            }
        }

        statements.add(String.format("ALTER TABLE %s ADD %s", change.getSchemaName() + "." + change.getTableName(), StringUtils.join(sqlParts, ",\n")));
        if (null != pkColumn)
        {
            statements.add(String.format("ALTER TABLE %s ADD CONSTRAINT %s PRIMARY KEY (%s)",
                    makeTableIdentifier(change),
                    change.getTableName() + "_pk",
                    makeLegalIdentifier(pkColumn)));
        }

        return statements;
    }

    private String getSqlColumnSpec(PropertyStorageSpec prop)
    {
        List<String> colSpec = new ArrayList<>();
        colSpec.add(makeLegalIdentifier(prop.getName()));
        colSpec.add(sqlTypeNameFromSqlType(prop));

        if (prop.getJdbcType().sqlType == Types.VARCHAR && !prop.isEntityId())
        {
            if (prop.getSize() == Integer.MAX_VALUE)
            {
                colSpec.add("(MAX)");
            }
            else
            {
                colSpec.add("(" + prop.getSize() + ")");
            }
        }
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

    @Override
    public void initializeConnection(Connection conn) throws SQLException
    {
        Statement stmt = conn.createStatement();
        stmt.execute("SET ARITHABORT ON");
        stmt.close();
    }

    @Override
    public void afterCoreUpgrade(ModuleContext context)
    {
        GroupConcatInstallationManager.ensureGroupConcat(context);
    }

    @Override
    public void addAdminWarningMessages(Collection<String> messages)
    {
        if (!_groupConcatInstalled)
            messages.add("The GROUP_CONCAT aggregate function is not installed. This function is required for optimal operation of this server. " + new HelpTopic("groupconcatinstall").getSimpleLinkHtml("View installation instructions."));

        if ("2008R2".equals(getProductVersion()))
            messages.add("LabKey Server no longer supports " + getProductName() + " " + getProductVersion() + "; please upgrade. " + MicrosoftSqlServerDialectFactory.RECOMMENDED);
    }

    @Override
    public void prepare(DbScope scope)
    {
        _groupConcatInstalled = GroupConcatInstallationManager.isInstalled(scope);
        _edition = getEdition(scope);

        super.prepare(scope);
    }

    private Edition getEdition(DbScope scope)
    {
        String edition = new SqlSelector(scope, "SELECT SERVERPROPERTY ('edition')").getObject(String.class);
        String name = edition.split(" ")[0];

        return Edition.getByEdition(name);
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
    }

    @Override
    public boolean isCaseSensitive()
    {
        return false;
    }

    @Override
    public boolean isEditable()
    {
        return true;
    }

    @Override
    public ColumnMetaDataReader getColumnMetaDataReader(ResultSet rsCols, TableInfo table)
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

        @Override
        public boolean isAutoIncrement() throws SQLException
        {
            // Address both "int identity" and "bigint identity", #14136
            return StringUtils.endsWithIgnoreCase(getSqlTypeName(), "identity");
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

    /**
     * @return any additional information that should be sent to the mothership in the case of a SQLException
     */
    @Override
    public String getExtraInfo(SQLException e)
    {
        // Deadlock between two different DB connections
        if ("40001".equals(e.getSQLState()))
        {
            return getOtherDatabaseThreads();
        }
        return null;
    }

    @Override
    protected String getDatabaseMaintenanceSql()
    {
        return "EXEC sp_updatestats;";
    }


    @Override
    public void defragmentIndex(DbSchema schema, String tableSelectName, String indexName)
    {
        SQLFragment sql = new SQLFragment("SELECT avg_fragmentation_in_percent\n" +
            "FROM sys.dm_db_index_physical_stats (DB_ID(), OBJECT_ID(?), NULL, NULL, 'DETAILED') s\n" +
            "INNER JOIN sys.indexes i ON s.object_id = i.object_id AND s.index_id = i.index_id WHERE index_level = 0 AND Name = ?");
        sql.add(tableSelectName);
        sql.add(indexName);

        Double fragmentationPercent = new SqlSelector(schema, sql).getObject(Double.class);

        // Follow the index rebuild/reorganize recommendations at https://msdn.microsoft.com/en-us/library/ms189858.aspx
        if (fragmentationPercent > 0.05)
        {
            String alterSql = "ALTER INDEX " + indexName + " ON " + tableSelectName + " " +
                (fragmentationPercent > 0.30 ? "REBUILD" + (_edition.isOnlineSupported() ? " WITH (ONLINE = ON)" : "") : "REORGANIZE");
            new SqlExecutor(schema).execute(alterSql);
        }
    }


    @Override
    public SQLFragment getISOFormat(SQLFragment date)
    {
        // see http://msdn.microsoft.com/en-us/library/ms187928.aspx
        SQLFragment iso = new SQLFragment("CONVERT(VARCHAR, CAST((");
        iso.append(date);
        iso.append(") AS DATETIME), 121)");
        return iso;
    }


    @Override
    public boolean canShowExecutionPlan()
    {
        return true;
    }

    @Override
    public Collection<String> getQueryExecutionPlan(DbScope scope, SQLFragment sql)
    {
        try (Connection conn = scope.getConnection())
        {
            try
            {
                new SqlExecutor(scope, conn).execute("SET SHOWPLAN_ALL ON");

                // I don't want to inline all the parameters... but SQL Server / jTDS blow up with some (not all)
                // prepared statements with parameters.
                return new SqlSelector(scope, conn, sql.toDebugString()).getCollection(String.class);
            }
            finally
            {
                new SqlExecutor(scope, conn).execute("SET SHOWPLAN_ALL OFF");
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    @Override
    public boolean isProcedureSupportsInlineResults()
    {
        return true;
    }

    public Map<String, MetadataParameterInfo> getParametersFromDbMetadata(DbScope scope, String procSchema, String procName) throws SQLException
    {
        CaseInsensitiveMapWrapper<MetadataParameterInfo> parameters = new CaseInsensitiveMapWrapper<>(new LinkedHashMap<>());

        try (Connection conn = scope.getConnection();
             ResultSet rs = conn.getMetaData().getProcedureColumns(scope.getDatabaseName(), procSchema, procName, null))
        {
            while (rs.next())
            {
                Map<ParamTraits, Integer> traitMap = new HashMap<>();
                if (rs.getInt("COLUMN_TYPE") == DatabaseMetaData.procedureColumnReturn)
                {
                    // jtds reports a column name of "return_code" from the getProcedureColumns call,
                    // but in the return from the execution, it's called "return_status".
                    // It can only be an integer and output parameter.
                    traitMap.put(ParamTraits.direction, DatabaseMetaData.procedureColumnOut);
                    traitMap.put(ParamTraits.datatype, Types.INTEGER);
                    parameters.put("return_status", new MetadataParameterInfo(traitMap));
                }
                else
                {
                    traitMap.put(ParamTraits.direction, rs.getInt("COLUMN_TYPE"));
                    traitMap.put(ParamTraits.datatype, rs.getInt("DATA_TYPE"));
                    //traitMap.put(ParamTraits.required, )
                    parameters.put(StringUtils.substringAfter(rs.getString("COLUMN_NAME"), "@"), new MetadataParameterInfo(traitMap));
                }
            }
        }

        return parameters;
    }

    public String buildProcedureCall(String procSchema, String procName, int paramCount, boolean hasReturn, boolean assignResult)
    {
        StringBuilder sb = new StringBuilder();
        if (hasReturn || assignResult)
        {
            sb.append("? = ");
            paramCount--;
        }
        sb.append("CALL ").append(procSchema).append(".").append(procName);
        if (paramCount > 0)
            sb.append("(");
        for (int i = 0; i < paramCount; i++)
        {
            sb.append("?,");
        }
        if (paramCount > 0)
            sb.append(")");

        return sb.toString();
    }

    @Override
    public void registerParameters(DbScope scope, CallableStatement stmt, Map<String, MetadataParameterInfo> parameters, boolean registerOutputAssignment) throws SQLException
    {
        for (Map.Entry<String, MetadataParameterInfo> parameter : parameters.entrySet())
        {
            String paramName = parameter.getKey();
            MetadataParameterInfo paramInfo = parameter.getValue();
            int datatype = paramInfo.getParamTraits().get(ParamTraits.datatype);
            int direction = paramInfo.getParamTraits().get(ParamTraits.direction);

            if (direction != DatabaseMetaData.procedureColumnOut)
                stmt.setObject(paramName, paramInfo.getParamValue(), datatype); // TODO: Can likely drop the "@"
            if (direction == DatabaseMetaData.procedureColumnInOut || direction == DatabaseMetaData.procedureColumnOut)
                stmt.registerOutParameter(paramName, datatype);
        }
    }

    @Override
    public int readOutputParameters(DbScope scope, CallableStatement stmt, Map<String, MetadataParameterInfo> parameters) throws SQLException
    {
        int returnVal = -1;
        for (Map.Entry<String, MetadataParameterInfo> parameter : parameters.entrySet())
        {
            String paramName = parameter.getKey();
            MetadataParameterInfo paramInfo = parameter.getValue();
            int direction = paramInfo.getParamTraits().get(ParamTraits.direction);
            if (direction == DatabaseMetaData.procedureColumnInOut)
                paramInfo.setParamValue(stmt.getObject(paramName));
            else if (direction == DatabaseMetaData.procedureColumnOut)
                returnVal = stmt.getInt(paramName);
        }
        return returnVal;
    }

    @Override
    public String translateParameterName(String name, boolean dialectSpecific)
    {
        if (dialectSpecific && !StringUtils.startsWith(name, "@"))
            name = "@" + name;
        else if (!dialectSpecific && StringUtils.startsWith(name, "@"))
            name = StringUtils.substringAfter(name, "@");
        return name;
    }


    @Override
    protected TableResolver getTableResolver()
    {
        return _tableResolver;
    }

    @Override
    public boolean canExecuteUpgradeScripts()
    {
        return true;
    }

    // Query INFORMATION_SCHEMA.TABLES directly, since this is 50X faster than jTDS getTables() (which calls sp_tables). Select only the columns we care about. SQL Server always returns NULL for REMARKS.
    private static final String ALL_TABLES_SQL = "SELECT TABLE_NAME, CASE TABLE_TYPE WHEN 'BASE TABLE' THEN 'TABLE' ELSE TABLE_TYPE END AS TABLE_TYPE, NULL AS REMARKS FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_CATALOG = ? AND TABLE_SCHEMA = ?";

    /* Query the system views for columns directly, bypassing jTDS's getColumns() call to sp_columns.
        This allows retrieval of the full list of sparse columns in a wide table. NOTE: This query does not
        return the full set of column metadata properties present in the driver's result set;
        only the subset of properties currently used by the application.
        Acknowledgement: This query is a modified form by the SQL Server internal view sys.spt_columns_odbc_view,
        which is used by sp_columns. It has been simplified for only the return columns and object types of interest,
        and the DATA_TYPEs have the mappings to jdbc types that are normally performed by the driver getColumns() call.
      */
    private static final String ALL_TABLE_COLUMNS_SQL = "SELECT\n" +
            "        COLUMN_NAME         = convert(sysname,c.name),\n" +
            "        DATA_TYPE        = convert(smallint,\n" +
            "                                case\n" +
            "                                when (c.system_type_id = 240) then -- CLR UDT, unknown if this is correct type\n" +
            "                                    -4 \n" +
            "                                when (c.system_type_id = 241) then -- XML\n" +
            "                                    2005\n" +
            "                                when (c.max_length = -1 and c.system_type_id = 167) then -- varchar(max)\n" +
            "                                    2005\n" +
            "                                when (c.max_length = -1 and c.system_type_id = 231) then -- nvarchar(max)\n" +
            "                                    2005\n" +
            "                                when (c.max_length = -1 and c.system_type_id = 165) then -- varbinary(max)\n" +
            "                                    2004\n" +
            "                                when c.system_type_id IN (40, 41, 42, 43) then -- DATE/TIME/DATETIME2/DATETIMEOFFSET\n" +
            "                                     12\n" +
            "                                when c.system_type_id IN (98, 167, 231) then -- sql_variant, varchar, nvarchar\n" +
            "                                     12\n" +
            "                                when c.system_type_id  = 34 then -- image\n" +
            "                                     2004\n" +
            "                when c.system_type_id IN (35, 99) then -- text, ntext\n" +
            "         2005 \n" +
            "        when c.system_type_id IN (58, 61) then -- smalldatetime, datetime\n" +
            "         93\n" +
            "                                when c.system_type_id  = 104 then -- bit\n" +
            "         -7\n" +
            "                                when c.system_type_id  = 48 then -- tinyint\n" +
            "         -6\n" +
            "                                when c.system_type_id  = 127 then -- bigint\n" +
            "         -5\n" +
            "                                when c.system_type_id  = 165 then -- varbinary\n" +
            "         -3\n" +
            "        when c.system_type_id IN (173, 189) then -- binary, timestamp (rowversion)\n" +
            "         -2                                              \n" +
            "        when c.system_type_id IN (36, 175, 239) then -- uniqueidentifier, char, nchar\n" +
            "         1\n" +
            "                                when c.system_type_id  = 108 then -- numeric\n" +
            "         2\n" +
            "        when c.system_type_id IN (60, 106, 122) then -- money, decimal, smallmoney\n" +
            "         3 \n" +
            "                                when c.system_type_id  = 56 then -- int\n" +
            "         4\n" +
            "                                when c.system_type_id  = 52 then -- smallint\n" +
            "         5\n" +
            "                                when c.system_type_id  = 59 then -- real\n" +
            "         7\n" +
            "                                when c.system_type_id  = 62 then -- float\n" +
            "         8\n" +
            "                                end),\n" +
            "        TYPE_NAME         = convert(sysname,\n" +
            "                                case\n" +
            "                                when (t.system_type_id = 240 or t.user_type_id > 255) then -- CLR UDTs\n" +
            "                                    t.name\n" +
            "                                when (c.max_length = -1 and c.system_type_id = 167) then -- varchar(max)\n" +
            "                                    N'text'\n" +
            "                                when (c.max_length = -1 and c.system_type_id = 231) then -- nvarchar(max)\n" +
            "                                    N'ntext'\n" +
            "                                when (c.max_length = -1 and c.system_type_id = 165) then -- varbinary(max)\n" +
            "                                    N'image'\n" +
            "                                else\n" +
            "                                    t.name\n" +
            "                                end) + CASE WHEN c.is_identity = 1 THEN ' identity' ELSE '' END,\n" +
            "        COLUMN_SIZE        = convert(int,\n" +
            "                                case\n" +
            "                                when c.system_type_id in (59,62) then -- FLOAT/REAL\n" +
            "                                    t.precision\n" +
            "                                when c.system_type_id = 241 then -- XML\n" +
            "                                    1073741823\n" +
            "                                when (c.max_length = -1 and c.system_type_id = 167) then -- varchar(max)\n" +
            "                                    2147483647\n" +
            "                                when (c.max_length = -1 and c.system_type_id = 231) then -- nvarchar(max)\n" +
            "                                    1073741823\n" +
            "                                when (c.max_length = -1 and c.system_type_id = 165) then -- varbinary(max)\n" +
            "                                    2147483647\n" +
            "                                when (c.max_length = -1 and c.system_type_id = 240) then -- Large UDT => image for non-SNAC clients\n" +
            "                                    2147483647\n" +
            "                                else\n" +
            "                                    OdbcPrec(c.system_type_id,c.max_length,c.precision)\n" +
            "                                end),\n" +
            "        NULLABLE            = convert(int, c.is_nullable),\n" +
            "        REMARKS             = convert(varchar(254),NULL),\n" +
            "        COLUMN_DEF          = convert(nvarchar(4000), object_definition(ColumnProperty(c.object_id, c.name, 'default'))),\n" +
            "        ORDINAL_POSITION    = ROW_NUMBER() OVER (ORDER BY c.column_id),\n" +
            "        IS_NULLABLE         = CASE WHEN c.is_nullable = 1 THEN 'YES' ELSE 'NO' END\n" +
            "    FROM\n" +
            "        sys.all_columns c inner join\n" +
            "        sys.all_objects o on\n" +
            "            (\n" +
            "                o.object_id = c.object_id and\n" +
            "                o.type in ('S','U','V', 'TF', 'IF') -- limit columns to tables, views, table-valued functions\n" +
            "            ) inner join\n" +
            "        sys.schemas s on \n" +
            "   s.schema_id = o.schema_id inner join\n" +
            "  sys.types t on\n" +
            "            (\n" +
            "                t.user_type_id = c.user_type_id\n" +
            "            ) \n" +
            " WHERE s.name = ? AND o.name = ?";

    @Override
    public DatabaseMetaData wrapDatabaseMetaData(DatabaseMetaData md, DbScope scope)
    {
        return new DatabaseMetaDataWrapper(md)
        {
            @Override
            public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types) throws SQLException
            {
                SQLFragment sql = new SQLFragment(ALL_TABLES_SQL);
                sql.add(catalog);
                sql.add(schemaPattern);

                if (!"%".equals(tableNamePattern))
                {
                    sql.append(" AND TABLE_NAME = ?");
                    sql.add(tableNamePattern);
                }

                return new MetadataSqlSelector(scope, sql).getResultSet();
            }

            @Override
            public ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException
            {
                SQLFragment sql = new SQLFragment(ALL_TABLE_COLUMNS_SQL);
                // Intentionally ignoring the 'catalog'; within the sp_columns proc we're bypassing, it's only used as a check that it is the same as the db_name
                sql.add(schemaPattern);
                sql.add(tableNamePattern);

                if (null != columnNamePattern && !"%".equals(tableNamePattern))
                {
                    sql.append(" AND c.name = ?");
                    sql.add(columnNamePattern);
                }

                return new MetadataSqlSelector(scope, sql).getResultSet();
            }
        };
    }

    private enum TokenType
    {
        BEGIN, END
    }

    private enum Token
    {
        BEGIN(TokenType.BEGIN), CASE(TokenType.BEGIN), END(TokenType.END), COMMIT(TokenType.END);

        private final TokenType _type;

        private EnumSet<Token> _terminatingTokens = null;

        Token(TokenType type)
        {
            _type = type;
        }

        private TokenType getType()
        {
            return _type;
        }

        public void setTerminatingTokens(EnumSet<Token> terminatingTokens)
        {
            _terminatingTokens = terminatingTokens;
        }

        public EnumSet<Token> getTerminatingTokens()
        {
            if (null == _terminatingTokens)
                throw new IllegalStateException("Parser should not have pushed a token having no terminating tokens");

            return _terminatingTokens;
        }

        static
        {
            // EnumSet won't allow these to be called in Token constructor
            BEGIN.setTerminatingTokens(EnumSet.copyOf(Arrays.asList(END, COMMIT)));
            CASE.setTerminatingTokens(EnumSet.copyOf(Collections.singletonList(END)));
        }
    }

    private final static CaseInsensitiveHashSet BEGIN = new CaseInsensitiveHashSet("BEGIN");
    private final static CaseInsensitiveHashSet BEGIN_END = new CaseInsensitiveHashSet("BEGIN", "CASE", "END", "COMMIT");

    @Override
    public Collection<String> getScriptWarnings(String sql)
    {
        // At the moment, we're only checking for stored procedure definitions that aren't followed immediately by a GO
        // statement or end of the script. These will cause major problem if they are missed during script consolidation

        // Dumb little parser that, within stored procedure definitions, matches up each BEGIN with COMMIT/END.
        String[] tokens = sql.replace(";", "").split("\\s+|,");
        int idx = 0;

        while (-1 != (idx = skipToCreateProcedure(tokens, idx)))
        {
            idx += 2;
            String procedureName = tokens[idx];

            idx = skipToToken(tokens, idx, BEGIN);

            if (-1 == idx)
                return Collections.singleton("Stored procedure definition " + procedureName + " has no BEGIN statement!");

            Stack<Token> stack = new Stack<>();
            stack.push(Token.BEGIN);

            while (!stack.isEmpty())
            {
                idx = skipToToken(tokens, idx + 1, BEGIN_END);

                if (-1 == idx)
                    return Collections.singleton("Stored procedure definition " + procedureName + " seems to be missing an END statement!");

                Token token = Token.valueOf(tokens[idx].toUpperCase());

                if (token.getType() == TokenType.BEGIN)
                {
                    // BEGIN or CASE
                    stack.push(token);
                }
                else
                {
                    // END or COMMIT
                    Token beginToken = stack.pop();

                    if (!beginToken.getTerminatingTokens().contains(token))
                        return Collections.singleton("Stored procedure definition " + procedureName + " has mismatched tokens: " + beginToken + " & " + token + "!");
                }
            }

            if (tokens.length <= (idx + 1) || !tokens[idx + 1].equalsIgnoreCase("GO"))
                return Collections.singleton("Stored procedure definition " + procedureName + " doesn't seem to terminate with a GO statement!");
        }

        return Collections.emptyList();
    }

    private final static CaseInsensitiveHashSet CREATE = new CaseInsensitiveHashSet("CREATE", "ALTER");

    private int skipToCreateProcedure(String[] tokens, int idx)
    {
        while (true)
        {
            int i = skipToToken(tokens, idx, CREATE);

            if (-1 == i)
                return -1;

            String token = tokens[i + 1];

            if (token.equalsIgnoreCase("PROCEDURE") || token.equalsIgnoreCase("FUNCTION"))
                return i;

            idx++;
        }
    }

    private int skipToToken(String[] tokens, int start, CaseInsensitiveHashSet desired)
    {
        for (int i = start; i < tokens.length; i++)
            if (desired.contains(tokens[i]))
                return i;

        return -1;
    }
}

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

package org.labkey.bigiron.mssql;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.*;
import org.labkey.api.data.bigiron.ClrAssemblyManager;
import org.labkey.api.data.dialect.ColumnMetaDataReader;
import org.labkey.api.data.dialect.DialectStringHandler;
import org.labkey.api.data.dialect.JdbcHelper;
import org.labkey.api.data.dialect.LimitRowsSqlGenerator;
import org.labkey.api.data.dialect.PkMetaDataReader;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.TableResolver;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.AliasManager;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.template.Warnings;
import org.labkey.bigiron.mssql.synonym.SynonymTableResolver;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.support.CustomSQLExceptionTranslatorRegistry;

import jakarta.servlet.ServletException;
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
import java.util.stream.Collectors;

import static java.sql.Types.NVARCHAR;
import static java.sql.Types.TIMESTAMP_WITH_TIMEZONE;

// Dialect specifics for Microsoft SQL Server
abstract class BaseMicrosoftSqlServerDialect extends SqlDialect
{
    private static final Logger LOG = LogHelper.getLogger(BaseMicrosoftSqlServerDialect.class, "SQL Server-specific SQL generation");

    // SQLServer limits maximum index key size of 900 bytes
    private static final int MAX_INDEX_SIZE = 900;

    private volatile boolean _groupConcatInstalled = false;
    private volatile String _versionYear = null;
    private volatile Edition _edition = null;

    private HtmlString _adminWarning = null;

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
            catch (IllegalArgumentException ignored)
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
            "semantickeyphrasetable, semanticsimilaritydetailstable, semanticsimilaritytable, " +
            "session_user, set, setuser, shutdown, some, statistics, system_user, table, tablesample, textsize, then, to, " +
            "top, tran, transaction, trigger, truncate, try_convert, tsequal, union, unique, unpivot, update, updatetext, " +
            "use, user, values, varying, view, waitfor, when, where, while, with, within, writetext"
        ));
    }

    static
    {
        // The Microsoft JDBC driver does a lackluster job of translating errors to the appropriate SQLState. Do our
        // own translation for the ones we care about. See issue 37040
        CustomSQLExceptionTranslatorRegistry.getInstance().registerTranslator("Microsoft SQL Server", (task, sql, ex) -> {
            if (ex.getErrorCode() == 8134)
            {
                return new DataIntegrityViolationException(ex.getMessage(), ex);
            }
            return null;
        });
    }

    @Override
    protected void addSqlTypeNames(Map<String, Integer> sqlTypeNameMap)
    {
        sqlTypeNameMap.put("BINARY", Types.BINARY);
        sqlTypeNameMap.put("FLOAT", Types.DOUBLE);
        sqlTypeNameMap.put("INT IDENTITY", Types.INTEGER);
        sqlTypeNameMap.put("BIGINT IDENTITY", Types.BIGINT);
        sqlTypeNameMap.put("DATETIME", Types.TIMESTAMP);
        sqlTypeNameMap.put("DATETIME2", Types.TIMESTAMP);
        sqlTypeNameMap.put("NTEXT", Types.LONGVARCHAR);
        sqlTypeNameMap.put("NVARCHAR", Types.VARCHAR);
        sqlTypeNameMap.put("UNIQUEIDENTIFIER", Types.VARCHAR);
        sqlTypeNameMap.put("TIMESTAMP", Types.BINARY);
        // NOTE we don't handle DATETIMEOFFSET (datetime with timezoe)
        sqlTypeNameMap.put("DATETIMEOFFSET", Types.VARCHAR);

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
    public String getSqlTypeName(PropertyStorageSpec prop)
    {
        if (prop.isAutoIncrement())
        {
            if (prop.getJdbcType() == JdbcType.INTEGER)
            {
                return "INT IDENTITY (1, 1)";
            }
            else if (prop.getJdbcType() == JdbcType.BIGINT)
            {
                return "BIGINT IDENTITY (1, 1)";
            }
            else
            {
                throw new IllegalArgumentException("AutoIncrement is not supported for JdbcType " + prop.getJdbcType() + " (" + getSqlTypeName(prop.getJdbcType()) + ")");
            }
        }
        else if (JdbcType.GUID == prop.getJdbcType())
        {
            return "ENTITYID";
        }
        else
        {
            return getSqlTypeName(prop.getJdbcType());
        }
    }

    @Override
    public void prepare(DbScope.LabKeyDataSource dataSource)
    {
        dataSource.setConnectionProperty("sendTimeAsDatetime", "false");
    }

    @Override
    @Nullable
    public String getSqlCastTypeName(JdbcType type)
    {
        return type == JdbcType.VARCHAR ? "NVARCHAR(MAX)" : getSqlTypeName(type);
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

    void setVersionYear(String versionYear)
    {
        _versionYear = versionYear;
    }

    @Override
    public String getProductVersion(String dbmdProductVersion)
    {
        return _versionYear + " (" + dbmdProductVersion + ")" + (null != _edition ? " " + _edition.name() + " Edition" : "");
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

    private static final int TEMPTABLE_GENERATOR_MINSIZE = 1000;

    private final InClauseGenerator _tempTableInClauseGenerator = new TempTableInClauseGenerator();

    @Override
    public SQLFragment appendInClauseSql(SQLFragment sql, @NotNull Collection<?> params)
    {
        if (params.size() >= TEMPTABLE_GENERATOR_MINSIZE)
        {
            SQLFragment ret = _tempTableInClauseGenerator.appendInClauseSql(sql, params);
            if (null != ret)
                return ret;
        }

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
    protected void checkSqlScript(String lowerNoComments, String lowerNoCommentsNoWhiteSpace, Collection<String> errors)
    {
        if (lowerNoComments.startsWith("use ") || lowerNoComments.contains("\nuse "))
            errors.add("USE statements are prohibited");
    }

    private enum ReselectType {INSERT, UPDATE, OTHER}

    private ReselectType getReselectType(String sql)
    {
        String trimmed = sql.trim();

        if (StringUtils.startsWith(trimmed, ReselectType.INSERT.name()))
            return ReselectType.INSERT;
        else if (StringUtils.startsWith(trimmed, ReselectType.UPDATE.name()))
            return ReselectType.UPDATE;
        else
            return ReselectType.OTHER;
    }

    @Override
    public String addReselect(SQLFragment sql, ColumnInfo column, @Nullable String proposedVariable)
    {
        String columnName = column.getSelectName();
        boolean hasDbTriggers = column.getParentTable().hasDbTriggers();

        return _addReselect(sql, columnName, hasDbTriggers, proposedVariable);
    }

    public String _addReselect(SQLFragment sql, String columnName, boolean useOutputIntoTableVar, @Nullable String proposedVariable)
    {
        ReselectType type = getReselectType(sql.getRawSQL());

        if (type == ReselectType.OTHER)
            throw new IllegalStateException("Can re-select only from INSERT or UPDATE statement");

        StringBuilder outputSql = new StringBuilder("OUTPUT INSERTED.");
        outputSql.append(columnName);

        // SQL Server OUTPUT ... INTO syntax requires a table variable, since, in theory, you could insert/update multiple
        // rows and select multiple columns. We don't support that, however.
        if (useOutputIntoTableVar || proposedVariable != null)
        {
            outputSql.append(" INTO @TableVar");
        }

        SqlScanner scanner = new SqlScanner(sql);
        int start;
        int end;

        if (type == ReselectType.INSERT)
        {
            start = scanner.indexOf(')');

            if (-1 == start)
                throw new IllegalStateException("Unable to insert OUTPUT clause");

            end = start;

            do
            {
                end++;
            }
            while (Character.isWhitespace(sql.charAt(end)));
        }
        else
        {
            end = scanner.indexOf("WHERE");

            if (-1 == end)
                throw new IllegalStateException("Unable to insert OUTPUT clause");

            start = end;

            do
            {
                start--;
            }
            while (Character.isWhitespace(sql.charAt(start)));
        }

        outputSql.append(sql.subSequence(start + 1, end));
        sql.insert(end, outputSql.toString());

        String ret = null;

        if (useOutputIntoTableVar || proposedVariable != null)
        {
            SQLFragment declareTableVar = new SQLFragment("DECLARE @TableVar TABLE(").appendIdentifier(columnName).append(" INTEGER)").appendEOS().append("\n");
            sql.prepend(declareTableVar);

            if (null != proposedVariable)
            {
                ret = "@" + proposedVariable;

                // Note: Assume one row and one column for now
                sql.appendEOS().append("\nSELECT TOP 1 ").append(ret).append(" = ").append(columnName).append(" FROM @TableVar").appendEOS();
            }
            else
            {
                // Note: Assume one row and one column for now
                sql.appendEOS().append("\nSELECT TOP 1 ").append(columnName).append(" FROM @TableVar").appendEOS();
            }
        }

        return ret;
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

    @Override
    public SQLFragment limitRows(SQLFragment frag, int maxRows)
    {
        if (maxRows != Table.ALL_ROWS)
        {
            String sql = frag.getRawSQL();
            if (!sql.substring(0, 6).equalsIgnoreCase("SELECT"))
                throw new IllegalArgumentException("ERROR: Limit SQL doesn't start with SELECT: " + sql);

            int index = 6;
            // NOTE: check for the trailing space to ensure we do not accidentally split a field name that begins with distinct
            if (sql.substring(0, 16).equalsIgnoreCase("SELECT DISTINCT "))
                index = 15;
            frag.insert(index, " TOP " + (Table.NO_ROWS == maxRows ? 0 : maxRows));
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

        // If there's no offset or requesting no rows then we can use a simple TOP approach for maxRows, which doesn't
        // require an ORDER BY and allows 0 rows (unlike OFFSET + FETCH)
        if (offset == 0 || maxRows == Table.NO_ROWS)
        {
            SQLFragment sql = LimitRowsSqlGenerator.appendFromFilterOrderAndGroupByNoValidation(select, from, filter, order, groupBy);

            return limitRows(sql, maxRows);
        }
        else
        {
            if (StringUtils.isBlank(order))
                throw new IllegalArgumentException("ERROR: ORDER BY clause required to use maxRows and offset");

            return _limitRows(select, from, filter, order, groupBy, maxRows, offset);
        }
    }

    // Called only if offset is > 0, maxRows is not NO_ROWS, and order is non-blank
    abstract protected SQLFragment _limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, @NotNull String order, String groupBy, int maxRows, long offset);

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
    public String getMedianFunction()
    {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement");
    }

    @Override
    public String getClobLengthFunction()
    {
        return "datalength";
    }

    @Override
    public SQLFragment getStringIndexOfFunction(SQLFragment toFind, SQLFragment toSearch)
    {
        // Use CHARINDEX instead of PATINDEX, which does wildcard matching
        SQLFragment result = new SQLFragment("CHARINDEX(");
        result.append(toFind);
        result.append(", ");
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

    private static final Pattern SELECT = Pattern.compile("\\bSELECT\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public boolean supportsGroupConcatSubSelect()
    {
        return false;
    }

    // Uses custom CLR aggregate function defined in group_concat_install.sql
    @Override
    public SQLFragment getGroupConcat(SQLFragment sql, boolean distinct, boolean sorted, @NotNull SQLFragment delimiterSQL, boolean includeNulls)
    {
        // SQL Server does not support aggregates on sub-queries; return a string constant in that case to keep from
        // blowing up. TODO: Don't pass sub-selects into group_contact.
        if (SELECT.matcher(sql.getSQL()).find())
            return new SQLFragment("'NOT SUPPORTED - GROUP_CONCAT IS NOT VALID IN A SUB-SELECT'");

        if (!supportsGroupConcat())
            return new SQLFragment("'NOT SUPPORTED - GROUP_CONCAT FUNCTION IS NOT INSTALLED'");

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

        if (includeNulls)
        {
            result.append("COALESCE(CAST(");
        }
        result.append(sql);
        if (includeNulls)
        {
            result.append(" AS NVARCHAR(MAX)), '')");
        }
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
    public SQLFragment getSelectConcat(SQLFragment selectSql, String delimiter)
    {
        String sql = selectSql.getRawSQL().toUpperCase();

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
        // This closes the COALESCE that's injected a couple of lines down, and starts the SQL Server-specific
        // syntax that concludes later with FOR XML PATH
        ret.insert(fromIndex, "AS NVARCHAR(MAX)), '') AS [text()] ");
        int selectIndex = sql.indexOf("SELECT");
        ret.insert(selectIndex + "SELECT".length(), "'" + delimiter + "' + COALESCE(CAST(");
        ret.insert(0, "SUBSTRING ((");
        ret.append(" FOR XML PATH ('')), ");
        // Trim off the first delimiter
        ret.appendValue(delimiter.length() + 1);
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
        return ("S0001".equals(e.getSQLState()) && e.getErrorCode() == 4060); // Microsoft driver
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
        return "CONVERT(DATETIME, CONVERT(VARCHAR, (" + expression + "), 111))"; // 111: yyyy/mm/dd
    }

    @Override
    public String getDateTimeToTimeCast(String columnName)
    {
        return String.format("DATEADD(day, DATEDIFF(day, %s, '19700101'), %s )", columnName, columnName);
    }

    @Override
    public SQLFragment getNumericCast(SQLFragment expression)
    {
        SQLFragment cast = new SQLFragment(expression);
        cast.setSqlUnsafe("CAST(" + cast.getRawSQL() + " AS FLOAT)");
        return cast;
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
    public boolean supportsNativeGreatestAndLeast()
    {
        return false;
    }

    @Override
    public SQLFragment getGreatestAndLeastSQL(String method, SQLFragment... arguments)
    {
        // Example TSQL equivalent to "greatest(col1, col2)": "(SELECT MAX(myCol) FROM (VALUES (col1), (col2)) AS myTbl(myCol))"

        String aggregate;
        if ("greatest".equalsIgnoreCase(method))
            aggregate = "MAX";
        else if ("least".equalsIgnoreCase(method))
            aggregate = "MIN";
        else
            throw new UnsupportedOperationException("Parameter 'method' must either be 'greatest' or 'least'. Was '" + method + "'.");

        SQLFragment ret = new SQLFragment();
        ret.append("(SELECT ");
        ret.append(aggregate).append("(virtualCol) ");
        ret.append("FROM (VALUES ");
        String comma = "";
        for (SQLFragment arg : arguments)
        {
            ret.append(comma);
            ret.append("(").append(arg).append(")");
            comma = ",";
        }
        ret.append(") AS virtualTbl(virtualCol))");
        return ret;
    }

    @Override
    protected String getSystemTableNames()
    {
        return "dtproperties,sysconstraints,syssegments";
    }

    private static final Set<String> SYSTEM_SCHEMAS = CaseInsensitiveHashSet.of("db_accessadmin", "db_backupoperator",
            "db_datareader", "db_datawriter", "db_ddladmin", "db_denydatareader", "db_denydatawriter", "db_owner",
            "db_securityadmin", "dbo", "guest", "INFORMATION_SCHEMA", "sys");

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
        return "UPDATE STATISTICS " + tableName;
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
    private static final Pattern PROC_PATTERN = Pattern.compile("^\\s*EXEC(?:UTE)?\\s+core\\.((executeJava(?:Upgrade|Initialization)Code\\s*'(.+)')|(bulkImport\\s*'(.+)'\\s*,\\s*'(.+)'\\s*,\\s*'(.+)'))\\s*,?\\s*(\\d)?;?\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

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
    public String getDefaultDatabaseName()
    {
        return "master";
    }


    @Override
    public JdbcHelper getJdbcHelper()
    {
        return new SqlServerJdbcHelper();
    }


    /*
        SQL Server example connection URLs we need to parse:

        jdbc:sqlserver://;databaseName=foo
        jdbc:sqlserver://;database=foo
        jdbc:sqlserver://host;databaseName=foo
        jdbc:sqlserver://host;database=foo
        jdbc:sqlserver://host:1433;databaseName=foo
        jdbc:sqlserver://host:1433;database=foo
        jdbc:sqlserver://host:1433;databaseName=foo;SelectMethod=cursor
        jdbc:sqlserver://host:1433;database=foo;SelectMethod=cursor
        jdbc:sqlserver://host:1433;SelectMethod=cursor;databaseName=database
        jdbc:sqlserver://host:1433;SelectMethod=cursor;database=database

        Note: SQL Server JDBC driver accepts connection URLs that lack a "databaseName" parameter (in which case the
        server uses the "default" database). But LabKey requires this parameter, especially when creating a new database.
        Although not documented, the driver will accept "database" as a synonym for "databaseName", so we allow it.
    */

    private static class SqlServerJdbcHelper implements JdbcHelper
    {
        @Override
        public String getDatabase(String url) throws ServletException
        {
            if (url.startsWith("jdbc:sqlserver://"))
            {
                int dbDelimiter = url.indexOf(";database=");
                if (-1 == dbDelimiter)
                    dbDelimiter = url.indexOf(";databaseName=");
                if (-1 == dbDelimiter)
                    throw new ServletException("Invalid sql server connection url; \"databaseName\" property is required: " + url);
                dbDelimiter = url.indexOf("=", dbDelimiter)+1;
                int dbEnd = url.indexOf(";", dbDelimiter);
                if (-1 == dbEnd)
                    dbEnd = url.length();
                return url.substring(dbDelimiter, dbEnd);
            }
            else
                throw new ServletException("Unsupported connection url; must begin with \"jdbc:sqlserver://\": " + url);
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
            case CreateTable -> sql.addAll(getCreateTableStatements(change));
            case DropTable -> sql.add("DROP TABLE " + change.getSchemaName() + "." + change.getTableName());
            case AddColumns -> sql.addAll(getAddColumnsStatements(change));
            case DropColumns -> sql.add(getDropColumnsStatement(change));
            case RenameColumns -> sql.addAll(getRenameColumnsStatements(change));
            case DropIndicesByName -> sql.addAll(getDropIndexByNameStatements(change));
            case DropIndices -> sql.addAll(getDropIndexStatements(change));
            case AddIndices -> sql.addAll(getCreateIndexStatements(change));
            case ResizeColumns, ChangeColumnTypes -> sql.addAll(getChangeColumnTypeStatement(change));
            case DropConstraints -> sql.addAll(getDropConstraintsStatement(change));
            case AddConstraints -> sql.addAll(getAddConstraintsStatement(change));
            default -> throw new IllegalArgumentException("Unsupported change type: " + change.getType());
        }

        return sql;
    }

    /**
     * Generate the Alter Table statement to change the size and/or data type of a column
     *
     * NOTE: expects data size check to be done prior,
     *       will throw a SQL exception if not able to change size due to existing data
     *       will throw an Argument exception if attempting to change Key column
     */
    private List<String> getChangeColumnTypeStatement(TableChange change)
    {
        change.updateResizeIndices();
        List<String> statements = new ArrayList<>(getDropIndexStatements(change));

        //Generate the alter table portion of statement
        String tableIdentifier = makeTableIdentifier(change);
        String alterTableSegment = String.format("ALTER TABLE %s", tableIdentifier);

        //Don't use getSqlColumnSpec as constraints must be dropped and re-applied (exception for NOT NULL)
        for (PropertyStorageSpec column : change.getColumns())
        {
            //T-SQL only allows 1 ALTER COLUMN clause per ALTER TABLE statement
            String statement;

            String columnName = makeLegalIdentifier(column.getName());
            if (column.getJdbcType().isDateOrTime())
            {
                // create a temp column
                String tempColumnName = column.getName() + "~~temp~~";
                String addTempColumnStatement = alterTableSegment
                        + String.format(" ADD %s", getSqlColumnSpec(column, tempColumnName));
                statements.add(addTempColumnStatement);

                // copy casted value to temp column
                String updateColumnValueStatement = "UPDATE " + tableIdentifier
                        + String.format(" SET %s = CAST(%s AS %s)", makeLegalIdentifier(tempColumnName), columnName, getSqlTypeName(column));
                statements.add(updateColumnValueStatement);

                // drop original column
                String dropColumnStatement = alterTableSegment
                        + String.format(" DROP COLUMN %s", columnName);
                statements.add(dropColumnStatement);

                // rename temp column to original column name
                String renameColumnStatement = String.format("EXEC sp_rename '%s','%s','COLUMN'",
                        tableIdentifier + "." + makeLegalIdentifier(tempColumnName), column.getName() /* don't use quote in sp_rename */);
                statements.add(renameColumnStatement);
            }
            else
            {
                if (column.getJdbcType().isText())
                {
                    //T-SQL will throw an error for nvarchar sizes >4000
                    //Use the common default max size to make type change to nvarchar(max)/text consistent
                    String size = column.getSize() == -1 || column.getSize() > SqlDialect.MAX_VARCHAR_SIZE ?
                            "max" :
                            column.getSize().toString();

                    statement = alterTableSegment + String.format(" ALTER COLUMN %s %s(%s) ",
                            makeLegalIdentifier(column.getName()),
                            getSqlTypeName(column.getJdbcType()),
                            size);
                }
                else
                {
                    statement = alterTableSegment + String.format(" ALTER COLUMN %s %s ",
                            makeLegalIdentifier(column.getName()),
                            getSqlTypeName(column.getJdbcType()));
                }

                //T-SQL will drop any existing null constraints
                statement += column.isNullable() ? "NULL;" : "NOT NULL;";
                statements.add(statement);
            }

        }
        statements.addAll(getCreateIndexStatements(change));

        return statements;
    }

    private List<String> getCreateTableStatements(TableChange change)
    {
        List<String> statements = new ArrayList<>();
        List<String> createTableSqlParts = new ArrayList<>();
        String pkColumn = null;
        boolean pkClustered = true;
        for (PropertyStorageSpec prop : change.getColumns())
        {
            createTableSqlParts.add(getSqlColumnSpec(prop));
            if (prop.isPrimaryKey())
            {
                assert null == pkColumn : "no more than one primary key defined";
                pkColumn = prop.getName();
                pkClustered = !prop.isPrimaryKeyNonClustered();
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
                    .append(tableInfo).append(" (")
                    .append(foreignKey.getForeignColumnName()).append(")");
            createTableSqlParts.add(fkString.toString());
        }

        statements.add(String.format("CREATE TABLE %s (%s)", makeTableIdentifier(change), StringUtils.join(createTableSqlParts, ",\n")));

        if (null != pkColumn)
        {
            Constraint constraint = new Constraint(change.getTableName(), Constraint.CONSTRAINT_TYPES.PRIMARYKEY, pkClustered, null);

            statements.add(String.format("ALTER TABLE %s ADD CONSTRAINT %s %s %s (%s)",
                    makeTableIdentifier(change),
                    constraint.getName(),
                    constraint.getType(),
                    (constraint.isCluster()?"":"NONCLUSTERED"),
                    makeLegalIdentifier(pkColumn)));

        }
        addCreateIndexStatements(statements, change);
        statements.addAll(getAddConstraintsStatement(change));
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
            if (index.columnNames.length > 16)
                throw new IllegalArgumentException(String.format("Error creating index over '%s' for table '%s.%s'.  Maximum number of columns in an index is 16",
                        StringUtils.join(index.columnNames, ", "), change.getSchemaName(), change.getTableName()));

            // If the set of columns is larger than 900 bytes, creating an index will succeed, but fail when trying to insert
            List<PropertyStorageSpec> specs = change.toSpecs(Arrays.asList(index.columnNames));
            long bytes = specs.stream().mapToLong(this::columnStorageSize).sum();

            if (bytes <= MAX_INDEX_SIZE)
            {
                statements.add(String.format("IF NOT EXISTS (SELECT 1 FROM sys.indexes i where i.name = '%s') CREATE %s %s INDEX %s ON %s (%s)",
                        nameIndex(change.getTableName(), index.columnNames, false),
                        index.isUnique ? "UNIQUE" : "",
                        index.isClustered ? "CLUSTERED" : "",
                        nameIndex(change.getTableName(), index.columnNames, false),
                        makeTableIdentifier(change),
                        makeLegalIdentifiers(index.columnNames)));
            }
            else
            {
                if (!index.isUnique)
                {
                    // Once Issue 26311 is fixed, throw an exception instead of logging a warning
                    //throw new IllegalArgumentException("Index over large columns only supported for unique");
                    LOG.warn(String.format("Error creating index over '%s' for table '%s.%s'.  Index over large columns only supported for unique",
                        StringUtils.join(index.columnNames, ", "), change.getSchemaName(), change.getTableName()));
                    return;
                }

                if (index.columnNames.length > 1)
                    throw new IllegalArgumentException(String.format("Error creating index over '%s' for table '%s.%s'.  Index over large columns currently only supported for a single string column",
                        StringUtils.join(index.columnNames, ", "), change.getSchemaName(), change.getTableName()));

                final String columnName = index.columnNames[0];
                final PropertyStorageSpec spec = specs.get(0);
                if (spec == null || !spec.getJdbcType().isText())
                    throw new IllegalArgumentException(String.format("Error creating index over '%s' for table '%s.%s'.  Index over large columns currently only supported for a single string column",
                        StringUtils.join(index.columnNames, ", "), change.getSchemaName(), change.getTableName()));

                final String legalColumnName = makeLegalIdentifier(columnName);
                final String hashedColumn = makeLegalIdentifier(PropertyStorageSpec.HASHED_COLUMN_PREFIX + columnName);
                final String tableName = makeTableIdentifier(change);

                // Create computed hash column
                // NOTE: Unfortunately, HASHBYTES is limited to 8000 bytes on SqlServer versions < 2016 so get the initial 4000 characters (nvarchars are 2 bytes each)
                // HASHBYTES is typed as varbinary(8000) but SHA2_512 always returns 64-bytes.  The cast is added to remove the "900-byte" limit warning when creating the index over the column.
                statements.add(String.format("ALTER TABLE %s\n" +
                        "ADD %s AS CAST(HASHBYTES('SHA2_512', LOWER(LEFT(%s, 4000))) AS binary(64)) PERSISTED",
                        tableName,
                        hashedColumn,
                        legalColumnName));

                // Add a index over the computed hash column
                statements.add(String.format("CREATE INDEX %s ON %s (%s) INCLUDE (%s)",
                        nameIndex(change.getTableName(), new String[] { columnName }, false),
                        tableName,
                        hashedColumn,
                        legalColumnName));

                // Create trigger to enforce uniqueness using the hashed column index
                statements.add(String.format(
                        "CREATE TRIGGER %s ON %s\n" +
                        "FOR INSERT, UPDATE AS\n" +
                        "BEGIN\n" +
                        "  SET NOCOUNT ON\n" +
                        "  IF EXISTS (\n" +
                        "    SELECT 1 FROM %s x\n" +
                        "    INNER JOIN INSERTED i\n" +
                        "    ON  x.%s = HASHBYTES('SHA2_512', LOWER(LEFT(i.%s, 4000)))\n" +
                        "    AND x.%s = i.%s\n" +
                        "    GROUP BY x.%s\n" +
                        "    HAVING COUNT(*) > 1\n" +
                        "  )\n" +
                        "  BEGIN\n" +
                        "    RAISERROR(N'" + CUSTOM_UNIQUE_ERROR_MESSAGE + " ''%s'' on table ''%s''.', 16, 123)\n" +
                        "  END\n" +
                        "END\n",
                        nameTrigger(change.getTableName(), new String[]{columnName}),
                        tableName,
                        tableName,
                        // ON  x.%s = HASHBYTES('SHA2_512', %s)
                        hashedColumn, legalColumnName,
                        // AND x.%s = i.%s
                        legalColumnName, legalColumnName,
                        // GROUP BY x.%s
                        legalColumnName,
                        // RAISERROR message
                        legalColumnName, tableName
                ));
            }
        }
    }

    private int columnStorageSize(PropertyStorageSpec spec)
    {
        return columnStorageSize(spec, null);
    }

    // Returns the column storage size in bytes
    // https://technet.microsoft.com/en-us/library/ms187752.aspx
    private int columnStorageSize(PropertyStorageSpec spec, @Nullable Integer oldScale)
    {
        JdbcType jdbcType = spec.getJdbcType();
        Integer size = oldScale != null ? oldScale : spec.getSize();
        switch (jdbcType)
        {
            case BIGINT:
                return 8;
            case BINARY:
                return size;
            case BOOLEAN:
                return 1;
            case CHAR:
                return 2 * size; /* NCHAR is two bytes */
            case DECIMAL:
//                if (size < 10) return 5;
//                if (size < 20) return 9;
//                if (size < 29) return 13;
//                if (size < 39) return 17;
//                throw new IllegalArgumentException("precision must be < 38");
                // We provision FLOAT(15,4) columns
                return 9;
            case DOUBLE:
                if (size != null && size < 25)
                    return 4;
                else
                    return 8;
            case INTEGER:
                return 4;
            case LONGVARBINARY:
                return 2^31 - 1;
            case LONGVARCHAR:
                return 2^31 - 1;
            case REAL:
                return 4;
            case SMALLINT:
                return 2;
            case DATE:
                // date is 3 bytes, but we actually create datetime instead, which is 8 bytes
                return 8;
            case TIME:
                // time is 5 bytes, but we actually create datetime instead, which is 8 bytes
                return 8;
            case TIMESTAMP:
                // timestamp is 8 bytes, but we actually create datetime instead, which is 8 bytes
                return 8;
            case TINYINT:
                return 1;
            case VARBINARY:
                if (size > 8000)
                    return 2^31-1;
                return size;
            case VARCHAR:
                if (size == -1 || size > SqlDialect.MAX_VARCHAR_SIZE)
                    return Integer.MAX_VALUE; /* 2^31-1 */
                else
                    return size * 2; /* NVARCHAR is two bytes */
            case GUID:
                return 36;
            case NULL:
            case OTHER:
            default:
                return 0;
        }
    }

    private Collection<? extends String> getDropIndexByNameStatements(TableChange change)
    {
        List<String> statements = new ArrayList<>();
        addDropIndexByNameStatements(statements, change);
        return statements;
    }

    private void addDropIndexByNameStatements(List<String> statements, TableChange change)
    {
        for (String indexName : change.getIndicesToBeDroppedByName())
        {
            statements.add(getDropIndexCommand(indexName, change));
        }
    }

    private String getDropIndexCommand(String indexName, TableChange change)
    {
        return getDropIndexCommand(makeTableIdentifier(change),indexName);
    }

    private List<String> getDropIndexStatements(TableChange change)
    {
        List<String> statements = new ArrayList<>();
        addDropIndexStatements(statements, change);
        return statements;
    }

    // There are cases where property scale was not saved properly. When updating these with accurate scale we cannot
    // determine how the previous indexes were created since the scale was null.  This function will basically try to
    // delete large and small column indexes for the particular index.
    private void bestEffortDropIndexStatements(List<String> statements, TableChange change, PropertyStorageSpec.Index index)
    {
        String nameIndex = nameIndex(change.getTableName(), index.columnNames, false);
        String nameIndexLegacy = nameIndex(change.getTableName(), index.columnNames, true);

        statements.add(String.format("IF EXISTS (SELECT 1 FROM sys.indexes i WHERE i.name = '%s') DROP INDEX %s ON %s",
                nameIndex,
                nameIndex,
                makeTableIdentifier(change)
        ));
        // Issue 26311: We add to drop statements to capture indexes named with legacy truncation limit as well as new, longer truncation limit
        statements.add(String.format("IF EXISTS (SELECT 1 FROM sys.indexes i WHERE i.name = '%s') DROP INDEX %s ON %s",
                nameIndexLegacy,
                nameIndexLegacy,
                makeTableIdentifier(change)
        ));

        final String columnName = index.columnNames[0];
        final String hashedColumn = makeLegalIdentifier(PropertyStorageSpec.HASHED_COLUMN_PREFIX + columnName);
        final String tableName = makeTableIdentifier(change);

        statements.add(String.format("IF EXISTS (SELECT 1 FROM sys.triggers i WHERE i.name = '%s') DROP TRIGGER %s.%s",
                nameTrigger(change.getTableName(), new String[]{columnName}),
                change.getSchemaName(),
                nameTrigger(change.getTableName(), new String[]{columnName})));

        statements.add(String.format("IF EXISTS (SELECT 1 FROM sys.indexes i WHERE i.name = '%s') DROP INDEX %s ON %s",
                nameIndex(change.getTableName(), index.columnNames, false),
                nameIndex(change.getTableName(), index.columnNames, false),
                tableName
        ));

        statements.add(String.format("IF EXISTS (SELECT 1 FROM sys.columns i WHERE i.name = '%s' AND i.object_id = OBJECT_ID(N'%s')) ALTER TABLE %s DROP COLUMN %s",
                hashedColumn,
                tableName,
                tableName,
                hashedColumn));
    }

    private void addDropIndexStatements(List<String> statements, TableChange change)
    {
        for (PropertyStorageSpec.Index index : change.getIndexedColumns())
        {
            // If the set of columns is larger than 900 bytes, creating an index will succeed, but fail when trying to insert
            int bytes = 0;
            TableChange.IndexSizeMode mode = change.getIndexSizeMode();
            if(mode == TableChange.IndexSizeMode.Auto)
            {
                List<PropertyStorageSpec> specs = change.toSpecs(Arrays.asList(index.columnNames));
                bytes = specs.stream().mapToInt(spec -> {
                    // Use the old scale if we're in the process of resizing the column
                    Integer oldScale = change.getColumnResizes().get(spec.getName());
                    return columnStorageSize(spec, oldScale);
                }).sum();
            }

            String nameIndex = nameIndex(change.getTableName(), index.columnNames, false);
            String nameIndexLegacy = nameIndex(change.getTableName(), index.columnNames, true);
            if ((mode == TableChange.IndexSizeMode.Auto && bytes < MAX_INDEX_SIZE) || mode == TableChange.IndexSizeMode.Normal)
            {
                statements.add(String.format("IF EXISTS (SELECT 1 FROM sys.indexes i where i.name = '%s') DROP INDEX %s ON %s",
                        nameIndex,
                        nameIndex,
                        makeTableIdentifier(change)
                ));
                // Issue 26311: We add to drop statements to capture indexes named with legacy truncation limit as well as new, longer truncation limit
                statements.add(String.format("IF EXISTS (SELECT 1 FROM sys.indexes i where i.name = '%s') DROP INDEX %s ON %s",
                        nameIndexLegacy,
                        nameIndexLegacy,
                        makeTableIdentifier(change)
                ));
            }
            else
            {
                if (index.columnNames.length > 1)
                {
                    bestEffortDropIndexStatements(statements, change, index);
                }
                else
                {
                    final String columnName = index.columnNames[0];
                    final String hashedColumn = makeLegalIdentifier(PropertyStorageSpec.HASHED_COLUMN_PREFIX + columnName);
                    final String tableName = makeTableIdentifier(change);

                    statements.add(String.format("IF EXISTS (SELECT 1 FROM sys.triggers i WHERE i.name = '%s') DROP TRIGGER %s.%s",
                            nameTrigger(change.getTableName(), new String[]{columnName}),
                            change.getSchemaName(),
                            nameTrigger(change.getTableName(), new String[]{columnName})));

                    statements.add(String.format("IF EXISTS (SELECT 1 FROM sys.indexes i WHERE i.name = '%s') DROP INDEX %s ON %s",
                            nameIndex,
                            nameIndex,
                            tableName
                    ));

                    statements.add(String.format("IF EXISTS (SELECT 1 FROM sys.indexes i where i.name = '%s') DROP INDEX %s ON %s",
                            nameIndexLegacy,
                            nameIndexLegacy,
                            tableName
                    ));

                    statements.add(String.format("IF EXISTS (SELECT 1 FROM sys.columns i WHERE i.name = '%s' AND i.object_id = OBJECT_ID(N'%s')) ALTER TABLE %s DROP COLUMN %s",
                            hashedColumn,
                            tableName,
                            tableName,
                            hashedColumn));
                }
            }
        }
    }

    private String makeTableIdentifier(TableChange change)
    {
        assert AliasManager.isLegalName(change.getTableName());
        return change.getSchemaName() + "." + change.getTableName();
    }

    @Override
    public String nameIndex(String tableName, String[] indexedColumns)
    {
        return nameIndex(tableName, indexedColumns, false);
    }

    public String nameIndex(String tableName, String[] indexedColumns, boolean useLegacyMaxLength)
    {
        return AliasManager.makeLegalName(tableName + '_' + StringUtils.join(indexedColumns, "_"), this, useLegacyMaxLength);
    }

    private String nameTrigger(String tableName, String[] indexedColumns)
    {
        return AliasManager.makeLegalName("TR_" + tableName + '_' + StringUtils.join(indexedColumns, "_"), this);
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
            String oldName = nameIndex(change.getTableName(), oldIndex.columnNames, false);
            String newName = nameIndex(change.getTableName(), newIndex.columnNames, false);
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

    private List<String> getDropConstraintsStatement(TableChange change)
    {
        List<String> statements = change.getConstraints().stream().map(constraint -> String.format("ALTER TABLE %s DROP CONSTRAINT %s",
                change.getSchemaName() + "." + change.getTableName(), constraint.getName())).collect(Collectors.toList());

        return statements;
    }

    private List<String> getAddConstraintsStatement(TableChange change)
    {
        List<String> statements = new ArrayList<>();
        Collection<Constraint> constraints = change.getConstraints();

        if(null!=constraints && !constraints.isEmpty())
        {
            statements = constraints.stream().map(constraint ->
                    String.format("IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS i where i.CONSTRAINT_NAME = '%s') ALTER TABLE %s ADD CONSTRAINT %s %s (%s)",
                            constraint.getName(), change.getSchemaName() + "." + change.getTableName(), constraint.getName(),
                            constraint.getType(), StringUtils.join(constraint.getColumns(), ","))).collect(Collectors.toList());
        }

        return statements;
    }

    private List<String> getAddColumnsStatements(TableChange change)
    {
        Collection<String> sqlParts = new ArrayList<>();
        String pkColumn = null;
        Constraint constraint = null;

        for (PropertyStorageSpec prop : change.getColumns())
        {
            sqlParts.add(getSqlColumnSpec(prop));
            if (prop.isPrimaryKey())
            {
                assert null == pkColumn : "no more than one primary key defined";
                pkColumn = prop.getName();
                constraint = new Constraint(change.getTableName(), Constraint.CONSTRAINT_TYPES.PRIMARYKEY, !prop.isPrimaryKeyNonClustered(), null);
            }
        }

        List<String> statements = sqlParts.stream().map(sql ->
                String.format("ALTER TABLE %s ADD %s", change.getSchemaName() + "." + change.getTableName(), sql)).collect(Collectors.toList());
        if (null != pkColumn)
        {
            statements.add(String.format("ALTER TABLE %s ADD CONSTRAINT %s %s %s (%s)",
                    makeTableIdentifier(change),
                    constraint.getName(),
                    constraint.getType(),
                    (constraint.isCluster()?"":PropertyStorageSpec.CLUSTER_TYPE.NONCLUSTERED),
                    makeLegalIdentifier(pkColumn)));
        }

        return statements;
    }

    private String getSqlColumnSpec(PropertyStorageSpec prop)
    {
        return getSqlColumnSpec(prop, prop.getName());
    }

    private String getSqlColumnSpec(PropertyStorageSpec prop, String columnName)
    {
        List<String> colSpec = new ArrayList<>();
        colSpec.add(makeLegalIdentifier(columnName));
        colSpec.add(getSqlTypeName(prop));

        if (prop.getJdbcType() == JdbcType.VARCHAR)
        {
            // If size is -1 or is greater than allowed size, change to Max
            if (prop.getSize() == -1 || prop.getSize() > SqlDialect.MAX_VARCHAR_SIZE)
            {
                colSpec.add("(MAX)");
            }
            else
            {
                colSpec.add("(" + prop.getSize() + ")");
            }
        }
        else if (prop.getJdbcType() == JdbcType.DECIMAL)
            colSpec.add(DEFAULT_DECIMAL_SCALE_PRECISION);

        if (prop.isPrimaryKey() || !prop.isNullable())
            colSpec.add("NOT NULL");

        if (null != prop.getDefaultValue())
        {
            if (prop.getJdbcType() == JdbcType.BOOLEAN)
            {
                String defaultClause = " DEFAULT " +
                        ((Boolean)prop.getDefaultValue() ? getBooleanTRUE() : getBooleanFALSE());
                colSpec.add(defaultClause);
            }
            else if (prop.getJdbcType() == JdbcType.VARCHAR)
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
    public void afterCoreUpgrade(ModuleContext context)
    {
        GroupConcatInstallationManager.get().ensureInstalled(context);
    }

    public void setAdminWarning(HtmlString warning)
    {
        _adminWarning = warning;
    }

    @Override
    public void addAdminWarningMessages(Warnings warnings, boolean showAllWarnings)
    {
        ClrAssemblyManager.addAdminWarningMessages(warnings);

        if (null != _adminWarning)
            warnings.add(_adminWarning);
        else if (showAllWarnings)
            warnings.add(HtmlString.of(MicrosoftSqlServerDialectFactory.getStandardWarningMessage("no longer supports", _versionYear)));
    }

    @Override
    public String prepare(DbScope scope)
    {
        _groupConcatInstalled = GroupConcatInstallationManager.get().isInstalled(scope);
        _edition = getEdition(scope);

        return super.prepare(scope);
    }

    @Override
    public void prepareConnection(Connection conn) throws SQLException
    {
        Statement stmt = conn.createStatement();
        stmt.execute("SET ARITHABORT ON");
        stmt.close();
    }

    private Edition getEdition(DbScope scope)
    {
        String edition = new SqlSelector(scope, "SELECT CAST(SERVERPROPERTY('edition') AS NVARCHAR(128))").getObject(String.class);
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
            _decimalDigitsKey = "DECIMAL_DIGITS";
            _nullableKey = "NULLABLE";
            _postionKey = "ORDINAL_POSITION";
            _generatedKey = "IS_GENERATEDCOLUMN";
        }

        @Override
        public int getSqlType() throws SQLException
        {
            // We don't support DATETIMEOFFSET but onprc uses it (???)
            int sqlType = super.getSqlType();
            if ((sqlType == -155 || sqlType == TIMESTAMP_WITH_TIMEZONE) && "datetimeoffset".equalsIgnoreCase(getSqlTypeName()))
                return NVARCHAR;
            return sqlType;
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
    protected @Nullable String getDatabaseMaintenanceSql()
    {
        // RDS doesn't allow executing sp_updatestats, so just skip it for now, part of #35805.
        // In the future, we may want to integrate with something like SQL Maintenance Solution tool,
        // https://ola.hallengren.com/sql-server-index-and-statistics-maintenance.html
        return DbScope.getLabKeyScope().isRds() ? null : "EXEC sp_updatestats";
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
    public boolean hasTriggers(DbSchema schema, String schemaName, String tableName)
    {
        SQLFragment sql = listTriggers(schemaName, tableName);
        return new SqlSelector(schema, sql).exists();
    }

    private SQLFragment listTriggers(@Nullable String schemaName, @Nullable String tableName)
    {
        SQLFragment ret = new SQLFragment("SELECT \n" +
                "     sysobjects.name AS trigger_name,\n" +
                "     s.name AS table_schema,\n" +
                "     OBJECT_NAME(parent_obj) AS table_name,\n" +
                "     OBJECTPROPERTY( id, 'ExecIsUpdateTrigger') AS isupdate,\n" +
                "     OBJECTPROPERTY( id, 'ExecIsDeleteTrigger') AS isdelete,\n" +
                "     OBJECTPROPERTY( id, 'ExecIsInsertTrigger') AS isinsert,\n" +
                "     OBJECTPROPERTY( id, 'ExecIsAfterTrigger') AS isafter,\n" +
                "     OBJECTPROPERTY( id, 'ExecIsInsteadOfTrigger') AS isinsteadof,\n" +
                "     OBJECTPROPERTY(id, 'ExecIsTriggerDisabled') AS [disabled]\n" +
                "FROM sysobjects\n" +
                "INNER JOIN sys.tables t\n" +
                "    ON sysobjects.parent_obj = t.object_id\n" +
                "INNER JOIN sys.schemas s\n" +
                "    ON t.schema_id = s.schema_id\n" +
                "WHERE sysobjects.type = 'TR'\n");
        if (schemaName != null)
            ret.append("AND s.name = ?\n").add(schemaName);
        if (tableName != null)
            ret.append("AND OBJECT_NAME(parent_obj) = ?\n").add(tableName);
        return ret;
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
    public boolean canShowExecutionPlan(ExecutionPlanType type)
    {
        // I don't think SQL Server provides actual times
        return type == ExecutionPlanType.Estimated;
    }

    @Override
    public Collection<String> getQueryExecutionPlan(Connection conn, DbScope scope, SQLFragment sql, ExecutionPlanType type)
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

    @Override
    public boolean isProcedureSupportsInlineResults()
    {
        return true;
    }

    @Override
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
                    traitMap.put(ParamTraits.direction, DatabaseMetaData.procedureColumnOut);
                    traitMap.put(ParamTraits.datatype, Types.INTEGER);
                    parameters.put(StringUtils.substringAfter(rs.getString("COLUMN_NAME"), "@"), new MetadataParameterInfo(traitMap));
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

    @Override
    public String buildProcedureCall(String procSchema, String procName, int paramCount, boolean hasReturn, boolean assignResult, DbScope procScope)
    {
        StringBuilder sb = new StringBuilder();
        if (hasReturn || assignResult)
        {
            sb.append("{");
            sb.append("? = ");
            paramCount--;
        }
        sb.append("CALL ").append(procSchema).append(".").append(procName);
        if (paramCount > 0)
        {
            sb.append("(");
            sb.append(StringUtils.repeat("?", ", ", paramCount));
            sb.append(")");
        }
        if (hasReturn || assignResult)
        {
            sb.append("}");
        }

        return sb.toString();
    }

    @Override
    public void registerParameters(DbScope scope, CallableStatement stmt, Map<String, MetadataParameterInfo> parameters, boolean registerOutputAssignment) throws SQLException
    {
        int index = 1;
        for (Map.Entry<String, MetadataParameterInfo> parameter : parameters.entrySet())
        {
            MetadataParameterInfo paramInfo = parameter.getValue();
            int datatype = paramInfo.getParamTraits().get(ParamTraits.datatype);
            int direction = paramInfo.getParamTraits().get(ParamTraits.direction);

            if (direction != DatabaseMetaData.procedureColumnOut)
            {
                stmt.setObject(index, paramInfo.getParamValue(), datatype);
            }
            if (direction == DatabaseMetaData.procedureColumnInOut || direction == DatabaseMetaData.procedureColumnOut)
            {
                stmt.registerOutParameter(index, datatype);
            }
            index++;
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

    private static final TableResolver TABLE_RESOLVER = new SynonymTableResolver();

    @Override
    public TableResolver getTableResolver()
    {
        return TABLE_RESOLVER;
    }

    @Override
    public boolean canExecuteUpgradeScripts()
    {
        return true;
    }

    // Query INFORMATION_SCHEMA.TABLES directly, since this is 50X faster than jTDS getTables() (which calls sp_tables). Select only the columns we care about. SQL Server always returns NULL for REMARKS.
    private static final String ALL_TABLES_SQL = "SELECT TABLE_NAME, CASE TABLE_TYPE WHEN 'BASE TABLE' THEN 'TABLE' ELSE TABLE_TYPE END AS TABLE_TYPE, NULL AS REMARKS FROM INFORMATION_SCHEMA.TABLES" +
        " WHERE TABLE_CATALOG = ? AND TABLE_SCHEMA LIKE ? ESCAPE '\\'";

    @Override
    public DatabaseMetaData wrapDatabaseMetaData(DatabaseMetaData md, DbScope scope)
    {
        return new DatabaseMetaDataWrapper(md)
        {
            @Override
            public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types)
            {
                if (null == schemaPattern)
                    throw new IllegalStateException("null schemaPattern is not supported");

                SQLFragment sql = new SQLFragment(ALL_TABLES_SQL);
                sql.add(catalog);
                sql.add(schemaPattern); // Note: Our query doesn't support schemaPattern == null because we never pass null

                if (null != tableNamePattern && !"%".equals(tableNamePattern))
                {
                    sql.append(" AND TABLE_NAME LIKE ? ESCAPE '\\'");
                    sql.add(tableNamePattern);
                }

                // Specification for getTables() states that results are ordered
                sql.append(" ORDER BY TABLE_TYPE, TABLE_NAME");

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
        BEGIN(TokenType.BEGIN),
        CASE(TokenType.BEGIN),
        END(TokenType.END),
        COMMIT(TokenType.END),
        OPEN_PAREN(TokenType.BEGIN)
        {
            @Override
            public String toString()
            {
                return "(";
            }
        },
        CLOSE_PAREN(TokenType.END);

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
            OPEN_PAREN.setTerminatingTokens(EnumSet.copyOf(Collections.singletonList(CLOSE_PAREN)));
        }

        static Token getToken(String tokenString)
        {
            if ("(".equals(tokenString))
                return OPEN_PAREN;
            else if (")".equals(tokenString))
                return CLOSE_PAREN;
            else
                return Token.valueOf(tokenString.toUpperCase());
        }
    }

    private final static CaseInsensitiveHashSet BEGIN_RETURNS = new CaseInsensitiveHashSet("BEGIN", "TABLE");
    private final static CaseInsensitiveHashSet BEGIN_END = new CaseInsensitiveHashSet("BEGIN", "CASE", "END", "COMMIT");
    private final static CaseInsensitiveHashSet PARENS = new CaseInsensitiveHashSet("(", ")");

    @Override
    public Collection<String> getScriptWarnings(String name, String sql)
    {
        sql = new SqlScanner(sql).stripComments().toString();
        sql = sql.replaceAll("\\[.+?]", "");  // Remove all bracketed strings -- these might contain keywords like BEGIN or END

        // At the moment, we're only checking for stored procedure definitions that aren't followed immediately by a GO
        // statement. These will cause major problems if they are missed during script consolidation.

        // Dumb little parser that, within stored procedure definitions, matches up each BEGIN with COMMIT/END.
        String[] tokens = sql
            .replace(";", "")     // Remove semicolons
            .replace("(", " ( ")  // Ensure whitespace around parentheses so they are treated as individual tokens
            .replace(")", " ) ")
            .split("\\s+|,");

        int idx = 0;

        while (-1 != (idx = skipToCreateProcedure(tokens, idx)))
        {
            idx += 2;
            String procedureName = tokens[idx];
            idx = skipToToken(tokens, idx, BEGIN_RETURNS);

            if (-1 == idx)
                return Collections.singleton("Stored procedure definition " + procedureName + " lacks both BEGIN and TABLE keywords!");

            Token firstToken;
            CaseInsensitiveHashSet skipToSet;

            if ("BEGIN".equalsIgnoreCase(tokens[idx]))
            {
                // BEGIN ... END
                firstToken = Token.BEGIN;
                skipToSet = BEGIN_END;
            }
            else
            {
                // Currently supporting two RETURNS TABLE options:
                // - RETURNS TABLE AS RETURN ( ... )
                // - RETURNS @returnTableName TABLE ( ... ) AS BEGIN ... END
                if (!"RETURNS".equalsIgnoreCase(tokens[idx - 1]) && !"RETURNS".equalsIgnoreCase(tokens[idx - 2]))
                    return Collections.singleton("Stored procedure definition " + procedureName + " doesn't seem to have a RETURNS keyword in the right spot");

                // Skip optional TABLE definition after TABLE AS RETURN
                if (tokens[idx + 1].equals("("))
                {
                    int open = 1;
                    idx = idx + 2;
                    while (open > 0 && idx < tokens.length)
                    {
                        String token = tokens[idx];
                        if (token.equals("("))
                            open++;
                        else if (token.equals(")"))
                            open--;
                        idx++;
                    }

                    if (idx == tokens.length)
                        return Collections.singleton("Stored procedure definition " + procedureName + " TABLE declaration doesn't have a closing )");

                    idx = assertNextTokens(tokens, idx, "AS BEGIN");
                }
                else
                {
                    idx = assertNextTokens(tokens, idx, "TABLE AS RETURN (");
                }
            }

            if ("BEGIN".equalsIgnoreCase(tokens[idx]))
            {
                // BEGIN ... END
                firstToken = Token.BEGIN;
                skipToSet = BEGIN_END;
            }
            else if ("(".equals(tokens[idx]))
            {
                // ( ... )
                firstToken = Token.OPEN_PAREN;
                skipToSet = PARENS;
            }
            else
            {
                return Collections.singleton("Stored procedure definition " + procedureName + " doesn't have an opening BEGIN or (");
            }

            Stack<Token> stack = new Stack<>();
            stack.push(firstToken);

            while (!stack.isEmpty())
            {
                idx = skipToToken(tokens, idx + 1, skipToSet);

                if (-1 == idx)
                    return Collections.singleton("Stored procedure definition " + procedureName + " seems to be missing a terminating token for " + firstToken);

                Token token = Token.getToken(tokens[idx]);

                if (token.getType() == TokenType.BEGIN)
                {
                    // BEGIN, CASE, or (
                    stack.push(token);
                }
                else
                {
                    // END, COMMIT, or )
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

    private int assertNextTokens(String[] tokens, int idx, String expected)
    {
        for (String token : expected.split(" "))
            if (!token.equalsIgnoreCase(tokens[idx++]))
                throw new IllegalArgumentException("Unexpected tokens before stored procedure definition");

        return idx - 1; // Stay on the last token
    }

    private final static CaseInsensitiveHashSet CREATE = new CaseInsensitiveHashSet("CREATE", "ALTER");

    private int skipToCreateProcedure(String[] tokens, int idx)
    {
        while (true)
        {
            int i = skipToToken(tokens, idx, CREATE);

            if (-1 == i)
                return -1;

            idx = i + 1;

            String token = tokens[idx];

            if (token.equalsIgnoreCase("PROCEDURE") || token.equalsIgnoreCase("FUNCTION"))
                return i;
        }
    }

    private int skipToToken(String[] tokens, int start, CaseInsensitiveHashSet desired)
    {
        for (int i = start; i < tokens.length; i++)
            if (desired.contains(tokens[i]))
                return i;

        return -1;
    }

    @Override
    public String encodeLikeOpSearchString(String search)
    {
        return search.replaceAll("_", "[_]").replaceAll("%", "[%]");
    }

    @Override
    public boolean isLabKeyWithSupported()
    {
        return true;
    }

    @Override
    public boolean isRds(DbScope scope)
    {
        // See https://stackoverflow.com/questions/35915024/amazon-rds-sql-server-how-to-detect-if-it-is-rds

        boolean rds = false;

        LOG.debug("Attempting to detect if " + scope.getDatabaseName() + " is an RDS SQL Server database");
        LOG.debug("Checking for a database named \"rdsadmin\"");
        Integer id = new SqlSelector(scope, "SELECT DB_ID('rdsadmin')").getObject(Integer.class);

        if (null == id)
        {
            LOG.debug("\"rdsadmin\" database is not present - this database is not RDS");
        }
        else
        {
            LOG.debug("\"rdsadmin\" database is present - this database may be RDS");
            LOG.debug("Now attempting to access model.sys.database_files, which should be disallowed on RDS");

            try
            {
                // Suppress exception logging -- we expect this to fail in the RDS case
                new SqlSelector(scope, "SELECT COUNT(*) FROM model.sys.database_files").setLogLevel(Level.OFF).getObject(Integer.class);
                LOG.debug("Successfully accessed model.sys.database_files - this database is not RDS");
            }
            catch (Exception e)
            {
                LOG.debug("Failed to access model.sys.database_files (\"" + e + "\") - determined that this database is RDS");
                rds = true;
            }
        }

        return rds;
    }

    @Override
    protected DialectStringHandler createStringHandler()
    {
        return new MicrosoftSqlServerStringHandler();
    }

    @Override
    public @Nullable String getApplicationNameParameter()
    {
        return "applicationName";
    }

    @Override
    public @Nullable String getApplicationNameSql()
    {
        return "SELECT APP_NAME()";
    }

    @Override
    public @Nullable String getDefaultApplicationName()
    {
        return "Microsoft JDBC Driver for SQL Server";
    }

    @Override
    public @NotNull String getApplicationConnectionsSql()
    {
        return "SELECT spid, loginame, hostname, net_address, last_batch, status, program_name, cmd FROM sys.sysprocesses WHERE spid <> @@SPID AND DB_NAME(dbid) = ? AND program_name = ?";
    }
}

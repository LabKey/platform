/*
 * Copyright (c) 2010-2018 LabKey Corporation
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
package org.labkey.bigiron.mysql;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.ColumnMetaDataReader;
import org.labkey.api.data.dialect.JdbcHelper;
import org.labkey.api.data.dialect.JdbcMetaDataLocator;
import org.labkey.api.data.dialect.LimitRowsSqlGenerator;
import org.labkey.api.data.dialect.LimitRowsSqlGenerator.LimitRowsCustomizer;
import org.labkey.api.data.dialect.LimitRowsSqlGenerator.StandardLimitRowsCustomizer;
import org.labkey.api.data.dialect.PkMetaDataReader;
import org.labkey.api.data.dialect.SimpleSqlDialect;
import org.labkey.api.data.dialect.StandardJdbcHelper;
import org.labkey.api.data.dialect.StandardJdbcMetaDataLocator;
import org.labkey.api.data.dialect.StandardTableResolver;
import org.labkey.api.data.dialect.TableResolver;
import org.labkey.api.util.PageFlowUtil;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class MySqlDialect extends SimpleSqlDialect
{
    @Override
    protected @NotNull Set<String> getReservedWords()
    {
        return Sets.newCaseInsensitiveHashSet(new CsvSet(
            "accessible, add, all, alter, analyze, and, as, asc, asensitive, before, between, bigint, binary, blob, " +
            "both, by, call, cascade, case, change, char, character, check, collate, column, condition, constraint, " +
            "continue, convert, create, cross, current_date, current_time, current_timestamp, current_user, cursor, " +
            "database, databases, day_hour, day_microsecond, day_minute, day_second, dec, decimal, declare, default, " +
            "delayed, delete, desc, describe, deterministic, distinct, distinctrow, div, double, drop, dual, each, " +
            "else, elseif, enclosed, end-exec, escaped, exists, exit, explain, false, fetch, float, float4, float8, " +
            "for, force, foreign, from, fulltext, grant, group, having, high_priority, hour_microsecond, hour_minute, " +
            "hour_second, if, ignore, in, index, infile, inner, inout, insensitive, insert, int, int1, int2, int3, " +
            "int4, int8, integer, interval, into, is, iterate, join, key, keys, kill, leading, leave, left, like, " +
            "limit, linear, lines, load, localtime, localtimestamp, lock, long, longblob, longtext, loop, low_priority, " +
            "master_ssl_verify_server_cert, match, maxvalue, mediumblob, mediumint, mediumtext, middleint, " +
            "minute_microsecond, minute_second, mod, modifies, natural, no_write_to_binlog, not, null, numeric, on, " +
            "optimize, option, optionally, or, order, out, outer, outfile, precision, primary, procedure, purge, range, " +
            "read, read_write, reads, real, references, regexp, release, rename, repeat, replace, require, resignal, " +
            "restrict, return, revoke, right, rlike, schema, schemas, second_microsecond, select, sensitive, separator, " +
            "set, show, signal, smallint, spatial, specific, sql, sql_big_result, sql_calc_found_rows, sql_small_result, " +
            "sqlexception, sqlstate, sqlwarning, ssl, starting, straight_join, table, terminated, then, tinyblob, " +
            "tinyint, tinytext, to, trailing, trigger, true, undo, union, unique, unlock, unsigned, update, usage, use, " +
            "using, utc_date, utc_time, utc_timestamp, values, varbinary, varchar, varcharacter, varying, when, where, " +
            "while, with, write, xor, year_month, zerofill"
        ));
    }

    @Override
    protected void initializeJdbcTableTypeMap(Map<String, DatabaseTableType> map)
    {
        super.initializeJdbcTableTypeMap(map);
        map.put("SYSTEM TABLE", DatabaseTableType.TABLE);
    }

    @Override
    // MySQL doesn't like executing multiple statements at once, so break it into separate calls.
    protected boolean isKeyword(SqlExecutor executor, String candidate)
    {
        String tableName = getTempTablePrefix() + candidate;

        try
        {
            executor.execute("SELECT " + candidate + " FROM (SELECT 1 AS " + candidate + ") x ORDER BY " + candidate);
            executor.execute("CREATE TEMPORARY TABLE mysql." + tableName + " (" + candidate + " VARCHAR(50))");
            executor.execute("DROP TEMPORARY TABLE mysql." + tableName);

            return false;
        }
        catch (Exception e)
        {
            return true;
        }
    }

    @Override
    public String getProductName()
    {
        return "MySQL";
    }

    @Override
    public JdbcHelper getJdbcHelper()
    {
        return new StandardJdbcHelper("jdbc:mysql:");
    }

    @Override
    protected String getSIDQuery()
    {
        return "SELECT connection_id();";
    }

    private static final TableResolver TABLE_RESOLVER = new StandardTableResolver() {
        @Override
        public JdbcMetaDataLocator getJdbcMetaDataLocator(DbScope scope, String schemaName, String schemaNamePattern, String tableName, String tableNamePattern) throws SQLException
        {
            // MySQL treats catalogs as schemas... i.e., getSchemaName() needs to return null and getCatalogName() needs to return the schema name
            return new StandardJdbcMetaDataLocator(scope, schemaName, schemaNamePattern, tableName, tableNamePattern)
            {
                @Override
                public String getSchemaName()
                {
                    return null;
                }

                @Override
                public String getCatalogName()
                {
                    return super.getSchemaName();
                }
            };
        }
    };

    @Override
    public TableResolver getTableResolver()
    {
        return TABLE_RESOLVER;
    }

    @Override
    public ColumnMetaDataReader getColumnMetaDataReader(ResultSet rsCols, TableInfo table)
    {
        return new MySqlColumnMetaDataReader(rsCols);
    }

    private static class MySqlColumnMetaDataReader extends ColumnMetaDataReader
    {
        private MySqlColumnMetaDataReader(ResultSet rsCols)
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
        public boolean isAutoIncrement() throws SQLException
        {
            return getSqlTypeName().equalsIgnoreCase("int identity");
        }
    }


    @Override
    public PkMetaDataReader getPkMetaDataReader(ResultSet rs)
    {
        return new PkMetaDataReader(rs, "COLUMN_NAME", "KEY_SEQ");
    }

    private static final LimitRowsCustomizer CUSTOMIZER = new StandardLimitRowsCustomizer(false);

    @Override
    public SQLFragment limitRows(SQLFragment frag, int maxRows)
    {
        return LimitRowsSqlGenerator.limitRows(frag, maxRows, 0, CUSTOMIZER);
    }

    @Override
    public SQLFragment limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, String order, String groupBy, int maxRows, long offset)
    {
        return LimitRowsSqlGenerator.limitRows(select, from, filter, order, groupBy, maxRows, offset, CUSTOMIZER);
    }

    @Override
    public boolean supportsOffset()
    {
        return true;
    }

    @Override
    public String concatenate(String... args)
    {
        return "CONCAT(" + StringUtils.join(args, ", ") + ")";
    }


    @Override
    public SQLFragment concatenate(SQLFragment... args)
    {
        SQLFragment ret = new SQLFragment();
        String op = "CONCAT(";
        for (SQLFragment arg : args)
        {
            ret.append(op).append(arg);
            op = ", ";
        }
        ret.append(")");
        return ret;
    }

    private static final Set<String> SYSTEM_SCHEMAS = PageFlowUtil.set("information_schema", "performance_schema", "mysql");

    @Override
    public boolean isSystemSchema(String schemaName)
    {
        return SYSTEM_SCHEMAS.contains(schemaName);
    }

    // TODO: MySQL has a lot of settings we should check (ANSI_QUOTES, NO_BACKSLASH_ESCAPES, etc.)
    // Should have a custom MySqlStringHandler

    @Override
    // Escape quotes and quote the identifier -- standard MySQL quote character is back tick (`)
    public String quoteIdentifier(String id)
    {
        return "`" + id.replaceAll("`", "``") + "`";
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
    public boolean allowSortOnSubqueryWithoutLimit()
    {
        return true;
    }

    @Override
    public boolean supportsNativeGreatestAndLeast()
    {
        return true;
    }

    @Override
    public boolean supportsRoundDouble()
    {
        return true;
    }

    @Override
    public SQLFragment wrapExistsExpression(SQLFragment existsSQL)
    {
        return existsSQL;
    }

    @Override
    public boolean canShowExecutionPlan(ExecutionPlanType type)
    {
        // Old MySQL versions only support estimated execution plans
        return type == ExecutionPlanType.Estimated;
    }

    @Override
    protected Collection<String> getQueryExecutionPlan(Connection conn, DbScope scope, SQLFragment sql, ExecutionPlanType type)
    {
        SQLFragment copy = new SQLFragment(sql);
        copy.insert(0, getExplainPrefix(type));

        return new SqlSelector(scope, conn, copy).getCollection(String.class);
    }

    protected String getExplainPrefix(ExecutionPlanType type)
    {
        // Note: This is not very useful because the query returns multiple columns, most of which we ignore. But
        // MySQL 5.7 and before don't support FORMAT = TREE. We could concatenate the columns ourselves, but it's
        // not worth the bother for unsupported versions of MySQL.
        return "EXPLAIN ";
    }

    @Override
    public @Nullable String getApplicationNameParameter()
    {
        return "ApplicationName";
    }

    @Override
    public ResultSet getSchemas(DatabaseMetaData dbmd, String databaseName) throws SQLException
    {
        return dbmd.getCatalogs();
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
}

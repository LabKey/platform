/*
 * Copyright (c) 2011-2019 LabKey Corporation
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

package org.labkey.bigiron.oracle;

import oracle.sql.TIMESTAMP;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ConnectionPool;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ResultSetWrapper;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.ColumnMetaDataReader;
import org.labkey.api.data.dialect.JdbcHelper;
import org.labkey.api.data.dialect.JdbcMetaDataLocator;
import org.labkey.api.data.dialect.LimitRowsSqlGenerator;
import org.labkey.api.data.dialect.PkMetaDataReader;
import org.labkey.api.data.dialect.SimpleSqlDialect;
import org.labkey.api.data.dialect.StandardJdbcHelper;
import org.labkey.api.data.dialect.StandardJdbcMetaDataLocator;
import org.labkey.api.data.dialect.StandardTableResolver;
import org.labkey.api.data.dialect.StatementWrapper;
import org.labkey.api.data.dialect.TableResolver;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

abstract class OracleDialect extends SimpleSqlDialect
{
    // To work around #33481, for each Oracle scope, create a special connection pool that invalidates connections before they hit the max usage limit
    private static final Map<DbScope, ConnectionPool> META_DATA_CONNECTION_POOLS = new ConcurrentHashMap<>();
    private static final TableResolver TABLE_RESOLVER = new StandardTableResolver() {
        @Override
        public JdbcMetaDataLocator getJdbcMetaDataLocator(DbScope scope, String schemaName, String schemaNamePattern, String tableName, String tableNamePattern) throws SQLException
        {
            return new StandardJdbcMetaDataLocator(scope, schemaName, schemaNamePattern, tableName, tableNamePattern)
            {
                @Override
                public Connection getConnection()
                {
                    ConnectionPool pool = META_DATA_CONNECTION_POOLS.computeIfAbsent(scope, OracleMetaDataConnectionPool::new);
                    try
                    {
                        return pool.getConnection();
                    }
                    catch (SQLException e)
                    {
                        throw new RuntimeSQLException(e);
                    }
                }
            };
        }
    };

    private final Set<String> _sortableTypeNames = new HashSet<>();

    public OracleDialect()
    {
        _sortableTypeNames.addAll(getSqlTypeNameMap().keySet());
        _sortableTypeNames.remove("BLOB"); // Not sortable
        _sortableTypeNames.remove("CLOB"); // Not sortable
    }

    @Override
    public TableResolver getTableResolver()
    {
        return TABLE_RESOLVER;
    }

    @Override
    public String getProductName()
    {
        return "Oracle";
    }

    @Override
    public ColumnMetaDataReader getColumnMetaDataReader(ResultSet rsCols, TableInfo table)
    {
        return new OracleColumnMetaDataReader(rsCols);
    }

    @Override
    public JdbcHelper getJdbcHelper()
    {
        return new StandardJdbcHelper("jdbc:oracle:thin:");
    }

    @Override
    public StatementWrapper getStatementWrapper(ConnectionWrapper conn, Statement stmt)
    {
        return new OracleStatementWrapper(conn, stmt);
    }

    @Override
    public StatementWrapper getStatementWrapper(ConnectionWrapper conn, Statement stmt, String sql)
    {
        return new OracleStatementWrapper(conn, stmt, sql);
    }

    @Override
    public void testDialectKeywords(SqlExecutor executor)
    {
        // TODO: Create queries to test keywords on Oracle
        // Don't test keywords on Oracle
    }

    @Override
    public int getIdentifierMaxLength()
    {
        // the limit for Oracle is 30, but we reserve 3 characters for
        // appending qualifiers ("_", and a two digit alias counter) to create aliases
        return 27;
    }

    @Override
    public boolean canCheckIndices(TableInfo ti)
    {
        // The Oracle JDBC driver throws a SQLException when calling DatabaseMetaData.getIndexInfo(), so need to avoid
        // calling it
//        return ti.getTableType() == DatabaseTableType.TABLE && !isExternalTable(ti) ;
        return false; //Issue 26354: ORA-00942: table or view does not exist
    }

//    private boolean isExternalTable(TableInfo ti)
//    {
//        String owner = ti.getSchema().getName();
//        SQLFragment sqlFragment = new SQLFragment();
//        sqlFragment.append("select * from all_external_tables where owner = ? and table_name = ?");
//        sqlFragment.add(owner);
//        sqlFragment.add(ti.getName());
//
//        SqlSelector sqlSelector = new SqlSelector(ti.getSchema(), sqlFragment);
//        return sqlSelector.getRowCount() > 0;
//    }

    @Override
    protected Set<String> getJdbcKeywords(SqlExecutor executor) throws SQLException, IOException
    {
        // Remove goofy "keyword" that Oracle includes in JDBC call
        Set<String> keywords = super.getJdbcKeywords(executor);
        keywords.remove("all_PL_SQL_reserved_ words");
        return keywords;
    }

    @Override
    protected String getSIDQuery()
    {
        return "select userenv('SESSIONID') from dual";
    }

    @Override
    public PkMetaDataReader getPkMetaDataReader(ResultSet rs)
    {
        return new PkMetaDataReader(rs, "COLUMN_NAME", "KEY_SEQ");
    }

    @Override
    public String concatenate(String... args)
    {
        return StringUtils.join(args, " || ");
    }

    @Override
    public SQLFragment sqlLocate(SQLFragment littleString, SQLFragment bigString)
    {
        return new SQLFragment("instr(").append(bigString).append(", ").append(littleString).append(")");
    }

    @Override
    public SQLFragment sqlLocate(SQLFragment littleString, SQLFragment bigString, SQLFragment startIndex)
    {
        return new SQLFragment("instr(").append(bigString).append(", ").append(littleString).append(", ").append(startIndex).append(")");
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
    public boolean supportsOffset()
    {
        return true;
    }

    @Override
    public SQLFragment limitRows(SQLFragment frag, int maxRows)
    {
        if (maxRows == Table.ALL_ROWS)
            return frag;

        // Use rownum approach here since row_number() (used in _limitRows() below) requires an explicit ORDER BY
        // parameter which this method doesn't provide.
        SQLFragment sql = new SQLFragment("SELECT * FROM (\n");
        sql.append(frag);
        sql.append("\n)\n");
        sql.append("WHERE rownum <= ").appendValue(maxRows);

        return sql;
    }

    @Override
    public SQLFragment limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, String order, String groupBy, int maxRows, long offset)
    {
        if (select == null)
            throw new IllegalArgumentException("select");
        if (from == null)
            throw new IllegalArgumentException("from");

        if (maxRows == Table.ALL_ROWS && offset == Table.NO_OFFSET)
        {
            return LimitRowsSqlGenerator.appendFromFilterOrderAndGroupByNoValidation(select, from, filter, order, groupBy);
        }
        else
        {
            if (StringUtils.isBlank(order))
                throw new IllegalArgumentException("ERROR: ORDER BY clause required to use maxRows or offset");

            return _limitRows(select, from, filter, order, groupBy, maxRows, offset);
        }
    }

    // This is very similar to MicrosoftSqlServer2008R2Dialect._limitRows() except that it requires an extra subselect
    // plus an alias.
    //
    // Example SQL: SELECT * FROM (SELECT x.*, row_number() OVER (ORDER BY ALTERNATE_TYPE_ID) rn__ FROM (SELECT * FROM granite.alternate_type) x) WHERE rn__ > 100 AND rn__ <= 201
    //
    protected SQLFragment _limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, String order, String groupBy, int maxRows, long offset)
    {
        SQLFragment sql = new SQLFragment("SELECT * FROM (SELECT x.*, row_number() OVER (");
        sql.append(order);
        sql.append(") rn__ FROM (\n");
        sql.append(select);
        sql.append(from);
        if (filter != null) sql.append("\n").append(filter);
        if (groupBy != null) sql.append("\n").append(groupBy);
        sql.append("\n) x\n)");
        sql.append("WHERE rn__ > ").appendValue(offset);

        if (maxRows != Table.ALL_ROWS)
            sql.append(" AND rn__ <= ").appendValue(maxRows + offset);

        return sql;
    }

    /*
        This rownum approach mostly works, but the ORDER BY doesn't seem to be respected 100% by the rownum.
     */
//    private SQLFragment _limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, String order, String groupBy, int maxRows, long offset)
//    {
//        SQLFragment sql = new SQLFragment();
//
//        sql.append("SELECT * FROM (\n");
//        int aliasOffset = 7;
//        if (select.getSQL().length() >= 16 && select.getSQL().substring(0, 16).equalsIgnoreCase("SELECT DISTINCT"))
//            aliasOffset = 16;
//        //x seems to be the alias assigned to the result set, so hardcoding x. before the * (otherwise rownum doesnt work)
//        select.insert(aliasOffset, " x.");
//        select.append(", rownum AS rn__ ");
//        sql.append(select);
//        sql.append(from);
//        if (filter != null) sql.append("\n").append(filter);
//        if (groupBy != null) sql.append("\n").append(groupBy);
//        if (order != null) sql.append("\n").append(order);
//        sql.append("\n)\n");
//        sql.append("WHERE rn__ > ").append(offset);
//        sql.append(" AND rn__ <= ").append(maxRows + offset);
//
//        return sql;
//    }

    @Override
    public boolean allowSortOnSubqueryWithoutLimit()
    {
        return true;
    }

    @Override
    public boolean isOracle()
    {
        return true;
    }

    @Override
    public boolean supportsRoundDouble()
    {
        return true;
    }

    @Override
    public boolean supportsNativeGreatestAndLeast()
    {
        return true;
    }

    // Like SQL Server, EXISTS on Oracle can only be used in a WHERE or CASE. In addition, FROM <table> is required in every SELECT statement
    // on Oracle, so add FROM DUAL, the Oracle dummy table: http://docs.oracle.com/cd/E11882_01/server.112/e41084/queries009.htm#SQLRF20036
    @Override
    public SQLFragment wrapExistsExpression(SQLFragment existsSQL)
    {
        // Note: "FROM DUAL" will only work when using EXISTS to return TRUE/FALSE, but Selector.exists() is the only code
        // path that should be invoking this method on Oracle.
        return new SQLFragment("CASE WHEN\n").append(existsSQL).append("\nTHEN 1 ELSE 0 END FROM DUAL");
    }

    @Override
    protected void addSqlTypeNames(Map<String, Integer> sqlTypeNameMap)
    {
        sqlTypeNameMap.put("RAW", Types.VARBINARY);
        sqlTypeNameMap.put("ROWID", Types.OTHER);
        sqlTypeNameMap.put("VARCHAR2", Types.VARCHAR);
        sqlTypeNameMap.put("NUMBER", Types.DECIMAL);
    }

    @Override
    public boolean isSortableDataType(String sqlDataTypeName)
    {
        return _sortableTypeNames.contains(sqlDataTypeName);
    }

    @Override
    public boolean canShowExecutionPlan(ExecutionPlanType type)
    {
        // Just the estimated plan for now
        return type == ExecutionPlanType.Estimated;
    }

    @Override
    protected Collection<String> getQueryExecutionPlan(Connection conn, DbScope scope, SQLFragment sql, ExecutionPlanType type)
    {
        SQLFragment copy = new SQLFragment(sql);
        copy.insert(0, "EXPLAIN PLAN FOR ");
        new SqlExecutor(scope, conn).execute(copy);

        return new SqlSelector(scope, conn, "SELECT plan_table_output FROM table(dbms_xplan.display('plan_table',null,'all'))").getCollection(String.class);
    }

    @Override
    public @NotNull String getDefaultSchemasToExcludeFromTesting()
    {
        return "SYS";
    }

    @Override
    public @NotNull String getDefaultTablesToExcludeFromTesting()
    {
        return "SYS_IOT_OVER_*";
    }

    @Override
    public @Nullable String getApplicationNameParameter()
    {
        return "v$session.program";
    }

    private static class OracleColumnMetaDataReader extends ColumnMetaDataReader
    {
        private OracleColumnMetaDataReader(ResultSet rsCols)
        {
            super(rsCols);

            _nameKey = "COLUMN_NAME";
            _sqlTypeKey = "DATA_TYPE";
            _sqlTypeNameKey = "TYPE_NAME";
            _scaleKey = "COLUMN_SIZE";
            _decimalDigitsKey = "DECIMAL_DIGITS";
            _nullableKey = "NULLABLE";
            _postionKey = "ORDINAL_POSITION";
        }

        // Since there is no autoincrement field in oracle, I have set this to false
        // Auto incrementing in Oracle is usually done through combination of a sequence and a before insert trigger

        @Override
        public boolean isAutoIncrement()
        {
            return false;
        }

        @Override
        public int getSqlType() throws SQLException
        {
            int sqlType = super.getSqlType();

            // Old JDBC driver gave us DECIMAL; assert that we don't anymore.
            assert sqlType != Types.DECIMAL;

            // Oracle claims all numbers are DECIMAL... convert to INTEGER if decimal digits <= 0 (sometimes -127 for numeric keys)
            if (Types.NUMERIC == sqlType && _rsCols.getInt("DECIMAL_DIGITS") <= 0)
                return Types.INTEGER;

            return sqlType;
        }

        @Override
        public String getSqlTypeName() throws SQLException
        {
            String typeName = super.getSqlTypeName();

            if ("NUMBER".equals(typeName) && _rsCols.getInt("DECIMAL_DIGITS") <= 0)
                typeName = "INTEGER";

            return typeName;
        }
    }


    private static class OracleStatementWrapper extends StatementWrapper
    {
        private OracleStatementWrapper(ConnectionWrapper conn, Statement stmt)
        {
            super(conn, stmt);
        }

        private OracleStatementWrapper(ConnectionWrapper conn, Statement stmt, String sql)
        {
            super(conn, stmt, sql);
        }

        @Override
        public ResultSet getResultSet() throws SQLException
        {
            return new OracleResultSetWrapper(super.getResultSet());
        }

        @Override
        public ResultSet executeQuery() throws SQLException
        {
            return new OracleResultSetWrapper(super.executeQuery());
        }

        @Override
        public ResultSet executeQuery(String sql) throws SQLException
        {
            return new OracleResultSetWrapper(super.executeQuery(sql));
        }
    }


    private static class OracleResultSetWrapper extends ResultSetWrapper
    {
        OracleResultSetWrapper(ResultSet rs)
        {
            super(rs);
        }

        private Object translateValue(int i, Object value) throws SQLException
        {
            ResultSetMetaData rsmd = resultset.getMetaData();

            // Oracle always returns BigDecimal.  Use scale to determine if this is really an Integer.
            if (Types.NUMERIC == rsmd.getColumnType(i) && 0 == rsmd.getScale(i) && value instanceof BigDecimal)
                return ((BigDecimal)value).intValue();

            /* Secure Issue 27345: Conversion failed when converting from a character string to uniqueidentifier */
            // As per Oracle Docs - The RAW data is intended for binary data or byte strings. It is not
            // interpreted/converted when moving data between different systems by Oracle Database.
            else if (("RAW").equalsIgnoreCase(rsmd.getColumnTypeName(i)))
            {
                byte[] val = (byte[]) value;

                //Handle GUID. A GUID in oracle is a 32 character representation of a 16 byte RAW value
                if(val != null && val.length == 16 && rsmd.getColumnDisplaySize(i) == 32)
                {
                    String hexVal = String.valueOf(Hex.encodeHex(val)); //get the 32 character representation

                    //format with dashes : 8 chars - 4 chars - 4 chars - 4 chars - 12 chars
                    String guidVal = hexVal.substring(0, 8) + "-" +
                            hexVal.substring(8, 12) + "-" +
                            hexVal.substring(12, 16) + "-" +
                            hexVal.substring(16, 20) + "-" +
                            hexVal.substring(20);

                    return guidVal.toUpperCase();
                }
                return val;
            }
            else if (value instanceof TIMESTAMP oracleTimestamp)
            {
                return oracleTimestamp.timestampValue();
            }
            else
            {
                return value;
            }
        }

        @Override
        public Object getObject(int i) throws SQLException
        {
            return translateValue(i, super.getObject(i));
        }

        @Override
        public Object getObject(int i, Map<String, Class<?>> map) throws SQLException
        {
            return translateValue(i, super.getObject(i, map));
        }

        @Override
        public Object getObject(String s) throws SQLException
        {
            return translateValue(findColumn(s), super.getObject(s));
        }

        @Override
        public Object getObject(String s, Map<String, Class<?>> map) throws SQLException
        {
            return translateValue(findColumn(s), super.getObject(s, map));
        }
    }
}

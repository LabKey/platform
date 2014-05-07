/*
 * Copyright (c) 2011-2014 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.ResultSetWrapper;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.ColumnMetaDataReader;
import org.labkey.api.data.dialect.JdbcHelper;
import org.labkey.api.data.dialect.PkMetaDataReader;
import org.labkey.api.data.dialect.SimpleSqlDialect;
import org.labkey.api.data.dialect.StandardJdbcHelper;
import org.labkey.api.data.dialect.StatementWrapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Map;
import java.util.Set;


/**
 * User: trent
 * Date: 6/10/11
 * Time: 3:40 PM
 */
public abstract class OracleDialect extends SimpleSqlDialect
{
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
    protected Set<String> getJdbcKeywords(SqlExecutor executor) throws SQLException, IOException
    {
        // Remove goofy "keyword" that Orcale includes in JDBC call
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


    private SQLFragment limitRows(SQLFragment frag, int rowCount, long offset)
    {
        // TODO: Oracle doesn't support offset and limit clauses. Implement by using rownum >= offset or some similar trickery
        // TODO: Below functionality has been taken from PostgreSql83Dialect
        /*if (rowCount != Table.ALL_ROWS)
        {
            frag.append("\nLIMIT ");
            frag.append(Integer.toString(Table.NO_ROWS == rowCount ? 0 : rowCount));

            if (offset > 0)
            {
                frag.append(" OFFSET ");
                frag.append(Long.toString(offset));
            }
        } */
        return frag;
    }

    @Override
    public SQLFragment limitRows(SQLFragment frag, int maxRows)
    {
        return limitRows(frag, maxRows, 0);
    }

    @Override
    public SQLFragment limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, String order, String groupBy, int maxRows, long offset)
    {
        if (select == null)
            throw new IllegalArgumentException("select");
        if (from == null)
            throw new IllegalArgumentException("from");

        if (maxRows == Table.ALL_ROWS || maxRows == Table.NO_ROWS || (maxRows > 0 && offset == 0))
        {
            SQLFragment sql = new SQLFragment();
            sql.append(select);
            sql.append("\n").append(from);

            if (filter != null) sql.append("\n").append(filter);
            if (groupBy != null) sql.append("\n").append(groupBy);
            if (order != null) sql.append("\n").append(order);
            return sql;
        }
        else
        {
            return _limitRows(select, from, filter, order, groupBy, maxRows, offset);
        }
    }

    /* Construct the query by adding rownum as one of the columns, defined as _row_num. (I didn't use camel case as to follow oracles naming conventions)

       Tom Kyte discusses alternative techniques: http://www.oracle.com/technetwork/issue-archive/2007/07-jan/o17asktom-093877.html

       Also, someone on the labkey forum suggested: http://stackoverflow.com/questions/241622/paging-with-oracle

     */
    private SQLFragment _limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, String order, String groupBy, int rowCount, long offset)
    {
        SQLFragment sql = new SQLFragment();

        sql.append("SELECT * FROM (\n");
        // sql.append(select).append(", rownum row_num").append("\n");
        //sql.append(select);
        int aliasOffset = 7;
        if (select.getSQL().length() >= 16 && select.getSQL().substring(0, 16).equalsIgnoreCase("SELECT DISTINCT"))
            aliasOffset = 16;
        //x seems to be the alias assigned to the result set, so hardcoding x. before the * (otherwise rownum doesnt work)
        select.insert(aliasOffset, " x.");
        // The MSSQL Server Dialect uses _RowNum - but starting with _ is not supported in Oracle, so left it as default
        // rownum and to avoid duplicate col's (as rownum is a reserved word, so it should be possible to add to the query)
        select.append(", rownum ");
        sql.append(select);
        sql.append(from);
        if (filter != null) sql.append("\n").append(filter);
        if (groupBy != null) sql.append("\n").append(groupBy);
        if (order != null) sql.append("\n").append(order);
        sql.append("\n)\n");
        sql.append("WHERE rownum > ").append(offset);
        sql.append(" AND rownum <= ").append(rowCount + offset);

        return sql;
    }


    @Override
    public boolean allowSortOnSubqueryWithoutLimit()
    {
        return true;
    }

    public boolean isOracle()
    {
        return true;
    }

    @Override
    public boolean supportsRoundDouble()
    {
        return true;
    }

    private class OracleColumnMetaDataReader extends ColumnMetaDataReader
    {
        private OracleColumnMetaDataReader(ResultSet rsCols)
        {
            super(rsCols);

            _nameKey = "COLUMN_NAME";
            _sqlTypeKey = "DATA_TYPE";
            _sqlTypeNameKey = "TYPE_NAME";
            _scaleKey = "COLUMN_SIZE";
            _nullableKey = "NULLABLE";
            _postionKey = "ORDINAL_POSITION";
        }

        // Since there is no autoincrement field in oracle, I have set this to false
        // Auto incrementing in Oracle is usually done through combination of a sequence and a before insert trigger

        public boolean isAutoIncrement() throws SQLException
        {
            return false;
        }

        @Override
        public int getSqlType() throws SQLException
        {
            int sqlType = super.getSqlType();

            // Oracle claims all numbers are NUMBER... convert to INTEGER if decimal digits == 0
            if (3 == sqlType && 0 == _rsCols.getInt("DECIMAL_DIGITS"))
                return Types.INTEGER;

            return sqlType;
        }

        @Override
        public String getSqlTypeName() throws SQLException
        {
            String typeName = super.getSqlTypeName();

            if ("NUMBER".equals(typeName) && 0 == _rsCols.getInt("DECIMAL_DIGITS"))
                return "INTEGER";

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
        public OracleResultSetWrapper(ResultSet rs)
        {
            super(rs);
        }

        private Object translateValue(int i, Object value) throws SQLException
        {
            ResultSetMetaData rsmd = resultset.getMetaData();

            // Oracle always returns BigDecimal.  Use scale to determine if this is really an Integer.
            if (Types.NUMERIC == rsmd.getColumnType(i) && 0 == rsmd.getScale(i) && value instanceof BigDecimal)
                return ((BigDecimal)value).intValue();
            else
                return value;
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

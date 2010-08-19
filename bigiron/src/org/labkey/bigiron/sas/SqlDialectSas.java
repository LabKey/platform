/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
package org.labkey.bigiron.sas;

import junit.framework.TestSuite;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleSqlDialect;
import org.labkey.api.data.StatementWrapper;
import org.labkey.api.util.PageFlowUtil;

import javax.servlet.ServletException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Calendar;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: Jan 21, 2009
 * Time: 3:15:40 PM
 */
public abstract class SqlDialectSas extends SimpleSqlDialect
{
    public SqlDialectSas()
    {
        // TODO: Add more keywords
        reservedWordSet = new CaseInsensitiveHashSet(PageFlowUtil.set(
            "LEFT", "RIGHT"
        ));
    }

    protected String getProductName()
    {
        return "SAS";
    }

    @Override
    public boolean treatCatalogsAsSchemas()
    {
        return false;
    }

    protected void addSqlTypeNames(Map<String, Integer> sqlTypeNameMap)
    {
    }

    protected void addSqlTypeInts(Map<Integer, String> sqlTypeIntMap)
    {
        sqlTypeIntMap.put(Types.VARCHAR, "VARCHAR");
        sqlTypeIntMap.put(Types.DATE, "DATE");
        sqlTypeIntMap.put(Types.DOUBLE, "DOUBLE");
    }

    protected boolean claimsDriverClassName(String driverClassName)
    {
        return driverClassName.equals("com.sas.net.sharenet.ShareNetDriver");
    }

    public boolean requiresStatementMaxRows()
    {
        return true;
    }

    public SQLFragment limitRows(SQLFragment frag, int rowCount)
    {
        return frag;
    }

    public SQLFragment limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, String order, int rowCount, long offset)
    {
        if (select == null)
            throw new IllegalArgumentException("select");
        if (from == null)
            throw new IllegalArgumentException("from");

        SQLFragment sql = new SQLFragment();
        sql.append(select);
        sql.append("\n").append(from);
        if (filter != null) sql.append("\n").append(filter);
        if (order != null) sql.append("\n").append(order);

        return sql;
    }

    public boolean supportsOffset()
    {
        return false;
    }

    public String getConcatenationOperator()
    {
        return "||";
    }

    @Override
    public String concatenate(String... args)
    {
        return StringUtils.join(args, " || ");
    }

    public boolean supportsComments()
    {
        return false;
    }

    @Override
    public String getGroupConcatAggregateFunction(String selectName)
    {
        return "MIN(" + selectName + ")";
    }

    @Override
    public Integer getSPID(Connection result) throws SQLException
    {
        return 0;  // TODO: Implement?
    }

    protected String getSIDQuery()
    {
        throw new UnsupportedOperationException();
    }

    private static final Set<String> SYSTEM_SCHEMAS = PageFlowUtil.set("MAPS", "SASADMIN", "SASCATCA", "SASHELP", "SASUSER", "WORK");

    @Override
    public boolean isSystemSchema(String schemaName)
    {
        return SYSTEM_SCHEMAS.contains(schemaName);
    }

    // SAS has no database name, so override both getDatabaseName() methods and return null.

    @Override
    public String getDatabaseName(String dsName, DataSource ds) throws ServletException
    {
        return null;
    }

    @Override
    public String getDatabaseName(String url) throws ServletException
    {
        return null;
    }

    // SAS has no database name, so no need to parse the URL.  Overrides above ensure this is never called.

    public JdbcHelper getJdbcHelper()
    {
        throw new IllegalStateException();
    }

    public TestSuite getTestSuite()
    {
        return new TestSuite();
    }

    @Override
    protected StatementWrapper getStatementWrapper(ConnectionWrapper conn, Statement stmt)
    {
        return new SasStatementWrapper(conn, stmt);
    }

    @Override
    protected StatementWrapper getStatementWrapper(ConnectionWrapper conn, Statement stmt, String sql)
    {
        return new SasStatementWrapper(conn, stmt, sql);
    }

    // SAS driver doesn't support setting java.sql.Timestamp parameters, so convert to java.sql.Date
    private static class SasStatementWrapper extends StatementWrapper
    {
        protected SasStatementWrapper(ConnectionWrapper conn, Statement stmt)
        {
            super(conn, stmt);
        }

        protected SasStatementWrapper(ConnectionWrapper conn, Statement stmt, String sql)
        {
            super(conn, stmt, sql);
        }

        @Override
        public void setObject(int parameterIndex, Object x) throws SQLException
        {
            super.setObject(parameterIndex, convertParameter(x));
        }

        @Override
        public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException
        {
            super.setObject(parameterIndex, convertParameter(x), targetSqlType);
        }

        @Override
        public void setObject(int parameterIndex, Object x, int targetSqlType, int scale) throws SQLException
        {
            super.setObject(parameterIndex, convertParameter(x), targetSqlType, scale);
        }

        @Override
        public void setObject(String parameterName, Object x) throws SQLException
        {
            super.setObject(parameterName, convertParameter(x));
        }

        @Override
        public void setObject(String parameterName, Object x, int targetSqlType) throws SQLException
        {
            super.setObject(parameterName, convertParameter(x), targetSqlType);
        }

        @Override
        public void setObject(String parameterName, Object x, int targetSqlType, int scale) throws SQLException
        {
            super.setObject(parameterName, convertParameter(x), targetSqlType, scale);
        }

        @Override
        public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException
        {
            super.setDate(parameterIndex, new Date(x.getTime()));
        }

        @Override
        public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException
        {
            super.setDate(parameterIndex, new Date(x.getTime()), cal);
        }

        @Override
        public void setTimestamp(String parameterName, Timestamp x) throws SQLException
        {
            super.setDate(parameterName, new Date(x.getTime()));
        }

        @Override
        public void setTimestamp(String parameterName, Timestamp x, Calendar cal) throws SQLException
        {
            super.setDate(parameterName, new Date(x.getTime()), cal);
        }

        private Object convertParameter(Object x)
        {
            if (x instanceof Timestamp)
                return new Date(((Timestamp)x).getTime());
            else
                return x;
        }
    }
}

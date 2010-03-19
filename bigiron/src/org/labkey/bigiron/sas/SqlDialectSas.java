/*
 * Copyright (c) 2009 LabKey Corporation
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
import org.labkey.api.data.*;
import org.labkey.api.module.ModuleContext;

import javax.servlet.ServletException;
import javax.sql.DataSource;
import java.sql.*;
import java.util.Collection;
import java.util.Map;
import java.util.Calendar;

/**
 * User: adam
 * Date: Jan 21, 2009
 * Time: 3:15:40 PM
 */
public abstract class SqlDialectSas extends SqlDialect
{
    protected void addSqlTypeNames(Map<String, Integer> sqlTypeNameMap)
    {
        //To change body of implemented methods use File | Settings | File Templates.
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

    public void prepareNewDbSchema(DbSchema schema)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    protected String getProductName()
    {
        return "SAS";
    }

    public String getSQLScriptPath()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void appendStatement(StringBuilder sql, String statement)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void appendSelectAutoIncrement(StringBuilder sql, TableInfo table, String columnName)
    {
        //To change body of implemented methods use File | Settings | File Templates.
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

    public boolean supportsComments()
    {
        return false;
    }

    public String execute(DbSchema schema, String procedureName, String parameters)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getConcatenationOperator()
    {
        return "||";
    }

    public String getCharClassLikeOperator()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getCaseInsensitiveLikeOperator()
    {
        return "LIKE";
    }

    public String getVarcharLengthFunction()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getStdDevFunction()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getClobLengthFunction()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getStringIndexOfFunction(String stringToFind, String stringToSearch)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getSubstringFunction(String s, String start, String length)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void runSql(DbSchema schema, String sql, UpgradeCode upgradeCode, ModuleContext moduleContext) throws SQLException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getMasterDataBaseName()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getDefaultDateTimeDatatype()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getUniqueIdentType()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getTempTableKeyword()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getTempTablePrefix()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getGlobalTempTablePrefix()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isNoDatabaseException(SQLException e)
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isSortableDataType(String sqlDataTypeName)
    {
        return true;
    }

    public String getDropIndexCommand(String tableName, String indexName)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getCreateDatabaseSql(String dbName)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getCreateSchemaSql(String schemaName)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getDateDiff(int part, String value1, String value2)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getDateTimeToDateCast(String expression)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getRoundFunction(String valueToRound)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean supportsRoundDouble()
    {
        return false;  // TODO
    }

    public void overrideAutoIncrement(StringBuilder statements, TableInfo tinfo)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    protected void checkSqlScript(String lower, String lowerNoWhiteSpace, Collection<String> errors)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public String sanitizeException(SQLException ex)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getAnalyzeCommandForTable(String tableName)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Integer getSPID(Connection result) throws SQLException
    {
        return 0;
    }

    protected String getSIDQuery()
    {
        throw new UnsupportedOperationException();
    }

    public String getBooleanDatatype()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public String getBooleanLiteral(boolean b)
    {
        throw new UnsupportedOperationException();
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

    public JdbcHelper getJdbcHelper(String url) throws ServletException
    {
        return null;
    }

    public SQLFragment sqlLocate(SQLFragment littleString, SQLFragment bigString)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public SQLFragment sqlLocate(SQLFragment littleString, SQLFragment bigString, SQLFragment startIndex)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean allowSortOnSubqueryWithoutLimit()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void initializeConnection(Connection conn) throws SQLException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void purgeTempSchema(Map<String, TempTableTracker> createdTableNames)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isCaseSensitive()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isEditable()
    {
        return false;
    }

    public boolean isSqlServer()
    {
        return false;
    }

    public boolean isPostgreSQL()
    {
        return false;
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

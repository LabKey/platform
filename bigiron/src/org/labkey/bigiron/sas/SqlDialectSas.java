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

import com.sas.net.sharenet.ShareNetDriver;
import junit.framework.TestSuite;
import org.labkey.api.data.*;
import org.labkey.api.module.ModuleContext;

import javax.servlet.ServletException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;

/**
 * User: adam
 * Date: Jan 21, 2009
 * Time: 3:15:40 PM
 */
public class SqlDialectSas extends SqlDialect
{
    public SqlDialectSas()
    {
        try
        {
            new ShareNetDriver();
        }
        catch (SQLException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    protected void addSqlTypeNames(Map<String, Integer> sqlTypeNameMap)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    protected void addSqlTypeInts(Map<Integer, String> sqlTypeIntMap)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    protected boolean claimsDriverClassName(String driverClassName)
    {
        return driverClassName.equals(ShareNetDriver.class.getName());
    }

    // NOTE: SAS/SHARE driver throws when invoking DatabaseMetaData database version methods, so this will never return true.
    protected boolean claimsProductNameAndVersion(String dataBaseProductName, int majorVersion, int minorVersion)
    {
        return dataBaseProductName.equals(getProductName());
    }

    public void prepareNewDbSchema(DbSchema schema)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    protected String getProductName()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
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

    public SQLFragment limitRows(SQLFragment sql, int rowCount)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    // TODO: Implement
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
        return false;  //To change body of implemented methods use File | Settings | File Templates.
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
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getCharClassLikeOperator()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getCaseInsensitiveLikeOperator()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
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

    public ColumnMetaDataReader getColumnMetaDataReader(ResultSet rsCols)
    {
        return new SasColumnMetaDataReader(rsCols);
    }

    private class SasColumnMetaDataReader extends ColumnMetaDataReader
    {
        private SasColumnMetaDataReader(ResultSet rsCols)
        {
            super(rsCols);

            _nameKey = "NAME";
            _sqlTypeKey = "SQLTYPE";
            _scaleKey = "SIZE";
            _nullableKey = "NULLABLE";
            _postionKey = "POSITION";
        }

        @Override
        public String getSqlTypeName() throws SQLException
        {
            return sqlTypeNameFromSqlTypeInt(getSqlType());
        }

        public boolean isAutoIncrement() throws SQLException
        {
            return false;
        }
    }

    public PkMetaDataReader getPkMetaDataReader(ResultSet rs)
    {
        return new PkMetaDataReader(rs, "NAME", "SEQ") {
            @Override
            public String getName() throws SQLException
            {
                return super.getName().trim();
            }
        };
    }


    public TestSuite getTestSuite()
    {
        return new TestSuite();
    }
}

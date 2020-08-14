/*
 * Copyright (c) 2012-2018 LabKey Corporation
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
package org.labkey.query.jdbc;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.RowIdLifetime;

/**
 * User: matthewb
 * Date: 4/25/12
 * Time: 8:19 PM
 */
public class QueryDatabaseMetaData implements DatabaseMetaData
{
    final QueryConnection _conn;

    QueryDatabaseMetaData(QueryConnection conn)
    {
        _conn = conn;
    }

    @Override
    public boolean allProceduresAreCallable()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean allTablesAreSelectable()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getURL()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getUserName()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isReadOnly()
    {
        return true;
    }

    @Override
    public boolean nullsAreSortedHigh()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean nullsAreSortedLow()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean nullsAreSortedAtStart()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean nullsAreSortedAtEnd()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getDatabaseProductName()
    {
        return "LabKey Server";
    }

    @Override
    public String getDatabaseProductVersion()
    {
        return getDriverMajorVersion() + "." + getDriverMinorVersion();
    }

    @Override
    public String getDriverName()
    {
        return "LabKey Server Driver";
    }

    @Override
    public String getDriverVersion()
    {
        return getDriverMajorVersion() + "." + getDriverMinorVersion();
    }

    @Override
    public int getDriverMajorVersion()
    {
        return 0;
    }

    @Override
    public int getDriverMinorVersion()
    {
        return 1;
    }

    @Override
    public boolean usesLocalFiles()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean usesLocalFilePerTable()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsMixedCaseIdentifiers()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean storesUpperCaseIdentifiers()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean storesLowerCaseIdentifiers()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean storesMixedCaseIdentifiers()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsMixedCaseQuotedIdentifiers()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean storesUpperCaseQuotedIdentifiers()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean storesLowerCaseQuotedIdentifiers()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean storesMixedCaseQuotedIdentifiers()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getIdentifierQuoteString()
    {
        return "\"";
    }

    @Override
    public String getSQLKeywords()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getNumericFunctions()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getStringFunctions()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getSystemFunctions()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getTimeDateFunctions()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getSearchStringEscape()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getExtraNameCharacters()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsAlterTableWithAddColumn()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsAlterTableWithDropColumn()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsColumnAliasing()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean nullPlusNonNullIsNull()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsConvert()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsConvert(int i, int i1)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsTableCorrelationNames()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsDifferentTableCorrelationNames()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsExpressionsInOrderBy()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsOrderByUnrelated()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsGroupBy()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsGroupByUnrelated()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsGroupByBeyondSelect()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsLikeEscapeClause()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsMultipleResultSets()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsMultipleTransactions()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsNonNullableColumns()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsMinimumSQLGrammar()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsCoreSQLGrammar()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsExtendedSQLGrammar()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsANSI92EntryLevelSQL()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsANSI92IntermediateSQL()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsANSI92FullSQL()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsIntegrityEnhancementFacility()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsOuterJoins()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsFullOuterJoins()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsLimitedOuterJoins()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getSchemaTerm()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getProcedureTerm()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getCatalogTerm()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCatalogAtStart()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getCatalogSeparator()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsSchemasInDataManipulation()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsSchemasInProcedureCalls()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsSchemasInTableDefinitions()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsSchemasInIndexDefinitions()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsSchemasInPrivilegeDefinitions()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsCatalogsInDataManipulation()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsCatalogsInProcedureCalls()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsCatalogsInTableDefinitions()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsCatalogsInIndexDefinitions()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsCatalogsInPrivilegeDefinitions()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsPositionedDelete()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsPositionedUpdate()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsSelectForUpdate()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsStoredProcedures()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsSubqueriesInComparisons()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsSubqueriesInExists()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsSubqueriesInIns()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsSubqueriesInQuantifieds()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsCorrelatedSubqueries()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsUnion()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsUnionAll()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsOpenCursorsAcrossCommit()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsOpenCursorsAcrossRollback()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsOpenStatementsAcrossCommit()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsOpenStatementsAcrossRollback()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxBinaryLiteralLength()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxCharLiteralLength()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxColumnNameLength()
    {
        return 40;
    }

    @Override
    public int getMaxColumnsInGroupBy()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxColumnsInIndex()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxColumnsInOrderBy()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxColumnsInSelect()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxColumnsInTable()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxConnections()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxCursorNameLength()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxIndexLength()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxSchemaNameLength()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxProcedureNameLength()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxCatalogNameLength()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxRowSize()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean doesMaxRowSizeIncludeBlobs()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxStatementLength()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxStatements()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxTableNameLength()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxTablesInSelect()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxUserNameLength()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getDefaultTransactionIsolation()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsTransactions()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsTransactionIsolationLevel(int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsDataDefinitionAndDataManipulationTransactions()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsDataManipulationTransactionsOnly()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean dataDefinitionCausesTransactionCommit()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean dataDefinitionIgnoredInTransactions()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getProcedures(String s, String s1, String s2)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getProcedureColumns(String s, String s1, String s2, String s3)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getTables(String s, String s1, String s2, String[] strings)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getSchemas()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getCatalogs()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getTableTypes()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getColumns(String s, String s1, String s2, String s3)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getColumnPrivileges(String s, String s1, String s2, String s3)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getTablePrivileges(String s, String s1, String s2)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getBestRowIdentifier(String s, String s1, String s2, int i, boolean b)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getVersionColumns(String s, String s1, String s2)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getPrimaryKeys(String s, String s1, String s2)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getImportedKeys(String s, String s1, String s2)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getExportedKeys(String s, String s1, String s2)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getCrossReference(String s, String s1, String s2, String s3, String s4, String s5)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getTypeInfo()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getIndexInfo(String s, String s1, String s2, boolean b, boolean b1)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsResultSetType(int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsResultSetConcurrency(int i, int i1)
    {
        // TODO
        return true;
    }

    @Override
    public boolean ownUpdatesAreVisible(int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean ownDeletesAreVisible(int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean ownInsertsAreVisible(int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean othersUpdatesAreVisible(int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean othersDeletesAreVisible(int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean othersInsertsAreVisible(int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean updatesAreDetected(int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean deletesAreDetected(int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean insertsAreDetected(int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsBatchUpdates()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getUDTs(String s, String s1, String s2, int[] ints)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Connection getConnection()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsSavepoints()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsNamedParameters()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsMultipleOpenResults()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsGetGeneratedKeys()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getSuperTypes(String s, String s1, String s2)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getSuperTables(String s, String s1, String s2)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getAttributes(String s, String s1, String s2, String s3)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsResultSetHoldability(int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getResultSetHoldability()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getDatabaseMajorVersion()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getDatabaseMinorVersion()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getJDBCMajorVersion()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getJDBCMinorVersion()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getSQLStateType()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean locatorsUpdateCopy()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsStatementPooling()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public RowIdLifetime getRowIdLifetime()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getSchemas(String s, String s1)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsStoredFunctionsUsingCallSyntax()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean autoCommitFailureClosesAllResultSets()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getClientInfoProperties()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getFunctions(String s, String s1, String s2)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getFunctionColumns(String s, String s1, String s2, String s3)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T unwrap(Class<T> tClass)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isWrapperFor(Class<?> aClass)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getPseudoColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean generatedKeyAlwaysReturned()
    {
        throw new UnsupportedOperationException();
    }
}

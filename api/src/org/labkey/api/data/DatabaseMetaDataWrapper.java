/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.api.data;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.RowIdLifetime;
import java.sql.SQLException;

/**
 * Created by adam on 9/8/2015.
 */

// Do nothing wrapper for DatabaseMetaData
public abstract class DatabaseMetaDataWrapper implements DatabaseMetaData
{
    protected final DatabaseMetaData _md;

    public DatabaseMetaDataWrapper(DatabaseMetaData md)
    {
        _md = md;
    }

    @Override
    public boolean allProceduresAreCallable() throws SQLException
    {
        return _md.allProceduresAreCallable();
    }

    @Override
    public boolean allTablesAreSelectable() throws SQLException
    {
        return _md.allTablesAreSelectable();
    }

    @Override
    public String getURL() throws SQLException
    {
        return _md.getURL();
    }

    @Override
    public String getUserName() throws SQLException
    {
        return _md.getUserName();
    }

    @Override
    public boolean isReadOnly() throws SQLException
    {
        return _md.isReadOnly();
    }

    @Override
    public boolean nullsAreSortedHigh() throws SQLException
    {
        return _md.nullsAreSortedHigh();
    }

    @Override
    public boolean nullsAreSortedLow() throws SQLException
    {
        return _md.nullsAreSortedLow();
    }

    @Override
    public boolean nullsAreSortedAtStart() throws SQLException
    {
        return _md.nullsAreSortedAtStart();
    }

    @Override
    public boolean nullsAreSortedAtEnd() throws SQLException
    {
        return _md.nullsAreSortedAtEnd();
    }

    @Override
    public String getDatabaseProductName() throws SQLException
    {
        return _md.getDatabaseProductName();
    }

    @Override
    public String getDatabaseProductVersion() throws SQLException
    {
        return _md.getDatabaseProductVersion();
    }

    @Override
    public String getDriverName() throws SQLException
    {
        return _md.getDriverName();
    }

    @Override
    public String getDriverVersion() throws SQLException
    {
        return _md.getDriverVersion();
    }

    @Override
    public int getDriverMajorVersion()
    {
        return _md.getDriverMajorVersion();
    }

    @Override
    public int getDriverMinorVersion()
    {
        return _md.getDriverMinorVersion();
    }

    @Override
    public boolean usesLocalFiles() throws SQLException
    {
        return _md.usesLocalFiles();
    }

    @Override
    public boolean usesLocalFilePerTable() throws SQLException
    {
        return _md.usesLocalFilePerTable();
    }

    @Override
    public boolean supportsMixedCaseIdentifiers() throws SQLException
    {
        return _md.supportsMixedCaseIdentifiers();
    }

    @Override
    public boolean storesUpperCaseIdentifiers() throws SQLException
    {
        return _md.storesUpperCaseIdentifiers();
    }

    @Override
    public boolean storesLowerCaseIdentifiers() throws SQLException
    {
        return _md.storesLowerCaseIdentifiers();
    }

    @Override
    public boolean storesMixedCaseIdentifiers() throws SQLException
    {
        return _md.storesMixedCaseIdentifiers();
    }

    @Override
    public boolean supportsMixedCaseQuotedIdentifiers() throws SQLException
    {
        return _md.supportsMixedCaseQuotedIdentifiers();
    }

    @Override
    public boolean storesUpperCaseQuotedIdentifiers() throws SQLException
    {
        return _md.storesUpperCaseQuotedIdentifiers();
    }

    @Override
    public boolean storesLowerCaseQuotedIdentifiers() throws SQLException
    {
        return _md.storesLowerCaseQuotedIdentifiers();
    }

    @Override
    public boolean storesMixedCaseQuotedIdentifiers() throws SQLException
    {
        return _md.storesMixedCaseQuotedIdentifiers();
    }

    @Override
    public String getIdentifierQuoteString() throws SQLException
    {
        return _md.getIdentifierQuoteString();
    }

    @Override
    public String getSQLKeywords() throws SQLException
    {
        return _md.getSQLKeywords();
    }

    @Override
    public String getNumericFunctions() throws SQLException
    {
        return _md.getNumericFunctions();
    }

    @Override
    public String getStringFunctions() throws SQLException
    {
        return _md.getStringFunctions();
    }

    @Override
    public String getSystemFunctions() throws SQLException
    {
        return _md.getSystemFunctions();
    }

    @Override
    public String getTimeDateFunctions() throws SQLException
    {
        return _md.getTimeDateFunctions();
    }

    @Override
    public String getSearchStringEscape() throws SQLException
    {
        return _md.getSearchStringEscape();
    }

    @Override
    public String getExtraNameCharacters() throws SQLException
    {
        return _md.getExtraNameCharacters();
    }

    @Override
    public boolean supportsAlterTableWithAddColumn() throws SQLException
    {
        return _md.supportsAlterTableWithAddColumn();
    }

    @Override
    public boolean supportsAlterTableWithDropColumn() throws SQLException
    {
        return _md.supportsAlterTableWithDropColumn();
    }

    @Override
    public boolean supportsColumnAliasing() throws SQLException
    {
        return _md.supportsColumnAliasing();
    }

    @Override
    public boolean nullPlusNonNullIsNull() throws SQLException
    {
        return _md.nullPlusNonNullIsNull();
    }

    @Override
    public boolean supportsConvert() throws SQLException
    {
        return _md.supportsConvert();
    }

    @Override
    public boolean supportsConvert(int fromType, int toType) throws SQLException
    {
        return _md.supportsConvert(fromType, toType);
    }

    @Override
    public boolean supportsTableCorrelationNames() throws SQLException
    {
        return _md.supportsTableCorrelationNames();
    }

    @Override
    public boolean supportsDifferentTableCorrelationNames() throws SQLException
    {
        return _md.supportsDifferentTableCorrelationNames();
    }

    @Override
    public boolean supportsExpressionsInOrderBy() throws SQLException
    {
        return _md.supportsExpressionsInOrderBy();
    }

    @Override
    public boolean supportsOrderByUnrelated() throws SQLException
    {
        return _md.supportsOrderByUnrelated();
    }

    @Override
    public boolean supportsGroupBy() throws SQLException
    {
        return _md.supportsGroupBy();
    }

    @Override
    public boolean supportsGroupByUnrelated() throws SQLException
    {
        return _md.supportsGroupByUnrelated();
    }

    @Override
    public boolean supportsGroupByBeyondSelect() throws SQLException
    {
        return _md.supportsGroupByBeyondSelect();
    }

    @Override
    public boolean supportsLikeEscapeClause() throws SQLException
    {
        return _md.supportsLikeEscapeClause();
    }

    @Override
    public boolean supportsMultipleResultSets() throws SQLException
    {
        return _md.supportsMultipleResultSets();
    }

    @Override
    public boolean supportsMultipleTransactions() throws SQLException
    {
        return _md.supportsMultipleTransactions();
    }

    @Override
    public boolean supportsNonNullableColumns() throws SQLException
    {
        return _md.supportsNonNullableColumns();
    }

    @Override
    public boolean supportsMinimumSQLGrammar() throws SQLException
    {
        return _md.supportsMinimumSQLGrammar();
    }

    @Override
    public boolean supportsCoreSQLGrammar() throws SQLException
    {
        return _md.supportsCoreSQLGrammar();
    }

    @Override
    public boolean supportsExtendedSQLGrammar() throws SQLException
    {
        return _md.supportsExtendedSQLGrammar();
    }

    @Override
    public boolean supportsANSI92EntryLevelSQL() throws SQLException
    {
        return _md.supportsANSI92EntryLevelSQL();
    }

    @Override
    public boolean supportsANSI92IntermediateSQL() throws SQLException
    {
        return _md.supportsANSI92IntermediateSQL();
    }

    @Override
    public boolean supportsANSI92FullSQL() throws SQLException
    {
        return _md.supportsANSI92FullSQL();
    }

    @Override
    public boolean supportsIntegrityEnhancementFacility() throws SQLException
    {
        return _md.supportsIntegrityEnhancementFacility();
    }

    @Override
    public boolean supportsOuterJoins() throws SQLException
    {
        return _md.supportsOuterJoins();
    }

    @Override
    public boolean supportsFullOuterJoins() throws SQLException
    {
        return _md.supportsFullOuterJoins();
    }

    @Override
    public boolean supportsLimitedOuterJoins() throws SQLException
    {
        return _md.supportsLimitedOuterJoins();
    }

    @Override
    public String getSchemaTerm() throws SQLException
    {
        return _md.getSchemaTerm();
    }

    @Override
    public String getProcedureTerm() throws SQLException
    {
        return _md.getProcedureTerm();
    }

    @Override
    public String getCatalogTerm() throws SQLException
    {
        return _md.getCatalogTerm();
    }

    @Override
    public boolean isCatalogAtStart() throws SQLException
    {
        return _md.isCatalogAtStart();
    }

    @Override
    public String getCatalogSeparator() throws SQLException
    {
        return _md.getCatalogSeparator();
    }

    @Override
    public boolean supportsSchemasInDataManipulation() throws SQLException
    {
        return _md.supportsSchemasInDataManipulation();
    }

    @Override
    public boolean supportsSchemasInProcedureCalls() throws SQLException
    {
        return _md.supportsSchemasInProcedureCalls();
    }

    @Override
    public boolean supportsSchemasInTableDefinitions() throws SQLException
    {
        return _md.supportsSchemasInTableDefinitions();
    }

    @Override
    public boolean supportsSchemasInIndexDefinitions() throws SQLException
    {
        return _md.supportsSchemasInIndexDefinitions();
    }

    @Override
    public boolean supportsSchemasInPrivilegeDefinitions() throws SQLException
    {
        return _md.supportsSchemasInPrivilegeDefinitions();
    }

    @Override
    public boolean supportsCatalogsInDataManipulation() throws SQLException
    {
        return _md.supportsCatalogsInDataManipulation();
    }

    @Override
    public boolean supportsCatalogsInProcedureCalls() throws SQLException
    {
        return _md.supportsCatalogsInProcedureCalls();
    }

    @Override
    public boolean supportsCatalogsInTableDefinitions() throws SQLException
    {
        return _md.supportsCatalogsInTableDefinitions();
    }

    @Override
    public boolean supportsCatalogsInIndexDefinitions() throws SQLException
    {
        return _md.supportsCatalogsInIndexDefinitions();
    }

    @Override
    public boolean supportsCatalogsInPrivilegeDefinitions() throws SQLException
    {
        return _md.supportsCatalogsInPrivilegeDefinitions();
    }

    @Override
    public boolean supportsPositionedDelete() throws SQLException
    {
        return _md.supportsPositionedDelete();
    }

    @Override
    public boolean supportsPositionedUpdate() throws SQLException
    {
        return _md.supportsPositionedUpdate();
    }

    @Override
    public boolean supportsSelectForUpdate() throws SQLException
    {
        return _md.supportsSelectForUpdate();
    }

    @Override
    public boolean supportsStoredProcedures() throws SQLException
    {
        return _md.supportsStoredProcedures();
    }

    @Override
    public boolean supportsSubqueriesInComparisons() throws SQLException
    {
        return _md.supportsSubqueriesInComparisons();
    }

    @Override
    public boolean supportsSubqueriesInExists() throws SQLException
    {
        return _md.supportsSubqueriesInExists();
    }

    @Override
    public boolean supportsSubqueriesInIns() throws SQLException
    {
        return _md.supportsSubqueriesInIns();
    }

    @Override
    public boolean supportsSubqueriesInQuantifieds() throws SQLException
    {
        return _md.supportsSubqueriesInQuantifieds();
    }

    @Override
    public boolean supportsCorrelatedSubqueries() throws SQLException
    {
        return _md.supportsCorrelatedSubqueries();
    }

    @Override
    public boolean supportsUnion() throws SQLException
    {
        return _md.supportsUnion();
    }

    @Override
    public boolean supportsUnionAll() throws SQLException
    {
        return _md.supportsUnionAll();
    }

    @Override
    public boolean supportsOpenCursorsAcrossCommit() throws SQLException
    {
        return _md.supportsOpenCursorsAcrossCommit();
    }

    @Override
    public boolean supportsOpenCursorsAcrossRollback() throws SQLException
    {
        return _md.supportsOpenCursorsAcrossRollback();
    }

    @Override
    public boolean supportsOpenStatementsAcrossCommit() throws SQLException
    {
        return _md.supportsOpenStatementsAcrossCommit();
    }

    @Override
    public boolean supportsOpenStatementsAcrossRollback() throws SQLException
    {
        return _md.supportsOpenStatementsAcrossRollback();
    }

    @Override
    public int getMaxBinaryLiteralLength() throws SQLException
    {
        return _md.getMaxBinaryLiteralLength();
    }

    @Override
    public int getMaxCharLiteralLength() throws SQLException
    {
        return _md.getMaxCharLiteralLength();
    }

    @Override
    public int getMaxColumnNameLength() throws SQLException
    {
        return _md.getMaxColumnNameLength();
    }

    @Override
    public int getMaxColumnsInGroupBy() throws SQLException
    {
        return _md.getMaxColumnsInGroupBy();
    }

    @Override
    public int getMaxColumnsInIndex() throws SQLException
    {
        return _md.getMaxColumnsInIndex();
    }

    @Override
    public int getMaxColumnsInOrderBy() throws SQLException
    {
        return _md.getMaxColumnsInOrderBy();
    }

    @Override
    public int getMaxColumnsInSelect() throws SQLException
    {
        return _md.getMaxColumnsInSelect();
    }

    @Override
    public int getMaxColumnsInTable() throws SQLException
    {
        return _md.getMaxColumnsInTable();
    }

    @Override
    public int getMaxConnections() throws SQLException
    {
        return _md.getMaxConnections();
    }

    @Override
    public int getMaxCursorNameLength() throws SQLException
    {
        return _md.getMaxCursorNameLength();
    }

    @Override
    public int getMaxIndexLength() throws SQLException
    {
        return _md.getMaxIndexLength();
    }

    @Override
    public int getMaxSchemaNameLength() throws SQLException
    {
        return _md.getMaxSchemaNameLength();
    }

    @Override
    public int getMaxProcedureNameLength() throws SQLException
    {
        return _md.getMaxProcedureNameLength();
    }

    @Override
    public int getMaxCatalogNameLength() throws SQLException
    {
        return _md.getMaxCatalogNameLength();
    }

    @Override
    public int getMaxRowSize() throws SQLException
    {
        return _md.getMaxRowSize();
    }

    @Override
    public boolean doesMaxRowSizeIncludeBlobs() throws SQLException
    {
        return _md.doesMaxRowSizeIncludeBlobs();
    }

    @Override
    public int getMaxStatementLength() throws SQLException
    {
        return _md.getMaxStatementLength();
    }

    @Override
    public int getMaxStatements() throws SQLException
    {
        return _md.getMaxStatements();
    }

    @Override
    public int getMaxTableNameLength() throws SQLException
    {
        return _md.getMaxTableNameLength();
    }

    @Override
    public int getMaxTablesInSelect() throws SQLException
    {
        return _md.getMaxTablesInSelect();
    }

    @Override
    public int getMaxUserNameLength() throws SQLException
    {
        return _md.getMaxUserNameLength();
    }

    @Override
    public int getDefaultTransactionIsolation() throws SQLException
    {
        return _md.getDefaultTransactionIsolation();
    }

    @Override
    public boolean supportsTransactions() throws SQLException
    {
        return _md.supportsTransactions();
    }

    @Override
    public boolean supportsTransactionIsolationLevel(int level) throws SQLException
    {
        return _md.supportsTransactionIsolationLevel(level);
    }

    @Override
    public boolean supportsDataDefinitionAndDataManipulationTransactions() throws SQLException
    {
        return _md.supportsDataDefinitionAndDataManipulationTransactions();
    }

    @Override
    public boolean supportsDataManipulationTransactionsOnly() throws SQLException
    {
        return _md.supportsDataManipulationTransactionsOnly();
    }

    @Override
    public boolean dataDefinitionCausesTransactionCommit() throws SQLException
    {
        return _md.dataDefinitionCausesTransactionCommit();
    }

    @Override
    public boolean dataDefinitionIgnoredInTransactions() throws SQLException
    {
        return _md.dataDefinitionIgnoredInTransactions();
    }

    @Override
    public ResultSet getProcedures(String catalog, String schemaPattern, String procedureNamePattern) throws SQLException
    {
        return _md.getProcedures(catalog, schemaPattern, procedureNamePattern);
    }

    @Override
    public ResultSet getProcedureColumns(String catalog, String schemaPattern, String procedureNamePattern, String columnNamePattern) throws SQLException
    {
        return _md.getProcedureColumns(catalog, schemaPattern, procedureNamePattern, columnNamePattern);
    }

    @Override
    public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types) throws SQLException
    {
        return _md.getTables(catalog, schemaPattern, tableNamePattern, types);
    }

    @Override
    public ResultSet getSchemas() throws SQLException
    {
        return _md.getSchemas();
    }

    @Override
    public ResultSet getCatalogs() throws SQLException
    {
        return _md.getCatalogs();
    }

    @Override
    public ResultSet getTableTypes() throws SQLException
    {
        return _md.getTableTypes();
    }

    @Override
    public ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException
    {
        return _md.getColumns(catalog, schemaPattern, tableNamePattern, columnNamePattern);
    }

    @Override
    public ResultSet getColumnPrivileges(String catalog, String schema, String table, String columnNamePattern) throws SQLException
    {
        return _md.getColumnPrivileges(catalog, schema, table, columnNamePattern);
    }

    @Override
    public ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern) throws SQLException
    {
        return _md.getTablePrivileges(catalog, schemaPattern, tableNamePattern);
    }

    @Override
    public ResultSet getBestRowIdentifier(String catalog, String schema, String table, int scope, boolean nullable) throws SQLException
    {
        return _md.getBestRowIdentifier(catalog, schema, table, scope, nullable);
    }

    @Override
    public ResultSet getVersionColumns(String catalog, String schema, String table) throws SQLException
    {
        return _md.getVersionColumns(catalog, schema, table);
    }

    @Override
    public ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException
    {
        return _md.getPrimaryKeys(catalog, schema, table);
    }

    @Override
    public ResultSet getImportedKeys(String catalog, String schema, String table) throws SQLException
    {
        return _md.getImportedKeys(catalog, schema, table);
    }

    @Override
    public ResultSet getExportedKeys(String catalog, String schema, String table) throws SQLException
    {
        return _md.getExportedKeys(catalog, schema, table);
    }

    @Override
    public ResultSet getCrossReference(String parentCatalog, String parentSchema, String parentTable, String foreignCatalog, String foreignSchema, String foreignTable) throws SQLException
    {
        return _md.getCrossReference(parentCatalog, parentSchema, parentTable, foreignCatalog, foreignSchema, foreignTable);
    }

    @Override
    public ResultSet getTypeInfo() throws SQLException
    {
        return _md.getTypeInfo();
    }

    @Override
    public ResultSet getIndexInfo(String catalog, String schema, String table, boolean unique, boolean approximate) throws SQLException
    {
        return _md.getIndexInfo(catalog, schema, table, unique, approximate);
    }

    @Override
    public boolean supportsResultSetType(int type) throws SQLException
    {
        return _md.supportsResultSetType(type);
    }

    @Override
    public boolean supportsResultSetConcurrency(int type, int concurrency) throws SQLException
    {
        return _md.supportsResultSetConcurrency(type, concurrency);
    }

    @Override
    public boolean ownUpdatesAreVisible(int type) throws SQLException
    {
        return _md.ownUpdatesAreVisible(type);
    }

    @Override
    public boolean ownDeletesAreVisible(int type) throws SQLException
    {
        return _md.ownDeletesAreVisible(type);
    }

    @Override
    public boolean ownInsertsAreVisible(int type) throws SQLException
    {
        return _md.ownInsertsAreVisible(type);
    }

    @Override
    public boolean othersUpdatesAreVisible(int type) throws SQLException
    {
        return _md.othersUpdatesAreVisible(type);
    }

    @Override
    public boolean othersDeletesAreVisible(int type) throws SQLException
    {
        return _md.othersDeletesAreVisible(type);
    }

    @Override
    public boolean othersInsertsAreVisible(int type) throws SQLException
    {
        return _md.othersInsertsAreVisible(type);
    }

    @Override
    public boolean updatesAreDetected(int type) throws SQLException
    {
        return _md.updatesAreDetected(type);
    }

    @Override
    public boolean deletesAreDetected(int type) throws SQLException
    {
        return _md.deletesAreDetected(type);
    }

    @Override
    public boolean insertsAreDetected(int type) throws SQLException
    {
        return _md.insertsAreDetected(type);
    }

    @Override
    public boolean supportsBatchUpdates() throws SQLException
    {
        return _md.supportsBatchUpdates();
    }

    @Override
    public ResultSet getUDTs(String catalog, String schemaPattern, String typeNamePattern, int[] types) throws SQLException
    {
        return _md.getUDTs(catalog, schemaPattern, typeNamePattern, types);
    }

    @Override
    public Connection getConnection() throws SQLException
    {
        return _md.getConnection();
    }

    @Override
    public boolean supportsSavepoints() throws SQLException
    {
        return _md.supportsSavepoints();
    }

    @Override
    public boolean supportsNamedParameters() throws SQLException
    {
        return _md.supportsNamedParameters();
    }

    @Override
    public boolean supportsMultipleOpenResults() throws SQLException
    {
        return _md.supportsMultipleOpenResults();
    }

    @Override
    public boolean supportsGetGeneratedKeys() throws SQLException
    {
        return _md.supportsGetGeneratedKeys();
    }

    @Override
    public ResultSet getSuperTypes(String catalog, String schemaPattern, String typeNamePattern) throws SQLException
    {
        return _md.getSuperTypes(catalog, schemaPattern, typeNamePattern);
    }

    @Override
    public ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern) throws SQLException
    {
        return _md.getSuperTables(catalog, schemaPattern, tableNamePattern);
    }

    @Override
    public ResultSet getAttributes(String catalog, String schemaPattern, String typeNamePattern, String attributeNamePattern) throws SQLException
    {
        return _md.getAttributes(catalog, schemaPattern, typeNamePattern, attributeNamePattern);
    }

    @Override
    public boolean supportsResultSetHoldability(int holdability) throws SQLException
    {
        return _md.supportsResultSetHoldability(holdability);
    }

    @Override
    public int getResultSetHoldability() throws SQLException
    {
        return _md.getResultSetHoldability();
    }

    @Override
    public int getDatabaseMajorVersion() throws SQLException
    {
        return _md.getDatabaseMajorVersion();
    }

    @Override
    public int getDatabaseMinorVersion() throws SQLException
    {
        return _md.getDatabaseMinorVersion();
    }

    @Override
    public int getJDBCMajorVersion() throws SQLException
    {
        return _md.getJDBCMajorVersion();
    }

    @Override
    public int getJDBCMinorVersion() throws SQLException
    {
        return _md.getJDBCMinorVersion();
    }

    @Override
    public int getSQLStateType() throws SQLException
    {
        return _md.getSQLStateType();
    }

    @Override
    public boolean locatorsUpdateCopy() throws SQLException
    {
        return _md.locatorsUpdateCopy();
    }

    @Override
    public boolean supportsStatementPooling() throws SQLException
    {
        return _md.supportsStatementPooling();
    }

    @Override
    public RowIdLifetime getRowIdLifetime() throws SQLException
    {
        return _md.getRowIdLifetime();
    }

    @Override
    public ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException
    {
        return _md.getSchemas(catalog, schemaPattern);
    }

    @Override
    public boolean supportsStoredFunctionsUsingCallSyntax() throws SQLException
    {
        return _md.supportsStoredFunctionsUsingCallSyntax();
    }

    @Override
    public boolean autoCommitFailureClosesAllResultSets() throws SQLException
    {
        return _md.autoCommitFailureClosesAllResultSets();
    }

    @Override
    public ResultSet getClientInfoProperties() throws SQLException
    {
        return _md.getClientInfoProperties();
    }

    @Override
    public ResultSet getFunctions(String catalog, String schemaPattern, String functionNamePattern) throws SQLException
    {
        return _md.getFunctions(catalog, schemaPattern, functionNamePattern);
    }

    @Override
    public ResultSet getFunctionColumns(String catalog, String schemaPattern, String functionNamePattern, String columnNamePattern) throws SQLException
    {
        return _md.getFunctionColumns(catalog, schemaPattern, functionNamePattern, columnNamePattern);
    }

    @Override
    public ResultSet getPseudoColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException
    {
        return _md.getPseudoColumns(catalog, schemaPattern, tableNamePattern, columnNamePattern);
    }

    @Override
    public boolean generatedKeyAlwaysReturned() throws SQLException
    {
        return _md.generatedKeyAlwaysReturned();
    }

    @Override
    public long getMaxLogicalLobSize() throws SQLException
    {
        return _md.getMaxLogicalLobSize();
    }

    @Override
    public boolean supportsRefCursors() throws SQLException
    {
        return _md.supportsRefCursors();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException
    {
        //noinspection unchecked
        return isWrapperFor(iface) ? (T)_md : null;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException
    {
        return iface.isAssignableFrom(DatabaseMetaData.class);
    }
}

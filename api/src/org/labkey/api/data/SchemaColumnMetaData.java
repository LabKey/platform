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

package org.labkey.api.data;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.dialect.JdbcMetaDataLocator;
import org.labkey.api.data.dialect.PkMetaDataReader;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryService;
import org.labkey.api.sql.LabKeySql;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.DebugInfoDumper;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/*
* User: adam
* Date: Jun 29, 2011
* Time: 3:02:47 PM
*/
public class SchemaColumnMetaData
{
    private final SchemaTableInfo _tinfo;
    private final List<ColumnInfo> _columns = new ArrayList<>();

    private Map<String, ColumnInfo> _colMap = null;
    private @NotNull List<String> _pkColumnNames = new ArrayList<>();
    private List<ColumnInfo> _pkColumns;
    private String _titleColumn = null;
    private boolean _hasDefaultTitleColumn = true;
    private Map<String, Pair<TableInfo.IndexType, List<ColumnInfo>>> _uniqueIndices;
    private Map<String, Pair<TableInfo.IndexType, List<ColumnInfo>>> _allIndices;

    private static final Logger _log = LogHelper.getLogger(SchemaColumnMetaData.class, "Extracts column metadata through JDBC and schema-scoped XML overrides");

    SchemaColumnMetaData(SchemaTableInfo tinfo, boolean load) throws SQLException
    {
        _tinfo = tinfo;
        if (load)
        {
            loadFromMetaData(_tinfo);
            loadColumnsFromXml(_tinfo, tinfo.getXmlTable());
        }
    }

    /* This constructor is used only to create a virtual/fake SchemaTableInfo */
    public SchemaColumnMetaData(SchemaTableInfo tinfo, List<MutableColumnInfo> cols, TableType xmlTable) throws SQLException
    {
        _tinfo = tinfo;
        for (var col : cols)
            addColumn(col);
        loadColumnsFromXml(_tinfo, xmlTable);
    }

    private AliasManager getAliasManager(AliasManager aliasManager)
    {
        if (null == aliasManager)
        {
            aliasManager = new AliasManager(_tinfo.getSchema());
            aliasManager.claimAliases(_columns);
        }
        return aliasManager;
    }

    private void loadColumnsFromXml(SchemaTableInfo tinfo, TableType xmlTable)
    {
        if (null == xmlTable)
            return;

        TableType.Columns columns = xmlTable.getColumns();

        if (null == columns)
            return;
        ColumnType[] xmlColumnArray = columns.getColumnArray();
        if (0 == xmlColumnArray.length)
            return;

        // Don't overwrite pk
        if (_pkColumnNames.isEmpty())
        {
            String pkColumnName = xmlTable.getPkColumnName();

            if (null != pkColumnName && pkColumnName.length() > 0)
            {
                setPkColumnNames(Arrays.asList(pkColumnName.split(",")));
            }
        }

        AliasManager aliasManager = null; // We're making an effort to be lazy about initializing.  Most SchemaTableInfo only have "real" columns.
        List<ColumnType> wrappedColumns = new ArrayList<>();

        for (ColumnType xmlColumn : xmlColumnArray)
        {
            if ((xmlColumn.isSetWrappedColumnName() && isNotBlank(xmlColumn.getWrappedColumnName())) ||
                (xmlColumn.isSetValueExpression() && isNotBlank(xmlColumn.getValueExpression())))
            {
                wrappedColumns.add(xmlColumn);
            }
            else
            {
                BaseColumnInfo colInfo;

                if (tinfo.getTableType() != DatabaseTableType.NOT_IN_DB)
                {
                    colInfo = (BaseColumnInfo) getColumn(xmlColumn.getColumnName());
                    if (null != colInfo)
                    {
                        loadFromXml(xmlColumn, colInfo, true);
                        continue;
                    }
                }

                if (tinfo.getTableType() != DatabaseTableType.NOT_IN_DB)
                    colInfo = new VirtualColumnInfo(FieldKey.fromParts(xmlColumn.getColumnName()), tinfo);
                else
                    colInfo = new BaseColumnInfo(FieldKey.fromParts(xmlColumn.getColumnName()), tinfo);
                colInfo.setNullable(true);
                loadFromXml(xmlColumn, colInfo, false);
                aliasManager = getAliasManager(aliasManager);
                aliasManager.ensureAlias(colInfo);
                addColumn(colInfo);
            }
        }

        // snapshot the names of "real" columns at this point.  These are use for validation.
        Map<FieldKey,ColumnInfo> allowedColumns = new HashMap<>();
        for (var c : _columns)
            allowedColumns.put(c.getFieldKey(), c);

        for (ColumnType xmlColumn : wrappedColumns)
        {
            // Treat schema.xml as "code" and throw ConfigurationException if problems are found
            if (null != getColumn(xmlColumn.getColumnName()))
                throw new ConfigurationException("Error adding column '" + xmlColumn.getColumnName() + "' to table '" + tinfo.getName() + "'. Column already exists.");

            ColumnInfo boundColumn = null;
            String sql = "";
            if (xmlColumn.isSetValueExpression() && isNotBlank(xmlColumn.getValueExpression()))
            {
                sql = xmlColumn.getValueExpression();
            }
            else if (xmlColumn.isSetWrappedColumnName() && isNotBlank(xmlColumn.getWrappedColumnName()))
            {
                sql = LabKeySql.quoteIdentifier(xmlColumn.getWrappedColumnName());
                boundColumn = getColumn(xmlColumn.getWrappedColumnName());
                if (null == boundColumn)
                    throw new ConfigurationException("Error adding column '" + xmlColumn.getColumnName() + "' to table '" + tinfo.getName() + "'. '" + xmlColumn.getWrappedColumnName() + "' was not found.");
            }
            if (isNotBlank(sql))
            {
                try
                {
                    var exprColumn  = null != boundColumn ?
                            QueryService.get().createQueryExpressionColumn(tinfo, FieldKey.fromParts(xmlColumn.getColumnName()), boundColumn.getFieldKey(), xmlColumn) :
                            QueryService.get().createQueryExpressionColumn(tinfo, FieldKey.fromParts(xmlColumn.getColumnName()), sql, xmlColumn);
                    QueryService.get().bindQueryExpressionColumn(exprColumn, allowedColumns, true, null);
                    aliasManager = getAliasManager(aliasManager);
                    aliasManager.ensureAlias(exprColumn);
                    addColumn(exprColumn);
                }
                catch (QueryParseException qpe)
                {
                    throw new ConfigurationException("Error adding column '" + xmlColumn.getColumnName() + "' to table '" + tinfo.getName() + "'. " + qpe.getMessage());
                }
            }
        }

        _titleColumn = xmlTable.getTitleColumn();

        if (null != _titleColumn)
            _hasDefaultTitleColumn = false;

        if (xmlTable.getUseColumnOrder())
        {
            int columnIndex = 0;
            // Reorder based on the sequence of columns in XML
            for (ColumnType xmlColumn : xmlTable.getColumns().getColumnArray())
            {
                // Iterate through the ones in the XML and add them in the right order
                ColumnInfo column = getColumn(xmlColumn.getColumnName());
                if (column != null)
                {
                    _columns.remove(column);
                    _columns.add(columnIndex++, column);
                }
            }
        }
    }

    protected void loadFromXml(ColumnType xmlColumn, BaseColumnInfo colInfo, boolean merge)
    {
        colInfo.loadFromXml(xmlColumn, merge);
    }

    /** Simplify the wrapping/unwrapping via a RuntimeSQLException */
    private interface RetrySqlException
    {
        void exec(DbScope.Transaction tx) throws SQLException;
    }

    private DbScope.RetryFn<Void> createRetryWrapper(RetrySqlException retry)
    {
        return (tx) ->
        {
            try
            {
                retry.exec(tx);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
            return null;
        };
    }

    private void loadFromMetaData(SchemaTableInfo ti) throws SQLException
    {
        try (var ignore = DebugInfoDumper.pushThreadDumpContext("SchemaColumnMetaData.loadFromMetaData(" + ti.getSelectName() + ")"))
        {
            // With the Microsoft JDBC driver we're seeing more deadlocks loading schema metadata so try multiple
            // times when possible
            ti.getSchema().getScope().executeWithRetryReadOnly(createRetryWrapper((tx) -> loadColumnsFromMetaData(ti)));
            ti.getSchema().getScope().executeWithRetryReadOnly(createRetryWrapper((tx) -> loadPkColumns(ti)));
            ti.getSchema().getScope().executeWithRetryReadOnly(createRetryWrapper((tx) -> loadIndices(ti)));
        }
        catch (RuntimeSQLException e)
        {
            // Unwrap to avoid changing the signature of loadFromMetaData()
            throw e.getSQLException();
        }
    }

    private void loadPkColumns(SchemaTableInfo ti) throws SQLException
    {
        DbSchema schema = ti.getSchema();
        DbScope scope = schema.getScope();
        String schemaName = schema.getName();

        // Use TreeMap to order columns by keySeq
        Map<Integer, String> pkMap = new TreeMap<>();

        try (JdbcMetaDataLocator locator = scope.getSqlDialect().getTableResolver().getSingleTableLocator(scope, schemaName, ti))
        {
            JdbcMetaDataSelector pkSelector = new JdbcMetaDataSelector(locator,
                (dbmd, locator1) -> dbmd.getPrimaryKeys(locator1.getCatalogName(), locator1.getSchemaName(), locator1.getTableName()));

            try (ResultSet rs = pkSelector.getResultSet())
            {
                int columnCount = 0;
                PkMetaDataReader reader = ti.getSqlDialect().getPkMetaDataReader(rs);

                while (rs.next())
                {
                    columnCount++;
                    String colName = reader.getName();
                    BaseColumnInfo colInfo = (BaseColumnInfo)getColumn(colName);

                    if (null != colInfo)
                    {
                        colInfo.setKeyField(true);
                        int keySeq = reader.getKeySeq();

                        // If we don't have sequence information (e.g., SAS doesn't return it) then use 1-based counter as a backup
                        if (0 == keySeq)
                            keySeq = columnCount;

                        pkMap.put(keySeq, colName);
                    }
                    else
                    {
                        // Logging to help track down #33924
                        ExceptionUtil.logExceptionToMothership(null, new IllegalStateException("Can't resolve PK column name \"" + colName + "\" in table \"" + ti.getSelectName() + "\" in schema \"" + schemaName + "\" in a " + scope.getDatabaseProductName() + " database. Valid names in _colMap: " + _colMap.keySet()));
                    }
                }
            }
        }

        setPkColumnNames(new ArrayList<>(pkMap.values()));
    }

    private void loadColumnsFromMetaData(SchemaTableInfo ti) throws SQLException
    {
        BaseColumnInfo.createFromDatabaseMetaData(ti.getSchema().getName(), ti, null /* all columns */)
            .forEach(this::addColumn);
    }

    private void loadIndices(SchemaTableInfo ti) throws SQLException
    {
        DbSchema schema = ti.getSchema();
        DbScope scope = schema.getScope();
        String schemaName = schema.getName();
        if (!ti.getSqlDialect().canCheckIndices(ti))
        {
            _uniqueIndices = Collections.emptyMap();
            _allIndices = Collections.emptyMap();
        }
        else
        {
            try (JdbcMetaDataLocator locator = scope.getSqlDialect().getTableResolver().getSingleTableLocator(scope, schemaName, ti))
            {
                JdbcMetaDataSelector uqSelector = new JdbcMetaDataSelector(locator,
                        ((dbmd, l) -> {
                            boolean uniqueIndicesOnly = false;
                            return dbmd.getIndexInfo(l.getCatalogName(), l.getSchemaName(), l.getTableName(), uniqueIndicesOnly, true);
                        }));

                Set<String> ignoreIndex = new HashSet<>();
                Map<String, Pair<TableInfo.IndexType, List<ColumnInfo>>> indexMap = new HashMap<>();
                uqSelector.forEach(rs -> {
                    String colName = rs.getString("COLUMN_NAME");
                    String indexName = rs.getString("INDEX_NAME");
                    if (indexName == null)
                        return;

                    indexName = indexName.toLowerCase();

                    Pair<TableInfo.IndexType, List<ColumnInfo>> pair = indexMap.get(indexName);
                    if (pair == null)
                    {
                        TableInfo.IndexType indexType = rs.getBoolean("NON_UNIQUE")?TableInfo.IndexType.NonUnique:TableInfo.IndexType.Unique;
                        indexMap.put(indexName, pair = Pair.of(indexType, new ArrayList<>(2)));
                    }

                    ColumnInfo colInfo = getColumn(colName);
                    // Column will be null for indices over expressions, eg.: "lower(name)"
                    if (colInfo == null)
                        ignoreIndex.add(indexName);
                    else
                        pair.getValue().add(colInfo);
                });

                // Remove ignored indices
                ignoreIndex.forEach(indexMap::remove);

                Map<String, Pair<TableInfo.IndexType, List<ColumnInfo>>> uniqueIndexMap = new HashMap<>();
                Map<String, Pair<TableInfo.IndexType, List<ColumnInfo>>> allIndexMap = new HashMap<>();

                // Search for the primary index and change the index type to Primary
                for (Map.Entry<String, Pair<TableInfo.IndexType, List<ColumnInfo>>> entry : indexMap.entrySet())
                {
                    List<ColumnInfo> cols = entry.getValue().getValue();
                    if (getPkColumns().equals(cols))
                    {
                        Pair<TableInfo.IndexType, List<ColumnInfo>> indexTypeListPair = Pair.of(TableInfo.IndexType.Primary, cols);
                        uniqueIndexMap.put(entry.getKey(),indexTypeListPair);
                        allIndexMap.put(entry.getKey(),indexTypeListPair);
                    }
                    else
                    {
                        if (entry.getValue().getKey()== TableInfo.IndexType.Unique)
                        {
                            uniqueIndexMap.put(entry.getKey(), entry.getValue());
                        }
                        allIndexMap.put(entry.getKey(), entry.getValue());
                    }
                }
                _uniqueIndices = Collections.unmodifiableMap(uniqueIndexMap);
                _allIndices = Collections.unmodifiableMap(allIndexMap);
            }
        }
    }

    public List<ColumnInfo> getColumns()
    {
        return Collections.unmodifiableList(_columns);
    }

    protected void addColumn(MutableColumnInfo column)
    {
        if (getColumn(column.getName()) != null)
            _log.warn("Duplicate column '" + column.getName() + "' on table '" + _tinfo.getName() + "'");

        _columns.add(column);
        assert null == column.getFieldKey().getParent();
        assert column.getName().equals(column.getFieldKey().getName());
        assert !(column instanceof BaseColumnInfo) || ((BaseColumnInfo)column).lockName();
        // set alias explicitly, so that getAlias() won't call makeLegalName() and mangle it
        if (!column.isAliasSet())
        {
            if (null != column.getMetaDataName())
                column.setAlias(column.getMetaDataName());
            else
                column.setAlias(column.getName());
        }
        _colMap = null;
    }

    ColumnInfo getColumn(String colName)
    {
        if (null == colName)
            return null;

        if (null == _colMap)
        {
            Map<String, ColumnInfo> m = new CaseInsensitiveHashMap<>();
            for (ColumnInfo colInfo : _columns)
            {
                m.put(colInfo.getName(), colInfo);
            }
            _colMap = m;
        }

        // TODO: Shouldn't do this -- ":" is a legal character in column names
        int colonIndex;

        if ((colonIndex = colName.indexOf(":")) != -1)
        {
            String first = colName.substring(0, colonIndex);
            String rest = colName.substring(colonIndex + 1);
            ColumnInfo fkColInfo = _colMap.get(first);

            // Fall through if this doesn't look like an FK -- : is a legal character
            if (fkColInfo != null && fkColInfo.getFk() != null)
                return fkColInfo.getFkTableInfo().getColumn(rest);
        }

        return _colMap.get(colName);
    }

    public void setPkColumnNames(@NotNull List<String> pkColumnNames)
    {
        _pkColumnNames = Collections.unmodifiableList(pkColumnNames);
        _pkColumns = null;
    }

    public List<ColumnInfo> getUserEditableColumns()
    {
        ArrayList<ColumnInfo> userEditableColumns = new ArrayList<>(_columns.size());

        for (ColumnInfo col : _columns)
            if (col.isUserEditable())
                userEditableColumns.add(col);

        return Collections.unmodifiableList(userEditableColumns);
    }


    public Set<String> getColumnNameSet()
    {
        Set<String> nameSet = new HashSet<>();

        for (ColumnInfo aColumnList : _columns)
        {
            nameSet.add(aColumnList.getName());
        }

        return Collections.unmodifiableSet(nameSet);
    }


    public String getTitleColumn()
    {
        if (null == _titleColumn && !_columns.isEmpty())
        {
            for (ColumnInfo column : _columns)
            {
                if (column.isStringType() && !SqlDialect.isGUIDType(column.getSqlTypeName()))
                {
                    _titleColumn = column.getName();
                    break;
                }
            }

            if (null == _titleColumn)
                _titleColumn = _columns.get(0).getName();
        }

        return _titleColumn;
    }


    // Move an existing column to a different spot in the ordered list
    public void setColumnIndex(ColumnInfo c, int i)
    {
        if (!_columns.remove(c))
        {
            throw new IllegalArgumentException("Column " + c + " is not part of table " + _tinfo);
        }
        _columns.add(Math.min(_columns.size(),i), c);
    }

    public boolean hasDefaultTitleColumn()
    {
        return _hasDefaultTitleColumn;
    }

    @NotNull 
    public List<ColumnInfo> getPkColumns()
    {
        if (null == _pkColumns)
        {
            List<ColumnInfo> cols = new ArrayList<>(_pkColumnNames.size());

            for (String name : _pkColumnNames)
            {
                ColumnInfo col = getColumn(name);

                if (null == col)
                    throw new IllegalStateException("Primary key name \"" + name + "\" with no corresponding column in table " + _tinfo.toString());

                cols.add(col);
            }

            _pkColumns = Collections.unmodifiableList(cols);
        }

        return _pkColumns;
    }

    public @NotNull List<String> getPkColumnNames()
    {
        return _pkColumnNames;
    }

    public @NotNull Map<String, Pair<TableInfo.IndexType, List<ColumnInfo>>> getUniqueIndices()
    {
        if(_uniqueIndices == null)
        {
            try
            {
                loadIndices(_tinfo);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
        return _uniqueIndices;
    }

    public @NotNull Map<String, Pair<TableInfo.IndexType, List<ColumnInfo>>> getAllIndices()
    {
        if(_allIndices == null)
        {
            try
            {
                loadIndices(_tinfo);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
        return _allIndices;
    }


    void copyToXml(TableType xmlTable, boolean bFull)
    {
        if (bFull)
        {
            if (!_pkColumnNames.isEmpty())
                xmlTable.setPkColumnName(StringUtils.join(_pkColumnNames, ','));
            if (null != _titleColumn)
                xmlTable.setTitleColumn(_titleColumn);
        }

        TableType.Columns xmlColumns = xmlTable.addNewColumns();
        ColumnType xmlCol;

        for (ColumnInfo columnInfo : _columns)
        {
            xmlCol = xmlColumns.addNewColumn();
            columnInfo.copyToXml(xmlCol, bFull);
        }
    }


    /** On upgrade there may be columns in .xml that are not in the database */
    private static class VirtualColumnInfo extends NullColumnInfo
    {
        VirtualColumnInfo(FieldKey fieldKey, TableInfo tinfo)
        {
            super(tinfo, fieldKey, (String)null);
            setIsUnselectable(true);    // minor hack, to indicate to other code that wants to detect this
        }
    }
}

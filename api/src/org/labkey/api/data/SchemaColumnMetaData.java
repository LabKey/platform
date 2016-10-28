/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.dialect.JdbcMetaDataLocator;
import org.labkey.api.data.dialect.PkMetaDataReader;
import org.labkey.api.util.Pair;
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
    private static Logger _log = Logger.getLogger(SchemaColumnMetaData.class);

    protected SchemaColumnMetaData(SchemaTableInfo tinfo) throws SQLException
    {
        this(tinfo, true);
    }

    SchemaColumnMetaData(SchemaTableInfo tinfo, boolean load) throws SQLException
    {
        _tinfo = tinfo;
        if (load)
        {
            loadFromMetaData(_tinfo);
            loadColumnsFromXml(_tinfo);
        }
    }

    private void loadColumnsFromXml(SchemaTableInfo tinfo)
    {
        TableType xmlTable = tinfo.getXmlTable();

        if (null == xmlTable)
            return;

        TableType.Columns columns = xmlTable.getColumns();

        if (null == columns)
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

        ColumnType[] xmlColumnArray = columns.getColumnArray();
        List<ColumnType> wrappedColumns = new ArrayList<>();

        for (ColumnType xmlColumn : xmlColumnArray)
        {
            if (xmlColumn.getWrappedColumnName() != null)
            {
                wrappedColumns.add(xmlColumn);
            }
            else
            {
                ColumnInfo colInfo;

                if (tinfo.getTableType() != DatabaseTableType.NOT_IN_DB)
                {
                    colInfo = getColumn(xmlColumn.getColumnName());
                    if (null != colInfo)
                    {
                        loadFromXml(xmlColumn, colInfo, true);
                        continue;
                    }
                }

                if (tinfo.getTableType() != DatabaseTableType.NOT_IN_DB)
                    colInfo = new VirtualColumnInfo(xmlColumn.getColumnName(), tinfo);
                else
                    colInfo = new ColumnInfo(xmlColumn.getColumnName(), tinfo);
                colInfo.setNullable(true);
                loadFromXml(xmlColumn, colInfo, false);
                addColumn(colInfo);
            }
        }

        for (ColumnType wrappedColumnXml : wrappedColumns)
        {
            ColumnInfo column = getColumn(wrappedColumnXml.getWrappedColumnName());

            if (column != null && getColumn(wrappedColumnXml.getColumnName()) == null)
            {
                ColumnInfo wrappedColumn = new WrappedColumn(column, wrappedColumnXml.getColumnName());
                loadFromXml(wrappedColumnXml, wrappedColumn, false);
                addColumn(wrappedColumn);
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

    protected void loadFromXml(ColumnType xmlColumn, ColumnInfo colInfo, boolean merge)
    {
        colInfo.loadFromXml(xmlColumn, merge);
    }

    private void loadFromMetaData(SchemaTableInfo ti) throws SQLException
    {
        loadColumnsFromMetaData(ti);
        loadPkColumns(ti);
        loadIndices(ti);
    }

    private void loadPkColumns(SchemaTableInfo ti) throws SQLException
    {
        DbSchema schema = ti.getSchema();
        DbScope scope = schema.getScope();
        String schemaName = schema.getName();

        // Use TreeMap to order columns by keySeq
        Map<Integer, String> pkMap = new TreeMap<>();

        try (JdbcMetaDataLocator locator = scope.getSqlDialect().getJdbcMetaDataLocator(scope, schemaName, ti.getMetaDataName()))
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
                    ColumnInfo colInfo = getColumn(colName);
                    assert null != colInfo;

                    colInfo.setKeyField(true);
                    int keySeq = reader.getKeySeq();

                    // If we don't have sequence information (e.g., SAS doesn't return it) then use 1-based counter as a backup
                    if (0 == keySeq)
                        keySeq = columnCount;

                    pkMap.put(keySeq, colName);
                }
            }
        }

        setPkColumnNames(new ArrayList<>(pkMap.values()));
    }

    private void loadColumnsFromMetaData(SchemaTableInfo ti) throws SQLException
    {
        ColumnInfo.createFromDatabaseMetaData(ti.getSchema().getName(), ti, null /** all columns **/)
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
            try (JdbcMetaDataLocator locator = scope.getSqlDialect().getJdbcMetaDataLocator(scope, schemaName, ti.getMetaDataName()))
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

    protected void addColumn(ColumnInfo column)
    {
        if(getColumn(column.getName()) != null)
            _log.warn("Duplicate column '" + column.getName() + "' on table '" + _tinfo.getName() + "'");

        _columns.add(column);
//        assert !column.isAliasSet();       // TODO: Investigate -- had to comment this out since ExprColumn() sets alias
        assert null == column.getFieldKey().getParent();
        assert column.getName().equals(column.getFieldKey().getName());
        assert column.lockName();
        // set alias explicitly, so that getAlias() won't call makeLegalName() and mangle it
        column.setAlias(column.getName());
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
                if (column.isStringType() && !StringUtils.equalsIgnoreCase("entityid",column.getSqlTypeName()))
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
    private class VirtualColumnInfo extends NullColumnInfo
    {
        VirtualColumnInfo(String name, TableInfo tinfo)
        {
            super(tinfo,name,(String)null);
            setIsUnselectable(true);    // minor hack, to indicate to other code that wants to detect this
        }
    }
}

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

package org.labkey.api.data;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.JdbcMetaDataSelector.JdbcMetaDataResultSetFactory;
import org.labkey.api.data.dialect.JdbcMetaDataLocator;
import org.labkey.api.data.dialect.PkMetaDataReader;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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

    protected SchemaColumnMetaData(SchemaTableInfo tinfo) throws SQLException
    {
        this(tinfo, true);
    }

    SchemaColumnMetaData(SchemaTableInfo tinfo, boolean load) throws SQLException
    {
        _tinfo = tinfo;
        if (load)
        {
            DbScope scope = _tinfo.getSchema().getScope();
            boolean inTransaction = scope.isTransactionActive();
            Connection conn = null;

            try
            {
                conn = scope.getConnection();
                loadFromMetaData(conn.getMetaData(), _tinfo);
            }
            finally
            {
                if (!inTransaction && null != conn)
                    scope.releaseConnection(conn);
            }

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

    private void loadFromMetaData(DatabaseMetaData dbmd, final SchemaTableInfo ti) throws SQLException
    {
        DbSchema schema = ti.getSchema();
        DbScope scope = schema.getScope();
        final String catalogName = scope.getDatabaseName();
        final String schemaName = schema.getName();

        loadColumnsFromMetaData(dbmd, catalogName, schemaName, ti);

        // Use TreeMap to order columns by keySeq
        Map<Integer, String> pkMap2 = new TreeMap<>();

        try (JdbcMetaDataLocator locator = scope.getSqlDialect().getMetaDataLocator(scope, schemaName, ti.getMetaDataName()))
        {
            JdbcMetaDataSelector pkSelector = new JdbcMetaDataSelector(locator, new JdbcMetaDataResultSetFactory(){
                @Override
                public ResultSet getResultSet(DatabaseMetaData dbmd, JdbcMetaDataLocator locator) throws SQLException
                {
                    return dbmd.getPrimaryKeys(locator.getCatalogName(), locator.getSchemaName(), locator.getTableName());
                }
            });

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

                    pkMap2.put(keySeq, colName);
                }
            }
        }

        setPkColumnNames(new ArrayList<>(pkMap2.values()));
    }

    private void loadColumnsFromMetaData(DatabaseMetaData dbmd, String catalogName, String schemaName, SchemaTableInfo ti) throws SQLException
    {
        Collection<ColumnInfo> meta = ColumnInfo.createFromDatabaseMetaData(dbmd, catalogName, schemaName, ti);
        for (ColumnInfo c : meta)
            addColumn(c);
    }

    public List<ColumnInfo> getColumns()
    {
        return Collections.unmodifiableList(_columns);
    }

    protected void addColumn(ColumnInfo column)
    {
        assert getColumn(column.getName()) == null : "Duplicate column " + column.getName() + " on table " + _tinfo.getName();
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

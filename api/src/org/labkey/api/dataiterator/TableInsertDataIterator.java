/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

package org.labkey.api.dataiterator;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.StatementUtils;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

public class TableInsertDataIterator extends StatementDataIterator implements DataIteratorBuilder
{
    DbScope _scope = null;
    Connection _conn = null;
    final TableInfo _table;
    final Container _c;
    boolean _selectIds = false;
    QueryUpdateService.InsertOption _insertOption = QueryUpdateService.InsertOption.INSERT;
    final Set<String> _skipColumnNames = new CaseInsensitiveHashSet();
    final Set<String> _dontUpdate = new CaseInsensitiveHashSet();
    final Set<String> _keyColumns = new CaseInsensitiveHashSet();


    public static DataIteratorBuilder create(DataIterator data, TableInfo table, DataIteratorContext context)
    {
        TableInsertDataIterator it;
        it = new TableInsertDataIterator(data, table, null, context, null, null, null);
        return it;
    }


    /** If container != null, it will be set as a constant in the insert statement */
    public static DataIteratorBuilder create(DataIteratorBuilder data, TableInfo table, @Nullable Container c, DataIteratorContext context)
    {
        return create(data, table, c, context, null, null, null);
    }


    public static DataIteratorBuilder create(DataIteratorBuilder data, TableInfo table, @Nullable Container c, DataIteratorContext context,
         @Nullable Set<String> keyColumns, @Nullable Set<String> addlSkipColumns, @Nullable Set<String> dontUpdate)
    {
        DataIterator di = data.getDataIterator(context);
        if (null == di)
        {
            //noinspection ThrowableResultOfMethodCallIgnored
            if (!context.getErrors().hasErrors())
                throw new NullPointerException("getDataIterator() returned NULL");
            return null;
        }
        if (null == dontUpdate)
        {
            dontUpdate = context.getDontUpdateColumnNames();
        }
        else
        {
            dontUpdate.addAll(context.getDontUpdateColumnNames());
        }
        if (null == keyColumns)
        {
            keyColumns = context.getAlternateKeys();
        }
        else
        {
            keyColumns.addAll(context.getAlternateKeys());
        }
        TableInsertDataIterator it;
        it = new TableInsertDataIterator(di, table, c, context, keyColumns, addlSkipColumns, dontUpdate);
        return it;
    }


    protected TableInsertDataIterator(DataIterator data, TableInfo table, Container c, DataIteratorContext context,
          @Nullable Set<String> keyColumns, @Nullable Set<String> addlSkipColumns, @Nullable Set<String> dontUpdate)
    {
        super(data, null, context);
        this._table = table;
        this._c = c;
        this._insertOption = context.getInsertOption();

        if (null != addlSkipColumns)
            _skipColumnNames.addAll(addlSkipColumns);
        if (null != dontUpdate)
            _dontUpdate.addAll(dontUpdate);
        if (null != keyColumns)
            _keyColumns.addAll(keyColumns);

        ColumnInfo colAutoIncrement = null;
        Integer indexAutoIncrement = null;

        Map<String,Integer> map = DataIteratorUtil.createColumnNameMap(data);
        for (ColumnInfo col : table.getColumns())
        {
            Integer index = map.get(col.getName());

            //Dont add null values if col has a Default from DB
            if (null == index && null != col.getJdbcDefaultValue())
                _skipColumnNames.add(col.getName());

            // Don't add calculated columns (CONSIDER: what about readOnly and !userEditable columns?)
            if (null == index && col.isCalculated())
                _skipColumnNames.add(col.getName());

            //record autoincrement key column so we can add if need to reselect
            if (col.isAutoIncrement() && !context.supportsAutoIncrementKey())
            {
                indexAutoIncrement = index;
                colAutoIncrement = col;
            }

            //Map missing-value columns
            FieldKey mvColumnName = col.getMvColumnName();
            if (null == index || null == mvColumnName)
                continue;
            data.getColumnInfo(index).setMvColumnName(mvColumnName);
        }

        // NOTE StatementUtils figures out reselect etc, but we need to get our metadata straight at construct time
        // Can't move StatementUtils.insertStatement here because the transaction might not be started yet

        if (null != context.getSelectIds())
            _selectIds = context.getSelectIds();
        else
        {
            boolean forInsert = _context.getInsertOption().reselectIds;
            boolean hasTriggers = _table.hasTriggers(_c);
            _selectIds = forInsert || hasTriggers;
        }

        //Add autoincrement column if we need to reselect
        if (_selectIds && colAutoIncrement != null)
        {
            TableInfo t = ((UpdateableTableInfo)table).getSchemaTableInfo();
            // check that there is actually an autoincrement column in schema table (List has fake auto increment)
            for (ColumnInfo col : t.getColumns())
            {
                if (col.isAutoIncrement())
                {
                    setRowIdColumn(indexAutoIncrement==null?-1:indexAutoIncrement, colAutoIncrement);
                    break;
                }
            }
        }
    }


    @Override
    void init()
    {
        try
        {
            final Map<String,Object> constants = new CaseInsensitiveHashMap<>();
            for (int i=1 ; i<=_data.getColumnCount() ; i++)
            {
                if (_data.isConstant(i))
                    constants.put(_data.getColumnInfo(i).getName(),_data.getConstantValue(i));
            }

            _scope = ((UpdateableTableInfo)_table).getSchemaTableInfo().getSchema().getScope();
            _conn = _scope.getConnection();

            Parameter.ParameterMap stmt;
            if (_insertOption == QueryUpdateService.InsertOption.MERGE)
            {
                if (_context.supportsAutoIncrementKey())
                    setAutoIncrement(INSERT.ON);
                stmt = StatementUtils.mergeStatement(_conn, _table, _keyColumns, _skipColumnNames, _dontUpdate, _c, null, _selectIds, false, _context.supportsAutoIncrementKey());
            }
            else
            {
                if (_insertOption == QueryUpdateService.InsertOption.IMPORT_IDENTITY)
                    setAutoIncrement(INSERT.ON);
                stmt = StatementUtils.insertStatement(_conn, _table, _skipColumnNames, _c, null, constants, _selectIds, false, _context.supportsAutoIncrementKey());
            }

            if (_context.getInsertOption().batch && null == _rowIdIndex && null == _objectIdIndex)
            {
                _stmts = new Parameter.ParameterMap[]{stmt, stmt.copy()};
                setUseAsynchronousExecute(true);
            }
            else
            {
                _stmts = new Parameter.ParameterMap[]{stmt};
                setUseAsynchronousExecute(false);
            }

            super.init();
            if (_selectIds)
                _batchSize = 1;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    @Override
    public DataIterator getDataIterator(DataIteratorContext context)
    {
        assert _context == context;
        return this;
    }


    @Override
    protected void onFirst()
    {
        init();
    }


    boolean _closed = false;

    @Override
    public void close() throws IOException
    {
        if (_closed)
            return;
        _closed = true;
        super.close();
        if (null != _scope && null != _conn)
        {
            if (_insertOption == QueryUpdateService.InsertOption.IMPORT_IDENTITY ||
                (_insertOption == QueryUpdateService.InsertOption.MERGE && _context.supportsAutoIncrementKey()))
            {
                setAutoIncrement(INSERT.OFF);
            }
            _scope.releaseConnection(_conn);
        }
    }

    private enum INSERT
    {ON, OFF};

    private void setAutoIncrement(INSERT bound)
    {
        TableInfo t = ((UpdateableTableInfo)_table).getSchemaTableInfo();
        if (_context.supportsAutoIncrementKey() && null != _scope && null != _conn && t.getSelectName() != null)
        {
            final String selectName = t.getSelectName();
            if (_scope.getSqlDialect().isSqlServer())
            {
                SQLFragment check = new SQLFragment("SET IDENTITY_INSERT ").append(selectName).append(" ").append(bound.toString());
                new SqlExecutor(_scope, _conn).execute(check);
            }
            else if (_scope.getSqlDialect().isPostgreSQL() && bound == INSERT.OFF)
            {
                // Find the 'serial' column
                ColumnInfo autoIncCol = null;
                for (ColumnInfo col : t.getColumns())
                {
                    if (col.isAutoIncrement())
                    {
                        autoIncCol = col;
                        break;
                    }
                }

                // Update the sequence for the serial column with the max+1 and handle empty tables
                if (autoIncCol != null && autoIncCol.getSelectName() != null)
                {
                    String colSelectName = autoIncCol.getSelectName();
                    SQLFragment resetSeq = new SQLFragment();
                    resetSeq.append("SELECT setval(\n");
                    resetSeq.append("  pg_get_serial_sequence('").append(selectName).append("', '").append(colSelectName).append("'),\n");
                    resetSeq.append("  COALESCE((SELECT MAX(").append(colSelectName).append(")+1 FROM ").append(selectName).append("), 1),\n");
                    resetSeq.append("  false");
                    resetSeq.append(");\n");
                    new SqlExecutor(_scope, _conn).execute(resetSeq);
                }
            }
        }
    }
}

/*
 * Copyright (c) 2016-2019 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.ParameterMapStatement;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.StatementUtils;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService.InsertOption;
import org.labkey.api.query.column.BuiltInColumnTypes;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.api.query.QueryUpdateService.ConfigParameters.PreferPKOverObjectUriAsKey;

public class TableInsertUpdateDataIterator extends StatementDataIterator implements DataIteratorBuilder
{
    private final TableInfo _table;
    private final Container _container;
    private final boolean _selectIds;
    private final InsertOption _insertOption;
    private final Set<String> _skipColumnNames = new CaseInsensitiveHashSet();
    private final Set<String> _dontUpdate = new CaseInsensitiveHashSet();
    private final Set<String> _keyColumns = new CaseInsensitiveHashSet();

    private DbScope _scope = null;
    private Connection _conn = null;
    private Set<DomainProperty> _adhocPropColumns = new LinkedHashSet<>();

    private boolean _skipCurrentIterator = false; // if StatementUtils generates a bad or meaningless (empty) statement, skip this iterator
    private boolean _failOnEmptyUpdate;

    private boolean _preferPKOverObjectUriAsKey = false;

    /**
     * Creates and configures a TableInsertDataIterator. DO NOT call this method directly.
     * Instead instantiate a {@link TableInsertDataIteratorBuilder}.
     * @param container If container != null, it will be set as a constant in the insert statement.
     */
    public static DataIterator create(DataIteratorBuilder data, TableInfo table, @Nullable Container container, DataIteratorContext context,
         @Nullable Set<String> keyColumns, @Nullable Set<String> addlSkipColumns, @Nullable Set<String> dontUpdate,
         @Nullable Set<DomainProperty> vocabularyColumns, boolean commitRowsBeforeContinuing,
                                      @Nullable Map<String, String> remapSchemaColumns, boolean failOnEmptyUpdate)
            //extra param @NUllable Set<PDs/Names?CIs> VOCCOls
    {
        // TODO it would be better to postpone calling data.getDataIterator() until the TableInsertDataIterator.getDataIterator() is called
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

        if (context.getInsertOption().mergeRows && !context.getInsertOption().replace)
        {
            final Map<String, String> remap = remapSchemaColumns != null ? remapSchemaColumns : Collections.emptyMap();

            // If the target has additional columns that aren't present in the source, don't overwrite (update) existing values...
            Set<String> targetOnlyColumnNames = table.getColumns()
                    .stream()
                    .map(col -> remap.containsKey(col.getName()) ? remap.get(col.getName()) : col.getName())
                    .collect(Collectors.toCollection(CaseInsensitiveHashSet::new));
            for (int i = 1; i <= di.getColumnCount(); i++) // Note DataIterator column index is 1-based
            {
                targetOnlyColumnNames.remove(di.getColumnInfo(i).getName());
            }
            // ... except for the Modified and ModifiedBy columns, which should still be updated.
            targetOnlyColumnNames.removeAll(Arrays.stream(BuiltInColumnTypes.values())
                    .filter(val -> val.when != BuiltInColumnTypes.When.insert)
                    .map(Enum::name)
                    .collect(Collectors.toSet()));

            dontUpdate.addAll(targetOnlyColumnNames);
        }

        if (null == keyColumns)
        {
            keyColumns = context.getAlternateKeys();
        }
        else
        {
            keyColumns.addAll(context.getAlternateKeys());
        }
        TableInsertUpdateDataIterator ti = new TableInsertUpdateDataIterator(di, table, container, context, keyColumns, addlSkipColumns, dontUpdate, failOnEmptyUpdate);
        DataIterator ret = ti;


        // UNFORTUNATELY I can't tell if TableInsertDataIterator is row at a time until AFTER init()
        // However, _selectIds is set during construction, and will force row-at-time
        if (commitRowsBeforeContinuing && !ti._selectIds)
        {
            var emb = new EmbargoDataIterator(context, ti, null, null);
            ti.setEmbargoDataIterator(emb);
            ret = emb;
        }

        if (null != vocabularyColumns && vocabularyColumns.size() > 0)
        {
            ti.setAdhocPropColumns(vocabularyColumns);
        }

        if (context.getConfigParameterBoolean(PreferPKOverObjectUriAsKey))
            ti.setPreferPKOverObjectUriAsKey(true);

        return ret;
    }


    protected TableInsertUpdateDataIterator(DataIterator data, TableInfo table, @Nullable Container container, DataIteratorContext context,
                                            @Nullable Set<String> keyColumns, @Nullable Set<String> addlSkipColumns,
                                            @Nullable Set<String> dontUpdate, boolean failOnEmptyUpdate)
    {
        super(table.getSqlDialect(), data, context);
        setDebugName(table.getName());

        _table = table;
        _container = container;
        _insertOption = context.getInsertOption();

        if (null != addlSkipColumns)
            _skipColumnNames.addAll(addlSkipColumns);
        if (null != dontUpdate)
            _dontUpdate.addAll(dontUpdate);
        if (null != keyColumns)
            _keyColumns.addAll(keyColumns);

        _failOnEmptyUpdate = failOnEmptyUpdate;

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
            if (col.isAutoIncrement() && !context.supportsAutoIncrementKey() && !col.isCalculated())
            {
                indexAutoIncrement = index;
                colAutoIncrement = col;
            }

            //Map missing-value columns
            FieldKey mvColumnName = col.getMvColumnName();
            if (null == index || null == mvColumnName)
                continue;
            // TODO copy ColumnInfo instead of modifying the input's ColumnInfo
            // If the input columninfo was created from a TableInfo (instead of manually) the mvColumnName is probably correct already.
            // Check before calling setMvColumnName() to avoid ColumnInfo is locked error.
            if (!mvColumnName.equals(getColumnInfo(index).getMvColumnName()))
                ((BaseColumnInfo)data.getColumnInfo(index)).setMvColumnName(mvColumnName);
        }

        // NOTE StatementUtils figures out reselect etc, but we need to get our metadata straight at construct time
        // Can't move StatementUtils.insertStatement here because the transaction might not be started yet

        if (null != context.getSelectIds())
            _selectIds = context.getSelectIds();
        else
        {
            boolean forInsert = _context.getInsertOption().reselectIds;
            boolean hasTriggers = _table.hasTriggers(_container);
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

            ParameterMapStatement stmt;
            if (_insertOption.updateOnly)
            {
                stmt = getUpdateStatement(constants);
            }
            else if (_insertOption.mergeRows)
            {
                stmt = getMergeStatement(constants);
            }
            else
            {
                stmt = getInsertStatement(constants);
            }

            if (_table.getSqlDialect().allowAsynchronousExecute() && _context.getInsertOption().batch && null == _rowIdIndex && null == _objectIdIndex)
            {
                _stmts = new ParameterMapStatement[]{stmt, stmt.copy()};
                setUseAsynchronousExecute(true);
            }
            else
            {
                _stmts = new ParameterMapStatement[]{stmt};
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
        catch(NoUpdatableColumnInDataException x)
        {
            if (_failOnEmptyUpdate)
                throw new IllegalArgumentException(x.getMessage());
            else
                _skipCurrentIterator = true;
        }
    }

    @Override
    protected boolean shouldSkipIterator()
    {
        return _skipCurrentIterator;
    }

    protected ParameterMapStatement getInsertStatement(Map<String, Object> constants) throws SQLException
    {
        ParameterMapStatement stmt;
        if (_insertOption.identity_insert)
            setAutoIncrement(INSERT.ON);

        //pass in the voc cols in this builder
        StatementUtils utils = new StatementUtils(StatementUtils.Operation.insert, _table)
                .skip(_skipColumnNames)
                .allowSetAutoIncrement(_context.supportsAutoIncrementKey())
                .updateBuiltinColumns(false)
                .selectIds(_selectIds)
                .constants(constants)
                .setVocabularyProperties(_adhocPropColumns);
        stmt = utils.createStatement(_conn, _container, null);
        return stmt;
    }

    protected ParameterMapStatement getMergeStatement(Map<String, Object> constants) throws SQLException
    {
        ParameterMapStatement stmt;
        if (_context.supportsAutoIncrementKey())
            setAutoIncrement(INSERT.ON);

        StatementUtils util = new StatementUtils(StatementUtils.Operation.merge, _table)
                    .keys(_keyColumns)
                    .skip(_skipColumnNames)
                    .allowSetAutoIncrement(_context.supportsAutoIncrementKey())
                    .noupdate(_dontUpdate)
                    .updateBuiltinColumns(false)
                    .selectIds(_selectIds)
                    .constants(constants)
                    .setVocabularyProperties(_adhocPropColumns);
        stmt = util.createStatement(_conn, _container, null);
        return stmt;
    }

    protected ParameterMapStatement getUpdateStatement(Map<String, Object> constants) throws SQLException, NoUpdatableColumnInDataException
    {
        ParameterMapStatement stmt;
        setAutoIncrement(INSERT.OFF);

        StatementUtils util = new StatementUtils(StatementUtils.Operation.update, _table)
                .keys(_keyColumns)
                .skip(_skipColumnNames)
                .allowSetAutoIncrement(false)
                .noupdate(_dontUpdate)
                .updateBuiltinColumns(false)
                .selectIds(_selectIds)
                .setPreferPKOverObjectUriAsKey(_preferPKOverObjectUriAsKey)
                .constants(constants)
                .setVocabularyProperties(_adhocPropColumns);
        stmt = util.createStatement(_conn, _container, null, true);
        return stmt;
    }


    @Override
    public DataIterator getDataIterator(DataIteratorContext context)
    {
        assert _context == context;
        return LoggingDataIterator.wrap(this);
    }


    @Override
    @Nullable
    protected Parameter getMvParameter(@NotNull ParameterMapStatement stmt, @NotNull FieldKey mvFieldKey)
    {
        Parameter mv = super.getMvParameter(stmt, mvFieldKey);
        if (null == mv)
        {
            // Issue #33549: MV columns with spaces in the name (both lists and datasets)
            // Iterator makes SQL stmt from table, but munges names (see Parameter), so we need to match that to find them.
            ColumnInfo mvColumn = _table.getColumn(mvFieldKey);
            if (null != mvColumn)
                mv = stmt.getParameter(BaseColumnInfo.jdbcRsNameFromName(mvColumn.getMetaDataName()));
        }
        return mv;
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
            if (_insertOption.identity_insert ||
                (_insertOption.mergeRows && _context.supportsAutoIncrementKey()))
            {
                setAutoIncrement(INSERT.OFF);
            }
            _scope.releaseConnection(_conn);
        }
    }

    private enum INSERT
    {ON, OFF}

    private void setAutoIncrement(INSERT bound)
    {
        TableInfo t = ((UpdateableTableInfo)_table).getSchemaTableInfo();
        if (_context.supportsAutoIncrementKey() && null != _scope && null != _conn && t.getSelectName() != null)
        {
            // Find a serial/identity column
            ColumnInfo autoIncCol = null;
            for (ColumnInfo col : t.getColumns())
            {
                if (col.isAutoIncrement())
                {
                    autoIncCol = col;
                    break;
                }
            }

            // 35579: Do not set auto-increment bounds if a auto-increment column is not found
            if (autoIncCol == null)
                return;

            if (_scope.getSqlDialect().isSqlServer())
            {
                SQLFragment check = new SQLFragment("SET IDENTITY_INSERT ").append(t).append(" ").append(bound.toString());
                new SqlExecutor(_scope, _conn).execute(check);
            }
            else if (_scope.getSqlDialect().isPostgreSQL() && bound == INSERT.OFF)
            {
                // Update the sequence for the serial column with the max+1 and handle empty tables
                if (autoIncCol.getSelectName() != null)
                {
                    String colSelectName = autoIncCol.getSelectName();
                    SQLFragment resetSeq = new SQLFragment();
                    resetSeq.append("SELECT setval(\n");
                    resetSeq.append("  pg_get_serial_sequence(").appendValue(t.getSelectName()).append(", ").appendValue(colSelectName).append("),\n");
                    resetSeq.append("  COALESCE((SELECT MAX(").append(colSelectName).append(")+1 FROM ").append(t).append("), 1),\n");
                    resetSeq.append("  false");
                    resetSeq.append(")");
                    new SqlExecutor(_scope, _conn).execute(resetSeq);
                }
            }
        }
    }

    public void setAdhocPropColumns(Set<DomainProperty> adhocPropColumns)
    {
        _adhocPropColumns = adhocPropColumns;
    }

    public void setPreferPKOverObjectUriAsKey(boolean preferPKOverObjectUriAsKey)
    {
        _preferPKOverObjectUriAsKey = preferPKOverObjectUriAsKey;
    }

    public static class NoUpdatableColumnInDataException extends Exception
    {
        public NoUpdatableColumnInDataException(String tableName)
        {
            super("The provided data contains no updatable column for '" + tableName + "'.");
        }
    }
}

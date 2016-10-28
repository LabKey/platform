/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.api.ldk.table;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.SimpleQueryUpdateService;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * User: bimber
 * Date: 4/2/13
 * Time: 2:54 PM
 */
abstract public class AbstractDataDefinedTable extends SimpleUserSchema.SimpleTable
{
    protected String _pk;

    protected String _filterColumn;
    protected String _filterValue;
    protected String _valueColumn;

    public AbstractDataDefinedTable(UserSchema schema, SchemaTableInfo table, String filterColumn, String valueColumn, String tableName, String filterValue)
    {
        super(schema, table);
        _filterColumn = filterColumn;
        _filterValue = filterValue;
        _valueColumn = valueColumn;

        setName(tableName);
        setTitle(tableName);
    }

    public SimpleUserSchema.SimpleTable init()
    {
        super.init();

        ColumnInfo col = getRealTable().getColumn(_filterColumn);
        addCondition(col, _filterValue); //enforce only showing rows from this category

        List<String> pks = getRealTable().getPkColumnNames();
        assert pks.size() > 0;
        _pk = pks.get(0);

        ColumnInfo valueCol = getColumn(_valueColumn);
        assert valueCol != null;

        valueCol.setKeyField(true);
        valueCol.setNullable(false);
        getColumn(_pk).setKeyField(false);

        ColumnInfo filterCol = getColumn(_filterColumn);
        assert filterCol != null;

        removeColumn(filterCol);

        return this;
    }

    protected void addTableURLs()
    {
        setInsertURL(LINK_DISABLER);
        setUpdateURL(LINK_DISABLER);
        setDeleteURL(LINK_DISABLER);
        setImportURL(LINK_DISABLER);
    }

    /**
     * @return A set of the allowable
     */
    protected Set<String> getDistinctValues()
    {
        Set<String> distinctValues = new HashSet<>();

        SimpleFilter filter = new SimpleFilter(FieldKey.fromString(_filterColumn), _filterValue, CompareType.EQUAL);
        TableSelector ts = new TableSelector(_rootTable, Collections.singleton(_valueColumn), filter, null);
        String[] existing = ts.getArray(String.class);
        distinctValues.addAll(Arrays.asList(existing));

        return distinctValues;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new UpdateService(this);
    }

    protected class UpdateService extends SimpleQueryUpdateService
    {
        private ValuesManager _vm;

        public UpdateService(SimpleUserSchema.SimpleTable ti)
        {
            super(ti, ti.getRealTable());

            _vm = new ValuesManager();
        }

        @Override
        protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
        {
            if (!keys.containsKey(_pk) || keys.get(_pk) == null)
            {
                SimpleFilter filter = new SimpleFilter(FieldKey.fromString(_valueColumn), keys.get(_valueColumn), CompareType.EQUAL);
                TableSelector ts = new TableSelector(getQueryTable(), Collections.singleton(_pk), filter, null);
                Object[] results = ts.getArray(Object.class);
                if (results.length == 0)
                    throw new InvalidKeyException("Existing row not found for value: " + keys.get(_valueColumn));
                else if (results.length > 1)
                    throw new InvalidKeyException("More than one existing row found for value: " + keys.get(_valueColumn));

                keys.put(_pk, results[0]);
            }

            return super.getRow(user, container, keys);
        }

        //NOTE: this code should never be called, now that we have migrated to DIB
        @Override
        protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            row.put(_filterColumn, _filterValue);

            String value = (String)row.get(_valueColumn);
            if (value != null && _vm.testIfRowExists(value))
                throw new ValidationException("There is already a record in the table " + getName() + " where " + _valueColumn + " equals " + value);

            return super.insertRow(user, container, row);
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            String oldValue = (String)oldRow.get(_valueColumn);
            String newValue = (String)row.get(_valueColumn);

            if (newValue != null && !oldValue.equals(newValue) && _vm.testIfRowExists(newValue))
                throw new ValidationException("There is already a record in the table " + getName() + " where " + _valueColumn + " equals " + newValue);

            if (!oldValue.equals(newValue))
                _vm.uncacheValue(oldValue);

            row.put(_filterColumn, _filterValue);
            return super.updateRow(user, container, row, oldRow);
        }

        @Override
        protected int truncateRows(User user, Container container) throws QueryUpdateServiceException, SQLException
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString(_filterColumn), _filterValue, CompareType.EQUAL);
            return Table.delete(getDbTable(), filter);
        }

    }

    protected class ValuesManager
    {
        private Set<String> _distinctValues;

        public ValuesManager()
        {
            _distinctValues = getDistinctValues();
        }

        public void uncacheValue(String value)
        {
            _distinctValues.remove(value);
        }

        public boolean testIfRowExists(String value)
        {
            boolean ret = _distinctValues.contains(value);

            if (!_distinctValues.contains(value))
                _distinctValues.add(value);

            return ret;
        }
    }

    @Override
    public DataIteratorBuilder persistRows(DataIteratorBuilder data, DataIteratorContext context)
    {
        data = new _DataIteratorBuilder(data, context);
        return super.persistRows(data, context);
    }

    protected class _DataIteratorBuilder implements DataIteratorBuilder
    {
        DataIteratorContext _context;
        final DataIteratorBuilder _in;

        public _DataIteratorBuilder(@NotNull DataIteratorBuilder in, DataIteratorContext context)
        {
            _context = context;
            _in = in;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            _context = context;
            DataIterator input = _in.getDataIterator(context);
            if (null == input)
                return null;           // Can happen if context has errors

            final SimpleTranslator it = new SimpleTranslator(input, context);
            configureTranslator(input, it);

            return LoggingDataIterator.wrap(it);
        }

        protected void configureTranslator(DataIterator input, final SimpleTranslator it)
        {
            final Map<String, Integer> inputColMap = new HashMap<>();
            for (int idx = 1; idx <= input.getColumnCount(); idx++)
            {
                ColumnInfo col = input.getColumnInfo(idx);
                if (StringUtils.equalsIgnoreCase(_filterColumn, col.getName()))
                {
                    inputColMap.put(_filterColumn, idx);
                    continue;
                }

                if (StringUtils.equalsIgnoreCase(_valueColumn, col.getName()))
                {
                    inputColMap.put(_valueColumn, idx);
                    continue;
                }

                it.addColumn(idx);
            }

            //always set the value for the filterCol
            ColumnInfo incrementCol = getRealTable().getColumn(_filterColumn);
            it.addColumn(incrementCol, new Callable()
            {
                @Override
                public Object call() throws Exception
                {
                    return _filterValue;
                }
            });

            //enforce uniqueness for values
            final ValuesManager vm = new ValuesManager();
            ColumnInfo valueCol = getRealTable().getColumn(_valueColumn);
            it.addColumn(valueCol, new Callable()
            {
                @Override
                public Object call() throws Exception
                {
                    String value = (String)it.getInputColumnValue(inputColMap.get(_valueColumn));
                    if (value == null)
                    {
                        _context.getErrors().addRowError(new ValidationException("Missing value for column: " + _valueColumn));
                    }

                    if (vm.testIfRowExists(value))
                    {
                        _context.getErrors().addRowError(new ValidationException("There is already a record in the table " + getName() + " where " + _valueColumn + " equals " + value));
                    }

                    return value;
                }
            });
        }
    }
}

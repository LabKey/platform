/*
 * Copyright (c) 2011-2026 LabKey Corporation
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
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.triggers.Trigger;
import org.labkey.api.exp.query.ExpTable;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.labkey.api.util.IntegerUtils.asInteger;

public class TriggerDataBuilderHelper
{
    final Container _c;
    final TableInfo _target;
    private final User _user;
    final Map<String,Object> _extraContext;
    final boolean _useImportAliases;
    /** Only fire complete() if we got far enough to fire init() */
    boolean firedInit = false;
    /** Existing records of rows routed to update, held from the before trigger until the after trigger */
    final Map<Integer, Map<String, Object>> _routedExistingRecords = new HashMap<>();
    boolean _routeMerge = false;

    public TriggerDataBuilderHelper(TableInfo target, Container c, User user, Map<String, Object> extraContext, boolean useImportAliases)
    {
        _target = target;
        _c = c;
        _user = user;
        _extraContext = extraContext;
        _useImportAliases = useImportAliases;
    }

    public DataIteratorBuilder before(DataIteratorBuilder in)
    {
        return new Before(in);
    }

    public DataIteratorBuilder after(DataIteratorBuilder in)
    {
        return new After(in);
    }

    abstract class TriggerDataIterator extends WrapperDataIterator
    {
        final DataIteratorContext _context;
        final BatchValidationException _errors;

        protected TriggerDataIterator(DataIterator di, DataIteratorContext context)
        {
            super(DataIteratorUtil.wrapMap(di,true));
            _context = context;
            _errors = context._errors;
        }

        BatchValidationException getErrors()
        {
            return _errors;
        }

        MapDataIterator getInput()
        {
            return (MapDataIterator)_delegate;
        }

        @Override
        public void debugLogInfo(StringBuilder sb)
        {
            sb.append(getDebugName()).append(": ").append(this.getClass().getName()).append("\n");
            if (_target instanceof AbstractTableInfo)
            {
                Collection<Trigger> triggers = ((AbstractTableInfo)_target).getTriggers(_c);
                for (var trigger : triggers)
                {
                    sb.append("  -trigger: ").append(trigger.getName()).append("\n");
                }
            }

            if (null != _delegate)
                _delegate.debugLogInfo(sb);
        }

        protected TableInfo.TriggerType getTriggerType()
        {
            return _context.getInsertOption().updateOnly ? TableInfo.TriggerType.UPDATE : TableInfo.TriggerType.INSERT;
        }

        protected Map<String, Object> getOldRow()
        {
            return _context.getInsertOption().updateOnly ? getExistingRecord() : null;
        }
    }

    class Before implements DataIteratorBuilder
    {
        final DataIteratorBuilder _in;

        Before(DataIteratorBuilder in)
        {
            _in = in;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator di = _in.getDataIterator(context);
            if (di == null)
                return null; // can happen if context has errors

            if (!_target.hasTriggers(_c))
                return di;
            di = LoggingDataIterator.wrap(di);

            QueryUpdateService.InsertOption option = context.getInsertOption();
            boolean routeMerge = option.mergeRows && _target instanceof AbstractTableInfo t && t.isMergeRoutedByExistingRecord(_c);
            _routeMerge = routeMerge;

            // Incorporate columns managed by triggers that may not overlap with the requested column set
            Set<String> managedOnlyColumns = new CaseInsensitiveHashSet();
            di = getManagedColumnsDataIterator(di, context, managedOnlyColumns);

            Set<String> existingRecordKeyColumnNames = null;
            Set<String> sharedKeys = null;
            boolean isMergeOrUpdate = option.allowUpdate;

            if (isMergeOrUpdate && _target instanceof ExpTable<?> expTable)
            {
                existingRecordKeyColumnNames = expTable.getExistingRecordKeyColumnNames(context, DataIteratorUtil.createColumnNameMap(di));
                sharedKeys = expTable.getExistingRecordSharedKeyColumnNames();
            }

            // Rows routed to update keep trigger-managed values the input omits, since beforeUpdate may not set them
            Set<String> carryForwardColumns = routeMerge ? managedOnlyColumns : Set.of();

            // GH Issue 1048: null padding would make MERGE overwrite every column the input omitted
            boolean padMissingColumns = !option.updateOnly && (!option.mergeRows || option.replace);
            di = LoggingDataIterator.wrap(new CoerceDataIterator(di, context, _target, padMissingColumns));
            context.setWithLookupRemapping(false);

            // The import pipeline doesn't expect an existing record from a lookup made only to route triggers
            boolean routingOnlyLookup = false;
            if (routeMerge && existingRecordKeyColumnNames == null)
            {
                MergeKeyColumns keys = getMergeKeyColumns(di, context);
                if (keys != null)
                {
                    di = keys.input();
                    existingRecordKeyColumnNames = keys.columnNames();
                    routingOnlyLookup = true;
                }
            }

            // Skip existing records
            if (!option.allowUpdate || existingRecordKeyColumnNames == null)
                return LoggingDataIterator.wrap(new BeforeIterator(new CachingDataIterator(di), context, Set.of()));

            // Merge request but merge is not supported
            if (option.mergeRows && !_target.supportsInsertOption(QueryUpdateService.InsertOption.MERGE))
                return LoggingDataIterator.wrap(new BeforeIterator(di, context, Set.of()));

            // Rows routed to update get the whole existing row as oldRow
            di = ExistingRecordDataIterator.createBuilder(di, _target, existingRecordKeyColumnNames, sharedKeys, _target instanceof ExpTable, routeMerge).getDataIterator(context);
            DataIterator before = new BeforeIterator(new CachingDataIterator(di), context, carryForwardColumns);
            if (routingOnlyLookup)
                before = new HiddenExistingRecordDataIterator(before);
            return LoggingDataIterator.wrap(before);
        }

        /** Columns that match each input row to the row MERGE will update: the table's computed key, alternate keys, or primary key */
        private @Nullable MergeKeyColumns getMergeKeyColumns(DataIterator di, DataIteratorContext context)
        {
            if (_target instanceof MergeKeyColumns.Provider provider)
                return provider.addMergeKeyColumns(di, context);

            Collection<String> keys = context.getAlternateKeys().isEmpty() ? _target.getPkColumnNames() : context.getAlternateKeys();
            Map<String, Integer> colNameMap = DataIteratorUtil.createColumnNameMap(di);
            if (keys.isEmpty() || !keys.stream().allMatch(colNameMap::containsKey))
                return null;
            return new MergeKeyColumns(di, new CaseInsensitiveHashSet(keys));
        }

        private DataIterator getManagedColumnsDataIterator(DataIterator di, DataIteratorContext context, Set<String> addedColumns)
        {
            if (QueryService.get().isTriggerManagedColumnsEnabled())
            {
                var triggerColumns = _target.getTriggerManagedColumns(_c, context.getInsertOption());
                if (triggerColumns != null && !triggerColumns.isEmpty())
                {
                    Function<String, ColumnInfo> columnMapper;
                    if (_target instanceof AbstractTableInfo target)
                        columnMapper = colName -> target.getColumn(colName, false);
                    else
                        columnMapper = _target::getColumn;

                    var columns = triggerColumns.stream().map(columnMapper).filter(Objects::nonNull).toList();
                    if (!columns.isEmpty())
                    {
                        var translator = new SimpleTranslator(di, context);
                        translator.setDebugName("TriggerDataBuilderHelper.Before.translator");
                        translator.selectAll();

                        var columnNameMap = translator.getColumnNameMap();

                        for (var column : columns)
                        {
                            if (!columnNameMap.containsKey(column.getName()))
                            {
                                translator.addColumn(column, (Supplier<Object>) () -> null);
                                addedColumns.add(column.getName());
                            }
                        }

                        di = translator.getDataIterator(context);
                    }
                }
            }

            return di;
        }
    }

    class BeforeIterator extends TriggerDataIterator
    {
        boolean _firstRow = true;
        Map<String, Object> _currentRow = null;
        final Set<String> _carryForwardColumns;

        BeforeIterator(DataIterator di, DataIteratorContext context, Set<String> carryForwardColumns)
        {
            super(di, context);
            _carryForwardColumns = carryForwardColumns;
        }

        @Override
        public boolean isScrollable()
        {
            // DON'T FIRE TRIGGERS TWICE!
            return false;
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            _currentRow = null;
            TableInfo.TriggerType triggerType = getTriggerType();
            if (_firstRow)
            {
                // HACK?: Before initializing the triggers, reset the table triggers.
                // This is late enough so that the data iteration context is configured properly.
                // Note: This is not ideal. getTriggers(c) accepts a @Nullable container, however, the underlying
                // scripts actually depend on this argument. For example, if the first caller passed in a container
                // and the next calls with null, then the null argument will see the scripts defined in the container.
                // But if they are called in the opposite order (null, then with a container), the container one will
                // receive no scripts (or whatever null initialized with).
                // Ideally, we could decouple loading the triggers and their associated container from the table/trigger
                // lifecycle.
                _target.resetTriggers(_c);

                _target.fireBatchTrigger(_c, _user, triggerType, _context.getInsertOption(), true, getErrors(), _extraContext);
                firedInit = true;
                _firstRow = false;
            }

            while (getInput().next())
            {
                int rowNumber = asInteger(getInput().get(0));
                _currentRow = getInput().getMap();
                Map<String, Object> existingRecord = getExistingRecord();
                carryForward(existingRecord);
                if (_routeMerge && existingRecord != null && !existingRecord.isEmpty())
                    _routedExistingRecords.put(rowNumber, existingRecord);
                try
                {
                    _target.fireRowTrigger(_c, _user, triggerType, _context.getInsertOption(), true, rowNumber, _currentRow, getOldRow(), _extraContext, existingRecord);
                    return true;
                }
                catch (ValidationException vex)
                {
                    getErrors().addRowError(vex.fillIn(_target.getPublicSchemaName(), _target.getName(), _currentRow, rowNumber));
                    _context.checkShouldCancel();
                }
            }

            return false;
        }

        private void carryForward(@Nullable Map<String, Object> existingRecord)
        {
            if (_carryForwardColumns.isEmpty() || existingRecord == null || existingRecord.isEmpty())
                return;

            Map<String, Object> existing = new CaseInsensitiveHashMap<>(existingRecord);
            for (String col : _carryForwardColumns)
            {
                if (existing.containsKey(col))
                    _currentRow.put(col, existing.get(col));
            }
        }

        @Override
        public Object get(int i)
        {
            // TODO THIS IS COMPLETELY INADEQUATE
            String name = getColumnInfo(i).getName();
            if (_currentRow.containsKey(name))
                return _currentRow.get(name);
            return super.get(i);
        }
    }

    /** Hides the existing record column and accessor from the iterators downstream */
    static class HiddenExistingRecordDataIterator extends WrapperDataIterator
    {
        HiddenExistingRecordDataIterator(DataIterator di)
        {
            super(di);
        }

        @Override
        public int getColumnCount()
        {
            int count = _delegate.getColumnCount();
            return ExistingRecordDataIterator.EXISTING_RECORD_COLUMN_NAME.equals(_delegate.getColumnInfo(count).getName()) ? count - 1 : count;
        }

        @Override
        public boolean supportsGetExistingRecord()
        {
            return false;
        }

        @Override
        public Map<String, Object> getExistingRecord()
        {
            return null;
        }
    }

    class After implements DataIteratorBuilder
    {
        final DataIteratorBuilder _in;

        After(DataIteratorBuilder in)
        {
            _in = in;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator di = _in.getDataIterator(context);
            if (di == null)
                return null; // can happen if context has errors

            if (!_target.hasTriggers(_c))
                return di;

            return new AfterIterator(LoggingDataIterator.wrap(di), context);
        }
    }

    class AfterIterator extends TriggerDataIterator
    {
        AfterIterator(DataIterator di, DataIteratorContext context)
        {
            super(di, context);
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = false;
            try
            {
                hasNext = getInput().next();
                if (hasNext)
                {
                    int rowNumber = asInteger(getInput().get(0));
                    Map<String,Object> newRow = getInput().getMap();
                    Map<String, Object> existingRecord = _routedExistingRecords.remove(rowNumber);
                    if (existingRecord == null)
                        existingRecord = getExistingRecord();
                    try
                    {
                        _target.fireRowTrigger(_c, _user, getTriggerType(), _context.getInsertOption(), false, rowNumber, newRow, getOldRow(), _extraContext, existingRecord);
                    }
                    catch (ValidationException vex)
                    {
                        getErrors().addRowError(vex.fillIn(_target.getPublicSchemaName(), _target.getName(), newRow, rowNumber));
                    }
                }
                return hasNext;
            }
            finally
            {
                if (!hasNext && firedInit && !getErrors().hasErrors())
                    _target.fireBatchTrigger(_c, _user, getTriggerType(), _context.getInsertOption(), false, getErrors(), _extraContext);
            }
        }
    }
}

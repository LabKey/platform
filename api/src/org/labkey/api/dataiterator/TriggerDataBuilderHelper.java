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

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.triggers.Trigger;
import org.labkey.api.exp.query.ExpTable;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.admin.FolderImportContext.IS_NEW_FOLDER_IMPORT_KEY;

/**
 * User: matthewb
 * Date: 2011-09-07
 * Time: 5:14 PM
 */
public class TriggerDataBuilderHelper
{
    final Container _c;
    final TableInfo _target;
    private final User _user;
    final Map<String,Object> _extraContext;
    final boolean _useImportAliases;
    /** Only fire complete() if we got far enough to fire init() */
    boolean firedInit = false;

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
        final DataIteratorBuilder _pre;

        Before(DataIteratorBuilder in)
        {
            _pre = in;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator pre = _pre.getDataIterator(context);
            if (!_target.hasTriggers(_c))
                return pre;
            pre = LoggingDataIterator.wrap(pre);

            Set<String> mergeKeys = null;
            if (_target instanceof ExpTable)
                mergeKeys = ((ExpTable<?>)_target).getAltMergeKeys(context);

            boolean isNewFolderImport = false;
            if (_extraContext != null && _extraContext.get(IS_NEW_FOLDER_IMPORT_KEY) != null)
            {
                isNewFolderImport = (boolean) _extraContext.get(IS_NEW_FOLDER_IMPORT_KEY);
            }

            boolean skipExistingRecord = !context.getInsertOption().allowUpdate || mergeKeys == null || isNewFolderImport;
            DataIterator coerce = new CoerceDataIterator(pre, context, _target, !context.getInsertOption().updateOnly);
            coerce = LoggingDataIterator.wrap(coerce);

            if (skipExistingRecord)
                return LoggingDataIterator.wrap(new BeforeIterator(new CachingDataIterator(coerce), context));
            else if (context.getInsertOption().mergeRows && !_target.supportsInsertOption(QueryUpdateService.InsertOption.MERGE))
                return LoggingDataIterator.wrap(new BeforeIterator(coerce, context));

            coerce = ExistingRecordDataIterator.createBuilder(coerce, _target, mergeKeys, true).getDataIterator(context);
            return LoggingDataIterator.wrap(new BeforeIterator(new CachingDataIterator(coerce), context));
        }
    }


    class BeforeIterator extends TriggerDataIterator
    {
        boolean _firstRow = true;
        Map<String,Object> _currentRow = null;

        BeforeIterator(DataIterator di, DataIteratorContext context)
        {
            super(di, context);
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
                _target.fireBatchTrigger(_c, _user, triggerType, true, getErrors(), _extraContext);
                firedInit = true;
                _firstRow = false;
            }

            while (getInput().next())
            {
                int rowNumber = (Integer)getInput().get(0);
                _currentRow = getInput().getMap();
                try
                {
                    _target.fireRowTrigger(_c, _user, triggerType, true, rowNumber, _currentRow, getOldRow(), _extraContext, getExistingRecord());
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


    class After implements DataIteratorBuilder
    {
        final DataIteratorBuilder _post;

        After(DataIteratorBuilder in)
        {
            _post = in;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator it = _post.getDataIterator(context);
            if (!_target.hasTriggers(_c))
                return it;
            return new AfterIterator(LoggingDataIterator.wrap(it), context);
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
                    int rowNumber = (Integer)getInput().get(0);
                    Map<String,Object> newRow = getInput().getMap();
                    try
                    {
                        _target.fireRowTrigger(_c, _user, getTriggerType(), false, rowNumber, newRow, getOldRow(), _extraContext, getExistingRecord());
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
                    _target.fireBatchTrigger(_c, _user, getTriggerType(), false, getErrors(), _extraContext);
            }
        }
    }
}

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

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;

import java.util.Map;

/**
 * User: matthewb
 * Date: 2011-09-07
 * Time: 5:14 PM
 */
public class TriggerDataBuilderHelper
{
    final Container _c;
    final TableInfo _target;
    final Map<String,Object> _extraContext;
    final boolean _useImportAliases;
    /** Only fire complete() if we got far enough to fire init() */
    boolean firedInit = false;

    public TriggerDataBuilderHelper(TableInfo target, Container c, Map<String,Object> extraContext, boolean useImportAliases)
    {
        _target = target;
        _c = c;
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
            DataIterator coerce = new CoerceDataIterator(pre, context, _target);
            coerce = LoggingDataIterator.wrap(coerce);
            return LoggingDataIterator.wrap(new BeforeIterator(coerce, context));
        }
    }


    class BeforeIterator extends WrapperDataIterator
    {
        final DataIteratorContext _context;
        final BatchValidationException _errors;
        boolean _firstRow = true;
        Map<String,Object> _currentRow = null;

        BeforeIterator(DataIterator di, DataIteratorContext context)
        {
            super(DataIteratorUtil.wrapMap(di,true));
            _context = context;
            _errors = context._errors;
        }

        @Override
        public boolean isScrollable()
        {
            // DON'T FIRE TRIGGERS TWICE!
            return false;
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
        public boolean next() throws BatchValidationException
        {
            _currentRow = null;

            if (_firstRow)
            {
                _target.fireBatchTrigger(_c, TableInfo.TriggerType.INSERT, true, getErrors(), _extraContext);
                firedInit = true;
                _firstRow = false;
            }

            while (getInput().next())
            {
                int rowNumber = (Integer)getInput().get(0);
                _currentRow = getInput().getMap();
                try
                {
                    _target.fireRowTrigger(_c, TableInfo.TriggerType.INSERT, true, rowNumber, _currentRow, null, _extraContext);
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


    class AfterIterator extends WrapperDataIterator
    {
        final DataIteratorContext _context;
        final BatchValidationException _errors;

        AfterIterator(DataIterator di, DataIteratorContext context)
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
                        _target.fireRowTrigger(_c, TableInfo.TriggerType.INSERT, false, rowNumber, newRow, null, _extraContext);
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
                    _target.fireBatchTrigger(_c, TableInfo.TriggerType.INSERT, false, getErrors(), _extraContext);
            }
        }
    }
}

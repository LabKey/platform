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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;

import java.io.IOException;

/**
 * This slightly silly class lets you postpone errors encountered during construction of a iterator until
 * next() is called.
 *
 * Two cases:
 *  - return error on first call to next()
 *  - return error on first call to next IF input iterator has at least one row
 *
 * User: matthewb
 * Date: 2011-06-03
 */
public class ErrorIterator extends AbstractDataIterator
{
    DataIterator _it;
    boolean _errorIfEmpty;
    ValidationException _error;

    public static DataIterator wrap(DataIterator di, DataIteratorContext context, boolean errorEvenIfEmpty, ValidationException x)
    {
        if (null == x || !x.hasErrors())
            return di;
        return new ErrorIterator(di, context, errorEvenIfEmpty, x);
    }

    ErrorIterator(DataIterator di, DataIteratorContext context, boolean errorEvenIfEmpty, ValidationException x)
    {
        super(context);
        this._it = di;
        this._errorIfEmpty = errorEvenIfEmpty;
        this._error = x;
    }

    @Override
    public int getColumnCount()
    {
        return _it.getColumnCount();
    }

    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        return _it.getColumnInfo(i);
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        boolean hasNext = _it.next();
        if (null != _error && (hasNext || _errorIfEmpty))
        {
            getGlobalError().addErrors(_error);
            _error = null;
            return false;
        }
        checkShouldCancel();
        return hasNext;
    }

    @Override
    public Object get(int i)
    {
        return _it.get(i);
    }

    @Override
    public void close() throws IOException
    {
        _it.close();
    }
}

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

package org.labkey.api.dataiterator;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;

import java.util.HashSet;
import java.util.Set;

/**
 * User: matthewb
 * Date: 2011-05-26
 * Time: 2:48 PM
 */
public abstract class AbstractDataIterator implements DataIterator
{
    private String _debugName = "";
    private ValidationException _globalError = null;
    final DataIteratorContext _context;
    final BatchValidationException _errors;
    protected ValidationException _rowError = null;

    protected AbstractDataIterator(DataIteratorContext context)
    {
        _context = context;
        _errors = null==context ? null : context._errors;
    }

    public void setDebugName(String name)
    {
        _debugName = name;
    }


    @Override
    public String getDebugName()
    {
        return StringUtils.defaultString(_debugName, getClass().getSimpleName());
    }


    @Override
    public boolean isConstant(int i)
    {
        return false;
    }


    @Override
    public Object getConstantValue(int i)
    {
        return null;
    }


    protected boolean hasErrors()
    {
        return _errors.hasErrors();
    }


    protected ValidationException getGlobalError()
    {
        if (null == _globalError)
        {
            _globalError = new ValidationException();
            _globalError.setRowNumber(-1);
            _errors.addRowError(_globalError);
        }
        return _globalError;
    }

    protected ValidationException getRowError()
    {
        int row = (Integer)this.get(0);
        if (null == _rowError)
            _rowError = _errors.getLastRowError();
        if (null == _rowError || row != _rowError.getRowNumber())
        {
            _rowError = new ValidationException();
            _rowError.setRowNumber(row);
            _errors.addRowError(_rowError);
        }
        return _rowError;
    }


    Set<String> errorFields = new HashSet<>();

    protected void addFieldError(String field, String msg)
    {
        if (_context.isVerbose() || errorFields.add(field))
            getRowError().addFieldError(field, msg);
    }

    protected void addRowError(String message)
    {
        getRowError().addGlobalError(message);
    }


    protected void checkShouldCancel() throws BatchValidationException
    {
        _context.checkShouldCancel();
    }

    protected boolean preserveEmptyString()
    {
        return _context.getConfigParameterBoolean(QueryUpdateService.ConfigParameters.PreserveEmptyString);
    }
}

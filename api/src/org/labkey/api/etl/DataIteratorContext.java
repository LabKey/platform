/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.api.etl;

import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryUpdateService;
import static org.labkey.api.query.QueryUpdateService.InsertOption.*;

/**
 * User: matthewb
 * Date: 2012-08-27
 * Time: 5:58 PM
 */
public class DataIteratorContext
{
    /*
      NOTE: DIC is not really meant to be a set up parameter block
      targetOption and selectIds should probably be moved out in a future
      refactor
     */
    QueryUpdateService.InsertOption _insertOption = INSERT;
    Boolean _selectIds = null;


    final BatchValidationException _errors;
    boolean _failFast = true;
    boolean _verbose = false;   // allow more than one error per field (across all rows)
    int _maxRowErrors = 1;

    public DataIteratorContext()
    {
        _errors = new BatchValidationException();
    }

    public DataIteratorContext(BatchValidationException errors)
    {
        _errors = errors;
    }

    public QueryUpdateService.InsertOption getInsertOption()
    {
        return _insertOption;
    }

    public void setInsertOption(QueryUpdateService.InsertOption targetOption)
    {
        _insertOption = targetOption;
    }

    public Boolean getSelectIds()
    {
        return _selectIds;
    }

    public void setSelectIds(Boolean selectIds)
    {
        _selectIds = selectIds;
    }

    public boolean isFailFast()
    {
        return _failFast;
    }

    public void setFailFast(boolean failFast)
    {
        _failFast = failFast;
        if (!_failFast && _maxRowErrors == 1)
            _maxRowErrors = 1000;
    }

    public int getMaxRowErrors()
    {
        return _maxRowErrors;
    }

    public void setMaxRowErrors(int maxRowErrors)
    {
        _maxRowErrors = maxRowErrors;
    }

    public boolean isVerbose()
    {
        return _verbose;
    }

    public void setVerbose(boolean verbose)
    {
        _verbose = verbose;
    }

    public BatchValidationException getErrors()
    {
        return _errors;
    }

    /** if this etl should be killed, will execute <code>throw _errors;</code> */
    public void checkShouldCancel() throws BatchValidationException
    {
        if (!_errors.hasErrors())
            return;
        if (_failFast || _errors.getRowErrors().size() > _maxRowErrors)
            throw _errors;
    }
}

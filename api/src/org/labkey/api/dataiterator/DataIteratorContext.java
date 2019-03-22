/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryUpdateService;

import java.util.Map;
import java.util.Set;

import static org.labkey.api.query.QueryUpdateService.InsertOption.INSERT;

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
    boolean _verbose = false;
    boolean _supportAutoIncrementKey = false;
    boolean _allowImportLookupByAlternateKey = false;
    private final Set<String> _passThroughBuiltInColumnNames = new CaseInsensitiveHashSet();
    private final Set<String> _dontUpdateColumnNames = new CaseInsensitiveHashSet();
    private final Set<String> _alternateKeys = new CaseInsensitiveHashSet();

    int _maxRowErrors = 1;

    private Map<Enum, Object> _configParameters;

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

    /** When true, allow more than one error per field (across all rows) */
    public void setVerbose(boolean verbose)
    {
        _verbose = verbose;
    }

    public BatchValidationException getErrors()
    {
        return _errors;
    }

    public boolean supportsAutoIncrementKey()
    {
        return _supportAutoIncrementKey;
    }

    /**
     * When true, iterators will treat an auto-incremented key as a normal provided key
     * making it the responsibility of the Context creator to manage auto-incrementing.
     */
    public void setSupportAutoIncrementKey(boolean supportAutoIncrementKey)
    {
        _supportAutoIncrementKey = supportAutoIncrementKey;
    }

    public boolean isAllowImportLookupByAlternateKey()
    {
        return _allowImportLookupByAlternateKey;
    }

    /** When true, allow importing lookup columns by the lookup table's alternate key instead of by primary key. */
    public void setAllowImportLookupByAlternateKey(boolean allowImportLookupByAlternateKey)
    {
        _allowImportLookupByAlternateKey = allowImportLookupByAlternateKey;
    }

    /** Normally all built in columns (created, createdBy, etc) are populated with newly calculated values on writing to target.
     * This list specifies those which should pass through from source.
     */
    public Set<String> getPassThroughBuiltInColumnNames()
    {
        return _passThroughBuiltInColumnNames;
    }

    public Set<String> getDontUpdateColumnNames()
    {
        return _dontUpdateColumnNames;
    }

    public Set<String> getAlternateKeys()
    {
        return _alternateKeys;
    }

    /** if this etl should be killed, will execute <code>throw _errors;</code> */
    public void checkShouldCancel() throws BatchValidationException
    {
        if (!_errors.hasErrors())
            return;
        if (_failFast || _errors.getRowErrors().size() > _maxRowErrors)
            throw _errors;
    }

    /** Extra parameters associated with the DataIterator sequence. */
    public void setConfigParameters(Map<Enum, Object> configParameters)
    {
        _configParameters = configParameters;
    }

    @Nullable
    public Map<Enum, Object> getConfigParameters()
    {
        return _configParameters;
    }

    @Nullable
    public Object getConfigParameter(Enum key)
    {
        Object param = null;
        if (null != getConfigParameters())
            param = getConfigParameters().get(key);
        return param;
    }

    public boolean getConfigParameterBoolean(Enum key)
    {
        return Boolean.TRUE == getConfigParameter(key);
    }
}

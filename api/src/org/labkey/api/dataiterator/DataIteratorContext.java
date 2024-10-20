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

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryImportPipelineJob;
import org.labkey.api.query.QueryUpdateService;

import java.util.HashMap;
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
    QueryImportPipelineJob _backgroundJob = null;
    boolean _crossTypeImport = false;
    boolean _crossFolderImport = false;
    boolean _allowCreateStorage = false;
    boolean _useTransactionAuditCache = false;
    private final Set<String> _passThroughBuiltInColumnNames = new CaseInsensitiveHashSet();
    private final Set<String> _dontUpdateColumnNames = new CaseInsensitiveHashSet();
    private final Set<String> _alternateKeys = new CaseInsensitiveHashSet();
    private String _dataSource;

    private final Map<String, Object> _responseInfo = new HashMap<>(); // information from the import/loadRows context to be passed back to the API response object
    private Logger _logger;

    int _maxRowErrors = 1;

    @NotNull
    private final Map<Enum, Object> _configParameters = new HashMap<>();

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

    @Nullable
    public String getDataSource()
    {
        return _dataSource;
    }

    public void setDataSource(@Nullable String dataSource)
    {
        _dataSource = dataSource;
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

    public boolean isCrossTypeImport()
    {
        return _crossTypeImport;
    }

    /** when true, allows import of files with data for multiple types in the same base schema (e.g., exp.material) */
    public void setCrossTypeImport(boolean crossTypeImport)
    {
        _crossTypeImport = crossTypeImport;
    }

    public boolean isCrossFolderImport()
    {
        return _crossFolderImport;
    }

    public void setCrossFolderImport(boolean crossFolderImport)
    {
        _crossFolderImport = crossFolderImport;
    }

    public boolean isAllowCreateStorage()
    {
        return _allowCreateStorage;
    }

    public void setAllowCreateStorage(boolean allowCreateStorage)
    {
        _allowCreateStorage = allowCreateStorage;
    }

    public boolean isUseTransactionAuditCache()
    {
        return _useTransactionAuditCache;
    }

    public void setUseTransactionAuditCache(boolean useTransactionAuditCache)
    {
        _useTransactionAuditCache = useTransactionAuditCache;
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

    @NotNull
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
        if (null == configParameters)
            _configParameters.clear();
        else
            _configParameters.putAll(configParameters);
    }

    public void putConfigParameter(Enum key, Object value)
    {
        _configParameters.put(key, value);
    }


    @NotNull
    public Map<Enum, Object> getConfigParameters()
    {
        return _configParameters;
    }

    @Nullable
    public Object getConfigParameter(Enum key)
    {
        return getConfigParameters().get(key);
    }

    public boolean getConfigParameterBoolean(Enum key)
    {
        return Boolean.TRUE == getConfigParameter(key);
    }

    public Map<String, Object> getResponseInfo()
    {
        return _responseInfo;
    }

    public void putResponseInfo(String key, Object value)
    {
        _responseInfo.put(key, value);
    }

    public Logger getLogger()
    {
        return _logger;
    }

    public void setLogger(Logger logger)
    {
        _logger = logger;
    }

    public boolean isBackgroundJob()
    {
        return _backgroundJob != null;
    }

    public void setBackgroundJob(QueryImportPipelineJob job)
    {
        _backgroundJob = job;
    }

    public QueryImportPipelineJob getBackgroundJob()
    {
        return _backgroundJob;
    }

}

/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
package org.labkey.api.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * A simple wrapper around another QueryUpdateService. All of the real work is delegated through, but
 * this class is responsible for clearing a cache in a subclass-specific way
 * after a database change has happened.
 *
 * User: jeckels
 * Date: 10/31/2014
 */
public abstract class CacheClearingQueryUpdateService implements QueryUpdateService
{
    private final QueryUpdateService _service;

    public CacheClearingQueryUpdateService(QueryUpdateService service)
    {
        _service = service;
    }

    @Override
    public List<Map<String, Object>> getRows(User user, Container container, List<Map<String, Object>> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        return _service.getRows(user, container, keys);
    }

    @Override
    public int loadRows(User user, Container container, DataIteratorBuilder rows, DataIteratorContext context, @Nullable Map<String, Object> extraScriptContext) throws SQLException
    {
        int ret = _service.loadRows(user, container, rows, context, extraScriptContext);
        clearCache();
        return ret;
    }

    @Override
    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext) throws DuplicateKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = _service.insertRows(user, container, rows, errors, configParameters, extraScriptContext);
        clearCache();
        return result;
    }

    @Override
    public int importRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext) throws SQLException
    {
        int result = _service.importRows(user, container, rows, errors, configParameters, extraScriptContext);
        clearCache();
        return result;
    }

    @Override
    public int mergeRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext) throws SQLException
    {
        int result = _service.mergeRows(user, container, rows, errors, configParameters, extraScriptContext);
        clearCache();
        return result;
    }

    @Override
    public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = _service.updateRows(user, container, rows, oldKeys, configParameters, extraScriptContext);
        clearCache();
        return result;
    }

    @Override
    public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = _service.deleteRows(user, container, keys, configParameters, extraScriptContext);
        clearCache();
        return result;
    }

    @Override
    public int truncateRows(User user, Container container, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext) throws BatchValidationException, QueryUpdateServiceException, SQLException
    {
        int result = _service.truncateRows(user, container, configParameters, extraScriptContext);
        clearCache();
        return result;
    }

    /**
     * Clear the cache after some sort of database change has happened
     */
    protected abstract void clearCache();

    @Override
    public void setBulkLoad(boolean bulkLoad)
    {
        _service.setBulkLoad(bulkLoad);
    }

    @Override
    public boolean isBulkLoad()
    {
        return _service.isBulkLoad();
    }
}

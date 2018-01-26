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
package org.labkey.api.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * User: kevink
 * Date: 5/18/13
 */
public class ExtendedTableUpdateService extends SimpleQueryUpdateService
{
    private final AbstractQueryUpdateService _baseTableUpdateService;

    public ExtendedTableUpdateService(SimpleUserSchema.SimpleTable queryTable, TableInfo dbTable, AbstractQueryUpdateService baseQUS)
    {
        super(queryTable, dbTable);
        _baseTableUpdateService = baseQUS;
    }

    protected AbstractQueryUpdateService getBaseTableUpdateService()
    {
        return _baseTableUpdateService;
    }

    @Override
    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        Map<String, Object> row = super.getRow(user, container, keys);
        if (null != row)
        {
            row.putAll(_baseTableUpdateService.getRow(user, container, keys));
        }
        return row;
    }

    @Override
    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext) throws DuplicateKeyException, QueryUpdateServiceException, SQLException
    {
        return super.insertRows(user, container, rows, errors, configParameters, extraScriptContext);
    }

    @Override
    protected Map<String, Object> _insert(User user, Container c, Map<String, Object> row) throws SQLException, ValidationException
    {
        return super._insert(user, c, row);
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        Map<String, Object> updatedRow = super.updateRow(user, container, row, oldRow);
        try
        {
            _baseTableUpdateService.updateRows(user, container, Arrays.asList(row), Arrays.asList(oldRow), null, null);
        }
        catch (BatchValidationException e)
        {
            throw new QueryUpdateServiceException(e);
        }
        return updatedRow;
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws QueryUpdateServiceException, SQLException, InvalidKeyException
    {
        // Delete this extended table record before deleting the parent table record.
        Map<String, Object> row = super.deleteRow(user, container, oldRowMap);
        try
        {
            _baseTableUpdateService.deleteRows(user, container, Arrays.asList(oldRowMap), null, null);
        }
        catch (BatchValidationException e)
        {
            throw new QueryUpdateServiceException(e);
        }
        return row;
    }

    @Override
    public int truncateRows(User user, Container container, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext)
    {
        throw new UnsupportedOperationException("truncate is not supported for all tables");
    }

    @Override
    public DataIteratorBuilder createImportDIB(User user, Container container, DataIteratorBuilder data, DataIteratorContext context)
    {
        return super.createImportDIB(user, container, data, context);
    }
}

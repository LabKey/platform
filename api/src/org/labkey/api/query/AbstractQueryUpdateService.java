/*
 * Copyright (c) 2010 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.*;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;

import java.sql.SQLException;
import java.util.*;

/**
 * User: jeckels
 * Date: Apr 23, 2010
 */
public abstract class AbstractQueryUpdateService implements QueryUpdateService
{
    private TableInfo _queryTable = null;

    protected AbstractQueryUpdateService(TableInfo queryTable)
    {
        if (queryTable == null)
            throw new IllegalArgumentException();
        _queryTable = queryTable;
    }

    protected TableInfo getQueryTable()
    {
        return _queryTable;
    }

    protected boolean hasPermission(User user, Class<? extends Permission> acl)
    {
        return getQueryTable().hasPermission(user, acl);
    }

    protected abstract Map<String, Object> getRow(User user, Container container, Map<String, Object> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException;

    public List<Map<String, Object>> getRows(User user, Container container, List<Map<String, Object>> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        if (!hasPermission(user, ReadPermission.class))
            throw new UnauthorizedException("You do not have permission to read data from this table.");

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> rowKeys : keys)
        {
            Map<String, Object> row = getRow(user, container, rowKeys);
            if (row != null)
                result.add(row);
        }
        return result;
    }

    protected abstract Map<String, Object> insertRow(User user, Container container, Map<String, Object> row, Map<String, String> rowErrors)
        throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException;

    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        if (!hasPermission(user, InsertPermission.class))
            throw new UnauthorizedException("You do not have permission to insert data into this table.");

        List<Map<String, String>> errors = new ArrayList<Map<String, String>>();
        getQueryTable().fireBatchTrigger(TableInfo.TriggerType.INSERT, true, rows.size(), errors);

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(rows.size());
        for (int i = 0; i < rows.size(); i++)
        {
            Map<String, Object> row = rows.get(i);
            Map<String, String> rowErrors = new HashMap<String, String>();
            if (!getQueryTable().fireRowTrigger(TableInfo.TriggerType.INSERT, true, i, row, null, errors))
                continue;

            row = insertRow(user, container, row, rowErrors);
            if (row == null || !rowErrors.isEmpty())
            {
                addError(errors, rowErrors, i, "failed to insert");
                continue;
            }

            if (!getQueryTable().fireRowTrigger(TableInfo.TriggerType.INSERT, false, i, row, null, errors))
                continue;

            result.add(row);
        }

        getQueryTable().fireBatchTrigger(TableInfo.TriggerType.INSERT, false, rows.size(), errors);

        return result;
    }

    protected abstract Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, Map<String, Object> oldRow, Map<String, String> rowErrors)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException;

    public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        if (!hasPermission(user, UpdatePermission.class))
            throw new UnauthorizedException("You do not have permission to update data in this table.");

        if (oldKeys != null && rows.size() != oldKeys.size())
            throw new IllegalArgumentException("rows and oldKeys are required to be the same length, but were " + rows.size() + " and " + oldKeys + " in length, respectively");

        List<Map<String, String>> errors = new ArrayList<Map<String, String>>();
        getQueryTable().fireBatchTrigger(TableInfo.TriggerType.UPDATE, true, rows.size(), errors);

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(rows.size());
        for (int i = 0; i < rows.size(); i++)
        {
            Map<String, Object> row = rows.get(i);
            Map<String, Object> oldKey = oldKeys == null ? row : oldKeys.get(i);
            Map<String, Object> oldRow = getRow(user, container, oldKey);
            if (oldRow == null)
                throw new NotFoundException("The existing row was not found.");

            Map<String, String> rowErrors = new HashMap<String, String>();
            if (!getQueryTable().fireRowTrigger(TableInfo.TriggerType.UPDATE, true, i, row, oldRow, errors))
                continue;

            Map<String, Object> updatedRow = updateRow(user, container, row, oldRow, rowErrors);
            if (updatedRow == null || !errors.isEmpty())
            {
                addError(errors, rowErrors, i, "failed to update");
                continue;
            }

            if (!getQueryTable().fireRowTrigger(TableInfo.TriggerType.UPDATE, false, i, updatedRow, oldRow, errors))
                continue;

            result.add(updatedRow);
        }

        getQueryTable().fireBatchTrigger(TableInfo.TriggerType.UPDATE, false, rows.size(), errors);

        return result;
    }

    protected abstract Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow, Map<String, String> rowErrors)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException;
    
    public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        if (!hasPermission(user, DeletePermission.class))
            throw new UnauthorizedException("You do not have permission to delete data from this table.");

        List<Map<String, String>> errors = new ArrayList<Map<String, String>>();
        getQueryTable().fireBatchTrigger(TableInfo.TriggerType.DELETE, true, keys.size(), errors);

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(keys.size());
        for (int i = 0; i < keys.size(); i++)
        {
            Map<String, Object> key = keys.get(i);
            Map<String, Object> oldRow = getRow(user, container, key);
            // if row doesn't exist, bail early
            if (oldRow == null)
                continue;

            if (!getQueryTable().fireRowTrigger(TableInfo.TriggerType.DELETE, true, i, null, oldRow, errors))
                continue;

            Map<String, String> rowErrors = new HashMap<String, String>();
            Map<String, Object> updatedRow = deleteRow(user, container, oldRow, rowErrors);
            if (updatedRow == null || !rowErrors.isEmpty())
            {
                addError(errors, rowErrors, i, "failed to delete");
                continue;
            }

            if (!getQueryTable().fireRowTrigger(TableInfo.TriggerType.DELETE, false, i, null, updatedRow, errors))
                continue;

            result.add(updatedRow);
        }

        getQueryTable().fireBatchTrigger(TableInfo.TriggerType.DELETE, false, keys.size(), errors);

        return result;
    }

    protected void addError(List<Map<String, String>> allErrors, Map rowErrors, int i, String msg)
    {
        if (rowErrors.isEmpty())
            rowErrors.put(null, msg);
        rowErrors.put(ValidationException.ERROR_ROW_NUMBER_KEY, i);
        allErrors.add(rowErrors);
    }

    // converts the List of error Maps into a ValidationException
    protected void throwValidationException(List<Map<String, String>> errors, int rowCount) throws ValidationException
    {
        ValidationException.throwValidationException(errors, rowCount > 1);
    }

}

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

    protected abstract Map<String, Object> insertRow(User user, Container container, Map<String, Object> row)
        throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException;

    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows)
            throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        if (!hasPermission(user, InsertPermission.class))
            throw new UnauthorizedException("You do not have permission to insert data into this table.");

        ValidationException errors = new ValidationException();
        getQueryTable().fireBatchTrigger(TableInfo.TriggerType.INSERT, true, errors);

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(rows.size());
        for (int i = 0; i < rows.size(); i++)
        {
            try
            {
                Map<String, Object> row = rows.get(i);
                getQueryTable().fireRowTrigger(TableInfo.TriggerType.INSERT, true, i, row, null);
                row = insertRow(user, container, row);
                if (row == null)
                    continue;

                getQueryTable().fireRowTrigger(TableInfo.TriggerType.INSERT, false, i, row, null);
                result.add(row);
            }
            catch (ValidationException vex)
            {
                errors.addNested(vex);
            }
        }

        getQueryTable().fireBatchTrigger(TableInfo.TriggerType.INSERT, false, errors);

        return result;
    }

    protected abstract Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, Map<String, Object> oldRow)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException;

    public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        if (!hasPermission(user, UpdatePermission.class))
            throw new UnauthorizedException("You do not have permission to update data in this table.");

        if (oldKeys != null && rows.size() != oldKeys.size())
            throw new IllegalArgumentException("rows and oldKeys are required to be the same length, but were " + rows.size() + " and " + oldKeys + " in length, respectively");

        ValidationException errors = new ValidationException();
        getQueryTable().fireBatchTrigger(TableInfo.TriggerType.UPDATE, true, errors);

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(rows.size());
        for (int i = 0; i < rows.size(); i++)
        {
            try
            {
                Map<String, Object> row = rows.get(i);
                Map<String, Object> oldKey = oldKeys == null ? row : oldKeys.get(i);
                Map<String, Object> oldRow = getRow(user, container, oldKey);
                if (oldRow == null)
                    throw new NotFoundException("The existing row was not found.");

                getQueryTable().fireRowTrigger(TableInfo.TriggerType.UPDATE, true, i, row, oldRow);
                Map<String, Object> updatedRow = updateRow(user, container, row, oldRow);
                if (updatedRow == null)
                    continue;

                getQueryTable().fireRowTrigger(TableInfo.TriggerType.UPDATE, false, i, updatedRow, oldRow);
                result.add(updatedRow);
            }
            catch (ValidationException vex)
            {
                errors.addNested(vex);
            }
        }

        getQueryTable().fireBatchTrigger(TableInfo.TriggerType.UPDATE, false, errors);

        return result;
    }

    protected abstract Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException;
    
    public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        if (!hasPermission(user, DeletePermission.class))
            throw new UnauthorizedException("You do not have permission to delete data from this table.");

        ValidationException errors = new ValidationException();
        getQueryTable().fireBatchTrigger(TableInfo.TriggerType.DELETE, true, errors);

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(keys.size());
        for (int i = 0; i < keys.size(); i++)
        {
            try
            {
                Map<String, Object> key = keys.get(i);
                Map<String, Object> oldRow = getRow(user, container, key);
                // if row doesn't exist, bail early
                if (oldRow == null)
                    continue;

                getQueryTable().fireRowTrigger(TableInfo.TriggerType.DELETE, true, i, null, oldRow);
                Map<String, Object> updatedRow = deleteRow(user, container, oldRow);
                if (updatedRow == null)
                    continue;

                getQueryTable().fireRowTrigger(TableInfo.TriggerType.DELETE, false, i, null, updatedRow);
                result.add(updatedRow);
            }
            catch (ValidationException vex)
            {
                errors.addNested(vex);
            }
        }

        getQueryTable().fireBatchTrigger(TableInfo.TriggerType.DELETE, false, errors);

        return result;
    }

}

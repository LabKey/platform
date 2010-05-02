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

    public abstract Map<String, Object> getRow(User user, Container container, Map<String, Object> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException;

    public List<Map<String, Object>> getRows(User user, Container container, List<Map<String, Object>> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> rowKeys : keys)
        {
            Map<String, Object> row = getRow(user, container, rowKeys);
            if (row != null)
            {
                result.add(row);
            }
        }
        return result;
    }

    public abstract Map<String, Object> insertRow(User user, Container container, Map<String, Object> row)
            throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException;

    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        Map<Integer, Map<String, String>> errors = new LinkedHashMap<Integer, Map<String, String>>();
        if (!getQueryTable().fireBatchTrigger(TableInfo.TriggerType.INSERT, true, rows, errors))
            throwValidationException(rows.size(), errors);

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(rows.size());
        for (int i = 0; i < rows.size(); i++)
        {
            Map<String, Object> row = rows.get(i);
            Map<String, String> rowErrors = new HashMap<String, String>();
            if (!getQueryTable().fireRowTrigger(TableInfo.TriggerType.INSERT, true, null, row, rowErrors))
            {
                errors.put(i, rowErrors);
                continue;
            }

            row = insertRow(user, container, row);
            if (row == null)
                continue;

            if (!getQueryTable().fireRowTrigger(TableInfo.TriggerType.INSERT, false, null, row, rowErrors))
            {
                errors.put(i, rowErrors);
                continue;
            }

            result.add(row);
        }

        if (!errors.isEmpty())
            throwValidationException(rows.size(), errors);

        if (!getQueryTable().fireBatchTrigger(TableInfo.TriggerType.INSERT, false, result, errors))
            throwValidationException(rows.size(), errors);

        return result;
    }

    public abstract Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, Map<String, Object> oldKeys)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException;

    public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        if (oldKeys != null && rows.size() != oldKeys.size())
        {
            throw new IllegalArgumentException("rows and oldKeys are required to be the same length, but were " + rows.size() + " and " + oldKeys + " in length, respectively");
        }

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(rows.size());
        for (int i = 0; i < rows.size(); i++)
        {
            Map<String, Object> updatedRow = updateRow(user, container, rows.get(i), oldKeys == null ? null : oldKeys.get(i));
            if (updatedRow != null)
            {
                result.add(updatedRow);
            }
        }
        return result;
    }

    public abstract Map<String, Object> deleteRow(User user, Container container, Map<String, Object> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException;
    
    public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(keys.size());
        for (Map<String, Object> key : keys)
        {
            Map<String, Object> updatedRow = deleteRow(user, container, key);
            if (updatedRow != null)
            {
                result.add(updatedRow);
            }
        }
        return result;
    }


    protected void throwValidationException(int rowCount, Map<Integer, Map<String, String>> errors) throws ValidationException
    {
        List<ValidationError> list = new ArrayList<ValidationError>(errors.size());
        for (Map.Entry<Integer, Map<String, String>> entry : errors.entrySet())
        {
            int row = entry.getKey() == null ? -1 : entry.getKey().intValue();
            Map<String, String> rowErrors = entry.getValue();
            for (Map.Entry<String, String> fields : rowErrors.entrySet())
            {
                String property = fields.getKey();
                StringBuilder message = new StringBuilder();
                if (rowCount > 1 && row > -1)
                    message.append("Row ").append(row).append(" has error: ");

                if (property != null)
                    message.append(String.valueOf(property)).append(": ");

                message.append(fields.getValue());

                if (property != null)
                    list.add(new PropertyValidationError(message.toString(), property));
                else
                    list.add(new SimpleValidationError(message.toString()));
            }
        }
        throw new ValidationException(list);
    }

}

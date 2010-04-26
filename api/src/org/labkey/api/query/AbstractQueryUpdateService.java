package org.labkey.api.query;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User: jeckels
 * Date: Apr 23, 2010
 */
public abstract class AbstractQueryUpdateService implements QueryUpdateService
{
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
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(rows.size());
        for (Map<String, Object> row : rows)
        {
            Map<String, Object> updatedRow = insertRow(user, container, row);
            if (updatedRow != null)
            {
                result.add(updatedRow);
            }
        }
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
}

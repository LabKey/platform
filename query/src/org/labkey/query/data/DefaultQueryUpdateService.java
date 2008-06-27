/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.query.data;

import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.Table;
import org.labkey.api.data.ColumnInfo;
import org.apache.commons.beanutils.ConvertUtils;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.Date;
import java.sql.SQLException;

/*
* User: Dave
* Date: Jun 18, 2008
* Time: 11:17:16 AM
*/
public class DefaultQueryUpdateService implements QueryUpdateService
{
    public TableInfo _table = null;

    public DefaultQueryUpdateService(TableInfo table)
    {
        _table = table;
    }

    protected TableInfo getTable()
    {
        return _table;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        Map<String,Object> row = ((Map<String,Object>)Table.selectObject(_table, getKeys(keys), Map.class));

        //PostgreSQL includes a column named _row for the row index, but since this is selecting by
        //primary key, it will always be 1, which is not only uncessary, but confusing, so strip it
        if(null != row)
            row.remove("_row");
        
        return row;
    }

    public Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        convertTypes(row);
        return Table.insert(user, getTable(), row);
    }

    public Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, Map<String, Object> oldKeys) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        //when updating a row, we should strip the following fields, as they are
        //automagically maintained by the table layer, and should not be allowed
        //to change once the record exists.
        //unfortunately, the Table.update() method doesn't strip these, so we'll
        //do that here.
        // Owner, CreatedBy, Created, EntityId
        Map<String,Object> rowStripped = new HashMap<String,Object>(row.size());
        for(String key : row.keySet())
        {
            if(0 != key.compareToIgnoreCase("Owner")
            && 0 != key.compareToIgnoreCase("CreatedBy")
            && 0 != key.compareToIgnoreCase("Created")
            && 0 != key.compareToIgnoreCase("EntityId"))
                rowStripped.put(key, row.get(key));
        }

        convertTypes(rowStripped);
        Map<String,Object> updatedRow = Table.update(user, getTable(), rowStripped, null == oldKeys ? getKeys(row) : getKeys(oldKeys), null);

        //when passing a map for the row, the Table layer returns the map of fields it updated, which excludes
        //the primary key columns as well as those marked read-only. So we can't simply return the map returned
        //from Table.update(). Instead, we need to copy values from updatedRow into row and return that.
        row.putAll(updatedRow);
        return row;
    }

    public Map<String, Object> deleteRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        Table.delete(getTable(), getKeys(keys), null);
        return keys;
    }

    protected Object[] getKeys(Map<String, Object> map) throws InvalidKeyException
    {
        //build an array of pk values based on the table info
        TableInfo table = getTable();
        List<ColumnInfo> pks = table.getPkColumns();
        Object[] pkVals = new Object[pks.size()];
        for(int idx = 0; idx < pks.size(); ++idx)
        {
            ColumnInfo pk = pks.get(idx);
            pkVals[idx] = map.get(pk.getName());
            if(null == pkVals[idx])
                throw new InvalidKeyException("Value for key field '" + pk.getName() + "' was null or not supplied!", map);
        }
        return pkVals;
    }

    protected void convertTypes(Map<String,Object> row)
    {
        for(ColumnInfo col : getTable().getColumns())
        {
            Object value = row.get(col.getName());
            if(null != value)
            {
                switch(col.getSqlTypeInt())
                {
                    case java.sql.Types.DATE:
                    case java.sql.Types.TIME:
                        row.put(col.getName(), value instanceof Date ? value : ConvertUtils.convert(value.toString(), Date.class));
                }
            }
        }
    }
}
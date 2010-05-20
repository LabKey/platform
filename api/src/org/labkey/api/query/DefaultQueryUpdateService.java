/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

import org.labkey.api.security.User;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.Table;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.view.UnauthorizedException;
import org.apache.commons.beanutils.ConvertUtils;

import java.util.Map;
import java.util.List;
import java.util.Date;
import java.sql.SQLException;

/*
* User: Dave
* Date: Jun 18, 2008
* Time: 11:17:16 AM
*/
public class DefaultQueryUpdateService extends AbstractQueryUpdateService
{
    private TableInfo _dbTable = null;

    public DefaultQueryUpdateService(TableInfo queryTable, TableInfo dbTable)
    {
        super(queryTable);
        _dbTable = dbTable;
    }

    protected TableInfo getDbTable()
    {
        return _dbTable;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        Map<String,Object> row = ((Map<String,Object>)Table.selectObject(getDbTable(), getKeys(keys), Map.class));

        //PostgreSQL includes a column named _row for the row index, but since this is selecting by
        //primary key, it will always be 1, which is not only unnecessary, but confusing, so strip it
        if(null != row)
            row.remove("_row");
        
        return row;
    }

    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row)
            throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        convertTypes(row);
        setSpecialColumns(user, container, getDbTable(), row);
        return Table.insert(user, getDbTable(), row);
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, Map<String, Object> oldRow)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        //when updating a row, we should strip the following fields, as they are
        //automagically maintained by the table layer, and should not be allowed
        //to change once the record exists.
        //unfortunately, the Table.update() method doesn't strip these, so we'll
        //do that here.
        // Owner, CreatedBy, Created, EntityId
        Map<String,Object> rowStripped = new CaseInsensitiveHashMap<Object>(row.size());
        for(String key : row.keySet())
        {
            if(0 != key.compareToIgnoreCase("Owner")
            && 0 != key.compareToIgnoreCase("CreatedBy")
            && 0 != key.compareToIgnoreCase("Created")
            && 0 != key.compareToIgnoreCase("EntityId"))
                rowStripped.put(key, row.get(key));
        }

        convertTypes(rowStripped);
        setSpecialColumns(user, container, getDbTable(), row);

        Object rowContainer = row.get("container");
        if (rowContainer != null)
        {
            if (oldRow == null)
                throw new UnauthorizedException("The existing row was not found");

            Object oldContainer = new CaseInsensitiveHashMap(oldRow).get("container");
            if (null != oldContainer && !rowContainer.equals(oldContainer))
                throw new UnauthorizedException("The row is from the wrong container.");
        }

        Map<String,Object> updatedRow = Table.update(user, getDbTable(), rowStripped, oldRow == null ? getKeys(row) : getKeys(oldRow));

        //when passing a map for the row, the Table layer returns the map of fields it updated, which excludes
        //the primary key columns as well as those marked read-only. So we can't simply return the map returned
        //from Table.update(). Instead, we need to copy values from updatedRow into row and return that.
        row.putAll(updatedRow);
        return row;
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        if (oldRowMap == null)
            return null;

        if (container != null && getDbTable().getColumn("container") != null)
        {
            // UNDONE: 9077: check container permission on each row before delete
            Object oldContainer = new CaseInsensitiveHashMap(oldRowMap).get("container");
            if (null != oldContainer && !container.getId().equals(oldContainer))
                throw new UnauthorizedException("The row is from the wrong container.");
        }

        Table.delete(getDbTable(), getKeys(oldRowMap));
        return oldRowMap;
    }

    protected Object[] getKeys(Map<String, Object> map) throws InvalidKeyException
    {
        //build an array of pk values based on the table info
        TableInfo table = getDbTable();
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
        for(ColumnInfo col : getDbTable().getColumns())
        {
            Object value = row.get(col.getName());
            if(null != value)
            {
                switch(col.getSqlTypeInt())
                {
                    case java.sql.Types.DATE:
                    case java.sql.Types.TIME:
                    case java.sql.Types.TIMESTAMP:
                        row.put(col.getName(), value instanceof Date ? value : ConvertUtils.convert(value.toString(), Date.class));
                }
            }
        }
    }

    /**
     * Override this method to alter the row before insert or update.
     * For example, you can automatically adjust certain column values based on context.
     * @param user The current user
     * @param container The current container
     * @param table The table to be updated
     * @param row The row data
     */
    protected void setSpecialColumns(User user, Container container, TableInfo table, Map<String,Object> row)
    {
        if (null != container)
            row.put("container", container.getId());
    }
}

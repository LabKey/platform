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
package org.labkey.query.data;

import org.apache.commons.beanutils.ConvertUtils;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.OntologyObject;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.query.data.SimpleUserSchema.SimpleTable;

import java.sql.SQLException;
import java.util.*;

/*
* User: Dave
* Date: Jun 18, 2008
* Time: 11:17:16 AM
*/
public class DefaultQueryUpdateService extends AbstractQueryUpdateService
{
    private TableInfo _dbTable = null;

    public DefaultQueryUpdateService(SimpleTable queryTable, TableInfo dbTable)
    {
        super(queryTable);
        _dbTable = dbTable;
    }

    protected SimpleTable getQueryTable()
    {
        return (SimpleTable)super.getQueryTable();
    }

    protected TableInfo getDbTable()
    {
        return _dbTable;
    }

    private class ImportHelper implements OntologyManager.ImportHelper
    {
        boolean insert = false;

        ImportHelper(boolean insert)
        {
            this.insert = insert;
        }

        @Override
        public String beforeImportObject(Map<String, Object> map) throws SQLException
        {
            SimpleTable queryTable = getQueryTable();
            ColumnInfo objectUriCol = queryTable.getObjectUriColumn();

            // Get existing Lsid
            String lsid = (String)map.get(objectUriCol.getName());
            assert insert || lsid != null;
            if (lsid != null)
                return lsid;

            // Generate a new Lsid
            lsid = queryTable.getPropertyURI();
            map.put(objectUriCol.getName(), lsid);
            return lsid;
        }

        @Override
        public void afterBatchInsert(int currentRow) throws SQLException
        {
        }

        @Override
        public void updateStatistics(int currentRow) throws SQLException
        {
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        Map<String,Object> row = _select(container, getKeys(keys));

        //PostgreSQL includes a column named _row for the row index, but since this is selecting by
        //primary key, it will always be 1, which is not only unnecessary, but confusing, so strip it
        if(null != row)
            row.remove("_row");
        
        return row;
    }

    protected Map<String, Object> _select(Container container, Object[] keys) throws SQLException
    {
        Map<String, Object> row = ((Map<String,Object>)Table.selectObject(getDbTable(), keys, Map.class));

        SimpleTable queryTable = getQueryTable();
        ColumnInfo objectUriCol = queryTable.getObjectUriColumn();
        Domain domain = queryTable.getDomain();
        if (objectUriCol != null && domain != null && domain.getProperties().length > 0)
        {
            String lsid = (String)row.get(objectUriCol.getName());
            if (lsid != null)
            {
                Map<String, Object> propertyValues = OntologyManager.getProperties(container, lsid);
                if (propertyValues.size() > 0)
                {
                    // convert PropertyURI->value map into "Property name"->value map
                    Map<String, DomainProperty> propertyMap = domain.createImportMap(false);
                    for (Map.Entry<String, Object> entry : propertyValues.entrySet())
                    {
                        String propertyURI = entry.getKey();
                        DomainProperty dp = propertyMap.get(propertyURI);
                        PropertyDescriptor pd = dp != null ? dp.getPropertyDescriptor() : null;
                        if (pd != null)
                            row.put(pd.getName(), entry.getValue());
                    }
                }
            }
        }

        return row;
    }

    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row)
            throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        convertTypes(row);
        setSpecialColumns(user, container, getDbTable(), row);
        return _insert(user, container, row);
    }

    protected Map<String, Object> _insert(User user, Container c, Map<String, Object> row)
            throws SQLException, ValidationException
    {
        SimpleTable queryTable = getQueryTable();
        ColumnInfo objectUriCol = queryTable.getObjectUriColumn();
        Domain domain = queryTable.getDomain();
        if (objectUriCol != null && domain != null && domain.getProperties().length > 0)
        {
            // convert "Property name"->value map into PropertyURI->value map
            List<PropertyDescriptor> pds = new ArrayList<PropertyDescriptor>();
            Map<String, Object> values = new HashMap<String, Object>();
            for (PropertyColumn pc : queryTable.getPropertyColumns())
            {
                PropertyDescriptor pd = pc.getPropertyDescriptor();
                pds.add(pd);
                Object value = getPropertyValue(row, pd);
                values.put(pd.getPropertyURI(), value);
            }

            PropertyDescriptor[] properties = pds.toArray(new PropertyDescriptor[pds.size()]);
            List<String> lsids = OntologyManager.insertTabDelimited(c, user, null, new ImportHelper(true), properties, Collections.singletonList(values), true);
            String lsid = lsids.get(0);

            row.put(objectUriCol.getName(), lsid);
        }

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

        Map<String,Object> updatedRow = _update(user, container, rowStripped, oldRow, oldRow == null ? getKeys(row) : getKeys(oldRow));

        //when passing a map for the row, the Table layer returns the map of fields it updated, which excludes
        //the primary key columns as well as those marked read-only. So we can't simply return the map returned
        //from Table.update(). Instead, we need to copy values from updatedRow into row and return that.
        row.putAll(updatedRow);
        return row;
    }

    protected Map<String, Object> _update(User user, Container c, Map<String, Object> row, Map<String, Object> oldRow, Object[] keys)
            throws SQLException, ValidationException
    {
        SimpleTable queryTable = getQueryTable();
        ColumnInfo objectUriCol = queryTable.getObjectUriColumn();
        Domain domain = queryTable.getDomain();
        if (objectUriCol != null && domain != null && domain.getProperties().length > 0)
        {
            String lsid = (String)oldRow.get(objectUriCol.getName());

            // convert "Property name"->value map into PropertyURI->value map
            List<PropertyDescriptor> pds = new ArrayList<PropertyDescriptor>();
            Map<String, Object> newValues = new HashMap<String, Object>();
            for (PropertyColumn pc : queryTable.getPropertyColumns())
            {
                PropertyDescriptor pd = pc.getPropertyDescriptor();
                pds.add(pd);

                if (hasProperty(oldRow, pd))
                    OntologyManager.deleteProperty(lsid, pd.getPropertyURI(), c, c);

                Object value = getPropertyValue(row, pd);
                if (value != null)
                    newValues.put(pd.getPropertyURI(), value);
            }

            // Note: copy lsid into newValues map so it will be found by the ImportHelper.beforeImportObject()
            newValues.put(objectUriCol.getName(), lsid);
            PropertyDescriptor[] properties = pds.toArray(new PropertyDescriptor[pds.size()]);
            OntologyManager.insertTabDelimited(c, user, null, new ImportHelper(false), properties, Collections.singletonList(newValues), true);
        }

        return Table.update(user, getDbTable(), row, keys);
    }

    // Get value from row map where the keys are column names.
    private Object getPropertyValue(Map<String, Object> row, PropertyDescriptor pd)
    {
        if (row.containsKey(pd.getName()))
            return row.get(pd.getName());

        if (row.containsKey(pd.getLabel()))
            return row.get(pd.getLabel());

        Set<String> aliases = pd.getImportAliasSet();
        if (aliases != null && aliases.size() > 0)
        {
            for (String alias : aliases)
            {
                if (row.containsKey(alias))
                    return row.get(alias);
            }
        }

        return null;
    }

    // Checks a value exists in the row map (value may be null)
    private boolean hasProperty(Map<String, Object> row, PropertyDescriptor pd)
    {
        if (row.containsKey(pd.getName()))
            return true;

        if (row.containsKey(pd.getLabel()))
            return true;

        Set<String> aliases = pd.getImportAliasSet();
        if (aliases != null && aliases.size() > 0)
        {
            for (String alias : aliases)
            {
                if (row.containsKey(alias))
                    return true;
            }
        }

        return false;
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

        _delete(container, oldRowMap);
        return oldRowMap;
    }

    protected void _delete(Container c, Map<String, Object> row) throws SQLException, InvalidKeyException
    {
        SimpleTable queryTable = getQueryTable();
        ColumnInfo objectUriCol = queryTable.getObjectUriColumn();
        if (objectUriCol != null)
        {
            String lsid = (String)row.get(objectUriCol.getName());
            if (lsid != null)
            {
                OntologyObject oo = OntologyManager.getOntologyObject(c, lsid);
                OntologyManager.deleteProperties(oo.getObjectId(), c);
            }
        }

        Table.delete(getDbTable(), getKeys(row));
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

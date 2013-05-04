/*
 * Copyright (c) 2008-2012 LabKey Corporation
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
package org.labkey.list.model;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.etl.DataIterator;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.view.NotFoundException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/*
* User: Dave
* Date: Jun 12, 2008
* Time: 1:51:50 PM
*/

/**
 * Implementation of QueryUpdateService for Lists
 */
public class ListQueryUpdateService extends AbstractQueryUpdateService
{
    ListDefinition _list = null;

    public ListQueryUpdateService(ListTable queryTable, ListDefinition list)
    {
        super(queryTable);
        _list = list;
    }

    public ListDefinition getList()
    {
        return _list;
    }

    @Override
    protected DataIteratorContext getDataIteratorContext(BatchValidationException errors, InsertOption insertOption)
    {
        DataIteratorContext context = super.getDataIteratorContext(errors, insertOption);
        if (insertOption.batch)
        {
            context.setMaxRowErrors(100);
            context.setFailFast(false);
        }
        return context;
    }

    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        throw new IllegalStateException("method not used by ListQueryUpdateService");
    }


    @Override
    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, Map<String, Object> extraScriptContext)
            throws DuplicateKeyException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = super._insertRowsUsingETL(user, container, rows, getDataIteratorContext(errors, InsertOption.INSERT), extraScriptContext);
        if (null != result && result.size() > 0 && !errors.hasErrors())
            ListManager.get().indexList(_list);
        return result;
    }


    @Override
    public int importRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, Map<String, Object> extraScriptContext) throws SQLException
    {
        int count = super._importRowsUsingETL(user, container, rows, null, getDataIteratorContext(errors, InsertOption.IMPORT), extraScriptContext);
        if (count > 0 && !errors.hasErrors())
            ListManager.get().indexList(_list);
        return count;
    }


    /*
     * ListItem oriented stuff still used by update/delete
     */

    @Override
    protected final Map<String, Object> getRow(User user, Container container, Map<String, Object> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        ListItem bean = get(user, container, keyFromMap(keys));
        return null == bean ? null : mapFromBean(bean);
    }


    public ListItem createNewBean()
    {
        return getList().createListItem();
    }


    public ListItem get(User user, Container container, Object key) throws QueryUpdateServiceException, SQLException
    {
        return getList().getListItem(key);
    }


    @Override
    protected final Map<String, Object> updateRow(User user, Container container,
            Map<String, Object> row, @NotNull Map<String, Object> oldRow)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        Object oldKey = null != oldRow ? keyFromMap(oldRow) : keyFromMap(row);
        ListItem bean = get(user, container, oldKey);
        if (null == bean)
            throw new NotFoundException("The object you are trying to update was not found in the database."); //CONSIDER: maybe we should return null for 0 rows affected instead?
        populateBean(bean, row, user);
        saveItem(user, bean);
        return mapFromBean(bean);
    }


    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        Object key = keyFromMap(oldRow);
        ListItem item = get(user, container, key);
        if (null != item)
            item.delete(user, container, isBulkLoad());
        return oldRow;
    }


    public Object keyFromMap(Map<String, Object> map) throws InvalidKeyException
    {
        ListDefinition list = getList();
        assert null != list;
        Object o = map.get(list.getKeyName());
        if (null != o)
            return o;
        o = map.get(list.getKeyName().toLowerCase());
        if (null != o)
            return o;
        throw new InvalidKeyException("No value supplied for the key column '" + list.getKeyName() + "'!", map);
    }


    private static boolean isKeyProperty(ListDefinition list, DomainProperty prop)
    {
        // UNDONE: store keypropertyid to make this faster
        return list.getKeyName().equalsIgnoreCase(prop.getName());
    }


    public Map<String, Object> mapFromBean(ListItem bean) throws QueryUpdateServiceException
    {
        return toMap(getList(), bean);
    }

    public static Map<String, Object> toMap(ListDefinition listdef, ListItem bean)
    {
        //since ListItems are not really 'beans' we need to convert to a map ourselves
        Map<String, Object> map = new CaseInsensitiveHashMap<Object>();

        //key
        map.put(listdef.getKeyName(), bean.getKey());

        //entity id
        map.put("EntityId", bean.getEntityId());

        //domain properties
        for (DomainProperty prop : listdef.getDomain().getProperties())
        {
            if (isKeyProperty(listdef, prop))
                continue;
            Object value = bean.getProperty(prop);
            if (value instanceof ObjectProperty)
                value = ((ObjectProperty)value).value();

            map.put(prop.getName(), value);
        }

        return map;
    }


    protected void populateBean(ListItem bean, Map<String, Object> row, User user) throws QueryUpdateServiceException
    {
        //since ListItems are not really 'beans' we need to handle the population
        ListDefinition listdef = getList();

        //set the key if list item does not have auto-assigned key
        if(ListDefinition.KeyType.AutoIncrementInteger != listdef.getKeyType())
        {
            Object key = row.get(listdef.getKeyName());
            if(null != key)
                bean.setKey(key);
            else
                throw new QueryUpdateServiceException("Items in the list '" + listdef.getName() + 
                        "' require user-supplied key values but no value for key column '"
                        + listdef.getKeyName() + "' was supplied!");
        }

        //set the domain properties, doing type coercion as necessary
        TableInfo table = listdef.getTable(user);
        for (DomainProperty prop : listdef.getDomain().getProperties())
        {
            if (isKeyProperty(listdef, prop))
                continue;
            //set the prop only if it was supplied in the map
            if(row.containsKey(prop.getName()))
            {
                Object value = row.get(prop.getName());
                if(null != value && value instanceof String)
                    value = StringUtils.trimToNull((String)value);
                bean.setProperty(prop, convertType(value, table.getColumn(prop.getName()), prop));
            }
        }
    }

    @Override
    protected Map<String, Object> coerceTypes(Map<String, Object> row)
    {
        // do nothing, list specific type conversion occurs later during bean population with the convertType method.
        return row;
    }

    protected Object convertType(Object value, ColumnInfo col, DomainProperty prop) throws ConversionException
    {
        if(null == value || null == col || value.getClass().equals(col.getJavaClass()))
            return value;

        // attachment type information is not propogated to columninfos so look it up in the property descriptor
        if (prop.getPropertyDescriptor().getPropertyType() == PropertyType.ATTACHMENT &&
                AttachmentFile.class.isAssignableFrom(value.getClass()))
            return value;

        Class targetType = col.getJavaClass();
        Converter converter = ConvertUtils.lookup(targetType);
        if(null == converter)
            throw new ConversionException("Cannot convert the value for column " + col.getName() + " from a " + value.getClass().toString() + " into a " + targetType.toString());
        return converter.convert(targetType, value);
    }

    protected void saveItem(User user, ListItem item) throws QueryUpdateServiceException, SQLException, ValidationException
    {
        try
        {
            item.save(user, isBulkLoad());
        }
        catch(AttachmentService.DuplicateFilenameException e)
        {
            throw new QueryUpdateServiceException(e);
        }
        catch(IOException e)
        {
            throw new QueryUpdateServiceException(e);
        }
    }
}

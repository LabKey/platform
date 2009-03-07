/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
package org.labkey.experiment.list;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.*;
import org.labkey.api.security.User;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/*
* User: Dave
* Date: Jun 12, 2008
* Time: 1:51:50 PM
*/

/**
 * Implementation of QueryUpdateService for Lists
 */
public class ListQueryUpdateService extends AbstractQueryUpdateService<ListItem, Object>
{
    ListDefinition _list = null;

    public ListQueryUpdateService(ListDefinition list)
    {
        _list = list;
    }

    public ListDefinition getList()
    {
        return _list;
    }

    public ListItem createNewBean()
    {
        return getList().createListItem();
    }

    public ListItem get(User user, Container container, Object key) throws QueryUpdateServiceException, SQLException
    {
        return getList().getListItem(key);
    }

    public ListItem insert(User user, Container container, ListItem bean) throws ValidationException, DuplicateKeyException, QueryUpdateServiceException, SQLException
    {
        saveItem(user, bean);
        return bean;
    }

    public ListItem update(User user, Container container, ListItem bean, Object oldKey) throws ValidationException, QueryUpdateServiceException, SQLException
    {
        saveItem(user, bean);
        return bean;
    }

    public void delete(User user, Container container, Object key) throws QueryUpdateServiceException, SQLException
    {
        ListItem item = get(user, container, key);
        if(null != item)
            item.delete(user, container);
    }

    public Object keyFromMap(Map<String, Object> map) throws InvalidKeyException
    {
        ListDefinition list = getList();
        assert null != list;
        if(!map.containsKey(list.getKeyName()) || map.get(list.getKeyName()) == null)
            throw new InvalidKeyException("No value supplied for the key column '" + list.getKeyName() + "'!", map);
        return map.get(getList().getKeyName());
    }

    public Map<String, Object> mapFromBean(ListItem bean) throws QueryUpdateServiceException
    {
        //since ListItems are not really 'beans' we need to convert to a map ourselves
        ListDefinition listdef = getList();
        Map<String,Object> map = new HashMap<String,Object>();

        //key
        map.put(listdef.getKeyName(), bean.getKey());

        //entity id
        map.put("EntityId", bean.getEntityId());

        //domain properties
        for(DomainProperty prop : listdef.getDomain().getProperties())
            map.put(prop.getName(), bean.getProperty(prop));

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
        TableInfo table = listdef.getTable(user, null);
        for(DomainProperty prop : listdef.getDomain().getProperties())
        {
            //set the prop only if it was supplied in the map
            if(row.containsKey(prop.getName()))
            {
                Object value = row.get(prop.getName());
                if(null != value && value instanceof String)
                    value = StringUtils.trimToNull((String)value);
                bean.setProperty(prop, convertType(value, table.getColumn(prop.getName())));
            }
        }
    }

    protected Object convertType(Object value, ColumnInfo col) throws ConversionException
    {
        if(null == value || null == col || value.getClass().equals(col.getJavaClass()))
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
            item.save(user);
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
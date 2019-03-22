/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.converters.IntegerConverter;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.view.NotFoundException;

import java.sql.SQLException;
import java.util.Map;
/**
 * Helpful base class for implementations of QueryUpdateSerivce. This class allows
 * the derived class to work with strongly-typed beans rather than
 * the maps used in the QueryUpdateService interface.
 * <p>
 * This class is a generic with two type parameters: T and K. T is the main
 * bean type (e.g., "Visit", "ListItem"). K is the primary key type, which may
 * be a simple type like Integer, or a more complex object in the case of
 * compound primary keys.
 * </p><p>
 * This class uses BeanUtils to convert between the maps in the
 * QueryUpdateService interface to beans of type T, and keys of
 * type K.</p>
 *
 * User: Dave
 * Date: Jun 9, 2008
 */
public abstract class AbstractBeanQueryUpdateService<T,K> extends AbstractQueryUpdateService
{
    private IntegerConverter _converter = new IntegerConverter();

    public AbstractBeanQueryUpdateService(TableInfo queryTable)
    {
        super(queryTable);
    }

    /**
     * Converts a bean to a map (or in Britt's eloquent parlance, 'map-ificates a T')
     * <p>
     * Override this method to perform your own conversion from bean to map.
     * @param bean The bean
     * @return A map of the bean's properties
     * @throws QueryUpdateServiceException Thrown if there were problems converting
     */
    @SuppressWarnings("unchecked")
    protected Map<String,Object> mapFromBean(T bean) throws QueryUpdateServiceException
    {
        if(null == bean)
            return null;

        Map<String,Object> map;
        try
        {
            map = (Map<String,Object>)BeanUtils.describe(bean);
        }
        catch(Exception e)
        {
            throw new QueryUpdateServiceException(e);
        }

        return new CaseInsensitiveHashMap<>(map);
    }

    /**
     * Populates a newly-created bean from a map of property values.
     * When combined with <code>createNewBean()</code> this
     * accomplishes Britt's timeless phrase: 'T-ificates a map'.
     * <p>
     * Override this to perform your own population.
     * @param bean The newly-created bean
     * @param row The row values as a map
     * @param user The user initiating the request
     * @throws QueryUpdateServiceException Thrown if there's a problem
     */
    protected void populateBean(T bean, Map<String, Object> row, User user) throws QueryUpdateServiceException
    {
        try
        {
            ObjectFactory f = ObjectFactory.Registry.getFactory(bean.getClass());
            f.fromMap(bean, row);
        }
        catch(Exception e)
        {
            throw new QueryUpdateServiceException(e);
        }
    }

    @Override
    protected final Map<String, Object> getRow(User user, Container container, Map<String, Object> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        T bean = get(user, container, keyFromMap(keys));
        return null == bean ? null : mapFromBean(bean);
    }

    @Override
    protected final Map<String, Object> insertRow(User user, Container container, Map<String, Object> row)
            throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        T bean = createNewBean();
        populateBean(bean, row, user);
        return mapFromBean(insert(user, container, bean));
    }

    @Override
    protected final Map<String, Object> updateRow(User user, Container container,
                                                  Map<String, Object> row, Map<String, Object> oldRow)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        K oldKey = null != oldRow ? keyFromMap(oldRow) : keyFromMap(row);
        T bean = get(user, container, oldKey);
        if(null == bean)
            throw new NotFoundException("The object you are trying to update was not found in the database."); //CONSIDER: maybe we should return null for 0 rows affected instead?
        populateBean(bean, row, user);
        return mapFromBean(update(user, container, bean, oldKey));
    }

    @Override
    protected final Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        delete(user, container, keyFromMap(oldRow));
        return oldRow;
    }

    /**
     * Used to create a new instance of the bean type (T).
     * Override this and return a new instance of your bean.
     * @return A new instance of the bean.
     */
    protected abstract T createNewBean();  //CONSIDER: should this be public instead of protected?

    /**
     * Returns a key given the map of primary key values. May not return null.
     * @param map A map of primary key values. For serial row ids, the map will contain only one entry.
     * @return The primary key.
     * @throws InvalidKeyException Thrown if the required key values in the map are missing or invalid.
     */
    protected abstract K keyFromMap(Map<String,Object> map) throws InvalidKeyException;

    /**
     * Returns an instance of the main bean type corresponding to the provided key.
     * If nothing was found for the given key, return null.
     * @param user The current user.
     * @param container The container in which the data should exist.
     * @param key The primary key.
     * @return The bean instance corresponding to the provided key.
     * @throws QueryUpdateServiceException Thrown for implementation-specific exceptions.
     * @throws SQLException Thrown if there is a problem communicating with the database.
     */
    protected abstract T get(User user, Container container, K key) throws QueryUpdateServiceException, SQLException;

    /**
     * Inserts a new bean into the database.
     * @param user The current user.
     * @param container The container in which this data should exist.
     * @param bean The populated bean. This cannot be null.
     * @return The bean after insert. If the bean has a database-assigned key, they key value(s) should
     * be set on the bean. Callers will reload the bean if trigger-assigned values are needed.
     * @throws ValidationException Thrown if the bean is invalid.
     * @throws DuplicateKeyException Thrown if the key is already used in the database.
     * @throws QueryUpdateServiceException Thrown for implementation-specific exceptions.
     * @throws SQLException Thrown if there was a problem communicating with the database.
     */
    protected abstract T insert(User user, Container container, T bean) throws ValidationException, DuplicateKeyException, QueryUpdateServiceException, SQLException;

    /**
     * Updates a bean in the database.
     * @param user The current user.
     * @param container The container in which this bean should exist.
     * @param bean The bean. This cannot be null.
     * @param oldKey If the keys are user-assignable, this will be the old key value. If not, this will be null.
     * @return The bean after update. Callers will reload the bean if trigger-assigned values are needed.
     * @throws ValidationException Thrown if the bean is invalid.
     * @throws QueryUpdateServiceException Thrown for implementation-specific exceptions.
     * @throws SQLException Thrown if there is a problem communicating with the database.
     */
    protected abstract T update(User user, Container container, T bean, K oldKey) throws ValidationException, QueryUpdateServiceException, SQLException;

    /**
     * Deletes a bean in the database.
     * @param user The current user.
     * @param container The container in which the bean should exist.
     * @param key The key for the bean.
     * @throws QueryUpdateServiceException Thrown for implementation-specific exceptions.
     * @throws SQLException Thrown if there is a problem communicating with the database.
     */
    protected abstract void delete(User user, Container container, K key) throws QueryUpdateServiceException, SQLException;

    protected Integer getInteger(Map<String, Object> map, String propName) throws InvalidKeyException
    {
        Object key = map.get(propName);
        if(null == key)
            throw new InvalidKeyException("No key value defined for key field '" + propName + "'!", map);
        Integer ikey = (Integer)(_converter.convert(Integer.class, key));
        if(null == ikey)
            throw new InvalidKeyException("Key value '" + key.toString() + "' could not be converted to an Integer!", map);
        return ikey;
    }
}

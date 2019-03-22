/*
 * Copyright (c) 2008-2011 LabKey Corporation
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

import org.apache.commons.beanutils.converters.IntegerConverter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.data.Container;

import java.util.Map;
import java.sql.SQLException;

/*
* User: Dave
* Date: Jun 13, 2008
* Time: 3:28:07 PM
*/
/**
 * An abstract base class for QueryUpdateService implementations that use
 * a single integer as the primary key value for each row. This class
 * handles translating from a map containing the key value as an Object to
 * a strongly-typed int value for the <code>get()</code> and
 * <code>delete()</code> methods.
 */
public abstract class RowIdQueryUpdateService<T> extends AbstractBeanQueryUpdateService<T, Integer>
{
    private String _keyColumn;

    public RowIdQueryUpdateService(TableInfo table)
    {
        super(table);
        assert table.getPkColumns().size() == 1;
        assert table.getPkColumns().get(0).getJavaClass() == Integer.class || table.getPkColumns().get(0).getJavaClass() == int.class;

        _keyColumn = table.getPkColumnNames().get(0);
    }

    public java.lang.Integer keyFromMap(Map<String, Object> map) throws InvalidKeyException
    {
        return getInteger(map, _keyColumn);
    }

    public final T get(User user, Container container, Integer key) throws QueryUpdateServiceException, SQLException
    {
        return get(user, container, key.intValue());
    }

    public final void delete(User user, Container container, Integer key) throws QueryUpdateServiceException, SQLException
    {
        delete(user, container, key.intValue());
    }

    /**
     * Returns the bean instance corresponding to the provided key value.
     * @param user The user.
     * @param container The container in which the bean should live.
     * @param key The primary key.
     * @return The bean instance corresponding to the key.
     * @throws QueryUpdateServiceException Thrown for provider-specific exceptions.
     * @throws SQLException Thrown if there was a problem communicating with the database.
     */
    public abstract T get(User user, Container container, int key) throws QueryUpdateServiceException, SQLException;

    /**
     * Deletes the bean instance corresponding to the provided key value.
     * @param user The user.
     * @param container The container in which the bean should live.
     * @param key The primary key.
     * @throws QueryUpdateServiceException Thrown for provider-specific exceptions.
     * @throws SQLException Thrown if there was a problem communicating with the database.
     */
    public abstract void delete(User user, Container container, int key) throws QueryUpdateServiceException, SQLException;

}

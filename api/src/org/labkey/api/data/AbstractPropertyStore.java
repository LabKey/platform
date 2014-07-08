/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.data.PropertyManager.PropertyMap;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * User: adam
 * Date: 10/11/13
 * Time: 4:56 PM
 */
public abstract class AbstractPropertyStore implements PropertyStore
{
    private static final PropertySchema _prop = PropertySchema.getInstance();

    private final PropertyCache _cache;

    AbstractPropertyStore(String name)
    {
        _cache = new PropertyCache(name, new PropertyLoader());
    }

    protected abstract void validateStore();
    protected abstract boolean isValidPropertyMap(PropertyMap props);
    protected abstract String getSaveValue(PropertyMap props, @Nullable String value);
    protected abstract void fillValueMap(TableSelector selector, PropertyMap props);
    protected abstract PropertyEncryption getPreferredPropertyEncryption();
    protected abstract void appendWhereFilter(SQLFragment sql);


    @NotNull
    @Override
    public Map<String, String> getProperties(User user, Container container, String category)
    {
        validateStore();

        Map<String, String> map = _cache.getProperties(user, container, category);

        return null != map ? map : NULL_MAP;
    }

    @NotNull
    @Override
    public Map<String, String> getProperties(Container container, String category)
    {
        return getProperties(PropertyManager.SHARED_USER, container, category);
    }

    @NotNull
    @Override
    public Map<String, String> getProperties(String category)
    {
        return getProperties(PropertyManager.SHARED_USER, ContainerManager.getRoot(), category);
    }

    @Override
    public PropertyMap getWritableProperties(User user, Container container, String category, boolean create)
    {
        validateStore();
        String containerId = container.getId().intern();

        try (DbScope.Transaction transaction = _prop.getSchema().getScope().ensureTransaction())
        {
            synchronized (containerId)
            {
                ColumnInfo setColumn = _prop.getTableInfoProperties().getColumn("Set");
                String setSelectName = setColumn.getSelectName();   // Keyword in some dialects

                SQLFragment sql = new SQLFragment("SELECT " + setSelectName + ", Encryption FROM " + _prop.getTableInfoPropertySets() +
                    " WHERE UserId = ? AND ObjectId = ? AND Category = ?", user, container, category);

                Map<String, Object> map = new SqlSelector(_prop.getSchema(), sql).getMap();
                boolean newSet = (null == map);
                PropertyEncryption propertyEncryption;

                if (newSet)
                {
                    if (!create)
                    {
                        transaction.commit();
                        return null;
                    }
                    propertyEncryption = getPreferredPropertyEncryption();
                    Map<String, Object> insertMap = new HashMap<>();
                    insertMap.put("UserId", user);
                    insertMap.put("ObjectId", container);
                    insertMap.put("Category", category);
                    insertMap.put("Encryption", propertyEncryption.getSerializedName());
                    map = Table.insert(user, _prop.getTableInfoPropertySets(), insertMap);
                }
                else
                {
                    String encryptionName = (String) map.get("Encryption");
                    propertyEncryption = PropertyEncryption.getBySerializedName(encryptionName);

                    if (null == propertyEncryption)
                        throw new IllegalStateException("Unknown encryption name: " + encryptionName);
                }

                // map should always contain the set number, whether brand new or old
                int set = (Integer)map.get("Set");
                PropertyMap m = new PropertyMap(set, user.getUserId(), containerId, category, propertyEncryption);

                validatePropertyMap(m);

                if (newSet)
                {
                    // A brand new set, but we might have previously cached a NULL marker and/or another thread might
                    // try to create this same set before we save.
                    _cache.remove(m);
                }
                else
                {
                    // Map-filling query needed only for existing property set
                    Filter filter = new SimpleFilter(setColumn.getFieldKey(), set);
                    TableInfo tinfo = _prop.getTableInfoProperties();
                    TableSelector selector = new TableSelector(tinfo, tinfo.getColumns("Name", "Value"), filter, null);
                    fillValueMap(selector, m);
                }

                transaction.commit();
                return m;
            }
        }
    }

    @Override
    public PropertyMap getWritableProperties(Container container, String category, boolean create)
    {
        return getWritableProperties(PropertyManager.SHARED_USER, container, category, create);
    }

    @Override
    public PropertyMap getWritableProperties(String category, boolean create)
    {
        return getWritableProperties(PropertyManager.SHARED_USER, ContainerManager.getRoot(), category, create);
    }

    @Override
    public void saveProperties(Map<String, String> map)
    {
        validateStore();

        if (!(map instanceof PropertyMap))
            throw new IllegalStateException("map must be created by getProperties()");

        PropertyMap props = (PropertyMap)map;

        validatePropertyMap(props);

        // Stored procedure property_saveValue is not thread-safe, so we synchronize the saving of each property set to
        // avoid attempting to modify the same property from two threads.  Use unique key + set id to avoid locking on a
        // shared interned string object.
        String lockString = "PropertyManager.Set=" + props.getSet();

        synchronized(lockString.intern())
        {
            try
            {
                // delete removed properties
                if (null != props.removedKeys)
                {
                    for (Object removedKey : props.removedKeys)
                    {
                        String name = toNullString(removedKey);
                        saveValue(props, name, null);
                    }
                }

                // set properties
                // we're not tracking modified or not, so set them all
                for (Object entry : props.entrySet())
                {
                    Map.Entry e = (Map.Entry) entry;
                    String name = toNullString(e.getKey());
                    String value = toNullString(e.getValue());
                    saveValue(props, name, value);
                }
            }
            finally
            {
                _cache.remove(props);
            }
        }
    }

    private static String toNullString(Object o)
    {
        return null == o ? null : String.valueOf(o);
    }


    private void saveValue(PropertyMap props, String name, String value)
    {
        if (null == name)
            return;

        String sql = _prop.getSqlDialect().execute(_prop.getSchema(), "property_setValue", "?, ?, ?");

        new SqlExecutor(_prop.getSchema()).execute(sql, props.getSet(), name, getSaveValue(props, value));
    }


    // Delete properties associated with this store
    void deleteProperties(Container c)
    {
        String setSelectName = _prop.getTableInfoProperties().getColumn("Set").getSelectName();   // Keyword in some dialects
        SQLFragment deleteProps = new SQLFragment("DELETE FROM " + _prop.getTableInfoProperties().getSelectName() +
                " WHERE " + setSelectName +  " IN " +
                "(SELECT " + setSelectName + " FROM " + _prop.getTableInfoPropertySets().getSelectName() + " WHERE ObjectId = ? AND ", c);
        appendWhereFilter(deleteProps);
        deleteProps.append(")");
        new SqlExecutor(_prop.getSchema()).execute(deleteProps);

        SQLFragment deleteSets = new SQLFragment("DELETE FROM " + _prop.getTableInfoPropertySets() + " WHERE ObjectId = ? AND ");
        deleteSets.add(c);
        appendWhereFilter(deleteSets);
        new SqlExecutor(_prop.getSchema()).execute(deleteSets);

        _cache.removeAll(c);
    }


    public void deletePropertySet(String category)
    {
        deletePropertySet(PropertyManager.SHARED_USER, ContainerManager.getRoot(), category);
    }

    public void deletePropertySet(Container container, String category)
    {
        deletePropertySet(PropertyManager.SHARED_USER, container, category);
    }

    public void deletePropertySet(User user, Container container, String category)
    {
        String containerId = container.getId().intern();
        try (DbScope.Transaction transaction = _prop.getSchema().getScope().ensureTransaction())
        {
            synchronized (containerId)
            {
                String setSelectName = _prop.getTableInfoProperties().getColumn("Set").getSelectName();   // Keyword in some dialects

                SQLFragment deleteProps = new SQLFragment();
                deleteProps.append("DELETE FROM ").append(_prop.getTableInfoProperties().getSelectName());
                deleteProps.append(" WHERE ").append(setSelectName).append(" IN ");
                deleteProps.append("(SELECT ").append(setSelectName).append(" FROM ").append(_prop.getTableInfoPropertySets(), "ps");
                deleteProps.append(" WHERE UserId = ? AND ObjectId = ? AND Category = ? AND ");
                deleteProps.add(user.getUserId());
                deleteProps.add(container.getId());
                deleteProps.add(category);

                appendWhereFilter(deleteProps);

                deleteProps.append(")");

                SqlExecutor sqlx = new SqlExecutor(_prop.getSchema());
                sqlx.execute(deleteProps);

                SQLFragment deleteSets = new SQLFragment("DELETE FROM " + _prop.getTableInfoPropertySets() + " WHERE UserId = ? AND ObjectId = ? AND Category = ? AND ");
                deleteSets.add(user);
                deleteSets.add(container);
                deleteSets.add(category);

                appendWhereFilter(deleteSets);

                new SqlExecutor(_prop.getSchema()).execute(deleteSets);

                _cache.remove(container, user, category);

            }
            transaction.commit();
        }
    }


    protected void validatePropertyMap(PropertyMap map)
    {
        if (!isValidPropertyMap(map))
            throw new IllegalStateException("Invalid property map for this property store, " + this.getClass().getSimpleName());
    }


    static final PropertyMap NULL_MAP;

    static
    {
        NULL_MAP = new PropertyMap(0, 0, "NULL_MAP", PropertyManager.class.getName(), PropertyEncryption.None)
        {
            @Override
            public String put(String key, String value)
            {
                throw new UnsupportedOperationException("Cannot modify NULL_MAP");
            }

            @Override
            public void clear()
            {
                throw new UnsupportedOperationException("Cannot modify NULL_MAP");
            }

            @Override
            public String remove(Object key)
            {
                throw new UnsupportedOperationException("Cannot modify NULL_MAP");
            }

            @Override
            public void putAll(Map<? extends String, ? extends String> m)
            {
                throw new UnsupportedOperationException("Cannot modify NULL_MAP");
            }
        };
    }


    protected class PropertyLoader implements CacheLoader<String, Map<String, String>>
    {
        @Override
        public Map<String, String> load(String key, Object argument)
        {
            Object[] params = (Object[])argument;
            PropertyMap map = getWritableProperties((User)params[1], (Container)params[0], (String)params[2], false);

            return null != map ? Collections.unmodifiableMap(map) : null;
        }
    }
}

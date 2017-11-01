/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
import org.labkey.api.security.Encryption;
import org.labkey.api.security.User;

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
    public PropertyManager.PropertyMap getProperties(User user, Container container, String category)
    {
        validateStore();

        PropertyManager.PropertyMap map = _cache.getProperties(user, container, category);

        return null != map ? map : NULL_MAP;
    }

    @NotNull
    @Override
    public PropertyManager.PropertyMap getProperties(Container container, String category)
    {
        return getProperties(PropertyManager.SHARED_USER, container, category);
    }

    @NotNull
    @Override
    public PropertyManager.PropertyMap getProperties(String category)
    {
        return getProperties(PropertyManager.SHARED_USER, ContainerManager.getRoot(), category);
    }

    @Override
    public PropertyMap getWritableProperties(User user, Container container, String category, boolean create)
    {
        validateStore();
        ColumnInfo setColumn = _prop.getTableInfoProperties().getColumn("Set");
        String setSelectName = setColumn.getSelectName();   // Keyword in some dialects

        SQLFragment sql = new SQLFragment("SELECT " + setSelectName + ", Encryption FROM " + _prop.getTableInfoPropertySets() +
            " WHERE UserId = ? AND ObjectId = ? AND Category = ?", user, container, category);

        Map<String, Object> map = new SqlSelector(_prop.getSchema(), sql).getMap();
        boolean newSet = (null == map);
        int set;
        PropertyEncryption propertyEncryption;

        if (newSet)
        {
            if (!create)
            {
                return null;
            }
            // Assign a dummy ID we can use later to tell if we need to insert a brand new set during save
            set = -1;
            propertyEncryption = getPreferredPropertyEncryption();
        }
        else
        {
            String encryptionName = (String) map.get("Encryption");
            propertyEncryption = PropertyEncryption.getBySerializedName(encryptionName);

            if (null == propertyEncryption)
                throw new IllegalStateException("Unknown encryption name: " + encryptionName);

            // map should always contain the set number
            set = (Integer)map.get("Set");
        }

        PropertyMap m = new PropertyMap(set, user, container.getId(), category, propertyEncryption, this);

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
            m.afterPropertiesSet(); // clear modified flag
        }

        return m;
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
        try
        {
            PropertyMap propertyMap = getWritableProperties(user, container, category, false);
            if (propertyMap != null)
            {
                propertyMap.delete();
            }
        }
        catch (Encryption.DecryptionException e)
        {
            // Delete without the advantages of synchronization
            PropertyManager.deleteSetDirectly(user, container.getId(), category, this);
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
        NULL_MAP = new PropertyMap(0, PropertyManager.SHARED_USER, "NULL_MAP", PropertyManager.class.getName(), PropertyEncryption.None, null)
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

    void clearCache(PropertyMap propertyMap)
    {
        _cache.remove(propertyMap);
    }

    protected class PropertyLoader implements CacheLoader<String, PropertyManager.PropertyMap>
    {
        @Override
        public PropertyManager.PropertyMap load(String key, Object argument)
        {
            Object[] params = (Object[])argument;
            PropertyMap map = getWritableProperties((User)params[1], (Container)params[0], (String)params[2], false);
            if (map != null)
            {
                map.lock();
            }
            return map;
        }
    }
}

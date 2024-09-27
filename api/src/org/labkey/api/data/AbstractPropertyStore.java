/*
 * Copyright (c) 2013-2018 LabKey Corporation
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
import org.labkey.api.data.PropertyManager.WritablePropertyMap;
import org.labkey.api.security.Encryption;
import org.labkey.api.security.User;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

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
    public PropertyMap getProperties(User user, Container container, String category)
    {
        validateStore();

        PropertyMap map = _cache.getProperties(user, container, category);

        return null != map ? map : NULL_MAP;
    }

    @Override
    public @NotNull PropertyMap getProperties(User user, String category)
    {
        return getProperties(user, ContainerManager.getRoot(), category);
    }

    @NotNull
    @Override
    public PropertyMap getProperties(Container container, String category)
    {
        return getProperties(PropertyManager.SHARED_USER, container, category);
    }

    @NotNull
    @Override
    public PropertyMap getProperties(String category)
    {
        return getProperties(ContainerManager.getRoot(), category);
    }

    @Override
    public WritablePropertyMap getWritableProperties(User user, Container container, String category, boolean create)
    {
        PropertyMap existingMap = _cache.getProperties(user, container, category);
        if (existingMap != null)
        {
            return new WritablePropertyMap(existingMap);
        }

        if (create)
        {
            // Assign a dummy ID we can use later to tell if we need to insert a brand-new set during save
            return new WritablePropertyMap(-1, user, container.getId(), category, getPreferredPropertyEncryption(), this);
        }

        return null;
    }

    @Override
    public WritablePropertyMap getWritableProperties(User user, String category, boolean create)
    {
        return getWritableProperties(user, ContainerManager.getRoot(), category, create);
    }

    @Override
    public WritablePropertyMap getWritableProperties(Container container, String category, boolean create)
    {
        return getWritableProperties(PropertyManager.SHARED_USER, container, category, create);
    }

    @Override
    public WritablePropertyMap getWritableProperties(String category, boolean create)
    {
        return getWritableProperties(ContainerManager.getRoot(), category, create);
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

    @Override
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

    @Override
    public void deletePropertySet(User user, String category)
    {
        deletePropertySet(user, ContainerManager.getRoot(), category);
    }

    @Override
    public void deletePropertySet(Container container, String category)
    {
        deletePropertySet(PropertyManager.SHARED_USER, container, category);
    }

    @Override
    public void deletePropertySet(String category)
    {
        deletePropertySet(ContainerManager.getRoot(), category);
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

    // Call this only if you must
    void clearCache()
    {
        _cache.clear();
    }

    protected class PropertyLoader implements CacheLoader<String, PropertyMap>
    {
        @Override
        public PropertyMap load(@NotNull String key, Object argument)
        {
            Object[] params = (Object[])argument;
            User user = (User)params[1];
            Container container = (Container)params[0];
            String category = (String)params[2];

            validateStore();
            PropertyMap m = getPropertyMapFromDatabase(user, container, category);
            if (m == null) return null;
            m.afterPropertiesSet(); // clear modified flag

            m.lock();
            return m;
        }
    }

    @Nullable
    public PropertyMap getPropertyMapFromDatabase(User user, Container container, String category)
    {
        ColumnInfo setColumn = _prop.getTableInfoProperties().getColumn("Set");
        String setSelectName = setColumn.getSelectName();   // Keyword in some dialects

        SQLFragment sql = new SQLFragment("SELECT " + setSelectName + ", Encryption FROM " + _prop.getTableInfoPropertySets() +
                " WHERE UserId = ? AND ObjectId = ? AND Category = ?", user, container, category);

        Map<String, Object> map = new SqlSelector(_prop.getSchema(), sql).getMap();
        if (map == null)
        {
            return null;
        }
        PropertyEncryption propertyEncryption;

        String encryptionName = (String) map.get("Encryption");
        propertyEncryption = PropertyEncryption.getBySerializedName(encryptionName);

        if (null == propertyEncryption)
            throw new IllegalStateException("Unknown encryption name: " + encryptionName);

        // map should always contain the set number
        int set = (Integer)map.get("Set");

        PropertyMap m = new PropertyMap(set, user, container.getId(), category, propertyEncryption, AbstractPropertyStore.this);

        validatePropertyMap(m);

        // Map-filling query needed only for existing property set
        Filter filter = new SimpleFilter(setColumn.getFieldKey(), set);
        TableInfo tinfo = _prop.getTableInfoProperties();
        TableSelector selector = new TableSelector(tinfo, tinfo.getColumns("Name", "Value"), filter, null);
        fillValueMap(selector, m);
        return m;
    }

    @Override
    public final Stream<Container> streamMatchingContainers(User user, String category)
    {
        SQLFragment sql = new SQLFragment("SELECT ObjectId FROM " + _prop.getTableInfoPropertySets() + " WHERE UserId = ? AND Category = ?", user, category);

        return new SqlSelector(_prop.getSchema(), sql).uncachedStream(String.class)
            .map(ContainerManager::getForId)
            .filter(Objects::nonNull);
    }
}

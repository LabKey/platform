/*
 * Copyright (c) 2004-2012 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.SimpleFilter.SQLClause;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * User: mbellew
 * Date: Apr 26, 2004
 * Time: 9:57:10 AM
 */
public class PropertyManager
{
    public static class PropertySchema
    {
        private static final PropertySchema _instance = new PropertySchema();
        private static final String SCHEMA_NAME = "prop";

        public static PropertySchema getInstance()
        {
            return _instance;
        }

        private PropertySchema()
        {
        }

        public String getSchemaName()
        {
            return SCHEMA_NAME;
        }

        public DbSchema getSchema()
        {
            return DbSchema.get(SCHEMA_NAME);
        }

        public SqlDialect getSqlDialect()
        {
            return getSchema().getSqlDialect();
        }

        public TableInfo getTableInfoProperties()
        {
            return getSchema().getTable("Properties");
        }

        public TableInfo getTableInfoPropertyEntries()
        {
            return getSchema().getTable("PropertyEntries");
        }

        public TableInfo getTableInfoPropertySets()
        {
            return getSchema().getTable("PropertySets");
        }
    }

    private static final Logger _log = Logger.getLogger(PropertyManager.class);
    private static final PropertySchema prop = PropertySchema.getInstance();

    public static final User SHARED_USER = User.guest;  // Shared properties are saved with user id 0


    private PropertyManager()
    {
    }

    // For global system properties that are attached to the root container
    // Returns an empty map if property set hasn't been created
    public static @NotNull Map<String, String> getProperties(String category)
    {
        return getProperties(SHARED_USER, ContainerManager.getRoot(), category);
    }


    // For shared properties that are attached to a specific container
    // Returns an empty map if property set hasn't been created
    public static @NotNull Map<String, String> getProperties(Container container, String category)
    {
        return getProperties(SHARED_USER, container, category);
    }


    public static @NotNull Map<String, String> getProperties(User user, Container container, String category)
    {
        Map<String, String> map = PropertyCache.getProperties(user, container, category);

        return null != map ? map : PropertyManager.NULL_MAP;
    }

    /**
     * This is designed to coalesce up the container hierarchy, returning the first non-null value
     * If a userId is provided, it first traverses containers using that user.  If no value is found,
     * it then default to all users (ie. 0), then retry all containers
     *
     * NOTE: this does not test permissions.  Callers should ensure the requested user has the appropriate
     * permissions to read these properties
     */
    public static String getCoalecedProperty(User user, Container c, String category, String name, boolean includeNullUser)
    {
        String value = getCoalecedPropertyForContainer(user, c, category, name);

        if(includeNullUser && value == null && user != SHARED_USER)
            value = getCoalecedPropertyForContainer(SHARED_USER, c, category, name);

        return value;
    }

    public static String getCoalecedProperty(User user, Container c, String category, String name)
    {
        return getCoalecedProperty(user, c, category, name, true);
    }

    private static String getCoalecedPropertyForContainer(User user, Container c, String category, String name)
    {
        Container curContainer = c;

        while (curContainer.isWorkbook())
            curContainer = curContainer.getParent();

        String value;

        while (curContainer != null)
        {
            value = getProperty(user, curContainer, category, name);
            if (value != null)
                return value;

            curContainer = curContainer.getParent();
        }

        return null;
    }

    /**
     * NOTE: does not test permissions
     */
    public static Map<Container, Map<Integer, String>> getPropertyValueAndAncestors(User user, Container c, String category, String name, boolean includeNullContainers)
    {
        Map<Container, Map<Integer, String>> map = new HashMap<Container, Map<Integer, String>>();
        Container curContainer = c;

        while (curContainer != null)
        {
            String value = getProperty(user, curContainer, category, name);
            Map<Integer, String> containerMap = new HashMap<Integer, String>();

            if(value != null)
                containerMap.put(user.getUserId(), value);
            else if (includeNullContainers)
                containerMap.put(0, value);

            if(!containerMap.isEmpty())
                map.put(curContainer, containerMap);

            curContainer = curContainer.getParent();
        }

        return map;
    }

    private static String getProperty(User user, Container container, String category, String name)
    {
        Map<String, String> props = PropertyManager.getProperties(user, container, category);
        return props.get(name);
    }

    // For global system properties that get attached to the root container
    public static PropertyMap getWritableProperties(String category, boolean create)
    {
        return getWritableProperties(SHARED_USER, ContainerManager.getRoot(), category, create);
    }


    public static PropertyMap getWritableProperties(Container container, String category, boolean create)
    {
        return getWritableProperties(SHARED_USER, container, category, create);
    }


    public static PropertyMap getWritableProperties(User user, Container container, String category, boolean create)
    {
        String containerId = container.getId().intern();
        try
        {
            prop.getSchema().getScope().ensureTransaction();

            synchronized (containerId)
            {
                ColumnInfo setColumn = prop.getTableInfoProperties().getColumn("Set");
                String setSelectName = setColumn.getSelectName();   // Keyword in some dialects

                Object[] params = new Object[]{user, container, category};

                SQLFragment sql = new SQLFragment("SELECT " + setSelectName + " FROM " + prop.getTableInfoPropertySets() +
                    " WHERE UserId = ? AND ObjectId = ? AND Category = ?");
                sql.addAll(params);

                Integer set = new SqlSelector(prop.getSchema(), sql).getObject(Integer.class);
                boolean newSet = (null == set);

                if (newSet)
                {
                    if (!create)
                    {
                        prop.getSchema().getScope().commitTransaction();
                        return null;
                    }
                    Map<String, Object> map = new HashMap<String, Object>();
                    map.put("UserId", user);
                    map.put("ObjectId", container);
                    map.put("Category", category);
                    Map<String, Object> mapOut = Table.insert(user, prop.getTableInfoPropertySets(), map);
                    set = (Integer)mapOut.get("Set");
                }

                PropertyMap m = new PropertyMap(set, user.getUserId(), containerId, category);

                if (newSet)
                {
                    // A brand new set, but we might have previously cached a NULL marker and/or another thread might
                    // try to create this same set before we save.
                    PropertyCache.remove(m);
                }
                else
                {
                    // Map-filling query needed only for existing property set
                    Filter filter = new SimpleFilter(setColumn.getFieldKey(), set);
                    new TableSelector(prop.getTableInfoProperties(), PageFlowUtil.set("Name", "Value"), filter, null).fillValueMap(m);
                }

                prop.getSchema().getScope().commitTransaction();
                return m;
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            prop.getSchema().getScope().closeConnection();
        }
    }

    static final PropertyMap NULL_MAP = new PropertyMap(0, 0, "NULL_MAP", PropertyManager.class.getName())
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


    public static void purgeObjectProperties(String objectId) throws SQLException
    {
        String setSelectName = prop.getTableInfoProperties().getColumn("Set").getSelectName();   // Keyword in some dialects

        String deleteProps = "DELETE FROM " + prop.getTableInfoProperties().getSelectName() +
                " WHERE " + setSelectName +  " IN " +
                "(SELECT " + setSelectName + " FROM " + prop.getTableInfoPropertySets().getSelectName() + " WHERE ObjectId=?)";

        Table.execute(prop.getSchema(), deleteProps, objectId);
        Table.delete(prop.getTableInfoPropertySets(), new SimpleFilter("ObjectId", objectId));
    }


    public static String getSchemaName()
    {
        return prop.getSchemaName();
    }


    public static DbSchema getSchema()
    {
        return prop.getSchema();
    }


    private static String _toNullString(Object o)
    {
        return null == o ? null : String.valueOf(o);
    }


    private static void _saveValue(int set, String name, String value)
    {
        if (null == name)
            return;

        String sql = prop.getSqlDialect().execute(prop.getSchema(), "property_setValue", "?, ?, ?");
        try
        {
            Table.execute(prop.getSchema(), sql, set, name, value);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public static void saveProperties(Map<String, String> map)
    {
        if (!(map instanceof PropertyMap))
            throw new IllegalArgumentException("map must be created by getProperties()");
        PropertyMap props = (PropertyMap)map;

        // Stored procedure property_saveValue is not thread-safe, so we synchronize the saving of each property set to
        // avoid attempting to modify the same property from two threads.  Use unique key + set id to avoid locking on a
        // shared interned string object.
        String lockString = "PropertyManager.Set=" + props.getSet();

        synchronized(lockString.intern())
        {
            // delete removed properties
            if (null != props.removedKeys)
            {
                for (Object removedKey : props.removedKeys)
                {
                    String name = _toNullString(removedKey);
                    _saveValue(props.getSet(), name, null);
                }
            }

            // set properties
            // we're not tracking modified or not, so set them all
            for (Object entry : props.entrySet())
            {
                Map.Entry e = (Map.Entry) entry;
                String name = _toNullString(e.getKey());
                String value = _toNullString(e.getValue());
                _saveValue(props.getSet(), name, value);
            }

            PropertyCache.remove(props);
        }
    }

    /**
     * Return full property entries. Use this function to return property entries that are part
     * of multiple property sets.
     *
     * @param user   User of the property. If null properties for all users (NOT JUST THE NULL USER) will be found.
     * @param container Container to search for. If null  properties of all containers will be found
     * @param category category to search for. If null  properties of all categories will be found
     * @param key      key to search for. If null all keys will be returned
     * @return array containing found properties
     */
    public static PropertyEntry[] findPropertyEntries(User user, Container container, String category, String key)
    {
        SimpleFilter filter = new SimpleFilter();
        if (null != user)
            filter.addCondition(FieldKey.fromString("UserId"), user.getUserId());
        if (null != container)
            filter.addCondition(FieldKey.fromString("ObjectId"), container.getId());
        if (null != category)
            filter.addCondition(FieldKey.fromString("Category"), category);
        if (null != key)
            filter.addCondition(FieldKey.fromString("Name"), key);

        Sort sort = new Sort("UserId,ObjectId,Category,Name");
        try
        {
            return Table.select(prop.getTableInfoPropertyEntries(), Table.ALL_COLUMNS, filter, sort, PropertyEntry.class);
        }
        catch (Exception x)
        {
            _log.error("selecting properties", x);
            return null;
        }
    }

    public static class PropertyMap extends HashMap<String, String>
    {
        private final int _set;
        private final int _userId;
        private final String _objectId;
        private final String _category;

        boolean _modified = false;
        Set<Object> removedKeys = null;


        private int getSet()
        {
            return _set;
        }


        private PropertyMap(int set, int userId, String objectId, String category)
        {
            _set = set;
            _userId = userId;
            _objectId = objectId;
            _category = category;
        }


        @Override
        public String remove(Object key)
        {
            if (null == removedKeys)
                removedKeys = new HashSet<Object>();
            if (this.containsKey(key))
            {
                removedKeys.add(key);
                _modified = true;
            }
            return super.remove(key);
        }


        @Override
        public String put(String key, @Nullable String value)
        {
            if (null != removedKeys)
                removedKeys.remove(key);
            if (!StringUtils.equals(value, get(value)))
                _modified = true;
            return super.put(key, value);
        }


        @Override
        public void putAll(Map<? extends String, ? extends String> m)
        {
            _modified = true;   // putAll() calls put(), but just to be safe
            super.putAll(m);
        }


        @Override
        public void clear()
        {
            if (null == removedKeys)
                removedKeys = new HashSet<Object>();
            removedKeys.addAll(keySet());
            _modified = true;
            super.clear();
        }


        @Override
        public Set<String> keySet()
        {
            return Collections.unmodifiableSet(super.keySet());
        }

        @Override
        public Collection<String> values()
        {
            return Collections.unmodifiableCollection(super.values());
        }

        @Override
        public Set<Map.Entry<String, String>> entrySet()
        {
            return Collections.unmodifiableSet(super.entrySet());
        }

        public Object[] getCacheParams()
        {
            return new Object[]{_userId, _objectId, _category};
        }
    }


    public static class PropertyEntry
    {
        private int _userId;
        private String _objectId;
        private String _category;
        private String _key;
        private String _value;

        public int getUserId()
        {
            return _userId;
        }

        public void setUserId(int userId)
        {
            this._userId = userId;
        }

        public String getObjectId()
        {
            return _objectId;
        }

        public void setObjectId(String _objectId)
        {
            this._objectId = _objectId;
        }

        public String getCategory()
        {
            return _category;
        }

        public void setCategory(String _category)
        {
            this._category = _category;
        }

        public String getKey()
        {
            return _key;
        }

        public void setKey(String key)
        {
            this._key = key;
        }

        public String getValue()
        {
            return _value;
        }

        public void setValue(String value)
        {
            this._value = value;
        }


    }

    public static class TestCase extends Assert
    {
        @Test
        public void test() throws SQLException
        {
            Container child = null;
            TestContext context = TestContext.get();
            User user = context.getUser();
            Container parent = JunitUtil.getTestContainer();

            try
            {
                child = ContainerManager.createContainer(parent, "Properties");

                // Do it twice to ensure multiple categories for same user and container works
                testProperties(user, child, "junit");
                testProperties(user, child, "junit2");
            }
            finally
            {
                if (child != null)
                {
                    //Properties should get cleaned up when container is deleted.
                    assertTrue(ContainerManager.delete(child, TestContext.get().getUser()));
                }
            }
            Map m = PropertyManager.getProperties(user, child, "junit");
            assertTrue(m == NULL_MAP);
            m = PropertyManager.getProperties(user, child, "junit2");
            assertTrue(m == NULL_MAP);
        }


        private void testProperties(User user, Container test, String category)
        {
            PropertyMap m = PropertyManager.getWritableProperties(user, test, category, true);
            assertNotNull(m);
            m.clear();
            PropertyManager.saveProperties(m);

            m = PropertyManager.getWritableProperties(user, test, category, false);
            m.put("foo", "bar");
            m.put("this", "that");
            m.put("zoo", null);
            PropertyManager.saveProperties(m);

            m = PropertyManager.getWritableProperties(user, test, category, false);
            assertEquals(m.get("foo"), "bar");
            assertEquals(m.get("this"), "that");
            assertFalse(m.containsKey("zoo"));

            m.remove("this");
            PropertyManager.saveProperties(m);

            m = PropertyManager.getWritableProperties(user, test, category, false);
            assertEquals(m.get("foo"), "bar");
            assertFalse(m.containsKey("this"));
            assertFalse(m.containsKey("zoo"));
        }


        @Test  // Note: Fairly worthless test... there's now an FK constraint in place
        public void testOrphanedPropertySets()
        {
            SimpleFilter filter = new SimpleFilter(new SQLClause("ObjectId NOT IN (SELECT EntityId FROM " + CoreSchema.getInstance().getTableInfoContainers() + ")", null, FieldKey.fromParts("ObjectId")));
            Selector selector = new TableSelector(prop.getTableInfoPropertySets(), filter, null);
            Long count = selector.getRowCount();

            // We found orphaned property sets... log them
            if (count != 0)
            {
                String topMessage = count + " orphaned property sets detected";
                final StringBuilder sb = new StringBuilder(topMessage + ":\ncategory\tset\tobjectid\n");
                selector.forEachMap(new Selector.ForEachBlock<Map<String, Object>>() {
                    @Override
                    public void exec(Map map) throws SQLException
                    {
                        sb.append(map.get("category") + "\t" + map.get("set") + "\t" + map.get("objectid") + "\n");
                    }
                });
                _log.error(topMessage + ":\n" + sb);
                fail(topMessage + "; see log for more details");
            }
        }
    }
}

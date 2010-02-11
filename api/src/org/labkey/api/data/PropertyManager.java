/*
 * Copyright (c) 2004-2010 Fred Hutchinson Cancer Research Center
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

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.User;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;

import java.sql.SQLException;
import java.util.*;


/**
 * User: mbellew
 * Date: Apr 26, 2004
 * Time: 9:57:10 AM
 */
public class PropertyManager
{
    private static class PropertySchema
    {
        private static PropertySchema instance = null;
        private static final String SCHEMA_NAME = "prop";

        public static PropertySchema getInstance()
        {
            if (null == instance)
                instance = new PropertySchema();

            return instance;
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

    private static Logger _log = Logger.getLogger(PropertyManager.class);
    private static PropertySchema prop = PropertySchema.getInstance();

    private static final String _cachePrefix = PropertyManager.class.getName() + "/";


    private PropertyManager()
    {
    }

    private static String _cacheKey(Object[] parameters)
    {
        return _cachePrefix + String.valueOf(parameters[0]) + "/" + _toEmptyString(parameters[1]) + "/" + _toEmptyString(parameters[2]);
    }

    // For global system properties that get attached to the root container
    // Returns an empty map if property set hasn't been created
    public static @NotNull Map<String, String> getProperties(String category)
    {
        return getProperties(0, ContainerManager.getRoot().getId(), category);
    }


    public static @NotNull Map<String, String> getProperties(String objectId, String category)
    {
        return getProperties(0, objectId, category);
    }


    public static @NotNull Map<String, String> getProperties(int userId, String objectId, String category)
    {
        Object[] parameters = new Object[]{userId, objectId, category};
        TableInfo tinfo;
        String cacheKey;

        // Pull property set from the cache
        tinfo = prop.getTableInfoProperties();
        cacheKey = _cacheKey(parameters);
        Object o = DbCache.get(tinfo, cacheKey);

        if (o instanceof PropertyMap)
            return (PropertyMap) o;

        // Not found in the cache,
        PropertyMap m = getWritableProperties(userId, objectId, category, false);

        if (null == m)
            m = NULL_MAP;

        DbCache.put(tinfo, cacheKey, m);

        return NULL_MAP == m ? m : Collections.unmodifiableMap(m);
    }


    // For global system properties that get attached to the root container
    public static PropertyMap getWritableProperties(String category, boolean create)
    {
        return getWritableProperties(0, ContainerManager.getRoot().getId(), category, create);
    }


    public static PropertyMap getWritableProperties(String objectId, String category, boolean create)
    {
        return getWritableProperties(0, objectId, category, create);
    }


    public static PropertyMap getWritableProperties(int userId, String objectId, String category, boolean create)
    {
        synchronized (objectId.intern())
        {
            // TODO: Should transact set/properties
            try
            {
                String setSelectName = prop.getTableInfoProperties().getColumn("Set").getSelectName();   // Keyword in some dialects

                Object[] parameters = new Object[]{userId, objectId, category};

                Integer set = Table.executeSingleton(prop.getSchema(),
                        "SELECT " + setSelectName + " FROM " + prop.getTableInfoPropertySets() + " WHERE UserId=? AND ObjectId=? AND Category=?",
                        parameters, Integer.class);

                if (null == set)
                {
                    if (!create)
                        return null;
                    Table.execute(prop.getSchema(),
                            "INSERT INTO " + prop.getTableInfoPropertySets() + " (UserId, ObjectId, Category) VALUES (?,?,?)",
                            parameters);
                    set = Table.executeSingleton(prop.getSchema(),
                            "SELECT " + setSelectName + " FROM " + prop.getTableInfoPropertySets() + " WHERE UserId=? AND ObjectId=? AND Category=?",
                            parameters, Integer.class);
                }

                PropertyMap m = new PropertyMap(set.intValue(), userId, objectId, category);
                Table.executeValueMap(prop.getSchema(),
                        "SELECT Name, Value FROM " + prop.getTableInfoProperties() + " WHERE " + setSelectName + "=?",
                        new Object[]{set},
                        (Map)m);

                return m;
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }
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

        Table.execute(prop.getSchema(), deleteProps, new Object[] {objectId});
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


    private static String _toEmptyString(Object o)
    {
        return null == o ? "" : String.valueOf(o);
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
            Table.execute(prop.getSchema(), sql, new Object[]{set, name, value});
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public static void saveProperties(Map<String,String> map)
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

            DbCache.remove(prop.getTableInfoProperties(), _cacheKey(props.getCacheParams()));
        }
    }

    /**
     * Return full property entries. Use this function to return property entries that are part
     * of multiple property sets.
     *
     * @param userId   UserId of the property. If null properties for all users (NOT JUST THE NULL USER) will be found.
     * @param objectId objectId (usually containerId) to search for. If null  properties of all containers will be found
     * @param category category to search for. If null  properties of all categories will be found
     * @param key      key to search for. If null all keys will be returned
     * @return array containing found properties
     */
    public static PropertyEntry[] findPropertyEntries(Integer userId, String objectId, String category, String key)
    {
        SimpleFilter filter = new SimpleFilter();
        if (null != userId)
            filter.addCondition("UserId", userId);
        if (null != objectId)
            filter.addCondition("ObjectId", objectId);
        if (null != category)
            filter.addCondition("Category", category);
        if (null != key)
            filter.addCondition("Name", key);

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
                removedKeys.add(key);
            return super.remove(key);
        }


        @Override
        public String put(String key, String value)
        {
            if (null != removedKeys)
                removedKeys.remove(key);
            return super.put(key, value);
        }


        @Override
        public void clear()
        {
            if (null == removedKeys)
                removedKeys = new HashSet<Object>();
            removedKeys.addAll(keySet());
            super.clear();
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

    public static class TestCase extends junit.framework.TestCase
    {
        public TestCase()
        {
            super();
        }


        public TestCase(String name)
        {
            super(name);
        }


        public void test()
        {
            try
            {
                TestContext context = TestContext.get();

                User user = context.getUser();
                Container parent = JunitUtil.getTestContainer();
                Container child = ContainerManager.createContainer(parent, "Properties");
                String objectId = child.getId();

                // Do it twice to ensure multiple categories for same user and container works
                testProperties(user, child, "junit");
                testProperties(user, child, "junit2");
                //Properties should get cleaned up when container is deleted.
                ContainerManager.delete(child, null);
                Map m = PropertyManager.getProperties(user.getUserId(), objectId, "junit");
                assertTrue(m == NULL_MAP);
                m = PropertyManager.getProperties(user.getUserId(), objectId, "junit2");
                assertTrue(m == NULL_MAP);
            }
            catch (SQLException x)
            {
                assertTrue(x.getMessage(), false);
            }
        }


        private void testProperties(User user, Container test, String category) throws SQLException
        {
            PropertyMap m = PropertyManager.getWritableProperties(user.getUserId(), test.getId(), category, true);
            assertNotNull(m);
            m.clear();
            PropertyManager.saveProperties(m);

            m = PropertyManager.getWritableProperties(user.getUserId(), test.getId(), category, false);
            m.put("foo", "bar");
            m.put("this", "that");
            m.put("zoo", null);
            PropertyManager.saveProperties(m);

            m = PropertyManager.getWritableProperties(user.getUserId(), test.getId(), category, false);
            assertEquals(m.get("foo"), "bar");
            assertEquals(m.get("this"), "that");
            assertFalse(m.containsKey("zoo"));

            m.remove("this");
            PropertyManager.saveProperties(m);

            m = PropertyManager.getWritableProperties(user.getUserId(), test.getId(), category, false);
            assertEquals(m.get("foo"), "bar");
            assertFalse(m.containsKey("this"));
            assertFalse(m.containsKey("zoo"));
        }


        public void testOrphanedPropertySets()
        {
            try
            {
                String sql = "SELECT COUNT(*) FROM " +  prop.getTableInfoPropertySets() + " WHERE ObjectId NOT IN (SELECT EntityId FROM " + CoreSchema.getInstance().getTableInfoContainers() + ")";

                Long count = Table.executeSingleton(getSchema(), sql, new Object[]{}, Long.class);

                assertEquals(count.intValue() + " orphaned property sets detected", 0, count.intValue());
            }
            catch (SQLException x)
            {
                assertTrue(x.getMessage(), false);
            }
        }


        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }
}

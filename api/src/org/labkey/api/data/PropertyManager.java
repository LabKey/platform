/*
 * Copyright (c) 2004-2015 Fred Hutchinson Cancer Research Center
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
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * User: mbellew
 * Date: Apr 26, 2004
 * Time: 9:57:10 AM
 */
public class PropertyManager
{
    public static final User SHARED_USER = User.guest;  // Shared properties are saved with the guest user

    private static final Logger _log = Logger.getLogger(PropertyManager.class);
    private static final PropertySchema prop = PropertySchema.getInstance();
    private static final NormalPropertyStore STORE = new NormalPropertyStore();
    private static final EncryptedPropertyStore ENCRYPTED_STORE = new EncryptedPropertyStore();

    private PropertyManager()
    {
    }


    public static PropertyStore getNormalStore()
    {
        return STORE;
    }


    public static PropertyStore getEncryptedStore()
    {
        return ENCRYPTED_STORE;
    }


    /**
     * For global system properties that are attached to the root container
     * Returns an empty map if property set hasn't been created
     */
    public static @NotNull Map<String, String> getProperties(String category)
    {
        return STORE.getProperties(category);
    }

    /**
     * For shared properties that are attached to a specific container
     * Returns an empty map if property set hasn't been created
     */
    public static @NotNull Map<String, String> getProperties(Container container, String category)
    {
        return STORE.getProperties(container, category);
    }

    /**
     * For shared properties that are attached to a specific container and user
     * Returns an empty map if property set hasn't been created
     */
    public static @NotNull Map<String, String> getProperties(User user, Container container, String category)
    {
        return STORE.getProperties(user, container, category);
    }


    /** For global system properties that get attached to the root container. */
    public static PropertyMap getWritableProperties(String category, boolean create)
    {
        return STORE.getWritableProperties(category, create);
    }


    public static PropertyMap getWritableProperties(Container container, String category, boolean create)
    {
        return STORE.getWritableProperties(container, category, create);
    }


    public static PropertyMap getWritableProperties(User user, Container container, String category, boolean create)
    {
        return STORE.getWritableProperties(user, container, category, create);
    }


    public static void saveProperties(Map<String, String> map)
    {
        STORE.saveProperties(map);
    }


    public static void purgeObjectProperties(Container c) throws SQLException
    {
        STORE.deleteProperties(c);
        ENCRYPTED_STORE.deleteProperties(c);
    }


    /**
     * This is designed to coalesce up the container hierarchy, returning the first non-null value
     * If a userId is provided, it first traverses containers using that user.  If no value is found,
     * it then default to all users (ie. User.guest), then retry all containers
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
        Map<Container, Map<Integer, String>> map = new HashMap<>();
        Container curContainer = c;

        while (curContainer != null)
        {
            String value = getProperty(user, curContainer, category, name);
            Map<Integer, String> containerMap = new HashMap<>();

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

    public static String getProperty(User user, Container container, String category, String name)
    {
        Map<String, String> props = getProperties(user, container, category);
        return props.get(name);
    }

    /**
     * Get a list of categories optionally filtered by user, container, and category prefix.
     * eturns entries from unencrypted store only.
     *
     * @param user   User of the property. If null properties for all users (NOT JUST THE NULL USER) will be found.
     * @param container Container to search for. If null properties of all containers will be found
     * @param categoryPrefix Prefix to search for. If null properties of all categories will be found
     * @return list of categories array containing found properties
     */
    public static List<String> getCategoriesByPrefix(@Nullable User user, @Nullable Container container, @Nullable String categoryPrefix)
    {
        SimpleFilter filter = new SimpleFilter();
        if (null != user)
            filter.addCondition(FieldKey.fromString("UserId"), user.getUserId());
        if (null != container)
            filter.addCondition(FieldKey.fromString("ObjectId"), container.getId());
        if (null != categoryPrefix)
            filter.addCondition(FieldKey.fromString("Category"), categoryPrefix, CompareType.STARTS_WITH);

        Sort sort = new Sort("UserId,ObjectId,Category,Name");

        return new TableSelector(prop.getTableInfoPropertyEntries(), Collections.singleton("Category"), filter, sort).getArrayList(String.class);
    }

    /**
     * Return full property entries. Use this function to return property entries that are part
     * of multiple property sets. Returns entries from unencrypted store only.
     *
     * @param user   User of the property. If null properties for all users (NOT JUST THE NULL USER) will be found.
     * @param container Container to search for. If null  properties of all containers will be found
     * @param category category to search for. If null  properties of all categories will be found
     * @param key      key to search for. If null all keys will be returned
     * @return array containing found properties
     */
    public static PropertyEntry[] findPropertyEntries(@Nullable User user, @Nullable Container container, @Nullable String category, @Nullable String key)
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

        return new TableSelector(prop.getTableInfoPropertyEntries(), filter, sort).getArray(PropertyEntry.class);
    }

    public static class PropertyMap extends HashMap<String, String>
    {
        private final int _set;
        private final int _userId;
        private final String _objectId;
        private final String _category;
        private final PropertyEncryption _propertyEncryption;

        private boolean _modified = false;
        Set<Object> removedKeys = null;


        PropertyMap(int set, int userId, String objectId, String category, PropertyEncryption propertyEncryption)
        {
            _set = set;
            _userId = userId;
            _objectId = objectId;
            _category = category;
            _propertyEncryption = propertyEncryption;
        }


        int getSet()
        {
            return _set;
        }


        PropertyEncryption getEncryptionAlgorithm()
        {
            return _propertyEncryption;
        }

        @Override
        public String remove(Object key)
        {
            if (null == removedKeys)
                removedKeys = new HashSet<>();
            if (containsKey(key))
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
                removedKeys = new HashSet<>();
            removedKeys.addAll(keySet());
            _modified = true;
            super.clear();
        }


        @NotNull
        @Override
        public Set<String> keySet()
        {
            return Collections.unmodifiableSet(super.keySet());
        }

        @NotNull
        @Override
        public Collection<String> values()
        {
            return Collections.unmodifiableCollection(super.values());
        }

        @NotNull
        @Override
        public Set<Map.Entry<String, String>> entrySet()
        {
            return Collections.unmodifiableSet(super.entrySet());
        }

        public Object[] getCacheParams()
        {
            return new Object[]{_objectId, _userId, _category};
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;

            PropertyMap that = (PropertyMap) o;

            if (_set != that._set) return false;
            if (_userId != that._userId) return false;
            if (_category != null ? !_category.equals(that._category) : that._category != null) return false;
            if (_objectId != null ? !_objectId.equals(that._objectId) : that._objectId != null) return false;

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = super.hashCode();
            result = 31 * result + _set;
            result = 31 * result + _userId;
            result = 31 * result + (_objectId != null ? _objectId.hashCode() : 0);
            result = 31 * result + (_category != null ? _category.hashCode() : 0);
            return result;
        }

        public boolean isModified()
        {
            return _modified;
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
            _userId = userId;
        }

        public String getObjectId()
        {
            return _objectId;
        }

        public void setObjectId(String objectId)
        {
            _objectId = objectId;
        }

        public String getCategory()
        {
            return _category;
        }

        public void setCategory(String category)
        {
            _category = category;
        }

        public String getKey()
        {
            return _key;
        }

        public void setKey(String key)
        {
            _key = key;
        }

        public String getValue()
        {
            return _value;
        }

        public void setValue(String value)
        {
            _value = value;
        }
    }

    public static class TestCase extends Assert
    {
        interface PropertyStoreTest
        {
            void runTest(PropertyStore store, User user, Container c);
            Collection<String> getCategories();
        }

        @Test
        public void test() throws SQLException
        {
            PropertyStore normal = getNormalStore();
            PropertyStore encrypted = getEncryptedStore();

            // Test should still pass whether MasterEncryptionKey is specified or not
            if (ENCRYPTED_STORE.getPreferredPropertyEncryption() == PropertyEncryption.NoKey)
            {
                // Just test the normal property store
                testPropertyStore(normal, (PropertyStore) null);

                // Validate that encrypted store with no key specified can't be used
                testPropertyStore(encrypted, new PropertyStoreTest()
                {
                    @Override
                    public void runTest(PropertyStore store, User user, Container c)
                    {
                        try
                        {
                            store.getWritableProperties(user, c, "this_should_break", true);
                            fail("Expected ConfigurationException");
                        }
                        catch (ConfigurationException ignored)
                        {
                        }

                        try
                        {
                            store.getWritableProperties(user, c, "this_should_break", false);
                            fail("Expected ConfigurationException");
                        }
                        catch (ConfigurationException ignored)
                        {
                        }

                        try
                        {
                            store.getProperties(user, c, "this_should_break");
                            fail("Expected ConfigurationException");
                        }
                        catch (ConfigurationException ignored)
                        {
                        }
                    }

                    @Override
                    public Collection<String> getCategories()
                    {
                        return Collections.emptySet();
                    }
                });

            }
            else
            {
                testPropertyStore(normal, encrypted);
                testPropertyStore(encrypted, normal);
            }
        }


        private void testPropertyStore(final PropertyStore store, final @Nullable PropertyStore badStore)
        {
            testPropertyStore(store, new PropertyStoreTest()
            {
                @Override
                public void runTest(PropertyStore store, User user, Container c)
                {
                    // Do it twice to ensure multiple categories for same user and container works
                    testProperties(store, user, c, "junit", badStore);
                    testProperties(store, user, c, "junit2", badStore);
                }

                @Override
                public Collection<String> getCategories()
                {
                    return PageFlowUtil.set("junit", "junit2");
                }
            });
        }


        private void testPropertyStore(PropertyStore store, PropertyStoreTest test)
        {
            TestContext context = TestContext.get();
            User user = context.getUser();
            Container parent = JunitUtil.getTestContainer();
            Container child = null;

            try
            {
                child = ContainerManager.createContainer(parent, "Properties");

                test.runTest(store, user, child);
            }
            finally
            {
                if (child != null)
                {
                    //Properties should get cleaned up when container is deleted.
                    assertTrue(ContainerManager.delete(child, TestContext.get().getUser()));
                }
            }

            // All categories created by test should be gone
            for (String category : test.getCategories())
            {
                Map m = store.getProperties(user, child, category);
                assertTrue(m == AbstractPropertyStore.NULL_MAP);
            }
        }


        private void testProperties(PropertyStore store, User user, Container test, String category, @Nullable PropertyStore badStore)
        {
            PropertyMap m = store.getWritableProperties(user, test, category, true);
            assertNotNull(m);
            m.clear();
            store.saveProperties(m);

            m = store.getWritableProperties(user, test, category, false);
            m.put("foo", "bar");
            m.put("this", "that");
            m.put("zoo", null);
            store.saveProperties(m);

            m = store.getWritableProperties(user, test, category, false);
            assertEquals(m.get("foo"), "bar");
            assertEquals(m.get("this"), "that");
            assertFalse(m.containsKey("zoo"));

            m.remove("this");
            store.saveProperties(m);

            Map<String, String> map = store.getProperties(user, test, category);
            assertEquals(map.get("foo"), "bar");
            assertFalse(map.containsKey("this"));
            assertFalse(map.containsKey("zoo"));

            if (null != badStore)
            {
                try
                {
                    badStore.saveProperties(m);
                    fail("Expected IllegalStateException");
                }
                catch (IllegalStateException e)
                {
                }

                try
                {
                    badStore.getProperties(user, test, category);
                    fail("Expected IllegalStateException");
                }
                catch (IllegalStateException e)
                {
                }
            }
        }


        @Test  // Note: Fairly worthless test... there's now an FK constraint in place
        public void testOrphanedPropertySets()
        {
            SimpleFilter filter = new SimpleFilter(new SQLClause("ObjectId NOT IN (SELECT EntityId FROM " + CoreSchema.getInstance().getTableInfoContainers() + ")", null, FieldKey.fromParts("ObjectId")));
            Selector selector = new TableSelector(prop.getTableInfoPropertySets(), filter, null);
            long count = selector.getRowCount();

            // We found orphaned property sets... log them
            if (count != 0)
            {
                String topMessage = count + " orphaned property sets detected";
                final StringBuilder sb = new StringBuilder(topMessage + ":\ncategory\tset\tobjectid\n");
                selector.forEachMap(new Selector.ForEachBlock<Map<String, Object>>() {
                    @Override
                    public void exec(Map map) throws SQLException
                    {
                        sb.append(map.get("category")).append("\t").append(map.get("set")).append("\t").append(map.get("objectid")).append("\n");
                    }
                });
                _log.error(topMessage + ":\n" + sb);
                fail(topMessage + "; see log for more details");
            }
        }
    }
}

/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.springframework.beans.factory.InitializingBean;

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

    private static final PropertySchema SCHEMA = PropertySchema.getInstance();

    private static final LockManager<PropertyMap> PROPERTY_MAP_LOCK_MANAGER = new LockManager<>();

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
    public static @NotNull PropertyManager.PropertyMap getProperties(String category)
    {
        return STORE.getProperties(category);
    }

    /**
     * For shared properties that are attached to a specific container
     * Returns an empty map if property set hasn't been created
     */
    public static @NotNull PropertyManager.PropertyMap getProperties(Container container, String category)
    {
        return STORE.getProperties(container, category);
    }

    /**
     * For shared properties that are attached to a specific container and user
     * Returns an empty map if property set hasn't been created
     */
    public static @NotNull PropertyManager.PropertyMap getProperties(User user, Container container, String category)
    {
        return STORE.getProperties(user, container, category);
    }


    /** For global system properties that get attached to the root container. */
    public static PropertyMap getWritableProperties(String category, boolean create)
    {
        PropertyMap ret = STORE.getWritableProperties(category, create);
        assert !ret.isModified();
        return ret;
    }


    public static PropertyMap getWritableProperties(Container container, String category, boolean create)
    {
        PropertyMap ret = STORE.getWritableProperties(container, category, create);
        assert !ret.isModified();
        return ret;
    }


    public static PropertyMap getWritableProperties(User user, Container container, String category, boolean create)
    {
        PropertyMap ret = STORE.getWritableProperties(user, container, category, create);
        assert !ret.isModified();
        return ret;
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

        while (curContainer != null && curContainer.isWorkbook())
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

    public static class PropertyMap extends HashMap<String, String> implements InitializingBean
    {
        private int _set;
        @NotNull
        private final User _user;
        @NotNull
        private final String _objectId;
        @NotNull
        private final String _category;
        private final PropertyEncryption _propertyEncryption;
        private final AbstractPropertyStore _store;

        private boolean _modified = false;
        Set<Object> removedKeys = null;
        private boolean _locked = false;

        PropertyMap(int set, @NotNull User user, @NotNull String objectId, @NotNull String category, PropertyEncryption propertyEncryption, AbstractPropertyStore store)
        {
            _set = set;
            _user = user;
            _objectId = objectId;
            _category = category;
            _propertyEncryption = propertyEncryption;
            _store = store;
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
            checkLocked();
            if (null == removedKeys)
                removedKeys = new HashSet<>();
            if (containsKey(key))
            {
                removedKeys.add(key);
                _modified = true;
            }
            return super.remove(key);
        }

        private void checkLocked()
        {
            if (_locked)
            {
                throw new IllegalStateException("Cannot modify a locked PropertyMap - use getWritableProperties() for a mutable copy");
            }
        }


        @Override
        public String put(String key, @Nullable String value)
        {
            checkLocked();
            if (null != removedKeys)
                removedKeys.remove(key);
            if (!StringUtils.equals(value, get(key)))
                _modified = true;
            return super.put(key, value);
        }

        @Override
        public String toString()
        {
            return "PropertyMap: " + _objectId + ", " + _category + ", " + _user.getDisplayName(null) + ": " + super.toString();
        }

        @Override
        public void putAll(Map<? extends String, ? extends String> m)
        {
            checkLocked();
            _modified = true;   // putAll() calls put(), but just to be safe
            super.putAll(m);
        }


        @Override
        public void clear()
        {
            checkLocked();
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

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PropertyMap that = (PropertyMap) o;

            if (!_user.equals(that._user)) return false;
            if (!_category.equals(that._category)) return false;
            if (!_objectId.equals(that._objectId)) return false;

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = _user.hashCode();
            result = 31 * result + _objectId.hashCode();
            result = 31 * result + _category.hashCode();
            return result;
        }

        public boolean isModified()
        {
            return _modified;
        }


        @Override
        public void afterPropertiesSet()
        {
            _modified = false;
        }


        private static String toNullString(Object o)
        {
            return null == o ? null : String.valueOf(o);
        }

        private void saveValue(String name, String value)
        {
            if (null == name)
                return;

            String sql = SCHEMA.getSqlDialect().execute(SCHEMA.getSchema(), "property_setValue", "?, ?, ?");

            new SqlExecutor(SCHEMA.getSchema()).execute(sql, getSet(), name, _store.getSaveValue(this, value));
        }

        public void save()
        {
            _store.validateStore();
            _store.validatePropertyMap(this);

            if (!isModified())
            {
                // No changes, so it's safe to bail out immediately
                return;
            }

            // Lock on code that changes the underlying database storage, to avoid constraint violations if we have multiple
            // threads manipulating at the same time
            try (DbScope.Transaction transaction = SCHEMA.getSchema().getScope().ensureTransaction(DbScope.FINAL_COMMIT_UNLOCK_TRANSACTION_KIND, PROPERTY_MAP_LOCK_MANAGER.getLock(this)))
            {
                if (_set == -1)
                {
                    Container container = ContainerManager.getForId(_objectId);
                    if (container == null)
                    {
                        throw new IllegalStateException("No container found with EntityId: " + _objectId);
                    }
                    PropertyMap existingMap = _store.getWritableProperties(_user, container, _category, false);
                    if (existingMap == null)
                    {
                        Map<String, Object> insertMap = new HashMap<>();
                        insertMap.put("UserId", _user);
                        insertMap.put("ObjectId", _objectId);
                        insertMap.put("Category", _category);
                        insertMap.put("Encryption", _propertyEncryption.getSerializedName());
                        insertMap = Table.insert(_user, SCHEMA.getTableInfoPropertySets(), insertMap);
                        _set = (Integer) insertMap.get("Set");
                    }
                    else
                    {
                        _set = existingMap.getSet();
                    }
                }

                // delete removed properties
                if (null != removedKeys)
                {
                    for (Object removedKey : removedKeys)
                    {
                        String name = toNullString(removedKey);
                        saveValue(name, null);
                    }
                }

                // set properties
                // we're not tracking modified or not, so set them all
                for (Object entry : entrySet())
                {
                    Map.Entry e = (Map.Entry) entry;
                    String name = toNullString(e.getKey());
                    String value = toNullString(e.getValue());
                    saveValue(name, value);
                }
                _store.clearCache(this);
                transaction.commit();
            }
        }

        public void delete()
        {
            // Lock on code that changes the underlying database storage, to avoid constraint violations if we have multiple
            // threads manipulating at the same time
            try (DbScope.Transaction transaction = SCHEMA.getSchema().getScope().ensureTransaction(DbScope.FINAL_COMMIT_UNLOCK_TRANSACTION_KIND, PROPERTY_MAP_LOCK_MANAGER.getLock(this)))
            {
                deleteSetDirectly(_user, _objectId, _category, _store);

                _store.clearCache(this);

                transaction.commit();
            }
        }

        @NotNull
        public String getObjectId()
        {
            return _objectId;
        }


        @NotNull
        public User getUser()
        {
            return _user;
        }

        @NotNull
        public String getCategory()
        {
            return _category;
        }

        public void lock()
        {
            _locked = true;
        }

        public boolean isLocked()
        {
            return _locked;
        }
    }

    /**
     * In general, callers should always go through PropertyMap.delete(). This is exposed here for cases
     * where we can't create a PropertyMap because we fail to decrypt the values previously store, and need
     * a direct way to delete the properties. See issue 18938
     */
    public static void deleteSetDirectly(User user, String objectId, String category, AbstractPropertyStore store)
    {
        String setSelectName = SCHEMA.getTableInfoProperties().getColumn("Set").getSelectName();   // Keyword in some dialects

        SQLFragment deleteProps = new SQLFragment();
        deleteProps.append("DELETE FROM ").append(SCHEMA.getTableInfoProperties().getSelectName());
        deleteProps.append(" WHERE ").append(setSelectName).append(" IN ");
        deleteProps.append("(SELECT ").append(setSelectName).append(" FROM ").append(SCHEMA.getTableInfoPropertySets(), "ps");
        deleteProps.append(" WHERE UserId = ? AND ObjectId = ? AND Category = ? AND ");
        deleteProps.add(user);
        deleteProps.add(objectId);
        deleteProps.add(category);

        store.appendWhereFilter(deleteProps);

        deleteProps.append(")");

        SqlExecutor sqlx = new SqlExecutor(SCHEMA.getSchema());
        sqlx.execute(deleteProps);

        SQLFragment deleteSets = new SQLFragment("DELETE FROM " + SCHEMA.getTableInfoPropertySets() + " WHERE UserId = ? AND ObjectId = ? AND Category = ? AND ");
        deleteSets.add(user);
        deleteSets.add(objectId);
        deleteSets.add(category);

        store.appendWhereFilter(deleteSets);

        new SqlExecutor(SCHEMA.getSchema()).execute(deleteSets);
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


    @TestWhen(TestWhen.When.BVT)
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
                testPropertyStore(normal);

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
                testPropertyStore(normal);
                testPropertyStore(encrypted);
            }
        }


        private void testPropertyStore(final PropertyStore store)
        {
            testPropertyStore(store, new PropertyStoreTest()
            {
                @Override
                public void runTest(PropertyStore store, User user, Container c)
                {
                    // Do it twice to ensure multiple categories for same user and container works
                    testProperties(store, user, c, "junit");
                    testProperties(store, user, c, "junit2");
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


        private void testProperties(PropertyStore store, User user, Container test, String category)
        {
            PropertyMap m = store.getWritableProperties(user, test, category, true);
            assertNotNull(m);
            m.clear();
            m.save();

            m = store.getWritableProperties(user, test, category, false);
            m.put("foo", "bar");
            m.put("this", "that");
            m.put("zoo", null);
            m.save();

            m = store.getWritableProperties(user, test, category, false);
            assertEquals(m.get("foo"), "bar");
            assertEquals(m.get("this"), "that");
            assertFalse(m.containsKey("zoo"));

            m.remove("this");
            m.save();

            Map<String, String> map = store.getProperties(user, test, category);
            assertEquals(map.get("foo"), "bar");
            assertFalse(map.containsKey("this"));
            assertFalse(map.containsKey("zoo"));
        }

        @Test
        public void testSynchronization() throws InterruptedException
        {
            final Container c = JunitUtil.getTestContainer();
            final String category = "TestPropertySetName";
            final PropertyStore store = PropertyManager.getNormalStore();
            final DbScope scope = PropertySchema.getInstance().getSchema().getScope();

            JunitUtil.createRaces(new Runnable()
            {
                @Override
                public void run()
                {
                    try (DbScope.Transaction transaction = scope.ensureTransaction())
                    {
                        PropertyMap map = store.getWritableProperties(c, category, true);
                        map.put("foo", "abc");
                        map.put("bar", "xyz");
                        map.save();
                        Map<String, String> newMap = store.getProperties(c, category);
                        map = store.getWritableProperties(c, category, true);
                        map.put("flam", "mno");
                        map.delete();

                        transaction.commit();
                    }
                }
            }, 20, 10, 30);
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

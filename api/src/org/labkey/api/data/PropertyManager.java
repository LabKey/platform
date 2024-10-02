/*
 * Copyright (c) 2004-2018 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.collections4.IterableMap;
import org.apache.commons.collections4.MapIterator;
import org.apache.commons.collections4.collection.UnmodifiableCollection;
import org.apache.commons.collections4.iterators.EntrySetMapIterator;
import org.apache.commons.collections4.iterators.UnmodifiableMapIterator;
import org.apache.commons.collections4.map.AbstractMapDecorator;
import org.apache.commons.collections4.map.UnmodifiableEntrySet;
import org.apache.commons.collections4.set.UnmodifiableSet;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CollectionUtils;
import org.labkey.api.data.DbScope.Transaction;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.Encryption.EncryptionMigrationHandler;
import org.labkey.api.security.User;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.logging.LogHelper;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

public class PropertyManager
{
    public static final User SHARED_USER = User.guest;  // Shared properties are saved with the guest user

    private static final Logger LOG = LogHelper.getLogger(PropertyManager.class, "Property map save and delete operations");
    private static final PropertySchema PROP_SCHEMA = PropertySchema.getInstance();
    private static final NormalPropertyStore STORE = new NormalPropertyStore();
    private static final PropertySchema SCHEMA = PropertySchema.getInstance();
    private static final LockManager<PropertyMap> PROPERTY_MAP_LOCK_MANAGER = new LockManager<>();

    static final EncryptedPropertyStore ENCRYPTED_STORE = new EncryptedPropertyStore();

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
    public static @NotNull PropertyMap getProperties(String category)
    {
        return STORE.getProperties(category);
    }

    /**
     * For shared properties that are attached to a specific container
     * Returns an empty map if property set hasn't been created
     */
    public static @NotNull PropertyMap getProperties(Container container, String category)
    {
        return STORE.getProperties(container, category);
    }

    /**
     * For shared properties that are attached to a specific container and user
     * Returns an empty map if property set hasn't been created
     */
    public static @NotNull PropertyMap getProperties(User user, Container container, String category)
    {
        return STORE.getProperties(user, container, category);
    }

    private static boolean assertWritableProperties(@Nullable WritablePropertyMap writableProps, boolean create)
    {
        // getWritableProperties() will return null if an existing map is not found and create is false.
        if (writableProps == null)
            assert !create;
        else
            assert !writableProps.isModified();

        return true;
    }

    /** For global system properties that get attached to the root container. */
    public static WritablePropertyMap getWritableProperties(String category, boolean create)
    {
        WritablePropertyMap ret = STORE.getWritableProperties(category, create);
        assert assertWritableProperties(ret, create);
        return ret;
    }


    public static WritablePropertyMap getWritableProperties(Container container, String category, boolean create)
    {
        WritablePropertyMap ret = STORE.getWritableProperties(container, category, create);
        assert assertWritableProperties(ret, create);
        return ret;
    }


    public static WritablePropertyMap getWritableProperties(User user, Container container, String category, boolean create)
    {
        WritablePropertyMap ret = STORE.getWritableProperties(user, container, category, create);
        assert assertWritableProperties(ret, create);
        return ret;
    }


    public static void purgeObjectProperties(Container c)
    {
        STORE.deleteProperties(c);
        ENCRYPTED_STORE.deleteProperties(c);
    }

    // Register a handler so encrypted store can migrate property values whenever encryption key changes
    public static void registerEncryptionMigrationHandler()
    {
        EncryptionMigrationHandler.registerHandler(ENCRYPTED_STORE);
    }

    /**
     * This is designed to coalesce up the container hierarchy, returning the first non-null value
     * If a userId is provided, it first traverses containers using that user. If no value is found,
     * it then defaults to all users (i.e. User.guest), then retry all containers
     * NOTE: this does not test permissions. Callers should ensure the requested user has the appropriate
     * permissions to read these properties
     */
    public static String getCoalescedProperty(User user, Container c, String category, String name, boolean includeNullUser)
    {
        String value = getCoalescedPropertyForContainer(user, c, category, name);

        if (includeNullUser && value == null && user != SHARED_USER)
            value = getCoalescedPropertyForContainer(SHARED_USER, c, category, name);

        return value;
    }

    public static String getCoalescedProperty(User user, Container c, String category, String name)
    {
        return getCoalescedProperty(user, c, category, name, true);
    }

    private static String getCoalescedPropertyForContainer(User user, Container c, String category, String name)
    {
        Container curContainer = c;

        while (curContainer != null && !curContainer.isContainerFor(ContainerType.DataType.properties))
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

            if (value != null)
                containerMap.put(user.getUserId(), value);
            else if (includeNullContainers)
                containerMap.put(0, value);

            if (!containerMap.isEmpty())
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
     * Returns entries from unencrypted store only.
     *
     * @param user   User of the property. If null, properties for all users (NOT JUST THE NULL USER) will be found.
     * @param container Container to search for. If null, properties of all containers will be found
     * @param categoryPrefix Prefix to search for. If null, properties of all categories will be found
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

        return new TableSelector(PROP_SCHEMA.getTableInfoPropertyEntries(), Collections.singleton("Category"), filter, sort).getArrayList(String.class);
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

        return new TableSelector(PROP_SCHEMA.getTableInfoPropertyEntries(), filter, sort).getArray(PropertyEntry.class);
    }

    // Instances of this specific class are guaranteed to be immutable; all mutating Map methods (put(), remove(), etc.)
    // throw exceptions. keySet(), values(), entrySet(), and mapIterator() return immutable data structures.
    // Instances of subclass WritablePropertyMap, however, are mutable Maps and can be saved & deleted.
    public static class PropertyMap extends AbstractMapDecorator<String, String>
    {
        protected int _set;
        protected final @NotNull User _user;
        protected final @NotNull String _objectId;
        protected final @NotNull String _category;
        protected final PropertyEncryption _propertyEncryption;
        protected final AbstractPropertyStore _store;

        protected PropertyMap(int set, @NotNull User user, @NotNull String objectId, @NotNull String category, PropertyEncryption propertyEncryption, AbstractPropertyStore store, Map<String, String> map)
        {
            super(map);
            _set = set;
            _user = user;
            _objectId = objectId;
            _category = category;
            _propertyEncryption = propertyEncryption;
            _store = store;
            validate(map);
        }

        protected void validate(Map<String, String> map)
        {
            if (CollectionUtils.isModifiableCollectionMapOrArray(map))
                throw new IllegalStateException("Map is modifiable!");
        }

        int getSet()
        {
            return _set;
        }

        @NotNull
        public User getUser()
        {
            return _user;
        }

        @NotNull
        public String getObjectId()
        {
            return _objectId;
        }

        @NotNull
        public String getCategory()
        {
            return _category;
        }

        PropertyEncryption getEncryptionAlgorithm()
        {
            return _propertyEncryption;
        }

        @Override
        public String toString()
        {
            return getClass().getSimpleName() + ": " + _objectId + ", " + _category + ", " + _user.getDisplayName(null) + ": " + super.toString();
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PropertyMap that = (PropertyMap) o;

            if (!_user.equals(that._user)) return false;
            if (!_category.equals(that._category)) return false;

            return _objectId.equals(that._objectId);
        }

        @Override
        public int hashCode()
        {
            int result = _user.hashCode();
            result = 31 * result + _objectId.hashCode();
            result = 31 * result + _category.hashCode();
            return result;
        }
    }

    /**
     * This subclass is a mutable Map and can be saved & deleted
     */
    public static class WritablePropertyMap extends PropertyMap
    {
        private boolean _modified = false;
        private Set<Object> _removedKeys = null;

        /** Clone the existing map, creating our own copy of the underlying data */
        WritablePropertyMap(PropertyMap copy)
        {
            super(copy._set, copy._user, copy._objectId, copy._category, copy._propertyEncryption, copy._store, new HashMap<>(copy));
        }

        /** New empty, writable map */
        WritablePropertyMap(int set, @NotNull User user, @NotNull String objectId, @NotNull String category, PropertyEncryption propertyEncryption, AbstractPropertyStore store)
        {
            super(set, user, objectId, category, propertyEncryption, store, new HashMap<>());
        }

        @Override
        protected void validate(Map<String, String> map)
        {
            // This class allows modifiable maps
        }

        // The following four overrides are not needed in PropertyMap since instances of that class are guaranteed to
        // be unmodifiable.

        @Override
        public MapIterator<String, String> mapIterator() {
            Map<String, String> map = decorated();
            if (map instanceof IterableMap) {
                final MapIterator<String, String> it = ((IterableMap<String, String>) decorated()).mapIterator();
                return UnmodifiableMapIterator.unmodifiableMapIterator(it);
            }
            final MapIterator<String, String> it = new EntrySetMapIterator<>(map);
            return UnmodifiableMapIterator.unmodifiableMapIterator(it);
        }

        @Override
        public @NotNull Set<Map.Entry<String, String>> entrySet() {
            final Set<Map.Entry<String, String>> set = super.entrySet();
            return UnmodifiableEntrySet.unmodifiableEntrySet(set);
        }

        @Override
        public @NotNull Set<String> keySet() {
            final Set<String> set = super.keySet();
            return UnmodifiableSet.unmodifiableSet(set);
        }

        @Override
        public @NotNull Collection<String> values() {
            final Collection<String> coll = super.values();
            return UnmodifiableCollection.unmodifiableCollection(coll);
        }

        // Map mutating methods below

        @Override
        public String remove(Object key)
        {
            if (null == _removedKeys)
                _removedKeys = new HashSet<>();
            if (containsKey(key))
            {
                _removedKeys.add(key);
                _modified = true;
            }
            return super.remove(key);
        }

        @Override
        public String put(String key, @Nullable String value)
        {
            if (null != _removedKeys)
                _removedKeys.remove(key);
            if (!StringUtils.equals(value, get(key)))
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
            if (null == _removedKeys)
                _removedKeys = new HashSet<>();
            _removedKeys.addAll(keySet());
            _modified = true;
            super.clear();
        }

        // Persistence methods below

        public boolean isModified()
        {
            return _modified;
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

            // Flag all property map saves here to catch the unmodified case (those code paths are likely to mutate in
            // other scenarios). Also, we want to flag updates to existing property maps, but that path invokes a stored
            // procedure that StatementWrapper doesn't recognize as mutating SQL.
            SpringActionController.executingMutatingSql("Saving a PropertyMap");

            if (!isModified())
            {
                // No changes, so it's safe to bail out immediately
                return;
            }

            // Lock on code that changes the underlying database storage, to avoid constraint violations if we have multiple
            // threads manipulating at the same time
            ReentrantLock lock = PROPERTY_MAP_LOCK_MANAGER.getLock(this);
            try (Transaction transaction = SCHEMA.getSchema().getScope().ensureTransaction(DbScope.FINAL_COMMIT_UNLOCK_TRANSACTION_KIND, lock))
            {
                LOG.debug("<PropertyMap.save() [{}, {}, {}, {}, map.hashCode: {}, lock: {}]>", _user.getUserId(), _objectId, _category, _set, hashCode(), lock.toString());

                Container container = ContainerManager.getForId(_objectId);
                if (container == null)
                {
                    throw new IllegalStateException("No container found with EntityId: " + _objectId);
                }
                // Check for existing map directly from the database, as the cache may be stale.
                PropertyMap existingMap = _store.getPropertyMapFromDatabase(_user, container, _category);

                if (existingMap == null)
                {
                    Map<String, Object> insertMap = new HashMap<>();
                    insertMap.put("UserId", _user);
                    insertMap.put("ObjectId", _objectId);
                    insertMap.put("Category", _category);
                    insertMap.put("Encryption", _propertyEncryption.getSerializedName());

                    // Skip because we already flagged this mutating operation above
                    try (var ignored = SpringActionController.ignoreSqlUpdates())
                    {
                        insertMap = Table.insert(_user, SCHEMA.getTableInfoPropertySets(), insertMap);
                    }

                    _set = (Integer) insertMap.get("Set");
                }
                else
                {
                    // Inside the scope of this ReentrantLock, an existing database value for "set" is the canonical value.
                    // This is true even if we'd thought it was new, or had a value for "set" coming in; another thread may have
                    // changed this state prior to this thread's call to save().
                    _set = existingMap.getSet();
                }

                // delete removed properties
                if (null != _removedKeys)
                {
                    for (Object removedKey : _removedKeys)
                    {
                        String name = toNullString(removedKey);
                        saveValue(name, null);
                    }
                }

                // set properties
                // we're not tracking modified or not, so set them all
                for (Map.Entry<String, String> e : entrySet())
                {
                    String name = toNullString(e.getKey());
                    String value = toNullString(e.getValue());
                    saveValue(name, value);
                }
                // Make sure that we clear the previously cached version of the map
                transaction.addCommitTask(() -> _store.clearCache(this), DbScope.CommitTaskOption.IMMEDIATE, DbScope.CommitTaskOption.POSTCOMMIT);
                transaction.commit();

                LOG.debug("</PropertyMap.save() [{}, {}, {}, {}, map.hashCode: {}, lock: {}]>", _user.getUserId(), _objectId, _category, _set, hashCode(), lock.toString());
            }
        }

        public void delete()
        {
            // Lock on code that changes the underlying database storage, to avoid constraint violations if we have multiple
            // threads manipulating at the same time
            final ReentrantLock lock = PROPERTY_MAP_LOCK_MANAGER.getLock(this);
            try (Transaction transaction = SCHEMA.getSchema().getScope().ensureTransaction(DbScope.FINAL_COMMIT_UNLOCK_TRANSACTION_KIND, lock))
            {
                LOG.debug("<PropertyMap.delete() [{}, {}, {}, {}, map.hashCode: {}, lock: {}]>", _user.getUserId(), _objectId, _category, _set, hashCode(), lock.toString());

                deleteSetDirectly(_user, _objectId, _category, _store);

                // Make sure that we clear the previously cached version of the map
                transaction.addCommitTask(() -> _store.clearCache(this), DbScope.CommitTaskOption.IMMEDIATE, DbScope.CommitTaskOption.POSTCOMMIT);
                transaction.commit();
                LOG.debug("</PropertyMap.delete() [{}, {}, {}, {}, map.hashCode: {}, lock: {}]>", _user.getUserId(), _objectId, _category, _set, hashCode(), lock.toString());
            }
        }
    }

    /**
     * In general, callers should always go through WritablePropertyMap.delete(). This is exposed here for cases
     * where we can't create a PropertyMap because we fail to decrypt the values previously stored, and need
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
        public void test()
        {
            PropertyStore normal = getNormalStore();
            PropertyStore encrypted = getEncryptedStore();

            // Test should still pass whether EncryptionKey is specified or not
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
                child = ContainerManager.ensureContainer(parent, "Properties", user);

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
                Map<String, String> m = store.getProperties(user, child, category);
                assertSame(m, AbstractPropertyStore.NULL_MAP);
            }
        }

        private void testProperties(PropertyStore store, User user, Container test, String category)
        {
            WritablePropertyMap m = store.getWritableProperties(user, test, category, true);
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

            // Test immutability of PropertyMap
            assertThrows(IllegalStateException.class, () -> map.put("bar", "blue"));
            assertThrows(IllegalStateException.class, () -> map.putAll(Map.of("foo", "bar", "color", "red")));
            assertThrows(IllegalStateException.class, () -> map.remove("foo"));
            assertThrows(IllegalStateException.class, map::clear);
        }

        @Test
        public void testSynchronization() throws Throwable
        {
            final Container c = JunitUtil.getTestContainer();
            final String category = "TestPropertySetName";
            final PropertyStore store = PropertyManager.getNormalStore();

            JunitUtil.createRaces(new Runnable()
            {
                @Override
                public void run()
                {
                    testProps();
                }

                private void testProps()
                {
                    WritablePropertyMap map = store.getWritableProperties(c, category, true);
                    map.put("foo", "abc");
                    map.put("bar", "xyz");
                    map.save();
                    Map<String, String> newMap = store.getProperties(c, category);
                    map = store.getWritableProperties(c, category, true);
                    map.put("flam", "mno");
                    map.delete();
                }
            }, 20, 10, 30);
        }
    }
}

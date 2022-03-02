/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.NotFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Utility class to resolve a String value to a lookup target table using query.  The lookup table's
 * primary key, alternate keys, and title column will be used to resolve the lookup value.
 */
public class RemapCache
{
    Map<Key, SimpleTranslator.RemapPostConvert> remaps = new HashMap<>();
    private final boolean _allowBulkLoads;

    public RemapCache()
    {
        this(false);
    }

    public RemapCache(boolean allowBulkLoads)
    {
        _allowBulkLoads = allowBulkLoads;
    }

    class Key
    {
        final SchemaKey _schemaKey;
        final String _queryName;
        final User _user;
        final Container _container;
        final ContainerFilter.Type _containerFilterType;

        // fetched on demand
        TableInfo _table;

        private Key(SchemaKey schemaKey, String queryName, User user, Container container, ContainerFilter.Type containerFilterType)
        {
            _schemaKey = schemaKey;
            _queryName = queryName;
            _user = user;
            _container = container;
            _containerFilterType = containerFilterType;
            _table = null;
        }

        private Key(@NotNull TableInfo lookupTable)
        {
            UserSchema schema = lookupTable.getUserSchema();
            User user = schema.getUser();
            Container c = schema.getContainer();

            ContainerFilter.Type filterType = ContainerFilter.Type.CurrentPlusProjectAndShared;
            if (lookupTable.getContainerFilter() != null)
                filterType = lookupTable.getContainerFilter().getType();

            SchemaKey schemaName = lookupTable.getUserSchema().getSchemaPath();
            String queryName = Objects.toString(lookupTable.getPublicName(), lookupTable.getName());

            _schemaKey = schema.getSchemaPath();
            _queryName = queryName;
            _user = user;
            _container = c;
            _containerFilterType = filterType;
            _table = lookupTable;
        }

        @NotNull
        TableInfo getTable()
        {
            if (_table == null)
            {
                UserSchema schema = QueryService.get().getUserSchema(_user, _container, _schemaKey);
                if (schema == null)
                    throw new NotFoundException("Schema not found: " + _schemaKey.toString());
                // TODO ContainerFilter test usages of this code
                ContainerFilter containerFilter = null;
                if (_containerFilterType != null)
                    containerFilter = _containerFilterType.create(_container, _user);
                TableInfo table = schema.getTable(_queryName, containerFilter);
                if (table == null)
                    throw new NotFoundException("Table not found: " + _queryName);

                _table = table;
            }

            return _table;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Key key = (Key) o;
            return Objects.equals(_schemaKey, key._schemaKey) &&
                    Objects.equals(_queryName, key._queryName) &&
                    Objects.equals(_user, key._user) &&
                    Objects.equals(_container, key._container) &&
                    _containerFilterType == key._containerFilterType;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(_schemaKey, _queryName, _user, _container, _containerFilterType);
        }
    }

    private Key key(SchemaKey schemaName, String queryName, User user, Container c, ContainerFilter.Type filterType)
    {
        return new Key(schemaName, queryName, user, c, filterType);
    }

    private Key key(@NotNull TableInfo table)
    {
        return new Key(table);
    }

    private SimpleTranslator.RemapPostConvert remapper(Key key, Map<Key, SimpleTranslator.RemapPostConvert> remapCache, boolean includePkLookup)
    {
        return remapCache.computeIfAbsent(key, (k) -> {
            TableInfo table = key.getTable();
            return new SimpleTranslator.RemapPostConvert(table, true, SimpleTranslator.RemapMissingBehavior.Null, _allowBulkLoads, includePkLookup);
        });
    }

    private <V> V remap(Key key, String value, boolean includePkLookup)
    {
        SimpleTranslator.RemapPostConvert remap = remapper(key, remaps, includePkLookup);
        if (remap == null)
            throw new NotFoundException("Failed to create remap: " + key._schemaKey.toString() + "." + key._queryName);
        //noinspection unchecked
        return (V) remap.mappedValue(value);
    }

    /**
     * Convert the string value to the target table's PK value by using the table's unique indices.
     */
    public <V> V remap(SchemaKey schemaName, String queryName, User user, Container c, ContainerFilter.Type filterType, String value)
    {
        return remap(key(schemaName, queryName, user, c, filterType), value, false);
    }

    /**
     * Convert the string value to the target table's PK value by using the table's unique indices and optionally pk.
     */
    public <V> V remap(TableInfo lookupTable, String value, boolean includePkLookup)
    {
        return remap(key(lookupTable), value, includePkLookup);
    }

}

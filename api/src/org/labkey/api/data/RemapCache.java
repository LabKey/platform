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

import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.NotFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class RemapCache
{
    public static final String EXPERIMENTAL_RESOLVE_LOOKUPS_BY_VALUE = "resolve-lookups-by-value";
    Map<Key, SimpleTranslator.RemapPostConvert> remaps = new HashMap<>();

    class Key
    {
        final SchemaKey _schemaKey;
        final String _queryName;
        final User _user;
        final Container _container;
        final ContainerFilter.Type _containerFilterType;

        public Key(SchemaKey schemaKey, String queryName, User user, Container container, ContainerFilter.Type containerFilterType)
        {
            _schemaKey = schemaKey;
            _queryName = queryName;
            _user = user;
            _container = container;
            _containerFilterType = containerFilterType;
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

    private SimpleTranslator.RemapPostConvert remapper(Key key, Map<Key, SimpleTranslator.RemapPostConvert> remapCache)
    {
        return remapCache.computeIfAbsent(key, (k) -> {
            UserSchema schema = QueryService.get().getUserSchema(key._user, key._container, key._schemaKey);
            if (schema == null)
                throw new NotFoundException("Schema not found: " + key._schemaKey.toString());
            // TODO ContainerFilter test usages of this code
            ContainerFilter containerFilter = null;
            if (key._containerFilterType != null)
                containerFilter = key._containerFilterType.create(key._user);
            TableInfo table = schema.getTable(key._queryName, containerFilter);
            if (table == null)
                throw new NotFoundException("Table not found: " + key._queryName);
            return new SimpleTranslator.RemapPostConvert(table, true, SimpleTranslator.RemapMissingBehavior.Null);
        });
    }

    /**
     * Convert the string value to the target table's PK value by using the table's unique indices.
     */
    public <V> V remap(SchemaKey schemaName, String queryName, User user, Container c, ContainerFilter.Type filterType, String value)
    {
        SimpleTranslator.RemapPostConvert remap = remapper(key(schemaName, queryName, user, c, filterType), remaps);
        if (remap == null)
            throw new NotFoundException("Failed to create remap: " + schemaName.toString() + "." + queryName);
        //noinspection unchecked
        return (V) remap.mappedValue(value);
    }

}

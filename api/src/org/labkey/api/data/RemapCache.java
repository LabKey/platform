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
            TableInfo table = schema.getTable(key._queryName);
            if (table == null)
                throw new NotFoundException("Table not found: " + key._queryName);

            if (key._containerFilterType != null && table instanceof ContainerFilterable)
            {
                ContainerFilter containerFilter = key._containerFilterType.create(key._user);
                ((ContainerFilterable)table).setContainerFilter(containerFilter);
            }
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

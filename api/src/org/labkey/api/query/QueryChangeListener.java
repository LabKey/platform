/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
package org.labkey.api.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.event.PropertyChange;
import org.labkey.api.security.User;

import java.util.Collection;

/**
 * Listener for table and query events that fires when the structure/schema changes, but not when individual data
 * rows change.
 * The interface supports bulk changes to multiple tables or queries with the caveat that
 * they all have the same container, scope, and schema.
 *
 * User: kevink
 * Date: 4/17/13
 */
public interface QueryChangeListener
{
    /**
     * This method is called when a set of tables or queries are created in the given container and schema.
     *
     * @param user The user that initiated the change.
     * @param container The container the tables or queries are created in.
     * @param scope The scope of containers that the tables or queries affect.
     * @param schema The schema of the tables or queries.
     * @param queries The query or table names.
     */
    void queryCreated(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries);

    /**
     * This method is called when a set of tables or queries are changed in the given container and schema.
     * <p>
     * <b>ACHTUNG!</b> - All dependent objects should be fixed up regardless of ownership or the <code>user</code>
     * that initiated the change. When persisting fixed up dependent objects, save using the <code>user</code>
     * that initiated the changes even if that user doesn't own the object.
     *
     * @param user The user that initiated the change.
     * @param container The container the tables or queries are changed in.
     * @param scope The scope of containers that the tables or queries affect.
     * @param schema The schema of the tables or queries.
     * @param property The QueryProperty that has changed.
     * @param changes The set of change events.  Each QueryPropertyChange is associated with a single table or query.
     */
    void queryChanged(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull QueryProperty property, @NotNull Collection<QueryPropertyChange> changes);

    /**
     * This method is called when a set of tables or queries are deleted from the given container and schema.
     *
     * @param user The user that initiated the change.
     * @param container The container the tables or queries are deleted from.
     * @param scope The scope of containers that the tables or queries affect.
     * @param schema The schema of the tables or queries.
     * @param queries The query or table names.
     */
    void queryDeleted(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries);

    /**
     * Get a textual representation of items that depend on a table or query.
     * For example, the user can be presented with a list of items that will break if a query is deleted.
     *
     * @param user The current user.
     * @param container The container the tables or queries are deleted from.
     * @param scope The scope of containers that the tables or queries affect.
     * @param schema The schema of the tables or queries.
     * @param queries The query or table names.
     */
    Collection<String> queryDependents(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries);

    // CONSIDER: Create a generic class instead of using an enum.
    public enum QueryProperty
    {
        Name(String.class),
        Container(Container.class),
        Description(String.class),
        Inherit(Boolean.class),
        Hidden(Boolean.class);

        private final Class<?> _klass;

        QueryProperty(Class<?> klass)
        {
            _klass = klass;
        }

        public Class<?> getPropertyClass()
        {
            return _klass;
        }
    }

    /**
     * A change event for a single property of a single table or query.
     * If multiple properties have been changed, QueryChangeListener will
     * fire {@link QueryChangeListener#queryChanged(User, Container, ContainerFilter, SchemaKey, QueryChangeListener.QueryProperty, Collection)}
     * for each property that has changed.
     *
     * @param <V> The property type.
     */
    class QueryPropertyChange<V> implements PropertyChange<QueryProperty, V>
    {
        private final QueryDefinition _queryDef;
        private final QueryProperty _property;
        private final V _oldValue;
        private final V _newValue;

        /**
         * A single property change event for a table or query.
         * @param queryDef Represents either a custom query or a TableInfo (TableQueryDefinition).
         * @param property The changed property.
         * @param oldValue The previous property value or null.
         * @param newValue The current property value or null.
         */
        public QueryPropertyChange(@NotNull QueryDefinition queryDef, @NotNull QueryProperty property, V oldValue, V newValue)
        {
            _queryDef = queryDef;
            _property = property;
            _oldValue = oldValue;
            _newValue = newValue;
        }

        public QueryDefinition getSource() { return _queryDef; }
        @NotNull
        public QueryProperty getProperty() { return _property; }
        @Nullable
        public V getOldValue() { return _oldValue; }
        @Nullable
        public V getNewValue() { return _newValue; }
    }
}

package org.labkey.api.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.event.PropertyChange;

import java.util.Collection;

/**
 * Listener for table and query events.
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
     * @param container The container the tables or queries are created in.
     * @param scope The scope of containers that the tables or queries affect.
     * @param schema The schema of the tables or queries.
     * @param queries The query or table names.
     */
    void queryCreated(Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries);

    /**
     * This method is called when a set of tables or queries are changed in the given container and schema.
     *
     * @param container The container the tables or queries are changed in.
     * @param scope The scope of containers that the tables or queries affect.
     * @param schema The schema of the tables or queries.
     * @param property The QueryProperty that has changed or null if more than one property has changed.
     * @param changes The set of change events.  Each QueryPropertyChange is associated with a single table or query.
     */
    void queryChanged(Container container, ContainerFilter scope, SchemaKey schema, QueryProperty property, Collection<QueryPropertyChange> changes);

    /**
     * This method is called when a set of tables or queries are deleted from the given container and schema.
     *
     * @param container The container the tables or queries are deleted from.
     * @param scope The scope of containers that the tables or queries affect.
     * @param schema The schema of the tables or queries.
     * @param queries The query or table names.
     */
    void queryDeleted(Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries);

    /**
     * Get a textual representation of items that depdend on a table or query.
     * For example, the user can be presented with a list of items that will break if a query is deleted.
     *
     * @param container The container the tables or queries are deleted from.
     * @param scope The scope of containers that the tables or queries affect.
     * @param schema The schema of the tables or queries.
     * @param queries The query or table names.
     */
    Collection<String> queryDependents(Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries);

    // CONSIDER: Create a generic class instead of using an enum.
    public enum QueryProperty
    {
        Name(String.class),
        Container(Container.class),
        Description(String.class),
        Inherit(Boolean.class),
        Hidden(Boolean.class);

        private Class<?> _klass;

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
     * A change event for a single table or query.
     * If multiple properties have been changed,
     * {@link .getProperty}, {@link .getOldValue}, and {@link .getNewValue} will return null.
     *
     * @param <V> The property type.
     */
    class QueryPropertyChange<V> implements PropertyChange<QueryProperty, V>
    {
        private QueryDefinition _queryDef;
        private QueryProperty _property;
        private V _oldValue;
        private V _newValue;

        /**
         * Multiple property changes for a table or query.
         * @param queryDef Represents either a custom query or a TableInfo (TableQueryDefinition)
         */
        public QueryPropertyChange(QueryDefinition queryDef)
        {
            this(queryDef, null, null, null);
        }

        /**
         * A single property change event for a table or query.
         * @param queryDef Represents either a custom query or a TableInfo (TableQueryDefinition).
         * @param property The changed property or null if more than one property has changed.
         * @param oldValue The previous property value or null if more than onde property has changed
         * @param newValue The current property value or null if more than onde property has changed
         */
        public QueryPropertyChange(QueryDefinition queryDef, QueryProperty property, V oldValue, V newValue)
        {
            _queryDef = queryDef;
            _property = property;
            _oldValue = oldValue;
            _newValue = newValue;
        }

        public QueryDefinition getSource() { return _queryDef; }
        @Nullable
        public QueryProperty getProperty() { return _property; }
        @Nullable
        public V getOldValue() { return _oldValue; }
        @Nullable
        public V getNewValue() { return _newValue; }
    }
}

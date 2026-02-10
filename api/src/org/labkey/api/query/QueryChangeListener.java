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
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.labkey.api.data.ColumnRenderPropertiesImpl.TEXT_CHOICE_CONCEPT_URI;

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
    void queryChanged(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull QueryProperty property, @NotNull Collection<QueryPropertyChange<?>> changes);

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
    enum QueryProperty
    {
        Name(String.class),
        Container(Container.class),
        Description(String.class),
        Inherit(Boolean.class),
        Hidden(Boolean.class),
        SchemaName(String.class),
        ColumnName(String.class),
        ColumnType(PropertyDescriptor.class),;

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
        public QueryPropertyChange(@Nullable QueryDefinition queryDef, @NotNull QueryProperty property, V oldValue, V newValue)
        {
            _queryDef = queryDef;
            _property = property;
            _oldValue = oldValue;
            _newValue = newValue;
        }

        public static void handleQueryNameChange(@NotNull String oldValue, String newValue, @NotNull SchemaKey schemaPath, User user, Container container)
        {
            if (oldValue.equals(newValue))
                return;

            QueryChangeListener.QueryPropertyChange<?> change = new QueryChangeListener.QueryPropertyChange<>(
                    QueryService.get().getUserSchema(user, container, schemaPath).getQueryDefForTable(newValue),
                    QueryChangeListener.QueryProperty.Name,
                    oldValue,
                    newValue
            );

            QueryService.get().fireQueryChanged(user, container, null, schemaPath,
                    QueryChangeListener.QueryProperty.Name, Collections.singleton(change));
        }

        public static void handleSchemaNameChange(@NotNull String oldValue, String newValue, @NotNull SchemaKey schemaPath, User user, Container container)
        {
            if (oldValue.equals(newValue))
                return;

            QueryChangeListener.QueryPropertyChange<?> change = new QueryChangeListener.QueryPropertyChange<>(
                    null,
                    QueryChangeListener.QueryProperty.SchemaName,
                    oldValue,
                    newValue
            );

            QueryService.get().fireQueryChanged(user, container, null, schemaPath,
                    QueryProperty.SchemaName, Collections.singleton(change));
        }

        public static void handleColumnTypeChange(@NotNull PropertyDescriptor oldValue, PropertyDescriptor newValue, @NotNull SchemaKey schemaPath, @NotNull String queryName, User user, Container container)
        {
            if (oldValue.getPropertyType() == newValue.getPropertyType())
                return;

            QueryChangeListener.QueryPropertyChange change = new QueryChangeListener.QueryPropertyChange<>(
                    QueryService.get().getUserSchema(user, container, schemaPath).getQueryDefForTable(queryName),
                    QueryChangeListener.QueryProperty.ColumnType,
                    oldValue,
                    newValue
            );

            QueryService.get().fireQueryColumnChanged(user, container, schemaPath,
                    QueryProperty.ColumnType, Collections.singleton(change));
        }

        @Nullable public QueryDefinition getSource() { return _queryDef; }

        @Override
        @NotNull
        public QueryProperty getProperty() { return _property; }

        @Override
        @Nullable
        public V getOldValue() { return _oldValue; }

        @Override
        @Nullable
        public V getNewValue() { return _newValue; }
    }

    /**
     * Utility to update encoded filter string when a column type changes from Multi_Choice to a non Multi_Choice.
     * This method performs targeted replacements for the given column name (case-insensitive).
     */
    private static String getUpdatedFilterStrFromMVTC(String filterStr, String prefix, String columnName, @NotNull PropertyDescriptor oldType, @NotNull PropertyDescriptor newType)
    {
        if (filterStr == null || columnName == null)
            return filterStr;

        // Only act when changing away from MULTI_CHOICE
        if (oldType.getPropertyType() != PropertyType.MULTI_CHOICE || newType.getPropertyType() == PropertyType.MULTI_CHOICE)
            return filterStr;

        String columnNameEncoded = PageFlowUtil.encodeURIComponent(QueryKey.encodePart(columnName));

        String colLower = columnNameEncoded.toLowerCase();
        String sLower = filterStr.toLowerCase();

        // No action if column doesn't match
        if (!sLower.startsWith(prefix + "." + colLower + "~"))
            return filterStr;

        // drop arraycontainsall since there is no good match
        if (sLower.startsWith(prefix + "." + colLower + "~arraycontainsall"))
            return "";

        String updated = filterStr;

        if (TEXT_CHOICE_CONCEPT_URI.equals(newType.getConceptURI()))
        {
            // only keep arraymatches/arraynotmatches when converting to a TEXT_CHOICE since current values are guaranteed to be single value
            if (containsOp(updated, prefix, columnNameEncoded, "arraymatches"))
            {
                return replaceOp(updated, prefix, columnNameEncoded, "arraymatches", "eq");
            }
            if (containsOp(updated, prefix, columnNameEncoded, "arraynotmatches"))
            {
                return replaceOp(updated, prefix, columnNameEncoded, "arraynotmatches", "neq");
            }
        }

        if (containsOp(updated, prefix, columnNameEncoded, "arrayisempty"))
        {
            return replaceOp(updated, prefix, columnNameEncoded, "arrayisempty", "isblank");
        }
        if (containsOp(updated, prefix, columnNameEncoded, "arrayisnotempty"))
        {
            return replaceOp(updated, prefix, columnNameEncoded, "arrayisnotempty", "isnonblank");
        }
        if (containsOp(updated, prefix, columnNameEncoded, "arraycontainsany"))
        {
            return replaceOp(updated, prefix, columnNameEncoded, "arraycontainsany", "in");
        }
        if (containsOp(updated, prefix, columnNameEncoded, "arraycontainsnone"))
        {
            return replaceOp(updated, prefix, columnNameEncoded, "arraycontainsnone", "notin");
        }

        // No matching operator found for this column, drop the filter
        return "";
    }

    /**
     * Utility to update encoded filter string when a column type is changed to Multi_Choice (migrating operators to array equivalents).
     */
    private static String getUpdatedMVTCFilterStr(String filterStr, String prefix, String columnName, @NotNull PropertyDescriptor oldType, @NotNull PropertyDescriptor newType)
    {
        if (filterStr == null || columnName == null || oldType == null || newType == null)
            return filterStr;

        // Only act when changing to MULTI_CHOICE
        if (oldType.getPropertyType() == PropertyType.MULTI_CHOICE || newType.getPropertyType() != PropertyType.MULTI_CHOICE)
            return filterStr;

        String columnNameEncoded = PageFlowUtil.encodeURIComponent(columnName);

        String colLower = columnNameEncoded.toLowerCase();
        String sLower = filterStr.toLowerCase();

        // No action if column doesn't match
        if (!sLower.startsWith(prefix + "." + colLower + "~"))
            return filterStr;

        String updated = filterStr;

        // Return on first matching operator for this column
        if (containsOp(updated, prefix, columnNameEncoded, "eq"))
        {
            return replaceOp(updated, prefix, columnNameEncoded, "eq", "arraymatches");
        }
        if (containsOp(updated, prefix, columnNameEncoded, "neq"))
        {
            return replaceOp(updated, prefix, columnNameEncoded, "neq", "arraycontainsnone");
        }
        if (containsOp(updated, prefix, columnNameEncoded, "isblank"))
        {
            return replaceOp(updated, prefix, columnNameEncoded, "isblank", "arrayisempty");
        }
        if (containsOp(updated, prefix, columnNameEncoded, "isnonblank"))
        {
            return replaceOp(updated, prefix, columnNameEncoded, "isnonblank", "arrayisnotempty");
        }
        if (containsOp(updated, prefix, columnNameEncoded, "in"))
        {
            return replaceOp(updated, prefix, columnNameEncoded, "in", "arraycontainsany");
        }
        if (containsOp(updated, prefix, columnNameEncoded, "notin"))
        {
            return replaceOp(updated, prefix, columnNameEncoded, "notin", "arraycontainsnone");
        }

        // No matching operator found for this column, drop the filter
        return "";
    }

    static String getUpdatedFilterStrOnColumnTypeUpdate(String filterStr, String prefix, String columnName, @NotNull PropertyDescriptor oldType, @NotNull PropertyDescriptor newType)
    {
        if (oldType.getPropertyType() == PropertyType.MULTI_CHOICE)
            return getUpdatedFilterStrFromMVTC(filterStr, prefix, columnName, oldType, newType);
        else if (newType.getPropertyType() == PropertyType.MULTI_CHOICE)
            return getUpdatedMVTCFilterStr(filterStr, prefix, columnName, oldType, newType);
        else
            return filterStr;
    }

    private static boolean containsOp(String filterStr, String prefix, String columnName, String op)
    {
        String regex = "(?i)" + prefix + "\\." + Pattern.quote(columnName) + "~" + Pattern.quote(op);
        return Pattern.compile(regex).matcher(filterStr).find();
    }

    private static String replaceOp(String filterStr, String prefix, String columnName, String fromOp, String toOp)
    {
        String regex = "(?i)(" + prefix + "\\.)(" + Pattern.quote(columnName) + ")(~)" + Pattern.quote(fromOp);
        Matcher m = Pattern.compile(regex).matcher(filterStr);
        StringBuffer sb = new StringBuffer();
        while (m.find())
        {
            // Preserve the literal 'filter.', 'columnName' and '~', but use the new operator
            String replacement = m.group(1) + m.group(2) + m.group(3) + toOp;
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

}

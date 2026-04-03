/*
 * Copyright (c) 2015-2018 LabKey Corporation
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
package org.labkey.api.data.triggers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.UnexpectedException;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Trigger scripts are invoked before insert/update/delete on many LabKey tables.
 * The Trigger is created by a TriggerFactory added to AbstractTableInfo.
 */
public interface Trigger
{
    /** The trigger name. */
    default String getName()
    {
        return getClass().getSimpleName();
    }

    /** Short description of the trigger. */
    default String getDescription()
    {
        return null;
    }

    /** Name of the module that defines this trigger. */
    default String getModuleName()
    {
        return null;
    }

    /**
     * For script triggers, this is the path to the trigger script.
     * For java triggers, this is the class name.
     */
    default String getSource()
    {
        return getClass().getName();
    }

    /**
     * The set of events that this trigger implements.
     */
    default List<TableInfo.TriggerMethod> getEvents()
    {
        try
        {
            Class<Trigger> triggerInterface = Trigger.class;
            Class<?> cls = getClass();
            return Arrays.stream(cls.getMethods())
                    .filter(m -> triggerInterface != m.getDeclaringClass())
                    .map(Method::getName)
                    .map(name -> {
                        try { return TableInfo.TriggerMethod.valueOf(name); }
                        catch (IllegalArgumentException e) { return null; }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
        catch (SecurityException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    record ManagedColumns(@NotNull Set<String> insert, @NotNull Set<String> update, @Nullable Set<String> ignored)
    {
        public static ManagedColumns all(@NotNull Set<String> all)
        {
            return new ManagedColumns(all, all, null);
        }

        public static ManagedColumns all(@NotNull String... all)
        {
            return all(Sets.newCaseInsensitiveHashSet(all));
        }

        public static ManagedColumns empty()
        {
            return new ManagedColumns(Collections.emptySet(), Collections.emptySet(), null);
        }

        public @Nullable Set<String> getColumns(TableInfo.TriggerType type)
        {
            if (type == TableInfo.TriggerType.INSERT)
                return insert;
            if (type == TableInfo.TriggerType.UPDATE)
                return update;
            return null;
        }
    }

    /**
     * Returns the set of column names this trigger will read or write during row processing.
     * <p>
     * For each row where a declared column is absent from the input, the trigger must
     * explicitly set each such column to null or a real value; failure to do so produces
     * a validation error naming this trigger and the unhandled column.
     * <p>
     * Columns that do not exist in the target table's schema (virtual/passthrough columns) may be
     * declared here and will work correctly — the database writer ignores them.
     */
    default @Nullable ManagedColumns getManagedColumns()
    {
        return null;
    }

    default void setInsertManagedColumns(Map<String, Object> newRow)
    {
        setManagedColumns(newRow, null, TableInfo.TriggerType.INSERT);
    }

    default void setUpdateManagedColumns(Map<String, Object> newRow, @NotNull Map<String, Object> oldRow)
    {
        if (oldRow == null)
            throw new IllegalArgumentException("oldRow must be non-null for UPDATE triggers");

        setManagedColumns(newRow, oldRow, TableInfo.TriggerType.UPDATE);
    }

    private void setManagedColumns(Map<String, Object> newRow, Map<String, Object> oldRow, TableInfo.TriggerType type)
    {
        if (newRow == null)
            return;

        var managedCols = getManagedColumns();
        if (managedCols == null)
            return;

        var cols = managedCols.getColumns(type);
        if (cols == null)
            return;

        for (var col : cols)
            newRow.putIfAbsent(col, oldRow == null ? null : oldRow.get(col));
    }

    /**
     * Returns true if this TriggerScript can be used in a streaming context; triggers will be called without old row values.
     */
    default boolean canStream()
    {
        return false;
    }

    default void batchTrigger(TableInfo table, Container c, User user, TableInfo.TriggerType event, boolean before, BatchValidationException errors, Map<String, Object> extraContext)
    {
        if (before)
            init(table, c, user, event, errors, extraContext);
        else
            complete(table, c, user, event, errors, extraContext);
    }

    default void init(TableInfo table, Container c, User user, TableInfo.TriggerType event, BatchValidationException errors, Map<String, Object> extraContext)
    {
    }

    default void complete(TableInfo table, Container c, User user, TableInfo.TriggerType event, BatchValidationException errors, Map<String, Object> extraContext)
    {
    }

    default void rowTrigger(TableInfo table, Container c, User user, TableInfo.TriggerType event, boolean before, int rowNumber,
                            @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow,
                            ValidationException errors, Map<String, Object> extraContext,
                            @Nullable Map<String, Object> existingRecord) throws ValidationException
    {
        if (before)
        {
            switch (event)
            {
                case INSERT:
                    beforeInsert(table, c, user, newRow, errors, extraContext, existingRecord);
                    break;
                case UPDATE:
                    beforeUpdate(table, c, user, newRow, oldRow, errors, extraContext);
                    break;
                case DELETE:
                    beforeDelete(table, c, user, oldRow, errors, extraContext);
                    break;
            }
        }
        else
        {
            switch (event)
            {
                case INSERT:
                    afterInsert(table, c, user, newRow, errors, extraContext, existingRecord);
                    break;
                case UPDATE:
                    afterUpdate(table, c, user, newRow, oldRow, errors, extraContext);
                    break;
                case DELETE:
                    afterDelete(table, c, user, oldRow, errors, extraContext);
                    break;
            }
        }
    }

    default void beforeInsert(TableInfo table, Container c,
                              User user, @Nullable Map<String, Object> newRow,
                              ValidationException errors, Map<String, Object> extraContext) throws ValidationException
    {
    }

    default void beforeInsert(TableInfo table, Container c,
                              User user, @Nullable Map<String, Object> newRow,
                              ValidationException errors, Map<String, Object> extraContext, @Nullable Map<String, Object> existingRecord) throws ValidationException
    {
        beforeInsert(table, c, user, newRow, errors, extraContext);
    }

    default void beforeUpdate(TableInfo table, Container c,
                              User user, @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow,
                              ValidationException errors, Map<String, Object> extraContext) throws ValidationException
    {
    }

    default void beforeDelete(TableInfo table, Container c,
                              User user, @Nullable Map<String, Object> oldRow,
                              ValidationException errors, Map<String, Object> extraContext) throws ValidationException
    {
    }

    default void afterInsert(TableInfo table, Container c,
                             User user, @Nullable Map<String, Object> newRow,
                             ValidationException errors, Map<String, Object> extraContext, @Nullable Map<String, Object> existingRecord) throws ValidationException
    {
        afterInsert(table, c, user, newRow, errors, extraContext);
    }

    default void afterInsert(TableInfo table, Container c,
                             User user, @Nullable Map<String, Object> newRow,
                             ValidationException errors, Map<String, Object> extraContext) throws ValidationException
    {
    }

    default void afterUpdate(TableInfo table, Container c,
                             User user, @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow,
                             ValidationException errors, Map<String, Object> extraContext) throws ValidationException
    {
    }

    default void afterDelete(TableInfo table, Container c,
                             User user, @Nullable Map<String, Object> oldRow,
                             ValidationException errors, Map<String, Object> extraContext) throws ValidationException
    {
    }


    /**
     * JSON serialization for query-getQueryDetails.api
     */
    default JSONObject toJSON()
    {
        return new JSONObject()
            .put("name", getName())
            .put("description", getDescription())
            .put("module", getModuleName())
            .put("source", getSource())
            .put("events", getEvents());
    }
}

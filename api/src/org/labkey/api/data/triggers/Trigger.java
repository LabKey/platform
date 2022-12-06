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

import org.jetbrains.annotations.Nullable;
import org.json.old.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.UnexpectedException;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * User: kevink
 * Date: 12/21/15
 *
 * Trigger scripts are invoked before insert/update/delete on many LabKey tables.
 * The Trigger is created by a TriggerFactory added to AbstractTableInfo.
 */
public interface Trigger
{
    /** The trigger name. */
    default String getName() { return getClass().getSimpleName(); }

    /** Short description of the trigger. */
    default String getDescription() { return null; }

    /** Name of module that defines this trigger. */
    default String getModuleName() { return null; }

    /**
     * For script triggers, this is the path to the trigger script.
     * For java triggers, this is the class name.
     */
    default String getSource() { return getClass().getName(); }

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

    /**
     * True if this TriggerScript can be used in a streaming context; triggers will be called without old row values.
     */
    default boolean canStream() { return false; }

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
                            ValidationException errors, Map<String, Object> extraContext) throws ValidationException
    {
        if (before)
        {
            switch (event)
            {
                case INSERT:
                    beforeInsert(table, c, user, newRow, errors, extraContext);
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
                    afterInsert(table, c, user, newRow, errors, extraContext);
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

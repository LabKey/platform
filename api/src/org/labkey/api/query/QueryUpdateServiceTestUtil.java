/*
 * Copyright (c) 2026 LabKey Corporation
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
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.triggers.Trigger;
import org.labkey.api.dataiterator.MapDataIterator;
import org.labkey.api.security.User;

import java.util.List;
import java.util.Map;

/** Test helpers that call a QueryUpdateService and throw its validation errors instead of returning them */
public class QueryUpdateServiceTestUtil
{
    public static @NotNull QueryUpdateService getUpdateService(@NotNull TableInfo table)
    {
        QueryUpdateService qus = table.getUpdateService();
        if (qus == null)
            throw new IllegalArgumentException("No QueryUpdateService for " + table.getName());
        return qus;
    }

    public static @NotNull TableInfo getTable(User user, Container c, SchemaKey schema, String query)
    {
        UserSchema userSchema = QueryService.get().getUserSchema(user, c, schema);
        if (userSchema == null)
            throw new IllegalArgumentException("No schema " + schema + " in " + c.getPath());
        TableInfo table = userSchema.getTable(query);
        if (table == null)
            throw new IllegalArgumentException("No table " + schema + "." + query + " in " + c.getPath());
        return table;
    }

    /** Cached tables are locked, so pass one built for write, e.g. {@code schema.getTable(name, null, true, true)} */
    public static @NotNull TableInfo withTrigger(@Nullable TableInfo writable, Container c, Trigger trigger)
    {
        if (!(writable instanceof AbstractTableInfo table))
            throw new IllegalArgumentException("Can't attach a trigger to " + writable);
        table.addTriggerFactory((_, _, _) -> List.of(trigger));
        table.resetTriggers(c);
        if (!table.hasTriggers(c))
            throw new IllegalStateException("Trigger not attached to " + table.getName());
        return table;
    }

    public static List<Map<String, Object>> insert(TableInfo table, User user, Container c, List<Map<String, Object>> rows) throws Exception
    {
        BatchValidationException errors = new BatchValidationException();
        List<Map<String, Object>> inserted = getUpdateService(table).insertRows(user, c, rows, errors, null, null);
        if (errors.hasErrors())
            throw errors;
        return inserted;
    }

    public static List<Map<String, Object>> update(TableInfo table, User user, Container c, List<Map<String, Object>> rows) throws Exception
    {
        BatchValidationException errors = new BatchValidationException();
        List<Map<String, Object>> updated = getUpdateService(table).updateRows(user, c, rows, null, errors, null, null);
        if (errors.hasErrors())
            throw errors;
        return updated;
    }

    public static int merge(TableInfo table, User user, Container c, List<Map<String, Object>> rows) throws Exception
    {
        BatchValidationException errors = new BatchValidationException();
        int count = getUpdateService(table).mergeRows(user, c, MapDataIterator.of(rows), errors, null, null);
        if (errors.hasErrors())
            throw errors;
        return count;
    }

    public static List<Map<String, Object>> delete(TableInfo table, User user, Container c, List<Map<String, Object>> keys) throws Exception
    {
        return getUpdateService(table).deleteRows(user, c, keys, null, null);
    }
}

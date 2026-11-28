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
package org.labkey.api.data.triggers;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/** Test trigger that records which row hook saw each row, e.g. "beforeUpdate 3 old x" */
public class RecordingTrigger implements Trigger
{
    public static final String COMPUTED = "computed";

    private final List<String> _events = new ArrayList<>();
    private final String _idColumn;
    private final List<String> _oldColumns;
    private final boolean _routed;
    private final @Nullable String _insertManagedColumn;

    /**
     * @param oldColumns oldRow values to record on beforeUpdate
     * @param insertManagedColumn declared managed and set to {@link #COMPUTED} on insert, if not null
     */
    public RecordingTrigger(String idColumn, List<String> oldColumns, boolean routed, @Nullable String insertManagedColumn)
    {
        _idColumn = idColumn;
        _oldColumns = oldColumns;
        _routed = routed;
        _insertManagedColumn = insertManagedColumn;
    }

    public List<String> getRecorded()
    {
        return _events;
    }

    /** Rows are batched, so hooks for different rows may interleave */
    public void assertEvents(String... expected)
    {
        assertEquals(Set.of(expected), Set.copyOf(_events));
        assertEquals(expected.length, _events.size());
    }

    @Override
    public boolean isMergeRoutedByExistingRecord()
    {
        return _routed;
    }

    @Override
    public @Nullable ManagedColumns getManagedColumns()
    {
        return _insertManagedColumn == null ? null : new ManagedColumns(Set.of(_insertManagedColumn), Set.of(), null);
    }

    @Override
    public void beforeInsert(TableInfo table, Container c, User user, @Nullable QueryUpdateService.InsertOption insertOption, @Nullable Map<String, Object> newRow, ValidationException errors, Map<String, Object> extraContext)
    {
        assertNotNull(newRow);
        _events.add("beforeInsert " + newRow.get(_idColumn));
        if (_insertManagedColumn != null)
            newRow.put(_insertManagedColumn, COMPUTED);
    }

    @Override
    public void beforeUpdate(TableInfo table, Container c, User user, @Nullable QueryUpdateService.InsertOption insertOption, @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow, ValidationException errors, Map<String, Object> extraContext)
    {
        assertNotNull(newRow);
        assertNotNull(oldRow);
        Map<String, Object> old = new CaseInsensitiveHashMap<>(oldRow);
        _events.add("beforeUpdate " + newRow.get(_idColumn) + " old " + _oldColumns.stream().map(col -> String.valueOf(old.get(col))).collect(Collectors.joining(" ")));
    }

    @Override
    public void afterInsert(TableInfo table, Container c, User user, @Nullable Map<String, Object> newRow, ValidationException errors, Map<String, Object> extraContext)
    {
        assertNotNull(newRow);
        _events.add("afterInsert " + newRow.get(_idColumn));
    }

    @Override
    public void afterUpdate(TableInfo table, Container c, User user, @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow, ValidationException errors, Map<String, Object> extraContext)
    {
        assertNotNull(newRow);
        assertNotNull(oldRow);
        _events.add("afterUpdate " + newRow.get(_idColumn));
    }
}

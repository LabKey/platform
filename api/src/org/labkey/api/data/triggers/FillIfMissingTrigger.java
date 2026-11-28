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
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.util.Map;

import static org.junit.Assert.assertNotNull;

/** Test trigger that fills a column with no value, like a script's {@code row.X = row.X || 'filled'}, without declaring it managed */
public record FillIfMissingTrigger(String column, boolean routed, boolean managedColumnsEnabled) implements Trigger
{
    public static final String FILLED = "filled";

    @Override
    public boolean isMergeRoutedByExistingRecord()
    {
        return routed;
    }

    @Override
    public boolean isManagedColumnsEnabled()
    {
        return managedColumnsEnabled;
    }

    @Override
    public void beforeInsert(TableInfo table, Container c, User user, @Nullable QueryUpdateService.InsertOption insertOption, @Nullable Map<String, Object> newRow, ValidationException errors, Map<String, Object> extraContext)
    {
        fill(newRow);
    }

    @Override
    public void beforeUpdate(TableInfo table, Container c, User user, @Nullable QueryUpdateService.InsertOption insertOption, @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow, ValidationException errors, Map<String, Object> extraContext)
    {
        fill(newRow);
    }

    private void fill(@Nullable Map<String, Object> row)
    {
        assertNotNull(row);
        row.putIfAbsent(column, FILLED);
    }
}

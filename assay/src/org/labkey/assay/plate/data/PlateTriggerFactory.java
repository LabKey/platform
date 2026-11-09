/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.assay.plate.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.triggers.Trigger;
import org.labkey.api.data.triggers.TriggerFactory;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.plate.query.PlateTable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.labkey.api.util.IntegerUtils.asInteger;

public final class PlateTriggerFactory implements TriggerFactory
{
    @Override
    public @NotNull Collection<Trigger> createTrigger(@Nullable Container c, TableInfo table, Map<String, Object> extraContext)
    {
        return List.of(
            new AuditPlateDeleteTrigger()
        );
    }

    @SuppressWarnings("InnerClassMayBeStatic")
    private class AuditPlateDeleteTrigger implements Trigger
    {
        private Map<GUID, List<Plate>> _platesDeleted = null;

        @Override
        public void beforeDelete(TableInfo table, Container c, User user, @Nullable Map<String, Object> oldRow, ValidationException errors, Map<String, Object> extraContext)
        {
            if (oldRow == null || errors.hasErrors())
                return;

            Integer plateId = asInteger(oldRow.get(PlateTable.Column.RowId.name()));
            Plate plate = PlateManager.get().getPlate(c, plateId);
            if (plate == null)
                return;

            if (_platesDeleted == null)
                _platesDeleted = new HashMap<>();
            _platesDeleted.putIfAbsent(c.getEntityId(), new ArrayList<>());
            _platesDeleted.get(c.getEntityId()).add(plate);
        }

        @Override
        public void complete(TableInfo table, Container c, User user, TableInfo.TriggerType event, BatchValidationException errors, Map<String, Object> extraContext)
        {
            DbScope.Transaction tx = DbScope.getLabKeyScope().getCurrentTransaction();
            assert tx != null;

            if (errors.hasErrors() || _platesDeleted == null || _platesDeleted.isEmpty())
                return;

            for (var entry : _platesDeleted.entrySet())
            {
                var container = ContainerManager.getForId(entry.getKey());
                if (container == null)
                    continue;

                PlateManager.get().addPlateDeletedAuditEvents(container, user, tx, entry.getValue());
            }
        }
    }
}

/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.assay.plate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayResultDomainKind;
import org.labkey.api.assay.plate.AssayPlateMetadataService;
import org.labkey.api.assay.plate.PlateDataStateManager;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableResultSet;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.triggers.Trigger;
import org.labkey.api.data.triggers.TriggerFactory;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.qc.DataState;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.UnexpectedException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.util.IntegerUtils.asLongElseNull;

public class AssayPlateTriggerFactory implements TriggerFactory
{
    private final AssayProvider _provider;
    private final ExpProtocol _protocol;
    private final DomainProperty _qcStateProp;

    public AssayPlateTriggerFactory(@NotNull AssayProvider provider, @NotNull ExpProtocol protocol)
    {
        _provider = provider;
        _protocol = protocol;
        _qcStateProp = AssayPlateMetadataServiceImpl.getAssayStateProp(provider.getResultsDomain(_protocol, false));
    }

    @Override
    public @NotNull Collection<Trigger> createTrigger(@Nullable Container c, TableInfo table, Map<String, Object> extraContext)
    {
        return List.of(
            new ReplicateStatsTrigger(),
            new DataStateTrigger(),
            new AutomaticHitSelectionTrigger()
        );
    }

    /**
     * Recompute the stats for the changed replicate rows.
     * Trigger to handle updates, inserts are handled during assay run creation.
     */
    private class ReplicateStatsTrigger implements Trigger
    {
        private final Map<String, Boolean> _replicateLsid = new HashMap<>();

        private void checkForChanges(@Nullable Map<String, Object> oldRow, boolean isUpdate)
        {
            if (oldRow != null)
            {
                // check if the change is to a replicate well row
                Object replicateLsid = oldRow.get(AssayResultDomainKind.Column.ReplicateLsid.name());
                if (replicateLsid != null)
                    _replicateLsid.put(String.valueOf(replicateLsid), isUpdate);
            }
        }

        @Override
        public void afterUpdate(TableInfo table, Container c, User user, @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow, ValidationException errors, Map<String, Object> extraContext)
        {
            if (errors.hasErrors())
                return;

            checkForChanges(oldRow, true);
        }

        @Override
        public void afterDelete(TableInfo table, Container c, User user, @Nullable Map<String, Object> oldRow, ValidationException errors, Map<String, Object> extraContext)
        {
            if (errors.hasErrors())
                return;

            checkForChanges(oldRow, false);
        }

        @Override
        public void complete(TableInfo table, Container c, User user, TableInfo.TriggerType event, BatchValidationException errors, Map<String, Object> extraContext)
        {
            if (_replicateLsid.isEmpty() || errors.hasErrors())
                return;

            Map<String, String> aliasMap = new HashMap<>();
            table.getColumns().forEach(col -> {
                // include the name if different from the alias
                if (!col.getName().equals(col.getAlias()))
                    aliasMap.put(col.getAlias().getId(), col.getName());
            });
            var filter = new SimpleFilter(FieldKey.fromParts(AssayResultDomainKind.Column.ReplicateLsid.name()), _replicateLsid.keySet(), CompareType.IN);

            try (TableResultSet rs = new TableSelector(table, filter, null).getResultSet())
            {
                var replicates = new HashMap<Lsid, List<Map<String, Object>>>();
                while (rs.next())
                {
                    var lsid = rs.getString(AssayResultDomainKind.Column.ReplicateLsid.name());
                    Map<String, Object> row = rs.getRowMap();
                    for (Map.Entry<String, String> entry : aliasMap.entrySet())
                    {
                        if (row.containsKey(entry.getKey()))
                            row.put(entry.getValue(), row.get(entry.getKey()));
                    }
                    replicates.computeIfAbsent(Lsid.parse(String.valueOf(lsid)), m -> new ArrayList<>()).add(rs.getRowMap());
                    _replicateLsid.remove(lsid);
                }

                // If results are being deleted, check if all rows for the well group have been deleted
                var deletedRows = new ArrayList<Map<String, Object>>();
                for (var entry : _replicateLsid.entrySet())
                {
                    if (!entry.getValue())
                        deletedRows.add(Map.of(PlateReplicateStatsDomainKind.Column.Lsid.name(), entry.getKey()));
                }

                if (!deletedRows.isEmpty())
                    AssayPlateMetadataService.get().deleteReplicateStats(c, user, _protocol, deletedRows);

                AssayPlateMetadataService.get().updateReplicateStats(c, user, _protocol, replicates);
            }
            catch (ValidationException ve)
            {
                errors.addRowError(ve);
            }
            catch (SQLException e)
            {
                throw UnexpectedException.wrap(e);
            }
        }
    }

    /**
     * Trigger to help validate state values on update. Inserts will be handled on assay
     * run creation.
     */
    private class DataStateTrigger implements Trigger
    {
        Set<Long> _excludedRows = new HashSet<>();

        @Override
        public void beforeUpdate(
            TableInfo table,
            Container c,
            User user,
            @Nullable QueryUpdateService.InsertOption insertOption,
            @Nullable Map<String, Object> newRow,
            @Nullable Map<String, Object> oldRow,
            ValidationException errors,
            Map<String, Object> extraContext
        ) throws ValidationException
        {
            if (errors.hasErrors())
                return;

            if (newRow != null && _qcStateProp != null)
            {
                DataState state = AssayPlateMetadataServiceImpl.validateRowDataStates(c, newRow, _qcStateProp);
                if (!PlateDataStateManager.get().isOperationPermitted(state, PlateDataStateManager.DataOperation.hitSelection))
                {
                    Object o = oldRow.get("RowId");
                    if (asLongElseNull(o) instanceof Long num)
                        _excludedRows.add(num);
                }
            }
        }

        @Override
        public void complete(TableInfo table, Container c, User user, TableInfo.TriggerType event, BatchValidationException errors, Map<String, Object> extraContext)
        {
            // clear out hit selections for exclusions which don't allow the operation
            if (!_excludedRows.isEmpty())
                PlateManager.get().deleteHits(_protocol.getRowId(), _excludedRows);
        }
    }

    private class AutomaticHitSelectionTrigger implements Trigger
    {
        private Set<Integer> dataIds = null;
        private boolean enabled = false;

        @Override
        public void init(
            TableInfo table,
            Container c,
            User user,
            TableInfo.TriggerType event,
            BatchValidationException errors,
            Map<String, Object> extraContext
        )
        {
            if (errors.hasErrors())
                return;

            if (_provider.hasFilterCriteria(_protocol))
            {
                enabled = true;
                dataIds = new HashSet<>();
            }
        }

        @Override
        public void afterUpdate(
            TableInfo table,
            Container c,
            User user,
            @Nullable Map<String, Object> newRow,
            @Nullable Map<String, Object> oldRow,
            ValidationException errors,
            Map<String, Object> extraContext
        )
        {
            if (!enabled || errors.hasErrors() || oldRow == null)
                return;

            dataIds.add(((Number) oldRow.get("DataId")).intValue());
        }

        @Override
        public void complete(
            TableInfo table,
            Container c,
            User user,
            TableInfo.TriggerType event,
            BatchValidationException errors,
            Map<String, Object> extraContext
        )
        {
            if (!enabled || errors.hasErrors())
                return;

            List<Long> runIds = new ArrayList<>();
            for (var expDataId : dataIds)
            {
                var data = ExperimentService.get().getExpData(expDataId);
                if (data != null)
                    runIds.add(data.getRunId());
            }

            try
            {
                AssayPlateMetadataService.get().applyHitSelectionCriteria(c, user, _protocol, table, runIds);
            }
            catch (ValidationException e)
            {
                errors.addRowError(e);
            }
        }
    }
}

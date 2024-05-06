package org.labkey.assay.plate.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.triggers.Trigger;
import org.labkey.api.data.triggers.TriggerFactory;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.plate.query.WellTable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class WellTriggerFactory implements TriggerFactory
{
    @Override
    public @NotNull Collection<Trigger> createTrigger(@Nullable Container c, TableInfo table, Map<String, Object> extraContext)
    {
        return List.of(new PrimaryPlateSetUniqueSamples());
    }

    protected class PrimaryPlateSetUniqueSamples implements Trigger
    {
        private final HashSet<Integer> mutatedWellRowIds = new HashSet<>();

        private void addWellId(@Nullable Map<String, Object> newRow)
        {
            if (
                newRow != null &&
                newRow.containsKey(WellTable.Column.RowId.name()) &&
                newRow.getOrDefault(WellTable.Column.SampleId.name(), null) != null
            )
            {
                Integer wellRowId = (Integer) newRow.get(WellTable.Column.RowId.name());
                if (wellRowId != null)
                    mutatedWellRowIds.add(wellRowId);
            }
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
            if (errors.hasErrors())
                return;
            PlateManager.get().validatePrimaryPlateSetUniqueSamples(mutatedWellRowIds, errors);
        }

        @Override
        public void afterInsert(
            TableInfo table,
            Container c,
            User user,
            @Nullable Map<String, Object> newRow,
            ValidationException errors,
            Map<String, Object> extraContext
        )
        {
            addWellId(newRow);
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
            addWellId(newRow);
        }
    }
}

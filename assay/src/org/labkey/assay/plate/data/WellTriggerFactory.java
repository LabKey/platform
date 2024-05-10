package org.labkey.assay.plate.data;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.triggers.Trigger;
import org.labkey.api.data.triggers.TriggerFactory;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.plate.query.WellTable;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WellTriggerFactory implements TriggerFactory
{
    @Override
    public @NotNull Collection<Trigger> createTrigger(@Nullable Container c, TableInfo table, Map<String, Object> extraContext)
    {
        return List.of(
            new ValidatePrimaryPlateSetUniqueSamplesTrigger(),
            new ComputeWellGroupsTrigger()
        );
    }

    protected class ValidatePrimaryPlateSetUniqueSamplesTrigger implements Trigger
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

    protected class ComputeWellGroupsTrigger implements Trigger
    {
        private final Map<Integer, Map<Integer, PlateManager.WellGroupChange>> wellGroupChanges = new HashMap<>();
        private final Set<Integer> modifiedPlates = new HashSet<>();

        private void checkForChanges(
            @Nullable Map<String, Object> newRow,
            @Nullable Map<String, Object> oldRow,
            ValidationException errors
        )
        {
            if (errors.hasErrors())
                return;

            var hasSampleChange = hasSampleChange(newRow);
            var hasTypeGroupChange = hasTypeGroupChange(newRow);

            if (!hasSampleChange && !hasTypeGroupChange)
                return;

            var wellRowId = (Integer) newRow.get(WellTable.Column.RowId.name());
            if (wellRowId == null)
            {
                errors.addError(new SimpleValidationError("Failed to resolve well \"RowId\"."));
                return;
            }

            var plateRowId = (Integer) newRow.get(WellTable.Column.PlateId.name());
            if (plateRowId == null)
            {
                if (oldRow != null)
                    plateRowId = (Integer) oldRow.get(WellTable.Column.PlateId.name());
                if (plateRowId == null)
                {
                    String error = String.format("Failed to resolve \"PlateId\" for well RowId (%d)", wellRowId);
                    errors.addError(new SimpleValidationError(error));
                    return;
                }
            }

            modifiedPlates.add(plateRowId);

            if (hasTypeGroupChange)
            {
                String type = null;
                String group = null;

                // If the row does contain the key, then it is treated as an explicit change.
                // In this case we set the value to the empty string.
                if (newRow.containsKey(WellTable.Column.Type.name()))
                {
                    type = (String) newRow.get(WellTable.Column.Type.name());
                    if (StringUtils.trimToNull(type) == null)
                        type = "";
                }
                if (newRow.containsKey(WellTable.Column.Group.name()))
                {
                    group = (String) newRow.get(WellTable.Column.Group.name());
                    if (StringUtils.trimToNull(group) == null)
                        group = "";
                }

                var change = new PlateManager.WellGroupChange(plateRowId, wellRowId, type, group);

                if (!wellGroupChanges.containsKey(plateRowId))
                    wellGroupChanges.put(plateRowId, new HashMap<>());
                wellGroupChanges.get(plateRowId).put(wellRowId, change);
            }
        }

        private boolean hasSampleChange(@Nullable Map<String, Object> row)
        {
            return row != null && row.containsKey(WellTable.Column.SampleId.name());
        }

        private boolean hasTypeGroupChange(@Nullable Map<String, Object> row)
        {
            return row != null && (row.containsKey(WellTable.Column.Type.name()) || row.containsKey(WellTable.Column.Group.name()));
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
            if (errors.hasErrors() || (wellGroupChanges.isEmpty() && modifiedPlates.isEmpty()))
                return;

            try
            {
                PlateManager.get().computeWellGroups(c, user, wellGroupChanges);
                PlateManager.get().validateWellGroups(c, modifiedPlates);
            }
            catch (ValidationException e)
            {
                errors.addRowError(e);
            }
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
            checkForChanges(newRow, null, errors);
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
            checkForChanges(newRow, oldRow, errors);
        }
    }
}

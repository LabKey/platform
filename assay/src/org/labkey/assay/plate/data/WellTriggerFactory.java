package org.labkey.assay.plate.data;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.triggers.Trigger;
import org.labkey.api.data.triggers.TriggerFactory;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.UserSchema;
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
            new EnsureSampleWellTypeTrigger(),
            new ValidatePrimaryPlateSetUniqueSamplesTrigger(),
            new ComputeWellGroupsTrigger()
        );
    }

    protected class EnsureSampleWellTypeTrigger implements Trigger
    {
        // When no type is given but sampleid is populated, provide 'Sample' as the type
        private void addTypeSample(@Nullable Map<String, Object> newRow)
        {
            if (
                    newRow != null &&
                            newRow.containsKey(WellTable.Column.SampleId.name()) &&
                            newRow.getOrDefault(WellTable.Column.SampleId.name(), null) != null &&
                            !newRow.containsKey(WellTable.Column.Type.name())
            )
                newRow.put(WellTable.Column.Type.name(), WellGroup.Type.SAMPLE.name());
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
            addTypeSample(newRow);
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
            addTypeSample(newRow);
        }
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
        private final Map<Integer, Map<Integer, String>> wellTypeMap = new HashMap<>();

        private void checkForChanges(
            Container container,
            User user,
            @Nullable Map<String, Object> newRow,
            @Nullable Map<String, Object> oldRow,
            ValidationException errors,
            Map<String, Object> extraContext
        )
        {
            // Skip computing well groups when this is a plate copy operation
            if (isCopyOperation(extraContext) || newRow == null)
                return;

            var hasSampleChange = hasSampleChange(newRow);
            var hasTypeGroupChange = hasTypeGroupChange(newRow);

            // If this is an insertion (newRow != null && oldRow == null),
            // then verify further to ignore when type and group are present but are set to null.
            if (hasTypeGroupChange && oldRow == null)
                hasTypeGroupChange = newRow.get(WellTable.Column.Type.name()) != null || newRow.get(WellTable.Column.WellGroup.name()) != null;

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

            // If the sample, type, or any data on a replicate well has been updated,
            // then mark the plate as modified and subsequently validate the well groups.
            if (!hasSampleChange && !hasTypeGroupChange && !hasReplicateChange(container, user, plateRowId, wellRowId))
                return;

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
                if (newRow.containsKey(WellTable.Column.WellGroup.name()))
                {
                    group = (String) newRow.get(WellTable.Column.WellGroup.name());
                    if (StringUtils.trimToNull(group) == null)
                        group = "";
                }

                var change = new PlateManager.WellGroupChange(plateRowId, wellRowId, type, group);

                wellGroupChanges.computeIfAbsent(plateRowId, HashMap::new).put(wellRowId, change);
            }
        }

        private boolean hasReplicateChange(Container container, User user, @NotNull Integer plateRowId, @NotNull Integer wellRowId)
        {
            var wellMap = wellTypeMap.computeIfAbsent(plateRowId, (pid) -> {
                var map = new HashMap<Integer, String>();
                UserSchema schema = QueryService.get().getUserSchema(user, container, "plate");
                SQLFragment sql = new SQLFragment("SELECT RowId, Type FROM plate.Well WHERE PlateId = ?").add(pid);
                QueryService.get().getSelectBuilder(schema, sql.toDebugString())
                        .buildSqlSelector(null)
                        .forEach(r -> map.put(r.getInt("RowId"), r.getString("Type")));

                return map;
            });

            return WellGroup.Type.REPLICATE.name().equals(wellMap.get(wellRowId));
        }

        private boolean hasSampleChange(@Nullable Map<String, Object> row)
        {
            return row != null && row.containsKey(WellTable.Column.SampleId.name());
        }

        private boolean hasTypeGroupChange(@Nullable Map<String, Object> row)
        {
            return row != null && (row.containsKey(WellTable.Column.Type.name()) || row.containsKey(WellTable.Column.WellGroup.name()));
        }

        private boolean isCopyOperation(Map<String, Object> extraContext)
        {
            return extraContext != null && (boolean) extraContext.getOrDefault(PlateManager.PLATE_COPY_FLAG, false);
        }

        private boolean isSaveOperation(Map<String, Object> extraContext)
        {
            return extraContext != null && (boolean) extraContext.getOrDefault(PlateManager.PLATE_SAVE_FLAG, false);
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
                PlateManager.get().validateWellGroups(c, user, modifiedPlates);
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
            if (errors.hasErrors())
                return;

            checkForChanges(c, user, newRow, null, errors, extraContext);
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
            if (errors.hasErrors())
                return;

            checkForChanges(c, user, newRow, oldRow, errors, extraContext);
        }
    }
}
